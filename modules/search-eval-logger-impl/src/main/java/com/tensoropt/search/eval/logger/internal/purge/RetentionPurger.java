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
import com.tensoropt.search.eval.logger.model.SearchEvent;
import com.tensoropt.search.eval.logger.service.SearchEventLocalService;
import com.tensoropt.search.eval.logger.service.persistence.SearchEventPersistence;
import com.tensoropt.search.eval.logger.service.persistence.SearchHitPersistence;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Deletes events and hits past the retention window (DESIGN.md 3.4).
 *
 * <p>
 * Deletion is bounded: a batch of events and their hits per pass, each pass its
 * own transaction, with a pause between passes. One large DELETE over a table
 * holding millions of rows would lock or time out on exactly the installs where
 * retention matters most, and would do it on a production database.
 * </p>
 *
 * <p>
 * Each pass re-queries rather than paging through a cursor, because every pass
 * removes the rows the previous one matched. The predicate is
 * <code>(companyId, createDate)</code>, which is indexed (DESIGN.md 4.5).
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
			System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays));

		int deletedEventCount = 0;

		for (int pass = 0; pass < _MAXIMUM_PASS_COUNT; pass++) {
			List<SearchEvent> searchEvents =
				_searchEventPersistence.findByC_LtCreateDate(
					companyId, cutoffDate, 0, _EVENT_BATCH_SIZE);

			if (searchEvents.isEmpty()) {
				break;
			}

			deletedEventCount += _deleteBatch(searchEvents);

			if (searchEvents.size() < _EVENT_BATCH_SIZE) {
				break;
			}

			Thread.sleep(_PASS_PAUSE_MILLIS);
		}

		if ((deletedEventCount > 0) && _log.isInfoEnabled()) {
			_log.info(
				"Purged " + deletedEventCount + " search events older than " +
					cutoffDate + " for company " + companyId);
		}
	}

	private int _deleteBatch(List<SearchEvent> searchEvents) throws Exception {
		try {
			return TransactionInvokerUtil.invoke(
				_transactionConfig,
				() -> {
					for (SearchEvent searchEvent : searchEvents) {
						_searchHitPersistence.removeBySearchEventUuid(
							searchEvent.getUuid());

						_searchEventLocalService.deleteSearchEvent(searchEvent);
					}

					return searchEvents.size();
				});
		}
		catch (Throwable throwable) {
			if (throwable instanceof Exception) {
				throw (Exception)throwable;
			}

			throw new Exception(throwable);
		}
	}

	/**
	 * Events per pass. With the ten to twenty hits an event realistically
	 * carries, a pass deletes on the order of a couple of thousand rows, which
	 * is the size DESIGN.md 3.4 calls for.
	 */
	private static final int _EVENT_BATCH_SIZE = 100;

	/**
	 * Bounds a single run so a backlog cannot hold a scheduler thread
	 * indefinitely. Whatever is left is deleted by the next run.
	 */
	private static final int _MAXIMUM_PASS_COUNT = 1000;

	private static final long _PASS_PAUSE_MILLIS = 1000;

	private static final Log _log = LogFactoryUtil.getLog(
		RetentionPurger.class);

	@Reference
	private SearchEvalLoggerConfigurationRegistry
		_searchEvalLoggerConfigurationRegistry;

	@Reference
	private SearchEventLocalService _searchEventLocalService;

	@Reference
	private SearchEventPersistence _searchEventPersistence;

	@Reference
	private SearchHitPersistence _searchHitPersistence;

	private final TransactionConfig _transactionConfig =
		TransactionConfig.Factory.create(
			Propagation.REQUIRED, new Class<?>[] {Exception.class});

}
