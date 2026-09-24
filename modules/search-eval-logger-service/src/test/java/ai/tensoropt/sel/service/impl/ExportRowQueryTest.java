/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockingDetails;
import org.mockito.invocation.Invocation;

/**
 * The export's range predicates, which is where D-1 lived.
 *
 * <p>
 * An absent bound used to be substituted with
 * <code>new Timestamp(Long.MIN_VALUE)</code> or
 * <code>new Timestamp(Long.MAX_VALUE)</code>, and PostgreSQL rejects both with
 * "timestamp out of range", so the documented affordance of leaving a field
 * empty failed the whole export. The clause is now omitted instead, and the
 * thing worth pinning is that the statement and the parameters stay in step
 * while it is: a predicate without its value, or a value without its
 * predicate, is a wrong result set rather than an error.
 * </p>
 */
public class ExportRowQueryTest {

	@Test
	public void bothBoundsProduceBothPredicatesBoundInOrder()
		throws SQLException {

		ExportRowQuery exportRowQuery = new ExportRowQuery(
			_COMPANY_ID, _START_DATE, _END_DATE);

		String sql = exportRowQuery.getSQL();

		assertTrue(
			sql.contains("e.createDate >= ?"),
			"The start bound was given and its predicate is missing: " + sql);
		assertTrue(
			sql.contains("e.createDate < ?"),
			"The end bound was given and its predicate is missing: " + sql);

		PreparedStatement preparedStatement = _setParameters(exportRowQuery);

		InOrder inOrder = inOrder(preparedStatement);

		inOrder.verify(
			preparedStatement
		).setLong(
			1, _COMPANY_ID
		);
		inOrder.verify(
			preparedStatement
		).setTimestamp(
			2, new Timestamp(_START_DATE.getTime())
		);
		inOrder.verify(
			preparedStatement
		).setTimestamp(
			3, new Timestamp(_END_DATE.getTime())
		);

		verifyNoMoreInteractions(preparedStatement);
	}

	/**
	 * The bound that remains has to move to index 2. Leaving it at 3 would
	 * bind nothing to the only placeholder in the statement, which is a driver
	 * error rather than a silent one, but leaving the predicate in and the
	 * value out is not.
	 */
	@Test
	public void missingStartDateOmitsItsPredicateAndRenumbersTheRest()
		throws SQLException {

		ExportRowQuery exportRowQuery = new ExportRowQuery(
			_COMPANY_ID, null, _END_DATE);

		String sql = exportRowQuery.getSQL();

		assertFalse(
			sql.contains("e.createDate >= ?"),
			"An absent start bound must not leave a predicate behind: " + sql);
		assertTrue(
			sql.contains("e.createDate < ?"),
			"The end bound was given and its predicate is missing: " + sql);

		PreparedStatement preparedStatement = _setParameters(exportRowQuery);

		verify(preparedStatement).setLong(1, _COMPANY_ID);
		verify(
			preparedStatement
		).setTimestamp(
			2, new Timestamp(_END_DATE.getTime())
		);

		verifyNoMoreInteractions(preparedStatement);
	}

	@Test
	public void missingEndDateOmitsItsPredicate() throws SQLException {
		ExportRowQuery exportRowQuery = new ExportRowQuery(
			_COMPANY_ID, _START_DATE, null);

		String sql = exportRowQuery.getSQL();

		assertTrue(
			sql.contains("e.createDate >= ?"),
			"The start bound was given and its predicate is missing: " + sql);
		assertFalse(
			sql.contains("e.createDate < ?"),
			"An absent end bound must not leave a predicate behind: " + sql);

		PreparedStatement preparedStatement = _setParameters(exportRowQuery);

		verify(preparedStatement).setLong(1, _COMPANY_ID);
		verify(
			preparedStatement
		).setTimestamp(
			2, new Timestamp(_START_DATE.getTime())
		);

		verifyNoMoreInteractions(preparedStatement);
	}

