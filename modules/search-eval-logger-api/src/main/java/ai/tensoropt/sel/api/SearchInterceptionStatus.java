/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

/**
 * Reports whether the {@code Searcher} wrapper is actually in the call path.
 *
 * <p>
 * Registering a higher ranked {@code Searcher} only wins at the moment a
 * consumer binds it. Liferay's search consumers declare a plain
 * <code>&#64;Reference</code>, which is static and reluctant, so a consumer
 * that activated before this plugin was installed stays bound to the portal's
 * own searcher and never rebinds on its own. Since TO-140, an impl-internal
 * rebinder moves the search entry points (the Search Results widget, shared
 * search requests, search bar suggestions) onto the wrapper automatically,
 * on install and on every redeploy, without a restart. A handful of other,
 * secondary consumers of {@code Searcher} are not moved that way (DESIGN.md
 * 3.1 explains why) and still need a restart to join.
 * </p>
 *
 * <p>
 * That distinction is invisible from the outside unless surfaced, so this
 * interface answers two separate questions rather than one. This is the
 * answer to EC-3 in DESIGN.md section 7.
 * </p>
 */
public interface SearchInterceptionStatus {

	/**
	 * Returns <code>true</code> when a secondary consumer of
	 * {@code Searcher}, one the automatic rebind does not target, is still
	 * bound to the portal's own searcher and will only move at the next
	 * portal restart.
	 *
	 * <p>
	 * Meaningful only once {@link #isIntercepting()} already answers
	 * <code>true</code>: it names a smaller, secondary gap, not the primary
	 * one, which calls for the restart warning instead.
	 * </p>
	 */
	public boolean hasOtherConsumersAwaitingRestart();

	/**
	 * Returns <code>true</code> when searches made through the entry points
	 * the automatic rebinder targets actually reach the wrapper.
	 *
	 * <p>
	 * A <code>false</code> result means a portal restart is required before
	 * anything will be collected from those entry points, however the
	 * configuration is set: the automatic rebind TO-140 added was attempted
	 * and did not clear the bypass.
	 * </p>
	 */
	public boolean isIntercepting();

}
