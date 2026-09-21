/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.tensoropt.search.eval.logger.service.persistence;

import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.OrderByComparator;

import com.tensoropt.search.eval.logger.model.SearchHit;

import java.io.Serializable;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The persistence utility for the search hit service. This utility wraps <code>com.tensoropt.search.eval.logger.service.persistence.impl.SearchHitPersistenceImpl</code> and provides direct access to the database for CRUD operations. This utility should only be used by the service layer, as it must operate within a transaction. Never access this utility in a JSP, controller, model, or other front-end class.
 *
 * <p>
 * Caching information and settings can be found in <code>portal.properties</code>
 * </p>
 *
 * @author Brian Wing Shun Chan
 * @see SearchHitPersistence
 * @generated
 */
public class SearchHitUtil {

	/*
	 * NOTE FOR DEVELOPERS:
	 *
	 * Never modify this class directly. Modify <code>service.xml</code> and rerun ServiceBuilder to regenerate this class.
	 */

	/**
	 * @see com.liferay.portal.kernel.service.persistence.BasePersistence#clearCache()
	 */
	public static void clearCache() {
		getPersistence().clearCache();
	}

	/**
	 * @see com.liferay.portal.kernel.service.persistence.BasePersistence#clearCache(com.liferay.portal.kernel.model.BaseModel)
	 */
	public static void clearCache(SearchHit searchHit) {
		getPersistence().clearCache(searchHit);
	}

	/**
	 * @see com.liferay.portal.kernel.service.persistence.BasePersistence#countWithDynamicQuery(DynamicQuery)
	 */
	public static long countWithDynamicQuery(DynamicQuery dynamicQuery) {
		return getPersistence().countWithDynamicQuery(dynamicQuery);
	}

	/**
	 * @see com.liferay.portal.kernel.service.persistence.BasePersistence#fetchByPrimaryKeys(Set)
	 */
	public static Map<Serializable, SearchHit> fetchByPrimaryKeys(
		Set<Serializable> primaryKeys) {

		return getPersistence().fetchByPrimaryKeys(primaryKeys);
	}

	/**
	 * @see com.liferay.portal.kernel.service.persistence.BasePersistence#findWithDynamicQuery(DynamicQuery)
	 */
	public static List<SearchHit> findWithDynamicQuery(
		DynamicQuery dynamicQuery) {

		return getPersistence().findWithDynamicQuery(dynamicQuery);
	}

	/**
	 * @see com.liferay.portal.kernel.service.persistence.BasePersistence#findWithDynamicQuery(DynamicQuery, int, int)
	 */
	public static List<SearchHit> findWithDynamicQuery(
		DynamicQuery dynamicQuery, int start, int end) {

		return getPersistence().findWithDynamicQuery(dynamicQuery, start, end);
	}

	/**
	 * @see com.liferay.portal.kernel.service.persistence.BasePersistence#findWithDynamicQuery(DynamicQuery, int, int, OrderByComparator)
	 */
	public static List<SearchHit> findWithDynamicQuery(
		DynamicQuery dynamicQuery, int start, int end,
		OrderByComparator<SearchHit> orderByComparator) {

		return getPersistence().findWithDynamicQuery(
			dynamicQuery, start, end, orderByComparator);
	}

	/**
	 * @see com.liferay.portal.kernel.service.persistence.BasePersistence#update(com.liferay.portal.kernel.model.BaseModel)
	 */
	public static SearchHit update(SearchHit searchHit) {
		return getPersistence().update(searchHit);
	}

	/**
	 * @see com.liferay.portal.kernel.service.persistence.BasePersistence#update(com.liferay.portal.kernel.model.BaseModel, ServiceContext)
	 */
	public static SearchHit update(
		SearchHit searchHit, ServiceContext serviceContext) {

		return getPersistence().update(searchHit, serviceContext);
	}

	/**
	 * Returns all the search hits where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @return the matching search hits
	 */
	public static List<SearchHit> findBySearchEventUuid(
		String searchEventUuid) {

		return getPersistence().findBySearchEventUuid(searchEventUuid);
	}

	/**
	 * Returns a range of all the search hits where searchEventUuid = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchHitModelImpl</code>.
	 * </p>
	 *
	 * @param searchEventUuid the search event uuid
	 * @param start the lower bound of the range of search hits
	 * @param end the upper bound of the range of search hits (not inclusive)
	 * @return the range of matching search hits
	 */
	public static List<SearchHit> findBySearchEventUuid(
		String searchEventUuid, int start, int end) {

		return getPersistence().findBySearchEventUuid(
			searchEventUuid, start, end);
	}

