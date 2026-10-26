/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.context;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.WebKeys;

import ai.tensoropt.sel.api.SourceType;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The Blueprint editor's Preview searches through the Search Experiences REST
 * API, so that API is classified as administrative, while the headless Search
 * API a Blueprint-driven user search goes through is not (TO-141).
 */
public class SearchRequestOriginResolverTest {

	@AfterEach
	public void tearDown() {
		ServiceContextThreadLocal.popServiceContext();
	}

	@Test
	public void theSearchExperiencesApiIsAdministrative() {
		SearchRequestOrigin searchRequestOrigin = _resolve(
			"/o/search-experiences-rest/v1.0/search");

		assertSame(SourceType.HEADLESS, searchRequestOrigin.getSourceType());
		assertTrue(searchRequestOrigin.isAdministrative());
		assertFalse(searchRequestOrigin.isSuggestion());
	}

	@Test
	public void theHeadlessSearchApiIsNotAdministrative() {
		SearchRequestOrigin searchRequestOrigin = _resolve(
			"/o/search/v1.0/search");

		assertSame(SourceType.HEADLESS, searchRequestOrigin.getSourceType());
		assertFalse(searchRequestOrigin.isAdministrative());
		assertFalse(searchRequestOrigin.isSuggestion());
	}

	@Test
	public void suggestionsAreClassifiedAsBefore() {
		SearchRequestOrigin searchRequestOrigin = _resolve(
			"/o/search/v1.0/suggestions");

		assertTrue(searchRequestOrigin.isSuggestion());
		assertFalse(searchRequestOrigin.isAdministrative());
	}

	/**
	 * The predecessor endpoint TO-115 still has to classify as a suggestion,
	 * not just the current one, through its own explicit prefix match
	 * rather than only the generic trailing-segment fallback below: the
	 * plain legacy path ends in "/suggestions" too, so a path that starts
	 * with the legacy prefix but does not end in "/suggestions" is what
	 * actually isolates the prefix check; deleting it would fail only this
	 * case, not theLegacySuggestionsPathIsClassifiedAsASuggestion's old,
	 * exact-path form.
	 */
	@Test
	public void theLegacySuggestionsPathIsClassifiedAsASuggestion() {
		SearchRequestOrigin searchRequestOrigin = _resolve(
			"/o/portal-search-rest/v1.0/suggestions/with-trailing-segment");

		assertTrue(searchRequestOrigin.isSuggestion());
	}

	/**
	 * A loose trailing match, deliberately: it also catches a future rename
	 * that keeps the trailing segment.
	 */
	@Test
	public void aPathMerelyEndingInSuggestionsIsClassifiedAsASuggestion() {
		SearchRequestOrigin searchRequestOrigin = _resolve(
			"/o/some-future-search-api/suggestions");

		assertTrue(searchRequestOrigin.isSuggestion());
	}

	/**
	 * DESIGN.md 3.2: a search reached from a background thread, with nothing
	 * ever pushed onto the ThreadLocal, has no request to classify.
	 */
	@Test
	public void noServiceContextAtAllResolvesToUnknown() {
		SearchRequestOrigin searchRequestOrigin =
			new SearchRequestOriginResolver().resolve();

		assertSame(SearchRequestOrigin.UNKNOWN, searchRequestOrigin);
	}

	/**
	 * A request is present, but its own URI is unreadable. That is still a
	 * web request, just not one this resolver can classify any further than
	 * OTHER.
	 */
	@Test
	public void aNullRequestURIResolvesToOtherWithAWebRequestPresent() {
		HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

		when(httpServletRequest.getRequestURI()).thenReturn(null);

		SearchRequestOrigin searchRequestOrigin = _resolve(httpServletRequest);

		assertSame(SourceType.OTHER, searchRequestOrigin.getSourceType());
		assertTrue(searchRequestOrigin.isWebRequestPresent());
		assertFalse(searchRequestOrigin.isSuggestion());
		assertFalse(searchRequestOrigin.isAdministrative());
	}

