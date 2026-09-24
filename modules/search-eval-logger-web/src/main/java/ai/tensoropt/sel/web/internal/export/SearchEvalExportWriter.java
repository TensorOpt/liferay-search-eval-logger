/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.export;

import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import ai.tensoropt.sel.api.SearchEvalLoggerConstants;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.service.SearchEventLocalService;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;

import java.util.Date;
import java.util.Objects;
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
 * result set: rows arrive one at a time from a JDBC cursor over an event and
 * hit join (<code>SearchEventLocalService.forEachExportRow</code>, which fetches
 * them from the database in batches of its own fetch size), each event is
 * serialized to a line once its last hit has arrived and written through a
 * buffered writer onto the {@link ZipOutputStream}, and the only rows held at
 * once are the hits of the event being written, which capture depth already
 * bounds. What bounds memory is that no line is retained after it is
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
 *
 * <p>
 * Every <code>long</code> is boxed before it is written. Liferay's
 * <code>JSONObject.put(String, long)</code> and <code>JSONArray.put(long)</code>
 * both store <code>String.valueOf</code> of the value, so an unboxed long comes
 * out quoted, against the numbers DESIGN.md 6.2 shows. The
 * <code>Object</code> overloads pass a <code>Long</code> through untouched. The
 * boxing looks redundant and is not; {@link SearchEvalExportManifestBuilder}
 * does the same.
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
		return ExportJSONTemplate.create(_jsonFactory, template);
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
				jsonArray.put(Long.valueOf(GetterUtil.getLong(part)));
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


	private void _writeEvents(
			ZipOutputStream zipOutputStream, long companyId, Date startDate,
			Date endDate, SearchEvalExportResult searchEvalExportResult)
		throws Exception {

		// Deliberately not closed: closing the writer would close the
		// ZipOutputStream under it, and the manifest and README still have to
		// be written into the same archive.

		Writer writer = new BufferedWriter(
			new OutputStreamWriter(zipOutputStream, StandardCharsets.UTF_8));

		// One ordered pass. The rows arrive grouped by event and ordered by
		// rank, so an event is complete the moment its uuid changes, and only
		// the current event's hits are held. That is what keeps memory flat
		// (DESIGN.md 6.1) without buffering the result set.

		EventAccumulator eventAccumulator = new EventAccumulator(
			writer, searchEvalExportResult);

		_searchEventLocalService.forEachExportRow(
			companyId, startDate, endDate,
			resultSet -> eventAccumulator.accept(resultSet));

		eventAccumulator.flushPending();

		writer.flush();
	}

	private class EventAccumulator {

		private EventAccumulator(
			Writer writer, SearchEvalExportResult searchEvalExportResult) {

			_writer = writer;
			_searchEvalExportResult = searchEvalExportResult;
		}

		private void accept(ResultSet resultSet) throws Exception {
			String uuid = resultSet.getString("uuid_");

			if (!Objects.equals(uuid, _currentUuid)) {
				flushPending();

				_currentUuid = uuid;
				_currentEventJSONObject = _toEventJSONObject(resultSet);
				_currentHitsJSONArray = _jsonFactory.createJSONArray();
			}

			// A left join yields one row with null hit columns for an event
			// that returned nothing, which must not become a phantom hit.

			if (resultSet.getString("docUid") != null) {
				_currentHitsJSONArray.put(
					_toHitJSONObject(resultSet, _searchEvalExportResult));
			}
		}

		private void flushPending() throws Exception {
			if (_currentEventJSONObject == null) {
				return;
			}

			_currentEventJSONObject.put("hits", _currentHitsJSONArray);

			_writer.write(_currentEventJSONObject.toString());
			_writer.write(_NEW_LINE);

			_searchEvalExportResult.incrementEventCount();

			_currentEventJSONObject = null;
			_currentHitsJSONArray = null;
		}

		private JSONArray _currentHitsJSONArray;
		private JSONObject _currentEventJSONObject;
		private String _currentUuid;
		private final SearchEvalExportResult _searchEvalExportResult;
		private final Writer _writer;

	}

	private JSONObject _toEventJSONObject(ResultSet resultSet)
		throws Exception {

		JSONObject jsonObject = _createFromTemplate(_EVENT_TEMPLATE);

		_put(jsonObject, "event_id", resultSet.getString("uuid_"));
		_put(
			jsonObject, "created_at",
			ExportTimestamps.format(resultSet.getTimestamp("createDate")));
		_put(jsonObject, "query", resultSet.getString("queryText"));
		jsonObject.put(
			"query_truncated", resultSet.getBoolean("queryTruncated"));
		_put(jsonObject, "locale", resultSet.getString("locale"));
		jsonObject.put(
			"scope_group_ids",
			_toJSONArray(resultSet.getString("scopeGroupIds"), true));
		jsonObject.put(
			"entry_class_names",
			_toJSONArray(resultSet.getString("entryClassNames"), false));
		_put(
			jsonObject, "applied_facets",
			_toJSONObject(resultSet.getString("appliedFacets")));
		_put(
			jsonObject, "facet_capture_status",
			resultSet.getString("facetCaptureStatus"));
		_put(jsonObject, "blueprint_id", resultSet.getString("blueprintId"));
		_put(jsonObject, "audience_type", resultSet.getString("audienceType"));
		_put(jsonObject, "cohort_hash", resultSet.getString("cohortHash"));
		jsonObject.put("requested_size", resultSet.getInt("requestedSize"));
		jsonObject.put("requested_from", resultSet.getInt("requestedFrom"));
		jsonObject.put(
			"total_hits", Long.valueOf(resultSet.getLong("totalHits")));
		jsonObject.put(
			"logged_hit_count", resultSet.getInt("loggedHitCount"));
		_put(jsonObject, "source_type", resultSet.getString("sourceType"));

		return jsonObject;
	}

	private JSONObject _toHitJSONObject(
			ResultSet resultSet,
			SearchEvalExportResult searchEvalExportResult)
		throws Exception {
		JSONObject jsonObject = _createFromTemplate(_HIT_TEMPLATE);

		jsonObject.put("rank", resultSet.getInt("rank_"));
		jsonObject.put("score", resultSet.getDouble("score"));
		_put(jsonObject, "doc_uid", resultSet.getString("docUid"));
		_put(
			jsonObject, "entry_class_name",
			resultSet.getString("entryClassName"));
		jsonObject.put(
			"entry_class_pk", Long.valueOf(resultSet.getLong("entryClassPK")));
		_put(jsonObject, "title", resultSet.getString("title"));
		_put(jsonObject, "snippet", resultSet.getString("snippet"));

		JSONObject extraFieldsJSONObject = _toJSONObject(
			resultSet.getString("extraFields"));

		if (extraFieldsJSONObject == null) {
			extraFieldsJSONObject = _jsonFactory.createJSONObject();
		}

		jsonObject.put("extra_fields", extraFieldsJSONObject);

		_countCoverage(
			searchEvalExportResult, resultSet, extraFieldsJSONObject);

		return jsonObject;
	}


	private void _countCoverage(
			SearchEvalExportResult searchEvalExportResult,
			ResultSet resultSet, JSONObject extraFieldsJSONObject)
		throws Exception {

		searchEvalExportResult.incrementHitCount();

		if (Validator.isNotNull(resultSet.getString("title"))) {
			searchEvalExportResult.incrementFieldCount(
				SearchEvalLoggerConstants.FIELD_TITLE);
		}

		if (Validator.isNotNull(resultSet.getString("snippet"))) {
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


}
