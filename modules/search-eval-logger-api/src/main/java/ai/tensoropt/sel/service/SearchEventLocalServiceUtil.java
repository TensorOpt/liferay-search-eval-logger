/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package ai.tensoropt.sel.service;

import ai.tensoropt.sel.model.SearchEvent;

import com.liferay.petra.sql.dsl.query.DSLQuery;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.model.PersistedModel;
import com.liferay.portal.kernel.module.service.Snapshot;
import com.liferay.portal.kernel.util.OrderByComparator;

import java.io.Serializable;

import java.util.List;
import java.util.Map;

/**
 * Provides the local service utility for SearchEvent. This utility wraps
 * <code>ai.tensoropt.sel.service.impl.SearchEventLocalServiceImpl</code> and
 * is an access point for service operations in application layer code running
 * on the local server. Methods of this service will not have security checks
 * based on the propagated JAAS credentials because this service can only be
 * accessed from within the same VM.
 *
 * @author Brian Wing Shun Chan
 * @see SearchEventLocalService
 * @generated
 */
public class SearchEventLocalServiceUtil {

	/*
	 * NOTE FOR DEVELOPERS:
	 *
	 * Never modify this class directly. Add custom service methods to <code>ai.tensoropt.sel.service.impl.SearchEventLocalServiceImpl</code> and rerun ServiceBuilder to regenerate this class.
	 */

	/**
	 * Adds the search event to the database. Also notifies the appropriate model listeners.
	 *
	 * <p>
	 * <strong>Important:</strong> Inspect SearchEventLocalServiceImpl for overloaded versions of the method. If provided, use these entry points to the API, as the implementation logic may require the additional parameters defined there.
	 * </p>
	 *
	 * @param searchEvent the search event
	 * @return the search event that was added
	 */
	public static SearchEvent addSearchEvent(SearchEvent searchEvent) {
		return getService().addSearchEvent(searchEvent);
	}

	/**
	 * @throws PortalException
	 */
	public static PersistedModel createPersistedModel(
			Serializable primaryKeyObj)
		throws PortalException {

		return getService().createPersistedModel(primaryKeyObj);
	}

	/**
	 * Creates a new search event with the primary key. Does not add the search event to the database.
	 *
	 * @param searchEventId the primary key for the new search event
	 * @return the new search event
	 */
	public static SearchEvent createSearchEvent(long searchEventId) {
		return getService().createSearchEvent(searchEventId);
	}

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
	public static int deleteByCompanyIdAndCreateDateBefore(
		long companyId, java.util.Date cutoffDate) {

		return getService().deleteByCompanyIdAndCreateDateBefore(
			companyId, cutoffDate);
	}

	/**
	 * @throws PortalException
	 */
	public static PersistedModel deletePersistedModel(
			PersistedModel persistedModel)
		throws PortalException {

		return getService().deletePersistedModel(persistedModel);
	}

	/**
	 * Deletes the search event with the primary key from the database. Also notifies the appropriate model listeners.
	 *
	 * <p>
	 * <strong>Important:</strong> Inspect SearchEventLocalServiceImpl for overloaded versions of the method. If provided, use these entry points to the API, as the implementation logic may require the additional parameters defined there.
	 * </p>
	 *
	 * @param searchEventId the primary key of the search event
	 * @return the search event that was removed
	 * @throws PortalException if a search event with the primary key could not be found
	 */
	public static SearchEvent deleteSearchEvent(long searchEventId)
		throws PortalException {

		return getService().deleteSearchEvent(searchEventId);
	}

	/**
	 * Deletes the search event from the database. Also notifies the appropriate model listeners.
	 *
	 * <p>
	 * <strong>Important:</strong> Inspect SearchEventLocalServiceImpl for overloaded versions of the method. If provided, use these entry points to the API, as the implementation logic may require the additional parameters defined there.
	 * </p>
	 *
	 * @param searchEvent the search event
	 * @return the search event that was removed
	 */
	public static SearchEvent deleteSearchEvent(SearchEvent searchEvent) {
		return getService().deleteSearchEvent(searchEvent);
	}

	public static <T> T dslQuery(DSLQuery dslQuery) {
		return getService().dslQuery(dslQuery);
	}

	public static int dslQueryCount(DSLQuery dslQuery) {
		return getService().dslQueryCount(dslQuery);
	}

	public static DynamicQuery dynamicQuery() {
		return getService().dynamicQuery();
	}

	/**
	 * Performs a dynamic query on the database and returns the matching rows.
	 *
	 * @param dynamicQuery the dynamic query
	 * @return the matching rows
	 */
	public static <T> List<T> dynamicQuery(DynamicQuery dynamicQuery) {
		return getService().dynamicQuery(dynamicQuery);
	}

	/**
	 * Performs a dynamic query on the database and returns a range of the matching rows.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>ai.tensoropt.sel.model.impl.SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param dynamicQuery the dynamic query
	 * @param start the lower bound of the range of model instances
	 * @param end the upper bound of the range of model instances (not inclusive)
	 * @return the range of matching rows
	 */
	public static <T> List<T> dynamicQuery(
		DynamicQuery dynamicQuery, int start, int end) {

		return getService().dynamicQuery(dynamicQuery, start, end);
	}

	/**
	 * Performs a dynamic query on the database and returns an ordered range of the matching rows.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>ai.tensoropt.sel.model.impl.SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param dynamicQuery the dynamic query
	 * @param start the lower bound of the range of model instances
	 * @param end the upper bound of the range of model instances (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @return the ordered range of matching rows
	 */
	public static <T> List<T> dynamicQuery(
		DynamicQuery dynamicQuery, int start, int end,
		OrderByComparator<T> orderByComparator) {

		return getService().dynamicQuery(
			dynamicQuery, start, end, orderByComparator);
	}

