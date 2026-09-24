/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.statistics;

import ai.tensoropt.sel.api.SearchEvalLoggerStatistics;

import java.util.concurrent.atomic.AtomicLong;

import org.osgi.service.component.annotations.Component;

/**
 * Process-wide counters behind {@link SearchEvalLoggerStatistics}.
 *
 * <p>
 * Registered under the concrete type as well as the published interface, so the
 * components that increment these counters can reach the mutators while every
 * reader outside this bundle sees only the read-only contract.
 * </p>
 */
@Component(
	service = {
		SearchEvalLoggerStatistics.class, SearchEvalLoggerStatisticsImpl.class
	}
)
public class SearchEvalLoggerStatisticsImpl
	implements SearchEvalLoggerStatistics {

	@Override
	public long getDispatchedEventCount() {
		return _dispatchedEventCount.get();
	}

	@Override
	public long getDroppedEventCount() {
		return _droppedEventCount.get();
	}

	@Override
	public long getPersistedEventCount() {
		return _persistedEventCount.get();
	}

	@Override
	public long getObservedSearchCount() {
		return _observedSearchCount.get();
	}

	@Override
	public long getKeywordSearchCount() {
		return _keywordSearchCount.get();
	}

	@Override
	public long getAdmittedSearchCount() {
		return _admittedSearchCount.get();
	}

	public void incrementObservedSearchCount() {
		_observedSearchCount.incrementAndGet();
	}

	public void incrementKeywordSearchCount() {
		_keywordSearchCount.incrementAndGet();
	}

	public void incrementAdmittedSearchCount() {
		_admittedSearchCount.incrementAndGet();
	}

	public void incrementDispatchedEventCount() {
		_dispatchedEventCount.incrementAndGet();
	}

	public void incrementDroppedEventCount() {
		_droppedEventCount.incrementAndGet();
	}

	public void incrementPersistedEventCount() {
		_persistedEventCount.incrementAndGet();
	}

	/**
	 * Moves an event already counted as dispatched to dropped. The dispatcher
	 * counts an event as dispatched before handing it over, because a full
	 * queue rejects it synchronously inside that same call and the send still
	 * returns normally. Counting after the send would count a rejected event
	 * as both.
	 */
	public void recordDispatchRejected() {
		_dispatchedEventCount.decrementAndGet();
		_droppedEventCount.incrementAndGet();
	}

	private final AtomicLong _admittedSearchCount = new AtomicLong();
	private final AtomicLong _dispatchedEventCount = new AtomicLong();
	private final AtomicLong _keywordSearchCount = new AtomicLong();
	private final AtomicLong _observedSearchCount = new AtomicLong();
	private final AtomicLong _droppedEventCount = new AtomicLong();
	private final AtomicLong _persistedEventCount = new AtomicLong();

}
