/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.configuration;

import aQute.bnd.annotation.metatype.Meta;

import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Collection settings, scoped per virtual instance, as specified in DESIGN.md
 * section 5.
 *
 * <p>
 * Every <code>deflt</code> below duplicates a constant in
 * <code>SearchEvalLoggerConstants</code>, because <code>&#64;Meta.AD</code>
 * takes its default as a <code>String</code> whatever the setting's type. The
 * two must be changed together; that class documents why the duplication is
 * accepted.
 * </p>
 */
@ExtendedObjectClassDefinition(
	category = "search",
	scope = ExtendedObjectClassDefinition.Scope.COMPANY
)
@Meta.OCD(
	id = "ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration",
	localization = "content/Language",
	name = "search-eval-logger-configuration-name"
)
@ProviderType
public interface SearchEvalLoggerConfiguration {

	/**
	 * Off on install. Installing the plugin must not silently begin collecting
	 * data.
	 */
	@Meta.AD(
		deflt = "false", description = "logging-enabled-description",
		name = "logging-enabled", required = false
	)
	public boolean enabled();

	/**
	 * A ceiling, not a target: actual capture is
	 * <code>min(K, hits returned)</code> and the response is never re-queried
	 * to reach it (D4).
	 */
	@Meta.AD(
		deflt = "100", description = "capture-depth-description", min = "1",
		name = "capture-depth", required = false
	)
	public int captureDepth();

	@Meta.AD(
		deflt = "90", description = "retention-days-description", min = "1",
		name = "retention-days", required = false
	)
	public int retentionDays();

	/**
	 * Whitelist of hit fields to capture. Never "whatever the response
	 * contains": this list is the difference between an export an admin
	 * approves after one read and an export that goes to legal.
	 */
	@Meta.AD(
		deflt = "title|snippet", description = "captured-field-names-description",
		name = "captured-field-names", required = false
	)
	public String[] capturedFieldNames();

	/**
	 * Search Bar Suggestions issue a fresh query per keystroke threshold and
	 * would otherwise swamp genuine user-submitted queries.
	 */
	@Meta.AD(
		deflt = "true", description = "exclude-suggestion-traffic-description",
		name = "exclude-suggestion-traffic", required = false
	)
	public boolean excludeSuggestionTraffic();

	/**
	 * Keyword-free, facet-driven interactions. Off by default: they carry no
	 * query string to evaluate, and admitting them weakens the admission
	 * filter's strongest discriminator.
	 */
	@Meta.AD(
		deflt = "false", description = "admit-facet-only-searches-description",
		name = "admit-facet-only-searches", required = false
	)
	public boolean admitFacetOnlySearches();

	@Meta.AD(
		deflt = "7", description = "cohort-salt-rotation-days-description",
		min = "1", name = "cohort-salt-rotation-days", required = false
	)
	public int cohortSaltRotationDays();

	/**
	 * Tightens the admission filter when internal traffic proves noisy
	 * (EC-10).
	 */
	@Meta.AD(
		deflt = "false",
		description = "require-web-request-context-description",
		name = "require-web-request-context", required = false
	)
	public boolean requireWebRequestContext();

	@Meta.AD(
		deflt = "2000", description = "query-text-cap-description", min = "1",
		name = "query-text-cap", required = false
	)
	public int queryTextCap();

	/**
	 * Escape hatch for very high-volume instances. Recorded in the export
	 * manifest so an evaluator knows whether the log is a census or a sample.
	 */
	@Meta.AD(
		deflt = "1.0", description = "sampling-rate-description",
		name = "sampling-rate", required = false
	)
	public double samplingRate();

	@Meta.AD(
		deflt = "", description = "excluded-entry-class-names-description",
		name = "excluded-entry-class-names", required = false
	)
	public String[] excludedEntryClassNames();

	/**
	 * Days since the collection start date, which is the first event actually
	 * persisted rather than the moment logging was switched on (3.6).
	 */
	@Meta.AD(
		deflt = "30", description = "readiness-minimum-days-description",
		min = "0", name = "readiness-minimum-days", required = false
	)
	public int readinessMinimumDays();

	@Meta.AD(
		deflt = "500", description = "readiness-minimum-events-description",
		min = "0", name = "readiness-minimum-events", required = false
	)
	public int readinessMinimumEvents();

	/**
	 * Hides both links of DESIGN.md 10. They are static anchors that are
	 * fetched only when a person clicks them (D9), so this governs what is
	 * displayed and nothing else.
	 */
	@Meta.AD(
		deflt = "true",
		description = "show-evaluation-service-links-description",
		name = "show-evaluation-service-links", required = false
	)
	public boolean showEvaluationServiceLinks();

}
