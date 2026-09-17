/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.tensoropt.search.eval.logger.service;

import com.liferay.portal.kernel.service.ServiceWrapper;
import com.liferay.portal.kernel.service.persistence.BasePersistence;

/**
 * Provides a wrapper for {@link SearchHitLocalService}.
 *
 * @author Brian Wing Shun Chan
 * @see SearchHitLocalService
 * @generated
 */
public class SearchHitLocalServiceWrapper
	implements SearchHitLocalService, ServiceWrapper<SearchHitLocalService> {

	public SearchHitLocalServiceWrapper() {
		this(null);
	}

	public SearchHitLocalServiceWrapper(
		SearchHitLocalService searchHitLocalService) {

		_searchHitLocalService = searchHitLocalService;
	}

	/**
	 * Adds the search hit to the database. Also notifies the appropriate model listeners.
	 *
	 * <p>
	 * <strong>Important:</strong> Inspect SearchHitLocalServiceImpl for overloaded versions of the method. If provided, use these entry points to the API, as the implementation logic may require the additional parameters defined there.
	 * </p>
	 *
	 * @param searchHit the search hit
	 * @return the search hit that was added
	 */
	@Override
	public com.tensoropt.search.eval.logger.model.SearchHit addSearchHit(
		com.tensoropt.search.eval.logger.model.SearchHit searchHit) {

		return _searchHitLocalService.addSearchHit(searchHit);
	}

	/**
	 * @throws PortalException
	 */
	@Override
	public com.liferay.portal.kernel.model.PersistedModel createPersistedModel(
			java.io.Serializable primaryKeyObj)
		throws com.liferay.portal.kernel.exception.PortalException {

		return _searchHitLocalService.createPersistedModel(primaryKeyObj);
	}

	/**
	 * Creates a new search hit with the primary key. Does not add the search hit to the database.
	 *
	 * @param searchHitId the primary key for the new search hit
	 * @return the new search hit
	 */
	@Override
	public com.tensoropt.search.eval.logger.model.SearchHit createSearchHit(
		long searchHitId) {

		return _searchHitLocalService.createSearchHit(searchHitId);
	}

	/**
	 * @throws PortalException
	 */
	@Override
	public com.liferay.portal.kernel.model.PersistedModel deletePersistedModel(
			com.liferay.portal.kernel.model.PersistedModel persistedModel)
		throws com.liferay.portal.kernel.exception.PortalException {

		return _searchHitLocalService.deletePersistedModel(persistedModel);
	}

	/**
	 * Deletes the search hit with the primary key from the database. Also notifies the appropriate model listeners.
	 *
	 * <p>
	 * <strong>Important:</strong> Inspect SearchHitLocalServiceImpl for overloaded versions of the method. If provided, use these entry points to the API, as the implementation logic may require the additional parameters defined there.
	 * </p>
	 *
	 * @param searchHitId the primary key of the search hit
	 * @return the search hit that was removed
	 * @throws PortalException if a search hit with the primary key could not be found
	 */
	@Override
	public com.tensoropt.search.eval.logger.model.SearchHit deleteSearchHit(
			long searchHitId)
		throws com.liferay.portal.kernel.exception.PortalException {

		return _searchHitLocalService.deleteSearchHit(searchHitId);
	}

	/**
	 * Deletes the search hit from the database. Also notifies the appropriate model listeners.
	 *
	 * <p>
	 * <strong>Important:</strong> Inspect SearchHitLocalServiceImpl for overloaded versions of the method. If provided, use these entry points to the API, as the implementation logic may require the additional parameters defined there.
	 * </p>
	 *
	 * @param searchHit the search hit
	 * @return the search hit that was removed
	 */
	@Override
	public com.tensoropt.search.eval.logger.model.SearchHit deleteSearchHit(
		com.tensoropt.search.eval.logger.model.SearchHit searchHit) {

		return _searchHitLocalService.deleteSearchHit(searchHit);
	}

	@Override
	public <T> T dslQuery(com.liferay.petra.sql.dsl.query.DSLQuery dslQuery) {
		return _searchHitLocalService.dslQuery(dslQuery);
	}

	@Override
	public int dslQueryCount(
		com.liferay.petra.sql.dsl.query.DSLQuery dslQuery) {

		return _searchHitLocalService.dslQueryCount(dslQuery);
	}

	@Override
	public com.liferay.portal.kernel.dao.orm.DynamicQuery dynamicQuery() {
		return _searchHitLocalService.dynamicQuery();
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

		return _searchHitLocalService.dynamicQuery(dynamicQuery);
	}

	/**
	 * Performs a dynamic query on the database and returns a range of the matching rows.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>com.tensoropt.search.eval.logger.model.impl.SearchHitModelImpl</code>.
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

		return _searchHitLocalService.dynamicQuery(dynamicQuery, start, end);
	}

	/**
	 * Performs a dynamic query on the database and returns an ordered range of the matching rows.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>com.tensoropt.search.eval.logger.model.impl.SearchHitModelImpl</code>.
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

		return _searchHitLocalService.dynamicQuery(
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

		return _searchHitLocalService.dynamicQueryCount(dynamicQuery);
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

		return _searchHitLocalService.dynamicQueryCount(
			dynamicQuery, projection);
	}

	@Override
	public com.tensoropt.search.eval.logger.model.SearchHit fetchSearchHit(
		long searchHitId) {

		return _searchHitLocalService.fetchSearchHit(searchHitId);
	}

	@Override
	public com.liferay.portal.kernel.dao.orm.ActionableDynamicQuery
		getActionableDynamicQuery() {

		return _searchHitLocalService.getActionableDynamicQuery();
	}

	@Override
	public com.liferay.portal.kernel.dao.orm.IndexableActionableDynamicQuery
		getIndexableActionableDynamicQuery() {

		return _searchHitLocalService.getIndexableActionableDynamicQuery();
	}

	/**
	 * Returns the OSGi service identifier.
	 *
	 * @return the OSGi service identifier
	 */
	@Override
	public String getOSGiServiceIdentifier() {
		return _searchHitLocalService.getOSGiServiceIdentifier();
	}

	/**
	 * @throws PortalException
	 */
	@Override
	public com.liferay.portal.kernel.model.PersistedModel getPersistedModel(
			java.io.Serializable primaryKeyObj)
		throws com.liferay.portal.kernel.exception.PortalException {

		return _searchHitLocalService.getPersistedModel(primaryKeyObj);
	}

	/**
	 * Returns the search hit with the primary key.
	 *
	 * @param searchHitId the primary key of the search hit
	 * @return the search hit
	 * @throws PortalException if a search hit with the primary key could not be found
	 */
	@Override
	public com.tensoropt.search.eval.logger.model.SearchHit getSearchHit(
			long searchHitId)
		throws com.liferay.portal.kernel.exception.PortalException {

		return _searchHitLocalService.getSearchHit(searchHitId);
	}

	/**
	 * Returns a range of all the search hits.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>com.tensoropt.search.eval.logger.model.impl.SearchHitModelImpl</code>.
	 * </p>
	 *
	 * @param start the lower bound of the range of search hits
	 * @param end the upper bound of the range of search hits (not inclusive)
	 * @return the range of search hits
	 */
	@Override
	public java.util.List<com.tensoropt.search.eval.logger.model.SearchHit>
		getSearchHits(int start, int end) {

		return _searchHitLocalService.getSearchHits(start, end);
	}

	/**
	 * Returns the number of search hits.
	 *
	 * @return the number of search hits
	 */
	@Override
	public int getSearchHitsCount() {
		return _searchHitLocalService.getSearchHitsCount();
	}

	/**
	 * Updates the search hit in the database or adds it if it does not yet exist. Also notifies the appropriate model listeners.
	 *
	 * <p>
	 * <strong>Important:</strong> Inspect SearchHitLocalServiceImpl for overloaded versions of the method. If provided, use these entry points to the API, as the implementation logic may require the additional parameters defined there.
	 * </p>
	 *
	 * @param searchHit the search hit
	 * @return the search hit that was updated
	 */
	@Override
	public com.tensoropt.search.eval.logger.model.SearchHit updateSearchHit(
		com.tensoropt.search.eval.logger.model.SearchHit searchHit) {

		return _searchHitLocalService.updateSearchHit(searchHit);
	}

	@Override
	public BasePersistence<?> getBasePersistence() {
		return _searchHitLocalService.getBasePersistence();
	}

	@Override
	public SearchHitLocalService getWrappedService() {
		return _searchHitLocalService;
	}

	@Override
	public void setWrappedService(SearchHitLocalService searchHitLocalService) {
		_searchHitLocalService = searchHitLocalService;
	}

	private SearchHitLocalService _searchHitLocalService;

}