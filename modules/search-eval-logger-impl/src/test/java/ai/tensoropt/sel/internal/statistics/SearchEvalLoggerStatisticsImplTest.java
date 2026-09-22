/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
	 * These are incremented from search threads and from the message bus
	 * consumer at the same time, so a lost update would understate exactly
	 * the drop count an evaluator uses to judge whether the log is a census.
	 */
	@Test
	public void incrementsDoNotLoseUpdatesUnderConcurrency() throws Exception {
		int threadCount = 8;
		int perThread = 2000;

		ExecutorService executorService = Executors.newFixedThreadPool(
			threadCount);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(threadCount);

		for (int i = 0; i < threadCount; i++) {
			executorService.execute(
				() -> {
					try {
						startLatch.await();

						for (int j = 0; j < perThread; j++) {
							_statistics.incrementDroppedEventCount();
						}
					}
					catch (InterruptedException interruptedException) {
						Thread.currentThread().interrupt();
					}
					finally {
						doneLatch.countDown();
					}
				});
		}

		startLatch.countDown();

		assertEquals(true, doneLatch.await(30, TimeUnit.SECONDS));

		executorService.shutdownNow();

		assertEquals(
			(long)threadCount * perThread, _statistics.getDroppedEventCount());
	}

	private SearchEvalLoggerStatisticsImpl _statistics;

}
