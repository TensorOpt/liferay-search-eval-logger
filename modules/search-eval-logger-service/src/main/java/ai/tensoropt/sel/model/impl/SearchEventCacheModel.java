/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package ai.tensoropt.sel.model.impl;

import ai.tensoropt.sel.model.SearchEvent;

import com.liferay.petra.lang.HashUtil;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.model.CacheModel;
import com.liferay.portal.kernel.model.MVCCModel;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import java.util.Date;

/**
 * The cache model class for representing SearchEvent in entity cache.
 *
 * @author Brian Wing Shun Chan
 * @generated
 */
public class SearchEventCacheModel
	implements CacheModel<SearchEvent>, Externalizable, MVCCModel {

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}

		if (!(object instanceof SearchEventCacheModel)) {
			return false;
		}

		SearchEventCacheModel searchEventCacheModel =
			(SearchEventCacheModel)object;

		if ((searchEventId == searchEventCacheModel.searchEventId) &&
			(mvccVersion == searchEventCacheModel.mvccVersion)) {

			return true;
		}

		return false;
	}

	@Override
	public int hashCode() {
		int hashCode = HashUtil.hash(0, searchEventId);

		return HashUtil.hash(hashCode, mvccVersion);
	}

	@Override
	public long getMvccVersion() {
		return mvccVersion;
	}

	@Override
	public void setMvccVersion(long mvccVersion) {
		this.mvccVersion = mvccVersion;
	}

	@Override
	public String toString() {
		StringBundler sb = new StringBundler(41);

		sb.append("{mvccVersion=");
		sb.append(mvccVersion);
		sb.append(", uuid=");
		sb.append(uuid);
		sb.append(", searchEventId=");
		sb.append(searchEventId);
		sb.append(", companyId=");
		sb.append(companyId);
		sb.append(", createDate=");
		sb.append(createDate);
		sb.append(", queryText=");
		sb.append(queryText);
		sb.append(", queryTruncated=");
		sb.append(queryTruncated);
		sb.append(", locale=");
		sb.append(locale);
		sb.append(", scopeGroupIds=");
		sb.append(scopeGroupIds);
		sb.append(", entryClassNames=");
		sb.append(entryClassNames);
		sb.append(", appliedFacets=");
		sb.append(appliedFacets);
		sb.append(", facetCaptureStatus=");
		sb.append(facetCaptureStatus);
		sb.append(", blueprintId=");
		sb.append(blueprintId);
		sb.append(", audienceType=");
		sb.append(audienceType);
		sb.append(", cohortHash=");
		sb.append(cohortHash);
		sb.append(", requestedSize=");
		sb.append(requestedSize);
		sb.append(", requestedFrom=");
		sb.append(requestedFrom);
		sb.append(", totalHits=");
		sb.append(totalHits);
		sb.append(", loggedHitCount=");
		sb.append(loggedHitCount);
		sb.append(", sourceType=");
		sb.append(sourceType);
		sb.append("}");

		return sb.toString();
	}

	@Override
	public SearchEvent toEntityModel() {
		SearchEventImpl searchEventImpl = new SearchEventImpl();

		searchEventImpl.setMvccVersion(mvccVersion);

		if (uuid == null) {
			searchEventImpl.setUuid("");
		}
		else {
			searchEventImpl.setUuid(uuid);
		}

		searchEventImpl.setSearchEventId(searchEventId);
		searchEventImpl.setCompanyId(companyId);

		if (createDate == Long.MIN_VALUE) {
			searchEventImpl.setCreateDate(null);
		}
		else {
			searchEventImpl.setCreateDate(new Date(createDate));
		}

		if (queryText == null) {
			searchEventImpl.setQueryText("");
		}
		else {
			searchEventImpl.setQueryText(queryText);
		}

		searchEventImpl.setQueryTruncated(queryTruncated);

		if (locale == null) {
			searchEventImpl.setLocale("");
		}
		else {
			searchEventImpl.setLocale(locale);
		}

		if (scopeGroupIds == null) {
			searchEventImpl.setScopeGroupIds("");
		}
		else {
			searchEventImpl.setScopeGroupIds(scopeGroupIds);
		}

		if (entryClassNames == null) {
			searchEventImpl.setEntryClassNames("");
		}
		else {
			searchEventImpl.setEntryClassNames(entryClassNames);
		}

		if (appliedFacets == null) {
			searchEventImpl.setAppliedFacets("");
		}
		else {
			searchEventImpl.setAppliedFacets(appliedFacets);
		}

		if (facetCaptureStatus == null) {
			searchEventImpl.setFacetCaptureStatus("");
		}
		else {
			searchEventImpl.setFacetCaptureStatus(facetCaptureStatus);
		}

		if (blueprintId == null) {
			searchEventImpl.setBlueprintId("");
		}
		else {
			searchEventImpl.setBlueprintId(blueprintId);
		}

		if (audienceType == null) {
			searchEventImpl.setAudienceType("");
		}
		else {
			searchEventImpl.setAudienceType(audienceType);
		}

		if (cohortHash == null) {
			searchEventImpl.setCohortHash("");
		}
		else {
			searchEventImpl.setCohortHash(cohortHash);
		}

		searchEventImpl.setRequestedSize(requestedSize);
		searchEventImpl.setRequestedFrom(requestedFrom);
		searchEventImpl.setTotalHits(totalHits);
		searchEventImpl.setLoggedHitCount(loggedHitCount);

		if (sourceType == null) {
			searchEventImpl.setSourceType("");
		}
		else {
			searchEventImpl.setSourceType(sourceType);
		}

		searchEventImpl.resetOriginalValues();

		return searchEventImpl;
	}

	@Override
	public void readExternal(ObjectInput objectInput)
		throws ClassNotFoundException, IOException {

		mvccVersion = objectInput.readLong();
		uuid = objectInput.readUTF();

		searchEventId = objectInput.readLong();

		companyId = objectInput.readLong();
		createDate = objectInput.readLong();
		queryText = objectInput.readUTF();

		queryTruncated = objectInput.readBoolean();
		locale = objectInput.readUTF();
		scopeGroupIds = objectInput.readUTF();
		entryClassNames = objectInput.readUTF();
		appliedFacets = (String)objectInput.readObject();
		facetCaptureStatus = objectInput.readUTF();
		blueprintId = objectInput.readUTF();
		audienceType = objectInput.readUTF();
		cohortHash = objectInput.readUTF();

		requestedSize = objectInput.readInt();

		requestedFrom = objectInput.readInt();

		totalHits = objectInput.readLong();

		loggedHitCount = objectInput.readInt();
		sourceType = objectInput.readUTF();
	}

	@Override
	public void writeExternal(ObjectOutput objectOutput) throws IOException {
		objectOutput.writeLong(mvccVersion);

		if (uuid == null) {
			objectOutput.writeUTF("");
		}
		else {
			objectOutput.writeUTF(uuid);
		}

		objectOutput.writeLong(searchEventId);

		objectOutput.writeLong(companyId);
		objectOutput.writeLong(createDate);

		if (queryText == null) {
			objectOutput.writeUTF("");
		}
		else {
			objectOutput.writeUTF(queryText);
		}

		objectOutput.writeBoolean(queryTruncated);

		if (locale == null) {
			objectOutput.writeUTF("");
		}
		else {
			objectOutput.writeUTF(locale);
		}

		if (scopeGroupIds == null) {
			objectOutput.writeUTF("");
		}
		else {
			objectOutput.writeUTF(scopeGroupIds);
		}

		if (entryClassNames == null) {
			objectOutput.writeUTF("");
		}
		else {
			objectOutput.writeUTF(entryClassNames);
		}

		if (appliedFacets == null) {
			objectOutput.writeObject("");
		}
		else {
			objectOutput.writeObject(appliedFacets);
		}

		if (facetCaptureStatus == null) {
			objectOutput.writeUTF("");
		}
		else {
			objectOutput.writeUTF(facetCaptureStatus);
		}

		if (blueprintId == null) {
			objectOutput.writeUTF("");
		}
		else {
			objectOutput.writeUTF(blueprintId);
		}

		if (audienceType == null) {
			objectOutput.writeUTF("");
		}
		else {
			objectOutput.writeUTF(audienceType);
		}

		if (cohortHash == null) {
			objectOutput.writeUTF("");
		}
		else {
			objectOutput.writeUTF(cohortHash);
		}

		objectOutput.writeInt(requestedSize);

		objectOutput.writeInt(requestedFrom);

		objectOutput.writeLong(totalHits);

		objectOutput.writeInt(loggedHitCount);

		if (sourceType == null) {
			objectOutput.writeUTF("");
		}
		else {
			objectOutput.writeUTF(sourceType);
		}
	}

	public long mvccVersion;
	public String uuid;
	public long searchEventId;
	public long companyId;
	public long createDate;
	public String queryText;
	public boolean queryTruncated;
	public String locale;
	public String scopeGroupIds;
	public String entryClassNames;
	public String appliedFacets;
	public String facetCaptureStatus;
	public String blueprintId;
	public String audienceType;
	public String cohortHash;
	public int requestedSize;
	public int requestedFrom;
	public long totalHits;
	public int loggedHitCount;
	public String sourceType;

}