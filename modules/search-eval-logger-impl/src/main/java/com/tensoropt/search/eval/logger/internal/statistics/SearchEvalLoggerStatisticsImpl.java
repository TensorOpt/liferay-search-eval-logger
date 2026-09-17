/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.internal.statistics;

import com.tensoropt.search.eval.logger.api.SearchEvalLoggerStatistics;

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

	public void incrementDispatchedEventCount() {
		_dispatchedEventCount.incrementAndGet();
	}

	public void incrementDroppedEventCount() {
		_droppedEventCount.incrementAndGet();
	}

	public void incrementPersistedEventCount() {
		_persistedEventCount.incrementAndGet();
	}

	private final AtomicLong _dispatchedEventCount = new AtomicLong();
	private final AtomicLong _droppedEventCount = new AtomicLong();
	private final AtomicLong _persistedEventCount = new AtomicLong();

}
