/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.service.impl;

import com.liferay.portal.aop.AopService;

import ai.tensoropt.sel.service.base.SearchHitLocalServiceBaseImpl;

import java.util.Date;

import org.osgi.service.component.annotations.Component;

@Component(
	property = "model.class.name=ai.tensoropt.sel.model.SearchHit",
	service = AopService.class
)
public class SearchHitLocalServiceImpl extends SearchHitLocalServiceBaseImpl {

	/**
	 * Deletes every hit older than the cutoff for one virtual instance,
	 * in a single statement, and returns how many rows went.
	 *
	 * <p>
	 * Service Builder's generated <code>removeByC_LtCreateDate</code> is not
	 * usable for this: it loads every matching row with
	 * <code>QueryUtil.ALL_POS</code> and deletes them one at a time, so a
	 * backlog would be pulled into the heap before anything was freed. This
	 * goes to JDBC instead, which is safe here specifically because both
	 * entities are <code>cache-enabled="false"</code> and nothing listens for
	 * their removal, so no cache is left holding rows that no longer exist.
	 * </p>
	 */
	public int deleteByCompanyIdAndCreateDateBefore(
		long companyId, Date cutoffDate) {

		return ServiceConnections.deleteByCompanyIdAndCreateDateBefore(
			searchHitPersistence.getDataSource(), "SEL_SearchHit", companyId,
			cutoffDate);
	}

}
