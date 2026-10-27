/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.searcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.security.auth.CompanyThreadLocal;
import com.liferay.portal.kernel.util.Props;
import com.liferay.portal.kernel.util.PropsUtil;
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Once a search is admitted, losing it anywhere downstream has to show up as
 * a drop (TO-92). Otherwise the manifest reports a search as admitted that
 * was neither persisted nor dropped, and admitted = persisted + dropped stops
 * holding.
 */
public class LoggingSearcherTest {

	/**
	 * Setting a non-default company id, as several cases below do, makes
	 * <code>CompanyThreadLocal.setCompanyId</code> sync the thread's own
	 * database partition state, which reads <code>PropsUtil</code>. A unit
	 * test has none, the same gap
	 * {@link SearchEvalLoggerConfigurationRegistry}'s own test fills the same
	 * way, so this fills it too rather than depending on test execution order
	 * to pick up another class's own fix.
	 */
	@BeforeAll
	public static void setUpClass() {
		PropsUtil.setProps(mock(Props.class));
	}

	@BeforeEach
	public void setUp() throws Exception {
		CompanyThreadLocal.setCompanyId(0L);

		_configuration = mock(SearchEvalLoggerConfiguration.class);

		when(_configuration.enabled()).thenReturn(true);

		_registry = mock(SearchEvalLoggerConfigurationRegistry.class);

		when(_registry.getConfiguration(anyLong())).thenReturn(_configuration);

		_searchContext = new SearchContext();

		SearchRequestBuilder searchRequestBuilder = mock(
			SearchRequestBuilder.class);

		when(
			searchRequestBuilder.withSearchContextGet(any())
		).thenReturn(
			_searchContext
		);

		_searchRequestBuilderFactory = mock(SearchRequestBuilderFactory.class);

		when(
			_searchRequestBuilderFactory.builder(any(SearchRequest.class))
		).thenReturn(
			searchRequestBuilder
		);

		_admissionFilter = mock(AdmissionFilter.class);

		when(
			_admissionFilter.admit(any(), any(), any())
		).thenReturn(
			new SearchRequestOrigin(SourceType.WIDGET, false, false, true)
		);

		_searchResponse = mock(SearchResponse.class);

		_searcher = mock(Searcher.class);

		when(_searcher.search(any())).thenReturn(_searchResponse);

		_searchEventCaptor = mock(SearchEventCaptor.class);
		_searchEventDispatcher = mock(SearchEventDispatcher.class);
		_statistics = new SearchEvalLoggerStatisticsImpl();

		TestInjection.set(
			_loggingSearcher, "_admissionFilter", _admissionFilter);
		TestInjection.set(_loggingSearcher, "_searcher", _searcher);
		TestInjection.set(
			_loggingSearcher, "_searchEvalLoggerConfigurationRegistry",
			_registry);
		TestInjection.set(
			_loggingSearcher, "_searchEvalLoggerStatisticsImpl", _statistics);
		TestInjection.set(
			_loggingSearcher, "_searchEventCaptor", _searchEventCaptor);
		TestInjection.set(
			_loggingSearcher, "_searchEventDispatcher",
			_searchEventDispatcher);
		TestInjection.set(
			_loggingSearcher, "_searchRequestBuilderFactory",
			_searchRequestBuilderFactory);
	}

