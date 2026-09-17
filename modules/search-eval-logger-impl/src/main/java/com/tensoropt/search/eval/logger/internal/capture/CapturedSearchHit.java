/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.internal.capture;

import java.io.Serializable;

/**
 * One captured hit, in flight between the search thread and the listener.
 */
public class CapturedSearchHit implements Serializable {

	public String getDocUid() {
		return _docUid;
	}

	public String getEntryClassName() {
		return _entryClassName;
	}

	public long getEntryClassPK() {
		return _entryClassPK;
	}

	public String getExtraFields() {
		return _extraFields;
	}

	public int getRank() {
		return _rank;
	}

	public double getScore() {
		return _score;
	}

	public String getSnippet() {
		return _snippet;
	}

	public String getTitle() {
		return _title;
	}

	public void setDocUid(String docUid) {
		_docUid = docUid;
	}

	public void setEntryClassName(String entryClassName) {
		_entryClassName = entryClassName;
	}

	public void setEntryClassPK(long entryClassPK) {
		_entryClassPK = entryClassPK;
	}

	public void setExtraFields(String extraFields) {
		_extraFields = extraFields;
	}

	public void setRank(int rank) {
		_rank = rank;
	}

	public void setScore(double score) {
		_score = score;
	}

	public void setSnippet(String snippet) {
		_snippet = snippet;
	}

	public void setTitle(String title) {
		_title = title;
	}

	private static final long serialVersionUID = 1L;

	private String _docUid;
	private String _entryClassName;
	private long _entryClassPK;
	private String _extraFields;
	private int _rank;
	private double _score;
	private String _snippet;
	private String _title;

}
