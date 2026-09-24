/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.service.impl;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The export's select, built for the bounds it was actually given.
 *
 * <p>
 * An absent bound is a missing clause, not an extreme value. The predicate is
 * left out of the statement entirely rather than widened to some
 * representable-but-enormous timestamp: every database has its own limit on
 * what a timestamp can hold, PostgreSQL rejects anything outside 4713 BC to
 * 294276 AD and MySQL stops at 9999, so a substituted bound is a portability
 * problem wearing the costume of a value. Omitting the clause also hands the
 * planner the truth, which is that the query is unconstrained in time, instead
 * of a range predicate it has to estimate selectivity for.
 * </p>
 *
 * <p>
 * The statement and its parameters are built together, which is the point of
 * this class existing at all. A predicate is only ever appended by the same
 * call that records its value, so the two cannot drift: <code>companyId</code>
 * is always parameter 1, and each bound that was supplied follows in the order
 * it was appended. There is no place where an index is written down by hand.
 * </p>
 *
 * <p>
 * Kept out of <code>SearchEventLocalServiceImpl</code> so it can be unit
 * tested without a portal: which clauses a pair of bounds produces, and which
 * parameters go with them, is exactly the thing that broke.
 * </p>
 */
class ExportRowQuery {

	ExportRowQuery(long companyId, Date startDate, Date endDate) {
		_companyId = companyId;

		StringBuilder sb = new StringBuilder(_SELECT);

		_appendBound(sb, _START_DATE_PREDICATE, startDate);
		_appendBound(sb, _END_DATE_PREDICATE, endDate);

		sb.append(_ORDER_BY);

		_sql = sb.toString();
	}

	String getSQL() {
		return _sql;
	}

	void setParameters(PreparedStatement preparedStatement)
		throws SQLException {

		preparedStatement.setLong(1, _companyId);

		int index = 2;

		for (Timestamp timestamp : _timestamps) {
			preparedStatement.setTimestamp(index++, timestamp);
		}
	}

	private void _appendBound(StringBuilder sb, String predicate, Date date) {
		if (date == null) {
			return;
		}

		sb.append(predicate);

		_timestamps.add(new Timestamp(date.getTime()));
	}

	private static final String _END_DATE_PREDICATE =
		" and e.createDate < ?";

	private static final String _ORDER_BY =
		" order by e.createDate, e.uuid_, h.rank_";

	private static final String _SELECT =
		"select e.uuid_, e.createDate, e.queryText, e.queryTruncated, " +
			"e.locale, e.scopeGroupIds, e.entryClassNames, e.appliedFacets, " +
				"e.facetCaptureStatus, e.blueprintId, e.audienceType, " +
					"e.cohortHash, e.requestedSize, e.requestedFrom, " +
						"e.totalHits, e.loggedHitCount, e.sourceType, " +
							"h.rank_, h.score, h.docUid, h.entryClassName, " +
								"h.entryClassPK, h.title, h.snippet, " +
									"h.extraFields from SEL_SearchEvent e " +
										"left join SEL_SearchHit h on " +
											"h.searchEventUuid = e.uuid_ " +
												"where e.companyId = ?";

	private static final String _START_DATE_PREDICATE =
		" and e.createDate >= ?";

	private final long _companyId;
	private final String _sql;
	private final List<Timestamp> _timestamps = new ArrayList<>(2);

}
