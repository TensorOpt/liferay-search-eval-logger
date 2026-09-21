/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.web.internal.export;

import com.tensoropt.search.eval.logger.configuration.SearchEvalLoggerConfiguration;

import java.util.Date;
import java.util.Map;

import org.osgi.service.component.annotations.Component;

/**
 * Builds the archive's <code>README.md</code> (DESIGN.md 6.2).
 *
 * <p>
 * It states the structural caveats in plain language, so someone who opens the
 * archive without having read the design doc cannot mistake what the dataset
 * is. Every caveat here is a property of how the data was collected, not a
 * disclaimer: each one changes how a metric computed over these rows should be
 * read.
 * </p>
 */
@Component(service = SearchEvalExportReadmeBuilder.class)
public class SearchEvalExportReadmeBuilder {

	public String build(
		Date startDate, Date endDate,
		SearchEvalExportResult searchEvalExportResult,
		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration) {

		StringBuilder sb = new StringBuilder();

		sb.append("# Search Evaluation Export\n\n");
		sb.append("Range: ");
		sb.append(ExportTimestamps.format(startDate));
		sb.append(" to ");
		sb.append(ExportTimestamps.format(endDate));
		sb.append(" (UTC, end exclusive)  \nEvents: ");
		sb.append(searchEvalExportResult.getEventCount());
		sb.append("  \nHits: ");
		sb.append(searchEvalExportResult.getHitCount());
		sb.append("\n\n");
		sb.append(_CONTENTS);
		sb.append(_WHAT_THESE_ROWS_ARE);
		sb.append("## Caveats that change how to read the data\n\n");
		sb.append(_CAPTURE_DEPTH_PREFIX);
		sb.append(searchEvalLoggerConfiguration.captureDepth());
		sb.append(_CAPTURE_DEPTH_SUFFIX);
		sb.append(_PERMISSION_FILTERED);
		sb.append(_COHORT_HASH_PREFIX);
		sb.append(searchEvalLoggerConfiguration.cohortSaltRotationDays());
		sb.append(_COHORT_HASH_SUFFIX);
		sb.append(_FIELD_COVERAGE);
		sb.append(_getCoverageSection(searchEvalExportResult));
		sb.append(_INTERNAL_TRAFFIC);
		sb.append(_FACET_CAPTURE);
		sb.append(_BLUEPRINTS);
		sb.append(_DROP_COUNTS);
		sb.append(_HANDLING);

		return sb.toString();
	}

