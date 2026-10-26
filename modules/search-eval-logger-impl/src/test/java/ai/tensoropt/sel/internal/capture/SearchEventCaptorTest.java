/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.capture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.search.document.Document;
import com.liferay.portal.search.highlight.HighlightField;
import com.liferay.portal.search.hits.SearchHit;
import com.liferay.portal.search.hits.SearchHits;
import com.liferay.portal.search.searcher.SearchRequest;
import com.liferay.portal.search.searcher.SearchResponse;

import ai.tensoropt.sel.api.FacetCaptureStatus;
import ai.tensoropt.sel.api.SourceType;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.TestInjection;
import ai.tensoropt.sel.internal.context.SearchRequestOrigin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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

		TestInjection.set(_searchEventCaptor, "_facetExtractor", facetExtractor);

		// Backed by real maps and lists rather than Liferay's own
		// JSONFactoryImpl, which needs the portal's property bootstrap
		// (PropsUtil) just to construct. The snippet tests assert the
		// grouping this class is responsible for; serializing that structure
		// into actual JSON text is JSONObjectImpl's contract, not this
		// class's, and is not re-tested here.

		_createdJSONObjects = new ArrayList<>();

		_jsonFactory = mock(JSONFactory.class);

		when(
			_jsonFactory.createJSONObject()
		).thenAnswer(
			invocation -> {
				JSONObject jsonObject = _newJSONObject();

				_createdJSONObjects.add(jsonObject);

				return jsonObject;
			}
		);
		when(
			_jsonFactory.createJSONArray()
		).thenAnswer(
			invocation -> _newJSONArray()
		);

		TestInjection.set(_searchEventCaptor, "_jsonFactory", _jsonFactory);

		_language = mock(Language.class);

		when(
			_language.getCompanyAvailableLocales(anyLong())
		).thenReturn(
			Set.of(
				new Locale("en", "US"), new Locale("sv", "SE"),
				new Locale("fi", "FI"))
		);
		when(
			_language.getLanguageId(any(Locale.class))
		).thenAnswer(
			invocation -> invocation.<Locale>getArgument(0).toString()
		);

		TestInjection.set(_searchEventCaptor, "_language", _language);

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
	 * TO-139: a blueprint named by external reference code, Liferay's
	 * preferred way, and one named by the older ID are each recorded in their
	 * own field, and both when a request carries both.
	 */
	@Test
	public void theBlueprintIsRecordedByExternalReferenceCodeAndById() {
		SearchContext byExternalReferenceCode = new SearchContext();

		byExternalReferenceCode.setAttribute(
			"search.experiences.blueprint.external.reference.code",
			"product-search");

		CapturedSearchEvent capturedSearchEvent = _capture(
			"Title", byExternalReferenceCode);

		assertEquals(
			"product-search",
			capturedSearchEvent.getBlueprintExternalReferenceCode());
		assertNull(capturedSearchEvent.getBlueprintId());

		SearchContext byId = new SearchContext();

		byId.setAttribute("search.experiences.blueprint.id", "38417");

		capturedSearchEvent = _capture("Title", byId);

		assertNull(capturedSearchEvent.getBlueprintExternalReferenceCode());
		assertEquals("38417", capturedSearchEvent.getBlueprintId());

		SearchContext byBoth = new SearchContext();

		byBoth.setAttribute(
			"search.experiences.blueprint.external.reference.code",
			"x".repeat(100));
		byBoth.setAttribute("search.experiences.blueprint.id", "38417");

		capturedSearchEvent = _capture("Title", byBoth);

		assertEquals(
			"x".repeat(75),
			capturedSearchEvent.getBlueprintExternalReferenceCode());
		assertEquals("38417", capturedSearchEvent.getBlueprintId());
	}

	/**
	 * TO-133: ranks are stored from 1 and absolute across pages, so the top
	 * result of page 1 is 1 and the top result of a page that starts at 20
	 * is 21.
	 */
	@Test
	public void ranksAreStoredFromOneAndAbsoluteAcrossPages() {
		CapturedSearchEvent firstPage = _capture(
			"Title", new SearchContext(), mock(SearchRequest.class));

		assertEquals(1, _firstHit(firstPage).getRank());

		SearchRequest secondPageRequest = mock(SearchRequest.class);

		when(secondPageRequest.getFrom()).thenReturn(20);

		CapturedSearchEvent secondPage = _capture(
			"Title", new SearchContext(), secondPageRequest);

		assertEquals(20, secondPage.getRequestedFrom());
		assertEquals(21, _firstHit(secondPage).getRank());
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

	/**
	 * The ticket's own example (TO-115): fragments for the same field in
	 * three locales, plus one field with no locale suffix at all, must come
	 * back grouped by locale then field, never joined.
	 */
	@Test
	public void highlightFragmentsAreGroupedByLocaleThenField() {
		_captureSnippet(
			new String[] {"snippet"},
			document -> {},
			searchHit -> {
				Map<String, HighlightField> highlightFieldsMap =
					_highlightFieldsMap(
						"content_sv_SE",
						List.of(
							"Välkommen till <liferay-hl>Liferay</liferay-hl>"),
						"content_fi_FI",
						List.of(
							"<liferay-hl>Liferay</liferay-hl> toivottaa " +
								"tervetulleeksi"),
						"content_en_US",
						List.of("Welcome to <liferay-hl>Liferay</liferay-hl>"),
						"title_en_US", List.of("Liferay"),
						"assetTagNames", List.of("portal"));

				when(
					searchHit.getHighlightFieldsMap()
				).thenReturn(
					highlightFieldsMap
				);
			}
		);

		JSONObject snippet = _snippetJSONObject();

		assertEquals(4, snippet.length());
		assertEquals(
			"Välkommen till <liferay-hl>Liferay</liferay-hl>",
			snippet.getJSONObject("sv_SE").getJSONArray("content").getString(0));
		assertEquals(
			1, snippet.getJSONObject("sv_SE").getJSONArray("content").length());
		assertEquals(
			"<liferay-hl>Liferay</liferay-hl> toivottaa tervetulleeksi",
			snippet.getJSONObject("fi_FI").getJSONArray("content").getString(0));
		assertEquals(2, snippet.getJSONObject("en_US").length());
		assertEquals(
			"Welcome to <liferay-hl>Liferay</liferay-hl>",
			snippet.getJSONObject("en_US").getJSONArray("content").getString(0));
		assertEquals(
			"Liferay",
			snippet.getJSONObject("en_US").getJSONArray("title").getString(0));
		assertEquals(
			"portal",
			snippet.getJSONObject(
				"_default"
			).getJSONArray(
				"assetTagNames"
			).getString(0));
	}

	/**
	 * A DDM structured content field name carries underscores of its own
	 * before the locale suffix. Only the recognized locale id at the end may
	 * be split off.
	 */
	@Test
	public void aDdmFieldNameKeepsItsOwnUnderscoresInTheFieldName() {
		_captureSnippet(
			new String[] {"snippet"},
			document -> {},
			searchHit -> {
				Map<String, HighlightField> highlightFieldsMap =
					_highlightFieldsMap(
						"ddm__text__35202__content_en_US",
						List.of("fragment"));

				when(
					searchHit.getHighlightFieldsMap()
				).thenReturn(
					highlightFieldsMap
				);
			}
		);

		JSONObject snippet = _snippetJSONObject();

		assertEquals(
			"fragment",
			snippet.getJSONObject(
				"en_US"
			).getJSONArray(
				"ddm__text__35202__content"
			).getString(0));
	}

	/**
	 * Multiple fragments on the same field are kept as an array in the order
	 * Liferay returned them, never joined into one string.
	 */
	@Test
	public void multipleFragmentsOnOneFieldStayAnArray() {
		_captureSnippet(
			new String[] {"snippet"},
			document -> {},
			searchHit -> {
				Map<String, HighlightField> highlightFieldsMap =
					_highlightFieldsMap(
						"content_en_US",
						List.of("first fragment", "second fragment"));

				when(
					searchHit.getHighlightFieldsMap()
				).thenReturn(
					highlightFieldsMap
				);
			}
		);

		JSONObject snippet = _snippetJSONObject();

		assertEquals(
			2, snippet.getJSONObject("en_US").getJSONArray("content").length());
		assertEquals(
			"first fragment",
			snippet.getJSONObject("en_US").getJSONArray("content").getString(0));
		assertEquals(
			"second fragment",
			snippet.getJSONObject("en_US").getJSONArray("content").getString(1));
	}

	/**
	 * EC-4's document-embedded fallback, first branch: a plain
	 * <code>snippet</code> field with no locale information at all stays
	 * under <code>_default</code>.
	 */
	@Test
	public void aPlainDocumentSnippetFieldFallsBackUnderDefault() {
		_captureSnippet(
			new String[] {"snippet"},
			document -> when(
				document.getString("snippet")
			).thenReturn(
				"a document-embedded snippet"
			),
			searchHit -> {}
		);

		JSONObject snippet = _snippetJSONObject();

		assertEquals(1, snippet.length());
		assertEquals(
			"a document-embedded snippet",
			snippet.getJSONObject("_default").getJSONArray("snippet").getString(0));
	}

	/**
	 * EC-4's document-embedded fallback: a <code>snippet_&lt;locale&gt;</code>
	 * field is recognised as that whole locale, not run through the generic
	 * per-field suffix detection, because its inner name is itself one of the
	 * company's language ids.
	 */
	@Test
	public void aLocalizedDocumentSnippetFieldCarriesItsOwnLocale() {
		SearchContext searchContext = new SearchContext();

		searchContext.setLocale(new Locale("sv", "SE"));

		_captureSnippet(
			searchContext, new String[] {"snippet"},
			document -> _stubPrefixedSnippetField(
				document, "snippet_sv_SE", "ett dokumentbäddat utdrag"),
			searchHit -> {}
		);

		JSONObject snippet = _snippetJSONObject();

		assertEquals(1, snippet.length());
		assertEquals(
			"ett dokumentbäddat utdrag",
			snippet.getJSONObject("sv_SE").getJSONArray("snippet").getString(0));
	}

	/**
	 * TO-115 review (finding 3): the shape of a <code>snippet_&lt;locale&gt;
	 * </code> field must not depend on which locale the search itself
	 * happens to be in. Searched in <code>en_US</code>, a document carrying
	 * <code>snippet_sv_SE</code> used to fall past the (removed) explicit
	 * probe for the search's own language id, reach the generic per-field
	 * loop, and have "sv_SE" misread as a plain field name with no locale,
	 * landing under <code>_default</code> instead of <code>sv_SE</code>.
	 */
	@Test
	public void aSnippetLocaleFieldIsRecognisedRegardlessOfTheSearchsOwnLocale() {
		SearchContext searchContext = new SearchContext();

		searchContext.setLocale(new Locale("en", "US"));

		_captureSnippet(
			searchContext, new String[] {"snippet"},
			document -> _stubPrefixedSnippetField(
				document, "snippet_sv_SE", "ett dokumentbäddat utdrag"),
			searchHit -> {}
		);

		JSONObject snippet = _snippetJSONObject();

		assertEquals(1, snippet.length());
		assertEquals(
			"ett dokumentbäddat utdrag",
			snippet.getJSONObject("sv_SE").getJSONArray("snippet").getString(0));
	}

	/**
	 * EC-4's document-embedded fallback, third branch: every
	 * <code>snippet_&lt;field&gt;</code> field is collected, not just the
	 * first one found, and the inner name is itself run through locale
	 * detection.
	 */
	@Test
	public void everyPrefixedDocumentSnippetFieldIsCollected() {
		_captureSnippet(
			new String[] {"snippet"},
			document -> {
				Map<String, com.liferay.portal.search.document.Field> fields =
					new LinkedHashMap<>();

				fields.put(
					"snippet_description",
					mock(com.liferay.portal.search.document.Field.class));
				fields.put(
					"snippet_content_en_US",
					mock(com.liferay.portal.search.document.Field.class));

				when(document.getFields()).thenReturn(fields);
				when(
					document.getString("snippet_description")
				).thenReturn(
					"a plain description snippet"
				);
				when(
					document.getString("snippet_content_en_US")
				).thenReturn(
					"a localized content snippet"
				);
			},
			searchHit -> {}
		);

		JSONObject snippet = _snippetJSONObject();

		assertEquals(2, snippet.length());
		assertEquals(
			"a plain description snippet",
			snippet.getJSONObject(
				"_default"
			).getJSONArray(
				"description"
			).getString(0));
		assertEquals(
			"a localized content snippet",
			snippet.getJSONObject("en_US").getJSONArray("content").getString(0));
	}

	/**
	 * No highlights and no document-embedded fallback: the column stays
	 * null, exactly as it did before TO-115, because per-field coverage
	 * counting depends on that.
	 */
	@Test
	public void noHighlightsLeavesTheSnippetNull() {
		CapturedSearchHit capturedSearchHit = _captureSnippet(
			new String[] {"snippet"}, document -> {}, searchHit -> {});

		assertNull(capturedSearchHit.getSnippet());
	}

	/**
	 * When the snippet is not in the configured field list at all, the
	 * installation's locales are never resolved: every other field is read
	 * as named, with no need for them.
	 */
	@Test
	public void theInstallationLocalesAreNotResolvedWhenSnippetIsNotCaptured() {
		_captureSnippet(
			new String[] {"title"},
			document -> {},
			searchHit -> {
				Map<String, HighlightField> highlightFieldsMap =
					_highlightFieldsMap(
						"content_en_US", List.of("fragment"));

				when(
					searchHit.getHighlightFieldsMap()
				).thenReturn(
					highlightFieldsMap
				);
			}
		);

		verify(_language, never()).getCompanyAvailableLocales(anyLong());
	}

	/**
	 * F2-7: capture depth is min(K, hits returned) (D4), never more than
	 * either side offers.
	 */
	@Test
	public void captureDepthBoundsHowManyOfTheHitsAreCaptured() {
		assertEquals(2, _captureHits(3, 2).getHits().size());
		assertEquals(0, _captureHits(3, 0).getHits().size());
		assertEquals(3, _captureHits(3, 10).getHits().size());
	}

	/**
	 * A negative captureDepth is clamped to zero rather than throwing or
	 * being read as "no limit".
	 */
	@Test
	public void aNegativeCaptureDepthCapturesNothing() {
		assertEquals(0, _captureHits(3, -5).getHits().size());
	}

	@Test
	public void queryTextExactlyAtTheCapIsNotTruncated() {
		when(_configuration.queryTextCap()).thenReturn(10);

		SearchContext searchContext = new SearchContext();

		searchContext.setKeywords("0123456789");

		CapturedSearchEvent capturedSearchEvent = _captureQuery(searchContext);

		assertEquals("0123456789", capturedSearchEvent.getQueryText());
		assertFalse(capturedSearchEvent.isQueryTruncated());
	}

	@Test
	public void queryTextOneOverTheCapIsTruncatedAndFlagged() {
		when(_configuration.queryTextCap()).thenReturn(10);

		SearchContext searchContext = new SearchContext();

		searchContext.setKeywords("01234567890");

		CapturedSearchEvent capturedSearchEvent = _captureQuery(searchContext);

		assertEquals("0123456789", capturedSearchEvent.getQueryText());
		assertTrue(capturedSearchEvent.isQueryTruncated());
	}

	/**
	 * The configured cap is itself clamped to the column width, so a
	 * configuration asking for more than the column can hold still
	 * produces a row that fits rather than a SQL error.
	 */
	@Test
	public void aQueryTextCapAboveTheColumnWidthIsClampedToIt() {
		when(_configuration.queryTextCap()).thenReturn(5000);

		SearchContext searchContext = new SearchContext();

		searchContext.setKeywords("x".repeat(2500));

		CapturedSearchEvent capturedSearchEvent = _captureQuery(searchContext);

		assertEquals(2000, capturedSearchEvent.getQueryText().length());
		assertTrue(capturedSearchEvent.isQueryTruncated());
	}

	/**
	 * A configured cap of zero or less is clamped to one rather than
	 * capturing nothing at all.
	 */
	@Test
	public void aQueryTextCapOfZeroIsClampedToOne() {
		when(_configuration.queryTextCap()).thenReturn(0);

		SearchContext searchContext = new SearchContext();

		searchContext.setKeywords("annual leave");

		CapturedSearchEvent capturedSearchEvent = _captureQuery(searchContext);

		assertEquals("a", capturedSearchEvent.getQueryText());
		assertTrue(capturedSearchEvent.isQueryTruncated());
	}

	@Test
	public void queryTextIsTrimmedBeforeTheCapIsApplied() {
		SearchContext searchContext = new SearchContext();

		searchContext.setKeywords("  annual leave  ");

		CapturedSearchEvent capturedSearchEvent = _captureQuery(searchContext);

		assertEquals("annual leave", capturedSearchEvent.getQueryText());
	}

	@Test
	public void aNullHitListCapturesNoHitsWithoutThrowing() {
		SearchContext searchContext = new SearchContext();

		searchContext.setKeywords("annual leave");

		SearchHits searchHits = mock(SearchHits.class);

		when(searchHits.getSearchHits()).thenReturn(null);

		SearchResponse searchResponse = mock(SearchResponse.class);

		when(searchResponse.getSearchHits()).thenReturn(searchHits);

		CapturedSearchEvent capturedSearchEvent = _searchEventCaptor.capture(
			mock(SearchRequest.class), searchResponse, searchContext,
			_configuration,
			new SearchRequestOrigin(SourceType.WIDGET, false, false, true));

		assertTrue(capturedSearchEvent.getHits().isEmpty());
	}

	/**
	 * A null element in the hits list is skipped, and the real hit after
	 * it keeps the rank its own position earns, rather than being
	 * renumbered as though the null were not there.
	 */
	@Test
	public void aNullHitElementIsSkippedWithAbsoluteRanksPreserved() {
		SearchContext searchContext = new SearchContext();

		searchContext.setKeywords("annual leave");

		Document document = mock(Document.class);

		when(document.getString(anyString())).thenReturn(null);

		SearchHit realHit = mock(SearchHit.class);

		when(realHit.getDocument()).thenReturn(document);

		SearchHits searchHits = mock(SearchHits.class);

		when(
			searchHits.getSearchHits()
		).thenReturn(
			Arrays.asList(null, realHit)
		);

		SearchResponse searchResponse = mock(SearchResponse.class);

		when(searchResponse.getSearchHits()).thenReturn(searchHits);

		CapturedSearchEvent capturedSearchEvent = _searchEventCaptor.capture(
			mock(SearchRequest.class), searchResponse, searchContext,
			_configuration,
			new SearchRequestOrigin(SourceType.WIDGET, false, false, true));

		assertEquals(1, capturedSearchEvent.getHits().size());
		assertEquals(2, _firstHit(capturedSearchEvent).getRank());
	}

	/**
	 * A hit with no document at all still carries its rank and score;
	 * every other field stays unset rather than the capture failing.
	 */
	@Test
	public void aHitWithNoDocumentCapturesOnlyRankAndScore() {
		SearchContext searchContext = new SearchContext();

		searchContext.setKeywords("annual leave");

		SearchHit hit = mock(SearchHit.class);

		when(hit.getScore()).thenReturn(4.5f);
		when(hit.getDocument()).thenReturn(null);

		SearchHits searchHits = mock(SearchHits.class);

		when(searchHits.getSearchHits()).thenReturn(List.of(hit));

		SearchResponse searchResponse = mock(SearchResponse.class);

		when(searchResponse.getSearchHits()).thenReturn(searchHits);

		CapturedSearchEvent capturedSearchEvent = _searchEventCaptor.capture(
			mock(SearchRequest.class), searchResponse, searchContext,
			_configuration,
			new SearchRequestOrigin(SourceType.WIDGET, false, false, true));

		CapturedSearchHit capturedSearchHit = _firstHit(capturedSearchEvent);

		assertEquals(1, capturedSearchHit.getRank());
		assertEquals(4.5, capturedSearchHit.getScore());
		assertNull(capturedSearchHit.getDocUid());
		assertNull(capturedSearchHit.getTitle());
	}

	/**
	 * The search context's own languageId wins over its Locale; a
	 * language id longer than the column (20 characters) is truncated the
	 * same way every other captured string is. A mocked SearchContext,
	 * not a real one, since the real class derives getLanguageId() from
	 * its own Locale field and the two can never disagree on it; this
	 * isolates the fallback _getLanguageId itself implements.
	 */
	@Test
	public void theLanguageIdWinsOverLocaleAndIsTruncatedAtTwentyCharacters() {
		SearchContext searchContext = mock(SearchContext.class);

		when(searchContext.getKeywords()).thenReturn("annual leave");
		when(
			searchContext.getLanguageId()
		).thenReturn(
			"en_US_avariantlongenoughtooverflow"
		);
		when(searchContext.getLocale()).thenReturn(new Locale("sv", "SE"));

		CapturedSearchEvent capturedSearchEvent = _capture(
			"Title", searchContext);

		assertEquals(20, capturedSearchEvent.getLocale().length());
		assertEquals(
			"en_US_avariantlongen", capturedSearchEvent.getLocale());
	}

	/**
	 * With no languageId at all, the Locale's own string form is used
	 * instead.
	 */
	@Test
	public void theLocaleIsUsedWhenNoLanguageIdIsSet() {
		SearchContext searchContext = mock(SearchContext.class);

		when(searchContext.getKeywords()).thenReturn("annual leave");
		when(searchContext.getLanguageId()).thenReturn(null);
		when(searchContext.getLocale()).thenReturn(new Locale("sv", "SE"));

		CapturedSearchEvent capturedSearchEvent = _capture(
			"Title", searchContext);

		assertEquals("sv_SE", capturedSearchEvent.getLocale());
	}

	@Test
	public void aDocUidOverTheColumnWidthIsTruncatedAtFiveHundred() {
		CapturedSearchHit capturedSearchHit = _captureDocumentField(
			Field.UID, "x".repeat(600));

		assertEquals(500, capturedSearchHit.getDocUid().length());
	}

	@Test
	public void anEntryClassNameOverTheColumnWidthIsTruncatedAtTwoHundred() {
		CapturedSearchHit capturedSearchHit = _captureDocumentField(
			Field.ENTRY_CLASS_NAME, "x".repeat(300));

		assertEquals(200, capturedSearchHit.getEntryClassName().length());
	}

	@Test
	public void aBlueprintIdOverTheColumnWidthIsTruncatedAtOneHundred() {
		SearchContext searchContext = new SearchContext();

		searchContext.setAttribute(
			"search.experiences.blueprint.id", "x".repeat(150));

		CapturedSearchEvent capturedSearchEvent = _capture(
			"Title", searchContext);

		assertEquals(100, capturedSearchEvent.getBlueprintId().length());
	}

	/**
	 * scopeGroupIds is the other comma separated column (TO-94): a long
	 * list is cut on a separator boundary, never mid identifier.
	 */
	@Test
	public void aLongScopeGroupIdsListIsCutOnASeparator() {
		long[] groupIds = new long[100];

		for (int i = 0; i < groupIds.length; i++) {
			groupIds[i] = 1000000000L + i;
		}

		SearchContext searchContext = new SearchContext();

		searchContext.setGroupIds(groupIds);

		String captured = _capture(
			"Title", searchContext
		).getScopeGroupIds();

		assertTrue(captured.length() <= 500);
		assertFalse(captured.endsWith(","));

		for (String groupId : captured.split(",")) {
			assertTrue(
				captured.contains(groupId),
				groupId + " is a fragment, not a captured group id");
		}
	}

	/**
	 * Only the configured, non-snippet, non-title fields land in
	 * extra_fields, read as named first and falling back to the search's
	 * own language id when the plain name is absent.
	 */
	@Test
	public void extraFieldsAreCapturedFromTheConfiguredSetWithLocalizedFallback() {
		when(
			_configuration.capturedFieldNames()
		).thenReturn(
			new String[] {"department", "location"}
		);

		SearchContext searchContext = new SearchContext();

		searchContext.setKeywords("annual leave");
		searchContext.setLocale(new Locale("en", "US"));

		Document document = mock(Document.class);

		when(document.getString(anyString())).thenReturn(null);
		when(document.getString(Field.UID)).thenReturn("uid");
		when(document.getString("department")).thenReturn("HR");
		when(document.getString("location_en_US")).thenReturn("Dublin");

		SearchHit searchHit = mock(SearchHit.class);

		when(searchHit.getDocument()).thenReturn(document);

		SearchHits searchHits = mock(SearchHits.class);

		when(searchHits.getSearchHits()).thenReturn(List.of(searchHit));

		SearchResponse searchResponse = mock(SearchResponse.class);

		when(searchResponse.getSearchHits()).thenReturn(searchHits);

		_searchEventCaptor.capture(
			mock(SearchRequest.class), searchResponse, searchContext,
			_configuration,
			new SearchRequestOrigin(SourceType.WIDGET, false, false, true));

		// Asserted against the mocked extra_fields JSONObject's own put
		// calls, the same way the snippet tests read their JSONObject
		// structure directly, rather than through JSONObject#toString(),
		// which this fake does not implement (see _snippetJSONObject).

		JSONObject extraFieldsJSONObject = _createdJSONObjects.get(0);

		verify(extraFieldsJSONObject).put("department", "HR");
		verify(extraFieldsJSONObject).put("location", "Dublin");
	}

	/**
	 * A configured field name is trimmed before it is read, matched, and
	 * used as the extra_fields key.
	 */
	@Test
	public void aConfiguredFieldNameIsTrimmedBeforeItIsRead() {
		when(
			_configuration.capturedFieldNames()
		).thenReturn(
			new String[] {"  department  "}
		);

		SearchContext searchContext = new SearchContext();

		searchContext.setKeywords("annual leave");

		Document document = mock(Document.class);

		when(document.getString(anyString())).thenReturn(null);
		when(document.getString(Field.UID)).thenReturn("uid");
		when(document.getString("department")).thenReturn("HR");

		SearchHit searchHit = mock(SearchHit.class);

		when(searchHit.getDocument()).thenReturn(document);

		SearchHits searchHits = mock(SearchHits.class);

		when(searchHits.getSearchHits()).thenReturn(List.of(searchHit));

		SearchResponse searchResponse = mock(SearchResponse.class);

		when(searchResponse.getSearchHits()).thenReturn(searchHits);

		_searchEventCaptor.capture(
			mock(SearchRequest.class), searchResponse, searchContext,
			_configuration,
			new SearchRequestOrigin(SourceType.WIDGET, false, false, true));

		JSONObject extraFieldsJSONObject = _createdJSONObjects.get(0);

		verify(extraFieldsJSONObject).put("department", "HR");
	}

	/**
	 * A blank or missing configured field name never reaches extra_fields
	 * at all: there is no key to put a value under.
	 */
	@Test
	public void blankOrMissingConfiguredFieldValuesAreSkipped() {
		when(
			_configuration.capturedFieldNames()
		).thenReturn(
			new String[] {"department", "location"}
		);

		SearchContext searchContext = new SearchContext();

		searchContext.setKeywords("annual leave");

		Document document = mock(Document.class);

		when(document.getString(anyString())).thenReturn(null);
		when(document.getString(Field.UID)).thenReturn("uid");
		when(document.getString("department")).thenReturn("");

		SearchHit searchHit = mock(SearchHit.class);

		when(searchHit.getDocument()).thenReturn(document);

		SearchHits searchHits = mock(SearchHits.class);

		when(searchHits.getSearchHits()).thenReturn(List.of(searchHit));

		SearchResponse searchResponse = mock(SearchResponse.class);

		when(searchResponse.getSearchHits()).thenReturn(searchHits);

		CapturedSearchEvent capturedSearchEvent = _searchEventCaptor.capture(
			mock(SearchRequest.class), searchResponse, searchContext,
			_configuration,
			new SearchRequestOrigin(SourceType.WIDGET, false, false, true));

		assertNull(_firstHit(capturedSearchEvent).getExtraFields());
	}

	/**
	 * Facet status, userId, companyId, sourceType and requestedSize are
	 * each propagated straight through onto the captured event.
	 */
	@Test
	public void facetStatusUserIdCompanyIdSourceTypeAndRequestedSizePropagate()
		throws Exception {
		FacetExtractor facetExtractor = mock(FacetExtractor.class);

		when(
			facetExtractor.extract(any(), any())
		).thenReturn(
			FacetCapture.captured("{\"category\":[\"HR\"]}")
		);

		TestInjection.set(_searchEventCaptor, "_facetExtractor", facetExtractor);

		SearchContext searchContext = new SearchContext();

		searchContext.setKeywords("annual leave");
		searchContext.setUserId(777L);
		searchContext.setCompanyId(888L);

		SearchRequest searchRequest = mock(SearchRequest.class);

		when(searchRequest.getSize()).thenReturn(25);

		SearchHits searchHits = mock(SearchHits.class);

		when(searchHits.getSearchHits()).thenReturn(List.of());

		SearchResponse searchResponse = mock(SearchResponse.class);

		when(searchResponse.getSearchHits()).thenReturn(searchHits);

		CapturedSearchEvent capturedSearchEvent = _searchEventCaptor.capture(
			searchRequest, searchResponse, searchContext, _configuration,
			new SearchRequestOrigin(SourceType.HEADLESS, false, false, true));

		assertEquals(
			FacetCaptureStatus.CAPTURED,
			capturedSearchEvent.getFacetCaptureStatus());
		assertEquals(777L, capturedSearchEvent.getUserId());
		assertEquals(888L, capturedSearchEvent.getCompanyId());
		assertEquals(SourceType.HEADLESS, capturedSearchEvent.getSourceType());
		assertEquals(25, capturedSearchEvent.getRequestedSize());
	}

	private CapturedSearchHit _captureDocumentField(
		String fieldName, String value) {

		SearchContext searchContext = new SearchContext();

		searchContext.setKeywords("annual leave");

		Document document = mock(Document.class);

		when(document.getString(anyString())).thenReturn(null);
		when(document.getString(Field.UID)).thenReturn("uid");
		when(document.getString(fieldName)).thenReturn(value);

		SearchHit searchHit = mock(SearchHit.class);

		when(searchHit.getDocument()).thenReturn(document);

		SearchHits searchHits = mock(SearchHits.class);

		when(searchHits.getSearchHits()).thenReturn(List.of(searchHit));

		SearchResponse searchResponse = mock(SearchResponse.class);

		when(searchResponse.getSearchHits()).thenReturn(searchHits);

		CapturedSearchEvent capturedSearchEvent = _searchEventCaptor.capture(
			mock(SearchRequest.class), searchResponse, searchContext,
			_configuration,
			new SearchRequestOrigin(SourceType.WIDGET, false, false, true));

		return _firstHit(capturedSearchEvent);
	}

	private CapturedSearchEvent _captureQuery(SearchContext searchContext) {
		SearchHits searchHits = mock(SearchHits.class);

		when(searchHits.getSearchHits()).thenReturn(List.of());

		SearchResponse searchResponse = mock(SearchResponse.class);

		when(searchResponse.getSearchHits()).thenReturn(searchHits);

		return _searchEventCaptor.capture(
			mock(SearchRequest.class), searchResponse, searchContext,
			_configuration,
			new SearchRequestOrigin(SourceType.WIDGET, false, false, true));
	}

	/**
	 * Builds hitCount hits, each with a document carrying only a uid, and
	 * captures them with captureDepth configured as given.
	 */
	private CapturedSearchEvent _captureHits(int hitCount, int captureDepth) {
		when(_configuration.captureDepth()).thenReturn(captureDepth);

		SearchContext searchContext = new SearchContext();

		searchContext.setKeywords("annual leave");

		List<SearchHit> hits = new ArrayList<>();

		for (int i = 0; i < hitCount; i++) {
			Document document = mock(Document.class);

			when(document.getString(anyString())).thenReturn(null);
			when(document.getString(Field.UID)).thenReturn("uid-" + i);

			SearchHit searchHit = mock(SearchHit.class);

			when(searchHit.getDocument()).thenReturn(document);

			hits.add(searchHit);
		}

		SearchHits searchHits = mock(SearchHits.class);

		when(searchHits.getSearchHits()).thenReturn(hits);

		SearchResponse searchResponse = mock(SearchResponse.class);

		when(searchResponse.getSearchHits()).thenReturn(searchHits);

		return _searchEventCaptor.capture(
			mock(SearchRequest.class), searchResponse, searchContext,
			_configuration,
			new SearchRequestOrigin(SourceType.WIDGET, false, false, true));
	}

	private Map<String, HighlightField> _highlightFieldsMap(
		Object... namesAndFragments) {

		Map<String, HighlightField> highlightFieldsMap = new LinkedHashMap<>();

		for (int i = 0; i < namesAndFragments.length; i += 2) {
			String name = (String)namesAndFragments[i];

			@SuppressWarnings("unchecked")
			List<String> fragments = (List<String>)namesAndFragments[i + 1];

			HighlightField highlightField = mock(HighlightField.class);

			when(highlightField.getFragments()).thenReturn(fragments);

			highlightFieldsMap.put(name, highlightField);
		}

		return highlightFieldsMap;
	}

	/**
	 * Stubs a single <code>snippet_&lt;something&gt;</code> document field,
	 * both in {@code getFields()} (where the production loop discovers the
	 * name) and in {@code getString(name)} (where it reads the value) -
	 * production reads a document-embedded snippet through both accessors,
	 * backed by the same field in a real {@code Document}, so a test double
	 * has to stub both rather than only the one a shortcut would read.
	 */
	private void _stubPrefixedSnippetField(
		Document document, String fieldName, String value) {

		Map<String, com.liferay.portal.search.document.Field> fields =
			new LinkedHashMap<>();

		fields.put(
			fieldName, mock(com.liferay.portal.search.document.Field.class));

		when(document.getFields()).thenReturn(fields);
		when(document.getString(fieldName)).thenReturn(value);
	}

	/**
	 * The root object built for the one hit the test captured. Tracked by
	 * creation order rather than parsed back out of {@link
	 * CapturedSearchHit#getSnippet()}: each test captures exactly one hit
	 * with <code>snippet</code> as the only configured field, so the root
	 * locales object is always the first {@link JSONObject} the factory
	 * created, and the real {@link JSONObject#toString()} this fake never
	 * implements is Liferay's contract to honour, not this class's.
	 */
	private JSONObject _snippetJSONObject() {
		assertTrue(
			!_createdJSONObjects.isEmpty(),
			"no JSONObject was built; the snippet was never captured");

		return _createdJSONObjects.get(0);
	}

	/**
	 * A minimal, real JSON rendering of a flat String-valued backing map,
	 * only ever used for extra_fields: that is the one case where
	 * {@code JSONObject#toString()}'s actual text is read back by
	 * production code ({@code CapturedSearchHit#setExtraFields}) rather
	 * than compared as a fake object, as the snippet assertions above do.
	 */
	private String _toJSONString(Map<String, Object> backing) {
		StringBuilder sb = new StringBuilder("{");
		boolean first = true;

		for (Map.Entry<String, Object> entry : backing.entrySet()) {
			if (!first) {
				sb.append(",");
			}

			first = false;

			sb.append("\"");
			sb.append(entry.getKey());
			sb.append("\":\"");
			sb.append(entry.getValue());
			sb.append("\"");
		}

		sb.append("}");

		return sb.toString();
	}

	private JSONObject _newJSONObject() {
		Map<String, Object> backing = new LinkedHashMap<>();

		JSONObject jsonObject = mock(JSONObject.class);

		when(
			jsonObject.put(anyString(), any(JSONObject.class))
		).thenAnswer(
			invocation -> {
				backing.put(invocation.getArgument(0), invocation.getArgument(1));

				return jsonObject;
			}
		);
		when(
			jsonObject.put(anyString(), any(JSONArray.class))
		).thenAnswer(
			invocation -> {
				backing.put(invocation.getArgument(0), invocation.getArgument(1));

				return jsonObject;
			}
		);

		// extra_fields puts a plain String value for each configured,
		// non-title, non-snippet field, rather than a nested JSONObject or
		// JSONArray; tracked in the same backing map so length() reflects
		// it, exactly as it does for the other two put overloads above.

		when(
			jsonObject.put(anyString(), any(String.class))
		).thenAnswer(
			invocation -> {
				backing.put(invocation.getArgument(0), invocation.getArgument(1));

				return jsonObject;
			}
		);
		when(
			jsonObject.getString(anyString())
		).thenAnswer(
			invocation -> backing.get(invocation.getArgument(0))
		);
		when(
			jsonObject.toString()
		).thenAnswer(
			invocation -> _toJSONString(backing)
		);
		when(
			jsonObject.getJSONObject(anyString())
		).thenAnswer(
			invocation -> backing.get(invocation.getArgument(0))
		);
		when(
			jsonObject.getJSONArray(anyString())
		).thenAnswer(
			invocation -> backing.get(invocation.getArgument(0))
		);
		when(
			jsonObject.length()
		).thenAnswer(
			invocation -> backing.size()
		);

		return jsonObject;
	}

	private JSONArray _newJSONArray() {
		List<Object> backing = new ArrayList<>();

		JSONArray jsonArray = mock(JSONArray.class);

		when(
			jsonArray.put(anyString())
		).thenAnswer(
			invocation -> {
				backing.add(invocation.getArgument(0));

				return jsonArray;
			}
		);
		when(
			jsonArray.getString(anyInt())
		).thenAnswer(
			invocation -> backing.get(invocation.<Integer>getArgument(0))
		);
		when(
			jsonArray.length()
		).thenAnswer(
			invocation -> backing.size()
		);

		return jsonArray;
	}

	private CapturedSearchHit _captureSnippet(
		String[] capturedFieldNames, Consumer<Document> documentConfigurer,
		Consumer<SearchHit> searchHitConfigurer) {

		return _captureSnippet(
			new SearchContext(), capturedFieldNames, documentConfigurer,
			searchHitConfigurer);
	}

	private CapturedSearchHit _captureSnippet(
		SearchContext searchContext, String[] capturedFieldNames,
		Consumer<Document> documentConfigurer,
		Consumer<SearchHit> searchHitConfigurer) {

		searchContext.setKeywords("annual leave");

		when(_configuration.capturedFieldNames()).thenReturn(capturedFieldNames);

		Document document = mock(Document.class);

		when(document.getString(anyString())).thenReturn(null);
		when(document.getString(Field.UID)).thenReturn("uid");

		documentConfigurer.accept(document);

		SearchHit searchHit = mock(SearchHit.class);

		when(searchHit.getDocument()).thenReturn(document);

		searchHitConfigurer.accept(searchHit);

		SearchHits searchHits = mock(SearchHits.class);

		when(searchHits.getSearchHits()).thenReturn(List.of(searchHit));

		SearchResponse searchResponse = mock(SearchResponse.class);

		when(searchResponse.getSearchHits()).thenReturn(searchHits);

		CapturedSearchEvent capturedSearchEvent = _searchEventCaptor.capture(
			mock(SearchRequest.class), searchResponse, searchContext,
			_configuration,
			new SearchRequestOrigin(SourceType.WIDGET, false, false, true));

		return capturedSearchEvent.getHits(
		).get(
			0
		);
	}

	private CapturedSearchEvent _capture(
		String title, SearchContext searchContext) {

		return _capture(title, searchContext, mock(SearchRequest.class));
	}

	private CapturedSearchEvent _capture(
		String title, SearchContext searchContext,
		SearchRequest searchRequest) {

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
			searchRequest, searchResponse, searchContext, _configuration,
			new SearchRequestOrigin(SourceType.WIDGET, false, false, true));
	}

	private CapturedSearchHit _firstHit(
		CapturedSearchEvent capturedSearchEvent) {

		List<CapturedSearchHit> capturedSearchHits =
			capturedSearchEvent.getHits();

		return capturedSearchHits.get(0);
	}

	private List<JSONObject> _createdJSONObjects;
	private SearchEvalLoggerConfiguration _configuration;
	private FacetExtractor _facetExtractor;
	private JSONFactory _jsonFactory;
	private Language _language;
	private final SearchEventCaptor _searchEventCaptor =
		new SearchEventCaptor();

}