	/**
	 * Returns an ordered range of all the search hits where searchEventUuid = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchHitModelImpl</code>.
	 * </p>
	 *
	 * @param searchEventUuid the search event uuid
	 * @param start the lower bound of the range of search hits
	 * @param end the upper bound of the range of search hits (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @return the ordered range of matching search hits
	 */
	public static List<SearchHit> findBySearchEventUuid(
		String searchEventUuid, int start, int end,
		OrderByComparator<SearchHit> orderByComparator) {

		return getPersistence().findBySearchEventUuid(
			searchEventUuid, start, end, orderByComparator);
	}

	/**
	 * Returns an ordered range of all the search hits where searchEventUuid = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchHitModelImpl</code>.
	 * </p>
	 *
	 * @param searchEventUuid the search event uuid
	 * @param start the lower bound of the range of search hits
	 * @param end the upper bound of the range of search hits (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @param useFinderCache whether to use the finder cache
	 * @return the ordered range of matching search hits
	 */
	public static List<SearchHit> findBySearchEventUuid(
		String searchEventUuid, int start, int end,
		OrderByComparator<SearchHit> orderByComparator,
		boolean useFinderCache) {

		return getPersistence().findBySearchEventUuid(
			searchEventUuid, start, end, orderByComparator, useFinderCache);
	}

	/**
	 * Returns the first search hit in the ordered set where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search hit
	 * @throws NoSuchSearchHitException if a matching search hit could not be found
	 */
	public static SearchHit findBySearchEventUuid_First(
			String searchEventUuid,
			OrderByComparator<SearchHit> orderByComparator)
		throws com.tensoropt.search.eval.logger.exception.
			NoSuchSearchHitException {

		return getPersistence().findBySearchEventUuid_First(
			searchEventUuid, orderByComparator);
	}

	/**
	 * Returns the first search hit in the ordered set where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search hit, or <code>null</code> if a matching search hit could not be found
	 */
	public static SearchHit fetchBySearchEventUuid_First(
		String searchEventUuid,
		OrderByComparator<SearchHit> orderByComparator) {

		return getPersistence().fetchBySearchEventUuid_First(
			searchEventUuid, orderByComparator);
	}

	/**
	 * Returns the last search hit in the ordered set where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search hit
	 * @throws NoSuchSearchHitException if a matching search hit could not be found
	 */
	public static SearchHit findBySearchEventUuid_Last(
			String searchEventUuid,
			OrderByComparator<SearchHit> orderByComparator)
		throws com.tensoropt.search.eval.logger.exception.
			NoSuchSearchHitException {

		return getPersistence().findBySearchEventUuid_Last(
			searchEventUuid, orderByComparator);
	}

	/**
	 * Returns the last search hit in the ordered set where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search hit, or <code>null</code> if a matching search hit could not be found
	 */
	public static SearchHit fetchBySearchEventUuid_Last(
		String searchEventUuid,
		OrderByComparator<SearchHit> orderByComparator) {

		return getPersistence().fetchBySearchEventUuid_Last(
			searchEventUuid, orderByComparator);
	}

	/**
	 * Returns the search hits before and after the current search hit in the ordered set where searchEventUuid = &#63;.
	 *
	 * @param searchHitId the primary key of the current search hit
	 * @param searchEventUuid the search event uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the previous, current, and next search hit
	 * @throws NoSuchSearchHitException if a search hit with the primary key could not be found
	 */
	public static SearchHit[] findBySearchEventUuid_PrevAndNext(
			long searchHitId, String searchEventUuid,
			OrderByComparator<SearchHit> orderByComparator)
		throws com.tensoropt.search.eval.logger.exception.
			NoSuchSearchHitException {

		return getPersistence().findBySearchEventUuid_PrevAndNext(
			searchHitId, searchEventUuid, orderByComparator);
	}

	/**
	 * Removes all the search hits where searchEventUuid = &#63; from the database.
	 *
	 * @param searchEventUuid the search event uuid
	 */
	public static void removeBySearchEventUuid(String searchEventUuid) {
		getPersistence().removeBySearchEventUuid(searchEventUuid);
	}

	/**
	 * Returns the number of search hits where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @return the number of matching search hits
	 */
	public static int countBySearchEventUuid(String searchEventUuid) {
		return getPersistence().countBySearchEventUuid(searchEventUuid);
	}

