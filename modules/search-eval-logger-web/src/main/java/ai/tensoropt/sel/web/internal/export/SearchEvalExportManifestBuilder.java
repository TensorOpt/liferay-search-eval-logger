/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.export;

import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.util.ReleaseInfo;
import com.liferay.portal.kernel.util.Validator;

import ai.tensoropt.sel.api.SearchEvalLoggerStatistics;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;

import java.time.Clock;

import java.util.Date;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Builds <code>manifest.json</code> (DESIGN.md 6.2).
 *
 * <p>
 * The manifest is what lets an evaluator judge the dataset before working with
 * it: the coverage rates say whether a judging pass is feasible at all, and the
 * drop count says whether the log is a census or a lossy sample.
 * </p>
 */
@Component(service = SearchEvalExportManifestBuilder.class)
public class SearchEvalExportManifestBuilder {

	public JSONObject build(
		long companyId, Date startDate, Date endDate,
		SearchEvalExportResult searchEvalExportResult,
		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration) {

		JSONObject jsonObject = _jsonFactory.createJSONObject();

		jsonObject.put("company_id", companyId);
		jsonObject.put("exported_at", ExportTimestamps.format(_clock.instant()));
		jsonObject.put(
			"export_range",
			_jsonFactory.createJSONObject(
			).put(
				"from", ExportTimestamps.format(startDate)
			).put(
				"to", ExportTimestamps.format(endDate)
			));
		jsonObject.put(
			"counts",
			_jsonFactory.createJSONObject(
			).put(
				"events", searchEvalExportResult.getEventCount()
			).put(
				"hits", searchEvalExportResult.getHitCount()
			));
		jsonObject.put("plugin_version", _getPluginVersion());
		jsonObject.put("liferay_version", ReleaseInfo.getReleaseInfo());
		jsonObject.put(
			"configuration", _getConfiguration(searchEvalLoggerConfiguration));
		jsonObject.put(
			"admission_counters", _getAdmissionCounters());
		jsonObject.put(
			"field_coverage_rates",
			_getFieldCoverageRates(searchEvalExportResult));

		return jsonObject;
	}

	/**
	 * The whole funnel, not just its last three stages.
	 *
	 * <p>
	 * The drop count on its own answers whether the log lost anything after
	 * admission. It cannot answer the question an evaluator actually has,
	 * which is what the log is a sample <em>of</em>: a dataset holding every
	 * event that was admitted is still a narrow slice if the admission filter
	 * rejected most of the traffic. Reporting the observed, keyword-bearing
	 * and admitted counts alongside makes that ratio, the EC-10 measurement in
	 * DESIGN.md section 7, readable from the archive instead of only from the
	 * admin screen of a portal that may since have restarted.
	 * </p>
	 *
	 * <p>
	 * Every counter here is process-wide and resets when the bundle restarts,
	 * so none of them can be attributed to the exported window. They are
	 * reported with that caveat attached rather than silently presented as
	 * figures for the period, which would misstate exactly the thing an
	 * evaluator would rely on them for.
	 * </p>
	 */
	private JSONObject _getAdmissionCounters() {
		return _jsonFactory.createJSONObject(
		).put(
			"observed_search_count",
			_searchEvalLoggerStatistics.getObservedSearchCount()
		).put(
			"keyword_search_count",
			_searchEvalLoggerStatistics.getKeywordSearchCount()
		).put(
			"admitted_search_count",
			_searchEvalLoggerStatistics.getAdmittedSearchCount()
		).put(
			"dispatched_event_count",
			_searchEvalLoggerStatistics.getDispatchedEventCount()
		).put(
			"dropped_event_count",
			_searchEvalLoggerStatistics.getDroppedEventCount()
		).put(
			"persisted_event_count",
			_searchEvalLoggerStatistics.getPersistedEventCount()
		).put(
			"scope",
			"Counted since this plugin last started, not for the exported " +
				"range, and not restricted to this company. A restart inside " +
					"the range means traffic before it is not represented, " +
						"and these counts can be far smaller than the " +
							"exported row counts."
		);
	}

	private JSONObject _getConfiguration(
		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration) {

		return _jsonFactory.createJSONObject(
		).put(
			"enabled", searchEvalLoggerConfiguration.enabled()
		).put(
			"capture_depth", searchEvalLoggerConfiguration.captureDepth()
		).put(
			"retention_days", searchEvalLoggerConfiguration.retentionDays()
		).put(
			"captured_field_names",
			_toJSONArray(searchEvalLoggerConfiguration.capturedFieldNames())
		).put(
			"exclude_suggestion_traffic",
			searchEvalLoggerConfiguration.excludeSuggestionTraffic()
		).put(
			"admit_facet_only_searches",
			searchEvalLoggerConfiguration.admitFacetOnlySearches()
		).put(
			"require_web_request_context",
			searchEvalLoggerConfiguration.requireWebRequestContext()
		).put(
			"query_text_cap", searchEvalLoggerConfiguration.queryTextCap()
		).put(
			"sampling_rate", searchEvalLoggerConfiguration.samplingRate()
		).put(
			"excluded_entry_class_names",
			_toJSONArray(
				searchEvalLoggerConfiguration.excludedEntryClassNames())
		).put(
			"cohort_salt_rotation_days",
			searchEvalLoggerConfiguration.cohortSaltRotationDays()
		);
	}

	private JSONObject _getFieldCoverageRates(
		SearchEvalExportResult searchEvalExportResult) {

		JSONObject jsonObject = _jsonFactory.createJSONObject();

		Map<String, Double> fieldCoverageRates =
			searchEvalExportResult.getFieldCoverageRates();

		for (Map.Entry<String, Double> entry : fieldCoverageRates.entrySet()) {
			jsonObject.put(entry.getKey(), entry.getValue());
		}

		return jsonObject;
	}

	private String _getPluginVersion() {
		Bundle bundle = FrameworkUtil.getBundle(
			SearchEvalExportManifestBuilder.class);

		if (bundle == null) {
			return null;
		}

		Version version = bundle.getVersion();

		return version.toString();
	}

	private JSONArray _toJSONArray(String[] values) {
		JSONArray jsonArray = _jsonFactory.createJSONArray();

		if (values == null) {
			return jsonArray;
		}

		for (String value : values) {
			if (Validator.isNotNull(value)) {
				jsonArray.put(value);
			}
		}

		return jsonArray;
	}

	/**
	 * Visible for testing, so a manifest is byte-comparable across runs.
	 */
	void setClock(Clock clock) {
		_clock = clock;
	}

	/**
	 * Visible for testing, alongside {@link #setClock}, so the builder can be
	 * exercised without an OSGi container.
	 */
	void setJSONFactory(JSONFactory jsonFactory) {
		_jsonFactory = jsonFactory;
	}

	void setSearchEvalLoggerStatistics(
		SearchEvalLoggerStatistics searchEvalLoggerStatistics) {

		_searchEvalLoggerStatistics = searchEvalLoggerStatistics;
	}

	private Clock _clock = Clock.systemUTC();

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private SearchEvalLoggerStatistics _searchEvalLoggerStatistics;

}
