/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

/**
 * Outcome of facet extraction for a captured search, as persisted in
 * <code>SEL_SearchEvent.facetCaptureStatus</code>.
 *
 * <p>
 * The three values must stay distinct in every code path that writes them. An
 * empty <code>appliedFacets</code> field on its own is ambiguous: it can mean
 * the user applied no facets ({@link #NONE_APPLIED}) or that facets were
 * applied and could not be read ({@link #UNAVAILABLE}), and those are not
 * interchangeable. Conflating them makes one query text look nondeterministic
 * across rows, which is exactly what facet capture exists to prevent. An
 * evaluator can segregate or discard {@link #UNAVAILABLE} rows; they cannot
 * recover from a conflated field. See DESIGN.md 3.2 and EC-12.
 * </p>
 *
 * <p>
 * {@link #name()} is the persisted string form.
 * </p>
 */
public enum FacetCaptureStatus {

	/**
	 * Facet selections were present and were read onto the event.
	 */
	CAPTURED,

	/**
	 * The request carried no facet or filter selections.
	 */
	NONE_APPLIED,

	/**
	 * Facet state could not be read on this path, so the absence of facets on
	 * the event says nothing about what the user applied.
	 */
	UNAVAILABLE;

}
