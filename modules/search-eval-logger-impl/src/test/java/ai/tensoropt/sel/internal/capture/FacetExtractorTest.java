/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.capture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.search.facet.Facet;

import ai.tensoropt.sel.api.FacetCaptureStatus;
import ai.tensoropt.sel.api.SourceType;
import ai.tensoropt.sel.internal.TestInjection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * DESIGN.md 3.2 and EC-12: an empty facet field must not be able to mean both
 * "the user applied none" and "facets could not be read", and the status this
 * class returns is what keeps the two apart. Each case below is one way the
 * probe order (new-API selections, then the request attribute a facet's own
 * query building reads, then the entry's own key when the facet declares no
 * field name) can go wrong.
 */
public class FacetExtractorTest {

	@BeforeEach
	public void setUp() throws Exception {
		_facetExtractor = new FacetExtractor();

		TestInjection.set(_facetExtractor, "_jsonFactory", _newJSONFactory());
	}

	@Test
	public void nullFacetsOnAWidgetRequestAreNoneApplied() {
		SearchContext searchContext = mock(SearchContext.class);

		when(searchContext.getFacets()).thenReturn(null);

		FacetCapture facetCapture = _facetExtractor.extract(
			searchContext, SourceType.WIDGET);

		assertSame(FacetCaptureStatus.NONE_APPLIED, facetCapture.getStatus());
	}

	@Test
	public void nullFacetsOnAHeadlessRequestAreUnavailable() {
		SearchContext searchContext = mock(SearchContext.class);

		when(searchContext.getFacets()).thenReturn(null);

		FacetCapture facetCapture = _facetExtractor.extract(
			searchContext, SourceType.HEADLESS);

		assertSame(FacetCaptureStatus.UNAVAILABLE, facetCapture.getStatus());
	}

	@Test
	public void nullFacetsWithNoSourceTypeAreUnavailable() {
		SearchContext searchContext = mock(SearchContext.class);

		when(searchContext.getFacets()).thenReturn(null);

		FacetCapture facetCapture = _facetExtractor.extract(
			searchContext, null);

		assertSame(FacetCaptureStatus.UNAVAILABLE, facetCapture.getStatus());
	}

	/**
	 * The newer facet API's own selections win over the request attribute
	 * fallback, and are enough on their own to capture.
	 */
	@Test
	public void newApiSelectionsAreCaptured() {
		com.liferay.portal.search.facet.Facet facet = mock(
			com.liferay.portal.search.facet.Facet.class);

		when(facet.getFieldName()).thenReturn("category");
		when(facet.getSelections()).thenReturn(new String[] {"a", "b"});

		SearchContext searchContext = mock(SearchContext.class);

		when(
			searchContext.getFacets()
		).thenReturn(
			Map.<String, Facet>of("category", facet)
		);

		FacetCapture facetCapture = _facetExtractor.extract(
			searchContext, SourceType.WIDGET);

		assertSame(FacetCaptureStatus.CAPTURED, facetCapture.getStatus());
		assertEquals(
			"{category=[a, b]}", facetCapture.getAppliedFacets());
	}

	/**
	 * A facet that only implements the older API falls back to the request
	 * attribute keyed by its field id, comma split, which is how the facet's
	 * own query building reads the same selections.
	 */
	@Test
	public void legacyFacetReadsTheRequestAttributeCommaSplit() {
		Facet facet = mock(Facet.class);

		when(facet.getFieldName()).thenReturn("category");
		when(facet.getFieldId()).thenReturn("categoryId");

		SearchContext searchContext = mock(SearchContext.class);

		when(
			searchContext.getFacets()
		).thenReturn(
			Map.of("category", facet)
		);
		when(searchContext.getAttribute("categoryId")).thenReturn("a,b");

		FacetCapture facetCapture = _facetExtractor.extract(
			searchContext, SourceType.WIDGET);

		assertSame(FacetCaptureStatus.CAPTURED, facetCapture.getStatus());
		assertEquals(
			"{category=[a, b]}", facetCapture.getAppliedFacets());
	}

	/**
	 * A facet declaring no field name is filed under the map entry's own key
	 * instead, rather than under a blank name.
	 */
	@Test
	public void aBlankFieldNameFallsBackToTheEntryKey() {
		com.liferay.portal.search.facet.Facet facet = mock(
			com.liferay.portal.search.facet.Facet.class);

		when(facet.getFieldName()).thenReturn("");
		when(facet.getSelections()).thenReturn(new String[] {"a"});

		SearchContext searchContext = mock(SearchContext.class);

		when(
			searchContext.getFacets()
		).thenReturn(
			Map.<String, Facet>of("entryKey", facet)
		);

		FacetCapture facetCapture = _facetExtractor.extract(
			searchContext, SourceType.WIDGET);

		assertSame(FacetCaptureStatus.CAPTURED, facetCapture.getStatus());
		assertEquals(
			"{entryKey=[a]}", facetCapture.getAppliedFacets());
	}

