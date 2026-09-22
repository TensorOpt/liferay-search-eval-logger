/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package ai.tensoropt.sel.service;

import com.liferay.petra.sql.dsl.query.DSLQuery;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.model.PersistedModel;
import com.liferay.portal.kernel.module.service.Snapshot;
import com.liferay.portal.kernel.util.OrderByComparator;

import ai.tensoropt.sel.model.SearchEvent;

import java.io.Serializable;

import java.util.List;

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
	 *
	 * <p>
	 * The connection is the one bound to the current transaction where there
	 * is one, matching what the generated <code>runSQL</code> does, so the
	 * delete commits or rolls back with its caller.
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
	 * An event with no hits still yields one row, with the hit columns null,
	 * so an empty result set is not silently dropped from the export.
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

	public static com.liferay.portal.kernel.dao.orm.ActionableDynamicQuery
		getActionableDynamicQuery() {

		return getService().getActionableDynamicQuery();
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