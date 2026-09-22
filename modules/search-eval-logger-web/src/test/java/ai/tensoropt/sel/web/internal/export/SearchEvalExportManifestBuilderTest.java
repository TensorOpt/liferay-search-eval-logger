/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.export;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

/**
 * The manifest is the only place an evaluator can see how narrow a slice of
 * search traffic the archive represents (DESIGN.md 6.2, EC-10). A counter that
 * silently stops being written would leave the archive looking like a census,
 * which is the one thing the manifest exists to prevent, so each one is
 * asserted by name.
 */
public class SearchEvalExportManifestBuilderTest {

	@BeforeEach
	public void setUp() {
		_jsonObject = mock(JSONObject.class, RETURNS_SELF);

		JSONFactory jsonFactory = mock(JSONFactory.class);

		when(jsonFactory.createJSONObject()).thenReturn(_jsonObject);
		when(
			jsonFactory.createJSONArray()
		).thenReturn(
			mock(com.liferay.portal.kernel.json.JSONArray.class, RETURNS_SELF)
		);

		_searchEvalLoggerStatistics = mock(SearchEvalLoggerStatistics.class);

		_searchEvalExportManifestBuilder = new SearchEvalExportManifestBuilder();

		_searchEvalExportManifestBuilder.setClock(
			Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC));
		_searchEvalExportManifestBuilder.setJSONFactory(jsonFactory);
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

	private void _build() {
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
			1L, new Date(0), new Date(1), new SearchEvalExportResult(),
			searchEvalLoggerConfiguration);
	}

	private SearchEvalExportManifestBuilder _searchEvalExportManifestBuilder;
	private JSONObject _jsonObject;
	private SearchEvalLoggerStatistics _searchEvalLoggerStatistics;

}
