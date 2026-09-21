/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.tensoropt.search.eval.logger.model.impl;

import com.liferay.petra.lang.HashUtil;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.model.CacheModel;
import com.liferay.portal.kernel.model.MVCCModel;

import com.tensoropt.search.eval.logger.model.SearchHit;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import java.util.Date;

/**
 * The cache model class for representing SearchHit in entity cache.
 *
 * @author Brian Wing Shun Chan
 * @generated
 */
public class SearchHitCacheModel
	implements CacheModel<SearchHit>, Externalizable, MVCCModel {

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}

		if (!(object instanceof SearchHitCacheModel)) {
			return false;
		}

		SearchHitCacheModel searchHitCacheModel = (SearchHitCacheModel)object;

		if ((searchHitId == searchHitCacheModel.searchHitId) &&
			(mvccVersion == searchHitCacheModel.mvccVersion)) {

			return true;
		}

		return false;
	}

	@Override
	public int hashCode() {
		int hashCode = HashUtil.hash(0, searchHitId);

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
		StringBundler sb = new StringBundler(27);

		sb.append("{mvccVersion=");
		sb.append(mvccVersion);
		sb.append(", searchHitId=");
		sb.append(searchHitId);
		sb.append(", searchEventUuid=");
		sb.append(searchEventUuid);
		sb.append(", companyId=");
		sb.append(companyId);
		sb.append(", createDate=");
		sb.append(createDate);
		sb.append(", rank=");
		sb.append(rank);
		sb.append(", score=");
		sb.append(score);
		sb.append(", docUid=");
		sb.append(docUid);
		sb.append(", entryClassName=");
		sb.append(entryClassName);
		sb.append(", entryClassPK=");
		sb.append(entryClassPK);
		sb.append(", title=");
		sb.append(title);
		sb.append(", snippet=");
		sb.append(snippet);
		sb.append(", extraFields=");
		sb.append(extraFields);
		sb.append("}");

		return sb.toString();
	}

	@Override
	public SearchHit toEntityModel() {
		SearchHitImpl searchHitImpl = new SearchHitImpl();

		searchHitImpl.setMvccVersion(mvccVersion);
		searchHitImpl.setSearchHitId(searchHitId);

		if (searchEventUuid == null) {
			searchHitImpl.setSearchEventUuid("");
		}
		else {
			searchHitImpl.setSearchEventUuid(searchEventUuid);
		}

		searchHitImpl.setCompanyId(companyId);

		if (createDate == Long.MIN_VALUE) {
			searchHitImpl.setCreateDate(null);
		}
		else {
			searchHitImpl.setCreateDate(new Date(createDate));
		}

		searchHitImpl.setRank(rank);
		searchHitImpl.setScore(score);

		if (docUid == null) {
			searchHitImpl.setDocUid("");
		}
		else {
			searchHitImpl.setDocUid(docUid);
		}

		if (entryClassName == null) {
			searchHitImpl.setEntryClassName("");
		}
		else {
			searchHitImpl.setEntryClassName(entryClassName);
		}

		searchHitImpl.setEntryClassPK(entryClassPK);

		if (title == null) {
			searchHitImpl.setTitle("");
		}
		else {
			searchHitImpl.setTitle(title);
		}

		if (snippet == null) {
			searchHitImpl.setSnippet("");
		}
		else {
			searchHitImpl.setSnippet(snippet);
		}

		if (extraFields == null) {
			searchHitImpl.setExtraFields("");
		}
		else {
			searchHitImpl.setExtraFields(extraFields);
		}

		searchHitImpl.resetOriginalValues();

		return searchHitImpl;
	}

	@Override
	public void readExternal(ObjectInput objectInput)
		throws ClassNotFoundException, IOException {

		mvccVersion = objectInput.readLong();

		searchHitId = objectInput.readLong();
		searchEventUuid = objectInput.readUTF();

		companyId = objectInput.readLong();
		createDate = objectInput.readLong();

		rank = objectInput.readInt();

		score = objectInput.readDouble();
		docUid = objectInput.readUTF();
		entryClassName = objectInput.readUTF();

		entryClassPK = objectInput.readLong();
		title = objectInput.readUTF();
		snippet = (String)objectInput.readObject();
		extraFields = (String)objectInput.readObject();
	}

	@Override
	public void writeExternal(ObjectOutput objectOutput) throws IOException {
		objectOutput.writeLong(mvccVersion);

		objectOutput.writeLong(searchHitId);

		if (searchEventUuid == null) {
			objectOutput.writeUTF("");
		}
		else {
			objectOutput.writeUTF(searchEventUuid);
		}

		objectOutput.writeLong(companyId);
		objectOutput.writeLong(createDate);

		objectOutput.writeInt(rank);

		objectOutput.writeDouble(score);

		if (docUid == null) {
			objectOutput.writeUTF("");
		}
		else {
			objectOutput.writeUTF(docUid);
		}

		if (entryClassName == null) {
			objectOutput.writeUTF("");
		}
		else {
			objectOutput.writeUTF(entryClassName);
		}

		objectOutput.writeLong(entryClassPK);

		if (title == null) {
			objectOutput.writeUTF("");
		}
		else {
			objectOutput.writeUTF(title);
		}

		if (snippet == null) {
			objectOutput.writeObject("");
		}
		else {
			objectOutput.writeObject(snippet);
		}

		if (extraFields == null) {
			objectOutput.writeObject("");
		}
		else {
			objectOutput.writeObject(extraFields);
		}
	}

	public long mvccVersion;
	public long searchHitId;
	public String searchEventUuid;
	public long companyId;
	public long createDate;
	public int rank;
	public double score;
	public String docUid;
	public String entryClassName;
	public long entryClassPK;
	public String title;
	public String snippet;
	public String extraFields;

}