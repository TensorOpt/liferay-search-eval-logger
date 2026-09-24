/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.capture;

import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.search.document.Document;
import com.liferay.portal.search.highlight.HighlightField;
import com.liferay.portal.search.hits.SearchHits;
import com.liferay.portal.search.searcher.SearchRequest;
import com.liferay.portal.search.searcher.SearchResponse;

import ai.tensoropt.sel.api.SearchEvalLoggerConstants;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.context.SearchRequestOrigin;

import java.time.Clock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Turns an admitted search and the response it already produced into the value
 * object the listener persists.
 *
 * <p>
 * Nothing here reaches back to the search engine. Capture depth is
 * <code>min(K, hits returned)</code> and the response is never re-queried to
 * deepen it (D4), and no field is read that the caller did not already ask for
 * (D8). Every string is truncated to its column width here rather than at the
 * database, so an oversized value produces a bounded row instead of a SQL
 * error.
 * </p>
 */
@Component(service = SearchEventCaptor.class)
public class SearchEventCaptor {

	public CapturedSearchEvent capture(
		SearchRequest searchRequest, SearchResponse searchResponse,
		SearchContext searchContext, SearchEvalLoggerConfiguration configuration,
		SearchRequestOrigin searchRequestOrigin) {

		CapturedSearchEvent capturedSearchEvent = new CapturedSearchEvent();

		capturedSearchEvent.setCompanyId(searchContext.getCompanyId());
		capturedSearchEvent.setCreateTime(_clock.millis());
		capturedSearchEvent.setUserId(searchContext.getUserId());
		capturedSearchEvent.setSourceType(searchRequestOrigin.getSourceType());

		_captureQueryText(capturedSearchEvent, searchContext, configuration);

		capturedSearchEvent.setLocale(
			_truncate(_getLanguageId(searchContext), _LOCALE_MAX_LENGTH));
		capturedSearchEvent.setScopeGroupIds(
			_truncateList(
				_join(searchContext.getGroupIds()), _SCOPE_GROUP_IDS_MAX_LENGTH));
		capturedSearchEvent.setEntryClassNames(
			_truncateList(
				_getEntryClassNames(searchRequest, searchContext),
				_ENTRY_CLASS_NAMES_MAX_LENGTH));

		FacetCapture facetCapture = _facetExtractor.extract(
			searchContext, searchRequestOrigin.getSourceType());

		capturedSearchEvent.setAppliedFacets(facetCapture.getAppliedFacets());
		capturedSearchEvent.setFacetCaptureStatus(facetCapture.getStatus());

		capturedSearchEvent.setBlueprintId(
			_truncate(_getBlueprintId(searchContext), _BLUEPRINT_ID_MAX_LENGTH));

		int requestedFrom = _getInt(searchRequest.getFrom());

		capturedSearchEvent.setRequestedFrom(requestedFrom);
		capturedSearchEvent.setRequestedSize(_getInt(searchRequest.getSize()));

		_captureHits(
			capturedSearchEvent, searchResponse, searchContext, configuration,
			requestedFrom);

		return capturedSearchEvent;
	}

	private void _captureHits(
		CapturedSearchEvent capturedSearchEvent, SearchResponse searchResponse,
		SearchContext searchContext,
		SearchEvalLoggerConfiguration configuration, int requestedFrom) {

		SearchHits searchHits = searchResponse.getSearchHits();

		if (searchHits == null) {
			return;
		}

		capturedSearchEvent.setTotalHits(searchHits.getTotalHits());

		List<com.liferay.portal.search.hits.SearchHit> hits =
			searchHits.getSearchHits();

		if ((hits == null) || hits.isEmpty()) {
			return;
		}

		int captureDepth = Math.min(
			Math.max(configuration.captureDepth(), 0), hits.size());

		String languageId = _getLanguageId(searchContext);
		String[] capturedFieldNames = configuration.capturedFieldNames();

		for (int i = 0; i < captureDepth; i++) {
			com.liferay.portal.search.hits.SearchHit hit = hits.get(i);

			if (hit == null) {
				continue;
			}

			capturedSearchEvent.addHit(
				_captureHit(hit, requestedFrom + i, languageId,
					capturedFieldNames));
		}
	}

