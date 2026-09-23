/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

/**
 * Read access to the current collection cycle (DESIGN.md 3.6).
 *
 * <p>
 * Published because the admin screen has to render the same state the
 * collector maintains, and because that screen is the fallback delivery channel
 * if Liferay's user notification framework turns out to be unusable (EC-14).
 * Only the read side is published: the cycle is opened, closed and marked by
 * the collector, and nothing outside its bundle may move it.
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
