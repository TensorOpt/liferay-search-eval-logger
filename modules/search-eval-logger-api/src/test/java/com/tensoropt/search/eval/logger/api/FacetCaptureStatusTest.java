/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * The three values must stay distinct. DESIGN.md 3.2 is explicit that merging
 * "no facets were applied" with "facets could not be read" reintroduces the
 * apparent-nondeterminism this field exists to prevent, and that an evaluator
 * cannot recover from a conflated column.
 */
public class FacetCaptureStatusTest {

	@Test
	public void parseReturnsTheMatchingConstant() {
		for (FacetCaptureStatus status : FacetCaptureStatus.values()) {
			assertSame(
				status,
				FacetCaptureStatus.parse(
					status.name(), FacetCaptureStatus.UNAVAILABLE));
		}
	}

	@Test
	public void parseFallsBackRatherThanThrowing() {
		assertSame(
			FacetCaptureStatus.UNAVAILABLE,
			FacetCaptureStatus.parse(null, FacetCaptureStatus.UNAVAILABLE));
		assertSame(
			FacetCaptureStatus.UNAVAILABLE,
			FacetCaptureStatus.parse("nope", FacetCaptureStatus.UNAVAILABLE));
	}

	@Test
	public void noneAppliedIsDistinctFromUnavailable() {
		assertEquals(3, FacetCaptureStatus.values().length);
		assertSame(
			FacetCaptureStatus.NONE_APPLIED,
			FacetCaptureStatus.parse(
				"NONE_APPLIED", FacetCaptureStatus.UNAVAILABLE));
		assertSame(
			FacetCaptureStatus.CAPTURED,
			FacetCaptureStatus.parse(
				"CAPTURED", FacetCaptureStatus.UNAVAILABLE));
	}

}
