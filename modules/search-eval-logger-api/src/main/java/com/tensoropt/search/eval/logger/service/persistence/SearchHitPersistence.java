/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.tensoropt.search.eval.logger.service.persistence;

import com.liferay.portal.kernel.service.persistence.BasePersistence;

import com.tensoropt.search.eval.logger.exception.NoSuchSearchHitException;
import com.tensoropt.search.eval.logger.model.SearchHit;

import org.osgi.annotation.versioning.ProviderType;

/**
 * The persistence interface for the search hit service.
 *
 * <p>
 * Caching information and settings can be found in <code>portal.properties</code>
 * </p>
 *
 * @author Brian Wing Shun Chan
 * @see SearchHitUtil
 * @generated
 */
@ProviderType
public interface SearchHitPersistence extends BasePersistence<SearchHit> {

	/*
	 * NOTE FOR DEVELOPERS:
	 *
	 * Never modify or reference this interface directly. Always use {@link SearchHitUtil} to access the search hit persistence. Modify <code>service.xml</code> and rerun ServiceBuilder to regenerate this interface.
	 */

	/**
	 * Returns all the search hits where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @return the matching search hits
	 */
	public java.util.List<SearchHit> findBySearchEventUuid(
		String searchEventUuid);

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
	public java.util.List<SearchHit> findBySearchEventUuid(
		String searchEventUuid, int start, int end);

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
	public java.util.List<SearchHit> findBySearchEventUuid(
		String searchEventUuid, int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<SearchHit>
			orderByComparator);

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
	public java.util.List<SearchHit> findBySearchEventUuid(
		String searchEventUuid, int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<SearchHit>
			orderByComparator,
		boolean useFinderCache);

	/**
	 * Returns the first search hit in the ordered set where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search hit
	 * @throws NoSuchSearchHitException if a matching search hit could not be found
	 */
	public SearchHit findBySearchEventUuid_First(
			String searchEventUuid,
			com.liferay.portal.kernel.util.OrderByComparator<SearchHit>
				orderByComparator)
		throws NoSuchSearchHitException;

	/**
	 * Returns the first search hit in the ordered set where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the first matching search hit, or <code>null</code> if a matching search hit could not be found
	 */
	public SearchHit fetchBySearchEventUuid_First(
		String searchEventUuid,
		com.liferay.portal.kernel.util.OrderByComparator<SearchHit>
			orderByComparator);

	/**
	 * Returns the last search hit in the ordered set where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search hit
	 * @throws NoSuchSearchHitException if a matching search hit could not be found
	 */
	public SearchHit findBySearchEventUuid_Last(
			String searchEventUuid,
			com.liferay.portal.kernel.util.OrderByComparator<SearchHit>
				orderByComparator)
		throws NoSuchSearchHitException;

	/**
	 * Returns the last search hit in the ordered set where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the last matching search hit, or <code>null</code> if a matching search hit could not be found
	 */
	public SearchHit fetchBySearchEventUuid_Last(
		String searchEventUuid,
		com.liferay.portal.kernel.util.OrderByComparator<SearchHit>
			orderByComparator);

	/**
	 * Returns the search hits before and after the current search hit in the ordered set where searchEventUuid = &#63;.
	 *
	 * @param searchHitId the primary key of the current search hit
	 * @param searchEventUuid the search event uuid
	 * @param orderByComparator the comparator to order the set by (optionally <code>null</code>)
	 * @return the previous, current, and next search hit
	 * @throws NoSuchSearchHitException if a search hit with the primary key could not be found
	 */
	public SearchHit[] findBySearchEventUuid_PrevAndNext(
			long searchHitId, String searchEventUuid,
			com.liferay.portal.kernel.util.OrderByComparator<SearchHit>
				orderByComparator)
		throws NoSuchSearchHitException;

	/**
	 * Removes all the search hits where searchEventUuid = &#63; from the database.
	 *
	 * @param searchEventUuid the search event uuid
	 */
	public void removeBySearchEventUuid(String searchEventUuid);

	/**
	 * Returns the number of search hits where searchEventUuid = &#63;.
	 *
	 * @param searchEventUuid the search event uuid
	 * @return the number of matching search hits
	 */
	public int countBySearchEventUuid(String searchEventUuid);

	/**
	 * Caches the search hit in the entity cache if it is enabled.
	 *
	 * @param searchHit the search hit
	 */
	public void cacheResult(SearchHit searchHit);

	/**
	 * Caches the search hits in the entity cache if it is enabled.
	 *
	 * @param searchHits the search hits
	 */
	public void cacheResult(java.util.List<SearchHit> searchHits);

	/**
	 * Creates a new search hit with the primary key. Does not add the search hit to the database.
	 *
	 * @param searchHitId the primary key for the new search hit
	 * @return the new search hit
	 */
	public SearchHit create(long searchHitId);

	/**
	 * Removes the search hit with the primary key from the database. Also notifies the appropriate model listeners.
	 *
	 * @param searchHitId the primary key of the search hit
	 * @return the search hit that was removed
	 * @throws NoSuchSearchHitException if a search hit with the primary key could not be found
	 */
	public SearchHit remove(long searchHitId) throws NoSuchSearchHitException;

	public SearchHit updateImpl(SearchHit searchHit);

	/**
	 * Returns the search hit with the primary key or throws a <code>NoSuchSearchHitException</code> if it could not be found.
	 *
	 * @param searchHitId the primary key of the search hit
	 * @return the search hit
	 * @throws NoSuchSearchHitException if a search hit with the primary key could not be found
	 */
	public SearchHit findByPrimaryKey(long searchHitId)
		throws NoSuchSearchHitException;

	/**
	 * Returns the search hit with the primary key or returns <code>null</code> if it could not be found.
	 *
	 * @param searchHitId the primary key of the search hit
	 * @return the search hit, or <code>null</code> if a search hit with the primary key could not be found
	 */
	public SearchHit fetchByPrimaryKey(long searchHitId);

	/**
	 * Returns all the search hits.
	 *
	 * @return the search hits
	 */
	public java.util.List<SearchHit> findAll();

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
	public java.util.List<SearchHit> findAll(int start, int end);

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
	public java.util.List<SearchHit> findAll(
		int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<SearchHit>
			orderByComparator);

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
	public java.util.List<SearchHit> findAll(
		int start, int end,
		com.liferay.portal.kernel.util.OrderByComparator<SearchHit>
			orderByComparator,
		boolean useFinderCache);

	/**
	 * Removes all the search hits from the database.
	 */
	public void removeAll();

	/**
	 * Returns the number of search hits.
	 *
	 * @return the number of search hits
	 */
	public int countAll();

}