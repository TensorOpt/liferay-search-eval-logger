/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.search.document.Document;
import com.liferay.portal.search.hits.SearchHit;
import com.liferay.portal.search.hits.SearchHits;
import com.liferay.portal.search.searcher.SearchRequest;
import com.liferay.portal.search.searcher.SearchResponse;

import ai.tensoropt.sel.api.SourceType;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.context.SearchRequestOrigin;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every captured string is cut to its column width before it is persisted.
 * Only the two comma separated columns may cut back to a separator (TO-94):
 * applied to a title, that cut discarded everything after the last comma.
 */
public class SearchEventCaptorTest {

	@BeforeEach
	public void setUp() throws Exception {
		FacetExtractor facetExtractor = mock(FacetExtractor.class);

		when(
			facetExtractor.extract(any(), any())
		).thenReturn(
			FacetCapture.NONE_APPLIED
		);

		java.lang.reflect.Field field =
			SearchEventCaptor.class.getDeclaredField("_facetExtractor");

		field.setAccessible(true);

		field.set(_searchEventCaptor, facetExtractor);

		_configuration = mock(SearchEvalLoggerConfiguration.class);

		when(_configuration.captureDepth()).thenReturn(10);
		when(
			_configuration.capturedFieldNames()
		).thenReturn(
			new String[] {"title"}
		);
		when(_configuration.queryTextCap()).thenReturn(2000);
	}

	/**
	 * A title over the 1,000 character column with a comma near the start.
	 * The pre-TO-94 cut kept "Annual leave" and dropped the rest.
	 */
	@Test
	public void aLongTitleIsCutAtTheColumnWidthNotAtItsLastComma() {
		String title = "Annual leave, " + "x".repeat(1200);

		CapturedSearchEvent capturedSearchEvent = _capture(
			title, new SearchContext());

		CapturedSearchHit capturedSearchHit = capturedSearchEvent.getHits(
		).get(
			0
		);

		assertEquals(title.substring(0, 1000), capturedSearchHit.getTitle());
	}

	/**
	 * The list columns still cut back to a separator, so the stored list
	 * never ends in a fragment that reads as a whole class name.
	 */
	@Test
	public void aLongEntryClassNameListIsCutOnASeparator() {
		List<String> entryClassNames = new ArrayList<>();

		for (int i = 0; i < 100; i++) {
			entryClassNames.add("com.example.model.EntryClassName" + i);
		}

		SearchContext searchContext = new SearchContext();

		searchContext.setEntryClassNames(
			entryClassNames.toArray(new String[0]));

		String captured = _capture(
			"title", searchContext
		).getEntryClassNames();

		assertTrue(captured.length() <= 2000);

		for (String entryClassName : captured.split(",")) {
			assertTrue(
				entryClassNames.contains(entryClassName),
				entryClassName + " is a fragment, not a captured class name");
		}
	}

	private CapturedSearchEvent _capture(
		String title, SearchContext searchContext) {

		searchContext.setKeywords("annual leave");

		Document document = mock(Document.class);

		when(document.getString(anyString())).thenReturn(null);
		when(document.getString("title")).thenReturn(title);
		when(document.getString(Field.UID)).thenReturn("uid");

		SearchHit searchHit = mock(SearchHit.class);

		when(searchHit.getDocument()).thenReturn(document);

		SearchHits searchHits = mock(SearchHits.class);

		when(searchHits.getSearchHits()).thenReturn(List.of(searchHit));

		SearchResponse searchResponse = mock(SearchResponse.class);

		when(searchResponse.getSearchHits()).thenReturn(searchHits);

		return _searchEventCaptor.capture(
			mock(SearchRequest.class), searchResponse, searchContext,
			_configuration,
			new SearchRequestOrigin(SourceType.WIDGET, false, true));
	}

	private SearchEvalLoggerConfiguration _configuration;
	private final SearchEventCaptor _searchEventCaptor =
		new SearchEventCaptor();

}
