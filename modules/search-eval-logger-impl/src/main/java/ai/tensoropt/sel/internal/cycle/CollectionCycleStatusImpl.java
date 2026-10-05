/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.cycle;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import ai.tensoropt.sel.api.CollectionCycle;
import ai.tensoropt.sel.api.CollectionCycleStatus;
import ai.tensoropt.sel.api.SignupBannerDismissal;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.configuration.SearchEvalLoggerConfigurationRegistry;
import ai.tensoropt.sel.internal.notifications.CollectionNotifier;

import java.time.Clock;
import java.time.Instant;
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
 * the same reason, as <code>SearchEvalLoggerStatisticsImpl</code>. It is also
 * registered as {@link SignupBannerDismissal}, the one narrow write the web
 * module is allowed (TO-112), kept as its own interface rather than widening
 * <code>CollectionCycleStatus</code> past the read-only contract that class
 * documents.
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
 *
 * <p>
 * <b>Mutators are serialised per company (TO-112).</b> Every method here that
 * writes a cycle record does so by reading the current record, changing one
 * part of it, and saving the whole thing back; {@link CollectionCycleStore}
 * does not offer a narrower write. Before TO-112 the only callers were the
 * Message Bus consumer thread (persisted events) and the scheduler thread
 * (the daily job), and a lost update between them was already possible, if
 * rare, in the same JVM. {@link #dismiss} made it common: an administrator can
 * click the signup banner's dismiss control at any moment, on any web request
 * thread, including the instant the daily job is marking the readiness
 * notification sent. Two callers that each read the old record and each save
 * their own single change silently lose whichever change was saved first - a
 * dismissal erasing a just-recorded <code>readinessNotifiedDate</code> would
 * re-send a notification that already went out. Every public mutator below,
 * and the private ones it calls, synchronizes on a lock object scoped to the
 * company id, which is the simplest correct fix: a whole read-modify-write
 * happens as one step with respect to every other caller for that company.
 * </p>
 *
 * <p>
 * <b>What the lock does not cover.</b> It is a plain JVM monitor, held only
 * within one node, so it does nothing for two nodes of a cluster acting on the
 * same company at the same instant - the scenario the class comment above
 * already discusses for the expiring marker. One consequence specific to
 * {@link #_recordCollectionStart}: the Message Bus destination is serial
 * <em>per node</em> (3.3), not cluster-wide, so two nodes can each receive a
 * message that is the first event of a new cycle, each see
 * <code>!isCollectionStarted()</code> under their own node-local lock, and
 * each notify. A cluster-wide "exactly once" is not claimed; this lock removes
 * the lost-update risk within one node, which is where {@link #dismiss}
 * actually introduced one.
 * </p>
 */
@Component(
	service = {
		CollectionCycleStatus.class, CollectionCycleStatusImpl.class,
		SignupBannerDismissal.class
	}
)
public class CollectionCycleStatusImpl
	implements CollectionCycleStatus, SignupBannerDismissal {

	/**
	 * TO-112: the one mutation a bundle other than this one may trigger. An
	 * administrator dismissing the signup banner of 10.1, or clicking its link,
	 * both call this; see {@link SignupBannerDismissal}.
	 */
	@Override
	public boolean dismiss(long companyId) {
		synchronized (_lockFor(companyId)) {
			CollectionCycle collectionCycle = _collectionCycleStore.get(
				companyId);

			// The banner is only shown on an open cycle, so a closed one here
			// is a failed read (the store answers closed on one) or a close
			// racing the click. Either way nothing is recorded, so say so.

			if (!collectionCycle.isOpen()) {
				return false;
			}

			if (collectionCycle.isSignupBannerDismissed()) {
				return true;
			}

			return _collectionCycleStore.save(
				collectionCycle.withSignupBannerDismissed(true));
		}
	}

	/**
	 * Ends the cycle, clearing the collection start date and the notification
	 * markers, so that re-enabling logging starts a new one (3.6).
	 */
	public void closeCycle(long companyId) {
		synchronized (_lockFor(companyId)) {
			_collectionStartConfirmed.remove(companyId);

			CollectionCycle collectionCycle = _collectionCycleStore.get(
				companyId);

			if (!collectionCycle.isOpen()) {
				return;
			}

			_collectionCycleStore.save(CollectionCycle.closed(companyId));
		}
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
	 *
	 * <p>
	 * An open cycle with no <code>cycleOpenedDate</code> is one carried over
	 * from before TO-112 added the stall grace period. It is backfilled with
	 * now rather than left null, which would otherwise suppress the stall
	 * notification for that cycle's whole remaining life (3.6 is called once a
	 * day, this is the only other place that moves the cycle, and there is
	 * nowhere else a real value could come from).
	 * </p>
	 */
	public void openCycle(long companyId, long enabledByUserId) {
		synchronized (_lockFor(companyId)) {
			CollectionCycle collectionCycle = _collectionCycleStore.get(
				companyId);

			if (collectionCycle.isOpen()) {
				CollectionCycle updatedCollectionCycle = collectionCycle;

				if ((enabledByUserId > 0) &&
					(collectionCycle.getEnabledByUserId() <= 0)) {

					updatedCollectionCycle = updatedCollectionCycle.
						withEnabledByUserId(enabledByUserId);
				}

				if (updatedCollectionCycle.getCycleOpenedDate() == null) {
					updatedCollectionCycle = updatedCollectionCycle.
						withCycleOpenedDate(_clock.instant());
				}

				if (updatedCollectionCycle != collectionCycle) {
					_collectionCycleStore.save(updatedCollectionCycle);
				}

				return;
			}

			_collectionStartConfirmed.remove(companyId);

			_collectionCycleStore.save(
				CollectionCycle.open(
					companyId, enabledByUserId, _clock.instant()));
		}
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
		synchronized (_lockFor(companyId)) {
			CollectionCycle collectionCycle = _collectionCycleStore.get(
				companyId);

			if (!collectionCycle.isOpen()) {
				return;
			}

			_collectionCycleStore.save(
				collectionCycle.withReadinessNotifiedDate(date));
		}
	}

	public void recordStallNotified(long companyId, LocalDate date) {
		synchronized (_lockFor(companyId)) {
			CollectionCycle collectionCycle = _collectionCycleStore.get(
				companyId);

			if (!collectionCycle.isOpen()) {
				return;
			}

			_collectionCycleStore.save(
				collectionCycle.withStallNotifiedDate(date));
		}
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
	void setCollectionNotifier(CollectionNotifier collectionNotifier) {
		_collectionNotifier = collectionNotifier;
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

	/**
	 * The simplest correct lock (TO-112): one monitor object per company,
	 * created once and kept for the JVM's lifetime. Entries are never
	 * removed, the same choice already made for {@link
	 * #_collectionStartConfirmed}, because the number of distinct company ids
	 * a JVM ever sees is small and bounded, and removing an entry while a
	 * thread might still be synchronized on it is its own hazard this avoids
	 * entirely by never trying.
	 */
	private Object _lockFor(long companyId) {
		return _locks.computeIfAbsent(companyId, ignored -> new Object());
	}

	/**
	 * Two independently guarded steps, not one: setting
	 * <code>collectionStartDate</code> (once, ever, per cycle) and sending the
	 * TO-112 collection-started notification (also once, but gated on its own
	 * marker, {@link CollectionCycle#isCollectionStartedNotified()}, rather
	 * than inferred from the first). Folding them into a single guard would
	 * mean a transient failure recording the notification could never be
	 * retried without also risking <code>collectionStartDate</code> moving to
	 * a later event's date, which 3.6 requires never happens.
	 *
	 * <p>
	 * <b>The notification fires only when this call is the one that set
	 * <code>collectionStartDate</code>, not merely when it finds the marker
	 * unset.</b> An instance upgraded from before TO-112 has an open cycle
	 * whose <code>collectionStartDate</code> is weeks old and whose
	 * <code>collectionStartedNotifiedDate</code> does not exist yet, because
	 * the field did not exist to have one. Without this distinction, the
	 * first event persisted after the upgrade restart would find
	 * <code>collectionStartDate</code> already set and
	 * <code>collectionStartedNotifiedDate</code> unset - indistinguishable,
	 * on that reading alone, from a cycle whose start date this very call
	 * just set - and would send "collection started, confirming the restart
	 * worked" for a restart that happened weeks ago.
	 * <code>justStarted</code> is <code>true</code> only when the branch
	 * above actually transitioned the date
	 * from unset to set in this call; a legacy record backfills the marker
	 * silently instead. This also covers the ordering {@link #openCycle}'s
	 * own backfill does not need to duplicate: whichever of the two runs
	 * first on a legacy cycle - this method, from a persisted event arriving
	 * before any reconciliation, or <code>openCycle</code> itself - the
	 * decision here is self-contained and does not depend on which one it
	 * was.
	 * </p>
	 *
	 * <p>
	 * The confirming cache ({@link #_confirm}) is armed only once both steps
	 * have actually succeeded - {@link #_recordCollectionStartedNotified} now
	 * reports whether its save landed - so a store failure on either step
	 * keeps costing a read per event until it stops failing, rather than
	 * being silently abandoned once the in-memory marker is set regardless.
	 * </p>
	 */
	private void _recordCollectionStart(long companyId, Date createDate) {
		synchronized (_lockFor(companyId)) {
			CollectionCycle collectionCycle = _collectionCycleStore.get(
				companyId);

			if (!collectionCycle.isOpen()) {

				// Either no configuration change has been observed on this
				// node yet, or logging was switched off while this event was
				// in flight. The configuration decides which, so that an
				// event arriving just after a disable cannot reopen a cycle
				// that is meant to be over.

				SearchEvalLoggerConfiguration searchEvalLoggerConfiguration =
					_searchEvalLoggerConfigurationRegistry.getConfiguration(
						companyId);

				if (!searchEvalLoggerConfiguration.enabled()) {
					return;
				}

				collectionCycle = CollectionCycle.open(
					companyId, 0, _clock.instant());
			}

			boolean justStarted = false;

			if (!collectionCycle.isCollectionStarted()) {
				LocalDate collectionStartDate = _toLocalDate(createDate);

				if (!_collectionCycleStore.save(
						collectionCycle.withCollectionStartDate(
							collectionStartDate))) {

					return;
				}

				collectionCycle = collectionCycle.withCollectionStartDate(
					collectionStartDate);
				justStarted = true;

				if (_log.isInfoEnabled()) {
					_log.info(
						"Collection started on " + collectionStartDate +
							" for company " + companyId);
				}
			}

			if (!collectionCycle.isCollectionStartedNotified()) {
				if (justStarted) {

					// Best effort: CollectionNotifier never throws, by its
					// own design (see its class comment), so this cannot
					// leave collectionStartDate set with the notification
					// silently never attempted.

					_collectionNotifier.notifyCollectionStarted(
						companyId, collectionCycle.getEnabledByUserId());
				}
				else if (_log.isInfoEnabled()) {
					_log.info(
						"Collection start date for company " + companyId +
							" predates TO-112; backfilling " +
								"collectionStartedNotifiedDate without " +
									"sending a notification for a restart " +
										"that already happened");
				}

				if (!_recordCollectionStartedNotified(
						companyId, collectionCycle.getCollectionStartDate())) {

					return;
				}
			}

			_confirm(companyId);
		}
	}

	/**
	 * Its own read-modify-write, inside the lock {@link
	 * #_recordCollectionStart} already holds (re-entrant on the same
	 * company's lock object), rather than reusing that method's in-memory
	 * <code>collectionCycle</code>: the notification attempt above may have
	 * taken long enough that reusing a stale snapshot here could overwrite a
	 * concurrent change to an unrelated field on this same cycle.
	 *
	 * <p>
	 * Returns whether the marker is now durably recorded, so {@link
	 * #_recordCollectionStart} can decide whether to arm {@link #_confirm}: a
	 * cycle found already closed counts as nothing left to do (returns
	 * <code>true</code>) rather than as a failure to retry forever.
	 * </p>
	 */
	private boolean _recordCollectionStartedNotified(
		long companyId, LocalDate date) {

		synchronized (_lockFor(companyId)) {
			CollectionCycle collectionCycle = _collectionCycleStore.get(
				companyId);

			if (!collectionCycle.isOpen()) {
				return true;
			}

			return _collectionCycleStore.save(
				collectionCycle.withCollectionStartedNotifiedDate(date));
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

	@Reference
	private CollectionNotifier _collectionNotifier;

	private final Map<Long, Long> _collectionStartConfirmed =
		new ConcurrentHashMap<>();

	private final Map<Long, Object> _locks = new ConcurrentHashMap<>();

	@Reference
	private SearchEvalLoggerConfigurationRegistry
		_searchEvalLoggerConfigurationRegistry;

}
