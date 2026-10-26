/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.admission;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.search.searcher.SearchRequest;

import ai.tensoropt.sel.api.SourceType;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.TestInjection;
import ai.tensoropt.sel.internal.capture.FacetCapture;
import ai.tensoropt.sel.internal.capture.FacetExtractor;
import ai.tensoropt.sel.internal.context.SearchRequestOrigin;
import ai.tensoropt.sel.internal.context.SearchRequestOriginResolver;
import ai.tensoropt.sel.internal.statistics.SearchEvalLoggerStatisticsImpl;

import java.util.Collections;

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

		when(
			_resolver.resolve()
		).thenReturn(
			new SearchRequestOrigin(SourceType.WIDGET, false, false, true)
		);

		_searchContext.setKeywords("annual report");

		TestInjection.set(
			_admissionFilter, "_searchEvalLoggerStatisticsImpl", _statistics);
		TestInjection.set(
			_admissionFilter, "_searchRequestOriginResolver", _resolver);
		TestInjection.set(_admissionFilter, "_facetExtractor", _facetExtractor);
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

	/**
	 * F2-5: condition 1's strongest case, a search with no keywords at all,
	 * dropped before the origin is even resolved, and not counted as a
	 * keyword search.
	 */
	@Test
	public void nullKeywordsAreDroppedWithNoKeywordCount() {
		_searchContext.setKeywords(null);

		assertNull(_admit());

		verify(_statistics, never()).incrementKeywordSearchCount();
		verify(_resolver, never()).resolve();
	}

	/**
	 * Validator.isNull treats a whitespace only string the same as empty: a
	 * search of only spaces has nothing a person actually typed.
	 */
	@Test
	public void whitespaceKeywordsAreDroppedWithNoKeywordCount() {
		_searchContext.setKeywords("   ");

		assertNull(_admit());

		verify(_statistics, never()).incrementKeywordSearchCount();
	}

	/**
	 * Facet only admission is off by default; a keyword free, facet bearing
	 * search must still be dropped.
	 */
	@Test
	public void facetOnlyAdmissionOffDropsAKeywordFreeSearch() {
		_searchContext.setKeywords(null);

		when(_configuration.admitFacetOnlySearches()).thenReturn(false);

		assertNull(_admit());

		verify(_facetExtractor, never()).extract(any(), any());
	}

	@Test
	public void facetOnlyAdmissionOnWithCapturedSelectionsIsAdmitted() {
		_searchContext.setKeywords(null);

		when(_configuration.admitFacetOnlySearches()).thenReturn(true);
		when(
			_facetExtractor.extract(_searchContext, null)
		).thenReturn(
			FacetCapture.captured("{\"category\":[\"HR\"]}")
		);

		assertNotNull(_admit());

		verify(_statistics).incrementKeywordSearchCount();
	}

	@Test
	public void facetOnlyAdmissionOnWithNoneAppliedIsDropped() {
		_searchContext.setKeywords(null);

		when(_configuration.admitFacetOnlySearches()).thenReturn(true);
		when(
			_facetExtractor.extract(_searchContext, null)
		).thenReturn(
			FacetCapture.NONE_APPLIED
		);

		assertNull(_admit());
	}

	@Test
	public void facetOnlyAdmissionOnWithUnavailableIsDropped() {
		_searchContext.setKeywords(null);

		when(_configuration.admitFacetOnlySearches()).thenReturn(true);
		when(
			_facetExtractor.extract(_searchContext, null)
		).thenReturn(
			FacetCapture.UNAVAILABLE
		);

		assertNull(_admit());
	}

	/**
	 * requireWebRequestContext tightens condition 2 (EC-10): an origin that
	 * could not be resolved at all must not be treated as a web request.
	 */
	@Test
	public void requireWebRequestContextDropsAnUnknownOrigin() {
		when(_configuration.requireWebRequestContext()).thenReturn(true);
		when(_resolver.resolve()).thenReturn(SearchRequestOrigin.UNKNOWN);

		assertNull(_admit());
	}

	/**
	 * DESIGN.md 5.1: absence of context is a reason to drop, not an error,
	 * when suggestion exclusion is on. Admitting instead would readmit
	 * exactly the typeahead traffic this exclusion exists to keep out.
	 */
	@Test
	public void excludeSuggestionTrafficDropsASearchWithNoWebRequest()
		throws Exception {

		when(_configuration.excludeSuggestionTraffic()).thenReturn(true);
		when(
			_resolver.resolve()
		).thenReturn(
			new SearchRequestOrigin(SourceType.OTHER, false, false, false)
		);

		assertNull(_admit());
	}

	@Test
	public void aSuggestionIsDroppedWhenExclusionIsOn() {
		when(_configuration.excludeSuggestionTraffic()).thenReturn(true);
		when(
			_resolver.resolve()
		).thenReturn(
			new SearchRequestOrigin(SourceType.HEADLESS, true, false, true)
		);

		assertNull(_admit());
	}

	@Test
	public void aSuggestionIsAdmittedWhenExclusionIsOff() {
		when(_configuration.excludeSuggestionTraffic()).thenReturn(false);

		SearchRequestOrigin searchRequestOrigin = new SearchRequestOrigin(
			SourceType.HEADLESS, true, false, true);

		when(_resolver.resolve()).thenReturn(searchRequestOrigin);

		assertSame(searchRequestOrigin, _admit());
	}

	/**
	 * Condition 3: an excluded type named on the request's own list, which
	 * wins over the legacy search context's.
	 */
	@Test
	public void anExcludedEntryClassOnTheRequestIsDropped() {
		when(
			_configuration.excludedEntryClassNames()
		).thenReturn(
			new String[] {"com.example.Excluded"}
		);

		SearchRequest searchRequest = mock(SearchRequest.class);

		when(
			searchRequest.getEntryClassNames()
		).thenReturn(
			Collections.singletonList("com.example.Excluded")
		);

		assertNull(_admit(searchRequest));
	}

	/**
	 * The same exclusion, read from the legacy search context, for a
	 * request whose own list is empty.
	 */
	@Test
	public void anExcludedEntryClassOnTheContextFallbackIsDropped() {
		when(
			_configuration.excludedEntryClassNames()
		).thenReturn(
			new String[] {"com.example.Excluded"}
		);

		_searchContext.setEntryClassNames(
			new String[] {"com.example.Excluded"});

		SearchRequest searchRequest = mock(SearchRequest.class);

		when(
			searchRequest.getEntryClassNames()
		).thenReturn(
			Collections.emptyList()
		);

		assertNull(_admit(searchRequest));
	}

	@Test
	public void noExcludedEntryClassNamesConfiguredAdmits() {
		when(
			_configuration.excludedEntryClassNames()
		).thenReturn(
			new String[0]
		);

		assertNotNull(_admit());
	}

	@Test
	public void aNullExcludedEntryClassNamesArrayAdmits() {
		when(_configuration.excludedEntryClassNames()).thenReturn(null);

		assertNotNull(_admit());
	}

	@Test
	public void anEmptyExcludedEntryClassNamesListOnTheRequestAdmits() {
		when(
			_configuration.excludedEntryClassNames()
		).thenReturn(
			new String[] {"com.example.Excluded"}
		);

		SearchRequest searchRequest = mock(SearchRequest.class);

		when(
			searchRequest.getEntryClassNames()
		).thenReturn(
			Collections.emptyList()
		);

		assertNotNull(_admit(searchRequest));
	}

	/**
	 * The admitted counter is the last thing incremented, and only when
	 * every condition passed; a search that fails the sampling draw after
	 * every earlier condition already passed must not be counted as
	 * admitted.
	 */
	@Test
	public void aSamplingRateOfZeroDropsWithNoAdmittedCount() {
		when(_configuration.samplingRate()).thenReturn(0.0);

		assertNull(_admit());

		verify(_statistics, never()).incrementAdmittedSearchCount();
	}

	@Test
	public void aSamplingRateOfOneAlwaysAdmits() {
		when(_configuration.samplingRate()).thenReturn(1.0);

		assertNotNull(_admit());
	}

	/**
	 * samplingRate is clamped by the same two guards that handle 0.0 and
	 * 1.0 exactly: anything at or above 1 behaves as 1, anything at or
	 * below 0 behaves as 0, with no separate clamping step to drift out of
	 * step with them.
	 */
	@Test
	public void anOutOfRangeSamplingRateIsClamped() {
		when(_configuration.samplingRate()).thenReturn(1.5);

		assertNotNull(_admit());

		when(_configuration.samplingRate()).thenReturn(-0.5);

		assertNull(_admit());
	}

	private SearchRequestOrigin _admit() {
		return _admit(mock(SearchRequest.class));
	}

	private SearchRequestOrigin _admit(SearchRequest searchRequest) {
		return _admissionFilter.admit(
			_searchContext, searchRequest, _configuration);
	}

	private final AdmissionFilter _admissionFilter = new AdmissionFilter();
	private final SearchEvalLoggerConfiguration _configuration = mock(
		SearchEvalLoggerConfiguration.class);
	private final FacetExtractor _facetExtractor = mock(FacetExtractor.class);
	private final SearchRequestOriginResolver _resolver = mock(
		SearchRequestOriginResolver.class);
	private final SearchContext _searchContext = new SearchContext();
	private final SearchEvalLoggerStatisticsImpl _statistics = mock(
		SearchEvalLoggerStatisticsImpl.class);

}
