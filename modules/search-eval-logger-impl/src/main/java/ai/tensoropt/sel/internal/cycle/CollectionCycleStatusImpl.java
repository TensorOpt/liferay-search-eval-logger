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
import java.util.Objects;
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
 * <b>One exception (D14):</b> {@link #_recordCollectionStart} releases the
 * lock before actually sending the collection-started notification, so that
 * a slow send cannot make {@link #dismiss}, or any other caller, wait on it.
 * Releasing the lock opens a gap a disable and re-enable can land in, which a
 * naive re-check of {@link CollectionCycle#isOpen()} alone cannot see through:
 * both the cycle the notification was computed for and the one that replaced
 * it are open. {@link #_recordCollectionStartedNotified} closes that gap by
 * also comparing <code>cycleOpenedDate</code>, captured under the same lock
 * that decided to send; see that method's own comment for the mechanism.
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
	 */
	public void openCycle(long companyId, long enabledByUserId) {
		synchronized (_lockFor(companyId)) {
			CollectionCycle collectionCycle = _collectionCycleStore.get(
				companyId);

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
	 * unset.</b> The two steps are saved separately, so a run that sets
	 * <code>collectionStartDate</code> and then fails to save
	 * <code>collectionStartedNotifiedDate</code> (a transient store failure)
	 * leaves a cycle in exactly the state a later event sees:
	 * <code>collectionStartDate</code> already set,
	 * <code>collectionStartedNotifiedDate</code> still unset. Without this
	 * distinction, that later event would read the unset marker as "just
	 * started" and send the collection-started notification a second time,
	 * for a cycle that already started and was already reported, whatever
	 * let it start: a restart that let a bypassed consumer rebind, an
	 * ordinary enable, or a cycle reopened by a disable and re-enable.
	 * <code>justStarted</code> is <code>true</code> only when the
	 * branch above actually transitioned the date from unset to set in this
	 * very call; any other call finding the marker unset records it silently
	 * instead, without sending again.
	 * </p>
	 *
	 * <p>
	 * The confirming cache ({@link #_confirm}) is armed only once both steps
	 * have actually succeeded - {@link #_recordCollectionStartedNotified} now
	 * reports whether its save landed - so a store failure on either step
	 * keeps costing a read per event until it stops failing, rather than
	 * being silently abandoned once the in-memory marker is set regardless.
	 * </p>
	 *
	 * <p>
	 * <b>The notification itself is sent outside the lock (D14).</b> Everything
	 * that reads or writes the cycle record, including the decision of whether
	 * to send, still happens inside one locked region, exactly as before;
	 * only the actual {@link CollectionNotifier#notifyCollectionStarted} call
	 * is moved out of it, into the gap between deciding and recording. Nothing
	 * else needing this company's lock, {@link #dismiss} most notably, should
	 * have to wait on a notification send, which may resolve a recipient and
	 * call into Liferay's notification framework and so is not guaranteed to
	 * be fast. The decision itself is still made exactly once, atomically,
	 * before the lock is released: <code>shouldSend</code> is computed and
	 * captured into a local variable while still inside the locked region, so
	 * releasing the lock before sending cannot let a second thread decide to
	 * send a second time for the same transition. That gap can still let a
	 * disable and re-enable replace the cycle this decision was made for
	 * before {@link #_recordCollectionStartedNotified} runs; see that
	 * method's own comment (D14) for how it tells the replaced cycle apart
	 * from the one it was asked to mark.
	 * </p>
	 *
	 * <p>
	 * <b>A crash in the gap between sending and recording does not repeat the
	 * notification.</b> If the JVM stops after a successful send but before
	 * {@link #_recordCollectionStartedNotified} saves, the cycle is left with
	 * <code>collectionStartDate</code> set and <code>collectionStartedNotifiedDate</code>
	 * still unset, exactly the state the comment above this method already
	 * describes for a plain transient store failure. The very next event
	 * persisted in the same cycle reads that state the same way: <code>
	 * isCollectionStarted()</code> is already true, so <code>justStarted</code>
	 * is <code>false</code>, so <code>shouldSend</code> is <code>false</code>
	 * - it records the marker silently rather than sending again. The window
	 * this leaves is a marker that can lag behind an already-sent
	 * notification until the next event heals it, not a repeated send; that
	 * lag is what this design tolerates, not a duplicate notification.
	 * </p>
	 */
	private void _recordCollectionStart(long companyId, Date createDate) {
		boolean needsNotification;
		boolean shouldSend = false;
		LocalDate collectionStartDate = null;
		long enabledByUserId = 0;
		Instant cycleOpenedDate = null;

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
				LocalDate newCollectionStartDate = _toLocalDate(createDate);

				if (!_collectionCycleStore.save(
						collectionCycle.withCollectionStartDate(
							newCollectionStartDate))) {

					return;
				}

				collectionCycle = collectionCycle.withCollectionStartDate(
					newCollectionStartDate);
				justStarted = true;

				if (_log.isInfoEnabled()) {
					_log.info(
						"Collection started on " + newCollectionStartDate +
							" for company " + companyId);
				}
			}

			needsNotification = !collectionCycle.isCollectionStartedNotified();

			if (needsNotification) {
				shouldSend = justStarted;
				collectionStartDate = collectionCycle.getCollectionStartDate();
				enabledByUserId = collectionCycle.getEnabledByUserId();
				cycleOpenedDate = collectionCycle.getCycleOpenedDate();

				if (!shouldSend && _log.isInfoEnabled()) {
					_log.info(
						"Collection start date for company " + companyId +
							" was already recorded by an earlier call; " +
								"recording collectionStartedNotifiedDate " +
									"without sending a second notification " +
										"for the same cycle");
				}
			}
		}

		if (needsNotification) {

			// Best effort: CollectionNotifier never throws, by its own
			// design (see its class comment), so this cannot leave
			// collectionStartDate set with the notification silently never
			// attempted. Deliberately outside the lock above; see this
			// method's own comment for why and for what a crash here does
			// not do.

			if (shouldSend) {
				_collectionNotifier.notifyCollectionStarted(
					companyId, enabledByUserId);
			}

			if (!_recordCollectionStartedNotified(
					companyId, collectionStartDate, cycleOpenedDate)) {

				return;
			}
		}

		_confirm(companyId);
	}

	/**
	 * Its own read-modify-write, under a fresh acquisition of the same
	 * per-company lock, <b>not</b> one {@link #_recordCollectionStart} still
	 * holds: by the time this runs, that method has already released its own
	 * lock and, when <code>shouldSend</code> was true, attempted the
	 * notification send outside it (D14). Re-reading the cycle here, rather
	 * than reusing that method's in-memory <code>collectionCycle</code>, is
	 * what makes that safe: the notification attempt may have taken long
	 * enough that a stale snapshot could overwrite a concurrent change to an
	 * unrelated field on this same cycle.
	 *
	 * <p>
	 * <b>D14, the cross-cycle race this guards against.</b> The gap between
	 * releasing the lock in {@link #_recordCollectionStart} and this method
	 * reacquiring it is exactly when a disable and a re-enable can land: a
	 * close clears the cycle this notification was computed for, and the
	 * reopen starts a new one with its own, later <code>cycleOpenedDate</code>
	 * but no <code>collectionStartDate</code> of its own yet. Checking only
	 * {@link CollectionCycle#isOpen()} cannot tell those two cycles apart,
	 * since the new one is open too; stamping
	 * <code>collectionStartedNotifiedDate</code> onto it regardless would
	 * mark the new cycle as already notified before its own first event ever
	 * arrives, so that event would never send its own, genuine notification.
	 * <code>cycleOpenedDate</code> is captured by {@link
	 * #_recordCollectionStart} under the same lock that decided to notify, so
	 * comparing it here against the live record tells the two cycles apart:
	 * a mismatch means the cycle has moved on, and nothing is recorded for
	 * the notification this call was sent for.
	 * </p>
	 *
	 * <p>
	 * Returns whether {@link #_recordCollectionStart} may arm {@link
	 * #_confirm}: <code>true</code> once the marker is durably recorded, or
	 * for a cycle found closed (the next openCycle clears the cache anyway);
	 * <code>false</code> when the save failed or the cycle was replaced
	 * meanwhile, so the new cycle's first event still runs the full path.
	 * </p>
	 */
	private boolean _recordCollectionStartedNotified(
		long companyId, LocalDate date, Instant cycleOpenedDate) {

		synchronized (_lockFor(companyId)) {
			CollectionCycle collectionCycle = _collectionCycleStore.get(
				companyId);

			if (!collectionCycle.isOpen()) {
				return true;
			}

			// The cycle was replaced during the send: openCycle has already
			// cleared the confirm cache for the new one, and arming it again
			// here would silence the new cycle's own first event for the
			// cache's lifetime. Leave the cache alone.

			if (!Objects.equals(
					collectionCycle.getCycleOpenedDate(), cycleOpenedDate)) {

				return false;
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
