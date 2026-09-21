/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.web.internal.export;

import com.liferay.portal.kernel.dao.orm.ActionableDynamicQuery;
import com.liferay.portal.kernel.dao.orm.Property;
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.transaction.Propagation;
import com.liferay.portal.kernel.transaction.TransactionConfig;
import com.liferay.portal.kernel.util.Validator;

import com.tensoropt.search.eval.logger.api.SearchEvalLoggerConstants;
import com.tensoropt.search.eval.logger.configuration.SearchEvalLoggerConfiguration;
import com.tensoropt.search.eval.logger.model.SearchEvent;
import com.tensoropt.search.eval.logger.service.SearchEventLocalService;
import com.tensoropt.search.eval.logger.service.persistence.SearchHitPersistence;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import java.nio.charset.StandardCharsets;

import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Writes the export archive of DESIGN.md 6.2 straight to disk.
 *
 * <p>
 * <strong>This streams, and that is a hard requirement rather than an
 * optimization.</strong> A full-window export can span millions of hit rows,
 * and administrators will run the full window. Nothing here accumulates a
 * result set: events arrive one interval at a time from an
 * {@link ActionableDynamicQuery}, each is serialized to a line and written
 * through a buffered writer onto the {@link ZipOutputStream}, and the only rows
 * held at once are the hits of the event being written, which capture depth
 * already bounds. What bounds memory is that no line is retained after it is
 * written, not the flushing: the writer is flushed once, at the end of the
 * entry, and the buffer in between is a fixed size. Memory stays flat whether
 * the export covers an hour or ninety days.
 * </p>
 *
 * <p>
 * JSONL rather than CSV, because snippets routinely contain quotes, commas,
 * newlines and highlight markup, and because nesting the hits inside the event
 * makes each line exactly the <code>(query, result[])</code> record the dataset
 * exists to provide, with no join to reconstruct.
 * </p>
 *
 * <p>
 * The three entries are written in one pass and in this order because the
 * manifest reports figures, counts and per-field coverage, that are only known
 * once the data has streamed.
 * </p>
 */
@Component(service = SearchEvalExportWriter.class)
public class SearchEvalExportWriter {

	public SearchEvalExportResult write(
			File file, long companyId, Date startDate, Date endDate,
			SearchEvalLoggerConfiguration searchEvalLoggerConfiguration)
		throws Exception {

		SearchEvalExportResult searchEvalExportResult =
			new SearchEvalExportResult();

		_registerCapturedFields(
			searchEvalExportResult, searchEvalLoggerConfiguration);

		try (FileOutputStream fileOutputStream = new FileOutputStream(file);
			ZipOutputStream zipOutputStream = new ZipOutputStream(
				fileOutputStream)) {

			zipOutputStream.putNextEntry(new ZipEntry(_EVENTS_FILE_NAME));

			_writeEvents(
				zipOutputStream, companyId, startDate, endDate,
				searchEvalExportResult);

			zipOutputStream.closeEntry();

			zipOutputStream.putNextEntry(new ZipEntry(_MANIFEST_FILE_NAME));

			JSONObject manifestJSONObject =
				_searchEvalExportManifestBuilder.build(
					companyId, startDate, endDate, searchEvalExportResult,
					searchEvalLoggerConfiguration);

			_write(zipOutputStream, manifestJSONObject.toString());

			zipOutputStream.closeEntry();

			zipOutputStream.putNextEntry(new ZipEntry(_README_FILE_NAME));

			_write(
				zipOutputStream,
				_searchEvalExportReadmeBuilder.build(
					startDate, endDate, searchEvalExportResult,
					searchEvalLoggerConfiguration));

			zipOutputStream.closeEntry();
		}

		return searchEvalExportResult;
	}

	private JSONObject _createFromTemplate(String template) {
		try {
			return _jsonFactory.createJSONObject(template);
		}
		catch (Exception exception) {
			if (_log.isWarnEnabled()) {
				_log.warn("Unable to parse a JSON template", exception);
			}

			return _jsonFactory.createJSONObject();
		}
	}

	/**
	 * Puts a value only when there is one, leaving the template's explicit null
	 * in place otherwise. Two reasons: Liferay's JSONObject delegates to a
	 * library that removes a key whose value is null, which would make the
	 * schema vary line by line; and Service Builder stores an unset string as
	 * an empty one, which would export as "" and read as a captured empty
	 * value rather than an absent field. Blank is absence here, consistently
	 * with how the manifest counts coverage.
	 */
	private void _put(JSONObject jsonObject, String key, Object value) {
		if (value == null) {
			return;
		}

		if ((value instanceof String) && Validator.isNull((String)value)) {
			return;
		}

		jsonObject.put(key, value);
	}

	private void _registerCapturedFields(
		SearchEvalExportResult searchEvalExportResult,
		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration) {

		String[] capturedFieldNames =
			searchEvalLoggerConfiguration.capturedFieldNames();

		if (capturedFieldNames == null) {
			return;
		}

		for (String capturedFieldName : capturedFieldNames) {
			if (Validator.isNotNull(capturedFieldName)) {
				searchEvalExportResult.registerField(
					StringUtil.trim(capturedFieldName));
			}
		}
	}

