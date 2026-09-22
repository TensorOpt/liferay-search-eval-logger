/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package ai.tensoropt.sel.service.persistence;

import ai.tensoropt.sel.model.SearchEvent;

import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.OrderByComparator;

import java.io.Serializable;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The persistence utility for the search event service. This utility wraps <code>ai.tensoropt.sel.service.persistence.impl.SearchEventPersistenceImpl</code> and provides direct access to the database for CRUD operations. This utility should only be used by the service layer, as it must operate within a transaction. Never access this utility in a JSP, controller, model, or other front-end class.
 *
 * <p>
 * Caching information and settings can be found in <code>portal.properties</code>
 * </p>
 *
 * @author Brian Wing Shun Chan
 * @see SearchEventPersistence
 * @generated
 */
public class SearchEventUtil {

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
	public static void clearCache(SearchEvent searchEvent) {
		getPersistence().clearCache(searchEvent);
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
	public static Map<Serializable, SearchEvent> fetchByPrimaryKeys(
		Set<Serializable> primaryKeys) {

		return getPersistence().fetchByPrimaryKeys(primaryKeys);
	}

	/**
	 * @see com.liferay.portal.kernel.service.persistence.BasePersistence#findWithDynamicQuery(DynamicQuery)
	 */
	public static List<SearchEvent> findWithDynamicQuery(
		DynamicQuery dynamicQuery) {

		return getPersistence().findWithDynamicQuery(dynamicQuery);
	}

	/**
	 * @see com.liferay.portal.kernel.service.persistence.BasePersistence#findWithDynamicQuery(DynamicQuery, int, int)
	 */
	public static List<SearchEvent> findWithDynamicQuery(
		DynamicQuery dynamicQuery, int start, int end) {

		return getPersistence().findWithDynamicQuery(dynamicQuery, start, end);
	}

	/**
	 * @see com.liferay.portal.kernel.service.persistence.BasePersistence#findWithDynamicQuery(DynamicQuery, int, int, OrderByComparator)
	 */
	public static List<SearchEvent> findWithDynamicQuery(
		DynamicQuery dynamicQuery, int start, int end,
		OrderByComparator<SearchEvent> orderByComparator) {

		return getPersistence().findWithDynamicQuery(
			dynamicQuery, start, end, orderByComparator);
	}

	/**
	 * @see com.liferay.portal.kernel.service.persistence.BasePersistence#update(com.liferay.portal.kernel.model.BaseModel)
	 */
	public static SearchEvent update(SearchEvent searchEvent) {
		return getPersistence().update(searchEvent);
	}

	/**
	 * @see com.liferay.portal.kernel.service.persistence.BasePersistence#update(com.liferay.portal.kernel.model.BaseModel, ServiceContext)
	 */
	public static SearchEvent update(
		SearchEvent searchEvent, ServiceContext serviceContext) {

		return getPersistence().update(searchEvent, serviceContext);
	}

	/**
	 * Returns all the search events where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @return the matching search events
	 */
	public static List<SearchEvent> findByUuid(String uuid) {
		return getPersistence().findByUuid(uuid);
	}

	/**
	 * Returns a range of all the search events where uuid = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param uuid the uuid
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @return the range of matching search events
	 */
	public static List<SearchEvent> findByUuid(
		String uuid, int start, int end) {

		return getPersistence().findByUuid(uuid, start, end);
	}

	/**
	 * Returns an ordered range of all the search events where uuid = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param uuid the uuid
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @return the ordered range of matching search events
	 */
	public static List<SearchEvent> findByUuid(
		String uuid, int start, int end,
		OrderByComparator<SearchEvent> orderByComparator) {

		return getPersistence().findByUuid(uuid, start, end, orderByComparator);
	}

	/**
	 * Returns an ordered range of all the search events where uuid = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param uuid the uuid
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @param useFinderCache whether to use the finder cache
	 * @return the ordered range of matching search events
	 */
	public static List<SearchEvent> findByUuid(
		String uuid, int start, int end,
		OrderByComparator<SearchEvent> orderByComparator,
		boolean useFinderCache) {

		return getPersistence().findByUuid(
			uuid, start, end, orderByComparator, useFinderCache);
	}

	/**
	 * Returns the first search event in the ordered set where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event
	 * @throws NoSuchSearchEventException if a matching search event could not be found
	 */
	public static SearchEvent findByUuid_First(
			String uuid, OrderByComparator<SearchEvent> orderByComparator)
		throws ai.tensoropt.sel.exception.NoSuchSearchEventException {

		return getPersistence().findByUuid_First(uuid, orderByComparator);
	}

	/**
	 * Returns the first search event in the ordered set where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event, or <code>null</code> if a matching search event could not be found
	 */
	public static SearchEvent fetchByUuid_First(
		String uuid, OrderByComparator<SearchEvent> orderByComparator) {

		return getPersistence().fetchByUuid_First(uuid, orderByComparator);
	}

	/**
	 * Returns the last search event in the ordered set where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search event
	 * @throws NoSuchSearchEventException if a matching search event could not be found
	 */
	public static SearchEvent findByUuid_Last(
			String uuid, OrderByComparator<SearchEvent> orderByComparator)
		throws ai.tensoropt.sel.exception.NoSuchSearchEventException {

		return getPersistence().findByUuid_Last(uuid, orderByComparator);
	}

	/**
	 * Returns the last search event in the ordered set where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search event, or <code>null</code> if a matching search event could not be found
	 */
	public static SearchEvent fetchByUuid_Last(
		String uuid, OrderByComparator<SearchEvent> orderByComparator) {

		return getPersistence().fetchByUuid_Last(uuid, orderByComparator);
	}

	/**
	 * Returns the search events before and after the current search event in the ordered set where uuid = &#63;.
	 *
	 * @param searchEventId the primary key of the current search event
	 * @param uuid the uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the previous, current, and next search event
	 * @throws NoSuchSearchEventException if a search event with the primary key could not be found
	 */
	public static SearchEvent[] findByUuid_PrevAndNext(
			long searchEventId, String uuid,
			OrderByComparator<SearchEvent> orderByComparator)
		throws ai.tensoropt.sel.exception.NoSuchSearchEventException {

		return getPersistence().findByUuid_PrevAndNext(
			searchEventId, uuid, orderByComparator);
	}

	/**
	 * Removes all the search events where uuid = &#63; from the database.
	 *
	 * @param uuid the uuid
	 */
	public static void removeByUuid(String uuid) {
		getPersistence().removeByUuid(uuid);
	}

	/**
	 * Returns the number of search events where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @return the number of matching search events
	 */
	public static int countByUuid(String uuid) {
		return getPersistence().countByUuid(uuid);
	}

	/**
	 * Returns all the search events where uuid = &#63; and companyId = &#63;.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @return the matching search events
	 */
	public static List<SearchEvent> findByUuid_C(String uuid, long companyId) {
		return getPersistence().findByUuid_C(uuid, companyId);
	}

	/**
	 * Returns a range of all the search events where uuid = &#63; and companyId = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @return the range of matching search events
	 */
	public static List<SearchEvent> findByUuid_C(
		String uuid, long companyId, int start, int end) {

		return getPersistence().findByUuid_C(uuid, companyId, start, end);
	}

	/**
	 * Returns an ordered range of all the search events where uuid = &#63; and companyId = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @return the ordered range of matching search events
	 */
	public static List<SearchEvent> findByUuid_C(
		String uuid, long companyId, int start, int end,
		OrderByComparator<SearchEvent> orderByComparator) {

		return getPersistence().findByUuid_C(
			uuid, companyId, start, end, orderByComparator);
	}

	/**
	 * Returns an ordered range of all the search events where uuid = &#63; and companyId = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @param useFinderCache whether to use the finder cache
	 * @return the ordered range of matching search events
	 */
	public static List<SearchEvent> findByUuid_C(
		String uuid, long companyId, int start, int end,
		OrderByComparator<SearchEvent> orderByComparator,
		boolean useFinderCache) {

		return getPersistence().findByUuid_C(
			uuid, companyId, start, end, orderByComparator, useFinderCache);
	}

	/**
	 * Returns the first search event in the ordered set where uuid = &#63; and companyId = &#63;.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event
	 * @throws NoSuchSearchEventException if a matching search event could not be found
	 */
	public static SearchEvent findByUuid_C_First(
			String uuid, long companyId,
			OrderByComparator<SearchEvent> orderByComparator)
		throws ai.tensoropt.sel.exception.NoSuchSearchEventException {

		return getPersistence().findByUuid_C_First(
			uuid, companyId, orderByComparator);
	}

	/**
	 * Returns the first search event in the ordered set where uuid = &#63; and companyId = &#63;.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event, or <code>null</code> if a matching search event could not be found
	 */
	public static SearchEvent fetchByUuid_C_First(
		String uuid, long companyId,
		OrderByComparator<SearchEvent> orderByComparator) {

		return getPersistence().fetchByUuid_C_First(
			uuid, companyId, orderByComparator);
	}

	/**
	 * Returns the last search event in the ordered set where uuid = &#63; and companyId = &#63;.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search event
	 * @throws NoSuchSearchEventException if a matching search event could not be found
	 */
	public static SearchEvent findByUuid_C_Last(
			String uuid, long companyId,
			OrderByComparator<SearchEvent> orderByComparator)
		throws ai.tensoropt.sel.exception.NoSuchSearchEventException {

		return getPersistence().findByUuid_C_Last(
			uuid, companyId, orderByComparator);
	}

	/**
	 * Returns the last search event in the ordered set where uuid = &#63; and companyId = &#63;.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search event, or <code>null</code> if a matching search event could not be found
	 */
	public static SearchEvent fetchByUuid_C_Last(
		String uuid, long companyId,
		OrderByComparator<SearchEvent> orderByComparator) {

		return getPersistence().fetchByUuid_C_Last(
			uuid, companyId, orderByComparator);
	}

	/**
	 * Returns the search events before and after the current search event in the ordered set where uuid = &#63; and companyId = &#63;.
	 *
	 * @param searchEventId the primary key of the current search event
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the previous, current, and next search event
	 * @throws NoSuchSearchEventException if a search event with the primary key could not be found
	 */
	public static SearchEvent[] findByUuid_C_PrevAndNext(
			long searchEventId, String uuid, long companyId,
			OrderByComparator<SearchEvent> orderByComparator)
		throws ai.tensoropt.sel.exception.NoSuchSearchEventException {

		return getPersistence().findByUuid_C_PrevAndNext(
			searchEventId, uuid, companyId, orderByComparator);
	}

	/**
	 * Removes all the search events where uuid = &#63; and companyId = &#63; from the database.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 */
	public static void removeByUuid_C(String uuid, long companyId) {
		getPersistence().removeByUuid_C(uuid, companyId);
	}

	/**
	 * Returns the number of search events where uuid = &#63; and companyId = &#63;.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @return the number of matching search events
	 */
	public static int countByUuid_C(String uuid, long companyId) {
		return getPersistence().countByUuid_C(uuid, companyId);
	}

	/**
	 * Returns all the search events where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @return the matching search events
	 */
	public static List<SearchEvent> findByC_LtCreateDate(
		long companyId, Date createDate) {

		return getPersistence().findByC_LtCreateDate(companyId, createDate);
	}

	/**
	 * Returns a range of all the search events where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @return the range of matching search events
	 */
	public static List<SearchEvent> findByC_LtCreateDate(
		long companyId, Date createDate, int start, int end) {

		return getPersistence().findByC_LtCreateDate(
			companyId, createDate, start, end);
	}

	/**
	 * Returns an ordered range of all the search events where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @return the ordered range of matching search events
	 */
	public static List<SearchEvent> findByC_LtCreateDate(
		long companyId, Date createDate, int start, int end,
		OrderByComparator<SearchEvent> orderByComparator) {

		return getPersistence().findByC_LtCreateDate(
			companyId, createDate, start, end, orderByComparator);
	}

	/**
	 * Returns an ordered range of all the search events where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @param useFinderCache whether to use the finder cache
	 * @return the ordered range of matching search events
	 */
	public static List<SearchEvent> findByC_LtCreateDate(
		long companyId, Date createDate, int start, int end,
		OrderByComparator<SearchEvent> orderByComparator,
		boolean useFinderCache) {

		return getPersistence().findByC_LtCreateDate(
			companyId, createDate, start, end, orderByComparator,
			useFinderCache);
	}

	/**
	 * Returns the first search event in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event
	 * @throws NoSuchSearchEventException if a matching search event could not be found
	 */
	public static SearchEvent findByC_LtCreateDate_First(
			long companyId, Date createDate,
			OrderByComparator<SearchEvent> orderByComparator)
		throws ai.tensoropt.sel.exception.NoSuchSearchEventException {

		return getPersistence().findByC_LtCreateDate_First(
			companyId, createDate, orderByComparator);
	}

	/**
	 * Returns the first search event in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event, or <code>null</code> if a matching search event could not be found
	 */
	public static SearchEvent fetchByC_LtCreateDate_First(
		long companyId, Date createDate,
		OrderByComparator<SearchEvent> orderByComparator) {

		return getPersistence().fetchByC_LtCreateDate_First(
			companyId, createDate, orderByComparator);
	}

	/**
	 * Returns the last search event in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search event
	 * @throws NoSuchSearchEventException if a matching search event could not be found
	 */
	public static SearchEvent findByC_LtCreateDate_Last(
			long companyId, Date createDate,
			OrderByComparator<SearchEvent> orderByComparator)
		throws ai.tensoropt.sel.exception.NoSuchSearchEventException {

		return getPersistence().findByC_LtCreateDate_Last(
			companyId, createDate, orderByComparator);
	}

	/**
	 * Returns the last search event in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search event, or <code>null</code> if a matching search event could not be found
	 */
	public static SearchEvent fetchByC_LtCreateDate_Last(
		long companyId, Date createDate,
		OrderByComparator<SearchEvent> orderByComparator) {

		return getPersistence().fetchByC_LtCreateDate_Last(
			companyId, createDate, orderByComparator);
	}

	/**
	 * Returns the search events before and after the current search event in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param searchEventId the primary key of the current search event
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the previous, current, and next search event
	 * @throws NoSuchSearchEventException if a search event with the primary key could not be found
	 */
	public static SearchEvent[] findByC_LtCreateDate_PrevAndNext(
			long searchEventId, long companyId, Date createDate,
			OrderByComparator<SearchEvent> orderByComparator)
		throws ai.tensoropt.sel.exception.NoSuchSearchEventException {

		return getPersistence().findByC_LtCreateDate_PrevAndNext(
			searchEventId, companyId, createDate, orderByComparator);
	}

	/**
	 * Removes all the search events where companyId = &#63; and createDate &lt; &#63; from the database.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 */
	public static void removeByC_LtCreateDate(long companyId, Date createDate) {
		getPersistence().removeByC_LtCreateDate(companyId, createDate);
	}

	/**
	 * Returns the number of search events where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @return the number of matching search events
	 */
	public static int countByC_LtCreateDate(long companyId, Date createDate) {
		return getPersistence().countByC_LtCreateDate(companyId, createDate);
	}

	/**
	 * Caches the search event in the entity cache if it is enabled.
	 *
	 * @param searchEvent the search event
	 */
	public static void cacheResult(SearchEvent searchEvent) {
		getPersistence().cacheResult(searchEvent);
	}

	/**
	 * Caches the search events in the entity cache if it is enabled.
	 *
	 * @param searchEvents the search events
	 */
	public static void cacheResult(List<SearchEvent> searchEvents) {
		getPersistence().cacheResult(searchEvents);
	}

	/**
	 * Creates a new search event with the primary key. Does not add the search event to the database.
	 *
	 * @param searchEventId the primary key for the new search event
	 * @return the new search event
	 */
	public static SearchEvent create(long searchEventId) {
		return getPersistence().create(searchEventId);
	}

	/**
	 * Removes the search event with the primary key from the database. Also notifies the appropriate model listeners.
	 *
	 * @param searchEventId the primary key of the search event
	 * @return the search event that was removed
	 * @throws NoSuchSearchEventException if a search event with the primary key could not be found
	 */
	public static SearchEvent remove(long searchEventId)
		throws ai.tensoropt.sel.exception.NoSuchSearchEventException {

		return getPersistence().remove(searchEventId);
	}

	public static SearchEvent updateImpl(SearchEvent searchEvent) {
		return getPersistence().updateImpl(searchEvent);
	}

	/**
	 * Returns the search event with the primary key or throws a <code>NoSuchSearchEventException</code> if it could not be found.
	 *
	 * @param searchEventId the primary key of the search event
	 * @return the search event
	 * @throws NoSuchSearchEventException if a search event with the primary key could not be found
	 */
	public static SearchEvent findByPrimaryKey(long searchEventId)
		throws ai.tensoropt.sel.exception.NoSuchSearchEventException {

		return getPersistence().findByPrimaryKey(searchEventId);
	}

	/**
	 * Returns the search event with the primary key or returns <code>null</code> if it could not be found.
	 *
	 * @param searchEventId the primary key of the search event
	 * @return the search event, or <code>null</code> if a search event with the primary key could not be found
	 */
	public static SearchEvent fetchByPrimaryKey(long searchEventId) {
		return getPersistence().fetchByPrimaryKey(searchEventId);
	}

	/**
	 * Returns all the search events.
	 *
	 * @return the search events
	 */
	public static List<SearchEvent> findAll() {
		return getPersistence().findAll();
	}

	/**
	 * Returns a range of all the search events.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @return the range of search events
	 */
	public static List<SearchEvent> findAll(int start, int end) {
		return getPersistence().findAll(start, end);
	}

	/**
	 * Returns an ordered range of all the search events.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @return the ordered range of search events
	 */
	public static List<SearchEvent> findAll(
		int start, int end, OrderByComparator<SearchEvent> orderByComparator) {

		return getPersistence().findAll(start, end, orderByComparator);
	}

	/**
	 * Returns an ordered range of all the search events.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @param useFinderCache whether to use the finder cache
	 * @return the ordered range of search events
	 */
	public static List<SearchEvent> findAll(
		int start, int end, OrderByComparator<SearchEvent> orderByComparator,
		boolean useFinderCache) {

		return getPersistence().findAll(
			start, end, orderByComparator, useFinderCache);
	}

	/**
	 * Removes all the search events from the database.
	 */
	public static void removeAll() {
		getPersistence().removeAll();
	}

	/**
	 * Returns the number of search events.
	 *
	 * @return the number of search events
	 */
	public static int countAll() {
		return getPersistence().countAll();
	}

	public static SearchEventPersistence getPersistence() {
		return _persistence;
	}

	public static void setPersistence(SearchEventPersistence persistence) {
		_persistence = persistence;
	}

	private static volatile SearchEventPersistence _persistence;

}