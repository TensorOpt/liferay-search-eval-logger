/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.searcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.search.searcher.SearchRequest;
import com.liferay.portal.search.searcher.SearchRequestBuilder;
import com.liferay.portal.search.searcher.SearchRequestBuilderFactory;
import com.liferay.portal.search.searcher.SearchResponse;
import com.liferay.portal.search.searcher.Searcher;

import ai.tensoropt.sel.api.SourceType;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.TestInjection;
import ai.tensoropt.sel.internal.admission.AdmissionFilter;
import ai.tensoropt.sel.internal.capture.SearchEventCaptor;
import ai.tensoropt.sel.internal.configuration.SearchEvalLoggerConfigurationRegistry;
import ai.tensoropt.sel.internal.context.SearchRequestOrigin;
import ai.tensoropt.sel.internal.messaging.SearchEventDispatcher;
import ai.tensoropt.sel.internal.statistics.SearchEvalLoggerStatisticsImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Once a search is admitted, losing it anywhere downstream has to show up as
 * a drop (TO-92). Otherwise the manifest reports a search as admitted that
 * was neither persisted nor dropped, and admitted = persisted + dropped stops
 * holding.
 */
public class LoggingSearcherTest {

	@BeforeEach
	public void setUp() throws Exception {
		SearchEvalLoggerConfiguration configuration = mock(
			SearchEvalLoggerConfiguration.class);

		when(configuration.enabled()).thenReturn(true);

		SearchEvalLoggerConfigurationRegistry registry = mock(
			SearchEvalLoggerConfigurationRegistry.class);

		when(registry.getConfiguration(anyLong())).thenReturn(configuration);

		SearchRequestBuilder searchRequestBuilder = mock(
			SearchRequestBuilder.class);

		when(
			searchRequestBuilder.withSearchContextGet(any())
		).thenReturn(
			new SearchContext()
		);

		SearchRequestBuilderFactory searchRequestBuilderFactory = mock(
			SearchRequestBuilderFactory.class);

		when(
			searchRequestBuilderFactory.builder(any(SearchRequest.class))
		).thenReturn(
			searchRequestBuilder
		);

		AdmissionFilter admissionFilter = mock(AdmissionFilter.class);

		when(
			admissionFilter.admit(any(), any(), any())
		).thenReturn(
			new SearchRequestOrigin(SourceType.WIDGET, false, false, true)
		);

		_searchResponse = mock(SearchResponse.class);

		Searcher searcher = mock(Searcher.class);

		when(searcher.search(any())).thenReturn(_searchResponse);

		_searchEventCaptor = mock(SearchEventCaptor.class);
		_searchEventDispatcher = mock(SearchEventDispatcher.class);
		_statistics = new SearchEvalLoggerStatisticsImpl();

		TestInjection.set(_loggingSearcher, "_admissionFilter", admissionFilter);
		TestInjection.set(_loggingSearcher, "_searcher", searcher);
		TestInjection.set(
			_loggingSearcher, "_searchEvalLoggerConfigurationRegistry",
			registry);
		TestInjection.set(
			_loggingSearcher, "_searchEvalLoggerStatisticsImpl", _statistics);
		TestInjection.set(
			_loggingSearcher, "_searchEventCaptor", _searchEventCaptor);
		TestInjection.set(
			_loggingSearcher, "_searchEventDispatcher",
			_searchEventDispatcher);
		TestInjection.set(
			_loggingSearcher, "_searchRequestBuilderFactory",
			searchRequestBuilderFactory);
	}

	@Test
	public void aCaptureThatFailsAfterAdmissionIsDropped() {
		when(
			_searchEventCaptor.capture(any(), any(), any(), any(), any())
		).thenThrow(
			new IllegalStateException("capture failed")
		);

		SearchResponse searchResponse = _loggingSearcher.search(
			mock(SearchRequest.class));

		assertSame(
			_searchResponse, searchResponse,
			"A logging failure must never reach the search");
		assertEquals(1, _statistics.getDroppedEventCount());

		verify(_searchEventDispatcher, never()).dispatch(any());
	}

	@Test
	public void aSuccessfulCaptureIsHandedToTheDispatcher() {
		_loggingSearcher.search(mock(SearchRequest.class));

		assertEquals(0, _statistics.getDroppedEventCount());

		verify(_searchEventDispatcher).dispatch(any());
	}

	private final LoggingSearcher _loggingSearcher = new LoggingSearcher();
	private SearchEventCaptor _searchEventCaptor;
	private SearchEventDispatcher _searchEventDispatcher;
	private SearchResponse _searchResponse;
	private SearchEvalLoggerStatisticsImpl _statistics;

}
