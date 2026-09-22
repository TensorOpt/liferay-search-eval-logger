/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package ai.tensoropt.sel.model;

import com.liferay.portal.kernel.model.ModelWrapper;
import com.liferay.portal.kernel.model.wrapper.BaseModelWrapper;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * This class is a wrapper for {@link SearchEvent}.
 * </p>
 *
 * @author Brian Wing Shun Chan
 * @see SearchEvent
 * @generated
 */
public class SearchEventWrapper
	extends BaseModelWrapper<SearchEvent>
	implements ModelWrapper<SearchEvent>, SearchEvent {

	public SearchEventWrapper(SearchEvent searchEvent) {
		super(searchEvent);
	}

	@Override
	public Map<String, Object> getModelAttributes() {
		Map<String, Object> attributes = new HashMap<String, Object>();

		attributes.put("mvccVersion", getMvccVersion());
		attributes.put("uuid", getUuid());
		attributes.put("searchEventId", getSearchEventId());
		attributes.put("companyId", getCompanyId());
		attributes.put("createDate", getCreateDate());
		attributes.put("queryText", getQueryText());
		attributes.put("queryTruncated", isQueryTruncated());
		attributes.put("locale", getLocale());
		attributes.put("scopeGroupIds", getScopeGroupIds());
		attributes.put("entryClassNames", getEntryClassNames());
		attributes.put("appliedFacets", getAppliedFacets());
		attributes.put("facetCaptureStatus", getFacetCaptureStatus());
		attributes.put("blueprintId", getBlueprintId());
		attributes.put("audienceType", getAudienceType());
		attributes.put("cohortHash", getCohortHash());
		attributes.put("requestedSize", getRequestedSize());
		attributes.put("requestedFrom", getRequestedFrom());
		attributes.put("totalHits", getTotalHits());
		attributes.put("loggedHitCount", getLoggedHitCount());
		attributes.put("sourceType", getSourceType());

		return attributes;
	}

	@Override
	public void setModelAttributes(Map<String, Object> attributes) {
		Long mvccVersion = (Long)attributes.get("mvccVersion");

		if (mvccVersion != null) {
			setMvccVersion(mvccVersion);
		}

		String uuid = (String)attributes.get("uuid");

		if (uuid != null) {
			setUuid(uuid);
		}

		Long searchEventId = (Long)attributes.get("searchEventId");

		if (searchEventId != null) {
			setSearchEventId(searchEventId);
		}

		Long companyId = (Long)attributes.get("companyId");

		if (companyId != null) {
			setCompanyId(companyId);
		}

		Date createDate = (Date)attributes.get("createDate");

		if (createDate != null) {
			setCreateDate(createDate);
		}

		String queryText = (String)attributes.get("queryText");

		if (queryText != null) {
			setQueryText(queryText);
		}

		Boolean queryTruncated = (Boolean)attributes.get("queryTruncated");

		if (queryTruncated != null) {
			setQueryTruncated(queryTruncated);
		}

		String locale = (String)attributes.get("locale");

		if (locale != null) {
			setLocale(locale);
		}

		String scopeGroupIds = (String)attributes.get("scopeGroupIds");

		if (scopeGroupIds != null) {
			setScopeGroupIds(scopeGroupIds);
		}

		String entryClassNames = (String)attributes.get("entryClassNames");

		if (entryClassNames != null) {
			setEntryClassNames(entryClassNames);
		}

		String appliedFacets = (String)attributes.get("appliedFacets");

		if (appliedFacets != null) {
			setAppliedFacets(appliedFacets);
		}

		String facetCaptureStatus = (String)attributes.get(
			"facetCaptureStatus");

		if (facetCaptureStatus != null) {
			setFacetCaptureStatus(facetCaptureStatus);
		}

		String blueprintId = (String)attributes.get("blueprintId");

		if (blueprintId != null) {
			setBlueprintId(blueprintId);
		}

		String audienceType = (String)attributes.get("audienceType");

		if (audienceType != null) {
			setAudienceType(audienceType);
		}

		String cohortHash = (String)attributes.get("cohortHash");

		if (cohortHash != null) {
			setCohortHash(cohortHash);
		}

		Integer requestedSize = (Integer)attributes.get("requestedSize");

		if (requestedSize != null) {
			setRequestedSize(requestedSize);
		}

		Integer requestedFrom = (Integer)attributes.get("requestedFrom");

		if (requestedFrom != null) {
			setRequestedFrom(requestedFrom);
		}

		Long totalHits = (Long)attributes.get("totalHits");

		if (totalHits != null) {
			setTotalHits(totalHits);
		}

		Integer loggedHitCount = (Integer)attributes.get("loggedHitCount");

		if (loggedHitCount != null) {
			setLoggedHitCount(loggedHitCount);
		}

		String sourceType = (String)attributes.get("sourceType");

		if (sourceType != null) {
			setSourceType(sourceType);
		}
	}

	@Override
	public SearchEvent cloneWithOriginalValues() {
		return wrap(model.cloneWithOriginalValues());
	}

	/**
	 * Returns the applied facets of this search event.
	 *
	 * @return the applied facets of this search event
	 */
	@Override
	public String getAppliedFacets() {
		return model.getAppliedFacets();
	}

	/**
	 * Returns the audience type of this search event.
	 *
	 * @return the audience type of this search event
	 */
	@Override
	public String getAudienceType() {
		return model.getAudienceType();
	}

	/**
	 * Returns the blueprint ID of this search event.
	 *
	 * @return the blueprint ID of this search event
	 */
	@Override
	public String getBlueprintId() {
		return model.getBlueprintId();
	}

	/**
	 * Returns the cohort hash of this search event.
	 *
	 * @return the cohort hash of this search event
	 */
	@Override
	public String getCohortHash() {
		return model.getCohortHash();
	}

	/**
	 * Returns the company ID of this search event.
	 *
	 * @return the company ID of this search event
	 */
	@Override
	public long getCompanyId() {
		return model.getCompanyId();
	}

	/**
	 * Returns the create date of this search event.
	 *
	 * @return the create date of this search event
	 */
	@Override
	public Date getCreateDate() {
		return model.getCreateDate();
	}

	/**
	 * Returns the entry class names of this search event.
	 *
	 * @return the entry class names of this search event
	 */
	@Override
	public String getEntryClassNames() {
		return model.getEntryClassNames();
	}

	/**
	 * Returns the facet capture status of this search event.
	 *
	 * @return the facet capture status of this search event
	 */
	@Override
	public String getFacetCaptureStatus() {
		return model.getFacetCaptureStatus();
	}

	/**
	 * Returns the locale of this search event.
	 *
	 * @return the locale of this search event
	 */
	@Override
	public String getLocale() {
		return model.getLocale();
	}

	/**
	 * Returns the logged hit count of this search event.
	 *
	 * @return the logged hit count of this search event
	 */
	@Override
	public int getLoggedHitCount() {
		return model.getLoggedHitCount();
	}

	/**
	 * Returns the mvcc version of this search event.
	 *
	 * @return the mvcc version of this search event
	 */
	@Override
	public long getMvccVersion() {
		return model.getMvccVersion();
	}

	/**
	 * Returns the primary key of this search event.
	 *
	 * @return the primary key of this search event
	 */
	@Override
	public long getPrimaryKey() {
		return model.getPrimaryKey();
	}

	/**
	 * Returns the query text of this search event.
	 *
	 * @return the query text of this search event
	 */
	@Override
	public String getQueryText() {
		return model.getQueryText();
	}

	/**
	 * Returns the query truncated of this search event.
	 *
	 * @return the query truncated of this search event
	 */
	@Override
	public boolean getQueryTruncated() {
		return model.getQueryTruncated();
	}

	/**
	 * Returns the requested from of this search event.
	 *
	 * @return the requested from of this search event
	 */
	@Override
	public int getRequestedFrom() {
		return model.getRequestedFrom();
	}

	/**
	 * Returns the requested size of this search event.
	 *
	 * @return the requested size of this search event
	 */
	@Override
	public int getRequestedSize() {
		return model.getRequestedSize();
	}

	/**
	 * Returns the scope group IDs of this search event.
	 *
	 * @return the scope group IDs of this search event
	 */
	@Override
	public String getScopeGroupIds() {
		return model.getScopeGroupIds();
	}

	/**
	 * Returns the search event ID of this search event.
	 *
	 * @return the search event ID of this search event
	 */
	@Override
	public long getSearchEventId() {
		return model.getSearchEventId();
	}

	/**
	 * Returns the source type of this search event.
	 *
	 * @return the source type of this search event
	 */
	@Override
	public String getSourceType() {
		return model.getSourceType();
	}

	/**
	 * Returns the total hits of this search event.
	 *
	 * @return the total hits of this search event
	 */
	@Override
	public long getTotalHits() {
		return model.getTotalHits();
	}

	/**
	 * Returns the uuid of this search event.
	 *
	 * @return the uuid of this search event
	 */
	@Override
	public String getUuid() {
		return model.getUuid();
	}

	/**
	 * Returns <code>true</code> if this search event is query truncated.
	 *
	 * @return <code>true</code> if this search event is query truncated; <code>false</code> otherwise
	 */
	@Override
	public boolean isQueryTruncated() {
		return model.isQueryTruncated();
	}

	@Override
	public void persist() {
		model.persist();
	}

	/**
	 * Sets the applied facets of this search event.
	 *
	 * @param appliedFacets the applied facets of this search event
	 */
	@Override
	public void setAppliedFacets(String appliedFacets) {
		model.setAppliedFacets(appliedFacets);
	}

	/**
	 * Sets the audience type of this search event.
	 *
	 * @param audienceType the audience type of this search event
	 */
	@Override
	public void setAudienceType(String audienceType) {
		model.setAudienceType(audienceType);
	}

	/**
	 * Sets the blueprint ID of this search event.
	 *
	 * @param blueprintId the blueprint ID of this search event
	 */
	@Override
	public void setBlueprintId(String blueprintId) {
		model.setBlueprintId(blueprintId);
	}

	/**
	 * Sets the cohort hash of this search event.
	 *
	 * @param cohortHash the cohort hash of this search event
	 */
	@Override
	public void setCohortHash(String cohortHash) {
		model.setCohortHash(cohortHash);
	}

	/**
	 * Sets the company ID of this search event.
	 *
	 * @param companyId the company ID of this search event
	 */
	@Override
	public void setCompanyId(long companyId) {
		model.setCompanyId(companyId);
	}

	/**
	 * Sets the create date of this search event.
	 *
	 * @param createDate the create date of this search event
	 */
	@Override
	public void setCreateDate(Date createDate) {
		model.setCreateDate(createDate);
	}

	/**
	 * Sets the entry class names of this search event.
	 *
	 * @param entryClassNames the entry class names of this search event
	 */
	@Override
	public void setEntryClassNames(String entryClassNames) {
		model.setEntryClassNames(entryClassNames);
	}

	/**
	 * Sets the facet capture status of this search event.
	 *
	 * @param facetCaptureStatus the facet capture status of this search event
	 */
	@Override
	public void setFacetCaptureStatus(String facetCaptureStatus) {
		model.setFacetCaptureStatus(facetCaptureStatus);
	}

	/**
	 * Sets the locale of this search event.
	 *
	 * @param locale the locale of this search event
	 */
	@Override
	public void setLocale(String locale) {
		model.setLocale(locale);
	}

	/**
	 * Sets the logged hit count of this search event.
	 *
	 * @param loggedHitCount the logged hit count of this search event
	 */
	@Override
	public void setLoggedHitCount(int loggedHitCount) {
		model.setLoggedHitCount(loggedHitCount);
	}

	/**
	 * Sets the mvcc version of this search event.
	 *
	 * @param mvccVersion the mvcc version of this search event
	 */
	@Override
	public void setMvccVersion(long mvccVersion) {
		model.setMvccVersion(mvccVersion);
	}

	/**
	 * Sets the primary key of this search event.
	 *
	 * @param primaryKey the primary key of this search event
	 */
	@Override
	public void setPrimaryKey(long primaryKey) {
		model.setPrimaryKey(primaryKey);
	}

	/**
	 * Sets the query text of this search event.
	 *
	 * @param queryText the query text of this search event
	 */
	@Override
	public void setQueryText(String queryText) {
		model.setQueryText(queryText);
	}

	/**
	 * Sets whether this search event is query truncated.
	 *
	 * @param queryTruncated the query truncated of this search event
	 */
	@Override
	public void setQueryTruncated(boolean queryTruncated) {
		model.setQueryTruncated(queryTruncated);
	}

	/**
	 * Sets the requested from of this search event.
	 *
	 * @param requestedFrom the requested from of this search event
	 */
	@Override
	public void setRequestedFrom(int requestedFrom) {
		model.setRequestedFrom(requestedFrom);
	}

	/**
	 * Sets the requested size of this search event.
	 *
	 * @param requestedSize the requested size of this search event
	 */
	@Override
	public void setRequestedSize(int requestedSize) {
		model.setRequestedSize(requestedSize);
	}

	/**
	 * Sets the scope group IDs of this search event.
	 *
	 * @param scopeGroupIds the scope group IDs of this search event
	 */
	@Override
	public void setScopeGroupIds(String scopeGroupIds) {
		model.setScopeGroupIds(scopeGroupIds);
	}

	/**
	 * Sets the search event ID of this search event.
	 *
	 * @param searchEventId the search event ID of this search event
	 */
	@Override
	public void setSearchEventId(long searchEventId) {
		model.setSearchEventId(searchEventId);
	}

	/**
	 * Sets the source type of this search event.
	 *
	 * @param sourceType the source type of this search event
	 */
	@Override
	public void setSourceType(String sourceType) {
		model.setSourceType(sourceType);
	}

	/**
	 * Sets the total hits of this search event.
	 *
	 * @param totalHits the total hits of this search event
	 */
	@Override
	public void setTotalHits(long totalHits) {
		model.setTotalHits(totalHits);
	}

	/**
	 * Sets the uuid of this search event.
	 *
	 * @param uuid the uuid of this search event
	 */
	@Override
	public void setUuid(String uuid) {
		model.setUuid(uuid);
	}

	@Override
	public String toXmlString() {
		return model.toXmlString();
	}

	@Override
	protected SearchEventWrapper wrap(SearchEvent searchEvent) {
		return new SearchEventWrapper(searchEvent);
	}

}