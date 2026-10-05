/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

/**
 * The one mutation of the collection cycle (DESIGN.md 3.6) that a bundle other
 * than the collector's own may trigger: an administrator dismissing the signup
 * banner of 10.1, or clicking its link, on the plugin's own admin screen
 * (TO-112).
 *
 * <p>
 * Kept apart from {@link CollectionCycleStatus}, which is read-only by design
 * (see its class comment), rather than widening that interface's contract for
 * one narrow, user-initiated action.
 * </p>
 *
 * <p>
 * Nothing about either action is transmitted anywhere. The state lives in the
 * same per-company record the rest of 3.6 uses, and is cleared on cycle close
 * like every other value there.
 * </p>
 */
public interface SignupBannerDismissal {

	/**
	 * Idempotent: dismissing an already dismissed banner, or a closed cycle,
	 * does nothing and answers <code>true</code> - there is nothing left to
	 * persist, which is not a failure.
	 *
	 * @return whether the dismissal is durably recorded (or already was).
	 *         <code>false</code> means the underlying write failed; the
	 *         caller decides what to do with that, typically surfacing it as
	 *         a failed request rather than reporting success for a change
	 *         that was not actually saved.
	 */
	public boolean dismiss(long companyId);

}