	/**
	 * Returns all the search hits where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @return the matching search hits
	 */
	public static List<SearchHit> findByC_LtCreateDate(
		long companyId, Date createDate) {

		return getPersistence().findByC_LtCreateDate(companyId, createDate);
	}

	/**
	 * Returns a range of all the search hits where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchHitModelImpl</code>.
	 * </p>
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param start the lower bound of the range of search hits
	 * @param end the upper bound of the range of search hits (not inclusive)
	 * @return the range of matching search hits
	 */
	public static List<SearchHit> findByC_LtCreateDate(
		long companyId, Date createDate, int start, int end) {

		return getPersistence().findByC_LtCreateDate(
			companyId, createDate, start, end);
	}

	/**
	 * Returns an ordered range of all the search hits where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchHitModelImpl</code>.
	 * </p>
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param start the lower bound of the range of search hits
	 * @param end the upper bound of the range of search hits (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @return the ordered range of matching search hits
	 */
	public static List<SearchHit> findByC_LtCreateDate(
		long companyId, Date createDate, int start, int end,
		OrderByComparator<SearchHit> orderByComparator) {

		return getPersistence().findByC_LtCreateDate(
			companyId, createDate, start, end, orderByComparator);
	}

	/**
	 * Returns an ordered range of all the search hits where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchHitModelImpl</code>.
	 * </p>
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param start the lower bound of the range of search hits
	 * @param end the upper bound of the range of search hits (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @param useFinderCache whether to use the finder cache
	 * @return the ordered range of matching search hits
	 */
	public static List<SearchHit> findByC_LtCreateDate(
		long companyId, Date createDate, int start, int end,
		OrderByComparator<SearchHit> orderByComparator,
		boolean useFinderCache) {

		return getPersistence().findByC_LtCreateDate(
			companyId, createDate, start, end, orderByComparator,
			useFinderCache);
	}

	/**
	 * Returns the first search hit in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search hit
	 * @throws NoSuchSearchHitException if a matching search hit could not be found
	 */
	public static SearchHit findByC_LtCreateDate_First(
			long companyId, Date createDate,
			OrderByComparator<SearchHit> orderByComparator)
		throws com.tensoropt.search.eval.logger.exception.
			NoSuchSearchHitException {

		return getPersistence().findByC_LtCreateDate_First(
			companyId, createDate, orderByComparator);
	}

	/**
	 * Returns the first search hit in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search hit, or <code>null</code> if a matching search hit could not be found
	 */
	public static SearchHit fetchByC_LtCreateDate_First(
		long companyId, Date createDate,
		OrderByComparator<SearchHit> orderByComparator) {

		return getPersistence().fetchByC_LtCreateDate_First(
			companyId, createDate, orderByComparator);
	}

	/**
	 * Returns the last search hit in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search hit
	 * @throws NoSuchSearchHitException if a matching search hit could not be found
	 */
	public static SearchHit findByC_LtCreateDate_Last(
			long companyId, Date createDate,
			OrderByComparator<SearchHit> orderByComparator)
		throws com.tensoropt.search.eval.logger.exception.
			NoSuchSearchHitException {

		return getPersistence().findByC_LtCreateDate_Last(
			companyId, createDate, orderByComparator);
	}

	/**
	 * Returns the last search hit in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search hit, or <code>null</code> if a matching search hit could not be found
	 */
	public static SearchHit fetchByC_LtCreateDate_Last(
		long companyId, Date createDate,
		OrderByComparator<SearchHit> orderByComparator) {

		return getPersistence().fetchByC_LtCreateDate_Last(
			companyId, createDate, orderByComparator);
	}

	/**
	 * Returns the search hits before and after the current search hit in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param searchHitId the primary key of the current search hit
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the previous, current, and next search hit
	 * @throws NoSuchSearchHitException if a search hit with the primary key could not be found
	 */
	public static SearchHit[] findByC_LtCreateDate_PrevAndNext(
			long searchHitId, long companyId, Date createDate,
			OrderByComparator<SearchHit> orderByComparator)
		throws com.tensoropt.search.eval.logger.exception.
			NoSuchSearchHitException {

		return getPersistence().findByC_LtCreateDate_PrevAndNext(
			searchHitId, companyId, createDate, orderByComparator);
	}

	/**
	 * Removes all the search hits where companyId = &#63; and createDate &lt; &#63; from the database.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 */
	public static void removeByC_LtCreateDate(long companyId, Date createDate) {
		getPersistence().removeByC_LtCreateDate(companyId, createDate);
	}

