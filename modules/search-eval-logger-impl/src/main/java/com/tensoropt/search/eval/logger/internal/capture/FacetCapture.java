/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.internal.capture;

import com.tensoropt.search.eval.logger.api.FacetCaptureStatus;

/**
 * The outcome of facet extraction: a status that is always meaningful, and a
 * JSON payload that is only meaningful when the status says so.
 */
public class FacetCapture {

	public static final FacetCapture NONE_APPLIED = new FacetCapture(
		FacetCaptureStatus.NONE_APPLIED, null);

	public static final FacetCapture UNAVAILABLE = new FacetCapture(
		FacetCaptureStatus.UNAVAILABLE, null);

	public static FacetCapture captured(String appliedFacets) {
		return new FacetCapture(FacetCaptureStatus.CAPTURED, appliedFacets);
	}

	public String getAppliedFacets() {
		return _appliedFacets;
	}

	public FacetCaptureStatus getStatus() {
		return _status;
	}

	private FacetCapture(FacetCaptureStatus status, String appliedFacets) {
		_status = status;
		_appliedFacets = appliedFacets;
	}

	private final String _appliedFacets;
	private final FacetCaptureStatus _status;

}
