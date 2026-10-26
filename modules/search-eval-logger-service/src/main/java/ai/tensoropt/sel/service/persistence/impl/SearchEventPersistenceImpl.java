/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.service.persistence.impl;

import ai.tensoropt.sel.exception.NoSuchSearchEventException;
import ai.tensoropt.sel.model.SearchEvent;
import ai.tensoropt.sel.model.SearchEventTable;
import ai.tensoropt.sel.model.impl.SearchEventImpl;
import ai.tensoropt.sel.model.impl.SearchEventModelImpl;
import ai.tensoropt.sel.service.persistence.SearchEventPersistence;
import ai.tensoropt.sel.service.persistence.SearchEventUtil;
import ai.tensoropt.sel.service.persistence.impl.constants.SELPersistenceConstants;

import com.liferay.portal.kernel.configuration.Configuration;
import com.liferay.portal.kernel.dao.orm.EntityCache;
import com.liferay.portal.kernel.dao.orm.FinderCache;
import com.liferay.portal.kernel.dao.orm.FinderPath;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.dao.orm.Session;
import com.liferay.portal.kernel.dao.orm.SessionFactory;
import com.liferay.portal.kernel.security.auth.CompanyThreadLocal;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.service.persistence.impl.BasePersistenceImpl;
import com.liferay.portal.kernel.service.persistence.impl.CollectionPersistenceFinder;
import com.liferay.portal.kernel.service.persistence.impl.FinderColumn;
import com.liferay.portal.kernel.util.OrderByComparator;
import com.liferay.portal.kernel.util.ProxyUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.uuid.PortalUUIDUtil;

import java.io.Serializable;

