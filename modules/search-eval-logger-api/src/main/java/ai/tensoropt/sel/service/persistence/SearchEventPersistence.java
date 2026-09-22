/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package ai.tensoropt.sel.service.persistence;

import com.liferay.portal.kernel.service.persistence.BasePersistence;

import ai.tensoropt.sel.exception.NoSuchSearchEventException;
import ai.tensoropt.sel.model.SearchEvent;

import java.util.Date;

import org.osgi.annotation.versioning.ProviderType;

/**
 * The persistence interface for the search event service.
 *
 * <p>
 * Caching information and settings can be found in <code>portal.properties</code>
 * </p>
 *
 * @author Brian Wing Shun Chan
 * @see SearchEventUtil
 * @generated
 */
@ProviderType
public interface SearchEventPersistence extends BasePersistence<SearchEvent> {

	/*
	 * NOTE FOR DEVELOPERS:
	 *
	 * Never modify or reference this interface directly. Always use {@link SearchEventUtil} to access the search event persistence. Modify <code>service.xml</code> and rerun ServiceBuilder to regenerate this interface.
	 */

	/**
	 * Returns all the search events where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @return the matching search events
	 */
	public java.util.List<SearchEvent> findByUuid(String uuid);

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
	public java.util.List<SearchEvent> findByUuid(
		String uuid, int start, int end);

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
	public java.util.List<SearchEvent> findByUuid(
		String uuid, int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator);

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
	public java.util.List<SearchEvent> findByUuid(
		String uuid, int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator,
		boolean useFinderCache);

	/**
	 * Returns the first search event in the ordered set where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event
	 * @throws NoSuchSearchEventException if a matching search event could not be found
	 */
	public SearchEvent findByUuid_First(
			String uuid,
			com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
				orderByComparator)
		throws NoSuchSearchEventException;

	/**
	 * Returns the first search event in the ordered set where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event, or <code>null</code> if a matching search event could not be found
	 */
	public SearchEvent fetchByUuid_First(
		String uuid,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator);

	/**
	 * Returns the last search event in the ordered set where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search event
	 * @throws NoSuchSearchEventException if a matching search event could not be found
	 */
	public SearchEvent findByUuid_Last(
			String uuid,
			com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
				orderByComparator)
		throws NoSuchSearchEventException;

	/**
	 * Returns the last search event in the ordered set where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search event, or <code>null</code> if a matching search event could not be found
	 */
	public SearchEvent fetchByUuid_Last(
		String uuid,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator);

	/**
	 * Returns the search events before and after the current search event in the ordered set where uuid = &#63;.
	 *
	 * @param searchEventId the primary key of the current search event
	 * @param uuid the uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the previous, current, and next search event
	 * @throws NoSuchSearchEventException if a search event with the primary key could not be found
	 */
	public SearchEvent[] findByUuid_PrevAndNext(
			long searchEventId, String uuid,
			com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
				orderByComparator)
		throws NoSuchSearchEventException;

	/**
	 * Removes all the search events where uuid = &#63; from the database.
	 *
	 * @param uuid the uuid
	 */
	public void removeByUuid(String uuid);

	/**
	 * Returns the number of search events where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @return the number of matching search events
	 */
	public int countByUuid(String uuid);

	/**
	 * Returns all the search events where uuid = &#63; and companyId = &#63;.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @return the matching search events
	 */
	public java.util.List<SearchEvent> findByUuid_C(
		String uuid, long companyId);

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
	public java.util.List<SearchEvent> findByUuid_C(
		String uuid, long companyId, int start, int end);

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
	public java.util.List<SearchEvent> findByUuid_C(
		String uuid, long companyId, int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator);

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
	public java.util.List<SearchEvent> findByUuid_C(
		String uuid, long companyId, int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator,
		boolean useFinderCache);

	/**
	 * Returns the first search event in the ordered set where uuid = &#63; and companyId = &#63;.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event
	 * @throws NoSuchSearchEventException if a matching search event could not be found
	 */
	public SearchEvent findByUuid_C_First(
			String uuid, long companyId,
			com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
				orderByComparator)
		throws NoSuchSearchEventException;

	/**
	 * Returns the first search event in the ordered set where uuid = &#63; and companyId = &#63;.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event, or <code>null</code> if a matching search event could not be found
	 */
	public SearchEvent fetchByUuid_C_First(
		String uuid, long companyId,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator);

	/**
	 * Returns the last search event in the ordered set where uuid = &#63; and companyId = &#63;.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search event
	 * @throws NoSuchSearchEventException if a matching search event could not be found
	 */
	public SearchEvent findByUuid_C_Last(
			String uuid, long companyId,
			com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
				orderByComparator)
		throws NoSuchSearchEventException;

	/**
	 * Returns the last search event in the ordered set where uuid = &#63; and companyId = &#63;.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search event, or <code>null</code> if a matching search event could not be found
	 */
	public SearchEvent fetchByUuid_C_Last(
		String uuid, long companyId,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator);

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
	public SearchEvent[] findByUuid_C_PrevAndNext(
			long searchEventId, String uuid, long companyId,
			com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
				orderByComparator)
		throws NoSuchSearchEventException;