	private CapturedSearchHit _captureHit(
		com.liferay.portal.search.hits.SearchHit hit, int rank,
		String languageId, String[] capturedFieldNames) {

		CapturedSearchHit capturedSearchHit = new CapturedSearchHit();

		capturedSearchHit.setRank(rank);
		capturedSearchHit.setScore(hit.getScore());

		Document document = hit.getDocument();

		if (document == null) {
			return capturedSearchHit;
		}

		capturedSearchHit.setDocUid(
			_truncate(document.getString(Field.UID), _DOC_UID_MAX_LENGTH));
		capturedSearchHit.setEntryClassName(
			_truncate(
				document.getString(Field.ENTRY_CLASS_NAME),
				_ENTRY_CLASS_NAME_MAX_LENGTH));
		capturedSearchHit.setEntryClassPK(
			GetterUtil.getLong(document.getString(Field.ENTRY_CLASS_PK)));

		if (_log.isDebugEnabled()) {
			_log.debug(
				StringBundler.concat(
					"Capturing hit docUid=",
					document.getString(Field.UID), ", capturedFieldNames=",
					Arrays.toString(capturedFieldNames),
					", document field names=",
					_fieldNames(document.getFields()),
					", highlight field names=",
					_highlightFieldNames(hit.getHighlightFieldsMap())));
		}

		JSONObject extraFieldsJSONObject = null;

		for (String fieldName : capturedFieldNames) {
			if (Validator.isNull(fieldName)) {
				continue;
			}

			fieldName = StringUtil.trim(fieldName);

			String value;

			if (SearchEvalLoggerConstants.FIELD_SNIPPET.equals(fieldName)) {
				value = _getSnippetValue(hit, document, languageId);
			}
			else {
				value = _getFieldValue(document, fieldName, languageId);
			}

			if (Validator.isNull(value)) {
				continue;
			}

			if (SearchEvalLoggerConstants.FIELD_TITLE.equals(fieldName)) {
				capturedSearchHit.setTitle(
					_truncate(value, _TITLE_MAX_LENGTH));
			}
			else if (SearchEvalLoggerConstants.FIELD_SNIPPET.equals(fieldName)) {
				capturedSearchHit.setSnippet(value);
			}
			else {
				if (extraFieldsJSONObject == null) {
					extraFieldsJSONObject = _jsonFactory.createJSONObject();
				}

				extraFieldsJSONObject.put(fieldName, value);
			}
		}

		if ((extraFieldsJSONObject != null) &&
			(extraFieldsJSONObject.length() > 0)) {

			capturedSearchHit.setExtraFields(extraFieldsJSONObject.toString());
		}

		if (_log.isDebugEnabled()) {
			_log.debug(
				StringBundler.concat(
					"Captured hit docUid=", document.getString(Field.UID),
					", title=",
					String.valueOf(
						Validator.isNotNull(capturedSearchHit.getTitle())),
					", snippet=",
					String.valueOf(
						Validator.isNotNull(capturedSearchHit.getSnippet())),
					", extraFields=",
					String.valueOf(
						Validator.isNotNull(
							capturedSearchHit.getExtraFields()))));
		}

		return capturedSearchHit;
	}

	private void _captureQueryText(
		CapturedSearchEvent capturedSearchEvent, SearchContext searchContext,
		SearchEvalLoggerConfiguration configuration) {

		String keywords = searchContext.getKeywords();

		if (keywords == null) {
			return;
		}

		keywords = keywords.trim();

		int queryTextCap = Math.min(
			Math.max(configuration.queryTextCap(), 1), _QUERY_TEXT_MAX_LENGTH);

		if (keywords.length() > queryTextCap) {
			capturedSearchEvent.setQueryText(
				keywords.substring(0, queryTextCap));
			capturedSearchEvent.setQueryTruncated(true);
		}
		else {
			capturedSearchEvent.setQueryText(keywords);
		}
	}