	/**
	 * The context path is part of the deployment, not part of the path this
	 * resolver classifies against; it has to be stripped before the headless
	 * prefix is matched.
	 */
	@Test
	public void theContextPathIsStrippedBeforeClassifying() {
		HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

		when(
			httpServletRequest.getRequestURI()
		).thenReturn(
			"/mycontext/o/search/v1.0/search"
		);
		when(httpServletRequest.getContextPath()).thenReturn("/mycontext");

		SearchRequestOrigin searchRequestOrigin = _resolve(httpServletRequest);

		assertSame(SourceType.HEADLESS, searchRequestOrigin.getSourceType());
	}

	/**
	 * A non-headless path carrying a THEME_DISPLAY attribute is a portlet
	 * rendering a widget.
	 */
	@Test
	public void aThemeDisplayAttributeIsClassifiedAsAWidget() {
		HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

		when(
			httpServletRequest.getRequestURI()
		).thenReturn(
			"/web/guest/home"
		);
		when(
			httpServletRequest.getAttribute(WebKeys.THEME_DISPLAY)
		).thenReturn(
			mock(ThemeDisplay.class)
		);

		SearchRequestOrigin searchRequestOrigin = _resolve(httpServletRequest);

		assertSame(SourceType.WIDGET, searchRequestOrigin.getSourceType());
	}

	/**
	 * A non-headless path with no THEME_DISPLAY but a PORTLET_ID attribute is
	 * also a portlet request, just one that has not reached the point where
	 * THEME_DISPLAY is set yet.
	 */
	@Test
	public void aPortletIdAttributeAloneIsClassifiedAsAWidget() {
		HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

		when(
			httpServletRequest.getRequestURI()
		).thenReturn(
			"/web/guest/home"
		);
		when(
			httpServletRequest.getAttribute(WebKeys.PORTLET_ID)
		).thenReturn(
			"com_liferay_search_eval_logger"
		);

		SearchRequestOrigin searchRequestOrigin = _resolve(httpServletRequest);

		assertSame(SourceType.WIDGET, searchRequestOrigin.getSourceType());
	}

	/**
	 * Neither a headless path nor any portlet attribute: the fallback
	 * bucket, still a web request.
	 */
	@Test
	public void neitherHeadlessNorPortletIsClassifiedAsOther() {
		HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

		when(
			httpServletRequest.getRequestURI()
		).thenReturn(
			"/web/guest/home"
		);

		SearchRequestOrigin searchRequestOrigin = _resolve(httpServletRequest);

		assertSame(SourceType.OTHER, searchRequestOrigin.getSourceType());
		assertTrue(searchRequestOrigin.isWebRequestPresent());
	}

	/**
	 * resolve() wraps the whole resolution in a try/catch(Throwable)
	 * specifically so a request implementation that throws cannot break the
	 * search it would otherwise only be describing.
	 */
	@Test
	public void aThrowingRequestResolvesToUnknown() {
		HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

		when(
			httpServletRequest.getRequestURI()
		).thenThrow(
			new RuntimeException("boom")
		);

		SearchRequestOrigin searchRequestOrigin = _resolve(httpServletRequest);

		assertSame(SearchRequestOrigin.UNKNOWN, searchRequestOrigin);
	}

	private SearchRequestOrigin _resolve(HttpServletRequest httpServletRequest) {
		ServiceContext serviceContext = new ServiceContext();

		serviceContext.setRequest(httpServletRequest);

		ServiceContextThreadLocal.pushServiceContext(serviceContext);

		return new SearchRequestOriginResolver().resolve();
	}

	private SearchRequestOrigin _resolve(String requestURI) {
		HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

		when(httpServletRequest.getRequestURI()).thenReturn(requestURI);

		ServiceContext serviceContext = new ServiceContext();

		serviceContext.setRequest(httpServletRequest);

		ServiceContextThreadLocal.pushServiceContext(serviceContext);

		return new SearchRequestOriginResolver().resolve();
	}

}
