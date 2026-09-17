/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.tensoropt.search.eval.logger.model;

import com.liferay.portal.kernel.model.ModelWrapper;
import com.liferay.portal.kernel.model.wrapper.BaseModelWrapper;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * This class is a wrapper for {@link SearchHit}.
 * </p>
 *
 * @author Brian Wing Shun Chan
 * @see SearchHit
 * @generated
 */
public class SearchHitWrapper
	extends BaseModelWrapper<SearchHit>
	implements ModelWrapper<SearchHit>, SearchHit {

	public SearchHitWrapper(SearchHit searchHit) {
		super(searchHit);
	}

	@Override
	public Map<String, Object> getModelAttributes() {
		Map<String, Object> attributes = new HashMap<String, Object>();

		attributes.put("mvccVersion", getMvccVersion());
		attributes.put("searchHitId", getSearchHitId());
		attributes.put("searchEventUuid", getSearchEventUuid());
		attributes.put("rank", getRank());
		attributes.put("score", getScore());
		attributes.put("docUid", getDocUid());
		attributes.put("entryClassName", getEntryClassName());
		attributes.put("entryClassPK", getEntryClassPK());
		attributes.put("title", getTitle());
		attributes.put("snippet", getSnippet());
		attributes.put("extraFields", getExtraFields());

		return attributes;
	}

	@Override
	public void setModelAttributes(Map<String, Object> attributes) {
		Long mvccVersion = (Long)attributes.get("mvccVersion");

		if (mvccVersion != null) {
			setMvccVersion(mvccVersion);
		}

		Long searchHitId = (Long)attributes.get("searchHitId");

		if (searchHitId != null) {
			setSearchHitId(searchHitId);
		}

		String searchEventUuid = (String)attributes.get("searchEventUuid");

		if (searchEventUuid != null) {
			setSearchEventUuid(searchEventUuid);
		}

		Integer rank = (Integer)attributes.get("rank");

		if (rank != null) {
			setRank(rank);
		}

		Double score = (Double)attributes.get("score");

		if (score != null) {
			setScore(score);
		}

		String docUid = (String)attributes.get("docUid");

		if (docUid != null) {
			setDocUid(docUid);
		}

		String entryClassName = (String)attributes.get("entryClassName");

		if (entryClassName != null) {
			setEntryClassName(entryClassName);
		}

		Long entryClassPK = (Long)attributes.get("entryClassPK");

		if (entryClassPK != null) {
			setEntryClassPK(entryClassPK);
		}

		String title = (String)attributes.get("title");

		if (title != null) {
			setTitle(title);
		}

		String snippet = (String)attributes.get("snippet");

		if (snippet != null) {
			setSnippet(snippet);
		}

		String extraFields = (String)attributes.get("extraFields");

		if (extraFields != null) {
			setExtraFields(extraFields);
		}
	}

	@Override
	public SearchHit cloneWithOriginalValues() {
		return wrap(model.cloneWithOriginalValues());
	}

	/**
	 * Returns the doc uid of this search hit.
	 *
	 * @return the doc uid of this search hit
	 */
	@Override
	public String getDocUid() {
		return model.getDocUid();
	}

	/**
	 * Returns the entry class name of this search hit.
	 *
	 * @return the entry class name of this search hit
	 */
	@Override
	public String getEntryClassName() {
		return model.getEntryClassName();
	}

	/**
	 * Returns the entry class pk of this search hit.
	 *
	 * @return the entry class pk of this search hit
	 */
	@Override
	public long getEntryClassPK() {
		return model.getEntryClassPK();
	}

	/**
	 * Returns the extra fields of this search hit.
	 *
	 * @return the extra fields of this search hit
	 */
	@Override
	public String getExtraFields() {
		return model.getExtraFields();
	}

	/**
	 * Returns the mvcc version of this search hit.
	 *
	 * @return the mvcc version of this search hit
	 */
	@Override
	public long getMvccVersion() {
		return model.getMvccVersion();
	}

	/**
	 * Returns the primary key of this search hit.
	 *
	 * @return the primary key of this search hit
	 */
	@Override
	public long getPrimaryKey() {
		return model.getPrimaryKey();
	}

	/**
	 * Returns the rank of this search hit.
	 *
	 * @return the rank of this search hit
	 */
	@Override
	public int getRank() {
		return model.getRank();
	}

	/**
	 * Returns the score of this search hit.
	 *
	 * @return the score of this search hit
	 */
	@Override
	public double getScore() {
		return model.getScore();
	}

	/**
	 * Returns the search event uuid of this search hit.
	 *
	 * @return the search event uuid of this search hit
	 */
	@Override
	public String getSearchEventUuid() {
		return model.getSearchEventUuid();
	}

	/**
	 * Returns the search hit ID of this search hit.
	 *
	 * @return the search hit ID of this search hit
	 */
	@Override
	public long getSearchHitId() {
		return model.getSearchHitId();
	}

	/**
	 * Returns the snippet of this search hit.
	 *
	 * @return the snippet of this search hit
	 */
	@Override
	public String getSnippet() {
		return model.getSnippet();
	}

	/**
	 * Returns the title of this search hit.
	 *
	 * @return the title of this search hit
	 */
	@Override
	public String getTitle() {
		return model.getTitle();
	}

	@Override
	public void persist() {
		model.persist();
	}

	/**
	 * Sets the doc uid of this search hit.
	 *
	 * @param docUid the doc uid of this search hit
	 */
	@Override
	public void setDocUid(String docUid) {
		model.setDocUid(docUid);
	}

	/**
	 * Sets the entry class name of this search hit.
	 *
	 * @param entryClassName the entry class name of this search hit
	 */
	@Override
	public void setEntryClassName(String entryClassName) {
		model.setEntryClassName(entryClassName);
	}

	/**
	 * Sets the entry class pk of this search hit.
	 *
	 * @param entryClassPK the entry class pk of this search hit
	 */
	@Override
	public void setEntryClassPK(long entryClassPK) {
		model.setEntryClassPK(entryClassPK);
	}

	/**
	 * Sets the extra fields of this search hit.
	 *
	 * @param extraFields the extra fields of this search hit
	 */
	@Override
	public void setExtraFields(String extraFields) {
		model.setExtraFields(extraFields);
	}

	/**
	 * Sets the mvcc version of this search hit.
	 *
	 * @param mvccVersion the mvcc version of this search hit
	 */
	@Override
	public void setMvccVersion(long mvccVersion) {
		model.setMvccVersion(mvccVersion);
	}

	/**
	 * Sets the primary key of this search hit.
	 *
	 * @param primaryKey the primary key of this search hit
	 */
	@Override
	public void setPrimaryKey(long primaryKey) {
		model.setPrimaryKey(primaryKey);
	}

	/**
	 * Sets the rank of this search hit.
	 *
	 * @param rank the rank of this search hit
	 */
	@Override
	public void setRank(int rank) {
		model.setRank(rank);
	}

	/**
	 * Sets the score of this search hit.
	 *
	 * @param score the score of this search hit
	 */
	@Override
	public void setScore(double score) {
		model.setScore(score);
	}

	/**
	 * Sets the search event uuid of this search hit.
	 *
	 * @param searchEventUuid the search event uuid of this search hit
	 */
	@Override
	public void setSearchEventUuid(String searchEventUuid) {
		model.setSearchEventUuid(searchEventUuid);
	}

	/**
	 * Sets the search hit ID of this search hit.
	 *
	 * @param searchHitId the search hit ID of this search hit
	 */
	@Override
	public void setSearchHitId(long searchHitId) {
		model.setSearchHitId(searchHitId);
	}

	/**
	 * Sets the snippet of this search hit.
	 *
	 * @param snippet the snippet of this search hit
	 */
	@Override
	public void setSnippet(String snippet) {
		model.setSnippet(snippet);
	}

	/**
	 * Sets the title of this search hit.
	 *
	 * @param title the title of this search hit
	 */
	@Override
	public void setTitle(String title) {
		model.setTitle(title);
	}

	@Override
	public String toXmlString() {
		return model.toXmlString();
	}

	@Override
	protected SearchHitWrapper wrap(SearchHit searchHit) {
		return new SearchHitWrapper(searchHit);
	}

}