	private String _getBlueprintId(SearchContext searchContext) {
		Object blueprintId = searchContext.getAttribute(
			_BLUEPRINT_ID_ATTRIBUTE_NAME);

		if (blueprintId == null) {
			return null;
		}

		String value = GetterUtil.getString(blueprintId);

		if (Validator.isNull(value)) {
			return null;
		}

		return value;
	}

	private String _getEntryClassNames(
		SearchRequest searchRequest, SearchContext searchContext) {

		List<String> entryClassNames = searchRequest.getEntryClassNames();

		if ((entryClassNames != null) && !entryClassNames.isEmpty()) {
			return _join(entryClassNames);
		}

		String[] contextEntryClassNames = searchContext.getEntryClassNames();

		if ((contextEntryClassNames != null) &&
			(contextEntryClassNames.length > 0)) {

			return _join(Arrays.asList(contextEntryClassNames));
		}

		return null;
	}

	/**
	 * Probes only what the response already carries (D8): the field as named,
	 * then its localized variant, then, for the snippet field alone, a
	 * document-embedded <code>snippet_&lt;field&gt;</code> field. This is a
	 * fallback for search engines that surface highlights inside the document
	 * rather than through {@link
	 * com.liferay.portal.search.hits.SearchHit#getHighlightFieldsMap()}, which
	 * {@link #_getSnippetValue} checks first. Which of these an installation
	 * actually returns is EC-4, and the export manifest reports the resulting
	 * coverage rather than this code assuming it.
	 */
	private String _getFieldValue(
		Document document, String fieldName, String languageId) {

		String value = document.getString(fieldName);

		if (Validator.isNotNull(value)) {
			return value;
		}

		if (Validator.isNotNull(languageId)) {
			value = document.getString(fieldName + _UNDERLINE + languageId);

			if (Validator.isNotNull(value)) {
				return value;
			}
		}

		if (SearchEvalLoggerConstants.FIELD_SNIPPET.equals(fieldName)) {
			return _getSnippetFieldValue(document);
		}

		return null;
	}

	/**
	 * The excerpt a search UI actually shows comes from
	 * {@link com.liferay.portal.search.hits.SearchHit#getHighlightFieldsMap()},
	 * a sibling of {@link Document} rather than a field inside it, so it is
	 * probed separately from every other captured field. Fragments from every
	 * highlighted field are joined, since no single named field corresponds to
	 * what the UI renders. Falls back to a document-embedded value for search
	 * engines that surface a snippet that way instead (EC-4).
	 */
	private String _getSnippetValue(
		com.liferay.portal.search.hits.SearchHit hit, Document document,
		String languageId) {

		String value = _getHighlightSnippet(hit);

		if (Validator.isNotNull(value)) {
			return value;
		}

		return _getFieldValue(
			document, SearchEvalLoggerConstants.FIELD_SNIPPET, languageId);
	}

	private String _getHighlightSnippet(
		com.liferay.portal.search.hits.SearchHit hit) {

		Map<String, HighlightField> highlightFieldsMap =
			hit.getHighlightFieldsMap();

		if ((highlightFieldsMap == null) || highlightFieldsMap.isEmpty()) {
			if (_log.isDebugEnabled()) {
				_log.debug("getHighlightFieldsMap() returned null or empty");
			}

			return null;
		}

		List<String> fragments = new ArrayList<>();

		for (HighlightField highlightField : highlightFieldsMap.values()) {
			if (highlightField == null) {
				continue;
			}

			List<String> highlightFieldFragments =
				highlightField.getFragments();

			// The fragment count, not the fragments. A fragment is indexed
			// content with the user's terms marked up inside it, so printing
			// one puts both the document text and the query into the portal
			// log, where nothing this plugin controls will ever purge them.
			// The count is what the diagnostic was actually for: whether the
			// highlighter returned anything at all.

			if (_log.isDebugEnabled()) {
				_log.debug(
					StringBundler.concat(
						"Highlight field name=", highlightField.getName(),
						", fragmentCount=",
						String.valueOf(
							(highlightFieldFragments == null) ? 0 :
								highlightFieldFragments.size())));
			}

			if (highlightFieldFragments != null) {
				fragments.addAll(highlightFieldFragments);
			}
		}

		if (fragments.isEmpty()) {
			return null;
		}

		return StringUtil.merge(fragments, " ... ");
	}

