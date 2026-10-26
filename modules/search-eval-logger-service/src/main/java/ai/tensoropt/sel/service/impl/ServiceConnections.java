/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.service.impl;

import com.liferay.petra.function.UnsafeFunction;
import com.liferay.portal.kernel.dao.jdbc.CurrentConnectionUtil;
import com.liferay.portal.kernel.exception.SystemException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import java.util.Date;

import javax.sql.DataSource;

/**
 * The raw JDBC access both local services share.
 *
 * <p>
 * The connection is the one bound to the current transaction where there is
 * one, matching what the generated <code>runSQL</code> does, so a statement
 * commits or rolls back with its caller. Otherwise one is opened and closed
 * around the call. Either way an SQLException surfaces as the SystemException
 * a local service is expected to throw, and any other exception the function
 * declares passes through unchanged.
 * </p>
 */
final class ServiceConnections {

	/**
	 * Deletes one virtual instance's rows older than the cutoff from a table
	 * whose rows carry <code>companyId</code> and <code>createDate</code>, in
	 * a single statement.
	 */
	static int deleteByCompanyIdAndCreateDateBefore(
		DataSource dataSource, String tableName, long companyId,
		Date cutoffDate) {

		return withConnection(
			dataSource,
			connection -> {
				try (PreparedStatement preparedStatement =
						connection.prepareStatement(
							"delete from " + tableName + " where companyId " +
								"= ? and createDate < ?")) {

					preparedStatement.setLong(1, companyId);
					preparedStatement.setTimestamp(
						2, new Timestamp(cutoffDate.getTime()));

					return preparedStatement.executeUpdate();
				}
			});
	}

	static <T, E extends Throwable> T withConnection(
			DataSource dataSource, ConnectionFunction<T, E> connectionFunction)
		throws E {

		try {
			Connection currentConnection = _currentConnectionResolver.apply(
				dataSource);

			if (currentConnection != null) {
				return connectionFunction.apply(currentConnection);
			}

			try (Connection connection = dataSource.getConnection()) {
				return connectionFunction.apply(connection);
			}
		}
		catch (SQLException sqlException) {
			throw new SystemException(sqlException);
		}
	}

	/**
	 * Visible for testing. <code>CurrentConnectionUtil</code>'s static field
	 * is only ever set by a running portal's own OSGi component, so a plain
	 * unit test cannot make it return anything, and calling it directly
	 * throws a <code>NullPointerException</code> rather than the "no bound
	 * transaction" <code>null</code> every real call site relies on. A test
	 * substitutes a resolver that never touches that static field, and must
	 * restore the default afterward, since this is shared, mutable state
	 * across every test in the same JVM.
	 */
	static void setCurrentConnectionResolver(
		UnsafeFunction<DataSource, Connection, SQLException>
			currentConnectionResolver) {

		_currentConnectionResolver = currentConnectionResolver;
	}

	interface ConnectionFunction<T, E extends Throwable> {

		T apply(Connection connection) throws E, SQLException;

	}

	private static UnsafeFunction<DataSource, Connection, SQLException>
		_currentConnectionResolver = CurrentConnectionUtil::getConnection;

	private ServiceConnections() {
	}

}
