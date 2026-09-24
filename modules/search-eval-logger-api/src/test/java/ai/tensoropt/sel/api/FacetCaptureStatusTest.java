/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The three values must stay distinct. DESIGN.md 3.2 is explicit that merging
 * "no facets were applied" with "facets could not be read" reintroduces the
 * apparent-nondeterminism this field exists to prevent, and that an evaluator
 * cannot recover from a conflated column.
 */
public class FacetCaptureStatusTest {

	@Test
	public void noneAppliedIsDistinctFromUnavailable() {
		assertEquals(3, FacetCaptureStatus.values().length);
		assertEquals("CAPTURED", FacetCaptureStatus.CAPTURED.name());
		assertEquals("NONE_APPLIED", FacetCaptureStatus.NONE_APPLIED.name());
		assertEquals("UNAVAILABLE", FacetCaptureStatus.UNAVAILABLE.name());
	}

}
