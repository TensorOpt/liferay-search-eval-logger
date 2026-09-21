/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.api;

/**
 * Reports whether the {@code Searcher} wrapper is actually in the call path.
 *
 * <p>
 * Registering a higher ranked {@code Searcher} only wins at the moment a
 * consumer binds it. Liferay's search consumers declare a plain
 * <code>&#64;Reference</code>, which is static and reluctant, so a consumer
 * that activated before this plugin was installed stays bound to the portal's
 * own searcher and never rebinds. The wrapper is then registered, healthy, and
 * never called: enabling collection logs nothing at all, with no error
 * anywhere, until the portal restarts and the consumers bind again.
 * </p>
 *
 * <p>
 * That failure is invisible from the outside, so it is detected and surfaced
 * rather than left for an administrator to discover by finding an empty table.
 * This is the answer to EC-3 in DESIGN.md section 7.
 * </p>
 */
public interface SearchInterceptionStatus {

	/**
	 * Returns <code>true</code> when searches actually reach the wrapper.
	 *
	 * <p>
	 * A <code>false</code> result means a portal restart is required before
	 * anything will be collected, however the configuration is set.
	 * </p>
	 */
	public boolean isIntercepting();

}
