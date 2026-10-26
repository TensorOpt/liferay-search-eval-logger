/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

/**
 * Read access to the current collection cycle (DESIGN.md 3.6).
 *
 * <p>
 * Published because the admin screen has to render the same state the
 * collector maintains. EC-14: delivery is confirmed; the admin screen remains
 * the channel that does not depend on it. Only the read side is published:
 * the cycle is opened, closed and marked by the collector, and nothing
 * outside its bundle may move it.
 * </p>
 */
public interface CollectionCycleStatus {

	/**
	 * Never null. A company with no cycle open, and a lookup that failed,
	 * both answer a closed cycle, because neither is a reason to fail the
	 * screen or the job asking.
	 */
	public CollectionCycle getCollectionCycle(long companyId);

}
