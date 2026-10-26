/**
 * SPDX-License-Identifier: Apache-2.0
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
	extends BasePersistenceImpl<SearchHit, NoSuchSearchHitException>
	implements SearchHitPersistence {

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

	private CollectionPersistenceFinder<SearchHit, NoSuchSearchHitException>
		_collectionPersistenceFinderBySearchEventUuid;

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

		return _collectionPersistenceFinderBySearchEventUuid.find(
			dummyFinderCache, new Object[] {searchEventUuid}, start, end,
			orderByComparator, useFinderCache);
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

		return _collectionPersistenceFinderBySearchEventUuid.findFirst(
			dummyFinderCache, new Object[] {searchEventUuid},
			orderByComparator);
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

		return _collectionPersistenceFinderBySearchEventUuid.fetchFirst(
			dummyFinderCache, new Object[] {searchEventUuid},
			orderByComparator);
	}

	/**
	 * Removes all the search hits where searchEventUuid = &#63; from the database.
	 *
	 * @param searchEventUuid the search event uuid
	 */
	@Override
	public void removeBySearchEventUuid(String searchEventUuid) {
		_collectionPersistenceFinderBySearchEventUuid.remove(
			dummyFinderCache, new Object[] {searchEventUuid});
	}

	/**
	 * Returns the number of search hits where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @return the number of matching search hits
	 */
	@Override
	public int countBySearchEventUuid(String searchEventUuid) {
		return _collectionPersistenceFinderBySearchEventUuid.count(
			dummyFinderCache, new Object[] {searchEventUuid});
	}

	private CollectionPersistenceFinder<SearchHit, NoSuchSearchHitException>
		_collectionPersistenceFinderByC_LtCreateDate;

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

		return _collectionPersistenceFinderByC_LtCreateDate.find(
			dummyFinderCache, new Object[] {companyId, createDate}, start, end,
			orderByComparator, useFinderCache);
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

		return _collectionPersistenceFinderByC_LtCreateDate.findFirst(
			dummyFinderCache, new Object[] {companyId, createDate},
			orderByComparator);
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

		return _collectionPersistenceFinderByC_LtCreateDate.fetchFirst(
			dummyFinderCache, new Object[] {companyId, createDate},
			orderByComparator);
	}

	/**
	 * Removes all the search hits where companyId = &#63; and createDate &lt; &#63; from the database.
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
	 * Returns the number of search hits where companyId = &#63; and createDate &lt; &#63;.
	 *
	 * @param companyId the company ID
	 * @param createDate the create date
	 * @return the number of matching search hits
	 */
	@Override
	public int countByC_LtCreateDate(long companyId, Date createDate) {
		return _collectionPersistenceFinderByC_LtCreateDate.count(
			dummyFinderCache, new Object[] {companyId, createDate});
	}

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

		cacheUniqueFindersResult(searchHit, false);

		if (isNew) {
			searchHit.setNew(false);
		}

		searchHit.resetOriginalValues();

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
		_collectionPersistenceFinderBySearchEventUuid =
			new CollectionPersistenceFinder<>(
				this,
				new FinderPath(
					FINDER_CLASS_NAME_LIST_WITH_PAGINATION,
					"findBySearchEventUuid",
					new String[] {
						String.class.getName(), Integer.class.getName(),
						Integer.class.getName(),
						OrderByComparator.class.getName()
					},
					new String[] {"searchEventUuid"}, true),
				new FinderPath(
					FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION,
					"findBySearchEventUuid",
					new String[] {String.class.getName()},
					new String[] {"searchEventUuid"}, 0, 1, true, null),
				new FinderPath(
					FINDER_CLASS_NAME_LIST_WITHOUT_PAGINATION,
					"countBySearchEventUuid",
					new String[] {String.class.getName()},
					new String[] {"searchEventUuid"}, 0, 1, false, null),
				_SQL_SELECT_SEARCHHIT_WHERE, _SQL_COUNT_SEARCHHIT_WHERE,
				SearchHitModelImpl.ORDER_BY_JPQL, _ENTITY_ALIAS_PREFIX, "", "",
				null,
				new FinderColumn<>(
					"searchHit.", "searchEventUuid", FinderColumn.Type.STRING,
					"=", true, true, SearchHit::getSearchEventUuid));

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
				_SQL_SELECT_SEARCHHIT_WHERE, _SQL_COUNT_SEARCHHIT_WHERE,
				SearchHitModelImpl.ORDER_BY_JPQL, _ENTITY_ALIAS_PREFIX, "", "",
				null,
				new FinderColumn<>(
					"searchHit.", "companyId", FinderColumn.Type.LONG, "=",
					true, true, SearchHit::getCompanyId),
				new FinderColumn<>(
					"searchHit.", "createDate", FinderColumn.Type.DATE, "<",
					true, true, SearchHit::getCreateDate));

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

	private static final String _ENTITY_ALIAS_PREFIX =
		SearchHitModelImpl.ENTITY_ALIAS + ".";

	private static final String _SQL_SELECT_SEARCHHIT =
		"SELECT searchHit FROM SearchHit searchHit";

	private static final String _SQL_SELECT_SEARCHHIT_WHERE =
		"SELECT searchHit FROM SearchHit searchHit WHERE ";

	private static final String _SQL_COUNT_SEARCHHIT_WHERE =
		"SELECT COUNT(searchHit) FROM SearchHit searchHit WHERE ";

	private static final Set<String> _badColumnNames = SetUtil.fromArray(
		new String[] {"rank"});

	@Override
	protected FinderCache getFinderCache() {
		return dummyFinderCache;
	}

}
// LIFERAY-SERVICE-BUILDER-HASH:1724569822