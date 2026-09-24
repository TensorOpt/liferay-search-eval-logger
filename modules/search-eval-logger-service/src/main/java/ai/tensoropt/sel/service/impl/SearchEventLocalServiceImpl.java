/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package ai.tensoropt.sel.service.impl;

import com.liferay.portal.aop.AopService;
import com.liferay.petra.function.UnsafeConsumer;
import com.liferay.portal.kernel.exception.SystemException;

import ai.tensoropt.sel.service.base.SearchEventLocalServiceBaseImpl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import java.util.Date;

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
	 */
	public int deleteByCompanyIdAndCreateDateBefore(
		long companyId, Date cutoffDate) {

		return ServiceConnections.deleteByCompanyIdAndCreateDateBefore(
			searchEventPersistence.getDataSource(), "SEL_SearchEvent",
			companyId, cutoffDate);
	}

	/**
	 * Counts the events of one virtual instance recorded on or after a date.
	 *
	 * <p>
	 * The readiness check of DESIGN.md 3.6 needs the number of events collected
	 * since the collection start date, and the counters in
	 * <code>SearchEvalLoggerStatistics</code> cannot answer it: they are
	 * process-wide and reset on restart, so on any instance that has been
	 * restarted since collection began they undercount, silently and by an
	 * unknown amount.
	 * </p>
	 *
	 * <p>
	 * Raw JDBC for the same reason as the delete above: the generated finder
	 * would materialise entities to count them. This runs once a day per
	 * virtual instance, on the same <code>(companyId, createDate)</code> index
	 * the export and the purge use.
	 * </p>
	 *
	 * <p>
	 * Named with a <code>get</code> prefix on purpose. Service Builder decides
	 * from the prefix whether a generated method is annotated
	 * <code>&#64;Transactional(propagation = SUPPORTS, readOnly = true)</code>,
	 * and <code>count</code> is not one of the prefixes it recognises, so the
	 * obvious name gave this a read-write <code>Isolation.PORTAL</code>
	 * transaction for a <code>select count(*)</code>.
	 * </p>
	 */
	public long getCountByCompanyIdAndCreateDateOnOrAfter(
		long companyId, Date startDate) {

		return ServiceConnections.withConnection(
			searchEventPersistence.getDataSource(),
			connection -> {
				try (PreparedStatement preparedStatement =
						connection.prepareStatement(_COUNT_SQL)) {

					preparedStatement.setLong(1, companyId);
					preparedStatement.setTimestamp(
						2, new Timestamp(startDate.getTime()));

					try (ResultSet resultSet =
							preparedStatement.executeQuery()) {

						return resultSet.next() ? resultSet.getLong(1) : 0L;
					}
				}
			});
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
	 *
	 * <p>
	 * Either bound may be null, which the export screen offers in as many
	 * words, and which means that end of the range is unbounded. The clause is
	 * then left out of the statement rather than filled with an extreme
	 * timestamp; see {@link ExportRowQuery}.
	 * </p>
	 */
	public <E extends Throwable> void forEachExportRow(
			long companyId, Date startDate, Date endDate,
			UnsafeConsumer<ResultSet, E> rowConsumer)
		throws E, SystemException {

		ServiceConnections.withConnection(
			searchEventPersistence.getDataSource(),
			connection -> {
				_stream(connection, companyId, startDate, endDate, rowConsumer);

				return null;
			});
	}

	private <E extends Throwable> void _stream(
			Connection connection, long companyId, Date startDate,
			Date endDate, UnsafeConsumer<ResultSet, E> rowConsumer)
		throws E, SQLException {

		ExportRowQuery exportRowQuery = new ExportRowQuery(
			companyId, startDate, endDate);

		boolean autoCommit = connection.getAutoCommit();

		// A server side cursor only materialises on demand. Without this
		// PostgreSQL buffers the whole result, which for this query is 6
		// million rows, and the streaming guarantee in DESIGN.md 6.1 is lost
		// before the writer sees a single row.

		if (autoCommit) {
			connection.setAutoCommit(false);
		}

		try (PreparedStatement preparedStatement = connection.prepareStatement(
				exportRowQuery.getSQL())) {

			preparedStatement.setFetchSize(_FETCH_SIZE);

			exportRowQuery.setParameters(preparedStatement);

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

	private static final int _FETCH_SIZE = 1000;

	private static final String _COUNT_SQL =
		"select count(*) from SEL_SearchEvent where companyId = ? and " +
			"createDate >= ?";

}