	private String _getCoverageSection(
		SearchEvalExportResult searchEvalExportResult) {

		Map<String, Double> fieldCoverageRates =
			searchEvalExportResult.getFieldCoverageRates();

		if (fieldCoverageRates.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();

		sb.append("Coverage in this export:\n\n");

		for (Map.Entry<String, Double> entry : fieldCoverageRates.entrySet()) {
			sb.append("- `");
			sb.append(entry.getKey());
			sb.append("`: ");
			sb.append(Math.round(entry.getValue() * 100));
			sb.append("% of hits\n");
		}

		sb.append("\n");

		return sb.toString();
	}

	private static final String _BLUEPRINTS =
		"**Blueprints can impose filters that are invisible here.** On " +
			"Liferay Enterprise Search, a Blueprint can constrain a search " +
				"page to a fixed tag or category. Those are not user-applied " +
					"facets, so they do not appear in `applied_facets`, and " +
						"the Blueprint configuration is not exported. The " +
							"same query on a constrained page and on a " +
								"generic one produces two rows that look " +
									"identical but have unrelated result " +
										"sets. Where `blueprint_id` is " +
											"present, use it to separate " +
												"those populations.\n\n";

	private static final String _CAPTURE_DEPTH_PREFIX =
		"**Capture depth is bounded by what the caller asked for.** Only the " +
			"results the search actually returned were recorded, up to a " +
				"ceiling of ";

	private static final String _CAPTURE_DEPTH_SUFFIX =
		". The query was never re-run to record more. A widget that " +
			"paginates at ten yields ten rows, however many matches existed. " +
				"Use `requested_size`, `requested_from`, `total_hits` and " +
					"`logged_hit_count` to tell \"only ten results existed\" " +
						"from \"only ten were captured\"; treating the " +
							"second as the first turns a valid metric into a " +
								"misleading one.\n\n";

	private static final String _COHORT_HASH_PREFIX =
		"**`cohort_hash` is not stable over time.** It is a salted hash of " +
			"the user ID whose salt rotates every ";

	private static final String _COHORT_HASH_SUFFIX =
		" days and is never exported. Rows sharing a value within one " +
			"rotation window are the same person; across windows they are " +
				"unrelated. A single cohort cannot be followed across the " +
					"whole range.\n\n";

	private static final String _CONTENTS =
		"## Contents\n\n- `events.jsonl` - one JSON object per line: a " +
			"search event with its hits nested, so each line is a complete " +
				"(query, result[]) record.\n- `manifest.json` - range, " +
					"counts, plugin and Liferay versions, the collector's " +
						"configuration at export time, backpressure counts " +
							"and per-field coverage rates.\n- `README.md` - " +
								"this file.\n\n";

	private static final String _DROP_COUNTS =
		"**Drop counts are process-wide.** Under load the collector drops " +
			"events rather than slowing search down. The counts in " +
				"`manifest.json` are since the plugin last started, not for " +
					"this range, so they bound the loss rather than " +
						"measuring it.\n\n";

	private static final String _FACET_CAPTURE =
		"**Facet capture is not uniform.** `facet_capture_status` says which " +
			"case each row is: `CAPTURED` means the selections are in " +
				"`applied_facets`, `NONE_APPLIED` means the user applied " +
					"none, and `UNAVAILABLE` means selections could not be " +
						"read on that path and may or may not have existed. " +
							"Do not read `UNAVAILABLE` as \"no facets\": the " +
								"same query text under different facets " +
									"returns a different list, so conflating " +
										"the two makes one query look " +
											"nondeterministic. Segregate or " +
												"discard those rows.\n\n";

	private static final String _FIELD_COVERAGE =
		"**Field coverage is a property of this installation, not of the " +
			"collector.** Titles and snippets were recorded only where the " +
				"search response already contained them, and snippets " +
					"generally appear only where the search UI had " +
						"highlighting switched on. Check " +
							"`field_coverage_rates` in `manifest.json` " +
								"before designing a judging pass. Thin " +
									"coverage is fixed by changing that " +
										"installation's search " +
											"configuration, not the " +
												"export.\n\n";

	private static final String _HANDLING =
		"## Handling\n\nThese rows are user-submitted text from a production " +
			"system. Whether that constitutes personal data is the " +
				"operator's determination; if it does, the receiving party " +
					"is a processor, and the appropriate instrument is a " +
						"data processing agreement rather than an NDA.\n";

	private static final String _INTERNAL_TRAFFIC =
		"**Internal traffic was filtered out.** Liferay uses its search " +
			"engine for administrative work as well as user search, so only " +
				"searches carrying user keywords were recorded. The " +
					"admission rules in force are recorded in " +
						"`manifest.json`.\n\n";

	private static final String _PERMISSION_FILTERED =
		"**Results are permission filtered.** Liferay filters results by the " +
			"requesting user's permissions, so two users running the same " +
				"query receive different lists, and an aggregate computed " +
					"across users measures access control as much as ranking " +
						"quality. `audience_type` and `cohort_hash` exist to " +
							"partition around this: restrict to guests for a " +
								"fully comparable population, or group by " +
									"`cohort_hash` and compute within " +
										"group.\n\n";

	private static final String _WHAT_THESE_ROWS_ARE =
		"## What these rows are\n\nEach line of `events.jsonl` is one user " +
			"search and the result list it returned, as the search engine " +
				"returned it. This is an interaction log, not a set of " +
					"relevance judgments: nothing here says whether a result " +
						"was good. Grading happens downstream.\n\n";

}
