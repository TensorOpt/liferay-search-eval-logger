/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SearchEvalLoggerStatisticsImplTest {

	@BeforeEach
	public void setUp() {
		_statistics = new SearchEvalLoggerStatisticsImpl();
	}

	@Test
	public void countersStartAtZero() {
		assertEquals(0, _statistics.getObservedSearchCount());
		assertEquals(0, _statistics.getKeywordSearchCount());
		assertEquals(0, _statistics.getAdmittedSearchCount());
		assertEquals(0, _statistics.getDispatchedEventCount());
		assertEquals(0, _statistics.getDroppedEventCount());
		assertEquals(0, _statistics.getPersistedEventCount());
	}

	@Test
	public void eachCounterIsIndependent() {
		_statistics.incrementObservedSearchCount();
		_statistics.incrementKeywordSearchCount();
		_statistics.incrementKeywordSearchCount();
		_statistics.incrementDroppedEventCount();

		assertEquals(1, _statistics.getObservedSearchCount());
		assertEquals(2, _statistics.getKeywordSearchCount());
		assertEquals(0, _statistics.getAdmittedSearchCount());
		assertEquals(1, _statistics.getDroppedEventCount());
		assertEquals(0, _statistics.getPersistedEventCount());
	}

	/**
	 * A rejected event is counted as dispatched before the send and then
	 * moved, so it ends up counted once, as dropped (TO-92).
	 */
	@Test
	public void aRejectedDispatchMovesFromDispatchedToDropped() {
		_statistics.incrementDispatchedEventCount();
		_statistics.incrementDispatchedEventCount();
		_statistics.recordDispatchRejected();

		assertEquals(1, _statistics.getDispatchedEventCount());
		assertEquals(1, _statistics.getDroppedEventCount());
	}

	private SearchEvalLoggerStatisticsImpl _statistics;

}
