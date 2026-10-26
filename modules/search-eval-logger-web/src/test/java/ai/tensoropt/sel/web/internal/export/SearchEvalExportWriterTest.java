/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.petra.function.UnsafeConsumer;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONException;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;

import ai.tensoropt.sel.api.SearchEvalLoggerStatistics;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.service.SearchEventLocalService;

import java.io.File;

import java.nio.file.Files;

import java.sql.ResultSet;
import java.sql.SQLException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link SearchEvalExportWriter#write} is where streamed rows become the
 * archive of DESIGN.md 6.2, and most of what can go wrong is in the per-row
 * decisions: when an event is complete, when a field is absent rather than
 * blank, and when a stored JSON column is counted as covered.
 *
 * <p>
 * The JSON engine is mocked the same way {@link
 * SearchEvalExportManifestBuilderTest} mocks it, since this codebase has no
 * real, non-portal {@code JSONFactory} implementation to serialize against,
 * so a case about JSON shape is asserted by what was put onto the mocked
 * object or array, not by parsing real JSON text back out of the archive.
 * Every count and coverage claim, however, is asserted on the real,
 * unmocked {@link SearchEvalExportResult} {@code write} returns, which is a
 * plain counter object untouched by the JSON mocking.
 * </p>
 */
public class SearchEvalExportWriterTest {

	@BeforeEach
	public void setUp() throws Exception {
		_jsonObject = mock(JSONObject.class, RETURNS_SELF);
		_jsonArray = mock(JSONArray.class, RETURNS_SELF);

		_jsonFactory = mock(JSONFactory.class);

		when(_jsonFactory.createJSONObject()).thenReturn(_jsonObject);
		when(_jsonFactory.createJSONObject(anyString())).thenReturn(_jsonObject);
		when(_jsonFactory.createJSONArray()).thenReturn(_jsonArray);

		// RETURNS_SELF only substitutes the mock itself for a method whose
		// return type matches the mock's own type; toString() returns
		// String, so it falls through to Mockito's plain default (null for
		// an unstubbed String), and the writer's buffered Writer#write(null)
		// would NPE. This stand-in string is never asserted on for content:
		// every behavioral assertion here reads the real,
		// unmocked SearchEvalExportResult the writer returns instead.

		when(_jsonObject.toString()).thenReturn("{}");

		_searchEventLocalService = mock(SearchEventLocalService.class);
		_searchEvalLoggerConfiguration = mock(
			SearchEvalLoggerConfiguration.class);

		when(
			_searchEvalLoggerConfiguration.capturedFieldNames()
		).thenReturn(
			new String[] {"title", "snippet"}
		);

		SearchEvalLoggerStatistics searchEvalLoggerStatistics = mock(
			SearchEvalLoggerStatistics.class);

		SearchEvalExportManifestBuilder searchEvalExportManifestBuilder =
			new SearchEvalExportManifestBuilder();

		searchEvalExportManifestBuilder.setClock(
			Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC));
		searchEvalExportManifestBuilder.setJSONFactory(_jsonFactory);
		searchEvalExportManifestBuilder.setSearchEvalLoggerStatistics(
			searchEvalLoggerStatistics);

		_searchEvalExportWriter = new SearchEvalExportWriter();

		_searchEvalExportWriter.setJSONFactory(_jsonFactory);
		_searchEvalExportWriter.setSearchEvalExportManifestBuilder(
			searchEvalExportManifestBuilder);
		_searchEvalExportWriter.setSearchEventLocalService(
			_searchEventLocalService);

