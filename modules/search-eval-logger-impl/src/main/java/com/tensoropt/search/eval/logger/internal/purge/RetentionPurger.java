/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.internal.purge;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.transaction.Propagation;
import com.liferay.portal.kernel.transaction.TransactionConfig;
import com.liferay.portal.kernel.transaction.TransactionInvokerUtil;

import com.tensoropt.search.eval.logger.configuration.SearchEvalLoggerConfiguration;
import com.tensoropt.search.eval.logger.internal.configuration.SearchEvalLoggerConfigurationRegistry;
import com.tensoropt.search.eval.logger.service.SearchEventLocalService;
import com.tensoropt.search.eval.logger.service.SearchHitLocalService;

import java.time.Clock;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Deletes events and hits past the retention window (DESIGN.md 3.4).
 *
 * <p>
 * Two statements in one transaction. Hits carry their own companyId and
 * createDate, so neither delete has to resolve the other's rows first and the
 * order between them does not matter. A hit that somehow outlived its event is
 * still matched, rather than becoming a row nothing can reach.
 * </p>
 *
 * <p>
 * This replaces a batched, paused loop that deleted a hundred events and their
 * hits per pass. That design was protecting against a long single transaction,
 * which measurement did not support: on 400,000 events and 4,000,000 hits the
 * whole delete takes about thirteen seconds, and a day of the volume section
 * 4.5 projects takes under a fifth of a second. The batching cost far more
 * than it saved. Its per-pass pause meant the job slept for more than
 * ninety-seven percent of its runtime, and its pass cap put a ceiling of a
 * hundred thousand events on a single run, so any real backlog took days to
 * clear while the job spent almost all of that time doing nothing.
 * </p>
 *
 * <p>
 * The job runs daily and keeps a sliding window, so in steady state each run
 * deletes roughly one day of rows. The large case only arises once, when
 * retention is first applied to an existing dataset.
 * </p>
 *
 * <p>
 * The purge runs whether or not collection is currently enabled. Turning
 * collection off must not strand rows that were already captured past their
 * retention window.
 * </p>
 */
@Component(service = RetentionPurger.class)
public class RetentionPurger {

	public void purge(long companyId) throws Exception {
		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration =
			_searchEvalLoggerConfigurationRegistry.getConfiguration(companyId);

		int retentionDays = Math.max(
			searchEvalLoggerConfiguration.retentionDays(), 1);

		Date cutoffDate = new Date(
			_clock.millis() - TimeUnit.DAYS.toMillis(retentionDays));

		long start = _clock.millis();

		int[] deleted = _delete(companyId, cutoffDate);

		if ((deleted[0] > 0) && _log.isInfoEnabled()) {
			_log.info(
				String.format(
					"Purged %d search events and %d hits older than %s for " +
						"company %d in %d ms",
					deleted[0], deleted[1], cutoffDate, companyId,
					_clock.millis() - start));
		}
	}

	/**
	 * Visible for testing, so the cutoff can be placed without waiting.
	 */
	void setClock(Clock clock) {
		_clock = clock;
	}

	private int[] _delete(long companyId, Date cutoffDate) throws Exception {
		try {
			return TransactionInvokerUtil.invoke(
				_transactionConfig,
				() -> new int[] {
					_searchEventLocalService.
						deleteByCompanyIdAndCreateDateBefore(
							companyId, cutoffDate),
					_searchHitLocalService.
						deleteByCompanyIdAndCreateDateBefore(
							companyId, cutoffDate)
				});
		}
		catch (Throwable throwable) {
			if (throwable instanceof Exception) {
				throw (Exception)throwable;
			}

			throw new Exception(throwable);
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		RetentionPurger.class);

	private Clock _clock = Clock.systemUTC();

	@Reference
	private SearchEvalLoggerConfigurationRegistry
		_searchEvalLoggerConfigurationRegistry;

	@Reference
	private SearchEventLocalService _searchEventLocalService;

	@Reference
	private SearchHitLocalService _searchHitLocalService;

	private final TransactionConfig _transactionConfig =
		TransactionConfig.Factory.create(
			Propagation.REQUIRED, new Class<?>[] {Exception.class});

}
