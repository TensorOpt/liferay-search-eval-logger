/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Values that more than one module has to agree on.
 *
 * <p>
 * The producer, the consumer, the purge job and the admin portlet live in
 * separate bundles, and the values below are the ones a mismatch would break
 * silently rather than loudly: a destination name typo detaches the listener
 * from the wrapper with no error, and a configuration default duplicated in the
 * portlet drifts from the one the runtime actually applies. Keeping them here
 * lets those modules agree without depending on each other.
 * </p>
 *
 * <p>
 * The defaults are specified in DESIGN.md 5 and are typed for runtime use: the
 * dispatcher, the purge job, the export manifest and the admin portlet all need
 * them when no configuration has been saved yet.
 * </p>
 *
 * <p>
 * They cannot also serve as the configuration interface's declared defaults.
 * DESIGN.md 5 requires configuration scoped per virtual instance, which in
 * Liferay means <code>&#64;Meta.OCD</code> and <code>&#64;Meta.AD</code> paired
 * with <code>&#64;ExtendedObjectClassDefinition</code>, and
 * <code>&#64;Meta.AD</code>'s <code>deflt</code> attribute is a
 * <code>String</code> whatever the setting's type. So
 * <code>&#64;Meta.AD(deflt = "100")</code> cannot reference
 * <code>DEFAULT_CAPTURE_DEPTH</code>. Plain OSGi
 * <code>&#64;ObjectClassDefinition</code> does take typed defaults that could
 * reference these constants, but it gives up per-instance scoping, which is not
 * a trade worth making to remove a duplicated literal. The two copies of each
 * default therefore stay in sync only by review: a change here must be mirrored
 * in the configuration interface in the same commit.
 * </p>
 */
public final class SearchEvalLoggerConstants {

	/**
	 * Message Bus destination carrying search events from the
	 * <code>Searcher</code> wrapper to the persisting listener. Registered as a
	 * serial destination so consumption is single threaded, which rate limits
	 * database write pressure on its own. See DESIGN.md 3.3.
	 */
	public static final String DESTINATION_NAME = "tensoropt/search-eval-log";

	public static final String FIELD_SNIPPET = "snippet";

	public static final String FIELD_TITLE = "title";

	/**
	 * Keyword-free, facet-driven searches. Off by default: they carry no query
	 * string to evaluate, and admitting them weakens the admission filter's
	 * strongest discriminator. See DESIGN.md 3.2.
	 */
	public static final boolean DEFAULT_ADMIT_FACET_ONLY_SEARCHES = false;

	/**
	 * Ceiling on captured hits per event, not a target: actual capture is
	 * <code>min(K, hits returned)</code> and is never deepened by re-querying.
	 * See DESIGN.md D4.
	 */
	public static final int DEFAULT_CAPTURE_DEPTH = 100;

	/**
	 * Whitelist of hit fields to capture. A configured field that the caller
	 * did not request is simply absent; there is no fallback that would widen
	 * the outgoing request. See DESIGN.md D2, D8 and 4.4.
	 */
	public static final List<String> DEFAULT_CAPTURED_FIELD_NAMES =
		Collections.unmodifiableList(
			Arrays.asList(FIELD_TITLE, FIELD_SNIPPET));

	/**
	 * Caps how long a <code>cohortHash</code> stays linkable. Lengthen only if
	 * cohort groups prove too small. See DESIGN.md 4.3.
	 */
	public static final int DEFAULT_COHORT_SALT_ROTATION_DAYS = 7;

	/**
	 * Collection is off on install: installing the plugin must not silently
	 * begin collecting data. See DESIGN.md 5.
	 */
	public static final boolean DEFAULT_ENABLED = false;

	/**
	 * Search Bar Suggestions issue a fresh query per keystroke threshold and
	 * would otherwise swamp genuine user-submitted queries. See DESIGN.md 5.1.
	 */
	public static final boolean DEFAULT_EXCLUDE_SUGGESTION_TRAFFIC = true;

	/**
	 * Characters of query text kept before truncation. The event also records
	 * that truncation happened, so an oversized paste yields a bounded row and a
	 * visible marker rather than a SQL error or invisible data loss. See
	 * DESIGN.md 4.5.
	 */
	public static final int DEFAULT_QUERY_TEXT_CAP = 2000;

	/**
	 * Tightens the admission filter to requests carrying a web request context.
	 * Off until measurement shows internal traffic survives the keywords
	 * condition in material volume. See DESIGN.md 3.2 and EC-10.
	 */
	public static final boolean DEFAULT_REQUIRE_WEB_REQUEST_CONTEXT = false;

	/**
	 * Days of history kept. The purge deletes in bounded batches rather than one
	 * large statement. See DESIGN.md 3.4.
	 */
	public static final int DEFAULT_RETENTION_DAYS = 90;

	/**
	 * Fraction of admitted searches to log. An escape hatch for very
	 * high-volume instances; sampling below 1.0 is recorded in the export
	 * manifest so an evaluator knows the log is a sample.
	 */
	public static final double DEFAULT_SAMPLING_RATE = 1.0;

	private SearchEvalLoggerConstants() {
		throw new AssertionError();
	}

}
