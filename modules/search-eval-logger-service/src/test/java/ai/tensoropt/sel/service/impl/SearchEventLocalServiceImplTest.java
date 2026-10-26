/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.dao.jdbc.CurrentConnectionUtil;

import ai.tensoropt.sel.service.persistence.SearchEventPersistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * F2-27: the cursor scaffolding {@code _streamQuery} shares between
 * {@code forEachExportRow} and {@code forEachZeroResultFirstPageQuery}, and
 * the null-skip that method's one row-level decision makes.
 *
 * <p>
 * Every test routes through {@link ServiceConnections}'s current-connection
 * resolver seam rather than a real DataSource, which also means the
 * "open its own connection" branch of {@code withConnection} is covered by
 * {@link ServiceConnectionsTest} instead of here.
 * </p>
 */
public class SearchEventLocalServiceImplTest {

	@BeforeEach
	public void setUp() throws SQLException {
		_connection = mock(Connection.class);
		_preparedStatement = mock(PreparedStatement.class);
		_resultSet = mock(ResultSet.class);

		when(
			_connection.prepareStatement(anyString())
		).thenReturn(
			_preparedStatement
		);
		when(_preparedStatement.executeQuery()).thenReturn(_resultSet);

		ServiceConnections.setCurrentConnectionResolver(ds -> _connection);

		_searchEventLocalServiceImpl = new _TestableSearchEventLocalServiceImpl();

		SearchEventPersistence searchEventPersistence = mock(
			SearchEventPersistence.class);

		when(searchEventPersistence.getDataSource()).thenReturn(null);

		_searchEventLocalServiceImpl.setSearchEventPersistenceForTesting(
			searchEventPersistence);
	}

	@AfterEach
	public void tearDown() {
		ServiceConnections.setCurrentConnectionResolver(
			CurrentConnectionUtil::getConnection);
	}

	@Test
	public void autocommitIsRestoredAfterTheRowConsumerThrows()
		throws Exception {

		when(_connection.getAutoCommit()).thenReturn(true);
		when(_resultSet.next()).thenReturn(true, false);

		assertThrows(
			RuntimeException.class,
			() -> _searchEventLocalServiceImpl.forEachExportRow(
				1L, null, null,
				resultSet -> {
					throw new RuntimeException("boom");
				}));

		verify(_connection).setAutoCommit(false);
		verify(_connection).setAutoCommit(true);
	}

	@Test
	public void autocommitAlreadyFalseIsNeverTouched() throws Exception {
		when(_connection.getAutoCommit()).thenReturn(false);
		when(_resultSet.next()).thenReturn(false);

		_searchEventLocalServiceImpl.forEachExportRow(
			1L, null, null, resultSet -> { });

		verify(_connection, never()).setAutoCommit(anyBoolean());
	}

	/**
	 * The export's cursor is unbounded; only the zero-result panel's two
	 * queries ever get a timeout scheduled against them. Actually observing
	 * PreparedStatement#cancel() would need the test to wait out the real
	 * delayedExecutor, which is not worth the flakiness; whether a timeout
	 * is scheduled at all is the testable, non-flaky half of "cancelled
	 * after a normal run".
	 */
	@Test
	public void theExportCursorSchedulesNoQueryTimeout() throws Exception {
		when(_connection.getAutoCommit()).thenReturn(false);
		when(_resultSet.next()).thenReturn(false);

		_searchEventLocalServiceImpl.forEachExportRow(
			1L, null, null, resultSet -> { });

		verify(_preparedStatement, never()).setQueryTimeout(anyInt());
	}

	@Test
	public void theZeroResultQueryIsBoundedByThePanelTimeout() throws Exception {
		when(_connection.getAutoCommit()).thenReturn(false);
		when(_resultSet.next()).thenReturn(false);

		_searchEventLocalServiceImpl.forEachZeroResultFirstPageQuery(
			1L, queryText -> { });

		verify(_preparedStatement).setQueryTimeout(10);
	}

	@Test
	public void aNullQueryTextRowIsSkipped() throws Exception {
		when(_connection.getAutoCommit()).thenReturn(false);
		when(_resultSet.next()).thenReturn(true, false);
		when(_resultSet.getString(1)).thenReturn(null);

		boolean[] invoked = {false};

		_searchEventLocalServiceImpl.forEachZeroResultFirstPageQuery(
			1L, queryText -> invoked[0] = true);

		assertFalse(invoked[0]);
	}

	@Test
	public void aNonNullQueryTextRowReachesTheConsumer() throws Exception {
		when(_connection.getAutoCommit()).thenReturn(false);
		when(_resultSet.next()).thenReturn(true, false);
		when(_resultSet.getString(1)).thenReturn("widget");

		String[] seen = {null};

		_searchEventLocalServiceImpl.forEachZeroResultFirstPageQuery(
			1L, queryText -> seen[0] = queryText);

		assertEquals("widget", seen[0]);
	}

	private Connection _connection;
	private PreparedStatement _preparedStatement;
	private ResultSet _resultSet;
	private _TestableSearchEventLocalServiceImpl _searchEventLocalServiceImpl;

	/**
	 * The generated base class only exposes its persistence field as
	 * protected, with no setter, so a test outside that class's own package
	 * has no access to it except through a subclass's own code, which this
	 * is.
	 */
	private static class _TestableSearchEventLocalServiceImpl
		extends SearchEventLocalServiceImpl {

		void setSearchEventPersistenceForTesting(
			SearchEventPersistence searchEventPersistence) {

			this.searchEventPersistence = searchEventPersistence;
		}

	}

}
