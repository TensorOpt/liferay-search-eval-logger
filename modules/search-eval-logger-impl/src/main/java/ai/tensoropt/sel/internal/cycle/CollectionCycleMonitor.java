/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.cycle;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import ai.tensoropt.sel.api.CollectionCycle;
import ai.tensoropt.sel.api.SearchInterceptionStatus;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.configuration.SearchEvalLoggerConfigurationRegistry;
import ai.tensoropt.sel.internal.notifications.CollectionNotifier;
import ai.tensoropt.sel.service.SearchEventLocalService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import java.util.Date;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The daily check of DESIGN.md 3.6.
 *
 * <p>
 * It also reconciles the cycle with the configuration. The configuration
 * listener moves the cycle the moment logging is switched on or off, but it is
 * the only thing that does, and EC-14 leaves open whether it is invoked at all
 * on this platform. Doing the same reconciliation here bounds the damage if it
 * is not: a cycle then opens or closes within a day instead of never, and the
 * only thing lost is the identity of the enabling administrator, which 3.6
 * already has a fallback for.
 * </p>
 *
 * <p>
 * The event count is deliberately the last thing evaluated. It is the only
 * condition that costs a query, and on an instance that started collecting
 * yesterday, or that has already been notified, the day arithmetic alone
 * settles the answer.
 * </p>
 */
@Component(service = CollectionCycleMonitor.class)
public class CollectionCycleMonitor {

	public void check(long companyId) {
		try {
			_check(companyId);
		}
		catch (Throwable throwable) {

			// One virtual instance failing must not stop the others being
			// checked, and none of this is worth an alarm: the state it
			// reports is also on the admin screen.

			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to check the collection cycle of company " +
						companyId,
					throwable);
			}
		}
	}

	/**
	 * Visible for testing, so the day the check runs on can be chosen.
	 */
	void setClock(Clock clock) {
		_clock = clock;
	}

	/**
	 * Visible for testing. The runtime binds the fields directly; there are no
	 * production callers.
	 */
	void setCollaborators(
		CollectionCycleStatusImpl collectionCycleStatusImpl,
		CollectionNotifier collectionNotifier,
		SearchEvalLoggerConfigurationRegistry
			searchEvalLoggerConfigurationRegistry,
		SearchEventLocalService searchEventLocalService,
		SearchInterceptionStatus searchInterceptionStatus) {

		_collectionCycleStatusImpl = collectionCycleStatusImpl;
		_collectionNotifier = collectionNotifier;
		_searchEvalLoggerConfigurationRegistry =
			searchEvalLoggerConfigurationRegistry;
		_searchEventLocalService = searchEventLocalService;
		_searchInterceptionStatus = searchInterceptionStatus;
	}

	private void _check(long companyId) {
		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration =
			_searchEvalLoggerConfigurationRegistry.getConfiguration(companyId);

		if (!searchEvalLoggerConfiguration.enabled()) {
			_collectionCycleStatusImpl.closeCycle(companyId);

			return;
		}

		_collectionCycleStatusImpl.openCycle(companyId, 0);

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(companyId);

		if (!collectionCycle.isOpen()) {
			return;
		}

		LocalDate today = LocalDate.now(_clock.withZone(ZoneOffset.UTC));

		if (_checkStall(collectionCycle, today)) {
			return;
		}

		_checkReadiness(
			collectionCycle, today,
			searchEvalLoggerConfiguration.readinessMinimumDays(),
			searchEvalLoggerConfiguration.readinessMinimumEvents());
	}

	private void _checkReadiness(
		CollectionCycle collectionCycle, LocalDate today, int minimumDays,
		int minimumEvents) {

		if (collectionCycle.isReadinessNotified() ||
			!CollectionReadiness.hasCollectedLongEnough(
				collectionCycle, today, minimumDays)) {

			return;
		}

		long companyId = collectionCycle.getCompanyId();

		long eventCount =
			_searchEventLocalService.getCountByCompanyIdAndCreateDateOnOrAfter(
				companyId,
				_toDate(collectionCycle.getCollectionStartDate()));

		if (!CollectionReadiness.isReady(
				collectionCycle, today, minimumDays, eventCount,
				minimumEvents)) {

			return;
		}

		_collectionNotifier.notifyReadiness(
			companyId, collectionCycle.getEnabledByUserId());

		_collectionCycleStatusImpl.recordReadinessNotified(companyId, today);
	}

	/**
	 * Returns whether the cycle is stalled, whether or not a notification went
	 * out. A stalled cycle has collected nothing, so there is no readiness to
	 * evaluate after it.
	 */
	private boolean _checkStall(
		CollectionCycle collectionCycle, LocalDate today) {

		if (!CollectionReadiness.isStalled(
				collectionCycle, _searchInterceptionStatus.isIntercepting())) {

			return false;
		}

		if (collectionCycle.isStallNotified()) {
			return true;
		}

		long companyId = collectionCycle.getCompanyId();

		_collectionNotifier.notifyStall(
			companyId, collectionCycle.getEnabledByUserId());

		_collectionCycleStatusImpl.recordStallNotified(companyId, today);

		return true;
	}

	/**
	 * Midnight UTC of the collection start day, which is the inclusive lower
	 * bound of "events since the collection started".
	 */
	private Date _toDate(LocalDate localDate) {
		return Date.from(localDate.atStartOfDay(ZoneOffset.UTC).toInstant());
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CollectionCycleMonitor.class);

	private Clock _clock = Clock.systemUTC();

	@Reference
	private CollectionCycleStatusImpl _collectionCycleStatusImpl;

	@Reference
	private CollectionNotifier _collectionNotifier;

	@Reference
	private SearchEvalLoggerConfigurationRegistry
		_searchEvalLoggerConfigurationRegistry;

	@Reference
	private SearchEventLocalService _searchEventLocalService;

	@Reference
	private SearchInterceptionStatus _searchInterceptionStatus;

}
