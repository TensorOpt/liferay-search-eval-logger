/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.service.persistence;

import ai.tensoropt.sel.exception.NoSuchSearchEventException;
import ai.tensoropt.sel.model.SearchEvent;

import com.liferay.portal.kernel.service.persistence.BasePersistence;

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
	 * Returns an ordered range of all the search events where uuid = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>ai.tensoropt.sel.model.impl.SearchEventModelImpl</code>.
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
	 * Returns an ordered range of all the search events where uuid = &#63; and companyId = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>ai.tensoropt.sel.model.impl.SearchEventModelImpl</code>.
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
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>ai.tensoropt.sel.model.impl.SearchEventModelImpl</code>.
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
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>ai.tensoropt.sel.model.impl.SearchEventModelImpl</code>.
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
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>ai.tensoropt.sel.model.impl.SearchEventModelImpl</code>.
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
	 * Returns an ordered range of all the search events where companyId = &#63; and requestedFrom = &#63; and totalHits = &#63; and audienceType = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>ai.tensoropt.sel.model.impl.SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param companyId the company ID
	 * @param requestedFrom the requested from
	 * @param totalHits the total hits
	 * @param audienceType the audience type
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @param useFinderCache whether to use the finder cache
	 * @return the ordered range of matching search events
	 */
	public java.util.List<SearchEvent> findByC_R_T_A(
		long companyId, int requestedFrom, long totalHits, String audienceType,
		int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator,
		boolean useFinderCache);

	/**
	 * Returns the first search event in the ordered set where companyId = &#63; and requestedFrom = &#63; and totalHits = &#63; and audienceType = &#63;.
	 *
	 * @param companyId the company ID
	 * @param requestedFrom the requested from
	 * @param totalHits the total hits
	 * @param audienceType the audience type
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event
	 * @throws NoSuchSearchEventException if a matching search event could not be found
	 */
	public SearchEvent findByC_R_T_A_First(
			long companyId, int requestedFrom, long totalHits,
			String audienceType,
			com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
				orderByComparator)
		throws NoSuchSearchEventException;

	/**
	 * Returns the first search event in the ordered set where companyId = &#63; and requestedFrom = &#63; and totalHits = &#63; and audienceType = &#63;.
	 *
	 * @param companyId the company ID
	 * @param requestedFrom the requested from
	 * @param totalHits the total hits
	 * @param audienceType the audience type
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event, or <code>null</code> if a matching search event could not be found
	 */
	public SearchEvent fetchByC_R_T_A_First(
		long companyId, int requestedFrom, long totalHits, String audienceType,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator);

	/**
	 * Removes all the search events where companyId = &#63; and requestedFrom = &#63; and totalHits = &#63; and audienceType = &#63; from the database.
	 *
	 * @param companyId the company ID
	 * @param requestedFrom the requested from
	 * @param totalHits the total hits
	 * @param audienceType the audience type
	 */
	public void removeByC_R_T_A(
		long companyId, int requestedFrom, long totalHits, String audienceType);

	/**
	 * Returns the number of search events where companyId = &#63; and requestedFrom = &#63; and totalHits = &#63; and audienceType = &#63;.
	 *
	 * @param companyId the company ID
	 * @param requestedFrom the requested from
	 * @param totalHits the total hits
	 * @param audienceType the audience type
	 * @return the number of matching search events
	 */
	public int countByC_R_T_A(
		long companyId, int requestedFrom, long totalHits, String audienceType);

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
	 * Returns all the search events where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @return the matching search events
	 */
	public default java.util.List<SearchEvent> findByUuid(String uuid) {
		return findByUuid(
			uuid, com.liferay.portal.kernel.dao.orm.QueryUtil.ALL_POS,
			com.liferay.portal.kernel.dao.orm.QueryUtil.ALL_POS, null, true);
	}

	/**
	 * Returns a range of all the search events where uuid = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>ai.tensoropt.sel.model.impl.SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param uuid the uuid
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @return the range of matching search events
	 */
	public default java.util.List<SearchEvent> findByUuid(
		String uuid, int start, int end) {

		return findByUuid(uuid, start, end, null, true);
	}

	/**
	 * Returns an ordered range of all the search events where uuid = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>ai.tensoropt.sel.model.impl.SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param uuid the uuid
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @return the ordered range of matching search events
	 */
	public default java.util.List<SearchEvent> findByUuid(
		String uuid, int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator) {

		return findByUuid(uuid, start, end, orderByComparator, true);
	}

	/**
	 * Returns all the search events where uuid = &#63; and companyId = &#63;.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @return the matching search events
	 */
	public default java.util.List<SearchEvent> findByUuid_C(
		String uuid, long companyId) {

		return findByUuid_C(
			uuid, companyId,
			com.liferay.portal.kernel.dao.orm.QueryUtil.ALL_POS,
			com.liferay.portal.kernel.dao.orm.QueryUtil.ALL_POS, null, true);
	}

	/**
	 * Returns a range of all the search events where uuid = &#63; and companyId = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>ai.tensoropt.sel.model.impl.SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @return the range of matching search events
	 */
	public default java.util.List<SearchEvent> findByUuid_C(
		String uuid, long companyId, int start, int end) {

		return findByUuid_C(uuid, companyId, start, end, null, true);
	}

	/**
	 * Returns an ordered range of all the search events where uuid = &#63; and companyId = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>ai.tensoropt.sel.model.impl.SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @return the ordered range of matching search events
	 */
	public default java.util.List<SearchEvent> findByUuid_C(
		String uuid, long companyId, int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator) {

		return findByUuid_C(
			uuid, companyId, start, end, orderByComparator, true);
	}

	/**
	 * Returns all the search events where companyId = &#63; and requestedFrom = &#63; and totalHits = &#63; and audienceType = &#63;.
	 *
	 * @param companyId the company ID
	 * @param requestedFrom the requested from
	 * @param totalHits the total hits
	 * @param audienceType the audience type
	 * @return the matching search events
	 */
	public default java.util.List<SearchEvent> findByC_R_T_A(
		long companyId, int requestedFrom, long totalHits,
		String audienceType) {

		return findByC_R_T_A(
			companyId, requestedFrom, totalHits, audienceType,
			com.liferay.portal.kernel.dao.orm.QueryUtil.ALL_POS,
			com.liferay.portal.kernel.dao.orm.QueryUtil.ALL_POS, null, true);
	}

	/**
	 * Returns a range of all the search events where companyId = &#63; and requestedFrom = &#63; and totalHits = &#63; and audienceType = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>ai.tensoropt.sel.model.impl.SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param companyId the company ID
	 * @param requestedFrom the requested from
	 * @param totalHits the total hits
	 * @param audienceType the audience type
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @return the range of matching search events
	 */
	public default java.util.List<SearchEvent> findByC_R_T_A(
		long companyId, int requestedFrom, long totalHits, String audienceType,
		int start, int end) {

		return findByC_R_T_A(
			companyId, requestedFrom, totalHits, audienceType, start, end, null,
			true);
	}

	/**
	 * Returns an ordered range of all the search events where companyId = &#63; and requestedFrom = &#63; and totalHits = &#63; and audienceType = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>ai.tensoropt.sel.model.impl.SearchEventModelImpl</code>.
	 * </p>
	 *
	 * @param companyId the company ID
	 * @param requestedFrom the requested from
	 * @param totalHits the total hits
	 * @param audienceType the audience type
	 * @param start the lower bound of the range of search events
	 * @param end the upper bound of the range of search events (not inclusive)
	 * @param orderByComparator the comparator to order the results by (optionally <code>null</code>)
	 * @return the ordered range of matching search events
	 */
	public default java.util.List<SearchEvent> findByC_R_T_A(
		long companyId, int requestedFrom, long totalHits, String audienceType,
		int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<SearchEvent>
			orderByComparator) {

		return findByC_R_T_A(
			companyId, requestedFrom, totalHits, audienceType, start, end,
			orderByComparator, true);
	}

}
// LIFERAY-SERVICE-BUILDER-HASH:993107822