		_file = Files.createTempFile("search-eval-export-writer-test", ".zip").
			toFile();
	}

	@AfterEach
	public void tearDown() {
		_file.delete();
	}

	@Test
	public void zeroRowsProduceAnEmptyEventsFileAndAWrittenManifest()
		throws Exception {

		_rows();

		SearchEvalExportResult searchEvalExportResult = _write();

		assertEquals(0, searchEvalExportResult.getEventCount());
		assertEquals(0, searchEvalExportResult.getHitCount());

		assertEquals(0, _entryBytes(_EVENTS_FILE_NAME).length);
		assertTrue(_entryBytes(_MANIFEST_FILE_NAME).length > 0);
		assertTrue(_entryBytes(_README_FILE_NAME).length > 0);
	}

	@Test
	public void anEventWithNoHitsEmitsAnEmptyHitsArrayAndNoHitCount()
		throws Exception {

		_rows(_row("event-1").noHit());

		SearchEvalExportResult searchEvalExportResult = _write();

		assertEquals(1, searchEvalExportResult.getEventCount());
		assertEquals(0, searchEvalExportResult.getHitCount());

		verify(_jsonArray, never()).put(any(JSONObject.class));
		verify(_jsonObject).put("hits", _jsonArray);
	}

	/**
	 * Two events, each with one hit: the uuid change between them must
	 * flush the first exactly once, and the last row read must still be
	 * flushed even though no further row changes the uuid again.
	 */
	@Test
	public void aUuidChangeFlushesThePreviousEventExactlyOnceAndTheLastRowIsFlushed()
		throws Exception {

		_rows(
			_row("event-1").hit(1), _row("event-2").hit(1));

		SearchEvalExportResult searchEvalExportResult = _write();

		assertEquals(2, searchEvalExportResult.getEventCount());
		assertEquals(2, searchEvalExportResult.getHitCount());
	}

	/**
	 * A blank title is absence, not a captured empty value: {@code _put}
	 * leaves the template's null in place, and coverage is not counted for
	 * it.
	 */
	@Test
	public void aBlankTitleIsExportedAsAbsentAndNotCounted() throws Exception {
		_rows(_row("event-1").hit(1).title(""));

		SearchEvalExportResult searchEvalExportResult = _write();

		assertEquals(0.0, searchEvalExportResult.getFieldCoverageRates().get(
			"title"));

		verify(_jsonObject, never()).put("title", "");
	}

	/**
	 * A snippet column that fails to parse as JSON is exported as null, not
	 * as the raw text, and is not counted as covered: the export and the
	 * coverage figure must agree about what happened to that value.
	 */
	@Test
	public void anUnparseableSnippetIsExportedNullAndCountedUncovered()
		throws Exception {

		when(
			_jsonFactory.createJSONObject(eq("not-json"))
		).thenThrow(
			new JSONException("not JSON")
		);

		_rows(_row("event-1").hit(1).snippet("not-json"));

		SearchEvalExportResult searchEvalExportResult = _write();

		assertEquals(0.0, searchEvalExportResult.getFieldCoverageRates().get(
			"snippet"));

		verify(_jsonObject, never()).put(eq("snippet"), eq("not-json"));
	}

	/**
	 * extra_fields is counted field by field, and only a non-blank value
	 * counts as coverage for that field.
	 */
	@Test
	public void extraFieldsAreCountedOnlyWhenNonBlank() throws Exception {
		String extraFieldsJSON = "{\"color\":\"\",\"size\":\"large\"}";

		JSONObject extraFieldsJSONObject = mock(JSONObject.class);

		when(
			extraFieldsJSONObject.keySet()
		).thenReturn(
			new LinkedHashSet<>(Arrays.asList("color", "size"))
		);
		when(extraFieldsJSONObject.getString("color")).thenReturn("");
		when(extraFieldsJSONObject.getString("size")).thenReturn("large");

		when(
			_jsonFactory.createJSONObject(eq(extraFieldsJSON))
		).thenReturn(
			extraFieldsJSONObject
		);

		_rows(_row("event-1").hit(1).extraFields(extraFieldsJSON));

		SearchEvalExportResult searchEvalExportResult = _write();

		assertNull(searchEvalExportResult.getFieldCoverageRates().get("color"));
		assertEquals(
			1.0, searchEvalExportResult.getFieldCoverageRates().get("size"));
	}

	/**
	 * scope_group_ids is a comma separated column read as a numeric JSON
	 * array; a blank part between two separators must be skipped rather
	 * than parsed as zero.
	 */
	@Test
	public void toJSONArrayNumericSkipsBlankParts() throws Exception {
		_rows(_row("event-1").scopeGroupIds("1,,3"));

		_write();

		verify(_jsonArray).put(Long.valueOf(1));
		verify(_jsonArray).put(Long.valueOf(3));
		verify(_jsonArray, never()).put(Long.valueOf(0));
	}

	/**
	 * A field registered with the writer's configuration but never actually
	 * present in any hit still appears in the result's coverage, at zero,
	 * rather than being left out.
	 */
	@Test
	public void aRegisteredFieldWithZeroOccurrencesStillAppearsInCoverage()
		throws Exception {

		_rows(_row("event-1").hit(1).title("report"));

		SearchEvalExportResult searchEvalExportResult = _write();

		assertEquals(
			0.0,
			searchEvalExportResult.getFieldCoverageRates().get("snippet"));
	}

	private byte[] _entryBytes(String name) throws Exception {
		try (ZipFile zipFile = new ZipFile(_file)) {
			ZipEntry zipEntry = zipFile.getEntry(name);

			return zipFile.getInputStream(zipEntry).readAllBytes();
		}
	}

	private _RowBuilder _row(String uuid) {
		return new _RowBuilder(uuid);
	}

	/**
	 * Feeds the writer's forEachExportRow callback with the given rows, in
	 * order, the same shape {@code SearchEventLocalService} itself produces:
	 * one row per hit, and one row with a null hit sentinel for an event
	 * that returned nothing.
	 */
	private void _rows(_RowBuilder... rowBuilders) throws Exception {
		doAnswer(
			invocation -> {
				UnsafeConsumer<ResultSet, Exception> rowConsumer =
					invocation.getArgument(3);

				for (_RowBuilder rowBuilder : rowBuilders) {
					rowConsumer.accept(rowBuilder.toResultSet());
				}

				return null;
			}
		).when(
			_searchEventLocalService
		).forEachExportRow(
			anyLong(), any(), any(), any()
		);
	}

	private SearchEvalExportResult _write() throws Exception {
		return _searchEvalExportWriter.write(
			_file, 1L, new Date(0), new Date(1),
			_searchEvalLoggerConfiguration);
	}

	private static final String _EVENTS_FILE_NAME = "events.jsonl";

	private static final String _MANIFEST_FILE_NAME = "manifest.json";

	private static final String _README_FILE_NAME = "README.md";

	private File _file;
	private JSONArray _jsonArray;
	private JSONFactory _jsonFactory;
	private JSONObject _jsonObject;
	private SearchEvalExportWriter _searchEvalExportWriter;
	private SearchEvalLoggerConfiguration _searchEvalLoggerConfiguration;
	private SearchEventLocalService _searchEventLocalService;

	/**
	 * Builds one export row's {@link ResultSet}, column by column, stubbing
	 * only what a given case actually sets; everything else keeps Mockito's
	 * own default (null, zero, or false), which every column SearchEvalExportWriter
	 * reads tolerates.
	 */
	private static class _RowBuilder {

		private _RowBuilder(String uuid) {
			_uuid = uuid;
		}

		private _RowBuilder extraFields(String value) {
			_extraFields = value;

			return this;
		}

		private _RowBuilder hit(int rank) {
			_hasHit = true;
			_rank = rank;

			return this;
		}

		private _RowBuilder noHit() {
			_hasHit = false;

			return this;
		}

		private _RowBuilder scopeGroupIds(String value) {
			_scopeGroupIds = value;

			return this;
		}

		private _RowBuilder snippet(String value) {
			_snippet = value;

			return this;
		}

		private _RowBuilder title(String value) {
			_title = value;

			return this;
		}

		private ResultSet toResultSet() throws SQLException {
			ResultSet resultSet = mock(ResultSet.class);

			when(resultSet.getString("uuid_")).thenReturn(_uuid);
			when(resultSet.getString("scopeGroupIds")).thenReturn(
				_scopeGroupIds);

			if (_hasHit) {
				when(resultSet.getObject("searchHitId")).thenReturn(
					Long.valueOf(_rank));
				when(resultSet.getInt("rank_")).thenReturn(_rank);
				when(resultSet.getString("title")).thenReturn(_title);
				when(resultSet.getString("snippet")).thenReturn(_snippet);
				when(resultSet.getString("extraFields")).thenReturn(
					_extraFields);
			}
			else {
				when(resultSet.getObject("searchHitId")).thenReturn(null);
			}

			return resultSet;
		}

		private String _extraFields;
		private boolean _hasHit;
		private int _rank;
		private String _scopeGroupIds;
		private String _snippet;
		private String _title;
		private final String _uuid;

	}

}
