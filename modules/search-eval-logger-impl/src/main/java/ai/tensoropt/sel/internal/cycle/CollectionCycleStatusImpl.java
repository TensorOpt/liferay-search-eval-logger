/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.cycle;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import ai.tensoropt.sel.api.CollectionCycle;
import ai.tensoropt.sel.api.CollectionCycleStatus;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.configuration.SearchEvalLoggerConfigurationRegistry;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Opens, closes and marks the collection cycle of DESIGN.md 3.6.
 *
 * <p>
 * Registered under the concrete type as well as the published interface, so the
 * components that move the cycle on can reach the mutators while every reader
 * outside this bundle sees only the read-only contract. Same arrangement, and
 * the same reason, as <code>SearchEvalLoggerStatisticsImpl</code>.
 * </p>
 *
 * <p>
 * <b>Why the first event is detected with an expiring marker.</b> 3.6 anchors
 * the collection start date on the first event persisted, but persistence is a
 * per-event path that must stay free of work. Once the date is known for a
 * company, the steady-state cost here is one hash lookup and one clock read per
 * event: no database access, no preference read. The marker expires rather than
 * being permanent because it is per JVM, and on a cluster the node that closes
 * and reopens a cycle need not be a node that persists events. A permanent
 * marker on some other node would suppress the write for the whole of the new
 * cycle, so the date would never be recorded, and the readiness notification
 * and the funnel link would never appear: a silent failure of exactly the kind
 * 3.6 exists to remove. The expiry bounds that to one interval, at the cost of
 * one preference read per company per interval.
 * </p>
 */
@Component(
	service = {CollectionCycleStatus.class, CollectionCycleStatusImpl.class}
)
public class CollectionCycleStatusImpl implements CollectionCycleStatus {

	/**
	 * Ends the cycle, clearing the collection start date and the notification
	 * markers, so that re-enabling logging starts a new one (3.6).
	 */
	public void closeCycle(long companyId) {
		_collectionStartConfirmed.remove(companyId);

		CollectionCycle collectionCycle = _collectionCycleStore.get(companyId);

		if (!collectionCycle.isOpen()) {
			return;
		}

		_collectionCycleStore.save(CollectionCycle.closed(companyId));
	}

	@Override
	public CollectionCycle getCollectionCycle(long companyId) {
		return _collectionCycleStore.get(companyId);
	}

	/**
	 * Opens a cycle if none is open, recording who enabled logging.
	 *
	 * <p>
	 * An open cycle is not reopened: that would reset the collection start
	 * date every time the configuration was saved for an unrelated reason. The
	 * one thing an open cycle will still accept is the enabling
	 * administrator's identity, and only when it does not already have one.
	 * The daily job opens cycles it finds missing and has no administrator to
	 * name, so without this a cycle it opened first would be stuck notifying
	 * every administrator on the instance for its whole life, even after a
	 * later save arrived carrying the right user. An identity already recorded
	 * is never replaced, so an unrelated save cannot re-attribute a cycle
	 * somebody else started.
	 * </p>
	 */
	public void openCycle(long companyId, long enabledByUserId) {
		CollectionCycle collectionCycle = _collectionCycleStore.get(companyId);

		if (collectionCycle.isOpen()) {
			if ((enabledByUserId > 0) &&
				(collectionCycle.getEnabledByUserId() <= 0)) {

				_collectionCycleStore.save(
					collectionCycle.withEnabledByUserId(enabledByUserId));
			}

			return;
		}

		_collectionStartConfirmed.remove(companyId);

		_collectionCycleStore.save(
			CollectionCycle.open(companyId, enabledByUserId));
	}

