/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.internal.context;

import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.WebKeys;

import com.tensoropt.search.eval.logger.api.SourceType;

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

}
