/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.internal.capture;

import com.tensoropt.search.eval.logger.api.FacetCaptureStatus;
import com.tensoropt.search.eval.logger.api.SourceType;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.List;

/**
 * One admitted search and its captured hits, in flight between the search
 * thread and the listener that persists them.
 *
 * <p>
 * {@link #getUserId()} is the one field here that never reaches a table. It is
 * carried so the listener can resolve the audience type and the cohort hash off
 * the search thread; D3 forbids persisting it, and
 * <code>SearchEventPersistenceMessageListener</code> is the only thing that
 * reads it.
 * </p>
 */
public class CapturedSearchEvent implements Serializable {

	public void addHit(CapturedSearchHit capturedSearchHit) {
		_hits.add(capturedSearchHit);
	}

	public String getAppliedFacets() {
		return _appliedFacets;
	}

	public String getBlueprintId() {
		return _blueprintId;
	}

	public long getCompanyId() {
		return _companyId;
	}

	public long getCreateTime() {
		return _createTime;
	}

	public String getEntryClassNames() {
		return _entryClassNames;
	}

	public FacetCaptureStatus getFacetCaptureStatus() {
		return _facetCaptureStatus;
	}

	public List<CapturedSearchHit> getHits() {
		return _hits;
	}

	public String getLocale() {
		return _locale;
	}

	public String getQueryText() {
		return _queryText;
	}

	public int getRequestedFrom() {
		return _requestedFrom;
	}

	public int getRequestedSize() {
		return _requestedSize;
	}

	public String getScopeGroupIds() {
		return _scopeGroupIds;
	}

	public SourceType getSourceType() {
		return _sourceType;
	}

	public long getTotalHits() {
		return _totalHits;
	}

	public long getUserId() {
		return _userId;
	}

	public boolean isQueryTruncated() {
		return _queryTruncated;
	}

	public void setAppliedFacets(String appliedFacets) {
		_appliedFacets = appliedFacets;
	}

	public void setBlueprintId(String blueprintId) {
		_blueprintId = blueprintId;
	}

	public void setCompanyId(long companyId) {
		_companyId = companyId;
	}

	public void setCreateTime(long createTime) {
		_createTime = createTime;
	}

	public void setEntryClassNames(String entryClassNames) {
		_entryClassNames = entryClassNames;
	}

	public void setFacetCaptureStatus(FacetCaptureStatus facetCaptureStatus) {
		_facetCaptureStatus = facetCaptureStatus;
	}

	public void setLocale(String locale) {
		_locale = locale;
	}

	public void setQueryText(String queryText) {
		_queryText = queryText;
	}

	public void setQueryTruncated(boolean queryTruncated) {
		_queryTruncated = queryTruncated;
	}

	public void setRequestedFrom(int requestedFrom) {
		_requestedFrom = requestedFrom;
	}

	public void setRequestedSize(int requestedSize) {
		_requestedSize = requestedSize;
	}

	public void setScopeGroupIds(String scopeGroupIds) {
		_scopeGroupIds = scopeGroupIds;
	}

	public void setSourceType(SourceType sourceType) {
		_sourceType = sourceType;
	}

	public void setTotalHits(long totalHits) {
		_totalHits = totalHits;
	}

	public void setUserId(long userId) {
		_userId = userId;
	}

	private static final long serialVersionUID = 1L;

	private String _appliedFacets;
	private String _blueprintId;
	private long _companyId;
	private long _createTime;
	private String _entryClassNames;
	private FacetCaptureStatus _facetCaptureStatus =
		FacetCaptureStatus.UNAVAILABLE;
	private final List<CapturedSearchHit> _hits = new ArrayList<>();
	private String _locale;
	private String _queryText;
	private boolean _queryTruncated;
	private int _requestedFrom;
	private int _requestedSize;
	private String _scopeGroupIds;
	private SourceType _sourceType = SourceType.UNKNOWN;
	private long _totalHits;
	private long _userId;

}