import java.lang.reflect.InvocationHandler;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	extends BasePersistenceImpl<SearchEvent, NoSuchSearchEventException>
	implements SearchEventPersistence {

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

	private CollectionPersistenceFinder<SearchEvent, NoSuchSearchEventException>
		_collectionPersistenceFinderByUuid;

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

		return _collectionPersistenceFinderByUuid.find(
			dummyFinderCache, new Object[] {uuid}, start, end,
			orderByComparator, useFinderCache);
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

		return _collectionPersistenceFinderByUuid.findFirst(
			dummyFinderCache, new Object[] {uuid}, orderByComparator);
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

		return _collectionPersistenceFinderByUuid.fetchFirst(
			dummyFinderCache, new Object[] {uuid}, orderByComparator);
	}

	/**
	 * Removes all the search events where uuid = &#63; from the database.
	 *
	 * @param uuid the uuid
	 */
	@Override
	public void removeByUuid(String uuid) {
		_collectionPersistenceFinderByUuid.remove(
			dummyFinderCache, new Object[] {uuid});
	}

	/**
	 * Returns the number of search events where uuid = &#63;.
	 *
	 * @param uuid the uuid
	 * @return the number of matching search events
	 */
	@Override
	public int countByUuid(String uuid) {
		return _collectionPersistenceFinderByUuid.count(
			dummyFinderCache, new Object[] {uuid});
	}

	private CollectionPersistenceFinder<SearchEvent, NoSuchSearchEventException>
		_collectionPersistenceFinderByUuid_C;

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

		return _collectionPersistenceFinderByUuid_C.find(
			dummyFinderCache, new Object[] {uuid, companyId}, start, end,
			orderByComparator, useFinderCache);
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

		return _collectionPersistenceFinderByUuid_C.findFirst(
			dummyFinderCache, new Object[] {uuid, companyId},
			orderByComparator);
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

		return _collectionPersistenceFinderByUuid_C.fetchFirst(
			dummyFinderCache, new Object[] {uuid, companyId},
			orderByComparator);
	}

	/**
	 * Removes all the search events where uuid = &#63; and companyId = &#63; from the database.
	 *
	 * @param uuid the uuid
	 * @param companyId the company ID
	 */
	@Override
	public void removeByUuid_C(String uuid, long companyId) {
		_collectionPersistenceFinderByUuid_C.remove(
			dummyFinderCache, new Object[] {uuid, companyId});
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
		return _collectionPersistenceFinderByUuid_C.count(
			dummyFinderCache, new Object[] {uuid, companyId});
	}

	private CollectionPersistenceFinder<SearchEvent, NoSuchSearchEventException>
		_collectionPersistenceFinderByC_LtCreateDate;

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

		return _collectionPersistenceFinderByC_LtCreateDate.find(
			dummyFinderCache, new Object[] {companyId, createDate}, start, end,
			orderByComparator, useFinderCache);
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

		return _collectionPersistenceFinderByC_LtCreateDate.findFirst(
			dummyFinderCache, new Object[] {companyId, createDate},
			orderByComparator);
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

		return _collectionPersistenceFinderByC_LtCreateDate.fetchFirst(
			dummyFinderCache, new Object[] {companyId, createDate},
			orderByComparator);
	}

	/**
	 * Removes all the search events where companyId = &#63; and createDate &lt; &#63; from the database.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 */
	@Override
	public void removeByC_LtCreateDate(long companyId, Date createDate) {
		_collectionPersistenceFinderByC_LtCreateDate.remove(
			dummyFinderCache, new Object[] {companyId, createDate});
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
		return _collectionPersistenceFinderByC_LtCreateDate.count(
			dummyFinderCache, new Object[] {companyId, createDate});
	}

	private CollectionPersistenceFinder<SearchEvent, NoSuchSearchEventException>
		_collectionPersistenceFinderByC_R_T_A;

	/**
	 * Returns an ordered range of all the search events where companyId = &#63; and requestedFrom = &#63; and totalHits = &#63; and audienceType = &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end - start</code> instances. <code>start</code> and <code>end</code> are not primary keys, they are indexes in the result set. Thus, <code>0</code> refers to the first result in the set. Setting both <code>start</code> and <code>end</code> to <code>QueryUtil#ALL_POS</code> will return the full result set. If <code>orderByComparator</code> is specified, then the query will include the given ORDER BY logic. If <code>orderByComparator</code> is absent, then the query will include the default ORDER BY logic from <code>SearchEventModelImpl</code>.
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
	@Override
	public List<SearchEvent> findByC_R_T_A(
		long companyId, int requestedFrom, long totalHits, String audienceType,
		int start, int end, OrderByComparator<SearchEvent> orderByComparator,
		boolean useFinderCache) {

		return _collectionPersistenceFinderByC_R_T_A.find(
			dummyFinderCache,
			new Object[] {companyId, requestedFrom, totalHits, audienceType},
			start, end, orderByComparator, useFinderCache);
	}

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
	@Override
	public SearchEvent findByC_R_T_A_First(
			long companyId, int requestedFrom, long totalHits,
			String audienceType,
			OrderByComparator<SearchEvent> orderByComparator)
		throws NoSuchSearchEventException {

		return _collectionPersistenceFinderByC_R_T_A.findFirst(
			dummyFinderCache,
			new Object[] {companyId, requestedFrom, totalHits, audienceType},
			orderByComparator);
	}

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
	@Override
	public SearchEvent fetchByC_R_T_A_First(
		long companyId, int requestedFrom, long totalHits, String audienceType,
		OrderByComparator<SearchEvent> orderByComparator) {

		return _collectionPersistenceFinderByC_R_T_A.fetchFirst(
			dummyFinderCache,
			new Object[] {companyId, requestedFrom, totalHits, audienceType},
			orderByComparator);
	}

	/**
	 * Removes all the search events where companyId = &#63; and requestedFrom = &#63; and totalHits = &#63; and audienceType = &#63; from the database.
	 *
	 * @param companyId the company ID
	 * @param requestedFrom the requested from
	 * @param totalHits the total hits
	 * @param audienceType the audience type
	 */
	@Override
	public void removeByC_R_T_A(
		long companyId, int requestedFrom, long totalHits,
		String audienceType) {

		_collectionPersistenceFinderByC_R_T_A.remove(
			dummyFinderCache,
			new Object[] {companyId, requestedFrom, totalHits, audienceType});
	}

	/**
	 * Returns the number of search events where companyId = &#63; and requestedFrom = &#63; and totalHits = &#63; and audienceType = &#63;.
	 *
	 * @param companyId the company ID
	 * @param requestedFrom the requested from
	 * @param totalHits the total hits
	 * @param audienceType the audience type
	 * @return the number of matching search events
	 */
	@Override
	public int countByC_R_T_A(
		long companyId, int requestedFrom, long totalHits,
		String audienceType) {

		return _collectionPersistenceFinderByC_R_T_A.count(
			dummyFinderCache,
			new Object[] {companyId, requestedFrom, totalHits, audienceType});
	}

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

		cacheUniqueFindersResult(searchEvent, false);

		if (isNew) {
			searchEvent.setNew(false);
		}

		searchEvent.resetOriginalValues();

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
		_collectionPersistenceFinderByUuid = new CollectionPersistenceFinder<>(
			this,
			new FinderPath(
				FINDER_CLASS_NAME_LIST_WITH_PAGINATION, "findByUuid",
				new String[] {
					String.class.getName(), Integer.class.getName(),
					Integer.class.getName(), OrderByComparator.class.getName()
				},
				new String[] {"uuid_"}, true),
			new FinderPath(
				FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION, "findByUuid",
				new String[] {String.class.getName()}, new String[] {"uuid_"},
				0, 1, true, null),
			new FinderPath(
				FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION, "countByUuid",
				new String[] {String.class.getName()}, new String[] {"uuid_"},
				0, 1, false, null),
			_SQL_SELECT_SEARCHEVENT_WHERE, _SQL_COUNT_SEARCHEVENT_WHERE,
			SearchEventModelImpl.ORDER_BY_JPQL, _ENTITY_ALIAS_PREFIX, "", "",
			null,
			new FinderColumn<>(
				"searchEvent.", "uuid", "uuid_", FinderColumn.Type.STRING, "=",
				true, true, SearchEvent::getUuid));

		_collectionPersistenceFinderByUuid_C =
			new CollectionPersistenceFinder<>(
				this,
				new FinderPath(
					FINDER_CLASS_NAME_LIST_WITH_PAGINATION, "findByUuid_C",
					new String[] {
						String.class.getName(), Long.class.getName(),
						Integer.class.getName(), Integer.class.getName(),
						OrderByComparator.class.getName()
					},
					new String[] {"uuid_", "companyId"}, true),
				new FinderPath(
					FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION, "findByUuid_C",
					new String[] {String.class.getName(), Long.class.getName()},
					new String[] {"uuid_", "companyId"}, 0, 1, true, null),
				new FinderPath(
					FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION, "countByUuid_C",
					new String[] {String.class.getName(), Long.class.getName()},
					new String[] {"uuid_", "companyId"}, 0, 1, false, null),
				_SQL_SELECT_SEARCHEVENT_WHERE, _SQL_COUNT_SEARCHEVENT_WHERE,
				SearchEventModelImpl.ORDER_BY_JPQL, _ENTITY_ALIAS_PREFIX, "",
				"", null,
				new FinderColumn<>(
					"searchEvent.", "uuid", "uuid_", FinderColumn.Type.STRING,
					"=", true, true, SearchEvent::getUuid),
				new FinderColumn<>(
					"searchEvent.", "companyId", FinderColumn.Type.LONG, "=",
					true, true, SearchEvent::getCompanyId));

		_collectionPersistenceFinderByC_LtCreateDate =
			new CollectionPersistenceFinder<>(
				this,
				new FinderPath(
					FINDER_CLASS_NAME_LIST_WITH_PAGINATION,
					"findByC_LtCreateDate",
					new String[] {
						Long.class.getName(), Date.class.getName(),
						Integer.class.getName(), Integer.class.getName(),
						OrderByComparator.class.getName()
					},
					new String[] {"companyId", "createDate"}, true),
				null,
				new FinderPath(
					FINDER_CLASS_NAME_LIST_WITH_PAGINATION,
					"countByC_LtCreateDate",
					new String[] {Long.class.getName(), Date.class.getName()},
					new String[] {"companyId", "createDate"}, false),
				_SQL_SELECT_SEARCHEVENT_WHERE, _SQL_COUNT_SEARCHEVENT_WHERE,
				SearchEventModelImpl.ORDER_BY_JPQL, _ENTITY_ALIAS_PREFIX, "",
				"", null,
				new FinderColumn<>(
					"searchEvent.", "companyId", FinderColumn.Type.LONG, "=",
					true, true, SearchEvent::getCompanyId),
				new FinderColumn<>(
					"searchEvent.", "createDate", FinderColumn.Type.DATE, "<",
					true, true, SearchEvent::getCreateDate));

		_collectionPersistenceFinderByC_R_T_A =
			new CollectionPersistenceFinder<>(
				this,
				new FinderPath(
					FINDER_CLASS_NAME_LIST_WITH_PAGINATION, "findByC_R_T_A",
					new String[] {
						Long.class.getName(), Integer.class.getName(),
						Long.class.getName(), String.class.getName(),
						Integer.class.getName(), Integer.class.getName(),
						OrderByComparator.class.getName()
					},
					new String[] {
						"companyId", "requestedFrom", "totalHits",
						"audienceType"
					},
					true),
				new FinderPath(
					FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION, "findByC_R_T_A",
					new String[] {
						Long.class.getName(), Integer.class.getName(),
						Long.class.getName(), String.class.getName()
					},
					new String[] {
						"companyId", "requestedFrom", "totalHits",
						"audienceType"
					},
					0, 8, true, null),
				new FinderPath(
					FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION, "countByC_R_T_A",
					new String[] {
						Long.class.getName(), Integer.class.getName(),
						Long.class.getName(), String.class.getName()
					},
					new String[] {
						"companyId", "requestedFrom", "totalHits",
						"audienceType"
					},
					0, 8, false, null),
				_SQL_SELECT_SEARCHEVENT_WHERE, _SQL_COUNT_SEARCHEVENT_WHERE,
				SearchEventModelImpl.ORDER_BY_JPQL, _ENTITY_ALIAS_PREFIX, "",
				"", null,
				new FinderColumn<>(
					"searchEvent.", "companyId", FinderColumn.Type.LONG, "=",
					true, true, SearchEvent::getCompanyId),
				new FinderColumn<>(
					"searchEvent.", "requestedFrom", FinderColumn.Type.INTEGER,
					"=", true, true, SearchEvent::getRequestedFrom),
				new FinderColumn<>(
					"searchEvent.", "totalHits", FinderColumn.Type.LONG, "=",
					true, true, SearchEvent::getTotalHits),
				new FinderColumn<>(
					"searchEvent.", "audienceType", FinderColumn.Type.STRING,
					"=", true, true, SearchEvent::getAudienceType));

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

	private static final String _ENTITY_ALIAS_PREFIX =
		SearchEventModelImpl.ENTITY_ALIAS + ".";

	private static final String _SQL_SELECT_SEARCHEVENT =
		"SELECT searchEvent FROM SearchEvent searchEvent";

	private static final String _SQL_SELECT_SEARCHEVENT_WHERE =
		"SELECT searchEvent FROM SearchEvent searchEvent WHERE ";

	private static final String _SQL_COUNT_SEARCHEVENT_WHERE =
		"SELECT COUNT(searchEvent) FROM SearchEvent searchEvent WHERE ";

	private static final Set<String> _badColumnNames = SetUtil.fromArray(
		new String[] {"uuid"});

	@Override
	protected FinderCache getFinderCache() {
		return dummyFinderCache;
	}

}
// LIFERAY-SERVICE-BUILDER-HASH:149632572