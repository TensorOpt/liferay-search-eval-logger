/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.api;

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
	 * Events handed to the Message Bus. Admitted searches that were never
	 * dispatched, because capture itself failed, are counted as dropped rather
	 * than dispatched.
	 */
	public long getDispatchedEventCount();

	/**
	 * Events lost rather than persisted: rejected by the destination's
	 * backpressure handler, or failed on the way to it. Dropping log rows is
	 * always preferable to degrading production search (DESIGN.md 3.3).
	 */
	public long getDroppedEventCount();

	/**
	 * Events written to the database by the listener.
	 */
	public long getPersistedEventCount();

}
