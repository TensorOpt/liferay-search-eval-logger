/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package ai.tensoropt.sel.model;

import com.liferay.petra.sql.dsl.Column;
import com.liferay.petra.sql.dsl.base.BaseTable;

import java.sql.Clob;
import java.sql.Types;

import java.util.Date;

/**
 * The table class for the &quot;SEL_SearchHit&quot; database table.
 *
 * @author Brian Wing Shun Chan
 * @see SearchHit
 * @generated
 */
public class SearchHitTable extends BaseTable<SearchHitTable> {

	public static final SearchHitTable INSTANCE = new SearchHitTable();

	public final Column<SearchHitTable, Long> mvccVersion = createColumn(
		"mvccVersion", Long.class, Types.BIGINT, Column.FLAG_NULLITY);
	public final Column<SearchHitTable, Long> searchHitId = createColumn(
		"searchHitId", Long.class, Types.BIGINT, Column.FLAG_PRIMARY);
	public final Column<SearchHitTable, String> searchEventUuid = createColumn(
		"searchEventUuid", String.class, Types.VARCHAR, Column.FLAG_DEFAULT);
	public final Column<SearchHitTable, Long> companyId = createColumn(
		"companyId", Long.class, Types.BIGINT, Column.FLAG_DEFAULT);
	public final Column<SearchHitTable, Date> createDate = createColumn(
		"createDate", Date.class, Types.TIMESTAMP, Column.FLAG_DEFAULT);
	public final Column<SearchHitTable, Integer> rank = createColumn(
		"rank_", Integer.class, Types.INTEGER, Column.FLAG_DEFAULT);
	public final Column<SearchHitTable, Double> score = createColumn(
		"score", Double.class, Types.DOUBLE, Column.FLAG_DEFAULT);
	public final Column<SearchHitTable, String> docUid = createColumn(
		"docUid", String.class, Types.VARCHAR, Column.FLAG_DEFAULT);
	public final Column<SearchHitTable, String> entryClassName = createColumn(
		"entryClassName", String.class, Types.VARCHAR, Column.FLAG_DEFAULT);
	public final Column<SearchHitTable, Long> entryClassPK = createColumn(
		"entryClassPK", Long.class, Types.BIGINT, Column.FLAG_DEFAULT);
	public final Column<SearchHitTable, String> title = createColumn(
		"title", String.class, Types.VARCHAR, Column.FLAG_DEFAULT);
	public final Column<SearchHitTable, Clob> snippet = createColumn(
		"snippet", Clob.class, Types.CLOB, Column.FLAG_DEFAULT);
	public final Column<SearchHitTable, Clob> extraFields = createColumn(
		"extraFields", Clob.class, Types.CLOB, Column.FLAG_DEFAULT);

	private SearchHitTable() {
		super("SEL_SearchHit", SearchHitTable::new);
	}

}