	/**
	 * A blank selection is dropped rather than put, and a facet whose only
	 * selection is blank leaves nothing behind to capture, so the field
	 * never appears and the whole extraction is not CAPTURED.
	 *
	 * <p>
	 * Kept apart from the null case below: a selections array holding both
	 * "" and null still asserts NONE_APPLIED even when only one of the two
	 * is actually filtered (whichever one is still skipped is enough to
	 * leave nothing behind), so a single combined test cannot tell the two
	 * apart or catch either one's filtering being broken on its own.
	 * </p>
	 */
	@Test
	public void aBlankSelectionIsSkippedAndNotCaptured() {
		com.liferay.portal.search.facet.Facet facet = mock(
			com.liferay.portal.search.facet.Facet.class);

		when(facet.getFieldName()).thenReturn("category");
		when(facet.getSelections()).thenReturn(new String[] {""});

		SearchContext searchContext = mock(SearchContext.class);

		when(
			searchContext.getFacets()
		).thenReturn(
			Map.<String, Facet>of("category", facet)
		);

		FacetCapture facetCapture = _facetExtractor.extract(
			searchContext, SourceType.WIDGET);

		assertSame(FacetCaptureStatus.NONE_APPLIED, facetCapture.getStatus());
	}

	/**
	 * A null selection is dropped rather than put, on its own, with no
	 * blank selection alongside it to leave nothing behind on its own
	 * account. See the blank case above for why the two are not combined.
	 */
	@Test
	public void aNullSelectionIsSkippedAndNotCaptured() {
		com.liferay.portal.search.facet.Facet facet = mock(
			com.liferay.portal.search.facet.Facet.class);

		when(facet.getFieldName()).thenReturn("category");
		when(
			facet.getSelections()
		).thenReturn(
			new String[] {null}
		);

		SearchContext searchContext = mock(SearchContext.class);

		when(
			searchContext.getFacets()
		).thenReturn(
			Map.<String, Facet>of("category", facet)
		);

		FacetCapture facetCapture = _facetExtractor.extract(
			searchContext, SourceType.WIDGET);

		assertSame(FacetCaptureStatus.NONE_APPLIED, facetCapture.getStatus());
	}

	/**
	 * extract() is wrapped in a try/catch(Throwable) specifically so that a
	 * facet implementation that throws cannot break the search it would
	 * otherwise only be describing. getFieldId() is the first call every
	 * facet that is not the newer API reaches unconditionally, so it is the
	 * one stubbed to throw here.
	 */
	@Test
	public void aThrowingFacetIsUnavailable() {
		Facet facet = mock(Facet.class);

		when(facet.getFieldId()).thenThrow(new RuntimeException("boom"));

		SearchContext searchContext = mock(SearchContext.class);

		when(
			searchContext.getFacets()
		).thenReturn(
			Map.of("category", facet)
		);

		FacetCapture facetCapture = _facetExtractor.extract(
			searchContext, SourceType.WIDGET);

		assertSame(FacetCaptureStatus.UNAVAILABLE, facetCapture.getStatus());
	}

	/**
	 * Backed by real maps and lists rather than Liferay's own
	 * JSONFactoryImpl, which needs the portal's property bootstrap
	 * (PropsUtil) just to construct, the same approach
	 * SearchEventCaptorTest uses for the same reason. The grouping this
	 * class is responsible for is asserted through the backing map's own
	 * deterministic toString(); serializing that structure into actual JSON
	 * text is JSONObjectImpl's contract, not this class's.
	 */
	private JSONFactory _newJSONFactory() {
		JSONFactory jsonFactory = mock(JSONFactory.class);

		when(
			jsonFactory.createJSONObject()
		).thenAnswer(
			invocation -> _newJSONObject()
		);
		when(
			jsonFactory.createJSONArray()
		).thenAnswer(
			invocation -> _newJSONArray()
		);

		return jsonFactory;
	}

	private JSONObject _newJSONObject() {
		Map<String, Object> backing = new LinkedHashMap<>();

		JSONObject jsonObject = mock(JSONObject.class);

		when(
			jsonObject.put(anyString(), any(JSONArray.class))
		).thenAnswer(
			invocation -> {
				backing.put(invocation.getArgument(0), invocation.getArgument(1));

				return jsonObject;
			}
		);
		when(
			jsonObject.length()
		).thenAnswer(
			invocation -> backing.size()
		);
		when(
			jsonObject.toString()
		).thenAnswer(
			invocation -> backing.toString()
		);

		return jsonObject;
	}

	private JSONArray _newJSONArray() {
		List<String> backing = new ArrayList<>();

		JSONArray jsonArray = mock(JSONArray.class);

		// nullable, not anyString: a selection array can hold a null
		// element (aNullSelectionIsSkippedAndNotCaptured), and that case
		// only discriminates production's own null filtering if a null put
		// actually reaches this backing list rather than being invisibly
		// absorbed by an unmatched mock call.

		when(
			jsonArray.put(nullable(String.class))
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
		when(
			jsonArray.toString()
		).thenAnswer(
			invocation -> backing.toString()
		);

		return jsonArray;
	}

	private FacetExtractor _facetExtractor;

}