	/**
	 * Returns the number of search hits where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @return the number of matching search hits
	 */
	public static int countByC_LtCreateDate(long companyId, Date createDate) {
		return getPersistence().countByC_LtCreateDate(companyId, createDate);
	}

	/**
	 * Caches the search hit in the entity cache if it is enabled.
	 *
	 * @param searchHit the search hit
	 */
	public static void cacheResult(SearchHit searchHit) {
		getPersistence().cacheResult(searchHit);
	}

	/**
	 * Caches the search hits in the entity cache if it is enabled.
	 *
	 * @param searchHits the search hits
	 */
	public static void cacheResult(List<SearchHit> searchHits) {
		getPersistence().cacheResult(searchHits);
	}

	/**
	 * Creates a new search hit with the primary key. Does not add the search hit to the database.
	 *
	 * @param searchHitId the primary key for the new search hit
	 * @return the new search hit
	 */
	public static SearchHit create(long searchHitId) {
		return getPersistence().create(searchHitId);
	}

	/**
	 * Removes the search hit with the primary key from the database. Also notifies the appropriate model listeners.
	 *
	 * @param searchHitId the primary key of the search hit
	 * @return the search hit that was removed
	 * @throws NoSuchSearchHitException if a search hit with the primary key could not be found
	 */
	public static SearchHit remove(long searchHitId)
		throws com.tensoropt.search.eval.logger.exception.
			NoSuchSearchHitException {

		return getPersistence().remove(searchHitId);
	}

	public static SearchHit updateImpl(SearchHit searchHit) {
		return getPersistence().updateImpl(searchHit);
	}

	/**
	 * Returns the search hit with the primary key or throws a <code>NoSuchSearchHitException</code> if it could not be found.
	 *
	 * @param searchHitId the primary key of the search hit
	 * @return the search hit
	 * @throws NoSuchSearchHitException if a search hit with the primary key could not be found
	 */
	public static SearchHit findByPrimaryKey(long searchHitId)
		throws com.tensoropt.search.eval.logger.exception.
			NoSuchSearchHitException {

		return getPersistence().findByPrimaryKey(searchHitId);
	}

	/**
	 * Returns the search hit with the primary key or returns <code>null</code> if it could not be found.
	 *
	 * @param searchHitId the primary key of the search hit
	 * @return the search hit, or <code>null</code> if a search hit with the primary key could not be found
	 */
	public static SearchHit fetchByPrimaryKey(long searchHitId) {
		return getPersistence().fetchByPrimaryKey(searchHitId);
	}

	/**
	 * Returns all the search hits.
	 *
	 * @return the search hits
	 */
	public static List<SearchHit> findAll() {
		return getPersistence().findAll();
	}

	/**
	 * Returns a range of all the search hits.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchHitModelImpl</code>.
	 * </p>
	 *
	 * @param start the lower bound of the range of search hits
	 * @param end the upper bound of the range of search hits (not inclusive)
	 * @return the range of search hits
	 */
	public static List<SearchHit> findAll(int start, int end) {
		return getPersistence().findAll(start, end);
	}

	/**
	 * Returns an ordered range of all the search hits.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchHitModelImpl</code>.
	 * </p>
	 *
	 * @param start the lower bound of the range of search hits
	 * @param end the upper bound of the range of search hits (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @return the ordered range of search hits
	 */
	public static List<SearchHit> findAll(
		int start, int end, OrderByComparator<SearchHit> orderByComparator) {

		return getPersistence().findAll(start, end, orderByComparator);
	}

	/**
	 * Returns an ordered range of all the search hits.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchHitModelImpl</code>.
	 * </p>
	 *
	 * @param start the lower bound of the range of search hits
	 * @param end the upper bound of the range of search hits (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @param useFinderCache whether to use the finder cache
	 * @return the ordered range of search hits
	 */
	public static List<SearchHit> findAll(
		int start, int end, OrderByComparator<SearchHit> orderByComparator,
		boolean useFinderCache) {

		return getPersistence().findAll(
			start, end, orderByComparator, useFinderCache);
	}

	/**
	 * Removes all the search hits from the database.
	 */
	public static void removeAll() {
		getPersistence().removeAll();
	}

	/**
	 * Returns the number of search hits.
	 *
	 * @return the number of search hits
	 */
	public static int countAll() {
		return getPersistence().countAll();
	}

	public static SearchHitPersistence getPersistence() {
		return _persistence;
	}

	public static void setPersistence(SearchHitPersistence persistence) {
		_persistence = persistence;
	}

	private static volatile SearchHitPersistence _persistence;

}