	/**
	 * The case the export screen's own help text offers, and the one that
	 * failed: no date predicate at all, and no timestamp bound anywhere.
	 */
	@Test
	public void noBoundsLeaveTheRangeOutEntirely() throws SQLException {
		ExportRowQuery exportRowQuery = new ExportRowQuery(
			_COMPANY_ID, null, null);

		String sql = exportRowQuery.getSQL();

		assertFalse(
			sql.contains("e.createDate >="),
			"An unbounded export must carry no start predicate: " + sql);
		assertFalse(
			sql.contains("e.createDate <"),
			"An unbounded export must carry no end predicate: " + sql);

		PreparedStatement preparedStatement = _setParameters(exportRowQuery);

		verify(preparedStatement).setLong(1, _COMPANY_ID);

		verifyNoMoreInteractions(preparedStatement);
	}

	/**
	 * The invariant that makes the other cases safe, asserted directly for
	 * every combination: the indices bound are exactly 1 to n, once each,
	 * where n is the number of placeholders in the statement.
	 *
	 * <p>
	 * Counting the calls alone would not do it. That is cardinality, and it
	 * accepts two parameters bound to the same index, or an index skipped and
	 * another repeated, which is a wrong result set rather than an error. The
	 * indices are read back off the recorded invocations instead.
	 * </p>
	 */
	@Test
	public void everyPlaceholderIsBoundExactlyOnceForEveryCombinationOfBounds()
		throws SQLException {

		Date[] startDates = {null, _START_DATE};
		Date[] endDates = {null, _END_DATE};

		for (Date startDate : startDates) {
			for (Date endDate : endDates) {
				ExportRowQuery exportRowQuery = new ExportRowQuery(
					_COMPANY_ID, startDate, endDate);

				String sql = exportRowQuery.getSQL();

				PreparedStatement preparedStatement = _setParameters(
					exportRowQuery);

				List<Integer> expectedIndexes = new ArrayList<>();

				for (int index = 1; index <= _countPlaceholders(sql); index++) {
					expectedIndexes.add(index);
				}

				assertEquals(
					expectedIndexes, _getBoundIndexes(preparedStatement),
					"The statement and its parameters disagree for start " +
						startDate + " and end " + endDate + ": " + sql);
			}
		}
	}

	/**
	 * Ordering is what lets the writer hold only one event's hits at a time
	 * (DESIGN.md 6.1), so it has to survive whichever predicates were left
	 * out, and it has to stay at the end of the statement.
	 */
	@Test
	public void orderByIsTheLastClauseWhicheverBoundsWereGiven() {
		Date[] startDates = {null, _START_DATE};
		Date[] endDates = {null, _END_DATE};

		for (Date startDate : startDates) {
			for (Date endDate : endDates) {
				ExportRowQuery exportRowQuery = new ExportRowQuery(
					_COMPANY_ID, startDate, endDate);

				String sql = exportRowQuery.getSQL();

				assertTrue(
					sql.endsWith("order by e.createDate, e.uuid_, h.rank_"),
					"The ordering has to close the statement: " + sql);
			}
		}
	}

	/**
	 * The indices each parameter was bound to, in the order they were bound,
	 * read off the recorded calls so the assertion does not have to know which
	 * overload carried each one.
	 */
	private List<Integer> _getBoundIndexes(
		PreparedStatement preparedStatement) {

		MockingDetails mockingDetails = mockingDetails(preparedStatement);

		Collection<Invocation> invocations = mockingDetails.getInvocations();

		List<Integer> indexes = new ArrayList<>();

		for (Invocation invocation : invocations) {
			indexes.add(invocation.getArgument(0));
		}

		return indexes;
	}

	private int _countPlaceholders(String sql) {
		int count = 0;

		for (int i = 0; i < sql.length(); i++) {
			if (sql.charAt(i) == '?') {
				count++;
			}
		}

		return count;
	}

	private PreparedStatement _setParameters(ExportRowQuery exportRowQuery)
		throws SQLException {

		PreparedStatement preparedStatement = mock(PreparedStatement.class);

		exportRowQuery.setParameters(preparedStatement);

		return preparedStatement;
	}

	private static final long _COMPANY_ID = 14396631550151L;

	private static final Date _END_DATE = new Date(1758585600000L);

	private static final Date _START_DATE = new Date(1755993600000L);

}
