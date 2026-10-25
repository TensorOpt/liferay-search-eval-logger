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

	private SearchRequestOrigin _resolve(String requestURI) {
		HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

		when(httpServletRequest.getRequestURI()).thenReturn(requestURI);

		ServiceContext serviceContext = new ServiceContext();

		serviceContext.setRequest(httpServletRequest);

		ServiceContextThreadLocal.pushServiceContext(serviceContext);

		return new SearchRequestOriginResolver().resolve();
	}

}
