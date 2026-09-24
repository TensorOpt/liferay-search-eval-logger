/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

/**
 * Counters the export manifest reports, so an evaluator can tell whether the
 * log is a census or a lossy sample (DESIGN.md 6.2).
 *
 * <p>
 * Counts are process-wide and reset when the bundle restarts, because the
 * backpressure drop happens in the Message Bus thread pool and nothing durable
 * exists to attribute it to. An export covering a period that includes a
 * restart therefore under-reports drops, and says so.
 * </p>
 */
public interface SearchEvalLoggerStatistics {

	/**
	 * Events the Message Bus accepted. An event its queue rejected is counted
	 * as dropped instead, never as both.
	 */
	public long getDispatchedEventCount();

	/**
	 * Admitted events lost rather than persisted, whatever the cause: capture
	 * failed, no listener was attached yet, the destination's backpressure
	 * handler rejected it, or the listener failed to write it. Dropping log
	 * rows is always preferable to degrading production search (DESIGN.md
	 * 3.3).
	 *
	 * <p>
	 * Every admitted event ends up persisted or dropped, so once nothing is in
	 * flight <code>admitted = persisted + dropped</code>.
	 * </p>
	 */
	public long getDroppedEventCount();

	/**
	 * Events written to the database by the listener.
	 */
	public long getPersistedEventCount();

	/**
	 * Searches this wrapper saw while collection was enabled, admitted or not.
	 *
	 * <p>
	 * The denominator for EC-10 in DESIGN.md section 7: Liferay issues many
	 * searches of its own, and the share of them that survives the
	 * keywords-present condition is the measure of whether the allowlist in
	 * 3.2 is doing its job.
	 * </p>
	 */
	public long getObservedSearchCount();

	/**
	 * Searches that carried user keywords, the admission filter's strongest
	 * discriminator (DESIGN.md 3.2, condition 1).
	 */
	public long getKeywordSearchCount();

	/**
	 * Searches that passed every admission condition and were captured.
	 */
	public long getAdmittedSearchCount();

}
