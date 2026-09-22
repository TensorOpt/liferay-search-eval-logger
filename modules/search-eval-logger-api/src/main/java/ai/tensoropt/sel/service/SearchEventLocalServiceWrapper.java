/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package ai.tensoropt.sel.service;

import com.liferay.portal.kernel.service.ServiceWrapper;
import com.liferay.portal.kernel.service.persistence.BasePersistence;

/**
 * Provides a wrapper for {@link SearchEventLocalService}.
 *
 * @author Brian Wing Shun Chan
 * @see SearchEventLocalService
 * @generated
 */
public class SearchEventLocalServiceWrapper
	implements SearchEventLocalService,
			   ServiceWrapper<SearchEventLocalService> {

	public SearchEventLocalServiceWrapper() {
		this(null);
	}

	public SearchEventLocalServiceWrapper(
		SearchEventLocalService searchEventLocalService) {

		_searchEventLocalService = searchEventLocalService;
	}

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
	@Override
	public ai.tensoropt.sel.model.SearchEvent addSearchEvent(
		ai.tensoropt.sel.model.SearchEvent searchEvent) {

		return _searchEventLocalService.addSearchEvent(searchEvent);
	}

	/**
	 * @throws PortalException
	 */
	@Override
	public com.liferay.portal.kernel.model.PersistedModel createPersistedModel(
			java.io.Serializable primaryKeyObj)
		throws com.liferay.portal.kernel.exception.PortalException {

		return _searchEventLocalService.createPersistedModel(primaryKeyObj);
	}

	/**
	 * Creates a new search event with the primary key. Does not add the search event to the database.
	 *
	 * @param searchEventId the primary key for the new search event
	 * @return the new search event
	 */
	@Override
	public ai.tensoropt.sel.model.SearchEvent createSearchEvent(
		long searchEventId) {

		return _searchEventLocalService.createSearchEvent(searchEventId);
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
	@Override
	public int deleteByCompanyIdAndCreateDateBefore(
		long companyId, java.util.Date cutoffDate) {

		return _searchEventLocalService.deleteByCompanyIdAndCreateDateBefore(
			companyId, cutoffDate);
	}

	/**
	 * @throws PortalException
	 */
	@Override
	public com.liferay.portal.kernel.model.PersistedModel deletePersistedModel(
			com.liferay.portal.kernel.model.PersistedModel persistedModel)
		throws com.liferay.portal.kernel.exception.PortalException {

		return _searchEventLocalService.deletePersistedModel(persistedModel);
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
	@Override
	public ai.tensoropt.sel.model.SearchEvent deleteSearchEvent(
			long searchEventId)
		throws com.liferay.portal.kernel.exception.PortalException {

		return _searchEventLocalService.deleteSearchEvent(searchEventId);
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
	@Override
	public ai.tensoropt.sel.model.SearchEvent deleteSearchEvent(
		ai.tensoropt.sel.model.SearchEvent searchEvent) {

		return _searchEventLocalService.deleteSearchEvent(searchEvent);
	}

	@Override
	public <T> T dslQuery(com.liferay.petra.sql.dsl.query.DSLQuery dslQuery) {
		return _searchEventLocalService.dslQuery(dslQuery);
	}

	@Override
	public int dslQueryCount(
		com.liferay.petra.sql.dsl.query.DSLQuery dslQuery) {

		return _searchEventLocalService.dslQueryCount(dslQuery);
	}

	@Override
	public com.liferay.portal.kernel.dao.orm.DynamicQuery dynamicQuery() {
		return _searchEventLocalService.dynamicQuery();
	}

	/**
	 * Performs a dynamic query on the database and returns the matching rows.
	 *
	 * @param dynamicQuery the dynamic query
	 * @return the matching rows
	 */
	@Override
	public <T> java.util.List<T> dynamicQuery(
		com.liferay.portal.kernel.dao.orm.DynamicQuery dynamicQuery) {

		return _searchEventLocalService.dynamicQuery(dynamicQuery);
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
	@Override
	public <T> java.util.List<T> dynamicQuery(
		com.liferay.portal.kernel.dao.orm.DynamicQuery dynamicQuery, int start,
		int end) {

		return _searchEventLocalService.dynamicQuery(dynamicQuery, start, end);
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
	@Override
	public <T> java.util.List<T> dynamicQuery(
		com.liferay.portal.kernel.dao.orm.DynamicQuery dynamicQuery, int start,
		int end,
		com.liferay.portal.kernel.util.OrderByComparator<T> orderByComparator) {

		return _searchEventLocalService.dynamicQuery(
			dynamicQuery, start, end, orderByComparator);
	}

	/**
	 * Returns the number of rows matching the dynamic query.
	 *
	 * @param dynamicQuery the dynamic query
	 * @return the number of rows matching the dynamic query
	 */
	@Override
	public long dynamicQueryCount(
		com.liferay.portal.kernel.dao.orm.DynamicQuery dynamicQuery) {

		return _searchEventLocalService.dynamicQueryCount(dynamicQuery);
	}

	/**
	 * Returns the number of rows matching the dynamic query.
	 *
	 * @param dynamicQuery the dynamic query
	 * @param projection the projection to apply to the query
	 * @return the number of rows matching the dynamic query
	 */
	@Override
	public long dynamicQueryCount(
		com.liferay.portal.kernel.dao.orm.DynamicQuery dynamicQuery,
		com.liferay.portal.kernel.dao.orm.Projection projection) {

		return _searchEventLocalService.dynamicQueryCount(
			dynamicQuery, projection);
	}

	@Override
	public ai.tensoropt.sel.model.SearchEvent fetchSearchEvent(
		long searchEventId) {

		return _searchEventLocalService.fetchSearchEvent(searchEventId);
	}

	/**
	 * Returns the search event with the matching UUID and company.
	 *
	 * @param uuid the search event's UUID
	 * @param companyId the primary key of the company
	 * @return the matching search event, or <code>null</code> if a matching search event could not be found
	 */
	@Override
	public ai.tensoropt.sel.model.SearchEvent
		fetchSearchEventByUuidAndCompanyId(String uuid, long companyId) {

		return _searchEventLocalService.fetchSearchEventByUuidAndCompanyId(
			uuid, companyId);
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
	@Override
	public <E extends Throwable> void forEachExportRow(
			long companyId, java.util.Date startDate, java.util.Date endDate,
			com.liferay.petra.function.UnsafeConsumer<java.sql.ResultSet, E>
				rowConsumer)
		throws com.liferay.portal.kernel.exception.SystemException, E {

		_searchEventLocalService.forEachExportRow(
			companyId, startDate, endDate, rowConsumer);
	}

	@Override
	public com.liferay.portal.kernel.dao.orm.ActionableDynamicQuery
		getActionableDynamicQuery() {

		return _searchEventLocalService.getActionableDynamicQuery();
	}

	@Override
	public com.liferay.portal.kernel.dao.orm.IndexableActionableDynamicQuery
		getIndexableActionableDynamicQuery() {

		return _searchEventLocalService.getIndexableActionableDynamicQuery();
	}

	/**
	 * Returns the OSGi service identifier.
	 *
	 * @return the OSGi service identifier
	 */
	@Override
	public String getOSGiServiceIdentifier() {
		return _searchEventLocalService.getOSGiServiceIdentifier();
	}

	/**
	 * @throws PortalException
	 */
	@Override
	public com.liferay.portal.kernel.model.PersistedModel getPersistedModel(
			java.io.Serializable primaryKeyObj)
		throws com.liferay.portal.kernel.exception.PortalException {

		return _searchEventLocalService.getPersistedModel(primaryKeyObj);
	}

	/**
	 * Returns the search event with the primary key.
	 *
	 * @param searchEventId the primary key of the search event
	 * @return the search event
	 * @throws PortalException if a search event with the primary key could not be found
	 */
	@Override
	public ai.tensoropt.sel.model.SearchEvent getSearchEvent(long searchEventId)
		throws com.liferay.portal.kernel.exception.PortalException {

		return _searchEventLocalService.getSearchEvent(searchEventId);
	}

	/**
	 * Returns the search event with the matching UUID and company.
	 *
	 * @param uuid the search event's UUID
	 * @param companyId the primary key of the company
	 * @return the matching search event
	 * @throws PortalException if a matching search event could not be found
	 */
	@Override
	public ai.tensoropt.sel.model.SearchEvent getSearchEventByUuidAndCompanyId(
			String uuid, long companyId)
		throws com.liferay.portal.kernel.exception.PortalException {

		return _searchEventLocalService.getSearchEventByUuidAndCompanyId(
			uuid, companyId);
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
	@Override
	public java.util.List<ai.tensoropt.sel.model.SearchEvent> getSearchEvents(
		int start, int end) {

		return _searchEventLocalService.getSearchEvents(start, end);
	}

	/**
	 * Returns the number of search events.
	 *
	 * @return the number of search events
	 */
	@Override
	public int getSearchEventsCount() {
		return _searchEventLocalService.getSearchEventsCount();
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
	@Override
	public ai.tensoropt.sel.model.SearchEvent updateSearchEvent(
		ai.tensoropt.sel.model.SearchEvent searchEvent) {

		return _searchEventLocalService.updateSearchEvent(searchEvent);
	}

	@Override
	public BasePersistence<?> getBasePersistence() {
		return _searchEventLocalService.getBasePersistence();
	}

	@Override
	public SearchEventLocalService getWrappedService() {
		return _searchEventLocalService;
	}

	@Override
	public void setWrappedService(
		SearchEventLocalService searchEventLocalService) {

		_searchEventLocalService = searchEventLocalService;
	}

	private SearchEventLocalService _searchEventLocalService;

}