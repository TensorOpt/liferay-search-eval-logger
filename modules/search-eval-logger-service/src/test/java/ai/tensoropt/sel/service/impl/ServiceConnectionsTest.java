/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.dao.jdbc.CurrentConnectionUtil;
import com.liferay.portal.kernel.exception.SystemException;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * F2-27: {@code withConnection}'s two branches (a connection already bound to
 * the current transaction, or none) and its SQLException wrapping.
 * CurrentConnectionUtil's static field is never set outside a running
 * portal, so calling it for real throws a NullPointerException rather than
 * the "nothing bound" null every production call site relies on; these tests
 * substitute a resolver through setCurrentConnectionResolver, and restore
 * the real one afterward since that state is shared across every test in
 * this JVM.
 */
public class ServiceConnectionsTest {

	@AfterEach
	public void tearDown() {
		ServiceConnections.setCurrentConnectionResolver(
			CurrentConnectionUtil::getConnection);
	}

	@Test
	public void aBoundTransactionConnectionIsUsedDirectlyAndNeverClosed()
		throws Exception {

		DataSource dataSource = mock(DataSource.class);
		Connection boundConnection = mock(Connection.class);

		ServiceConnections.setCurrentConnectionResolver(
			ds -> boundConnection);

		String result = ServiceConnections.withConnection(
			dataSource, connection -> {
				assertSame(boundConnection, connection);

				return "ok";
			});

		assertEquals("ok", result);

		verify(dataSource, never()).getConnection();
		verify(boundConnection, never()).close();
	}

	@Test
	public void noBoundConnectionOpensAndClosesItsOwn() throws Exception {
		DataSource dataSource = mock(DataSource.class);
		Connection ownConnection = mock(Connection.class);

		when(dataSource.getConnection()).thenReturn(ownConnection);

		ServiceConnections.setCurrentConnectionResolver(ds -> null);

		String result = ServiceConnections.withConnection(
			dataSource, connection -> {
				assertSame(ownConnection, connection);

				return "ok";
			});

		assertEquals("ok", result);

		verify(ownConnection).close();
	}

	@Test
	public void anSQLExceptionBecomesASystemException() {
		DataSource dataSource = mock(DataSource.class);

		ServiceConnections.setCurrentConnectionResolver(
			ds -> {
				throw new SQLException("boom");
			});

		assertThrows(
			SystemException.class,
			() -> ServiceConnections.withConnection(
				dataSource, connection -> "unreachable"));
	}

	@Test
	public void anSQLExceptionFromTheConnectionFunctionAlsoBecomesASystemException()
		throws Exception {

		DataSource dataSource = mock(DataSource.class);
		Connection boundConnection = mock(Connection.class);

		ServiceConnections.setCurrentConnectionResolver(
			ds -> boundConnection);

		assertThrows(
			SystemException.class,
			() -> ServiceConnections.withConnection(
				dataSource,
				connection -> {
					throw new SQLException("boom");
				}));
	}

}
