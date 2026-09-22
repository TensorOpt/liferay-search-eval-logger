/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import ai.tensoropt.sel.api.FacetCaptureStatus;

import org.junit.jupiter.api.Test;

/**
 * DESIGN.md 3.2: an empty facet field must not be able to mean both "the user
 * applied none" and "facets were applied and could not be read". These two
 * constants are what keeps those apart.
 */
public class FacetCaptureTest {

	@Test
	public void noneAppliedHasNoFacetsAndSaysSo() {
		assertSame(
			FacetCaptureStatus.NONE_APPLIED, FacetCapture.NONE_APPLIED.getStatus());
		assertNull(FacetCapture.NONE_APPLIED.getAppliedFacets());
	}

	@Test
	public void unavailableIsDistinctFromNoneApplied() {
		assertSame(
			FacetCaptureStatus.UNAVAILABLE, FacetCapture.UNAVAILABLE.getStatus());
		assertNull(FacetCapture.UNAVAILABLE.getAppliedFacets());
		assertEquals(
			FacetCapture.NONE_APPLIED.getAppliedFacets(),
			FacetCapture.UNAVAILABLE.getAppliedFacets(),
			"Both carry no payload, so only the status can tell them apart");
	}

	@Test
	public void capturedKeepsTheEncodedSelections() {
		FacetCapture facetCapture = FacetCapture.captured(
			"{\"category\":[\"HR Policies\"]}");

		assertSame(FacetCaptureStatus.CAPTURED, facetCapture.getStatus());
		assertEquals(
			"{\"category\":[\"HR Policies\"]}", facetCapture.getAppliedFacets());
	}

}
