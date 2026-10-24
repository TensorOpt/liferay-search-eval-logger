/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.zeroresult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Checked against the acceptance jq recipe (TO-135's Z2):
 *
 * <pre>
 * [.[] | select(.requested_from == 0 and .total_hits == 0) | .query |
 *   ascii_downcase | gsub("^\s+|\s+$"; "")] |
 *   group_by(.) | map({query: .[0], searches: length}) |
 *   sort_by(-.searches) | .[:20]
 * </pre>
 *
 * <p>
 * Every test here was written to fail if the normalization, tie order or
 * cutoff silently drifted from that recipe, not merely to exercise the code
 * once. Merging case/whitespace variants into one group is
 * {@code ZeroResultPanelLoaderTest}'s job, not this class's: that merge now
 * happens once, in the loader, as it streams; {@link #topZeroResultQueries}
 * only sorts and cuts an already-normalized map, so a test feeding it raw,
 * unmerged variants directly would be testing a merge step that code no
 * longer has.
 * </p>
 */
public class ZeroResultQueryNormalizerTest {

	@Test
	public void normalizeFoldsOnlyASCIILetters() {

		// jq's ascii_downcase leaves non-ASCII case alone. If this folded
		// with String#toLowerCase, the Turkish dotless i (İ) would come
		// out as "i̇" (i with a combining dot above) under some
		// locales, which is not what the recipe does, and would wrongly
		// merge it with a plain "i".

		assertEquals("cafÉ", ZeroResultQueryNormalizer.normalize("CAFÉ"));
		assertEquals("abc", ZeroResultQueryNormalizer.normalize("AbC"));
	}

	@Test
	public void normalizeTrimsUnicodeWhitespaceNotOnlyASCII() {

		// Confirmed empirically with jq: its \s strips a non-breaking space
		// (U+00A0) and an ideographic space (U+3000) at the edges, not only
		// the ASCII set [ \t\n\x0B\f\r]. A Java \s without
		// UNICODE_CHARACTER_CLASS would leave these in place.

		assertEquals("hello", ZeroResultQueryNormalizer.normalize(" hello "));
		assertEquals("hello", ZeroResultQueryNormalizer.normalize("　hello　"));
		assertEquals("hello", ZeroResultQueryNormalizer.normalize("\t\n hello \r\n"));
	}

	@Test
	public void normalizeLeavesInnerWhitespaceAlone() {

		// Only leading and trailing whitespace is stripped (the recipe
		// anchors both alternatives), so "two words" must not collapse.

		assertEquals(
			"two  words", ZeroResultQueryNormalizer.normalize("  two  words  "));
	}

	/**
	 * {@link ZeroResultQueryNormalizer#topZeroResultQueries} does not
	 * normalize or merge: it is fed already-normalized counts, so two
	 * distinct normalized keys stay two rows even though, had they been
	 * raw case variants of each other, {@link ZeroResultQueryNormalizer#normalize}
	 * would have merged them. (That merge path is
	 * {@code ZeroResultPanelLoaderTest}'s "widget"/"WIDGET" case, against
	 * the actual loader.)
	 */
	@Test
	public void doesNotMergeAlreadyDistinctNormalizedKeys() {
		Map<String, Long> normalizedCounts = new LinkedHashMap<>();

		normalizedCounts.put("nothingmatches", 3L);
		normalizedCounts.put("somethingelse", 2L);

		List<ZeroResultQuery> topQueries =
			ZeroResultQueryNormalizer.topZeroResultQueries(
				normalizedCounts, 20);

		assertEquals(2, topQueries.size());
		assertEquals("nothingmatches", topQueries.get(0).getQuery());
		assertEquals(3L, topQueries.get(0).getSearchCount());
		assertEquals("somethingelse", topQueries.get(1).getQuery());
		assertEquals(2L, topQueries.get(1).getSearchCount());
	}

	@Test
	public void sortsByCountDescending() {
		Map<String, Long> normalizedCounts = new LinkedHashMap<>();

		normalizedCounts.put("rare", 1L);
		normalizedCounts.put("common", 5L);
		normalizedCounts.put("medium", 3L);

		List<ZeroResultQuery> topQueries =
			ZeroResultQueryNormalizer.topZeroResultQueries(normalizedCounts, 20);

		assertEquals(
			List.of("common", "medium", "rare"),
			topQueries.stream(
			).map(
				ZeroResultQuery::getQuery
			).collect(
				java.util.stream.Collectors.toList()
			));
	}

	/**
	 * jq's group_by sorts groups by key (Unicode code point order), and its
	 * sort_by is a stable sort, so equal counts keep that ascending key
	 * order rather than, say, insertion order or a reverse order.
	 */
	@Test
	public void tiesBreakByAscendingCodePointOrderOfTheNormalizedQuery() {
		Map<String, Long> normalizedCounts = new LinkedHashMap<>();

		normalizedCounts.put("zebra", 2L);
		normalizedCounts.put("apple", 2L);
		normalizedCounts.put("mango", 2L);

		List<ZeroResultQuery> topQueries =
			ZeroResultQueryNormalizer.topZeroResultQueries(normalizedCounts, 20);

		assertEquals(
			List.of("apple", "mango", "zebra"),
			topQueries.stream(
			).map(
				ZeroResultQuery::getQuery
			).collect(
				java.util.stream.Collectors.toList()
			));
	}

	@Test
	public void cutsOffAtTheLimit() {
		Map<String, Long> normalizedCounts = new LinkedHashMap<>();

		for (int i = 0; i < 25; i++) {
			normalizedCounts.put("query" + i, (long)(25 - i));
		}

		List<ZeroResultQuery> topQueries =
			ZeroResultQueryNormalizer.topZeroResultQueries(normalizedCounts, 20);

		assertEquals(20, topQueries.size());
		assertEquals("query0", topQueries.get(0).getQuery());
		assertEquals(25L, topQueries.get(0).getSearchCount());
		assertEquals("query19", topQueries.get(19).getQuery());
	}

	@Test
	public void emptyInputProducesNoRows() {
		assertTrue(
			ZeroResultQueryNormalizer.topZeroResultQueries(
				Map.of(), 20
			).isEmpty());
	}

	@Test
	public void aNullKeyIsSkippedRatherThanThrowing() {
		Map<String, Long> normalizedCounts = new LinkedHashMap<>();

		normalizedCounts.put(null, 4L);
		normalizedCounts.put("real query", 2L);

		List<ZeroResultQuery> topQueries =
			ZeroResultQueryNormalizer.topZeroResultQueries(
				normalizedCounts, 20);

		assertEquals(1, topQueries.size());
		assertEquals("real query", topQueries.get(0).getQuery());
	}

}
