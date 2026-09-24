/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.export;

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

import ai.tensoropt.sel.api.SearchEvalLoggerStatistics;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

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
		when(
			_jsonFactory.createJSONArray()
		).thenReturn(
			mock(com.liferay.portal.kernel.json.JSONArray.class, RETURNS_SELF)
		);

		_searchEvalLoggerStatistics = mock(SearchEvalLoggerStatistics.class);

		_searchEvalExportManifestBuilder = new SearchEvalExportManifestBuilder();

		_searchEvalExportManifestBuilder.setClock(
			Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC));
		_searchEvalExportManifestBuilder.setJSONFactory(_jsonFactory);
		_searchEvalExportManifestBuilder.setSearchEvalLoggerStatistics(
			_searchEvalLoggerStatistics);
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

		verify(_jsonObject).put("observed_search_count", 18L);
		verify(_jsonObject).put("keyword_search_count", 10L);
		verify(_jsonObject).put("admitted_search_count", 9L);
		verify(_jsonObject).put("dispatched_event_count", 9L);
		verify(_jsonObject).put("dropped_event_count", 1L);
		verify(_jsonObject).put("persisted_event_count", 8L);

		// The counters describe the process, not the exported range, and an
		// evaluator who reads them as a figure for the range would draw the
		// wrong conclusion from them. The caveat travels with the numbers.

		verify(_jsonObject).put(
			org.mockito.ArgumentMatchers.eq("scope"), anyString());
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

	private void _build() {
		_build(new Date(0), new Date(1));
	}

	private void _build(Date startDate, Date endDate) {
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
			1L, startDate, endDate, new SearchEvalExportResult(),
			searchEvalLoggerConfiguration);
	}

	private SearchEvalExportManifestBuilder _searchEvalExportManifestBuilder;
	private JSONFactory _jsonFactory;
	private JSONObject _jsonObject;
	private SearchEvalLoggerStatistics _searchEvalLoggerStatistics;

}