	private String _fieldNames(
		Map<String, com.liferay.portal.search.document.Field> fields) {

		if (fields == null) {
			return "null";
		}

		return fields.keySet().toString();
	}

	private String _highlightFieldNames(
		Map<String, HighlightField> highlightFieldsMap) {

		if (highlightFieldsMap == null) {
			return "null";
		}

		return highlightFieldsMap.keySet().toString();
	}

	private int _getInt(Integer value) {
		if (value == null) {
			return 0;
		}

		return value.intValue();
	}

	private String _getLanguageId(SearchContext searchContext) {
		String languageId = searchContext.getLanguageId();

		if (Validator.isNotNull(languageId)) {
			return languageId;
		}

		Locale locale = searchContext.getLocale();

		if (locale == null) {
			return null;
		}

		return locale.toString();
	}

	private String _getSnippetFieldValue(Document document) {
		java.util.Map<String, com.liferay.portal.search.document.Field> fields =
			document.getFields();

		if (fields == null) {
			return null;
		}

		for (String name : fields.keySet()) {
			if ((name != null) && name.startsWith(_SNIPPET_FIELD_PREFIX)) {
				String value = document.getString(name);

				if (Validator.isNotNull(value)) {
					return value;
				}
			}
		}

		return null;
	}

	private String _join(Collection<String> values) {
		if ((values == null) || values.isEmpty()) {
			return null;
		}

		return StringUtil.merge(values, _SEPARATOR);
	}

	private String _join(long[] values) {
		if ((values == null) || (values.length == 0)) {
			return null;
		}

		return StringUtil.merge(values, _SEPARATOR);
	}

	private String _truncate(String value, int maxLength) {
		if ((value == null) || (value.length() <= maxLength)) {
			return value;
		}

		return value.substring(0, maxLength);
	}

	/**
	 * For the comma separated columns only. Cuts on a separator boundary where
	 * there is one, so a truncated list never ends in half an identifier that
	 * reads as a whole one. Applied to free text such as a title, the same cut
	 * would throw away everything after the last comma (TO-94).
	 */
	private String _truncateList(String value, int maxLength) {
		String truncated = _truncate(value, maxLength);

		if ((truncated == null) || (truncated.length() == value.length())) {
			return truncated;
		}

		int index = truncated.lastIndexOf(_SEPARATOR);

		if (index > 0) {
			return truncated.substring(0, index);
		}

		return truncated;
	}

	// Column widths from portlet-model-hints.xml in the service module. Values
	// are truncated here rather than at the database, so these must move
	// together with that file.

	private static final String _BLUEPRINT_ID_ATTRIBUTE_NAME =
		"search.experiences.blueprint.id";

	private static final int _BLUEPRINT_ID_MAX_LENGTH = 100;

	private static final int _DOC_UID_MAX_LENGTH = 500;

	private static final int _ENTRY_CLASS_NAME_MAX_LENGTH = 200;

	private static final int _ENTRY_CLASS_NAMES_MAX_LENGTH = 2000;

	private static final int _LOCALE_MAX_LENGTH = 20;

	private static final int _QUERY_TEXT_MAX_LENGTH = 2000;

	private static final int _SCOPE_GROUP_IDS_MAX_LENGTH = 500;

	private static final String _SEPARATOR = ",";

	private static final String _SNIPPET_FIELD_PREFIX = "snippet_";

	private static final String _UNDERLINE = "_";

	private static final int _TITLE_MAX_LENGTH = 1000;

	private static final Log _log = LogFactoryUtil.getLog(
		SearchEventCaptor.class);

	/**
	 * Visible for testing, so a captured event's timestamp is deterministic.
	 */
	void setClock(Clock clock) {
		_clock = clock;
	}

	private Clock _clock = Clock.systemUTC();

	@Reference
	private FacetExtractor _facetExtractor;

	@Reference
	private JSONFactory _jsonFactory;

}
