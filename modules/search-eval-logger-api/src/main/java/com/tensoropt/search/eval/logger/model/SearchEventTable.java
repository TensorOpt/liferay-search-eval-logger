/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.tensoropt.search.eval.logger.model;

import com.liferay.petra.sql.dsl.Column;
import com.liferay.petra.sql.dsl.base.BaseTable;

import java.sql.Clob;
import java.sql.Types;

import java.util.Date;

/**
 * The table class for the &quot;SEL_SearchEvent&quot; database table.
 *
 * @author Brian Wing Shun Chan
 * @see SearchEvent
 * @generated
 */
public class SearchEventTable extends BaseTable<SearchEventTable> {

	public static final SearchEventTable INSTANCE = new SearchEventTable();

	public final Column<SearchEventTable, Long> mvccVersion = createColumn(
		"mvccVersion", Long.class, Types.BIGINT, Column.FLAG_NULLITY);
	public final Column<SearchEventTable, String> uuid = createColumn(
		"uuid_", String.class, Types.VARCHAR, Column.FLAG_DEFAULT);
	public final Column<SearchEventTable, Long> searchEventId = createColumn(
		"searchEventId", Long.class, Types.BIGINT, Column.FLAG_PRIMARY);
	public final Column<SearchEventTable, Long> companyId = createColumn(
		"companyId", Long.class, Types.BIGINT, Column.FLAG_DEFAULT);
	public final Column<SearchEventTable, Date> createDate = createColumn(
		"createDate", Date.class, Types.TIMESTAMP, Column.FLAG_DEFAULT);
	public final Column<SearchEventTable, String> queryText = createColumn(
		"queryText", String.class, Types.VARCHAR, Column.FLAG_DEFAULT);
	public final Column<SearchEventTable, Boolean> queryTruncated =
		createColumn(
			"queryTruncated", Boolean.class, Types.BOOLEAN,
			Column.FLAG_DEFAULT);
	public final Column<SearchEventTable, String> locale = createColumn(
		"locale", String.class, Types.VARCHAR, Column.FLAG_DEFAULT);
	public final Column<SearchEventTable, String> scopeGroupIds = createColumn(
		"scopeGroupIds", String.class, Types.VARCHAR, Column.FLAG_DEFAULT);
	public final Column<SearchEventTable, String> entryClassNames =
		createColumn(
			"entryClassNames", String.class, Types.VARCHAR,
			Column.FLAG_DEFAULT);
	public final Column<SearchEventTable, Clob> appliedFacets = createColumn(
		"appliedFacets", Clob.class, Types.CLOB, Column.FLAG_DEFAULT);
	public final Column<SearchEventTable, String> facetCaptureStatus =
		createColumn(
			"facetCaptureStatus", String.class, Types.VARCHAR,
			Column.FLAG_DEFAULT);
	public final Column<SearchEventTable, String> blueprintId = createColumn(
		"blueprintId", String.class, Types.VARCHAR, Column.FLAG_DEFAULT);
	public final Column<SearchEventTable, String> audienceType = createColumn(
		"audienceType", String.class, Types.VARCHAR, Column.FLAG_DEFAULT);
	public final Column<SearchEventTable, String> cohortHash = createColumn(
		"cohortHash", String.class, Types.VARCHAR, Column.FLAG_DEFAULT);
	public final Column<SearchEventTable, Integer> requestedSize = createColumn(
		"requestedSize", Integer.class, Types.INTEGER, Column.FLAG_DEFAULT);
	public final Column<SearchEventTable, Integer> requestedFrom = createColumn(
		"requestedFrom", Integer.class, Types.INTEGER, Column.FLAG_DEFAULT);
	public final Column<SearchEventTable, Long> totalHits = createColumn(
		"totalHits", Long.class, Types.BIGINT, Column.FLAG_DEFAULT);
	public final Column<SearchEventTable, Integer> loggedHitCount =
		createColumn(
			"loggedHitCount", Integer.class, Types.INTEGER,
			Column.FLAG_DEFAULT);
	public final Column<SearchEventTable, String> sourceType = createColumn(
		"sourceType", String.class, Types.VARCHAR, Column.FLAG_DEFAULT);

	private SearchEventTable() {
		super("SEL_SearchEvent", SearchEventTable::new);
	}

}