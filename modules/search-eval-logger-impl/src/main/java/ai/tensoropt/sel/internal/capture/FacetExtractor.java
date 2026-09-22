/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.capture;

import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.search.facet.Facet;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import ai.tensoropt.sel.api.SourceType;

import java.io.Serializable;

import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Reads the facet and filter selections active on a request (DESIGN.md 3.2,
 * EC-12).
 *
 * <p>
 * The probe order follows the design doc: the facets on the
 * <code>SearchContext</code> first, then the raw request state some UI
 * components leave in its attributes. The attribute probe is keyed by the field
 * IDs the facets themselves declare rather than by scanning every attribute,
 * because a blind scan would sweep unrelated internal state into a column an
 * evaluator is supposed to be able to read.
 * </p>
 *
 * <p>
 * The status returned matters as much as the payload. An empty facet field can
 * mean the user applied none or that facets were applied and could not be read,
 * and those two are not interchangeable (DESIGN.md 3.2). So the distinction is
 * drawn by what is knowable, not by what is convenient:
 * </p>
 *
 * <ul>
 * <li>Selections found: <code>CAPTURED</code>.</li>
 * <li>No selections found on a widget request: <code>NONE_APPLIED</code>. A
 * widget path puts user selections on the search context, so their absence is
 * evidence of absence.</li>
 * <li>No selections found anywhere else, or extraction failed:
 * <code>UNAVAILABLE</code>. On headless and Blueprint-driven paths, selections
 * may already have been translated into query clauses before the request was
 * finalized, so an empty facet map proves nothing. EC-12 accepts that outcome
 * for v1 rather than reporting a false <code>NONE_APPLIED</code>.</li>
 * </ul>
 */
@Component(service = FacetExtractor.class)
public class FacetExtractor {

	public FacetCapture extract(
		SearchContext searchContext, SourceType sourceType) {

		try {
			JSONObject jsonObject = _extractSelections(searchContext);

			if (jsonObject.length() > 0) {
				return FacetCapture.captured(jsonObject.toString());
			}

			if (sourceType == SourceType.WIDGET) {
				return FacetCapture.NONE_APPLIED;
			}

			return FacetCapture.UNAVAILABLE;
		}
		catch (Throwable throwable) {
			return FacetCapture.UNAVAILABLE;
		}
	}

	private JSONObject _extractSelections(SearchContext searchContext) {
		JSONObject jsonObject = _jsonFactory.createJSONObject();

		Map<String, Facet> facets = searchContext.getFacets();

		if (facets == null) {
			return jsonObject;
		}

		for (Map.Entry<String, Facet> entry : facets.entrySet()) {
			Facet facet = entry.getValue();

			if (facet == null) {
				continue;
			}

			String[] selections = _getSelections(facet, searchContext);

			if ((selections == null) || (selections.length == 0)) {
				continue;
			}

			String fieldName = facet.getFieldName();

			if (Validator.isNull(fieldName)) {
				fieldName = entry.getKey();
			}

			JSONArray jsonArray = _jsonFactory.createJSONArray();

			for (String selection : selections) {
				if (Validator.isNotNull(selection)) {
					jsonArray.put(selection);
				}
			}

			if (jsonArray.length() > 0) {
				jsonObject.put(fieldName, jsonArray);
			}
		}

		return jsonObject;
	}

	/**
	 * Selections declared by the newer facet API win; otherwise the request
	 * attribute keyed by the facet's field ID is read, which is how the facet's
	 * own query building reads them.
	 */
	private String[] _getSelections(Facet facet, SearchContext searchContext) {
		if (facet instanceof com.liferay.portal.search.facet.Facet) {
			com.liferay.portal.search.facet.Facet searchFacet =
				(com.liferay.portal.search.facet.Facet)facet;

			String[] selections = searchFacet.getSelections();

			if ((selections != null) && (selections.length > 0)) {
				return selections;
			}
		}

		String fieldId = facet.getFieldId();

		if (Validator.isNull(fieldId)) {
			return null;
		}

		Serializable attribute = searchContext.getAttribute(fieldId);

		if (attribute == null) {
			return null;
		}

		return StringUtil.split(GetterUtil.getString(attribute));
	}

	@Reference
	private JSONFactory _jsonFactory;

}
