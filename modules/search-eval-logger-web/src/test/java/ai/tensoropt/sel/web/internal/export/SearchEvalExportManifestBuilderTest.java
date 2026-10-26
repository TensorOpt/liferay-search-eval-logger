/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONException;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;

import ai.tensoropt.sel.api.AudienceType;
import ai.tensoropt.sel.api.FacetCaptureStatus;
import ai.tensoropt.sel.api.SearchEvalLoggerConstants;
import ai.tensoropt.sel.api.SearchEvalLoggerStatistics;
import ai.tensoropt.sel.api.SourceType;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.invocation.InvocationOnMock;
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
	 * Unlike captured_field_names, excluded_entry_class_names must reach the
	 * manifest raw (TO-144): AdmissionFilter compares a configured excluded
	 * entry class name as-is, with no trimming of its own, so a manifest
	 * that trimmed it would claim a class is excluded that the filter never
	 * actually matches.
	 */
	@Test
	public void excludedEntryClassNamesReachTheManifestUntrimmed() {
		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration = mock(
			SearchEvalLoggerConfiguration.class);

		when(
			searchEvalLoggerConfiguration.capturedFieldNames()
		).thenReturn(
			new String[0]
		);
		when(
			searchEvalLoggerConfiguration.excludedEntryClassNames()
		).thenReturn(
			new String[] {" com.example.Excluded "}
		);

		_searchEvalExportManifestBuilder.build(
			1L, new Date(0), new Date(1), new SearchEvalExportResult(),
			searchEvalLoggerConfiguration);

		verify(_jsonArray).put(" com.example.Excluded ");
		verify(_jsonArray, never()).put("com.example.Excluded");
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
			eq("plugin_version"), nullable(String.class)
		);
		verify(
			_jsonObject
		).put(
			eq("liferay_version"), nullable(String.class)
		);
	}

	/**
	 * Every test above pins that one key is put with its value; none of
	 * them would fail if a key were renamed or a new one added alongside
	 * it, since each is a positive verify() against a loosely stubbed,
	 * shared mock. This backs the built JSONObject with a real map
	 * (the same fake FacetExtractorTest and SearchEventCaptorTest use, for
	 * the same reason), so the exact set of keys at both the top level and
	 * inside "configuration" can be read back and compared, not merely
	 * queried for one key's presence.
	 */
	@Test
	public void topLevelAndConfigurationKeysAreExactlyThese()
		throws JSONException {

		JSONFactory jsonFactory = _newJSONFactory();

		SearchEvalExportManifestBuilder searchEvalExportManifestBuilder =
			new SearchEvalExportManifestBuilder();

		searchEvalExportManifestBuilder.setClock(
			Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC));
		searchEvalExportManifestBuilder.setJSONFactory(jsonFactory);
		searchEvalExportManifestBuilder.setSearchEvalLoggerStatistics(
			mock(SearchEvalLoggerStatistics.class));

		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration = mock(
			SearchEvalLoggerConfiguration.class);

		when(
			searchEvalLoggerConfiguration.capturedFieldNames()
		).thenReturn(
			new String[] {"title"}
		);
		when(
			searchEvalLoggerConfiguration.excludedEntryClassNames()
		).thenReturn(
			new String[0]
		);

		JSONObject jsonObject = searchEvalExportManifestBuilder.build(
			1L, new Date(0), new Date(1), new SearchEvalExportResult(),
			searchEvalLoggerConfiguration);

		assertEquals(_TOP_LEVEL_KEYS, jsonObject.keySet());
		assertEquals(
			_CONFIGURATION_KEYS,
			jsonObject.getJSONObject("configuration").keySet());
	}

	/**
	 * AudienceType, SourceType and FacetCaptureStatus each name an enum
	 * whose {@code name()} is both persisted (SEL_SearchEvent's
	 * corresponding column) and exported (events.jsonl's corresponding
	 * field), so renaming, adding or removing a constant changes data this
	 * plugin has already written and archives it has already produced, not
	 * just this test. Pinned here, by name, with the export-format tests,
	 * rather than back in the api module as a literal-against-itself
	 * assertion with nothing independent to compare against (TO-144
	 * deleted the three per-enum tests that did exactly that).
	 */
	@Test
	public void theEnumNamesPersistedAndExportedAreExactlyThese() {
		assertEquals(
			Set.of("AUTHENTICATED", "GUEST"),
			_namesOf(AudienceType.values()));
		assertEquals(
			Set.of("HEADLESS", "OTHER", "UNKNOWN", "WIDGET"),
			_namesOf(SourceType.values()));
		assertEquals(
			Set.of("CAPTURED", "NONE_APPLIED", "UNAVAILABLE"),
			_namesOf(FacetCaptureStatus.values()));
	}

	private Set<String> _namesOf(Enum<?>[] values) {
		return Arrays.stream(
			values
		).map(
			Enum::name
		).collect(
			Collectors.toSet()
		);
	}

	/**
	 * Backed by real maps rather than Liferay's own JSONObjectImpl, which
	 * needs the portal's property bootstrap (PropsUtil) just to construct,
	 * the same approach SearchEventCaptorTest and FacetExtractorTest use for
	 * the same reason. Unlike the RETURNS_SELF mock {@code setUp} wires up
	 * for every other test in this class, each object this factory creates
	 * is its own, distinct mock with its own backing map, so a key set read
	 * back from one is never polluted by another object's puts.
	 */
	private JSONFactory _newJSONFactory() throws JSONException {
		JSONFactory jsonFactory = mock(JSONFactory.class);

		when(
			jsonFactory.createJSONObject()
		).thenAnswer(
			invocation -> _newJSONObject()
		);
		when(
			jsonFactory.createJSONObject(anyString())
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
			jsonObject.put(anyString(), anyBoolean())
		).thenAnswer(
			invocation -> _put(backing, jsonObject, invocation)
		);
		when(
			jsonObject.put(anyString(), anyDouble())
		).thenAnswer(
			invocation -> _put(backing, jsonObject, invocation)
		);
		when(
			jsonObject.put(anyString(), anyInt())
		).thenAnswer(
			invocation -> _put(backing, jsonObject, invocation)
		);
		when(
			jsonObject.put(anyString(), any(JSONArray.class))
		).thenAnswer(
			invocation -> _put(backing, jsonObject, invocation)
		);
		when(
			jsonObject.put(anyString(), any(JSONObject.class))
		).thenAnswer(
			invocation -> _put(backing, jsonObject, invocation)
		);
		when(
			jsonObject.put(anyString(), any(Object.class))
		).thenAnswer(
			invocation -> _put(backing, jsonObject, invocation)
		);
		when(
			jsonObject.put(anyString(), nullable(String.class))
		).thenAnswer(
			invocation -> _put(backing, jsonObject, invocation)
		);
		when(
			jsonObject.keySet()
		).thenAnswer(
			invocation -> backing.keySet()
		);
		when(
			jsonObject.getJSONObject(anyString())
		).thenAnswer(
			invocation -> backing.get(invocation.getArgument(0))
		);

		return jsonObject;
	}

	private JSONObject _put(
		Map<String, Object> backing, JSONObject jsonObject,
		InvocationOnMock invocation) {

		backing.put(invocation.getArgument(0), invocation.getArgument(1));

		return jsonObject;
	}

	private JSONArray _newJSONArray() {
		return mock(JSONArray.class, RETURNS_SELF);
	}

	private static final Set<String> _CONFIGURATION_KEYS = Set.of(
		"enabled", "capture_depth", "retention_days", "captured_field_names",
		"exclude_suggestion_traffic", "admit_facet_only_searches",
		"require_web_request_context", "query_text_cap", "sampling_rate",
		"excluded_entry_class_names", "cohort_salt_rotation_days");

	private static final Set<String> _TOP_LEVEL_KEYS = Set.of(
		"company_id", "exported_at", "export_range", "counts",
		"format_version", "plugin_version", "liferay_version",
		"configuration", "admission_counters", "field_coverage_rates");

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
