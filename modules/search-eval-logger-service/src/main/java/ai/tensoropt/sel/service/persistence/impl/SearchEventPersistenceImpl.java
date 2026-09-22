/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package ai.tensoropt.sel.service.persistence.impl;

import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.configuration.Configuration;
import com.liferay.portal.kernel.dao.orm.EntityCache;
import com.liferay.portal.kernel.dao.orm.FinderCache;
import com.liferay.portal.kernel.dao.orm.FinderPath;
import com.liferay.portal.kernel.dao.orm.Query;
import com.liferay.portal.kernel.dao.orm.QueryPos;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.dao.orm.Session;
import com.liferay.portal.kernel.dao.orm.SessionFactory;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.security.auth.CompanyThreadLocal;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.service.persistence.impl.BasePersistenceImpl;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.OrderByComparator;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.ProxyUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.uuid.PortalUUIDUtil;

import ai.tensoropt.sel.exception.NoSuchSearchEventException;
import ai.tensoropt.sel.model.SearchEvent;
import ai.tensoropt.sel.model.SearchEventTable;
import ai.tensoropt.sel.model.impl.SearchEventImpl;
import ai.tensoropt.sel.model.impl.SearchEventModelImpl;
import ai.tensoropt.sel.service.persistence.SearchEventPersistence;
import ai.tensoropt.sel.service.persistence.SearchEventUtil;
import ai.tensoropt.sel.service.persistence.impl.constants.SELPersistenceConstants;

import java.io.Serializable;

import java.lang.reflect.InvocationHandler;

import java.sql.Timestamp;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.sql.DataSource;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * The persistence implementation for the search event service.
 *
 * <p>
 * Caching information and settings can be found in <code>portal.properties</code>
 * </p>
 *
 * @author Brian Wing Shun Chan
 * @generated
 */