	/**
	 * Returns the number of rows matching the dynamic query.
	 *
	 * @param dynamicQuery the dynamic query
	 * @return the number of rows matching the dynamic query
	 */
	public static long dynamicQueryCount(DynamicQuery dynamicQuery) {
		return getService().dynamicQueryCount(dynamicQuery);
	}

	/**
	 * Returns the number of rows matching the dynamic query.
	 *
	 * @param dynamicQuery the dynamic query
	 * @param projection the projection to apply to the query
	 * @return the number of rows matching the dynamic query
	 */
	public static long dynamicQueryCount(
		DynamicQuery dynamicQuery,
		com.liferay.portal.kernel.dao.orm.Projection projection) {

		return getService().dynamicQueryCount(dynamicQuery, projection);
	}

	public static SearchEvent fetchSearchEvent(long searchEventId) {
		return getService().fetchSearchEvent(searchEventId);
	}

	/**
	 * Returns the search event with the matching UUID and company.
	 *
	 * @param uuid the search event's UUID
	 * @param companyId the primary key of the company
	 * @return the matching search event, or <code>null</code> if a matching search event could not be found
	 */
	public static SearchEvent fetchSearchEventByUuidAndCompanyId(
		String uuid, long companyId) {

		return getService().fetchSearchEventByUuidAndCompanyId(uuid, companyId);
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
	public static <E extends Throwable> void forEachExportRow(
			long companyId, java.util.Date startDate, java.util.Date endDate,
			com.liferay.petra.function.UnsafeConsumer<java.sql.ResultSet, E>
				rowConsumer)
		throws E, SystemException {

		getService().forEachExportRow(
			companyId, startDate, endDate, rowConsumer);
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
	public static <E extends Throwable> void forEachZeroResultFirstPageQuery(
			long companyId,
			com.liferay.petra.function.UnsafeConsumer<String, E> queryConsumer)
		throws E, SystemException {

		getService().forEachZeroResultFirstPageQuery(companyId, queryConsumer);
	}

	public static com.liferay.portal.kernel.dao.orm.ActionableDynamicQuery
		getActionableDynamicQuery() {

		return getService().getActionableDynamicQuery();
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
	public static long getCountByCompanyIdAndCreateDateOnOrAfter(
		long companyId, java.util.Date startDate) {

		return getService().getCountByCompanyIdAndCreateDateOnOrAfter(
			companyId, startDate);
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
	public static Map<String, long[]> getFirstPageSearchCountsByAudienceType(
		long companyId) {

		return getService().getFirstPageSearchCountsByAudienceType(companyId);
	}

	public static
		com.liferay.portal.kernel.dao.orm.IndexableActionableDynamicQuery
			getIndexableActionableDynamicQuery() {

		return getService().getIndexableActionableDynamicQuery();
	}

	/**
	 * Returns the OSGi service identifier.
	 *
	 * @return the OSGi service identifier
	 */
	public static String getOSGiServiceIdentifier() {
		return getService().getOSGiServiceIdentifier();
	}

	/**
	 * @throws PortalException
	 */
	public static PersistedModel getPersistedModel(Serializable primaryKeyObj)
		throws PortalException {

		return getService().getPersistedModel(primaryKeyObj);
	}

	/**
	 * Returns the search event with the primary key.
	 *
	 * @param searchEventId the primary key of the search event
	 * @return the search event
	 * @throws PortalException if a search event with the primary key could not be found
	 */
	public static SearchEvent getSearchEvent(long searchEventId)
		throws PortalException {

		return getService().getSearchEvent(searchEventId);
	}

	/**
	 * Returns the search event with the matching UUID and company.
	 *
	 * @param uuid the search event's UUID
	 * @param companyId the primary key of the company
	 * @return the matching search event
	 * @throws PortalException if a matching search event could not be found
	 */
	public static SearchEvent getSearchEventByUuidAndCompanyId(
			String uuid, long companyId)
		throws PortalException {

		return getService().getSearchEventByUuidAndCompanyId(uuid, companyId);
	}

	/**
	 * Returns a range of all the search events.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>ai.tensoropt.sel.model.impl.SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @return the range of search events
	 */
	public static List<SearchEvent> getSearchEvents(int start, int end) {
		return getService().getSearchEvents(start, end);
	}

	/**
	 * Returns the number of search events.
	 *
	 * @return the number of search events
	 */
	public static int getSearchEventsCount() {
		return getService().getSearchEventsCount();
	}

	/**
	 * Updates the search event in the database or adds it if it does not yet exist. Also notifies the appropriate model listeners.
	 *
	 * <p>
	 * <strong>Important:</strong> Inspect SearchEventLocalServiceImpl for overloaded versions of the method. If provided, use these entry points to the API, as the implementation logic may require the additional parameters defined there.
	 * </p>
	 *
	 * @param searchEvent the search event
	 * @return the search event that was updated
	 */
	public static SearchEvent updateSearchEvent(SearchEvent searchEvent) {
		return getService().updateSearchEvent(searchEvent);
	}

	public static SearchEventLocalService getService() {
		return _serviceSnapshot.get();
	}

	private static final Snapshot<SearchEventLocalService> _serviceSnapshot =
		new Snapshot<>(
			SearchEventLocalServiceUtil.class, SearchEventLocalService.class);

}
// LIFERAY-SERVICE-BUILDER-HASH:2141788713