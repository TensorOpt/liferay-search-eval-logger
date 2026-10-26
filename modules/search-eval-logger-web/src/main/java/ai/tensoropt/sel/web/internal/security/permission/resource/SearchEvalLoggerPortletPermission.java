/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.security.permission.resource;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.service.permission.PortletPermissionUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;

import ai.tensoropt.sel.web.internal.constants.SearchEvalLoggerPortletKeys;

/**
 * Checks the dedicated export permission of DESIGN.md 6.1.
 *
 * <p>
 * It is a portlet resource action rather than a role check, so that a site
 * can grant exporting to someone who is not a portal administrator. Liferay's
 * own company-administrator check bypasses resource actions, so the
 * Administrator role holds this permission implicitly and it cannot be
 * withheld from it; this separation only ever widens who may export beyond
 * that role, never narrows it. An export leaves the instance, and deciding
 * who else may produce one is a different decision from deciding who may
 * administer the portal.
 * </p>
 */
public class SearchEvalLoggerPortletPermission {

	public static boolean contains(ThemeDisplay themeDisplay, String actionId)
		throws PortalException {

		if (themeDisplay == null) {
			return false;
		}

		return PortletPermissionUtil.contains(
			themeDisplay.getPermissionChecker(), themeDisplay.getLayout(),
			SearchEvalLoggerPortletKeys.PORTLET_NAME, actionId);
	}

	private SearchEvalLoggerPortletPermission() {
		throw new AssertionError();
	}

}
