/**
 * SPDX-License-Identifier: Apache-2.0
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.osgi.service.component.annotations.Component;

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
	 * Counts first-page searches and, within them, zero-result searches,
	 * grouped by audience type, for the zero-result panel on the export
	 * screen (DESIGN.md 6).
	 *
	 * <p>
	 * "First-page" is <code>requestedFrom == 0</code>: a caller re-reading
	 * page two of an earlier query must not count a second time, for either
	 * the rate or the per-audience denominator. "Zero-result" is
	 * <code>totalHits == 0</code>. Both match the jq recipes the panel's
	 * figures are checked against.
	 * </p>
	 *
	 * <p>
	 * One row per distinct <code>audienceType</code> value actually present
	 * in the data. The panel's overall totals are the caller's sum across
	 * every row this returns, which is correct regardless of which audience
	 * type values exist, rather than a second query of its own.
	 * </p>
	 *
	 * <p>
	 * Raw JDBC and a <code>group by</code>, for the same reason as every
	 * other method here: this aggregates over the whole table and must never
	 * materialise a row as an entity to do it.
	 * </p>
	 *
	 * @return a map from the stored <code>audienceType</code> value to a pair of <code>{searchCount, zeroResultSearchCount}</code>
	 */
	public Map<String, long[]> getFirstPageSearchCountsByAudienceType(
		long companyId) {

		return ServiceConnections.withConnection(
			searchEventPersistence.getDataSource(),
			connection -> {
				try (PreparedStatement preparedStatement =
						connection.prepareStatement(
							_AUDIENCE_COUNTS_SQL)) {

					preparedStatement.setLong(1, companyId);

					CompletableFuture<Void> cancellation =
						_scheduleCancellation(
							preparedStatement, _PANEL_QUERY_TIMEOUT_SECONDS);

					try {
						Map<String, long[]> counts = new HashMap<>();

						try (ResultSet resultSet =
								preparedStatement.executeQuery()) {

							while (resultSet.next()) {
								counts.put(
									resultSet.getString(1),
									new long[] {
										resultSet.getLong(2),
										resultSet.getLong(3)
									});
							}
						}

						return counts;
					}
					finally {
						cancellation.cancel(false);
					}
				}
			});
	}

	/**
	 * Streams the exact, unmodified query text of every zero-result
	 * first-page search in one virtual instance, one row at a time, via a
	 * forward-only cursor, for the caller to normalise and count as it
	 * consumes them.
	 *
	 * <p>
	 * <b>Deliberately not <code>group by queryText</code>.</b> An earlier
	 * version of this method grouped in SQL. That groups under the table's
	 * own collation, which on MySQL 8.4's default
	 * (<code>utf8mb4_0900_ai_ci</code>) and on MariaDB is case- and
	 * accent-insensitive: "café", "cafÉ" and "cafe" would merge into one
	 * arbitrary row, silently disagreeing with the acceptance jq recipe's
	 * ASCII-only <code>ascii_downcase</code>, which keeps "café" and "cafe"
	 * apart (it folds only <code>A</code>-<code>Z</code>). PostgreSQL's
	 * default collation is exact, which is why grouping in SQL looked
	 * correct there and nowhere else. Returning the raw string and counting
	 * in the caller, exactly matching the recipe's own normalisation
	 * (<code>ai.tensoropt.sel.web.internal.zeroresult.ZeroResultQueryNormalizer</code>),
	 * is the one grouping rule that behaves identically on every database
	 * Liferay supports, including ones this project has not tested against;
	 * an unsupported dialect's own <code>group by</code> collation default
	 * is not something this method can know in advance, and must not be
	 * guessed at with a dialect-specific cast or function such as
	 * <code>md5()</code>, which does not exist on every supported database
	 * either.
	 * </p>
	 *
	 * <p>
	 * Raw JDBC with a server side cursor, the same pattern
	 * {@link #forEachExportRow} uses and for the same reason: the caller is
	 * expected to normalise and count as it consumes rows, not collect them,
	 * so memory stays bounded by the number of distinct normalised queries,
	 * never by the number of zero-result searches.
	 * </p>
	 */
	public <E extends Throwable> void forEachZeroResultFirstPageQuery(
			long companyId, UnsafeConsumer<String, E> queryConsumer)
		throws E, SystemException {

		ServiceConnections.withConnection(
			searchEventPersistence.getDataSource(),
			connection -> {
				_streamZeroResultQueries(
					connection, companyId, queryConsumer);

				return null;
			});
	}

	private <E extends Throwable> void _streamZeroResultQueries(
			Connection connection, long companyId,
			UnsafeConsumer<String, E> queryConsumer)
		throws E, SQLException {

		this.<E>_streamQuery(
			connection, _ZERO_RESULT_QUERY_SQL,
			preparedStatement -> preparedStatement.setLong(1, companyId),
			_PANEL_QUERY_TIMEOUT_SECONDS,
			resultSet -> {

				// Reading the column is wrapped here, not left to propagate
				// as SQLException: the row callback's own type (E) is
				// whatever queryConsumer's caller chose, which is under no
				// obligation to cover SQLException, so it is rethrown
				// unchecked, the same type ServiceConnections.withConnection
				// already wraps every other SQLException in this class as.

				String queryText;

				try {
					queryText = resultSet.getString(1);
				}
				catch (SQLException sqlException) {
					throw new SystemException(sqlException);
				}

				if (queryText != null) {
					queryConsumer.accept(queryText);
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
	 * timestamp; see <code>ExportRowQuery</code>.
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

		_streamQuery(
			connection, exportRowQuery.getSQL(),
			exportRowQuery::setParameters, _NO_TIMEOUT, rowConsumer);
	}

	/**
	 * The cursor scaffolding {@link #_stream} and
	 * {@link #_streamZeroResultQueries} both need and otherwise duplicated:
	 * disable autocommit so the driver honours the fetch size instead of
	 * buffering the whole result (PostgreSQL would, for a query that can
	 * return millions of rows, losing the streaming guarantee DESIGN.md
	 * 6.1 depends on before the caller sees a single row), set the
	 * statement's parameters, and walk the result set one row at a time.
	 * Restores autocommit afterward either way.
	 *
	 * <p>
	 * <code>timeoutSeconds</code> of <code>_NO_TIMEOUT</code> (zero) means
	 * none: the export's cursor can run as long as the export itself does,
	 * and only the zero-result panel's queries are ever bounded.
	 * </p>
	 */
	private <E extends Throwable> void _streamQuery(
			Connection connection, String sql,
			UnsafeConsumer<PreparedStatement, SQLException> parameterSetter,
			int timeoutSeconds, UnsafeConsumer<ResultSet, E> rowConsumer)
		throws E, SQLException {

		boolean autoCommit = connection.getAutoCommit();

		if (autoCommit) {
			connection.setAutoCommit(false);
		}

		try (PreparedStatement preparedStatement = connection.prepareStatement(
				sql)) {

			preparedStatement.setFetchSize(_FETCH_SIZE);

			parameterSetter.accept(preparedStatement);

			CompletableFuture<Void> cancellation = _scheduleCancellation(
				preparedStatement, timeoutSeconds);

			try (ResultSet resultSet = preparedStatement.executeQuery()) {
				while (resultSet.next()) {
					rowConsumer.accept(resultSet);
				}
			}
			finally {
				cancellation.cancel(false);
			}
		}
		finally {
			if (autoCommit) {
				connection.setAutoCommit(true);
			}
		}
	}

	/**
	 * Bounds a query to <code>timeoutSeconds</code>, or schedules nothing at
	 * all when it is <code>_NO_TIMEOUT</code>. The standard query timeout
	 * alone is not enough: the MariaDB driver DXP 2025.Q1 ships (3.2.0)
	 * ignores it against a MySQL server and stays blocked on a table lock,
	 * while every driver tested honours <code>Statement.cancel()</code>. So
	 * both are set, the cancel on the JDK's own delayed executor, which
	 * leaves this bundle no thread to stop. The caller cancels the returned
	 * future once the query is done.
	 */
	private static CompletableFuture<Void> _scheduleCancellation(
			PreparedStatement preparedStatement, int timeoutSeconds)
		throws SQLException {

		if (timeoutSeconds <= _NO_TIMEOUT) {
			return CompletableFuture.completedFuture(null);
		}

		preparedStatement.setQueryTimeout(timeoutSeconds);

		return CompletableFuture.runAsync(
			() -> {
				try {
					preparedStatement.cancel();
				}
				catch (SQLException sqlException) {

					// Best effort: the query's own timeout still applies

				}
			},
			CompletableFuture.delayedExecutor(
				timeoutSeconds, TimeUnit.SECONDS));
	}

	private static final String _AUDIENCE_COUNTS_SQL =
		"select audienceType, count(*), sum(case when totalHits = 0 then " +
			"1 else 0 end) from SEL_SearchEvent where companyId = ? and " +
				"requestedFrom = 0 group by audienceType";

	private static final int _FETCH_SIZE = 1000;

	/**
	 * Passed as a query's timeout to mean none is scheduled at all, rather
	 * than a real zero second timeout.
	 */
	private static final int _NO_TIMEOUT = 0;

	/**
	 * The zero-result panel's two queries run on each cache miss
	 * (ZeroResultPanelLoader caches a computed panel for an hour per virtual
	 * instance), on a table the export screen did not otherwise touch.
	 * Without a timeout, anything holding an exclusive lock on the table (a
	 * schema change, the e2e suite's queue-overflow case) froze the whole
	 * screen. On a timeout the driver throws, and ZeroResultPanelLoader
	 * leaves the panel out while the rest of the screen renders.
	 */
	private static final int _PANEL_QUERY_TIMEOUT_SECONDS = 10;

	private static final String _COUNT_SQL =
		"select count(*) from SEL_SearchEvent where companyId = ? and " +
			"createDate >= ?";

	private static final String _ZERO_RESULT_QUERY_SQL =
		"select queryText from SEL_SearchEvent where companyId = ? and " +
			"requestedFrom = 0 and totalHits = 0";

}
