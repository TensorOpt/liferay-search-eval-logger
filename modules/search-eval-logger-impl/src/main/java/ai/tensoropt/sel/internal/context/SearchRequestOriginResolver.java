/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.context;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.security.auth.CompanyThreadLocal;
import com.liferay.portal.kernel.security.auth.PrincipalThreadLocal;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.WebKeys;

import ai.tensoropt.sel.api.SourceType;

import javax.servlet.http.HttpServletRequest;

import org.osgi.service.component.annotations.Component;

/**
 * Classifies the origin of a search from the current request, per DESIGN.md
 * 3.2.
 *
 * <p>
 * Every read here is null guarded and the whole method is wrapped, because
 * <code>Searcher</code> is reachable from background threads where no request
 * exists. Absence of context is a finding, not an error: it resolves to
 * {@link SourceType#UNKNOWN} rather than a guess.
 * </p>
 *
 * <p>
 * Stack trace inspection is deliberately not used. It would be the only way to
 * separate some of these cases exactly, and it is far too expensive for a path
 * that runs on every search.
 * </p>
 */
@Component(service = SearchRequestOriginResolver.class)
public class SearchRequestOriginResolver {

	public SearchRequestOrigin resolve() {
		try {
			SearchRequestOrigin searchRequestOrigin = _doResolve();

			_probeThreadLocals(searchRequestOrigin);

			return searchRequestOrigin;
		}
		catch (Throwable throwable) {
			return SearchRequestOrigin.UNKNOWN;
		}
	}

	/**
	 * EC-11 in DESIGN.md section 7: which ThreadLocals are actually populated
	 * differs per search path, and everything context-dependent this plugin
	 * records depends on the answer. Reported per resolved origin so the paths
	 * can be told apart, and read defensively: a probe that throws must not
	 * change what the caller sees.
	 */
	private void _probeThreadLocals(SearchRequestOrigin searchRequestOrigin) {
		if (!_log.isDebugEnabled()) {
			return;
		}

		try {
			HttpServletRequest httpServletRequest = _getHttpServletRequest();

			Object themeDisplay = null;

			if (httpServletRequest != null) {
				themeDisplay = httpServletRequest.getAttribute(
					WebKeys.THEME_DISPLAY);
			}

			_log.debug(
				StringBundler.concat(
					"EC-11 sourceType=", String.valueOf(
						searchRequestOrigin.getSourceType()),
					" suggestion=", String.valueOf(
						searchRequestOrigin.isSuggestion()),
					" httpServletRequest=", String.valueOf(
						httpServletRequest != null),
					" themeDisplay=", String.valueOf(themeDisplay != null),
					" serviceContext=", String.valueOf(
						ServiceContextThreadLocal.getServiceContext() != null),
					" companyId=", String.valueOf(
						CompanyThreadLocal.getCompanyId()),
					" principalUserId=", String.valueOf(
						PrincipalThreadLocal.getUserId()),
					" permissionChecker=", String.valueOf(
						PermissionThreadLocal.getPermissionChecker() != null),
					" path=", String.valueOf(
						(httpServletRequest == null) ? null :
							_getPath(httpServletRequest))));
		}
		catch (Throwable throwable) {
			_log.debug("EC-11 probe failed", throwable);
		}
	}

	private SearchRequestOrigin _doResolve() {
		try {
			HttpServletRequest httpServletRequest = _getHttpServletRequest();

			if (httpServletRequest == null) {
				return SearchRequestOrigin.UNKNOWN;
			}

			String path = _getPath(httpServletRequest);

			if (path == null) {
				return new SearchRequestOrigin(SourceType.OTHER, false, true);
			}

			if (path.startsWith(_HEADLESS_PATH_PREFIX)) {
				return new SearchRequestOrigin(
					SourceType.HEADLESS, _isSuggestionPath(path), true);
			}

			if (_hasPortletContext(httpServletRequest)) {
				return new SearchRequestOrigin(SourceType.WIDGET, false, true);
			}

			return new SearchRequestOrigin(SourceType.OTHER, false, true);
		}
		catch (Throwable throwable) {
			return SearchRequestOrigin.UNKNOWN;
		}
	}

	private HttpServletRequest _getHttpServletRequest() {
		ServiceContext serviceContext =
			ServiceContextThreadLocal.getServiceContext();

		if (serviceContext == null) {
			return null;
		}

		return serviceContext.getRequest();
	}

	private String _getPath(HttpServletRequest httpServletRequest) {
		String requestURI = httpServletRequest.getRequestURI();

		if (requestURI == null) {
			return null;
		}

		String contextPath = httpServletRequest.getContextPath();

		if ((contextPath != null) && !contextPath.isEmpty() &&
			requestURI.startsWith(contextPath)) {

			return requestURI.substring(contextPath.length());
		}

		return requestURI;
	}

	private boolean _hasPortletContext(HttpServletRequest httpServletRequest) {
		Object themeDisplay = httpServletRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		if (themeDisplay instanceof ThemeDisplay) {
			return true;
		}

		return httpServletRequest.getAttribute(WebKeys.PORTLET_ID) != null;
	}

	/**
	 * Matches the current suggestions endpoint, its predecessor, and any future
	 * rename that keeps the trailing segment. The loose trailing match is
	 * deliberate: a false positive costs a few unlogged searches, while a false
	 * negative lets typeahead traffic swamp the log, which is the failure this
	 * exclusion exists to prevent (DESIGN.md 5.1). Whether suggestion requests
	 * reach this code at all is EC-2.
	 */
	private boolean _isSuggestionPath(String path) {
		if (path.startsWith(_SUGGESTIONS_PATH) ||
			path.startsWith(_LEGACY_SUGGESTIONS_PATH)) {

			return true;
		}

		return path.endsWith("/suggestions");
	}

	private static final String _HEADLESS_PATH_PREFIX = "/o/";

	private static final String _LEGACY_SUGGESTIONS_PATH =
		"/o/portal-search-rest/v1.0/suggestions";

	private static final String _SUGGESTIONS_PATH =
		"/o/search/v1.0/suggestions";


	private static final Log _log = LogFactoryUtil.getLog(
		SearchRequestOriginResolver.class);

}
