/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package ai.tensoropt.sel.service.persistence.impl;

import ai.tensoropt.sel.exception.NoSuchSearchHitException;
import ai.tensoropt.sel.model.SearchHit;
import ai.tensoropt.sel.model.SearchHitTable;
import ai.tensoropt.sel.model.impl.SearchHitImpl;
import ai.tensoropt.sel.model.impl.SearchHitModelImpl;
import ai.tensoropt.sel.service.persistence.SearchHitPersistence;
import ai.tensoropt.sel.service.persistence.SearchHitUtil;
import ai.tensoropt.sel.service.persistence.impl.constants.SELPersistenceConstants;

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
 * The persistence implementation for the search hit service.
 *
 * <p>
 * Caching information and settings can be found in <code>portal.properties</code>
 * </p>
 *
 * @author Brian Wing Shun Chan
 * @generated
 */
@Component(service = SearchHitPersistence.class)
public class SearchHitPersistenceImpl
	extends BasePersistenceImpl<SearchHit> implements SearchHitPersistence {

	/*
	 * NOTE FOR DEVELOPERS:
	 *
	 * Never modify or reference this class directly. Always use <code>SearchHitUtil</code> to access the search hit persistence. Modify <code>service.xml</code> and rerun ServiceBuilder to regenerate this class.
	 */
	public static final String FINDER_CLASS_NAME_ENTITY =
		SearchHitImpl.class.getName();

	public static final String FINDER_CLASS_NAME_LIST_WITH_PAGINATION =
		FINDER_CLASS_NAME_ENTITY + ".List1";

	public static final String FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION =
		FINDER_CLASS_NAME_ENTITY + ".List2";

	private FinderPath _finderPathWithPaginationFindAll;
	private FinderPath _finderPathWithoutPaginationFindAll;
	private FinderPath _finderPathCountAll;
	private FinderPath _finderPathWithPaginationFindBySearchEventUuid;
	private FinderPath _finderPathWithoutPaginationFindBySearchEventUuid;
	private FinderPath _finderPathCountBySearchEventUuid;

	/**
	 * Returns all the search hits where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @return the matching search hits
	 */
	@Override
	public List<SearchHit> findBySearchEventUuid(String searchEventUuid) {
		return findBySearchEventUuid(
			searchEventUuid, QueryUtil.ALL_POS, QueryUtil.ALL_POS, null);
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
	@Override
	public List<SearchHit> findBySearchEventUuid(
		String searchEventUuid, int start, int end) {

		return findBySearchEventUuid(searchEventUuid, start, end, null);
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
	@Override
	public List<SearchHit> findBySearchEventUuid(
		String searchEventUuid, int start, int end,
		OrderByComparator<SearchHit> orderByComparator) {

		return findBySearchEventUuid(
			searchEventUuid, start, end, orderByComparator, true);
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
	@Override
	public List<SearchHit> findBySearchEventUuid(
		String searchEventUuid, int start, int end,
		OrderByComparator<SearchHit> orderByComparator,
		boolean useFinderCache) {

		searchEventUuid = Objects.toString(searchEventUuid, "");

		FinderPath finderPath = null;
		Object[] finderArgs = null;

		if ((start == QueryUtil.ALL_POS) && (end == QueryUtil.ALL_POS) &&
			(orderByComparator == null)) {

			if (useFinderCache) {
				finderPath = _finderPathWithoutPaginationFindBySearchEventUuid;
				finderArgs = new Object[] {searchEventUuid};
			}
		}
		else if (useFinderCache) {
			finderPath = _finderPathWithPaginationFindBySearchEventUuid;
			finderArgs = new Object[] {
				searchEventUuid, start, end, orderByComparator
			};
		}

		List<SearchHit> list = null;

		if (useFinderCache) {
			list = (List<SearchHit>)dummyFinderCache.getResult(
				finderPath, finderArgs, this);

			if ((list != null) && !list.isEmpty()) {
				for (SearchHit searchHit : list) {
					if (!searchEventUuid.equals(
							searchHit.getSearchEventUuid())) {

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

			sb.append(_SQL_SELECT_SEARCHHIT_WHERE);

			boolean bindSearchEventUuid = false;

			if (searchEventUuid.isEmpty()) {
				sb.append(_FINDER_COLUMN_SEARCHEVENTUUID_SEARCHEVENTUUID_3);
			}
			else {
				bindSearchEventUuid = true;

				sb.append(_FINDER_COLUMN_SEARCHEVENTUUID_SEARCHEVENTUUID_2);
			}

			if (orderByComparator != null) {
				appendOrderByComparator(
					sb, _ORDER_BY_ENTITY_ALIAS, orderByComparator);
			}
			else {
				sb.append(SearchHitModelImpl.ORDER_BY_JPQL);
			}

			String sql = sb.toString();

			Session session = null;

			try {
				session = openSession();

				Query query = session.createQuery(sql);

				QueryPos queryPos = QueryPos.getInstance(query);

				if (bindSearchEventUuid) {
					queryPos.add(searchEventUuid);
				}

				list = (List<SearchHit>)QueryUtil.list(
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
	 * Returns the first search hit in the ordered set where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search hit
	 * @throws NoSuchSearchHitException if a matching search hit could not be found
	 */
	@Override
	public SearchHit findBySearchEventUuid_First(
			String searchEventUuid,
			OrderByComparator<SearchHit> orderByComparator)
		throws NoSuchSearchHitException {

		SearchHit searchHit = fetchBySearchEventUuid_First(
			searchEventUuid, orderByComparator);

		if (searchHit != null) {
			return searchHit;
		}

		StringBundler sb = new StringBundler(4);

		sb.append(_NO_SUCH_ENTITY_WITH_KEY);

		sb.append("searchEventUuid=");
		sb.append(searchEventUuid);

		sb.append("}");

		throw new NoSuchSearchHitException(sb.toString());
	}

	/**
	 * Returns the first search hit in the ordered set where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search hit, or <code>null</code> if a matching search hit could not be found
	 */
	@Override
	public SearchHit fetchBySearchEventUuid_First(
		String searchEventUuid,
		OrderByComparator<SearchHit> orderByComparator) {

		List<SearchHit> list = findBySearchEventUuid(
			searchEventUuid, 0, 1, orderByComparator);

		if (!list.isEmpty()) {
			return list.get(0);
		}

		return null;
	}

	/**
	 * Returns the last search hit in the ordered set where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search hit
	 * @throws NoSuchSearchHitException if a matching search hit could not be found
	 */
	@Override
	public SearchHit findBySearchEventUuid_Last(
			String searchEventUuid,
			OrderByComparator<SearchHit> orderByComparator)
		throws NoSuchSearchHitException {

		SearchHit searchHit = fetchBySearchEventUuid_Last(
			searchEventUuid, orderByComparator);

		if (searchHit != null) {
			return searchHit;
		}

		StringBundler sb = new StringBundler(4);

		sb.append(_NO_SUCH_ENTITY_WITH_KEY);

		sb.append("searchEventUuid=");
		sb.append(searchEventUuid);

		sb.append("}");

		throw new NoSuchSearchHitException(sb.toString());
	}

	/**
	 * Returns the last search hit in the ordered set where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search hit, or <code>null</code> if a matching search hit could not be found
	 */
	@Override
	public SearchHit fetchBySearchEventUuid_Last(
		String searchEventUuid,
		OrderByComparator<SearchHit> orderByComparator) {

		int count = countBySearchEventUuid(searchEventUuid);

		if (count == 0) {
			return null;
		}

		List<SearchHit> list = findBySearchEventUuid(
			searchEventUuid, count - 1, count, orderByComparator);

		if (!list.isEmpty()) {
			return list.get(0);
		}

		return null;
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
	@Override
	public SearchHit[] findBySearchEventUuid_PrevAndNext(
			long searchHitId, String searchEventUuid,
			OrderByComparator<SearchHit> orderByComparator)
		throws NoSuchSearchHitException {

		searchEventUuid = Objects.toString(searchEventUuid, "");

		SearchHit searchHit = findByPrimaryKey(searchHitId);

		Session session = null;

		try {
			session = openSession();

			SearchHit[] array = new SearchHitImpl[3];

			array[0] = getBySearchEventUuid_PrevAndNext(
				session, searchHit, searchEventUuid, orderByComparator, true);

			array[1] = searchHit;

			array[2] = getBySearchEventUuid_PrevAndNext(
				session, searchHit, searchEventUuid, orderByComparator, false);

			return array;
		}
		catch (Exception exception) {
			throw processException(exception);
		}
		finally {
			closeSession(session);
		}
	}

	protected SearchHit getBySearchEventUuid_PrevAndNext(
		Session session, SearchHit searchHit, String searchEventUuid,
		OrderByComparator<SearchHit> orderByComparator, boolean previous) {

		StringBundler sb = null;

		if (orderByComparator != null) {
			sb = new StringBundler(
				4 + (orderByComparator.getOrderByConditionFields().length * 3) +
					(orderByComparator.getOrderByFields().length * 3));
		}
		else {
			sb = new StringBundler(3);
		}

		sb.append(_SQL_SELECT_SEARCHHIT_WHERE);

		boolean bindSearchEventUuid = false;

		if (searchEventUuid.isEmpty()) {
			sb.append(_FINDER_COLUMN_SEARCHEVENTUUID_SEARCHEVENTUUID_3);
		}
		else {
			bindSearchEventUuid = true;

			sb.append(_FINDER_COLUMN_SEARCHEVENTUUID_SEARCHEVENTUUID_2);
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
			sb.append(SearchHitModelImpl.ORDER_BY_JPQL);
		}

		String sql = sb.toString();

		Query query = session.createQuery(sql);

		query.setFirstResult(0);
		query.setMaxResults(2);

		QueryPos queryPos = QueryPos.getInstance(query);

		if (bindSearchEventUuid) {
			queryPos.add(searchEventUuid);
		}

		if (orderByComparator != null) {
			for (Object orderByConditionValue :
					orderByComparator.getOrderByConditionValues(searchHit)) {

				queryPos.add(orderByConditionValue);
			}
		}

		List<SearchHit> list = query.list();

		if (list.size() == 2) {
			return list.get(1);
		}
		else {
			return null;
		}
	}

	/**
	 * Removes all the search hits where searchEventUuid = &#63; from the database.
	 *
	 * @param searchEventUuid the search event uuid
	 */
	@Override
	public void removeBySearchEventUuid(String searchEventUuid) {
		for (SearchHit searchHit :
				findBySearchEventUuid(
					searchEventUuid, QueryUtil.ALL_POS, QueryUtil.ALL_POS,
					null)) {

			remove(searchHit);
		}
	}

	/**
	 * Returns the number of search hits where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @return the number of matching search hits
	 */
	@Override
	public int countBySearchEventUuid(String searchEventUuid) {
		searchEventUuid = Objects.toString(searchEventUuid, "");

		FinderPath finderPath = _finderPathCountBySearchEventUuid;

		Object[] finderArgs = new Object[] {searchEventUuid};

		Long count = (Long)dummyFinderCache.getResult(
			finderPath, finderArgs, this);

		if (count == null) {
			StringBundler sb = new StringBundler(2);

			sb.append(_SQL_COUNT_SEARCHHIT_WHERE);

			boolean bindSearchEventUuid = false;

			if (searchEventUuid.isEmpty()) {
				sb.append(_FINDER_COLUMN_SEARCHEVENTUUID_SEARCHEVENTUUID_3);
			}
			else {
				bindSearchEventUuid = true;

				sb.append(_FINDER_COLUMN_SEARCHEVENTUUID_SEARCHEVENTUUID_2);
			}

			String sql = sb.toString();

			Session session = null;

			try {
				session = openSession();

				Query query = session.createQuery(sql);

				QueryPos queryPos = QueryPos.getInstance(query);

				if (bindSearchEventUuid) {
					queryPos.add(searchEventUuid);
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

	private static final String
		_FINDER_COLUMN_SEARCHEVENTUUID_SEARCHEVENTUUID_2 =
			"searchHit.searchEventUuid = ?";

	private static final String
		_FINDER_COLUMN_SEARCHEVENTUUID_SEARCHEVENTUUID_3 =
			"(searchHit.searchEventUuid IS NULL OR searchHit.searchEventUuid = '')";

	private FinderPath _finderPathWithPaginationFindByC_LtCreateDate;
	private FinderPath _finderPathWithPaginationCountByC_LtCreateDate;

	/**
	 * Returns all the search hits where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @return the matching search hits
	 */
	@Override
	public List<SearchHit> findByC_LtCreateDate(
		long companyId, Date createDate) {

		return findByC_LtCreateDate(
			companyId, createDate, QueryUtil.ALL_POS, QueryUtil.ALL_POS, null);
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
	@Override
	public List<SearchHit> findByC_LtCreateDate(
		long companyId, Date createDate, int start, int end) {

		return findByC_LtCreateDate(companyId, createDate, start, end, null);
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
	@Override
	public List<SearchHit> findByC_LtCreateDate(
		long companyId, Date createDate, int start, int end,
		OrderByComparator<SearchHit> orderByComparator) {

		return findByC_LtCreateDate(
			companyId, createDate, start, end, orderByComparator, true);
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
	@Override
	public List<SearchHit> findByC_LtCreateDate(
		long companyId, Date createDate, int start, int end,
		OrderByComparator<SearchHit> orderByComparator,
		boolean useFinderCache) {

		FinderPath finderPath = null;
		Object[] finderArgs = null;

		finderPath = _finderPathWithPaginationFindByC_LtCreateDate;
		finderArgs = new Object[] {
			companyId, _getTime(createDate), start, end, orderByComparator
		};

		List<SearchHit> list = null;

		if (useFinderCache) {
			list = (List<SearchHit>)dummyFinderCache.getResult(
				finderPath, finderArgs, this);

			if ((list != null) && !list.isEmpty()) {
				for (SearchHit searchHit : list) {
					if ((companyId != searchHit.getCompanyId()) ||
						(createDate.getTime() <= searchHit.getCreateDate(
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

			sb.append(_SQL_SELECT_SEARCHHIT_WHERE);

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
				sb.append(SearchHitModelImpl.ORDER_BY_JPQL);
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

				list = (List<SearchHit>)QueryUtil.list(
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
	 * Returns the first search hit in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search hit
	 * @throws NoSuchSearchHitException if a matching search hit could not be found
	 */
	@Override
	public SearchHit findByC_LtCreateDate_First(
			long companyId, Date createDate,
			OrderByComparator<SearchHit> orderByComparator)
		throws NoSuchSearchHitException {

		SearchHit searchHit = fetchByC_LtCreateDate_First(
			companyId, createDate, orderByComparator);

		if (searchHit != null) {
			return searchHit;
		}

		StringBundler sb = new StringBundler(6);

		sb.append(_NO_SUCH_ENTITY_WITH_KEY);

		sb.append("companyId=");
		sb.append(companyId);

		sb.append(", createDate<");
		sb.append(createDate);

		sb.append("}");

		throw new NoSuchSearchHitException(sb.toString());
	}

	/**
	 * Returns the first search hit in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search hit, or <code>null</code> if a matching search hit could not be found
	 */
	@Override
	public SearchHit fetchByC_LtCreateDate_First(
		long companyId, Date createDate,
		OrderByComparator<SearchHit> orderByComparator) {

		List<SearchHit> list = findByC_LtCreateDate(
			companyId, createDate, 0, 1, orderByComparator);

		if (!list.isEmpty()) {
			return list.get(0);
		}

		return null;
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
	@Override
	public SearchHit findByC_LtCreateDate_Last(
			long companyId, Date createDate,
			OrderByComparator<SearchHit> orderByComparator)
		throws NoSuchSearchHitException {

		SearchHit searchHit = fetchByC_LtCreateDate_Last(
			companyId, createDate, orderByComparator);

		if (searchHit != null) {
			return searchHit;
		}

		StringBundler sb = new StringBundler(6);

		sb.append(_NO_SUCH_ENTITY_WITH_KEY);

		sb.append("companyId=");
		sb.append(companyId);

		sb.append(", createDate<");
		sb.append(createDate);

		sb.append("}");

		throw new NoSuchSearchHitException(sb.toString());
	}

	/**
	 * Returns the last search hit in the ordered set where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search hit, or <code>null</code> if a matching search hit could not be found
	 */
	@Override
	public SearchHit fetchByC_LtCreateDate_Last(
		long companyId, Date createDate,
		OrderByComparator<SearchHit> orderByComparator) {

		int count = countByC_LtCreateDate(companyId, createDate);

		if (count == 0) {
			return null;
		}

		List<SearchHit> list = findByC_LtCreateDate(
			companyId, createDate, count - 1, count, orderByComparator);

		if (!list.isEmpty()) {
			return list.get(0);
		}

		return null;
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
	@Override
	public SearchHit[] findByC_LtCreateDate_PrevAndNext(
			long searchHitId, long companyId, Date createDate,
			OrderByComparator<SearchHit> orderByComparator)
		throws NoSuchSearchHitException {

		SearchHit searchHit = findByPrimaryKey(searchHitId);

		Session session = null;

		try {
			session = openSession();

			SearchHit[] array = new SearchHitImpl[3];

			array[0] = getByC_LtCreateDate_PrevAndNext(
				session, searchHit, companyId, createDate, orderByComparator,
				true);

			array[1] = searchHit;

			array[2] = getByC_LtCreateDate_PrevAndNext(
				session, searchHit, companyId, createDate, orderByComparator,
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

	protected SearchHit getByC_LtCreateDate_PrevAndNext(
		Session session, SearchHit searchHit, long companyId, Date createDate,
		OrderByComparator<SearchHit> orderByComparator, boolean previous) {

		StringBundler sb = null;

		if (orderByComparator != null) {
			sb = new StringBundler(
				5 + (orderByComparator.getOrderByConditionFields().length * 3) +
					(orderByComparator.getOrderByFields().length * 3));
		}
		else {
			sb = new StringBundler(4);
		}

		sb.append(_SQL_SELECT_SEARCHHIT_WHERE);

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
			sb.append(SearchHitModelImpl.ORDER_BY_JPQL);
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
					orderByComparator.getOrderByConditionValues(searchHit)) {

				queryPos.add(orderByConditionValue);
			}
		}

		List<SearchHit> list = query.list();

		if (list.size() == 2) {
			return list.get(1);
		}
		else {
			return null;
		}
	}

	/**
	 * Removes all the search hits where companyId = &#63; and createDate &lt; &#63; from the database.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 */
	@Override
	public void removeByC_LtCreateDate(long companyId, Date createDate) {
		for (SearchHit searchHit :
				findByC_LtCreateDate(
					companyId, createDate, QueryUtil.ALL_POS, QueryUtil.ALL_POS,
					null)) {

			remove(searchHit);
		}
	}

	/**
	 * Returns the number of search hits where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @return the number of matching search hits
	 */
	@Override
	public int countByC_LtCreateDate(long companyId, Date createDate) {
		FinderPath finderPath = _finderPathWithPaginationCountByC_LtCreateDate;

		Object[] finderArgs = new Object[] {companyId, _getTime(createDate)};

		Long count = (Long)dummyFinderCache.getResult(
			finderPath, finderArgs, this);

		if (count == null) {
			StringBundler sb = new StringBundler(3);

			sb.append(_SQL_COUNT_SEARCHHIT_WHERE);

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
		"searchHit.companyId = ? AND ";

	private static final String _FINDER_COLUMN_C_LTCREATEDATE_CREATEDATE_1 =
		"searchHit.createDate IS NULL";

	private static final String _FINDER_COLUMN_C_LTCREATEDATE_CREATEDATE_2 =
		"searchHit.createDate < ?";

	public SearchHitPersistenceImpl() {
		Map<String, String> dbColumnNames = new HashMap<String, String>();

		dbColumnNames.put("rank", "rank_");

		setDBColumnNames(dbColumnNames);

		setModelClass(SearchHit.class);

		setModelImplClass(SearchHitImpl.class);
		setModelPKClass(long.class);

		setTable(SearchHitTable.INSTANCE);
	}

	/**
	 * Caches the search hit in the entity cache if it is enabled.
	 *
	 * @param searchHit the search hit
	 */
	@Override
	public void cacheResult(SearchHit searchHit) {
		dummyEntityCache.putResult(
			SearchHitImpl.class, searchHit.getPrimaryKey(), searchHit);
	}

	private int _valueObjectFinderCacheListThreshold;

	/**
	 * Caches the search hits in the entity cache if it is enabled.
	 *
	 * @param searchHits the search hits
	 */
	@Override
	public void cacheResult(List<SearchHit> searchHits) {
		if ((_valueObjectFinderCacheListThreshold == 0) ||
			((_valueObjectFinderCacheListThreshold > 0) &&
			 (searchHits.size() > _valueObjectFinderCacheListThreshold))) {

			return;
		}

		for (SearchHit searchHit : searchHits) {
			if (dummyEntityCache.getResult(
					SearchHitImpl.class, searchHit.getPrimaryKey()) == null) {

				cacheResult(searchHit);
			}
		}
	}

	/**
	 * Clears the cache for all search hits.
	 *
	 * <p>
	 * The <code>EntityCache</code> and <code>FinderCache</code> are both cleared by this method.
	 * </p>
	 */
	@Override
	public void clearCache() {
		dummyEntityCache.clearCache(SearchHitImpl.class);

		dummyFinderCache.clearCache(SearchHitImpl.class);
	}

	/**
	 * Clears the cache for the search hit.
	 *
	 * <p>
	 * The <code>EntityCache</code> and <code>FinderCache</code> are both cleared by this method.
	 * </p>
	 */
	@Override
	public void clearCache(SearchHit searchHit) {
		dummyEntityCache.removeResult(SearchHitImpl.class, searchHit);
	}

	@Override
	public void clearCache(List<SearchHit> searchHits) {
		for (SearchHit searchHit : searchHits) {
			dummyEntityCache.removeResult(SearchHitImpl.class, searchHit);
		}
	}

	@Override
	public void clearCache(Set<Serializable> primaryKeys) {
		dummyFinderCache.clearCache(SearchHitImpl.class);

		for (Serializable primaryKey : primaryKeys) {
			dummyEntityCache.removeResult(SearchHitImpl.class, primaryKey);
		}
	}

	/**
	 * Creates a new search hit with the primary key. Does not add the search hit to the database.
	 *
	 * @param searchHitId the primary key for the new search hit
	 * @return the new search hit
	 */
	@Override
	public SearchHit create(long searchHitId) {
		SearchHit searchHit = new SearchHitImpl();

		searchHit.setNew(true);
		searchHit.setPrimaryKey(searchHitId);

		searchHit.setCompanyId(CompanyThreadLocal.getCompanyId());

		return searchHit;
	}

	/**
	 * Removes the search hit with the primary key from the database. Also notifies the appropriate model listeners.
	 *
	 * @param searchHitId the primary key of the search hit
	 * @return the search hit that was removed
	 * @throws NoSuchSearchHitException if a search hit with the primary key could not be found
	 */
	@Override
	public SearchHit remove(long searchHitId) throws NoSuchSearchHitException {
		return remove((Serializable)searchHitId);
	}

	/**
	 * Removes the search hit with the primary key from the database. Also notifies the appropriate model listeners.
	 *
	 * @param primaryKey the primary key of the search hit
	 * @return the search hit that was removed
	 * @throws NoSuchSearchHitException if a search hit with the primary key could not be found
	 */
	@Override
	public SearchHit remove(Serializable primaryKey)
		throws NoSuchSearchHitException {

		Session session = null;

		try {
			session = openSession();

			SearchHit searchHit = (SearchHit)session.get(
				SearchHitImpl.class, primaryKey);

			if (searchHit == null) {
				if (_log.isDebugEnabled()) {
					_log.debug(_NO_SUCH_ENTITY_WITH_PRIMARY_KEY + primaryKey);
				}

				throw new NoSuchSearchHitException(
					_NO_SUCH_ENTITY_WITH_PRIMARY_KEY + primaryKey);
			}

			return remove(searchHit);
		}
		catch (NoSuchSearchHitException noSuchEntityException) {
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
	protected SearchHit removeImpl(SearchHit searchHit) {
		Session session = null;

		try {
			session = openSession();

			if (!session.contains(searchHit)) {
				searchHit = (SearchHit)session.get(
					SearchHitImpl.class, searchHit.getPrimaryKeyObj());
			}

			if (searchHit != null) {
				session.delete(searchHit);
			}
		}
		catch (Exception exception) {
			throw processException(exception);
		}
		finally {
			closeSession(session);
		}

		if (searchHit != null) {
			clearCache(searchHit);
		}

		return searchHit;
	}

	@Override
	public SearchHit updateImpl(SearchHit searchHit) {
		boolean isNew = searchHit.isNew();

		if (!(searchHit instanceof SearchHitModelImpl)) {
			InvocationHandler invocationHandler = null;

			if (ProxyUtil.isProxyClass(searchHit.getClass())) {
				invocationHandler = ProxyUtil.getInvocationHandler(searchHit);

				throw new IllegalArgumentException(
					"Implement ModelWrapper in searchHit proxy " +
						invocationHandler.getClass());
			}

			throw new IllegalArgumentException(
				"Implement ModelWrapper in custom SearchHit implementation " +
					searchHit.getClass());
		}

		SearchHitModelImpl searchHitModelImpl = (SearchHitModelImpl)searchHit;

		if (isNew && (searchHit.getCreateDate() == null)) {
			ServiceContext serviceContext =
				ServiceContextThreadLocal.getServiceContext();

			Date date = new Date();

			if (serviceContext == null) {
				searchHit.setCreateDate(date);
			}
			else {
				searchHit.setCreateDate(serviceContext.getCreateDate(date));
			}
		}

		Session session = null;

		try {
			session = openSession();

			if (isNew) {
				session.save(searchHit);
			}
			else {
				searchHit = (SearchHit)session.merge(searchHit);
			}
		}
		catch (Exception exception) {
			throw processException(exception);
		}
		finally {
			closeSession(session);
		}

		dummyEntityCache.putResult(
			SearchHitImpl.class, searchHitModelImpl, false, true);

		if (isNew) {
			searchHit.setNew(false);
		}

		searchHit.resetOriginalValues();

		return searchHit;
	}

	/**
	 * Returns the search hit with the primary key or throws a <code>com.liferay.portal.kernel.exception.NoSuchModelException</code> if it could not be found.
	 *
	 * @param primaryKey the primary key of the search hit
	 * @return the search hit
	 * @throws NoSuchSearchHitException if a search hit with the primary key could not be found
	 */
	@Override
	public SearchHit findByPrimaryKey(Serializable primaryKey)
		throws NoSuchSearchHitException {

		SearchHit searchHit = fetchByPrimaryKey(primaryKey);

		if (searchHit == null) {
			if (_log.isDebugEnabled()) {
				_log.debug(_NO_SUCH_ENTITY_WITH_PRIMARY_KEY + primaryKey);
			}

			throw new NoSuchSearchHitException(
				_NO_SUCH_ENTITY_WITH_PRIMARY_KEY + primaryKey);
		}

		return searchHit;
	}

	/**
	 * Returns the search hit with the primary key or throws a <code>NoSuchSearchHitException</code> if it could not be found.
	 *
	 * @param searchHitId the primary key of the search hit
	 * @return the search hit
	 * @throws NoSuchSearchHitException if a search hit with the primary key could not be found
	 */
	@Override
	public SearchHit findByPrimaryKey(long searchHitId)
		throws NoSuchSearchHitException {

		return findByPrimaryKey((Serializable)searchHitId);
	}

	/**
	 * Returns the search hit with the primary key or returns <code>null</code> if it could not be found.
	 *
	 * @param searchHitId the primary key of the search hit
	 * @return the search hit, or <code>null</code> if a search hit with the primary key could not be found
	 */
	@Override
	public SearchHit fetchByPrimaryKey(long searchHitId) {
		return fetchByPrimaryKey((Serializable)searchHitId);
	}

	/**
	 * Returns all the search hits.
	 *
	 * @return the search hits
	 */
	@Override
	public List<SearchHit> findAll() {
		return findAll(QueryUtil.ALL_POS, QueryUtil.ALL_POS, null);
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
	@Override
	public List<SearchHit> findAll(int start, int end) {
		return findAll(start, end, null);
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
	@Override
	public List<SearchHit> findAll(
		int start, int end, OrderByComparator<SearchHit> orderByComparator) {

		return findAll(start, end, orderByComparator, true);
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
	@Override
	public List<SearchHit> findAll(
		int start, int end, OrderByComparator<SearchHit> orderByComparator,
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

		List<SearchHit> list = null;

		if (useFinderCache) {
			list = (List<SearchHit>)dummyFinderCache.getResult(
				finderPath, finderArgs, this);
		}

		if (list == null) {
			StringBundler sb = null;
			String sql = null;

			if (orderByComparator != null) {
				sb = new StringBundler(
					2 + (orderByComparator.getOrderByFields().length * 2));

				sb.append(_SQL_SELECT_SEARCHHIT);

				appendOrderByComparator(
					sb, _ORDER_BY_ENTITY_ALIAS, orderByComparator);

				sql = sb.toString();
			}
			else {
				sql = _SQL_SELECT_SEARCHHIT;

				sql = sql.concat(SearchHitModelImpl.ORDER_BY_JPQL);
			}

			Session session = null;

			try {
				session = openSession();

				Query query = session.createQuery(sql);

				list = (List<SearchHit>)QueryUtil.list(
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
	 * Removes all the search hits from the database.
	 *
	 */
	@Override
	public void removeAll() {
		for (SearchHit searchHit : findAll()) {
			remove(searchHit);
		}
	}

	/**
	 * Returns the number of search hits.
	 *
	 * @return the number of search hits
	 */
	@Override
	public int countAll() {
		Long count = (Long)dummyFinderCache.getResult(
			_finderPathCountAll, FINDER_ARGS_EMPTY, this);

		if (count == null) {
			Session session = null;

			try {
				session = openSession();

				Query query = session.createQuery(_SQL_COUNT_SEARCHHIT);

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
		return "searchHitId";
	}

	@Override
	protected String getSelectSQL() {
		return _SQL_SELECT_SEARCHHIT;
	}

	@Override
	protected Map<String, Integer> getTableColumnsMap() {
		return SearchHitModelImpl.TABLE_COLUMNS_MAP;
	}

	/**
	 * Initializes the search hit persistence.
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

		_finderPathWithPaginationFindBySearchEventUuid = new FinderPath(
			FINDER_CLASS_NAME_LIST_WITH_PAGINATION, "findBySearchEventUuid",
			new String[] {
				String.class.getName(), Integer.class.getName(),
				Integer.class.getName(), OrderByComparator.class.getName()
			},
			new String[] {"searchEventUuid"}, true);

		_finderPathWithoutPaginationFindBySearchEventUuid = new FinderPath(
			FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION, "findBySearchEventUuid",
			new String[] {String.class.getName()},
			new String[] {"searchEventUuid"}, true);

		_finderPathCountBySearchEventUuid = new FinderPath(
			FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION, "countBySearchEventUuid",
			new String[] {String.class.getName()},
			new String[] {"searchEventUuid"}, false);

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

		SearchHitUtil.setPersistence(this);
	}

	@Deactivate
	public void deactivate() {
		SearchHitUtil.setPersistence(null);

		dummyEntityCache.removeCache(SearchHitImpl.class.getName());
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

	private static final String _SQL_SELECT_SEARCHHIT =
		"SELECT searchHit FROM SearchHit searchHit";

	private static final String _SQL_SELECT_SEARCHHIT_WHERE =
		"SELECT searchHit FROM SearchHit searchHit WHERE ";

	private static final String _SQL_COUNT_SEARCHHIT =
		"SELECT COUNT(searchHit) FROM SearchHit searchHit";

	private static final String _SQL_COUNT_SEARCHHIT_WHERE =
		"SELECT COUNT(searchHit) FROM SearchHit searchHit WHERE ";

	private static final String _ORDER_BY_ENTITY_ALIAS = "searchHit.";

	private static final String _NO_SUCH_ENTITY_WITH_PRIMARY_KEY =
		"No SearchHit exists with the primary key ";

	private static final String _NO_SUCH_ENTITY_WITH_KEY =
		"No SearchHit exists with the key {";

	private static final Log _log = LogFactoryUtil.getLog(
		SearchHitPersistenceImpl.class);

	private static final Set<String> _badColumnNames = SetUtil.fromArray(
		new String[] {"rank"});

	@Override
	protected FinderCache getFinderCache() {
		return dummyFinderCache;
	}

}