	@AfterEach
	public void tearDown() {
		CompanyThreadLocal.setCompanyId(0L);
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

	/**
	 * F2-9: the cheapest possible exit for the install default. Nothing is
	 * observed, and the admission filter is never reached, let alone called.
	 *
	 * <p>
	 * The search context's company is set equal to the thread's, so that
	 * deleting the early disabled-return would not be masked by the later
	 * re-check (which only re-queries for a search context on a different
	 * company): with the companies equal, deleting the early return falls
	 * straight through to observing and admitting the search.
	 * </p>
	 */
	@Test
	public void disabledLoggingSkipsObservedCountAndNeverCallsAdmit() {
		CompanyThreadLocal.setCompanyId(42L);

		_searchContext.setCompanyId(42L);

		when(_configuration.enabled()).thenReturn(false);

		SearchResponse searchResponse = _loggingSearcher.search(
			mock(SearchRequest.class));

		assertSame(_searchResponse, searchResponse);
		assertEquals(0, _statistics.getObservedSearchCount());

		verify(_admissionFilter, never()).admit(any(), any(), any());
	}

	/**
	 * _getSearchContext wraps the builder call and returns null on any
	 * failure; the response still comes back, and nothing downstream of
	 * the search context is ever counted.
	 */
	@Test
	public void aBuilderFailureStillReturnsTheResponseAndCountsNothing() {
		when(
			_searchRequestBuilderFactory.builder(any(SearchRequest.class))
		).thenThrow(
			new IllegalStateException("builder failed")
		);

		SearchResponse searchResponse = _loggingSearcher.search(
			mock(SearchRequest.class));

		assertSame(_searchResponse, searchResponse);
		assertEquals(0, _statistics.getObservedSearchCount());

		verify(_admissionFilter, never()).admit(any(), any(), any());
	}

	/**
	 * The thread's company settles the cheap early exit; a search context
	 * for a different company (a background job running one company's
	 * reindex while serving another's request, for instance) has to be
	 * re-checked against its own configuration rather than the thread's.
	 *
	 * <p>
	 * Company 222's configuration is a distinct, disabled mock (Mockito's
	 * default for an unstubbed boolean), rather than the thread's own
	 * enabled one: if the re-check reused company 111's configuration
	 * instead of actually querying the registry again for 222, admit would
	 * still be called, which this would catch.
	 * </p>
	 */
	@Test
	public void aDifferentSearchContextCompanyReQueriesTheRegistry() {
		CompanyThreadLocal.setCompanyId(111L);

		_searchContext.setCompanyId(222L);

		SearchEvalLoggerConfiguration disabledConfiguration = mock(
			SearchEvalLoggerConfiguration.class);

		when(_registry.getConfiguration(111L)).thenReturn(_configuration);
		when(
			_registry.getConfiguration(222L)
		).thenReturn(
			disabledConfiguration
		);

		_loggingSearcher.search(mock(SearchRequest.class));

		verify(_registry).getConfiguration(111L);
		verify(_registry).getConfiguration(222L);
		verify(_admissionFilter, never()).admit(any(), any(), any());
	}

	/**
	 * No thread company at all (CompanyThreadLocal never set, so it reads
	 * as 0L) skips the early exit entirely rather than reading a stale or
	 * default company; the search context's own company is what gets
	 * checked.
	 *
	 * <p>
	 * Company 0L is never queried: {@code setUp} stubs
	 * {@code getConfiguration(anyLong())} to return the enabled
	 * configuration for any argument, so a {@code threadCompanyId > 0}
	 * weakened to {@code >= 0} would still let the search through, but
	 * only by also querying the registry for company 0L on the way, which
	 * this catches.
	 * </p>
	 */
	@Test
	public void noThreadCompanyUsesTheSearchContextsOwnCompany() {
		_searchContext.setCompanyId(333L);

		when(_registry.getConfiguration(333L)).thenReturn(_configuration);

		_loggingSearcher.search(mock(SearchRequest.class));

		verify(_registry).getConfiguration(333L);
		verify(_registry, never()).getConfiguration(0L);
		verify(_admissionFilter).admit(any(), any(), any());
	}

	@Test
	public void anUnadmittedSearchNeverReachesTheCaptor() {
		when(
			_admissionFilter.admit(any(), any(), any())
		).thenReturn(
			null
		);

		SearchResponse searchResponse = _loggingSearcher.search(
			mock(SearchRequest.class));

		assertSame(_searchResponse, searchResponse);

		verify(_searchEventCaptor, never()).capture(
			any(), any(), any(), any(), any());
		verify(_searchEventDispatcher, never()).dispatch(any());
	}

	/**
	 * D2 and the class's own contract: only the delegate's own exceptions
	 * propagate. A capture failure is caught (see
	 * aCaptureThatFailsAfterAdmissionIsDropped); the delegate's is not.
	 */
	@Test
	public void aDelegateFailurePropagates() {
		when(
			_searcher.search(any())
		).thenThrow(
			new IllegalStateException("search failed")
		);

		assertThrows(
			IllegalStateException.class,
			() -> _loggingSearcher.search(mock(SearchRequest.class)));
	}

	private AdmissionFilter _admissionFilter;
	private SearchEvalLoggerConfiguration _configuration;
	private final LoggingSearcher _loggingSearcher = new LoggingSearcher();
	private SearchEvalLoggerConfigurationRegistry _registry;
	private Searcher _searcher;
	private SearchContext _searchContext;
	private SearchEventCaptor _searchEventCaptor;
	private SearchEventDispatcher _searchEventDispatcher;
	private SearchRequestBuilderFactory _searchRequestBuilderFactory;
	private SearchResponse _searchResponse;
	private SearchEvalLoggerStatisticsImpl _statistics;

}
