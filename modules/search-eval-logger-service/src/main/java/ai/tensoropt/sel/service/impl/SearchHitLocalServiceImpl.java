/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package ai.tensoropt.sel.service.impl;

import com.liferay.portal.aop.AopService;
import com.liferay.portal.kernel.dao.jdbc.CurrentConnectionUtil;
import com.liferay.portal.kernel.exception.SystemException;

import ai.tensoropt.sel.service.base.SearchHitLocalServiceBaseImpl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

import java.util.Date;

import javax.sql.DataSource;

import org.osgi.service.component.annotations.Component;

/**
 * @author Brian Wing Shun Chan
 */
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
	 *
	 * <p>
	 * The connection is the one bound to the current transaction where there
	 * is one, matching what the generated <code>runSQL</code> does, so the
	 * delete commits or rolls back with its caller.
	 * </p>
	 */
	public int deleteByCompanyIdAndCreateDateBefore(
		long companyId, Date cutoffDate) {

		DataSource dataSource = searchHitPersistence.getDataSource();

		Connection currentConnection = CurrentConnectionUtil.getConnection(
			dataSource);

		try {
			if (currentConnection != null) {
				return _delete(currentConnection, companyId, cutoffDate);
			}

			try (Connection connection = dataSource.getConnection()) {
				return _delete(connection, companyId, cutoffDate);
			}
		}
		catch (Exception exception) {
			throw new SystemException(exception);
		}
	}

	private int _delete(
			Connection connection, long companyId, Date cutoffDate)
		throws Exception {

		try (PreparedStatement preparedStatement = connection.prepareStatement(
				_DELETE_SQL)) {

			preparedStatement.setLong(1, companyId);
			preparedStatement.setTimestamp(
				2, new Timestamp(cutoffDate.getTime()));

			return preparedStatement.executeUpdate();
		}
	}

	private static final String _DELETE_SQL =
		"delete from SEL_SearchHit where companyId = ? and createDate < ?";

}
