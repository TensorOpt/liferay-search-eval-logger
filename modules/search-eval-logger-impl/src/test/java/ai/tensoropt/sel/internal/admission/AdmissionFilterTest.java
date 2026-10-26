/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.admission;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.search.searcher.SearchRequest;

import ai.tensoropt.sel.api.SourceType;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.TestInjection;
import ai.tensoropt.sel.internal.context.SearchRequestOrigin;
import ai.tensoropt.sel.internal.context.SearchRequestOriginResolver;
import ai.tensoropt.sel.internal.statistics.SearchEvalLoggerStatisticsImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * An administrative search is a keyword search that is never admitted, so it
 * counts toward the funnel's keyword searches and not its admitted ones, which
 * is how every other dropped search is counted (TO-141).
 */
public class AdmissionFilterTest {

	@BeforeEach
	public void setUp() throws Exception {
		when(_configuration.samplingRate()).thenReturn(1.0);

		_searchContext.setKeywords("annual report");

		TestInjection.set(
			_admissionFilter, "_searchEvalLoggerStatisticsImpl", _statistics);
		TestInjection.set(
			_admissionFilter, "_searchRequestOriginResolver", _resolver);
	}

	@Test
	public void anAdministrativeSearchIsDropped() {
		when(
			_resolver.resolve()
		).thenReturn(
			new SearchRequestOrigin(SourceType.HEADLESS, false, true, true)
		);

		assertNull(_admit());

		verify(_statistics).incrementKeywordSearchCount();
		verify(_statistics, never()).incrementAdmittedSearchCount();
	}

	@Test
	public void anAdministrativeSearchIsDroppedWhateverTheSettings() {
		when(_configuration.excludeSuggestionTraffic()).thenReturn(false);
		when(_configuration.requireWebRequestContext()).thenReturn(false);

		when(
			_resolver.resolve()
		).thenReturn(
			new SearchRequestOrigin(SourceType.HEADLESS, false, true, true)
		);

		assertNull(_admit());
	}

	@Test
	public void aHeadlessUserSearchIsStillAdmitted() {
		SearchRequestOrigin searchRequestOrigin = new SearchRequestOrigin(
			SourceType.HEADLESS, false, false, true);

		when(_configuration.excludeSuggestionTraffic()).thenReturn(true);
		when(_resolver.resolve()).thenReturn(searchRequestOrigin);

		assertSame(searchRequestOrigin, _admit());

		verify(_statistics).incrementAdmittedSearchCount();
	}

	private SearchRequestOrigin _admit() {
		return _admissionFilter.admit(
			_searchContext, mock(SearchRequest.class), _configuration);
	}

	private final AdmissionFilter _admissionFilter = new AdmissionFilter();
	private final SearchEvalLoggerConfiguration _configuration = mock(
		SearchEvalLoggerConfiguration.class);
	private final SearchRequestOriginResolver _resolver = mock(
		SearchRequestOriginResolver.class);
	private final SearchContext _searchContext = new SearchContext();
	private final SearchEvalLoggerStatisticsImpl _statistics = mock(
		SearchEvalLoggerStatisticsImpl.class);

}