	/**
	 * Removes all the search events where uuid = &#63; and companyId = &#63; from the database.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 */
	public void removeByUuid_C(String uuid, long companyId);

	/**
	 * Returns the number of search events where uuid = &#63; and companyId = &#63;.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @return the number of matching search events
	 */
	public int countByUuid_C(String uuid, long companyId);

	/**
	 * Returns all the search events where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @return the matching search events
	 */
	public java.util.List<SearchEvent> findByC_LtCreateDate(
		long companyId, Date createDate);

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
	public java.util.List<SearchEvent> findByC_LtCreateDate(
		long companyId, Date createDate, int start, int end);

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
	public java.util.List<SearchEvent> findByC_LtCreateDate(
		long companyId, Date createDate, int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator);

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
	public java.util.List<SearchEvent> findByC_LtCreateDate(
		long companyId, Date createDate, int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator,
		boolean useFinderCache);

	/**
	 * Returns the first search event in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event
	 * @throws NoSuchSearchEventException if a matching search event could not be found
	 */
	public SearchEvent findByC_LtCreateDate_First(
			long companyId, Date createDate,
			com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
				orderByComparator)
		throws NoSuchSearchEventException;

	/**
	 * Returns the first search event in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event, or <code>null</code> if a matching search event could not be found
	 */
	public SearchEvent fetchByC_LtCreateDate_First(
		long companyId, Date createDate,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator);

	/**
	 * Returns the last search event in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search event
	 * @throws NoSuchSearchEventException if a matching search event could not be found
	 */
	public SearchEvent findByC_LtCreateDate_Last(
			long companyId, Date createDate,
			com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
				orderByComparator)
		throws NoSuchSearchEventException;

	/**
	 * Returns the last search event in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search event, or <code>null</code> if a matching search event could not be found
	 */
	public SearchEvent fetchByC_LtCreateDate_Last(
		long companyId, Date createDate,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator);

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
	public SearchEvent[] findByC_LtCreateDate_PrevAndNext(
			long searchEventId, long companyId, Date createDate,
			com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
				orderByComparator)
		throws NoSuchSearchEventException;

	/**
	 * Removes all the search events where companyId = &#63; and createDate &lt; &#63; from the database.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 */
	public void removeByC_LtCreateDate(long companyId, Date createDate);

	/**
	 * Returns the number of search events where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @return the number of matching search events
	 */
	public int countByC_LtCreateDate(long companyId, Date createDate);

	/**
	 * Caches the search event in the entity cache if it is enabled.
	 *
	 * @param searchEvent the search event
	 */
	public void cacheResult(SearchEvent searchEvent);

	/**
	 * Caches the search events in the entity cache if it is enabled.
	 *
	 * @param searchEvents the search events
	 */
	public void cacheResult(java.util.List<SearchEvent> searchEvents);

	/**
	 * Creates a new search event with the primary key. Does not add the search event to the database.
	 *
	 * @param searchEventId the primary key for the new search event
	 * @return the new search event
	 */
	public SearchEvent create(long searchEventId);

	/**
	 * Removes the search event with the primary key from the database. Also notifies the appropriate model listeners.
	 *
	 * @param searchEventId the primary key of the search event
	 * @return the search event that was removed
	 * @throws NoSuchSearchEventException if a search event with the primary key could not be found
	 */
	public SearchEvent remove(long searchEventId)
		throws NoSuchSearchEventException;

	public SearchEvent updateImpl(SearchEvent searchEvent);

	/**
	 * Returns the search event with the primary key or throws a <code>NoSuchSearchEventException</code> if it could not be found.
	 *
	 * @param searchEventId the primary key of the search event
	 * @return the search event
	 * @throws NoSuchSearchEventException if a search event with the primary key could not be found
	 */
	public SearchEvent findByPrimaryKey(long searchEventId)
		throws NoSuchSearchEventException;

	/**
	 * Returns the search event with the primary key or returns <code>null</code> if it could not be found.
	 *
	 * @param searchEventId the primary key of the search event
	 * @return the search event, or <code>null</code> if a search event with the primary key could not be found
	 */
	public SearchEvent fetchByPrimaryKey(long searchEventId);

	/**
	 * Returns all the search events.
	 *
	 * @return the search events
	 */
	public java.util.List<SearchEvent> findAll();

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
	public java.util.List<SearchEvent> findAll(int start, int end);

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
	public java.util.List<SearchEvent> findAll(
		int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator);

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
	public java.util.List<SearchEvent> findAll(
		int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator,
		boolean useFinderCache);

	/**
	 * Removes all the search events from the database.
	 */
	public void removeAll();

	/**
	 * Returns the number of search events.
	 *
	 * @return the number of search events
	 */
	public int countAll();

}