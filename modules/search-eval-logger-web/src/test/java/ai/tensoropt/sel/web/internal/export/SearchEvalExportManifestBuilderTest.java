/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.export;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.json.JSONException;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;

import ai.tensoropt.sel.api.SearchEvalLoggerConstants;
import ai.tensoropt.sel.api.SearchEvalLoggerStatistics;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import java.util.Date;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.osgi.framework.Version;

/**
 * The manifest is the only place an evaluator can see how narrow a slice of
 * search traffic the archive represents (DESIGN.md 6.2, EC-10). A counter that
 * silently stops being written would leave the archive looking like a census,
 * which is the one thing the manifest exists to prevent, so each one is
 * asserted by name.
 */
public class SearchEvalExportManifestBuilderTest {

	@BeforeEach
	public void setUp() throws Exception {
		_jsonObject = mock(JSONObject.class, RETURNS_SELF);

		_jsonFactory = mock(JSONFactory.class);

		when(_jsonFactory.createJSONObject()).thenReturn(_jsonObject);
		when(_jsonFactory.createJSONObject(anyString())).thenReturn(_jsonObject);
		_jsonArray = mock(
			com.liferay.portal.kernel.json.JSONArray.class, RETURNS_SELF);

		when(_jsonFactory.createJSONArray()).thenReturn(_jsonArray);

		_searchEvalLoggerStatistics = mock(SearchEvalLoggerStatistics.class);

		_searchEvalExportManifestBuilder = new SearchEvalExportManifestBuilder();

		_searchEvalExportManifestBuilder.setClock(
			Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC));
		_searchEvalExportManifestBuilder.setJSONFactory(_jsonFactory);
		_searchEvalExportManifestBuilder.setSearchEvalLoggerStatistics(
			_searchEvalLoggerStatistics);
	}

	@Test
	public void formatVersionIsTheMajorMinorAPatchLeavesAlone() {
		Assertions.assertEquals(
			"1.0",
			SearchEvalExportManifestBuilder.formatVersion(new Version(1, 0, 0)));
		Assertions.assertEquals(
			"1.0",
			SearchEvalExportManifestBuilder.formatVersion(new Version(1, 0, 1)));
		Assertions.assertEquals(
			"1.1",
			SearchEvalExportManifestBuilder.formatVersion(new Version(1, 1, 0)));
		Assertions.assertNull(SearchEvalExportManifestBuilder.formatVersion(null));
	}

	@Test
	public void wholeAdmissionFunnelReachesTheManifest() {
		when(_searchEvalLoggerStatistics.getObservedSearchCount()).thenReturn(
			18L);
		when(_searchEvalLoggerStatistics.getKeywordSearchCount()).thenReturn(
			10L);
		when(_searchEvalLoggerStatistics.getAdmittedSearchCount()).thenReturn(
			9L);
		when(_searchEvalLoggerStatistics.getDispatchedEventCount()).thenReturn(
			9L);
		when(_searchEvalLoggerStatistics.getDroppedEventCount()).thenReturn(1L);
		when(_searchEvalLoggerStatistics.getPersistedEventCount()).thenReturn(
			8L);

		_build();

		verify(_jsonObject).put("observed_search_count", (Object)18L);
		verify(_jsonObject).put("keyword_search_count", (Object)10L);
		verify(_jsonObject).put("admitted_search_count", (Object)9L);
		verify(_jsonObject).put("dispatched_event_count", (Object)9L);
		verify(_jsonObject).put("dropped_event_count", (Object)1L);
		verify(_jsonObject).put("persisted_event_count", (Object)8L);

		// The counters describe the process, not the exported range, and an
		// evaluator who reads them as a figure for the range would draw the
		// wrong conclusion from them. The caveat travels with the numbers.

		verify(_jsonObject).put(
			org.mockito.ArgumentMatchers.eq("scope"), anyString());
	}

	/**
	 * DESIGN.md 6.2 types ids and counts as JSON numbers, and Liferay's
	 * <code>put(String, long)</code> writes them as strings (D-2). Every long
	 * has to reach the <code>Object</code> overload as a <code>Long</code>, so
	 * the <code>long</code> overload is asserted never to be called at all:
	 * that also catches a long field added later without boxing.
	 */
	@Test
	public void longValuesAreWrittenAsNumbers() {
		SearchEvalExportResult searchEvalExportResult =
			new SearchEvalExportResult();

		searchEvalExportResult.incrementEventCount();
		searchEvalExportResult.incrementHitCount();
		searchEvalExportResult.incrementHitCount();

		when(_searchEvalLoggerStatistics.getObservedSearchCount()).thenReturn(
			18L);

		_build(new Date(0), new Date(1), searchEvalExportResult);

		verify(_jsonObject).put("company_id", (Object)1L);
		verify(_jsonObject).put("events", (Object)1L);
		verify(_jsonObject).put("hits", (Object)2L);
		verify(_jsonObject).put("observed_search_count", (Object)18L);

		verify(_jsonObject, never()).put(anyString(), anyLong());
	}

	/**
	 * A bound the administrator left empty is a range that reaches that far,
	 * and the manifest is the only place the archive says how far it reaches.
	 *
	 * <p>
	 * Liferay's JSONObject drops a key whose value is null, so writing the
	 * absent bound straight through would leave <code>export_range</code> as an
	 * empty object and a consumer could not tell an unbounded export from a
	 * manifest that never recorded a range. The keys come from a template that
	 * carries both of them explicitly null.
	 * </p>
	 */
	@Test
	public void unboundedRangeKeepsBothKeysInTheManifest() throws Exception {
		_build(null, null);

		verify(
			_jsonFactory
		).createJSONObject(
			ArgumentMatchers.<String>argThat(
				template ->
					template.contains("\"from\":null") &&
					template.contains("\"to\":null"))
		);

		verify(_jsonObject, never()).put(eq("from"), anyString());
		verify(_jsonObject, never()).put(eq("to"), anyString());
	}

	@Test
	public void boundedRangeReportsBothEnds() {
		_build(
			Date.from(Instant.parse("2026-08-24T00:00:00Z")),
			Date.from(Instant.parse("2026-09-24T00:00:00Z")));

		verify(_jsonObject).put("from", "2026-08-24T00:00:00Z");
		verify(_jsonObject).put("to", "2026-09-24T00:00:00Z");
	}

	/**
	 * One bound left empty is the combination an administrator reaches by
	 * accident, and it must not take the other one with it.
	 */
	@Test
	public void halfBoundedRangeReportsTheEndItHas() {
		_build(null, Date.from(Instant.parse("2026-09-24T00:00:00Z")));

		verify(_jsonObject, never()).put(eq("from"), anyString());
		verify(_jsonObject).put("to", "2026-09-24T00:00:00Z");
	}

	/**
	 * Everything from a date onward: the other half bounded combination, and
	 * the likelier of the two.
	 */
	@Test
	public void halfBoundedRangeReportsTheStartItHas() {
		_build(Date.from(Instant.parse("2026-08-24T00:00:00Z")), null);

		verify(_jsonObject).put("from", "2026-08-24T00:00:00Z");
		verify(_jsonObject, never()).put(eq("to"), anyString());
	}

	/**
	 * The template is a compile-time constant, so this branch is unreachable
	 * short of someone editing it into invalid JSON. It is pinned anyway
	 * because it is the one path that still produces the shape D-1's fix
	 * removed, and an untested fallback is how that shape would come back
	 * unnoticed.
	 */
	@Test
	public void anUnparseableTemplateDegradesWithoutThrowing()
		throws Exception {

		when(
			_jsonFactory.createJSONObject(anyString())
		).thenThrow(
			new JSONException("not JSON")
		);

		_build(Date.from(Instant.parse("2026-08-24T00:00:00Z")), null);

		verify(_jsonObject).put("from", "2026-08-24T00:00:00Z");
	}

	/**
	 * F2-12: the writer trims a captured field name before counting coverage
	 * under it, so the manifest has to report the same trimmed name, or the
	 * two would disagree about what "title" covers whenever the configuration
	 * carries incidental whitespace.
	 */
	@Test
	public void capturedFieldNamesAreTrimmedBeforeTheyReachTheManifest() {
		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration = mock(
			SearchEvalLoggerConfiguration.class);

		when(
			searchEvalLoggerConfiguration.capturedFieldNames()
		).thenReturn(
			new String[] {" title ", "snippet"}
		);
		when(
			searchEvalLoggerConfiguration.excludedEntryClassNames()
		).thenReturn(
			new String[0]
		);

		_searchEvalExportManifestBuilder.build(
			1L, new Date(0), new Date(1), new SearchEvalExportResult(),
			searchEvalLoggerConfiguration);

		verify(_jsonArray).put("title");
		verify(_jsonArray, never()).put(" title ");
	}

	/**
	 * F2-26: every key DESIGN.md section 5's configuration surface puts into
	 * the manifest, pinned by name and by the specific value a distinctly
	 * configured mock produces for it. The fixture's JSONFactory returns one
	 * shared mocked JSONObject for every object the builder creates (see
	 * setUp), so this cannot isolate the configuration sub-object's calls
	 * from the rest of the manifest's; it pins that each key below is put
	 * with its configured value at least once, which is what would break if
	 * _getConfiguration dropped or renamed a key.
	 */
	@Test
	public void configurationBlockCarriesEveryKeyDesignDocSpecifies() {
		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration = mock(
			SearchEvalLoggerConfiguration.class);

		when(searchEvalLoggerConfiguration.enabled()).thenReturn(true);
		when(searchEvalLoggerConfiguration.captureDepth()).thenReturn(42);
		when(searchEvalLoggerConfiguration.retentionDays()).thenReturn(91);
		when(
			searchEvalLoggerConfiguration.capturedFieldNames()
		).thenReturn(
			new String[] {"title"}
		);
		when(
			searchEvalLoggerConfiguration.excludeSuggestionTraffic()
		).thenReturn(
			true
		);
		when(
			searchEvalLoggerConfiguration.admitFacetOnlySearches()
		).thenReturn(
			true
		);
		when(
			searchEvalLoggerConfiguration.requireWebRequestContext()
		).thenReturn(
			true
		);
		when(searchEvalLoggerConfiguration.queryTextCap()).thenReturn(1234);
		when(searchEvalLoggerConfiguration.samplingRate()).thenReturn(0.5);
		when(
			searchEvalLoggerConfiguration.excludedEntryClassNames()
		).thenReturn(
			new String[] {"com.example.Excluded"}
		);
		when(
			searchEvalLoggerConfiguration.cohortSaltRotationDays()
		).thenReturn(
			14
		);

		_searchEvalExportManifestBuilder.build(
			1L, new Date(0), new Date(1), new SearchEvalExportResult(),
			searchEvalLoggerConfiguration);

		verify(_jsonObject).put("enabled", true);
		verify(_jsonObject).put("capture_depth", 42);
		verify(_jsonObject).put("retention_days", 91);
		verify(_jsonObject).put("exclude_suggestion_traffic", true);
		verify(_jsonObject).put("admit_facet_only_searches", true);
		verify(_jsonObject).put("require_web_request_context", true);
		verify(_jsonObject).put("query_text_cap", 1234);
		verify(_jsonObject).put("sampling_rate", 0.5);
		verify(_jsonObject).put("cohort_salt_rotation_days", 14);
		verify(
			_jsonObject
		).put(
			eq("captured_field_names"),
			any(com.liferay.portal.kernel.json.JSONArray.class)
		);
		verify(
			_jsonObject
		).put(
			eq("excluded_entry_class_names"),
			any(com.liferay.portal.kernel.json.JSONArray.class)
		);
	}

	/**
	 * F2-26: a field registered with the writer but never actually covered
	 * by a hit still reports a rate, of zero, rather than being left out of
	 * field_coverage_rates. Dropping it would read as "never asked for"
	 * instead of "asked for and never present", which is the whole reason
	 * registerField exists.
	 */
	@Test
	public void fieldCoverageRatesReportsAZeroOccurrenceRegisteredFieldAsZero() {
		SearchEvalExportResult searchEvalExportResult =
			new SearchEvalExportResult();

		searchEvalExportResult.incrementHitCount();
		searchEvalExportResult.incrementFieldCount(
			SearchEvalLoggerConstants.FIELD_TITLE);
		searchEvalExportResult.registerField(
			SearchEvalLoggerConstants.FIELD_SNIPPET);

		_build(new Date(0), new Date(1), searchEvalExportResult);

		verify(
			_jsonObject
		).put(
			SearchEvalLoggerConstants.FIELD_TITLE, (Object)Double.valueOf(1.0)
		);
		verify(
			_jsonObject
		).put(
			SearchEvalLoggerConstants.FIELD_SNIPPET,
			(Object)Double.valueOf(0.0)
		);
	}

	/**
	 * F2-26: both version keys are always present, even though this test has
	 * no real OSGi bundle (plugin_version) or running portal (liferay_version)
	 * to produce a meaningful value from. The manifest must still carry the
	 * key, exactly as formatVersionIsTheMajorMinorAPatchLeavesAlone already
	 * pins format_version to null rather than an absent key in that case.
	 */
	@Test
	public void bothVersionKeysArePresent() {
		_build();

		verify(
			_jsonObject
		).put(
			eq("plugin_version"), org.mockito.ArgumentMatchers.nullable(
				String.class)
		);
		verify(
			_jsonObject
		).put(
			eq("liferay_version"), org.mockito.ArgumentMatchers.nullable(
				String.class)
		);
	}

	private void _build() {
		_build(new Date(0), new Date(1));
	}

	private void _build(Date startDate, Date endDate) {
		_build(startDate, endDate, new SearchEvalExportResult());
	}

	private void _build(
		Date startDate, Date endDate,
		SearchEvalExportResult searchEvalExportResult) {

		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration = mock(
			SearchEvalLoggerConfiguration.class);

		when(searchEvalLoggerConfiguration.capturedFieldNames()).thenReturn(
			new String[] {"title", "snippet"});
		when(
			searchEvalLoggerConfiguration.excludedEntryClassNames()
		).thenReturn(
			new String[0]
		);

		_searchEvalExportManifestBuilder.build(
			1L, startDate, endDate, searchEvalExportResult,
			searchEvalLoggerConfiguration);
	}

	private SearchEvalExportManifestBuilder _searchEvalExportManifestBuilder;
	private JSONFactory _jsonFactory;
	private com.liferay.portal.kernel.json.JSONArray _jsonArray;
	private JSONObject _jsonObject;
	private SearchEvalLoggerStatistics _searchEvalLoggerStatistics;

}
