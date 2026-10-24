/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.zeroresult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Normalizes and orders zero-result queries exactly the way the acceptance
 * check's jq recipe does (DESIGN.md 6, TO-135's Z2), so the panel's table
 * and the recipe agree on every input, not only the common ones.
 *
 * <p>
 * Normalizing ({@link #normalize}) and merging by the normalized form happen
 * once, in {@link ZeroResultPanelLoader}, as it streams raw query text one
 * row at a time out of {@code SearchEventLocalService#forEachZeroResultFirstPageQuery}:
 * the loader's running counts are already keyed by normalized query by the
 * time anything here sees them. {@link #topZeroResultQueries} therefore
 * takes normalized counts and only sorts and cuts; it does not normalize or
 * merge a second time; the one direct test of case/whitespace variants
 * merging into a single row is {@code ZeroResultPanelLoaderTest}, against
 * the actual loader, not this class in isolation.
 * </p>
 *
 * <h2>Normalization</h2>
 *
 * <p>
 * jq's <code>ascii_downcase</code> lower-cases only ASCII <code>A</code>-
 * <code>Z</code> and leaves every other character untouched, unlike
 * {@link String#toLowerCase()}, which case-folds the whole Unicode range and
 * would merge queries jq keeps apart (a Turkish dotless i, a German sharp s,
 * and more). {@link #normalize} folds only that range by hand.
 * </p>
 *
 * <p>
 * jq's <code>gsub("^\s+|\s+$"; "")</code> runs on Oniguruma's <code>\s</code>,
 * which was checked empirically (<code>jq</code> is installed in this
 * environment) against a tab, a newline, a carriage return, a vertical tab, a
 * form feed, a plain space, U+00A0 non-breaking space, U+3000 ideographic
 * space and U+2002/U+2028: it stripped every one of them, i.e. it matches the
 * full Unicode <code>White_Space</code> property, not only the ASCII set.
 * Java's <code>\s</code> without a flag matches only
 * <code>[ \t\n\x0B\f\r]</code> and leaves the non-ASCII examples in place,
 * which would under-trim relative to the recipe and split one group into two.
 * Compiling with {@link Pattern#UNICODE_CHARACTER_CLASS} switches
 * <code>\s</code> to the Unicode property and was confirmed, against the same
 * characters, to match jq exactly.
 * </p>
 *
 * <h2>Order</h2>
 *
 * <p>
 * The recipe groups first, then sorts by <code>-length</code>. jq's
 * <code>sort_by</code> is stable, and <code>group_by</code> already sorted
 * the groups by key (jq orders strings by Unicode code point), so equal
 * counts come out in ascending code point order of the normalized query.
 * {@link #topZeroResultQueries} reproduces that with a descending count
 * comparator and a code-point-ordered tiebreaker, rather than
 * {@link String#compareTo}, which orders by UTF-16 code unit and disagrees
 * with code point order whenever a supplementary character (outside the
 * Basic Multilingual Plane) is involved.
 * </p>
 */
public final class ZeroResultQueryNormalizer {

	/**
	 * Sorts already-normalized zero-result query counts by count descending,
	 * ties broken by ascending code point order of the query, and returns at
	 * most <code>limit</code> rows. See the class comment for why each step
	 * has to be exactly this, not the nearest Java equivalent, and for why
	 * <code>normalizedQueryCounts</code> is expected to be normalized
	 * already rather than normalized here.
	 *
	 * <p>
	 * A null key is skipped rather than thrown on, the same defensive
	 * posture the rest of this plugin takes (DESIGN.md 3.2), though the one
	 * production caller, {@link ZeroResultPanelLoader}, never produces one.
	 * </p>
	 *
	 * <p>
	 * Bounded by the number of distinct normalized queries, which is the
	 * caller's job to keep small: {@link ZeroResultPanelLoader} merges as it
	 * streams, never holding more than that many entries at once.
	 * </p>
	 */
	public static List<ZeroResultQuery> topZeroResultQueries(
		Map<String, Long> normalizedQueryCounts, int limit) {

		List<ZeroResultQuery> zeroResultQueries = new ArrayList<>(
			normalizedQueryCounts.size());

		for (Map.Entry<String, Long> entry :
				normalizedQueryCounts.entrySet()) {

			String query = entry.getKey();

			if (query == null) {
				continue;
			}

			zeroResultQueries.add(
				new ZeroResultQuery(query, entry.getValue()));
		}

		zeroResultQueries.sort(_TOP_QUERY_COMPARATOR);

		if (zeroResultQueries.size() <= limit) {
			return zeroResultQueries;
		}

		return zeroResultQueries.subList(0, limit);
	}

	/**
	 * jq's <code>ascii_downcase | gsub("^\s+|\s+$"; "")</code>; see the class
	 * comment for why neither half can be {@link String}'s own Unicode-aware
	 * equivalent.
	 */
	public static String normalize(String query) {
		return _TRIM_PATTERN.matcher(_asciiDowncase(query)).replaceAll("");
	}

	private static String _asciiDowncase(String s) {
		char[] chars = s.toCharArray();

		for (int i = 0; i < chars.length; i++) {
			char c = chars[i];

			if ((c >= 'A') && (c <= 'Z')) {
				chars[i] = (char)(c + ('a' - 'A'));
			}
		}

		return new String(chars);
	}

	/**
	 * jq orders strings by Unicode code point, which {@link String#compareTo}
	 * does not: it compares UTF-16 code units, under which a supplementary
	 * character (encoded as a surrogate pair in the U+D800-U+DFFF range)
	 * sorts before a Basic Multilingual Plane character in U+E000-U+FFFF,
	 * the opposite of its actual, higher code point value.
	 */
	private static int _compareByCodePoint(String a, String b) {
		int[] aCodePoints = a.codePoints().toArray();
		int[] bCodePoints = b.codePoints().toArray();

		int length = Math.min(aCodePoints.length, bCodePoints.length);

		for (int i = 0; i < length; i++) {
			int result = Integer.compare(aCodePoints[i], bCodePoints[i]);

			if (result != 0) {
				return result;
			}
		}

		return Integer.compare(aCodePoints.length, bCodePoints.length);
	}

	private static final Comparator<ZeroResultQuery> _TOP_QUERY_COMPARATOR =
		Comparator.comparingLong(
			ZeroResultQuery::getSearchCount
		).reversed(
		).thenComparing(
			ZeroResultQuery::getQuery,
			ZeroResultQueryNormalizer::_compareByCodePoint
		);

	private static final Pattern _TRIM_PATTERN = Pattern.compile(
		"^\\s+|\\s+$", Pattern.UNICODE_CHARACTER_CLASS);

	private ZeroResultQueryNormalizer() {
		throw new AssertionError();
	}

}
