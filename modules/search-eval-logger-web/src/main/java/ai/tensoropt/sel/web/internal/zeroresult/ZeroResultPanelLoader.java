/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.zeroresult;

import ai.tensoropt.sel.service.SearchEventLocalService;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import java.util.HashMap;
import java.util.Map;

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
 */
public final class ZeroResultPanelLoader {

	/**
	 * {@code null} if either aggregate throws. The caller already treats a
	 * {@code null} the same as "the viewer lacks the export permission": no
	 * "zeroResultPanel" request attribute, and the JSP renders the rest of
	 * the screen exactly as it would otherwise.
	 */
	public static ZeroResultPanel load(
		SearchEventLocalService searchEventLocalService, long companyId) {

		try {
			Map<String, Long> zeroResultQueryCounts = new HashMap<>();

			searchEventLocalService.forEachZeroResultFirstPageQuery(
				companyId,
				queryText -> zeroResultQueryCounts.merge(
					ZeroResultQueryNormalizer.normalize(queryText), 1L,
					Long::sum));

			return ZeroResultPanel.build(
				searchEventLocalService.
					getFirstPageSearchCountsByAudienceType(companyId),
				zeroResultQueryCounts);
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

	private static final Log _log = LogFactoryUtil.getLog(
		ZeroResultPanelLoader.class);

}