	private JSONArray _toJSONArray(String value, boolean numeric) {
		JSONArray jsonArray = _jsonFactory.createJSONArray();

		if (Validator.isNull(value)) {
			return jsonArray;
		}

		for (String part : StringUtil.split(value)) {
			if (Validator.isNull(part)) {
				continue;
			}

			if (numeric) {
				jsonArray.put(GetterUtil.getLong(part));
			}
			else {
				jsonArray.put(part);
			}
		}

		return jsonArray;
	}

	/**
	 * Re-embeds a JSON-encoded column as real JSON. Exporting it as an escaped
	 * string would force every consumer to parse a second time, and would
	 * quietly turn a malformed value into a plausible-looking string. A value
	 * that will not parse is reported as null, not as text.
	 */
	private JSONObject _toJSONObject(String value) {
		if (Validator.isNull(value)) {
			return null;
		}

		try {
			return _jsonFactory.createJSONObject(value);
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug("Unable to parse a stored JSON column", exception);
			}

			return null;
		}
	}

	private void _write(ZipOutputStream zipOutputStream, String content)
		throws IOException {

		zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
	}

	private void _writeEvent(
			Writer writer, SearchEvent searchEvent,
			SearchEvalExportResult searchEvalExportResult)
		throws IOException {

		// Seeded from a template so every line carries every key, with an
		// explicit null where there is no value. Liferay's JSONObject drops a
		// key whose value is null, which would make the schema vary line by
		// line and leave a consumer unable to tell a field that was never
		// captured from one this row happens to lack.

		JSONObject jsonObject = _createFromTemplate(_EVENT_TEMPLATE);

		_put(jsonObject, "event_id", searchEvent.getUuid());
		_put(
			jsonObject, "created_at",
			ExportTimestamps.format(searchEvent.getCreateDate()));
		_put(jsonObject, "query", searchEvent.getQueryText());
		jsonObject.put("query_truncated", searchEvent.isQueryTruncated());
		_put(jsonObject, "locale", searchEvent.getLocale());
		jsonObject.put(
			"scope_group_ids",
			_toJSONArray(searchEvent.getScopeGroupIds(), true));
		jsonObject.put(
			"entry_class_names",
			_toJSONArray(searchEvent.getEntryClassNames(), false));
		_put(
			jsonObject, "applied_facets",
			_toJSONObject(searchEvent.getAppliedFacets()));
		_put(
			jsonObject, "facet_capture_status",
			searchEvent.getFacetCaptureStatus());
		_put(jsonObject, "blueprint_id", searchEvent.getBlueprintId());
		_put(jsonObject, "audience_type", searchEvent.getAudienceType());
		_put(jsonObject, "cohort_hash", searchEvent.getCohortHash());
		jsonObject.put("requested_size", searchEvent.getRequestedSize());
		jsonObject.put("requested_from", searchEvent.getRequestedFrom());
		jsonObject.put("total_hits", searchEvent.getTotalHits());
		jsonObject.put("logged_hit_count", searchEvent.getLoggedHitCount());
		_put(jsonObject, "source_type", searchEvent.getSourceType());
		jsonObject.put(
			"hits", _toHitsJSONArray(
				searchEvent.getUuid(), searchEvalExportResult));

		writer.write(jsonObject.toString());
		writer.write(_NEW_LINE);

		searchEvalExportResult.incrementEventCount();
	}

	private void _writeEvents(
			ZipOutputStream zipOutputStream, long companyId, Date startDate,
			Date endDate, SearchEvalExportResult searchEvalExportResult)
		throws Exception {

		// Deliberately not closed: closing the writer would close the
		// ZipOutputStream under it, and the manifest and README still have to
		// be written into the same archive.

		Writer writer = new BufferedWriter(
			new OutputStreamWriter(zipOutputStream, StandardCharsets.UTF_8));

		ActionableDynamicQuery actionableDynamicQuery =
			_searchEventLocalService.getActionableDynamicQuery();

		actionableDynamicQuery.setCompanyId(companyId);
		actionableDynamicQuery.setInterval(_INTERVAL);

		actionableDynamicQuery.setAddCriteriaMethod(
			dynamicQuery -> {
				Property createDateProperty = PropertyFactoryUtil.forName(
					"createDate");

				if (startDate != null) {
					dynamicQuery.add(createDateProperty.ge(startDate));
				}

				if (endDate != null) {
					dynamicQuery.add(createDateProperty.lt(endDate));
				}
			});

		// Without this the export dies on its first event. The background task
		// thread carries no transaction, so the per-event hit lookup below
		// cannot open a Hibernate session: "No current transaction executor".
		// The event iteration alone would have survived, because
		// ActionableDynamicQuery manages a session for its own paging, which
		// is why the gap only appears once a nested read is added. Scoped per
		// batch by setInterval, so this stays bounded rather than holding one
		// transaction open for the whole export.

		actionableDynamicQuery.setTransactionConfig(_TRANSACTION_CONFIG);

		actionableDynamicQuery.setPerformActionMethod(
			(SearchEvent searchEvent) -> {
				try {
					_writeEvent(writer, searchEvent, searchEvalExportResult);
				}
				catch (IOException ioException) {
					throw new PortalException(ioException);
				}
			});

		actionableDynamicQuery.performActions();

		writer.flush();
	}

	private JSONArray _toHitsJSONArray(
		String searchEventUuid, SearchEvalExportResult searchEvalExportResult) {

		JSONArray jsonArray = _jsonFactory.createJSONArray();

		if (Validator.isNull(searchEventUuid)) {
			return jsonArray;
		}

		// Bounded by capture depth, which is what keeps this from being the
		// place memory grows. The join column is indexed for exactly this
		// lookup (DESIGN.md 4.5).

		List<com.tensoropt.search.eval.logger.model.SearchHit> searchHits =
			_searchHitPersistence.findBySearchEventUuid(searchEventUuid);

		for (com.tensoropt.search.eval.logger.model.SearchHit searchHit :
				searchHits) {

			JSONObject jsonObject = _createFromTemplate(_HIT_TEMPLATE);

			jsonObject.put("rank", searchHit.getRank());
			jsonObject.put("score", searchHit.getScore());
			_put(jsonObject, "doc_uid", searchHit.getDocUid());
			_put(jsonObject, "entry_class_name", searchHit.getEntryClassName());
			jsonObject.put("entry_class_pk", searchHit.getEntryClassPK());
			_put(jsonObject, "title", searchHit.getTitle());
			_put(jsonObject, "snippet", searchHit.getSnippet());

			JSONObject extraFieldsJSONObject = _toJSONObject(
				searchHit.getExtraFields());

			if (extraFieldsJSONObject == null) {
				extraFieldsJSONObject = _jsonFactory.createJSONObject();
			}

			jsonObject.put("extra_fields", extraFieldsJSONObject);

			_countCoverage(
				searchEvalExportResult, searchHit, extraFieldsJSONObject);

			jsonArray.put(jsonObject);
		}

		return jsonArray;
	}

	private void _countCoverage(
		SearchEvalExportResult searchEvalExportResult,
		com.tensoropt.search.eval.logger.model.SearchHit searchHit,
		JSONObject extraFieldsJSONObject) {

		searchEvalExportResult.incrementHitCount();

		if (Validator.isNotNull(searchHit.getTitle())) {
			searchEvalExportResult.incrementFieldCount(
				SearchEvalLoggerConstants.FIELD_TITLE);
		}

		if (Validator.isNotNull(searchHit.getSnippet())) {
			searchEvalExportResult.incrementFieldCount(
				SearchEvalLoggerConstants.FIELD_SNIPPET);
		}

		for (String fieldName : extraFieldsJSONObject.keySet()) {
			if (Validator.isNotNull(
					extraFieldsJSONObject.getString(fieldName))) {

				searchEvalExportResult.incrementFieldCount(fieldName);
			}
		}
	}

	private static final TransactionConfig _TRANSACTION_CONFIG =
		TransactionConfig.Factory.create(
			Propagation.REQUIRED, new Class<?>[] {Exception.class});

	private static final String _EVENTS_FILE_NAME = "events.jsonl";

	private static final String _EVENT_TEMPLATE =
		"{\"event_id\":null,\"created_at\":null,\"query\":null," +
			"\"query_truncated\":false,\"locale\":null,\"scope_group_ids\":[]," +
				"\"entry_class_names\":[],\"applied_facets\":null," +
					"\"facet_capture_status\":null,\"blueprint_id\":null," +
						"\"audience_type\":null,\"cohort_hash\":null," +
							"\"requested_size\":0,\"requested_from\":0," +
								"\"total_hits\":0,\"logged_hit_count\":0," +
									"\"source_type\":null,\"hits\":[]}";

	private static final String _HIT_TEMPLATE =
		"{\"rank\":0,\"score\":0,\"doc_uid\":null,\"entry_class_name\":null," +
			"\"entry_class_pk\":0,\"title\":null,\"snippet\":null," +
				"\"extra_fields\":{}}";

	/**
	 * Rows fetched per round trip. Small enough that one round trip's events
	 * are trivially sized, large enough that a ninety day export is not a query
	 * per row.
	 */
	private static final int _INTERVAL = 100;

	private static final String _MANIFEST_FILE_NAME = "manifest.json";

	private static final String _NEW_LINE = "\n";

	private static final String _README_FILE_NAME = "README.md";

	private static final Log _log = LogFactoryUtil.getLog(
		SearchEvalExportWriter.class);

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private SearchEvalExportManifestBuilder _searchEvalExportManifestBuilder;

	@Reference
	private SearchEvalExportReadmeBuilder _searchEvalExportReadmeBuilder;

	@Reference
	private SearchEventLocalService _searchEventLocalService;

	@Reference
	private SearchHitPersistence _searchHitPersistence;

}