@Component(service = SearchEventPersistence.class)
public class SearchEventPersistenceImpl
	extends BasePersistenceImpl<SearchEvent> implements SearchEventPersistence {

	/*
	 * NOTE FOR DEVELOPERS:
	 *
	 * Never modify or reference this class directly. Always use <code>SearchEventUtil</code> to access the search event persistence. Modify <code>service.xml</code> and rerun ServiceBuilder to regenerate this class.
	 */
	public static final String FINDER_CLASS_NAME_ENTITY =
		SearchEventImpl.class.getName();

	public static final String FINDER_CLASS_NAME_LIST_WITH_PAGINATION =
		FINDER_CLASS_NAME_ENTITY + ".List1";

	public static final String FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION =
		FINDER_CLASS_NAME_ENTITY + ".List2";

	private FinderPath _finderPathWithPaginationFindAll;
	private FinderPath _finderPathWithoutPaginationFindAll;
	private FinderPath _finderPathCountAll;
	private FinderPath _finderPathWithPaginationFindByUuid;
	private FinderPath _finderPathWithoutPaginationFindByUuid;
	private FinderPath _finderPathCountByUuid;

	/**
	 * Returns all the search events where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @return the matching search events
	 */
	@Override
	public List<SearchEvent> findByUuid(String uuid) {
		return findByUuid(uuid, QueryUtil.ALL_POS, QueryUtil.ALL_POS, null);
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
	@Override
	public List<SearchEvent> findByUuid(String uuid, int start, int end) {
		return findByUuid(uuid, start, end, null);
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
	@Override
	public List<SearchEvent> findByUuid(
		String uuid, int start, int end,
		OrderByComparator<SearchEvent> orderByComparator) {

		return findByUuid(uuid, start, end, orderByComparator, true);
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
	@Override
	public List<SearchEvent> findByUuid(
		String uuid, int start, int end,
		OrderByComparator<SearchEvent> orderByComparator,
		boolean useFinderCache) {

		uuid = Objects.toString(uuid, "");

		FinderPath finderPath = null;
		Object[] finderArgs = null;

		if ((start == QueryUtil.ALL_POS) && (end == QueryUtil.ALL_POS) &&
			(orderByComparator == null)) {

			if (useFinderCache) {
				finderPath = _finderPathWithoutPaginationFindByUuid;
				finderArgs = new Object[] {uuid};
			}
		}
		else if (useFinderCache) {
			finderPath = _finderPathWithPaginationFindByUuid;
			finderArgs = new Object[] {uuid, start, end, orderByComparator};
		}

		List<SearchEvent> list = null;

		if (useFinderCache) {
			list = (List<SearchEvent>)dummyFinderCache.getResult(
				finderPath, finderArgs, this);

			if ((list != null) && !list.isEmpty()) {
				for (SearchEvent searchEvent : list) {
					if (!uuid.equals(searchEvent.getUuid())) {
						list = null;

						break;
					}
				}
			}
		}

		if (list == null) {
			StringBundler sb = null;

			if (orderByComparator != null) {
				sb = new StringBundler(
					3 + (orderByComparator.getOrderByFields().length * 2));
			}
			else {
				sb = new StringBundler(3);
			}

			sb.append(_SQL_SELECT_SEARCHEVENT_WHERE);

			boolean bindUuid = false;

			if (uuid.isEmpty()) {
				sb.append(_FINDER_COLUMN_UUID_UUID_3);
			}
			else {
				bindUuid = true;

				sb.append(_FINDER_COLUMN_UUID_UUID_2);
			}

			if (orderByComparator != null) {
				appendOrderByComparator(
					sb, _ORDER_BY_ENTITY_ALIAS, orderByComparator);
			}
			else {
				sb.append(SearchEventModelImpl.ORDER_BY_JPQL);
			}

			String sql = sb.toString();

			Session session = null;

			try {
				session = openSession();

				Query query = session.createQuery(sql);

				QueryPos queryPos = QueryPos.getInstance(query);

				if (bindUuid) {
					queryPos.add(uuid);
				}

				list = (List<SearchEvent>)QueryUtil.list(
					query, getDialect(), start, end);

				cacheResult(list);

				if (useFinderCache) {
					dummyFinderCache.putResult(finderPath, finderArgs, list);
				}
			}
			catch (Exception exception) {
				throw processException(exception);
			}
			finally {
				closeSession(session);
			}
		}

		return list;
	}

	/**
	 * Returns the first search event in the ordered set where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event
	 * @throws NoSuchSearchEventException if a matching search event could not be found
	 */
	@Override
	public SearchEvent findByUuid_First(
			String uuid, OrderByComparator<SearchEvent> orderByComparator)
		throws NoSuchSearchEventException {

		SearchEvent searchEvent = fetchByUuid_First(uuid, orderByComparator);

		if (searchEvent != null) {
			return searchEvent;
		}

		StringBundler sb = new StringBundler(4);

		sb.append(_NO_SUCH_ENTITY_WITH_KEY);

		sb.append("uuid=");
		sb.append(uuid);

		sb.append("}");

		throw new NoSuchSearchEventException(sb.toString());
	}

	/**
	 * Returns the first search event in the ordered set where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event, or <code>null</code> if a matching search event could not be found
	 */
	@Override
	public SearchEvent fetchByUuid_First(
		String uuid, OrderByComparator<SearchEvent> orderByComparator) {

		List<SearchEvent> list = findByUuid(uuid, 0, 1, orderByComparator);

		if (!list.isEmpty()) {
			return list.get(0);
		}

		return null;
	}

	/**
	 * Returns the last search event in the ordered set where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search event
	 * @throws NoSuchSearchEventException if a matching search event could not be found
	 */
	@Override
	public SearchEvent findByUuid_Last(
			String uuid, OrderByComparator<SearchEvent> orderByComparator)
		throws NoSuchSearchEventException {

		SearchEvent searchEvent = fetchByUuid_Last(uuid, orderByComparator);

		if (searchEvent != null) {
			return searchEvent;
		}

		StringBundler sb = new StringBundler(4);

		sb.append(_NO_SUCH_ENTITY_WITH_KEY);

		sb.append("uuid=");
		sb.append(uuid);

		sb.append("}");

		throw new NoSuchSearchEventException(sb.toString());
	}

	/**
	 * Returns the last search event in the ordered set where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search event, or <code>null</code> if a matching search event could not be found
	 */
	@Override
	public SearchEvent fetchByUuid_Last(
		String uuid, OrderByComparator<SearchEvent> orderByComparator) {

		int count = countByUuid(uuid);

		if (count == 0) {
			return null;
		}

		List<SearchEvent> list = findByUuid(
			uuid, count - 1, count, orderByComparator);

		if (!list.isEmpty()) {
			return list.get(0);
		}

		return null;
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
	@Override
	public SearchEvent[] findByUuid_PrevAndNext(
			long searchEventId, String uuid,
			OrderByComparator<SearchEvent> orderByComparator)
		throws NoSuchSearchEventException {

		uuid = Objects.toString(uuid, "");

		SearchEvent searchEvent = findByPrimaryKey(searchEventId);

		Session session = null;

		try {
			session = openSession();

			SearchEvent[] array = new SearchEventImpl[3];

			array[0] = getByUuid_PrevAndNext(
				session, searchEvent, uuid, orderByComparator, true);

			array[1] = searchEvent;

			array[2] = getByUuid_PrevAndNext(
				session, searchEvent, uuid, orderByComparator, false);

			return array;
		}
		catch (Exception exception) {
			throw processException(exception);
		}
		finally {
			closeSession(session);
		}
	}

	protected SearchEvent getByUuid_PrevAndNext(
		Session session, SearchEvent searchEvent, String uuid,
		OrderByComparator<SearchEvent> orderByComparator, boolean previous) {

		StringBundler sb = null;

		if (orderByComparator != null) {
			sb = new StringBundler(
				4 + (orderByComparator.getOrderByConditionFields().length * 3) +
					(orderByComparator.getOrderByFields().length * 3));
		}
		else {
			sb = new StringBundler(3);
		}

		sb.append(_SQL_SELECT_SEARCHEVENT_WHERE);

		boolean bindUuid = false;

		if (uuid.isEmpty()) {
			sb.append(_FINDER_COLUMN_UUID_UUID_3);
		}
		else {
			bindUuid = true;

			sb.append(_FINDER_COLUMN_UUID_UUID_2);
		}

		if (orderByComparator != null) {
			String[] orderByConditionFields =
				orderByComparator.getOrderByConditionFields();

			if (orderByConditionFields.length > 0) {
				sb.append(WHERE_AND);
			}

			for (int i = 0; i < orderByConditionFields.length; i++) {
				sb.append(_ORDER_BY_ENTITY_ALIAS);
				sb.append(orderByConditionFields[i]);

				if ((i + 1) < orderByConditionFields.length) {
					if (orderByComparator.isAscending() ^ previous) {
						sb.append(WHERE_GREATER_THAN_HAS_NEXT);
					}
					else {
						sb.append(WHERE_LESSER_THAN_HAS_NEXT);
					}
				}
				else {
					if (orderByComparator.isAscending() ^ previous) {
						sb.append(WHERE_GREATER_THAN);
					}
					else {
						sb.append(WHERE_LESSER_THAN);
					}
				}
			}

			sb.append(ORDER_BY_CLAUSE);

			String[] orderByFields = orderByComparator.getOrderByFields();

			for (int i = 0; i < orderByFields.length; i++) {
				sb.append(_ORDER_BY_ENTITY_ALIAS);
				sb.append(orderByFields[i]);

				if ((i + 1) < orderByFields.length) {
					if (orderByComparator.isAscending() ^ previous) {
						sb.append(ORDER_BY_ASC_HAS_NEXT);
					}
					else {
						sb.append(ORDER_BY_DESC_HAS_NEXT);
					}
				}
				else {
					if (orderByComparator.isAscending() ^ previous) {
						sb.append(ORDER_BY_ASC);
					}
					else {
						sb.append(ORDER_BY_DESC);
					}
				}
			}
		}
		else {
			sb.append(SearchEventModelImpl.ORDER_BY_JPQL);
		}

		String sql = sb.toString();

		Query query = session.createQuery(sql);

		query.setFirstResult(0);
		query.setMaxResults(2);

		QueryPos queryPos = QueryPos.getInstance(query);

		if (bindUuid) {
			queryPos.add(uuid);
		}

		if (orderByComparator != null) {
			for (Object orderByConditionValue :
					orderByComparator.getOrderByConditionValues(searchEvent)) {

				queryPos.add(orderByConditionValue);
			}
		}

		List<SearchEvent> list = query.list();

		if (list.size() == 2) {
			return list.get(1);
		}
		else {
			return null;
		}
	}

	/**
	 * Removes all the search events where uuid = &#63; from the database.
	 *
	 * @param uuid the uuid
	 */
	@Override
	public void removeByUuid(String uuid) {
		for (SearchEvent searchEvent :
				findByUuid(uuid, QueryUtil.ALL_POS, QueryUtil.ALL_POS, null)) {

			remove(searchEvent);
		}
	}

	/**
	 * Returns the number of search events where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @return the number of matching search events
	 */
	@Override
	public int countByUuid(String uuid) {
		uuid = Objects.toString(uuid, "");

		FinderPath finderPath = _finderPathCountByUuid;

		Object[] finderArgs = new Object[] {uuid};

		Long count = (Long)dummyFinderCache.getResult(
			finderPath, finderArgs, this);

		if (count == null) {
			StringBundler sb = new StringBundler(2);

			sb.append(_SQL_COUNT_SEARCHEVENT_WHERE);

			boolean bindUuid = false;

			if (uuid.isEmpty()) {
				sb.append(_FINDER_COLUMN_UUID_UUID_3);
			}
			else {
				bindUuid = true;

				sb.append(_FINDER_COLUMN_UUID_UUID_2);
			}

			String sql = sb.toString();

			Session session = null;

			try {
				session = openSession();

				Query query = session.createQuery(sql);

				QueryPos queryPos = QueryPos.getInstance(query);

				if (bindUuid) {
					queryPos.add(uuid);
				}

				count = (Long)query.uniqueResult();

				dummyFinderCache.putResult(finderPath, finderArgs, count);
			}
			catch (Exception exception) {
				throw processException(exception);
			}
			finally {
				closeSession(session);
			}
		}

		return count.intValue();
	}

	private static final String _FINDER_COLUMN_UUID_UUID_2 =
		"searchEvent.uuid = ?";

	private static final String _FINDER_COLUMN_UUID_UUID_3 =
		"(searchEvent.uuid IS NULL OR searchEvent.uuid = '')";

	private FinderPath _finderPathWithPaginationFindByUuid_C;
	private FinderPath _finderPathWithoutPaginationFindByUuid_C;
	private FinderPath _finderPathCountByUuid_C;

	/**
	 * Returns all the search events where uuid = &#63; and companyId = &#63;.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @return the matching search events
	 */
	@Override
	public List<SearchEvent> findByUuid_C(String uuid, long companyId) {
		return findByUuid_C(
			uuid, companyId, QueryUtil.ALL_POS, QueryUtil.ALL_POS, null);
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
	@Override
	public List<SearchEvent> findByUuid_C(
		String uuid, long companyId, int start, int end) {

		return findByUuid_C(uuid, companyId, start, end, null);
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
	@Override
	public List<SearchEvent> findByUuid_C(
		String uuid, long companyId, int start, int end,
		OrderByComparator<SearchEvent> orderByComparator) {

		return findByUuid_C(
			uuid, companyId, start, end, orderByComparator, true);
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
	@Override
	public List<SearchEvent> findByUuid_C(
		String uuid, long companyId, int start, int end,
		OrderByComparator<SearchEvent> orderByComparator,
		boolean useFinderCache) {

		uuid = Objects.toString(uuid, "");

		FinderPath finderPath = null;
		Object[] finderArgs = null;

		if ((start == QueryUtil.ALL_POS) && (end == QueryUtil.ALL_POS) &&
			(orderByComparator == null)) {

			if (useFinderCache) {
				finderPath = _finderPathWithoutPaginationFindByUuid_C;
				finderArgs = new Object[] {uuid, companyId};
			}
		}
		else if (useFinderCache) {
			finderPath = _finderPathWithPaginationFindByUuid_C;
			finderArgs = new Object[] {
				uuid, companyId, start, end, orderByComparator
			};
		}

		List<SearchEvent> list = null;

		if (useFinderCache) {
			list = (List<SearchEvent>)dummyFinderCache.getResult(
				finderPath, finderArgs, this);

			if ((list != null) && !list.isEmpty()) {
				for (SearchEvent searchEvent : list) {
					if (!uuid.equals(searchEvent.getUuid()) ||
						(companyId != searchEvent.getCompanyId())) {

						list = null;

						break;
					}
				}
			}
		}

		if (list == null) {
			StringBundler sb = null;

			if (orderByComparator != null) {
				sb = new StringBundler(
					4 + (orderByComparator.getOrderByFields().length * 2));
			}
			else {
				sb = new StringBundler(4);
			}

			sb.append(_SQL_SELECT_SEARCHEVENT_WHERE);

			boolean bindUuid = false;

			if (uuid.isEmpty()) {
				sb.append(_FINDER_COLUMN_UUID_C_UUID_3);
			}
			else {
				bindUuid = true;

				sb.append(_FINDER_COLUMN_UUID_C_UUID_2);
			}

			sb.append(_FINDER_COLUMN_UUID_C_COMPANYID_2);

			if (orderByComparator != null) {
				appendOrderByComparator(
					sb, _ORDER_BY_ENTITY_ALIAS, orderByComparator);
			}
			else {
				sb.append(SearchEventModelImpl.ORDER_BY_JPQL);
			}

			String sql = sb.toString();

			Session session = null;

			try {
				session = openSession();

				Query query = session.createQuery(sql);

				QueryPos queryPos = QueryPos.getInstance(query);

				if (bindUuid) {
					queryPos.add(uuid);
				}

				queryPos.add(companyId);

				list = (List<SearchEvent>)QueryUtil.list(
					query, getDialect(), start, end);

				cacheResult(list);

				if (useFinderCache) {
					dummyFinderCache.putResult(finderPath, finderArgs, list);
				}
			}
			catch (Exception exception) {
				throw processException(exception);
			}
			finally {
				closeSession(session);
			}
		}

		return list;
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
	@Override
	public SearchEvent findByUuid_C_First(
			String uuid, long companyId,
			OrderByComparator<SearchEvent> orderByComparator)
		throws NoSuchSearchEventException {

		SearchEvent searchEvent = fetchByUuid_C_First(
			uuid, companyId, orderByComparator);

		if (searchEvent != null) {
			return searchEvent;
		}

		StringBundler sb = new StringBundler(6);

		sb.append(_NO_SUCH_ENTITY_WITH_KEY);

		sb.append("uuid=");
		sb.append(uuid);

		sb.append(", companyId=");
		sb.append(companyId);

		sb.append("}");

		throw new NoSuchSearchEventException(sb.toString());
	}

	/**
	 * Returns the first search event in the ordered set where uuid = &#63; and companyId = &#63;.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event, or <code>null</code> if a matching search event could not be found
	 */
	@Override
	public SearchEvent fetchByUuid_C_First(
		String uuid, long companyId,
		OrderByComparator<SearchEvent> orderByComparator) {

		List<SearchEvent> list = findByUuid_C(
			uuid, companyId, 0, 1, orderByComparator);

		if (!list.isEmpty()) {
			return list.get(0);
		}

		return null;
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
	@Override
	public SearchEvent findByUuid_C_Last(
			String uuid, long companyId,
			OrderByComparator<SearchEvent> orderByComparator)
		throws NoSuchSearchEventException {

		SearchEvent searchEvent = fetchByUuid_C_Last(
			uuid, companyId, orderByComparator);

		if (searchEvent != null) {
			return searchEvent;
		}

		StringBundler sb = new StringBundler(6);

		sb.append(_NO_SUCH_ENTITY_WITH_KEY);

		sb.append("uuid=");
		sb.append(uuid);

		sb.append(", companyId=");
		sb.append(companyId);

		sb.append("}");

		throw new NoSuchSearchEventException(sb.toString());
	}

	/**
	 * Returns the last search event in the ordered set where uuid = &#63; and companyId = &#63;.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search event, or <code>null</code> if a matching search event could not be found
	 */
	@Override
	public SearchEvent fetchByUuid_C_Last(
		String uuid, long companyId,
		OrderByComparator<SearchEvent> orderByComparator) {

		int count = countByUuid_C(uuid, companyId);

		if (count == 0) {
			return null;
		}

		List<SearchEvent> list = findByUuid_C(
			uuid, companyId, count - 1, count, orderByComparator);

		if (!list.isEmpty()) {
			return list.get(0);
		}

		return null;
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
	@Override
	public SearchEvent[] findByUuid_C_PrevAndNext(
			long searchEventId, String uuid, long companyId,
			OrderByComparator<SearchEvent> orderByComparator)
		throws NoSuchSearchEventException {

		uuid = Objects.toString(uuid, "");

		SearchEvent searchEvent = findByPrimaryKey(searchEventId);

		Session session = null;

		try {
			session = openSession();

			SearchEvent[] array = new SearchEventImpl[3];

			array[0] = getByUuid_C_PrevAndNext(
				session, searchEvent, uuid, companyId, orderByComparator, true);

			array[1] = searchEvent;

			array[2] = getByUuid_C_PrevAndNext(
				session, searchEvent, uuid, companyId, orderByComparator,
				false);

			return array;
		}
		catch (Exception exception) {
			throw processException(exception);
		}
		finally {
			closeSession(session);
		}
	}

	protected SearchEvent getByUuid_C_PrevAndNext(
		Session session, SearchEvent searchEvent, String uuid, long companyId,
		OrderByComparator<SearchEvent> orderByComparator, boolean previous) {

		StringBundler sb = null;

		if (orderByComparator != null) {
			sb = new StringBundler(
				5 + (orderByComparator.getOrderByConditionFields().length * 3) +
					(orderByComparator.getOrderByFields().length * 3));
		}
		else {
			sb = new StringBundler(4);
		}

		sb.append(_SQL_SELECT_SEARCHEVENT_WHERE);

		boolean bindUuid = false;

		if (uuid.isEmpty()) {
			sb.append(_FINDER_COLUMN_UUID_C_UUID_3);
		}
		else {
			bindUuid = true;

			sb.append(_FINDER_COLUMN_UUID_C_UUID_2);
		}

		sb.append(_FINDER_COLUMN_UUID_C_COMPANYID_2);

		if (orderByComparator != null) {
			String[] orderByConditionFields =
				orderByComparator.getOrderByConditionFields();

			if (orderByConditionFields.length > 0) {
				sb.append(WHERE_AND);
			}

			for (int i = 0; i < orderByConditionFields.length; i++) {
				sb.append(_ORDER_BY_ENTITY_ALIAS);
				sb.append(orderByConditionFields[i]);

				if ((i + 1) < orderByConditionFields.length) {
					if (orderByComparator.isAscending() ^ previous) {
						sb.append(WHERE_GREATER_THAN_HAS_NEXT);
					}
					else {
						sb.append(WHERE_LESSER_THAN_HAS_NEXT);
					}
				}
				else {
					if (orderByComparator.isAscending() ^ previous) {
						sb.append(WHERE_GREATER_THAN);
					}
					else {
						sb.append(WHERE_LESSER_THAN);
					}
				}
			}

			sb.append(ORDER_BY_CLAUSE);

			String[] orderByFields = orderByComparator.getOrderByFields();

			for (int i = 0; i < orderByFields.length; i++) {
				sb.append(_ORDER_BY_ENTITY_ALIAS);
				sb.append(orderByFields[i]);

				if ((i + 1) < orderByFields.length) {
					if (orderByComparator.isAscending() ^ previous) {
						sb.append(ORDER_BY_ASC_HAS_NEXT);
					}
					else {
						sb.append(ORDER_BY_DESC_HAS_NEXT);
					}
				}
				else {
					if (orderByComparator.isAscending() ^ previous) {
						sb.append(ORDER_BY_ASC);
					}
					else {
						sb.append(ORDER_BY_DESC);
					}
				}
			}
		}
		else {
			sb.append(SearchEventModelImpl.ORDER_BY_JPQL);
		}

		String sql = sb.toString();

		Query query = session.createQuery(sql);

		query.setFirstResult(0);
		query.setMaxResults(2);

		QueryPos queryPos = QueryPos.getInstance(query);

		if (bindUuid) {
			queryPos.add(uuid);
		}

		queryPos.add(companyId);

		if (orderByComparator != null) {
			for (Object orderByConditionValue :
					orderByComparator.getOrderByConditionValues(searchEvent)) {

				queryPos.add(orderByConditionValue);
			}
		}

		List<SearchEvent> list = query.list();

		if (list.size() == 2) {
			return list.get(1);
		}
		else {
			return null;
		}
	}

	/**
	 * Removes all the search events where uuid = &#63; and companyId = &#63; from the database.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 */
	@Override
	public void removeByUuid_C(String uuid, long companyId) {
		for (SearchEvent searchEvent :
				findByUuid_C(
					uuid, companyId, QueryUtil.ALL_POS, QueryUtil.ALL_POS,
					null)) {

			remove(searchEvent);
		}
	}

	/**
	 * Returns the number of search events where uuid = &#63; and companyId = &#63;.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 * @return the number of matching search events
	 */
	@Override
	public int countByUuid_C(String uuid, long companyId) {
		uuid = Objects.toString(uuid, "");

		FinderPath finderPath = _finderPathCountByUuid_C;

		Object[] finderArgs = new Object[] {uuid, companyId};

		Long count = (Long)dummyFinderCache.getResult(
			finderPath, finderArgs, this);

		if (count == null) {
			StringBundler sb = new StringBundler(3);

			sb.append(_SQL_COUNT_SEARCHEVENT_WHERE);

			boolean bindUuid = false;

			if (uuid.isEmpty()) {
				sb.append(_FINDER_COLUMN_UUID_C_UUID_3);
			}
			else {
				bindUuid = true;

				sb.append(_FINDER_COLUMN_UUID_C_UUID_2);
			}

			sb.append(_FINDER_COLUMN_UUID_C_COMPANYID_2);

			String sql = sb.toString();

			Session session = null;

			try {
				session = openSession();

				Query query = session.createQuery(sql);

				QueryPos queryPos = QueryPos.getInstance(query);

				if (bindUuid) {
					queryPos.add(uuid);
				}

				queryPos.add(companyId);

				count = (Long)query.uniqueResult();

				dummyFinderCache.putResult(finderPath, finderArgs, count);
			}
			catch (Exception exception) {
				throw processException(exception);
			}
			finally {
				closeSession(session);
			}
		}

		return count.intValue();
	}

	private static final String _FINDER_COLUMN_UUID_C_UUID_2 =
		"searchEvent.uuid = ? AND ";

	private static final String _FINDER_COLUMN_UUID_C_UUID_3 =
		"(searchEvent.uuid IS NULL OR searchEvent.uuid = '') AND ";

	private static final String _FINDER_COLUMN_UUID_C_COMPANYID_2 =
		"searchEvent.companyId = ?";

	private FinderPath _finderPathWithPaginationFindByC_LtCreateDate;
	private FinderPath _finderPathWithPaginationCountByC_LtCreateDate;

	/**
	 * Returns all the search events where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @return the matching search events
	 */
	@Override
	public List<SearchEvent> findByC_LtCreateDate(
		long companyId, Date createDate) {

		return findByC_LtCreateDate(
			companyId, createDate, QueryUtil.ALL_POS, QueryUtil.ALL_POS, null);
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
	@Override
	public List<SearchEvent> findByC_LtCreateDate(
		long companyId, Date createDate, int start, int end) {

		return findByC_LtCreateDate(companyId, createDate, start, end, null);
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
	@Override
	public List<SearchEvent> findByC_LtCreateDate(
		long companyId, Date createDate, int start, int end,
		OrderByComparator<SearchEvent> orderByComparator) {

		return findByC_LtCreateDate(
			companyId, createDate, start, end, orderByComparator, true);
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
	@Override
	public List<SearchEvent> findByC_LtCreateDate(
		long companyId, Date createDate, int start, int end,
		OrderByComparator<SearchEvent> orderByComparator,
		boolean useFinderCache) {

		FinderPath finderPath = null;
		Object[] finderArgs = null;

		finderPath = _finderPathWithPaginationFindByC_LtCreateDate;
		finderArgs = new Object[] {
			companyId, _getTime(createDate), start, end, orderByComparator
		};

		List<SearchEvent> list = null;

		if (useFinderCache) {
			list = (List<SearchEvent>)dummyFinderCache.getResult(
				finderPath, finderArgs, this);

			if ((list != null) && !list.isEmpty()) {
				for (SearchEvent searchEvent : list) {
					if ((companyId != searchEvent.getCompanyId()) ||
						(createDate.getTime() <= searchEvent.getCreateDate(
						).getTime())) {

						list = null;

						break;
					}
				}
			}
		}

		if (list == null) {
			StringBundler sb = null;

			if (orderByComparator != null) {
				sb = new StringBundler(
					4 + (orderByComparator.getOrderByFields().length * 2));
			}
			else {
				sb = new StringBundler(4);
			}

			sb.append(_SQL_SELECT_SEARCHEVENT_WHERE);

			sb.append(_FINDER_COLUMN_C_LTCREATEDATE_COMPANYID_2);

			boolean bindCreateDate = false;

			if (createDate == null) {
				sb.append(_FINDER_COLUMN_C_LTCREATEDATE_CREATEDATE_1);
			}
			else {
				bindCreateDate = true;

				sb.append(_FINDER_COLUMN_C_LTCREATEDATE_CREATEDATE_2);
			}

			if (orderByComparator != null) {
				appendOrderByComparator(
					sb, _ORDER_BY_ENTITY_ALIAS, orderByComparator);
			}
			else {
				sb.append(SearchEventModelImpl.ORDER_BY_JPQL);
			}

			String sql = sb.toString();

			Session session = null;

			try {
				session = openSession();

				Query query = session.createQuery(sql);

				QueryPos queryPos = QueryPos.getInstance(query);

				queryPos.add(companyId);

				if (bindCreateDate) {
					queryPos.add(new Timestamp(createDate.getTime()));
				}

				list = (List<SearchEvent>)QueryUtil.list(
					query, getDialect(), start, end);

				cacheResult(list);

				if (useFinderCache) {
					dummyFinderCache.putResult(finderPath, finderArgs, list);
				}
			}
			catch (Exception exception) {
				throw processException(exception);
			}
			finally {
				closeSession(session);
			}
		}

		return list;
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
	@Override
	public SearchEvent findByC_LtCreateDate_First(
			long companyId, Date createDate,
			OrderByComparator<SearchEvent> orderByComparator)
		throws NoSuchSearchEventException {

		SearchEvent searchEvent = fetchByC_LtCreateDate_First(
			companyId, createDate, orderByComparator);

		if (searchEvent != null) {
			return searchEvent;
		}

		StringBundler sb = new StringBundler(6);

		sb.append(_NO_SUCH_ENTITY_WITH_KEY);

		sb.append("companyId=");
		sb.append(companyId);

		sb.append(", createDate<");
		sb.append(createDate);

		sb.append("}");

		throw new NoSuchSearchEventException(sb.toString());
	}

	/**
	 * Returns the first search event in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search event, or <code>null</code> if a matching search event could not be found
	 */
	@Override
	public SearchEvent fetchByC_LtCreateDate_First(
		long companyId, Date createDate,
		OrderByComparator<SearchEvent> orderByComparator) {

		List<SearchEvent> list = findByC_LtCreateDate(
			companyId, createDate, 0, 1, orderByComparator);

		if (!list.isEmpty()) {
			return list.get(0);
		}

		return null;
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
	@Override
	public SearchEvent findByC_LtCreateDate_Last(
			long companyId, Date createDate,
			OrderByComparator<SearchEvent> orderByComparator)
		throws NoSuchSearchEventException {

		SearchEvent searchEvent = fetchByC_LtCreateDate_Last(
			companyId, createDate, orderByComparator);

		if (searchEvent != null) {
			return searchEvent;
		}

		StringBundler sb = new StringBundler(6);

		sb.append(_NO_SUCH_ENTITY_WITH_KEY);

		sb.append("companyId=");
		sb.append(companyId);

		sb.append(", createDate<");
		sb.append(createDate);

		sb.append("}");

		throw new NoSuchSearchEventException(sb.toString());
	}

	/**
	 * Returns the last search event in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search event, or <code>null</code> if a matching search event could not be found
	 */
	@Override
	public SearchEvent fetchByC_LtCreateDate_Last(
		long companyId, Date createDate,
		OrderByComparator<SearchEvent> orderByComparator) {

		int count = countByC_LtCreateDate(companyId, createDate);

		if (count == 0) {
			return null;
		}

		List<SearchEvent> list = findByC_LtCreateDate(
			companyId, createDate, count - 1, count, orderByComparator);

		if (!list.isEmpty()) {
			return list.get(0);
		}

		return null;
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
	@Override
	public SearchEvent[] findByC_LtCreateDate_PrevAndNext(
			long searchEventId, long companyId, Date createDate,
			OrderByComparator<SearchEvent> orderByComparator)
		throws NoSuchSearchEventException {

		SearchEvent searchEvent = findByPrimaryKey(searchEventId);

		Session session = null;

		try {
			session = openSession();

			SearchEvent[] array = new SearchEventImpl[3];

			array[0] = getByC_LtCreateDate_PrevAndNext(
				session, searchEvent, companyId, createDate, orderByComparator,
				true);

			array[1] = searchEvent;

			array[2] = getByC_LtCreateDate_PrevAndNext(
				session, searchEvent, companyId, createDate, orderByComparator,
				false);

			return array;
		}
		catch (Exception exception) {
			throw processException(exception);
		}
		finally {
			closeSession(session);
		}
	}

	protected SearchEvent getByC_LtCreateDate_PrevAndNext(
		Session session, SearchEvent searchEvent, long companyId,
		Date createDate, OrderByComparator<SearchEvent> orderByComparator,
		boolean previous) {

		StringBundler sb = null;

		if (orderByComparator != null) {
			sb = new StringBundler(
				5 + (orderByComparator.getOrderByConditionFields().length * 3) +
					(orderByComparator.getOrderByFields().length * 3));
		}
		else {
			sb = new StringBundler(4);
		}

		sb.append(_SQL_SELECT_SEARCHEVENT_WHERE);

		sb.append(_FINDER_COLUMN_C_LTCREATEDATE_COMPANYID_2);

		boolean bindCreateDate = false;

		if (createDate == null) {
			sb.append(_FINDER_COLUMN_C_LTCREATEDATE_CREATEDATE_1);
		}
		else {
			bindCreateDate = true;

			sb.append(_FINDER_COLUMN_C_LTCREATEDATE_CREATEDATE_2);
		}

		if (orderByComparator != null) {
			String[] orderByConditionFields =
				orderByComparator.getOrderByConditionFields();

			if (orderByConditionFields.length > 0) {
				sb.append(WHERE_AND);
			}

			for (int i = 0; i < orderByConditionFields.length; i++) {
				sb.append(_ORDER_BY_ENTITY_ALIAS);
				sb.append(orderByConditionFields[i]);

				if ((i + 1) < orderByConditionFields.length) {
					if (orderByComparator.isAscending() ^ previous) {
						sb.append(WHERE_GREATER_THAN_HAS_NEXT);
					}
					else {
						sb.append(WHERE_LESSER_THAN_HAS_NEXT);
					}
				}
				else {
					if (orderByComparator.isAscending() ^ previous) {
						sb.append(WHERE_GREATER_THAN);
					}
					else {
						sb.append(WHERE_LESSER_THAN);
					}
				}
			}

			sb.append(ORDER_BY_CLAUSE);

			String[] orderByFields = orderByComparator.getOrderByFields();

			for (int i = 0; i < orderByFields.length; i++) {
				sb.append(_ORDER_BY_ENTITY_ALIAS);
				sb.append(orderByFields[i]);

				if ((i + 1) < orderByFields.length) {
					if (orderByComparator.isAscending() ^ previous) {
						sb.append(ORDER_BY_ASC_HAS_NEXT);
					}
					else {
						sb.append(ORDER_BY_DESC_HAS_NEXT);
					}
				}
				else {
					if (orderByComparator.isAscending() ^ previous) {
						sb.append(ORDER_BY_ASC);
					}
					else {
						sb.append(ORDER_BY_DESC);
					}
				}
			}
		}
		else {
			sb.append(SearchEventModelImpl.ORDER_BY_JPQL);
		}

		String sql = sb.toString();

		Query query = session.createQuery(sql);

		query.setFirstResult(0);
		query.setMaxResults(2);

		QueryPos queryPos = QueryPos.getInstance(query);

		queryPos.add(companyId);

		if (bindCreateDate) {
			queryPos.add(new Timestamp(createDate.getTime()));
		}

		if (orderByComparator != null) {
			for (Object orderByConditionValue :
					orderByComparator.getOrderByConditionValues(searchEvent)) {

				queryPos.add(orderByConditionValue);
			}
		}

		List<SearchEvent> list = query.list();

		if (list.size() == 2) {
			return list.get(1);
		}
		else {
			return null;
		}
	}

	/**
	 * Removes all the search events where companyId = &#63; and createDate &lt; &#63; from the database.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 */
	@Override
	public void removeByC_LtCreateDate(long companyId, Date createDate) {
		for (SearchEvent searchEvent :
				findByC_LtCreateDate(
					companyId, createDate, QueryUtil.ALL_POS, QueryUtil.ALL_POS,
					null)) {

			remove(searchEvent);
		}
	}

	/**
	 * Returns the number of search events where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @return the number of matching search events
	 */
	@Override
	public int countByC_LtCreateDate(long companyId, Date createDate) {
		FinderPath finderPath = _finderPathWithPaginationCountByC_LtCreateDate;

		Object[] finderArgs = new Object[] {companyId, _getTime(createDate)};

		Long count = (Long)dummyFinderCache.getResult(
			finderPath, finderArgs, this);

		if (count == null) {
			StringBundler sb = new StringBundler(3);

			sb.append(_SQL_COUNT_SEARCHEVENT_WHERE);

			sb.append(_FINDER_COLUMN_C_LTCREATEDATE_COMPANYID_2);

			boolean bindCreateDate = false;

			if (createDate == null) {
				sb.append(_FINDER_COLUMN_C_LTCREATEDATE_CREATEDATE_1);
			}
			else {
				bindCreateDate = true;

				sb.append(_FINDER_COLUMN_C_LTCREATEDATE_CREATEDATE_2);
			}

			String sql = sb.toString();

			Session session = null;

			try {
				session = openSession();

				Query query = session.createQuery(sql);

				QueryPos queryPos = QueryPos.getInstance(query);

				queryPos.add(companyId);

				if (bindCreateDate) {
					queryPos.add(new Timestamp(createDate.getTime()));
				}

				count = (Long)query.uniqueResult();

				dummyFinderCache.putResult(finderPath, finderArgs, count);
			}
			catch (Exception exception) {
				throw processException(exception);
			}
			finally {
				closeSession(session);
			}
		}

		return count.intValue();
	}

	private static final String _FINDER_COLUMN_C_LTCREATEDATE_COMPANYID_2 =
		"searchEvent.companyId = ? AND ";

	private static final String _FINDER_COLUMN_C_LTCREATEDATE_CREATEDATE_1 =
		"searchEvent.createDate IS NULL";

	private static final String _FINDER_COLUMN_C_LTCREATEDATE_CREATEDATE_2 =
		"searchEvent.createDate < ?";

	public SearchEventPersistenceImpl() {
		Map<String, String> dbColumnNames = new HashMap<String, String>();

		dbColumnNames.put("uuid", "uuid_");

		setDBColumnNames(dbColumnNames);

		setModelClass(SearchEvent.class);

		setModelImplClass(SearchEventImpl.class);
		setModelPKClass(long.class);

		setTable(SearchEventTable.INSTANCE);
	}

	/**
	 * Caches the search event in the entity cache if it is enabled.
	 *
	 * @param searchEvent the search event
	 */
	@Override
	public void cacheResult(SearchEvent searchEvent) {
		dummyEntityCache.putResult(
			SearchEventImpl.class, searchEvent.getPrimaryKey(), searchEvent);
	}

	private int _valueObjectFinderCacheListThreshold;

	/**
	 * Caches the search events in the entity cache if it is enabled.
	 *
	 * @param searchEvents the search events
	 */
	@Override
	public void cacheResult(List<SearchEvent> searchEvents) {
		if ((_valueObjectFinderCacheListThreshold == 0) ||
			((_valueObjectFinderCacheListThreshold > 0) &&
			 (searchEvents.size() > _valueObjectFinderCacheListThreshold))) {

			return;
		}

		for (SearchEvent searchEvent : searchEvents) {
			if (dummyEntityCache.getResult(
					SearchEventImpl.class, searchEvent.getPrimaryKey()) ==
						null) {

				cacheResult(searchEvent);
			}
		}
	}

	/**
	 * Clears the cache for all search events.
	 *
	 * <p>
	 * The <code>EntityCache</code> and <code>FinderCache</code> are both cleared by this method.
	 * </p>
	 */
	@Override
	public void clearCache() {
		dummyEntityCache.clearCache(SearchEventImpl.class);

		dummyFinderCache.clearCache(SearchEventImpl.class);
	}

	/**
	 * Clears the cache for the search event.
	 *
	 * <p>
	 * The <code>EntityCache</code> and <code>FinderCache</code> are both cleared by this method.
	 * </p>
	 */
	@Override
	public void clearCache(SearchEvent searchEvent) {
		dummyEntityCache.removeResult(SearchEventImpl.class, searchEvent);
	}

	@Override
	public void clearCache(List<SearchEvent> searchEvents) {
		for (SearchEvent searchEvent : searchEvents) {
			dummyEntityCache.removeResult(SearchEventImpl.class, searchEvent);
		}
	}

	@Override
	public void clearCache(Set<Serializable> primaryKeys) {
		dummyFinderCache.clearCache(SearchEventImpl.class);

		for (Serializable primaryKey : primaryKeys) {
			dummyEntityCache.removeResult(SearchEventImpl.class, primaryKey);
		}
	}

	/**
	 * Creates a new search event with the primary key. Does not add the search event to the database.
	 *
	 * @param searchEventId the primary key for the new search event
	 * @return the new search event
	 */
	@Override
	public SearchEvent create(long searchEventId) {
		SearchEvent searchEvent = new SearchEventImpl();

		searchEvent.setNew(true);
		searchEvent.setPrimaryKey(searchEventId);

		String uuid = PortalUUIDUtil.generate();

		searchEvent.setUuid(uuid);

		searchEvent.setCompanyId(CompanyThreadLocal.getCompanyId());

		return searchEvent;
	}

	/**
	 * Removes the search event with the primary key from the database. Also notifies the appropriate model listeners.
	 *
	 * @param searchEventId the primary key of the search event
	 * @return the search event that was removed
	 * @throws NoSuchSearchEventException if a search event with the primary key could not be found
	 */
	@Override
	public SearchEvent remove(long searchEventId)
		throws NoSuchSearchEventException {

		return remove((Serializable)searchEventId);
	}

	/**
	 * Removes the search event with the primary key from the database. Also notifies the appropriate model listeners.
	 *
	 * @param primaryKey the primary key of the search event
	 * @return the search event that was removed
	 * @throws NoSuchSearchEventException if a search event with the primary key could not be found
	 */
	@Override
	public SearchEvent remove(Serializable primaryKey)
		throws NoSuchSearchEventException {

		Session session = null;

		try {
			session = openSession();

			SearchEvent searchEvent = (SearchEvent)session.get(
				SearchEventImpl.class, primaryKey);

			if (searchEvent == null) {
				if (_log.isDebugEnabled()) {
					_log.debug(_NO_SUCH_ENTITY_WITH_PRIMARY_KEY + primaryKey);
				}

				throw new NoSuchSearchEventException(
					_NO_SUCH_ENTITY_WITH_PRIMARY_KEY + primaryKey);
			}

			return remove(searchEvent);
		}
		catch (NoSuchSearchEventException noSuchEntityException) {
			throw noSuchEntityException;
		}
		catch (Exception exception) {
			throw processException(exception);
		}
		finally {
			closeSession(session);
		}
	}

	@Override
	protected SearchEvent removeImpl(SearchEvent searchEvent) {
		Session session = null;

		try {
			session = openSession();

			if (!session.contains(searchEvent)) {
				searchEvent = (SearchEvent)session.get(
					SearchEventImpl.class, searchEvent.getPrimaryKeyObj());
			}

			if (searchEvent != null) {
				session.delete(searchEvent);
			}
		}
		catch (Exception exception) {
			throw processException(exception);
		}
		finally {
			closeSession(session);
		}

		if (searchEvent != null) {
			clearCache(searchEvent);
		}

		return searchEvent;
	}

	@Override
	public SearchEvent updateImpl(SearchEvent searchEvent) {
		boolean isNew = searchEvent.isNew();

		if (!(searchEvent instanceof SearchEventModelImpl)) {
			InvocationHandler invocationHandler = null;

			if (ProxyUtil.isProxyClass(searchEvent.getClass())) {
				invocationHandler = ProxyUtil.getInvocationHandler(searchEvent);

				throw new IllegalArgumentException(
					"Implement ModelWrapper in searchEvent proxy " +
						invocationHandler.getClass());
			}

			throw new IllegalArgumentException(
				"Implement ModelWrapper in custom SearchEvent implementation " +
					searchEvent.getClass());
		}

		SearchEventModelImpl searchEventModelImpl =
			(SearchEventModelImpl)searchEvent;

		if (Validator.isNull(searchEvent.getUuid())) {
			String uuid = PortalUUIDUtil.generate();

			searchEvent.setUuid(uuid);
		}

		if (isNew && (searchEvent.getCreateDate() == null)) {
			ServiceContext serviceContext =
				ServiceContextThreadLocal.getServiceContext();

			Date date = new Date();

			if (serviceContext == null) {
				searchEvent.setCreateDate(date);
			}
			else {
				searchEvent.setCreateDate(serviceContext.getCreateDate(date));
			}
		}

		Session session = null;

		try {
			session = openSession();

			if (isNew) {
				session.save(searchEvent);
			}
			else {
				searchEvent = (SearchEvent)session.merge(searchEvent);
			}
		}
		catch (Exception exception) {
			throw processException(exception);
		}
		finally {
			closeSession(session);
		}

		dummyEntityCache.putResult(
			SearchEventImpl.class, searchEventModelImpl, false, true);

		if (isNew) {
			searchEvent.setNew(false);
		}

		searchEvent.resetOriginalValues();

		return searchEvent;
	}

	/**
	 * Returns the search event with the primary key or throws a <code>com.liferay.portal.kernel.exception.NoSuchModelException</code> if it could not be found.
	 *
	 * @param primaryKey the primary key of the search event
	 * @return the search event
	 * @throws NoSuchSearchEventException if a search event with the primary key could not be found
	 */
	@Override
	public SearchEvent findByPrimaryKey(Serializable primaryKey)
		throws NoSuchSearchEventException {

		SearchEvent searchEvent = fetchByPrimaryKey(primaryKey);

		if (searchEvent == null) {
			if (_log.isDebugEnabled()) {
				_log.debug(_NO_SUCH_ENTITY_WITH_PRIMARY_KEY + primaryKey);
			}

			throw new NoSuchSearchEventException(
				_NO_SUCH_ENTITY_WITH_PRIMARY_KEY + primaryKey);
		}

		return searchEvent;
	}

	/**
	 * Returns the search event with the primary key or throws a <code>NoSuchSearchEventException</code> if it could not be found.
	 *
	 * @param searchEventId the primary key of the search event
	 * @return the search event
	 * @throws NoSuchSearchEventException if a search event with the primary key could not be found
	 */
	@Override
	public SearchEvent findByPrimaryKey(long searchEventId)
		throws NoSuchSearchEventException {

		return findByPrimaryKey((Serializable)searchEventId);
	}

	/**
	 * Returns the search event with the primary key or returns <code>null</code> if it could not be found.
	 *
	 * @param searchEventId the primary key of the search event
	 * @return the search event, or <code>null</code> if a search event with the primary key could not be found
	 */
	@Override
	public SearchEvent fetchByPrimaryKey(long searchEventId) {
		return fetchByPrimaryKey((Serializable)searchEventId);
	}

	/**
	 * Returns all the search events.
	 *
	 * @return the search events
	 */
	@Override
	public List<SearchEvent> findAll() {
		return findAll(QueryUtil.ALL_POS, QueryUtil.ALL_POS, null);
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
	@Override
	public List<SearchEvent> findAll(int start, int end) {
		return findAll(start, end, null);
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
	@Override
	public List<SearchEvent> findAll(
		int start, int end, OrderByComparator<SearchEvent> orderByComparator) {

		return findAll(start, end, orderByComparator, true);
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
	@Override
	public List<SearchEvent> findAll(
		int start, int end, OrderByComparator<SearchEvent> orderByComparator,
		boolean useFinderCache) {

		FinderPath finderPath = null;
		Object[] finderArgs = null;

		if ((start == QueryUtil.ALL_POS) && (end == QueryUtil.ALL_POS) &&
			(orderByComparator == null)) {

			if (useFinderCache) {
				finderPath = _finderPathWithoutPaginationFindAll;
				finderArgs = FINDER_ARGS_EMPTY;
			}
		}
		else if (useFinderCache) {
			finderPath = _finderPathWithPaginationFindAll;
			finderArgs = new Object[] {start, end, orderByComparator};
		}

		List<SearchEvent> list = null;

		if (useFinderCache) {
			list = (List<SearchEvent>)dummyFinderCache.getResult(
				finderPath, finderArgs, this);
		}

		if (list == null) {
			StringBundler sb = null;
			String sql = null;

			if (orderByComparator != null) {
				sb = new StringBundler(
					2 + (orderByComparator.getOrderByFields().length * 2));

				sb.append(_SQL_SELECT_SEARCHEVENT);

				appendOrderByComparator(
					sb, _ORDER_BY_ENTITY_ALIAS, orderByComparator);

				sql = sb.toString();
			}
			else {
				sql = _SQL_SELECT_SEARCHEVENT;

				sql = sql.concat(SearchEventModelImpl.ORDER_BY_JPQL);
			}

			Session session = null;

			try {
				session = openSession();

				Query query = session.createQuery(sql);

				list = (List<SearchEvent>)QueryUtil.list(
					query, getDialect(), start, end);

				cacheResult(list);

				if (useFinderCache) {
					dummyFinderCache.putResult(finderPath, finderArgs, list);
				}
			}
			catch (Exception exception) {
				throw processException(exception);
			}
			finally {
				closeSession(session);
			}
		}

		return list;
	}

	/**
	 * Removes all the search events from the database.
	 *
	 */
	@Override
	public void removeAll() {
		for (SearchEvent searchEvent : findAll()) {
			remove(searchEvent);
		}
	}

	/**
	 * Returns the number of search events.
	 *
	 * @return the number of search events
	 */
	@Override
	public int countAll() {
		Long count = (Long)dummyFinderCache.getResult(
			_finderPathCountAll, FINDER_ARGS_EMPTY, this);

		if (count == null) {
			Session session = null;

			try {
				session = openSession();

				Query query = session.createQuery(_SQL_COUNT_SEARCHEVENT);

				count = (Long)query.uniqueResult();

				dummyFinderCache.putResult(
					_finderPathCountAll, FINDER_ARGS_EMPTY, count);
			}
			catch (Exception exception) {
				throw processException(exception);
			}
			finally {
				closeSession(session);
			}
		}

		return count.intValue();
	}

	@Override
	public Set<String> getBadColumnNames() {
		return _badColumnNames;
	}

	@Override
	protected EntityCache getEntityCache() {
		return dummyEntityCache;
	}

	@Override
	protected String getPKDBName() {
		return "searchEventId";
	}

	@Override
	protected String getSelectSQL() {
		return _SQL_SELECT_SEARCHEVENT;
	}

	@Override
	protected Map<String, Integer> getTableColumnsMap() {
		return SearchEventModelImpl.TABLE_COLUMNS_MAP;
	}

	/**
	 * Initializes the search event persistence.
	 */
	@Activate
	public void activate() {
		_valueObjectFinderCacheListThreshold = GetterUtil.getInteger(
			PropsUtil.get(PropsKeys.VALUE_OBJECT_FINDER_CACHE_LIST_THRESHOLD));

		_finderPathWithPaginationFindAll = new FinderPath(
			FINDER_CLASS_NAME_LIST_WITH_PAGINATION, "findAll", new String[0],
			new String[0], true);

		_finderPathWithoutPaginationFindAll = new FinderPath(
			FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION, "findAll", new String[0],
			new String[0], true);

		_finderPathCountAll = new FinderPath(
			FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION, "countAll",
			new String[0], new String[0], false);

		_finderPathWithPaginationFindByUuid = new FinderPath(
			FINDER_CLASS_NAME_LIST_WITH_PAGINATION, "findByUuid",
			new String[] {
				String.class.getName(), Integer.class.getName(),
				Integer.class.getName(), OrderByComparator.class.getName()
			},
			new String[] {"uuid_"}, true);

		_finderPathWithoutPaginationFindByUuid = new FinderPath(
			FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION, "findByUuid",
			new String[] {String.class.getName()}, new String[] {"uuid_"},
			true);

		_finderPathCountByUuid = new FinderPath(
			FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION, "countByUuid",
			new String[] {String.class.getName()}, new String[] {"uuid_"},
			false);

		_finderPathWithPaginationFindByUuid_C = new FinderPath(
			FINDER_CLASS_NAME_LIST_WITH_PAGINATION, "findByUuid_C",
			new String[] {
				String.class.getName(), Long.class.getName(),
				Integer.class.getName(), Integer.class.getName(),
				OrderByComparator.class.getName()
			},
			new String[] {"uuid_", "companyId"}, true);

		_finderPathWithoutPaginationFindByUuid_C = new FinderPath(
			FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION, "findByUuid_C",
			new String[] {String.class.getName(), Long.class.getName()},
			new String[] {"uuid_", "companyId"}, true);

		_finderPathCountByUuid_C = new FinderPath(
			FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION, "countByUuid_C",
			new String[] {String.class.getName(), Long.class.getName()},
			new String[] {"uuid_", "companyId"}, false);

		_finderPathWithPaginationFindByC_LtCreateDate = new FinderPath(
			FINDER_CLASS_NAME_LIST_WITH_PAGINATION, "findByC_LtCreateDate",
			new String[] {
				Long.class.getName(), Date.class.getName(),
				Integer.class.getName(), Integer.class.getName(),
				OrderByComparator.class.getName()
			},
			new String[] {"companyId", "createDate"}, true);

		_finderPathWithPaginationCountByC_LtCreateDate = new FinderPath(
			FINDER_CLASS_NAME_LIST_WITH_PAGINATION, "countByC_LtCreateDate",
			new String[] {Long.class.getName(), Date.class.getName()},
			new String[] {"companyId", "createDate"}, false);

		SearchEventUtil.setPersistence(this);
	}

	@Deactivate
	public void deactivate() {
		SearchEventUtil.setPersistence(null);

		dummyEntityCache.removeCache(SearchEventImpl.class.getName());
	}

	@Override
	@Reference(
		target = SELPersistenceConstants.SERVICE_CONFIGURATION_FILTER,
		unbind = "-"
	)
	public void setConfiguration(Configuration configuration) {
	}

	@Override
	@Reference(
		target = SELPersistenceConstants.ORIGIN_BUNDLE_SYMBOLIC_NAME_FILTER,
		unbind = "-"
	)
	public void setDataSource(DataSource dataSource) {
		super.setDataSource(dataSource);
	}

	@Override
	@Reference(
		target = SELPersistenceConstants.ORIGIN_BUNDLE_SYMBOLIC_NAME_FILTER,
		unbind = "-"
	)
	public void setSessionFactory(SessionFactory sessionFactory) {
		super.setSessionFactory(sessionFactory);
	}

	private static Long _getTime(Date date) {
		if (date == null) {
			return null;
		}

		return date.getTime();
	}

	private static final String _SQL_SELECT_SEARCHEVENT =
		"SELECT searchEvent FROM SearchEvent searchEvent";

	private static final String _SQL_SELECT_SEARCHEVENT_WHERE =
		"SELECT searchEvent FROM SearchEvent searchEvent WHERE ";

	private static final String _SQL_COUNT_SEARCHEVENT =
		"SELECT COUNT(searchEvent) FROM SearchEvent searchEvent";

	private static final String _SQL_COUNT_SEARCHEVENT_WHERE =
		"SELECT COUNT(searchEvent) FROM SearchEvent searchEvent WHERE ";

	private static final String _ORDER_BY_ENTITY_ALIAS = "searchEvent.";

	private static final String _NO_SUCH_ENTITY_WITH_PRIMARY_KEY =
		"No SearchEvent exists with the primary key ";

	private static final String _NO_SUCH_ENTITY_WITH_KEY =
		"No SearchEvent exists with the key {";

	private static final Log _log = LogFactoryUtil.getLog(
		SearchEventPersistenceImpl.class);

	private static final Set<String> _badColumnNames = SetUtil.fromArray(
		new String[] {"uuid"});

	@Override
	protected FinderCache getFinderCache() {
		return dummyFinderCache;
	}

}