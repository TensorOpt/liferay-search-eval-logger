/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package ai.tensoropt.sel.service.impl;

import com.liferay.portal.aop.AopService;
import com.liferay.petra.function.UnsafeConsumer;
import com.liferay.portal.kernel.dao.jdbc.CurrentConnectionUtil;
import com.liferay.portal.kernel.exception.SystemException;

import ai.tensoropt.sel.service.base.SearchEventLocalServiceBaseImpl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import java.util.Date;

import javax.sql.DataSource;

import org.osgi.service.component.annotations.Component;

/**
 * @author Brian Wing Shun Chan
 */
@Component(
	property = "model.class.name=ai.tensoropt.sel.model.SearchEvent",
	service = AopService.class
)
public class SearchEventLocalServiceImpl extends SearchEventLocalServiceBaseImpl {

	/**
	 * Deletes every event older than the cutoff for one virtual instance,
	 * in a single statement, and returns how many rows went.
	 *
	 * <p>
	 * Service Builder's generated <code>removeByC_LtCreateDate</code> is not
	 * usable for this: it loads every matching row with
	 * <code>QueryUtil.ALL_POS</code> and deletes them one at a time, so a
	 * backlog would be pulled into the heap before anything was freed. This
	 * goes to JDBC instead, which is safe here specifically because both
	 * entities are <code>cache-enabled="false"</code> and nothing listens for
	 * their removal, so no cache is left holding rows that no longer exist.
	 * </p>
	 *
	 * <p>
	 * The connection is the one bound to the current transaction where there
	 * is one, matching what the generated <code>runSQL</code> does, so the
	 * delete commits or rolls back with its caller.
	 * </p>
	 */
	public int deleteByCompanyIdAndCreateDateBefore(
		long companyId, Date cutoffDate) {

		DataSource dataSource = searchEventPersistence.getDataSource();

		Connection currentConnection = CurrentConnectionUtil.getConnection(
			dataSource);

		try {
			if (currentConnection != null) {
				return _delete(currentConnection, companyId, cutoffDate);
			}

			try (Connection connection = dataSource.getConnection()) {
				return _delete(connection, companyId, cutoffDate);
			}
		}
		catch (Exception exception) {
			throw new SystemException(exception);
		}
	}

	private int _delete(
			Connection connection, long companyId, Date cutoffDate)
		throws Exception {

		try (PreparedStatement preparedStatement = connection.prepareStatement(
				_DELETE_SQL)) {

			preparedStatement.setLong(1, companyId);
			preparedStatement.setTimestamp(
				2, new Timestamp(cutoffDate.getTime()));

			return preparedStatement.executeUpdate();
		}
	}

	/**
	 * Streams every event in the range with its hits already attached, one
	 * row per hit, grouped by event and ordered by rank.
	 *
	 * <p>
	 * This replaces a per-event hit lookup. Measured on 600,014 events and
	 * 6,000,056 hits, the 600,014 individual lookups cost 57.5 s of database
	 * time against 8.8 s for this single join, and the join hands back rows
	 * already in the order the writer emits them, so the caller holds only
	 * the hits of the event it is currently writing.
	 * </p>
	 *
	 * <p>
	 * Raw JDBC rather than the generated finders, on purpose: the export's
	 * cost is not the queries, it is materialising millions of entities that
	 * exist only to be turned into JSON and discarded. The caller reads
	 * columns and never sees an entity. Keeping this in the service module
	 * means the web module still depends on a service rather than a
	 * DataSource.
	 * </p>
	 *
	 * <p>
	 * Ordered by createDate first, then uuid. Grouping only needs the uuid,
	 * but ordering by it alone scattered the export in time: the log stopped
	 * being chronological, which is how anyone reading it expects to consume
	 * it, and adjacent records stopped sharing temporal context, which cost
	 * seven percent of the compressed size for identical content.
	 * </p>
	 *
	 * <p>
	 * An event with no hits still yields one row, with the hit columns null,
	 * so an empty result set is not silently dropped from the export.
	 * </p>
	 */
	public <E extends Throwable> void forEachExportRow(
			long companyId, Date startDate, Date endDate,
			UnsafeConsumer<ResultSet, E> rowConsumer)
		throws E, SystemException {

		DataSource dataSource = searchEventPersistence.getDataSource();

		Connection currentConnection = CurrentConnectionUtil.getConnection(
			dataSource);

		try {
			if (currentConnection != null) {
				_stream(
					currentConnection, companyId, startDate, endDate,
					rowConsumer);

				return;
			}

			try (Connection connection = dataSource.getConnection()) {
				_stream(
					connection, companyId, startDate, endDate, rowConsumer);
			}
		}
		catch (SQLException sqlException) {
			throw new SystemException(sqlException);
		}
	}

	private <E extends Throwable> void _stream(
			Connection connection, long companyId, Date startDate,
			Date endDate, UnsafeConsumer<ResultSet, E> rowConsumer)
		throws E, SQLException {

		boolean autoCommit = connection.getAutoCommit();

		// A server side cursor only materialises on demand. Without this
		// PostgreSQL buffers the whole result, which for this query is 6
		// million rows, and the streaming guarantee in DESIGN.md 6.1 is lost
		// before the writer sees a single row.

		if (autoCommit) {
			connection.setAutoCommit(false);
		}

		try (PreparedStatement preparedStatement = connection.prepareStatement(
				_SELECT_SQL)) {

			preparedStatement.setFetchSize(_FETCH_SIZE);
			preparedStatement.setLong(1, companyId);
			preparedStatement.setTimestamp(
				2, _toTimestamp(startDate, Long.MIN_VALUE));
			preparedStatement.setTimestamp(
				3, _toTimestamp(endDate, Long.MAX_VALUE));

			try (ResultSet resultSet = preparedStatement.executeQuery()) {
				while (resultSet.next()) {
					rowConsumer.accept(resultSet);
				}
			}
		}
		finally {
			if (autoCommit) {
				connection.setAutoCommit(true);
			}
		}
	}

	private Timestamp _toTimestamp(Date date, long unbounded) {
		if (date == null) {
			return new Timestamp(unbounded);
		}

		return new Timestamp(date.getTime());
	}

	private static final int _FETCH_SIZE = 1000;

	private static final String _DELETE_SQL =
		"delete from SEL_SearchEvent where companyId = ? and createDate < ?";

	private static final String _SELECT_SQL =
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
												"where e.companyId = ? and " +
													"e.createDate >= ? and " +
														"e.createDate < ? " +
															"order by " +
																"e.createDate, " +
																	"e.uuid_, " +
																		"h.rank_";

}
