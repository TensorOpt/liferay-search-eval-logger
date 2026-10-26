/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.zeroresult;

import ai.tensoropt.sel.api.AudienceType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The zero-result panel on the export screen (DESIGN.md 6, TO-135): the
 * share of first-page searches that returned nothing, overall and by
 * audience, and the most frequent zero-result queries.
 *
 * <p>
 * Built, on a cache miss, from two aggregates {@link ZeroResultPanelLoader}
 * reads and caches for an hour per virtual instance: a SQL
 * <code>group by audienceType</code>
 * (<code>SearchEventLocalService#getFirstPageSearchCountsByAudienceType</code>),
 * and a map of normalized query text to count that the loader itself builds
 * by streaming raw rows
 * (<code>SearchEventLocalService#forEachZeroResultFirstPageQuery</code>) and
 * merging them by {@link ZeroResultQueryNormalizer#normalize} as it goes, not
 * in SQL (see that method's javadoc for why). Nothing here re-queries the
 * log or collects every event into memory; this class only sorts and cuts
 * the already-aggregated rows it is given. Computed from the existing log
 * only, for the whole retention window, since there is no date range on
 * this screen to narrow it (purge already bounds what the log holds).
 * </p>
 *
 * <p>
 * Plain getters, not a record: the JSP reads this bean and
 * {@link ZeroResultQuery} through EL, which on this platform's Tomcat
 * resolves JavaBeans properties only (see
 * <code>ai.tensoropt.sel.web.internal.portlet.ExportRow</code>).
 * </p>
 */
public class ZeroResultPanel {

	/**
	 * @param firstPageSearchCountsByAudienceType as returned by
	 *        <code>SearchEventLocalService#getFirstPageSearchCountsByAudienceType</code>:
	 *        stored <code>audienceType</code> value to
	 *        <code>{searchCount, zeroResultSearchCount}</code>
	 * @param normalizedZeroResultFirstPageQueryCounts as built by
	 *        {@link ZeroResultPanelLoader}: normalized query text
	 *        ({@link ZeroResultQueryNormalizer#normalize}) to how many
	 *        zero-result first-page searches, across however many raw
	 *        variants, normalized to it
	 * @param topQueriesApproximate whether {@link ZeroResultQueryCounts}
	 *        dropped at least one distinct normalized query because its
	 *        10,000-entry cap was already reached (DESIGN.md 6.3); the top
	 *        20 may then be missing a query that would otherwise have
	 *        placed, though an already-counted query is never undercounted
	 *        by it
	 */
	public static ZeroResultPanel build(
		Map<String, long[]> firstPageSearchCountsByAudienceType,
		Map<String, Long> normalizedZeroResultFirstPageQueryCounts,
		boolean topQueriesApproximate) {

		long overallSearches = 0;
		long overallZeroResultSearches = 0;

		for (long[] counts :
				firstPageSearchCountsByAudienceType.values()) {

			overallSearches += counts[0];
			overallZeroResultSearches += counts[1];
		}

		long[] guestCounts = firstPageSearchCountsByAudienceType.getOrDefault(
			AudienceType.GUEST.name(), _NO_COUNTS);
		long[] signedInCounts =
			firstPageSearchCountsByAudienceType.getOrDefault(
				AudienceType.AUTHENTICATED.name(), _NO_COUNTS);

		// Only an audience with at least one zero-result search can
		// contribute to the table, so skip the sort entirely when there is
		// nothing to show (also covers the empty-log case, where
		// normalizedZeroResultFirstPageQueryCounts is always empty).

		List<ZeroResultQuery> topQueries = Collections.emptyList();

		if (overallZeroResultSearches > 0) {
			topQueries = ZeroResultQueryNormalizer.topZeroResultQueries(
				normalizedZeroResultFirstPageQueryCounts, _TOP_QUERY_LIMIT);
		}

		return new ZeroResultPanel(
			overallSearches, overallZeroResultSearches, guestCounts[0],
			guestCounts[1], signedInCounts[0], signedInCounts[1],
			topQueries, topQueriesApproximate);
	}

	/**
	 * <code>{zeroResultSearches, searches, percent}</code>, all already
	 * formatted as strings, for the "Guests: N of M searches (X%)." line.
	 *
	 * <p>
	 * A real array, not a {@link List}: the JSP passes this straight to
	 * <code>liferay-ui:message</code>'s <code>arguments</code> attribute,
	 * whose tag handler only recognizes an actual array as multiple
	 * arguments (<code>Object.getClass().isArray()</code>) and otherwise
	 * wraps whatever it is given, a {@code List} included, as a single
	 * argument.
	 * </p>
	 */
	public Object[] getGuestMessageArguments() {
		return _messageArguments(_guestZeroResultSearches, _guestSearches);
	}

	/**
	 * See {@link #getGuestMessageArguments}; this is the same triple for the
	 * "N of M searches (X%) in the log returned no results." line.
	 */
	public Object[] getOverallMessageArguments() {
		return _messageArguments(
			_overallZeroResultSearches, _overallSearches);
	}

	/**
	 * See {@link #getGuestMessageArguments}; this is the same triple for the
	 * "Signed-in users: N of M searches (X%)." line.
	 */
	public Object[] getSignedInMessageArguments() {
		return _messageArguments(
			_signedInZeroResultSearches, _signedInSearches);
	}

	public List<ZeroResultQuery> getTopQueries() {
		return _topQueries;
	}

	/**
	 * Whether the top-20 table may be missing a query: DESIGN.md 6.3's
	 * 10,000-entry cap on distinct normalized zero-result queries was
	 * reached while this panel was computed. The audience rate figures are
	 * never affected, since they come from a separate SQL aggregate, not
	 * from the capped map this flag describes.
	 */
	public boolean isTopQueriesApproximate() {
		return _topQueriesApproximate;
	}

	/**
	 * The log held nothing to compute a rate from at all. Distinct from "no
	 * zero-result searches", which still has a rate to show (0 (0.0%)).
	 *
	 * <p>
	 * Not named <code>isEmpty</code>: EL's <code>empty</code> operator and a
	 * bean property of that name are easy to confuse when reading the JSP,
	 * even though the dotted form is unambiguous to the parser.
	 * </p>
	 */
	public boolean isNoSearchesLogged() {
		return _overallSearches == 0;
	}

	private Object[] _messageArguments(long zeroResultSearches, long searches) {
		return new String[] {
			String.valueOf(zeroResultSearches), String.valueOf(searches),
			ZeroResultRate.formatPercent(zeroResultSearches, searches)
		};
	}

	private ZeroResultPanel(
		long overallSearches, long overallZeroResultSearches,
		long guestSearches, long guestZeroResultSearches,
		long signedInSearches, long signedInZeroResultSearches,
		List<ZeroResultQuery> topQueries, boolean topQueriesApproximate) {

		_overallSearches = overallSearches;
		_overallZeroResultSearches = overallZeroResultSearches;
		_guestSearches = guestSearches;
		_guestZeroResultSearches = guestZeroResultSearches;
		_signedInSearches = signedInSearches;
		_signedInZeroResultSearches = signedInZeroResultSearches;
		_topQueries = topQueries;
		_topQueriesApproximate = topQueriesApproximate;
	}

	private static final long[] _NO_COUNTS = {0, 0};

	/**
	 * Top 20, matching the acceptance jq recipe's <code>.[:20]</code>.
	 */
	private static final int _TOP_QUERY_LIMIT = 20;

	private final long _guestSearches;
	private final long _guestZeroResultSearches;
	private final long _overallSearches;
	private final long _overallZeroResultSearches;
	private final long _signedInSearches;
	private final long _signedInZeroResultSearches;
	private final List<ZeroResultQuery> _topQueries;
	private final boolean _topQueriesApproximate;

}
