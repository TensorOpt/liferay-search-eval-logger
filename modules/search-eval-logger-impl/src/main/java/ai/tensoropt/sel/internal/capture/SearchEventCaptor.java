/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.capture;

import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.util.GetterUtil;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
				_join(EntryClassNames.get(searchRequest, searchContext)),
				_ENTRY_CLASS_NAMES_MAX_LENGTH));

		FacetCapture facetCapture = _facetExtractor.extract(
			searchContext, searchRequestOrigin.getSourceType());

		capturedSearchEvent.setAppliedFacets(facetCapture.getAppliedFacets());
		capturedSearchEvent.setFacetCaptureStatus(facetCapture.getStatus());

		capturedSearchEvent.setBlueprintId(
			_truncate(_getBlueprintId(searchContext), _BLUEPRINT_ID_MAX_LENGTH));

		int requestedFrom = GetterUtil.getInteger(searchRequest.getFrom());

		capturedSearchEvent.setRequestedFrom(requestedFrom);
		capturedSearchEvent.setRequestedSize(
			GetterUtil.getInteger(searchRequest.getSize()));

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

		// Only resolved when the snippet is actually configured to be
		// captured: every other field is read as named, with no need for
		// the installation's language ids at all.

		Set<String> languageIds =
			_containsSnippetFieldName(capturedFieldNames) ?
				_getLanguageIds(searchContext.getCompanyId()) :
					Collections.emptySet();

		for (int i = 0; i < captureDepth; i++) {
			com.liferay.portal.search.hits.SearchHit hit = hits.get(i);

			if (hit == null) {
				continue;
			}

			capturedSearchEvent.addHit(
				_captureHit(hit, requestedFrom + i, languageId,
					capturedFieldNames, languageIds));
		}
	}

	private CapturedSearchHit _captureHit(
		com.liferay.portal.search.hits.SearchHit hit, int rank,
		String languageId, String[] capturedFieldNames,
		Set<String> languageIds) {

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

		JSONObject extraFieldsJSONObject = null;

		for (String fieldName : capturedFieldNames) {
			if (Validator.isNull(fieldName)) {
				continue;
			}

			fieldName = StringUtil.trim(fieldName);

			String value;

			if (SearchEvalLoggerConstants.FIELD_SNIPPET.equals(fieldName)) {
				value = _getSnippetValue(hit, document, languageIds);
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
		String blueprintId = GetterUtil.getString(
			searchContext.getAttribute(_BLUEPRINT_ID_ATTRIBUTE_NAME));

		return Validator.isNull(blueprintId) ? null : blueprintId;
	}

	/**
	 * Probes only what the response already carries (D8): the field as
	 * named, then its localized variant. Shared by every captured field
	 * except the snippet, which carries locale and field context of its own
	 * and so is built separately by {@link #_getSnippetValue}.
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

		return null;
	}

	private Set<String> _getLanguageIds(long companyId) {
		Set<Locale> locales = _language.getCompanyAvailableLocales(companyId);

		if ((locales == null) || locales.isEmpty()) {
			return Collections.emptySet();
		}

		Set<String> languageIds = new HashSet<>();

		for (Locale locale : locales) {
			languageIds.add(_language.getLanguageId(locale));
		}

		return languageIds;
	}

	/**
	 * Builds the <code>snippet</code> column's JSON object (TO-115): every
	 * fragment Liferay returned, grouped first by the locale its field name
	 * carries and then by that field, with highlight markup kept exactly as
	 * returned and fragments always arrays, even a single one. Never joined
	 * into one string, which is what this column held before and what made
	 * the locale and field each fragment came from unrecoverable.
	 *
	 * <p>
	 * The excerpt a search UI actually shows comes from {@link
	 * com.liferay.portal.search.hits.SearchHit#getHighlightFieldsMap()}, a
	 * sibling of {@link Document} rather than a field inside it, so it is
	 * probed separately from every other captured field and first. Only when
	 * that carries nothing does this fall back to a document-embedded value,
	 * for search engines that surface a snippet that way instead (EC-4).
	 * </p>
	 */
	private String _getSnippetValue(
		com.liferay.portal.search.hits.SearchHit hit, Document document,
		Set<String> languageIds) {

		Map<String, Map<String, List<String>>> snippetFragments =
			_getHighlightSnippetFragments(hit, languageIds);

		if (snippetFragments.isEmpty()) {
			snippetFragments = _getDocumentSnippetFragments(
				document, languageIds);
		}

		if (snippetFragments.isEmpty()) {
			return null;
		}

		return _toSnippetJSONObject(snippetFragments).toString();
	}

	private void _addSnippetFragment(
		Map<String, Map<String, List<String>>> snippetFragments,
		String locale, String fieldName, String fragment) {

		Map<String, List<String>> fieldsByLocale =
			snippetFragments.computeIfAbsent(
				locale, key -> new LinkedHashMap<>());

		fieldsByLocale.computeIfAbsent(
			fieldName, key -> new ArrayList<>()
		).add(
			fragment
		);
	}

	/**
	 * The document-embedded fallback for EC-4 (no entry in {@link
	 * com.liferay.portal.search.hits.SearchHit#getHighlightFieldsMap()}): a
	 * plain <code>snippet</code> field first, then every field prefixed
	 * <code>snippet_</code>, for a search engine that names a per-field (or
	 * per-locale) snippet that way.
	 *
	 * <p>
	 * A prefixed field's inner name (what follows <code>snippet_</code>) is
	 * checked against the company's locales as a whole string before it is
	 * run through the same suffix-based locale detection a highlighted field
	 * name gets: <code>snippet_fr_FR</code> names the whole snippet in
	 * French, not a field literally called <code>fr_FR</code>, so it is
	 * filed as <code>(fr_FR, snippet)</code> rather than
	 * <code>(_default, fr_FR)</code>. Checking the whole string first is
	 * also what makes a separate, explicit probe for the search's own
	 * current language id unnecessary (TO-115 review): that field is simply
	 * one more <code>snippet_&lt;locale&gt;</code> entry this loop already
	 * finds, whichever locale the search happens to be in.
	 * </p>
	 */
	private Map<String, Map<String, List<String>>> _getDocumentSnippetFragments(
		Document document, Set<String> languageIds) {

		Map<String, Map<String, List<String>>> snippetFragments =
			new LinkedHashMap<>();

		String value = document.getString(
			SearchEvalLoggerConstants.FIELD_SNIPPET);

		if (Validator.isNotNull(value)) {
			_addSnippetFragment(
				snippetFragments, LocalizedFieldName.DEFAULT_LOCALE,
				SearchEvalLoggerConstants.FIELD_SNIPPET, value);

			return snippetFragments;
		}

		Map<String, com.liferay.portal.search.document.Field> fields =
			document.getFields();

		if (fields == null) {
			return snippetFragments;
		}

		for (String name : fields.keySet()) {
			if ((name == null) || !name.startsWith(_SNIPPET_FIELD_PREFIX)) {
				continue;
			}

			value = document.getString(name);

			if (Validator.isNull(value)) {
				continue;
			}

			String innerFieldName = name.substring(
				_SNIPPET_FIELD_PREFIX.length());

			if (Validator.isNull(innerFieldName)) {
				continue;
			}

			if (languageIds.contains(innerFieldName)) {
				_addSnippetFragment(
					snippetFragments, innerFieldName,
					SearchEvalLoggerConstants.FIELD_SNIPPET, value);

				continue;
			}

			LocalizedFieldName localizedFieldName = LocalizedFieldName.parse(
				innerFieldName, languageIds);

			_addSnippetFragment(
				snippetFragments, localizedFieldName.getLocale(),
				localizedFieldName.getFieldName(), value);
		}

		return snippetFragments;
	}

	private Map<String, Map<String, List<String>>> _getHighlightSnippetFragments(
		com.liferay.portal.search.hits.SearchHit hit,
		Set<String> languageIds) {

		Map<String, Map<String, List<String>>> snippetFragments =
			new LinkedHashMap<>();

		Map<String, HighlightField> highlightFieldsMap =
			hit.getHighlightFieldsMap();

		if ((highlightFieldsMap == null) || highlightFieldsMap.isEmpty()) {
			return snippetFragments;
		}

		for (Map.Entry<String, HighlightField> entry :
				highlightFieldsMap.entrySet()) {

			HighlightField highlightField = entry.getValue();

			if (highlightField == null) {
				continue;
			}

			List<String> fragments = highlightField.getFragments();

			if ((fragments == null) || fragments.isEmpty()) {
				continue;
			}

			LocalizedFieldName localizedFieldName = LocalizedFieldName.parse(
				entry.getKey(), languageIds);

			for (String fragment : fragments) {
				_addSnippetFragment(
					snippetFragments, localizedFieldName.getLocale(),
					localizedFieldName.getFieldName(), fragment);
			}
		}

		return snippetFragments;
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

	private boolean _containsSnippetFieldName(String[] capturedFieldNames) {
		if (capturedFieldNames == null) {
			return false;
		}

		for (String fieldName : capturedFieldNames) {
			if (SearchEvalLoggerConstants.FIELD_SNIPPET.equals(
					StringUtil.trim(fieldName))) {

				return true;
			}
		}

		return false;
	}

	private JSONObject _toSnippetJSONObject(
		Map<String, Map<String, List<String>>> snippetFragments) {

		JSONObject localesJSONObject = _jsonFactory.createJSONObject();

		for (Map.Entry<String, Map<String, List<String>>> localeEntry :
				snippetFragments.entrySet()) {

			JSONObject fieldsJSONObject = _jsonFactory.createJSONObject();

			for (Map.Entry<String, List<String>> fieldEntry :
					localeEntry.getValue().entrySet()) {

				JSONArray fragmentsJSONArray = _jsonFactory.createJSONArray();

				for (String fragment : fieldEntry.getValue()) {
					fragmentsJSONArray.put(fragment);
				}

				fieldsJSONObject.put(
					fieldEntry.getKey(), fragmentsJSONArray);
			}

			localesJSONObject.put(localeEntry.getKey(), fieldsJSONObject);
		}

		return localesJSONObject;
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

	@Reference
	private Language _language;

}