	/**
	 * Called for every persisted event. Does nothing once the cycle's start
	 * date is known, which is all but the first call of each interval.
	 */
	public void recordPersistedEvent(long companyId, Date createDate) {
		Long confirmedUntil = _collectionStartConfirmed.get(companyId);

		if ((confirmedUntil != null) && (_clock.millis() < confirmedUntil)) {
			return;
		}

		try {
			_recordCollectionStart(companyId, createDate);
		}
		catch (Throwable throwable) {

			// The persistence listener has already committed the event by the
			// time it gets here. Failing to note the cycle's start date must
			// not turn a written event into a logged failure.

			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to record the collection start date of company " +
						companyId,
					throwable);
			}
		}
	}

	public void recordReadinessNotified(long companyId, LocalDate date) {
		CollectionCycle collectionCycle = _collectionCycleStore.get(companyId);

		if (!collectionCycle.isOpen()) {
			return;
		}

		_collectionCycleStore.save(
			collectionCycle.withReadinessNotifiedDate(date));
	}

	public void recordStallNotified(long companyId, LocalDate date) {
		CollectionCycle collectionCycle = _collectionCycleStore.get(companyId);

		if (!collectionCycle.isOpen()) {
			return;
		}

		_collectionCycleStore.save(collectionCycle.withStallNotifiedDate(date));
	}

	/**
	 * Visible for testing, so the marker's expiry can be driven without
	 * waiting.
	 */
	void setClock(Clock clock) {
		_clock = clock;
	}

	/**
	 * Visible for testing. The runtime binds the field directly; there is no
	 * production caller.
	 */
	void setCollectionCycleStore(CollectionCycleStore collectionCycleStore) {
		_collectionCycleStore = collectionCycleStore;
	}

	/**
	 * Visible for testing. The runtime binds the field directly; there is no
	 * production caller.
	 */
	void setSearchEvalLoggerConfigurationRegistry(
		SearchEvalLoggerConfigurationRegistry
			searchEvalLoggerConfigurationRegistry) {

		_searchEvalLoggerConfigurationRegistry =
			searchEvalLoggerConfigurationRegistry;
	}

	private void _confirm(long companyId) {
		_collectionStartConfirmed.put(
			companyId, _clock.millis() + _CONFIRM_INTERVAL_MILLIS);
	}

	private void _recordCollectionStart(long companyId, Date createDate) {
		CollectionCycle collectionCycle = _collectionCycleStore.get(companyId);

		if (!collectionCycle.isOpen()) {

			// Either no configuration change has been observed on this node
			// yet, or logging was switched off while this event was in flight.
			// The configuration decides which, so that an event arriving just
			// after a disable cannot reopen a cycle that is meant to be over.

			SearchEvalLoggerConfiguration searchEvalLoggerConfiguration =
				_searchEvalLoggerConfigurationRegistry.getConfiguration(
					companyId);

			if (!searchEvalLoggerConfiguration.enabled()) {
				return;
			}

			collectionCycle = CollectionCycle.open(companyId, 0);
		}

		if (collectionCycle.isCollectionStarted()) {
			_confirm(companyId);

			return;
		}

		LocalDate collectionStartDate = _toLocalDate(createDate);

		if (_collectionCycleStore.save(
				collectionCycle.withCollectionStartDate(collectionStartDate))) {

			_confirm(companyId);

			if (_log.isInfoEnabled()) {
				_log.info(
					"Collection started on " + collectionStartDate +
						" for company " + companyId);
			}
		}
	}

	private LocalDate _toLocalDate(Date date) {
		if (date == null) {
			return LocalDate.now(_clock.withZone(ZoneOffset.UTC));
		}

		return LocalDate.ofInstant(date.toInstant(), ZoneOffset.UTC);
	}

	private static final long _CONFIRM_INTERVAL_MILLIS =
		TimeUnit.MINUTES.toMillis(10);

	private static final Log _log = LogFactoryUtil.getLog(
		CollectionCycleStatusImpl.class);

	private Clock _clock = Clock.systemUTC();

	@Reference
	private CollectionCycleStore _collectionCycleStore;

	private final Map<Long, Long> _collectionStartConfirmed =
		new ConcurrentHashMap<>();

	@Reference
	private SearchEvalLoggerConfigurationRegistry
		_searchEvalLoggerConfigurationRegistry;

}
