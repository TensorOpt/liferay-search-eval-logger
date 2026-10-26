/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.zeroresult;

import ai.tensoropt.sel.service.SearchEventLocalService;

import com.liferay.portal.kernel.cache.PortalCache;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

/**
 * Builds the zero-result panel (DESIGN.md 6, TO-135) from
 * {@code SearchEventLocalService}, with nothing allowed to escape and take
 * the rest of the export screen down with it.
 *
 * <p>
 * <b>Failure isolation.</b> Either underlying aggregate can throw: a
 * timeout, a query the connected database does not support, a transient
 * connection failure. None of that is a reason the admin screen should
 * render as "temporarily unavailable" instead of showing the export controls
 * and the admission counters it already shows reliably. Kept in its own
 * class, separate from {@code SearchEvalLoggerPortlet}, specifically so the
 * failure path is unit-testable against a mocked
 * {@code SearchEventLocalService} without a portal, a render request or a
 * theme display.
 * </p>
 *
 * <p>
 * <b>Caching.</b> A computed panel is kept for an hour per virtual instance
 * in a single-VM portal cache. The figures describe the whole retention
 * window, so an hour barely moves them, while each computation reads every
 * first-page event of the instance; without the cache every render of the
 * export screen would repeat that read. A failed computation is not cached,
 * so the next render tries again. Server Administration's "Clear content
 * cached by this VM" empties the cache, as does a restart.
 * </p>
 */
public final class ZeroResultPanelLoader {

	public static final String PORTAL_CACHE_NAME =
		ZeroResultPanel.class.getName();

	/**
	 * The cached panel for the company if one is less than an hour old,
	 * otherwise a freshly computed one, which is then cached. {@code null}
	 * if either aggregate throws. The caller already treats a {@code null}
	 * the same as "the viewer lacks the export permission": no
	 * "zeroResultPanel" request attribute, and the JSP renders the rest of
	 * the screen exactly as it would otherwise.
	 */
	public static ZeroResultPanel load(
		PortalCache<Long, ZeroResultPanel> portalCache,
		SearchEventLocalService searchEventLocalService, long companyId) {

		ZeroResultPanel zeroResultPanel = portalCache.get(companyId);

		if (zeroResultPanel != null) {
			return zeroResultPanel;
		}

		zeroResultPanel = _compute(searchEventLocalService, companyId);

		if (zeroResultPanel != null) {
			portalCache.put(companyId, zeroResultPanel, _TIME_TO_LIVE_SECONDS);
		}

		return zeroResultPanel;
	}

	private static ZeroResultPanel _compute(
		SearchEventLocalService searchEventLocalService, long companyId) {

		try {
			ZeroResultQueryCounts zeroResultQueryCounts =
				new ZeroResultQueryCounts();

			searchEventLocalService.forEachZeroResultFirstPageQuery(
				companyId,
				queryText -> zeroResultQueryCounts.add(
					ZeroResultQueryNormalizer.normalize(queryText)));

			return ZeroResultPanel.build(
				searchEventLocalService.
					getFirstPageSearchCountsByAudienceType(companyId),
				zeroResultQueryCounts.getCounts(),
				zeroResultQueryCounts.isDistinctQueryCapHit());
		}
		catch (Exception exception) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to compute the zero-result panel for company " +
						companyId,
					exception);
			}

			return null;
		}
	}

	private ZeroResultPanelLoader() {
		throw new AssertionError();
	}

	private static final int _TIME_TO_LIVE_SECONDS = 3600;

	private static final Log _log = LogFactoryUtil.getLog(
		ZeroResultPanelLoader.class);

}
