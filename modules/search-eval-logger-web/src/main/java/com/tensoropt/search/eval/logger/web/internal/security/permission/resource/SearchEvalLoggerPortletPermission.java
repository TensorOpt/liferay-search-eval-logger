/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.web.internal.security.permission.resource;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.service.permission.PortletPermissionUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;

import com.tensoropt.search.eval.logger.web.internal.constants.SearchEvalLoggerPortletKeys;

/**
 * Checks the dedicated export permission of DESIGN.md 6.1.
 *
 * <p>
 * It is a portlet resource action rather than a role check, so that a site can
 * grant exporting to someone who is not a portal administrator, or withhold it
 * from someone who is. That separation is the point: an export leaves the
 * instance, and deciding who may produce one is a different decision from
 * deciding who may administer the portal.
 * </p>
 */
public class SearchEvalLoggerPortletPermission {

	public static boolean contains(
			PermissionChecker permissionChecker, Layout layout, String actionId)
		throws PortalException {

		return PortletPermissionUtil.contains(
			permissionChecker, layout, SearchEvalLoggerPortletKeys.PORTLET_NAME,
			actionId);
	}

	public static boolean contains(ThemeDisplay themeDisplay, String actionId)
		throws PortalException {

		if (themeDisplay == null) {
			return false;
		}

		return contains(
			themeDisplay.getPermissionChecker(), themeDisplay.getLayout(),
			actionId);
	}

	private SearchEvalLoggerPortletPermission() {
		throw new AssertionError();
	}

}
