/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.tensoropt.sel.api.CollectionCycle;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.configuration.SearchEvalLoggerConfigurationRegistry;
import ai.tensoropt.sel.internal.notifications.CollectionNotifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * DESIGN.md 3.6 anchors the collection start date on the first event actually
 * persisted, which puts this class on a per-event path. What is asserted here
 * is as much what it does <em>not</em> do (touch the store once the date is
 * known) as what it does.
 */
public class CollectionCycleStatusImplTest {

	@BeforeEach
	public void setUp() {
		_now = Instant.parse("2026-03-01T09:30:00Z");

		_collectionCycleStore = new RecordingCollectionCycleStore();

		_searchEvalLoggerConfiguration = mock(
			SearchEvalLoggerConfiguration.class);

		when(
			_searchEvalLoggerConfiguration.enabled()
		).thenReturn(
			true
		);

		SearchEvalLoggerConfigurationRegistry
			searchEvalLoggerConfigurationRegistry = mock(
				SearchEvalLoggerConfigurationRegistry.class);

		when(
			searchEvalLoggerConfigurationRegistry.getConfiguration(_COMPANY_ID)
		).thenReturn(
			_searchEvalLoggerConfiguration
		);

		_collectionNotifier = mock(CollectionNotifier.class);

		_collectionCycleStatusImpl = new CollectionCycleStatusImpl();

		_collectionCycleStatusImpl.setClock(_tickingClock());
		_collectionCycleStatusImpl.setCollectionCycleStore(
			_collectionCycleStore);
		_collectionCycleStatusImpl.setCollectionNotifier(_collectionNotifier);
		_collectionCycleStatusImpl.setSearchEvalLoggerConfigurationRegistry(
			searchEvalLoggerConfigurationRegistry);
	}

	@Test
	public void theFirstPersistedEventSetsTheCollectionStartDate() {
		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 42L);

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T09:31:00Z"));

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertEquals(
			LocalDate.parse("2026-03-01"),
			collectionCycle.getCollectionStartDate());
		assertEquals(42L, collectionCycle.getEnabledByUserId());
	}

	/**
	 * TO-112: fired from the persistence path, not the daily job, the moment
	 * the collection start date is first set.
	 */
	@Test
	public void theFirstPersistedEventNotifiesCollectionStarted() {
		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 42L);

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T09:31:00Z"));

		verify(_collectionNotifier).notifyCollectionStarted(_COMPANY_ID, 42L);

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertEquals(
			LocalDate.parse("2026-03-01"),
			collectionCycle.getCollectionStartedNotifiedDate());
	}

	/**
	 * Exactly once per cycle: later events in the same cycle must not notify
	 * again.
	 */
	@Test
	public void laterEventsDoNotNotifyCollectionStartedAgain() {
		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 42L);

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T09:31:00Z"));

		for (int i = 0; i < 5; i++) {
			_collectionCycleStatusImpl.recordPersistedEvent(
				_COMPANY_ID, _date("2026-03-01T09:32:00Z"));
		}

		verify(_collectionNotifier, times(1)).notifyCollectionStarted(
			anyLong(), anyLong());
	}

	/**
	 * A new cycle re-arms the notification: closing and reopening must
	 * produce a second one, not leave it permanently fired.
	 */
	@Test
	public void aNewCycleReArmsTheCollectionStartedNotification() {
		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 42L);

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T09:31:00Z"));

		_collectionCycleStatusImpl.closeCycle(_COMPANY_ID);

		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 42L);

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-11T09:31:00Z"));

		verify(
			_collectionNotifier, times(2)
		).notifyCollectionStarted(
			anyLong(), anyLong()
		);
	}

	/**
	 * TO-112, round 3 review: an instance upgraded from before TO-112 has an
	 * open cycle whose <code>collectionStartDate</code> is weeks old and
	 * whose <code>collectionStartedNotifiedDate</code> does not exist, since
	 * the field did not exist to have one. The first event persisted after
	 * the upgrade restart must not read that as "just started" and send a
	 * false "collection started, confirming the restart worked" for a
	 * restart that happened weeks ago; it must backfill the marker silently
	 * instead.
	 */
	@Test
	public void aLegacyRecordWithNoNotifiedMarkerIsBackfilledWithoutNotifying() {
		LocalDate legacyStartDate = LocalDate.parse("2026-01-01");

		_collectionCycleStore.save(
			new CollectionCycle(
				_COMPANY_ID, true, 42L, Instant.parse("2026-01-01T00:00:00Z"),
				legacyStartDate, null, null, null, false));

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T09:31:00Z"));

		verify(
			_collectionNotifier, never()
		).notifyCollectionStarted(
			anyLong(), anyLong()
		);

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertEquals(
			legacyStartDate, collectionCycle.getCollectionStartDate(),
			"A legacy collection start date must never move to a later " +
				"event's date");
		assertEquals(
			legacyStartDate,
			collectionCycle.getCollectionStartedNotifiedDate(),
			"The marker should be backfilled rather than left null forever");
	}

	/**
	 * The backfill must not repeat the notification check on every
	 * subsequent event either, once the marker is durably recorded.
	 */
	@Test
	public void aLegacyRecordIsBackfilledOnlyOnceAcrossMultipleEvents() {
		_collectionCycleStore.save(
			new CollectionCycle(
				_COMPANY_ID, true, 42L, Instant.parse("2026-01-01T00:00:00Z"),
				LocalDate.parse("2026-01-01"), null, null, null, false));

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T09:31:00Z"));

		_collectionCycleStore.resetCounts();

		for (int i = 0; i < 1000; i++) {
			_collectionCycleStatusImpl.recordPersistedEvent(
				_COMPANY_ID, _date("2026-03-01T09:32:00Z"));
		}

		assertEquals(
			0, _collectionCycleStore.getReadCount(),
			"Once the marker is backfilled, later events must not reach " +
				"the store either, the same guarantee a normally started " +
					"cycle already gets");
		verify(
			_collectionNotifier, never()
		).notifyCollectionStarted(
			anyLong(), anyLong()
		);
	}

	/**
	 * The date is the event's, in UTC, not the moment the marker happened to
	 * be written. An event persisted just after midnight UTC belongs to the
	 * new day even where the server's own zone disagrees.
	 */
	@Test
	public void theStartDateComesFromTheEventInUTC() {
		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 0);

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-02T00:00:01Z"));

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertEquals(
			LocalDate.parse("2026-03-02"),
			collectionCycle.getCollectionStartDate());
	}

	/**
	 * The point of the in-memory marker: once the date is known, the per-event
	 * path stops going near the store.
	 */
	@Test
	public void laterEventsDoNotReachTheStore() {
		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 0);

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T09:31:00Z"));

		_collectionCycleStore.resetCounts();

		for (int i = 0; i < 1000; i++) {
			_collectionCycleStatusImpl.recordPersistedEvent(
				_COMPANY_ID, _date("2026-03-01T09:32:00Z"));
		}

		assertEquals(
			0, _collectionCycleStore.getReadCount(),
			"A persisted event must not read the cycle record once the " +
				"collection start date is known");
		assertEquals(0, _collectionCycleStore.getWriteCount());
	}

	/**
	 * The marker expires so that a cycle reopened on another cluster node is
	 * eventually noticed. Without this, that node would suppress the write for
	 * the whole of the new cycle and no start date would ever be recorded.
	 */
	@Test
	public void theMarkerIsRecheckedAfterItExpires() {
		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 0);

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T09:31:00Z"));

		_collectionCycleStore.resetCounts();

		_now = _now.plus(Duration.ofHours(1));

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T10:31:00Z"));

		assertTrue(
			_collectionCycleStore.getReadCount() > 0,
			"An expired marker must be revalidated against the store");
	}

	/**
	 * A start date already recorded is never moved. It is the date collection
	 * began, and a second event does not begin it again.
	 */
	@Test
	public void anExistingStartDateIsNotOverwritten() {
		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 0);

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T09:31:00Z"));

		_now = _now.plus(Duration.ofDays(3));

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-04T09:31:00Z"));

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertEquals(
			LocalDate.parse("2026-03-01"),
			collectionCycle.getCollectionStartDate());
	}

	/**
	 * DESIGN.md 3.6: disabling logging clears the collection start date, and
	 * re-enabling starts a new cycle. The notification markers go with it, or
	 * the new cycle would inherit the old one's "already told them".
	 */
	@Test
	public void closingTheCycleClearsEverythingItRecorded() {
		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 42L);

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T09:31:00Z"));

		_collectionCycleStatusImpl.recordReadinessNotified(
			_COMPANY_ID, LocalDate.parse("2026-04-01"));

		_collectionCycleStatusImpl.dismiss(_COMPANY_ID);

		_collectionCycleStatusImpl.closeCycle(_COMPANY_ID);

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertFalse(collectionCycle.isOpen());
		assertNull(collectionCycle.getCycleOpenedDate());
		assertNull(collectionCycle.getCollectionStartDate());
		assertNull(collectionCycle.getCollectionStartedNotifiedDate());
		assertNull(collectionCycle.getReadinessNotifiedDate());
		assertNull(collectionCycle.getStallNotifiedDate());
		assertFalse(collectionCycle.isSignupBannerDismissed());
	}

	/**
	 * TO-112: opening a cycle records when, which the stall grace period
	 * counts from.
	 */
	@Test
	public void openingACycleRecordsWhenItOpened() {
		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 42L);

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertEquals(_now, collectionCycle.getCycleOpenedDate());
	}

	/**
	 * An open cycle carried over from before TO-112 has no recorded open
	 * instant. It is backfilled with now rather than left null, or the stall
	 * notification would be suppressed for that cycle's whole remaining life.
	 */
	@Test
	public void anOpenCycleWithNoRecordedOpenInstantIsBackfilled() {
		_collectionCycleStore.save(
			new CollectionCycle(
				_COMPANY_ID, true, 42L, null, null, null, null, null,
				false));

		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 42L);

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertEquals(_now, collectionCycle.getCycleOpenedDate());
	}

	/**
	 * TO-112: dismissing the signup banner, or clicking its link, both call
	 * this, and either suppresses it for the rest of the cycle.
	 */
	@Test
	public void dismissingTheSignupBannerRecordsIt() {
		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 42L);

		assertTrue(_collectionCycleStatusImpl.dismiss(_COMPANY_ID));

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertTrue(collectionCycle.isSignupBannerDismissed());
	}

	@Test
	public void dismissingAClosedCycleDoesNothing() {
		assertTrue(
			_collectionCycleStatusImpl.dismiss(_COMPANY_ID),
			"Nothing left to persist is not a failure");

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertFalse(collectionCycle.isOpen());
		assertFalse(collectionCycle.isSignupBannerDismissed());
	}

	@Test
	public void dismissingAnAlreadyDismissedBannerIsIdempotent() {
		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 42L);

		_collectionCycleStatusImpl.dismiss(_COMPANY_ID);
		_collectionCycleStore.resetCounts();

		assertTrue(_collectionCycleStatusImpl.dismiss(_COMPANY_ID));
		assertEquals(
			0, _collectionCycleStore.getWriteCount(),
			"Dismissing an already dismissed banner must not write again");
	}

	/**
	 * TO-112, round 3 review: a caller that ignores this return value cannot
	 * tell a saved dismissal from one that silently was not, which is what
	 * made the web module's own failure handling unreachable dead code.
	 */
	@Test
	public void dismissReportsWhenTheWriteFails() {
		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 42L);

		_collectionCycleStore.setFailWrites(true);

		assertFalse(
			_collectionCycleStatusImpl.dismiss(_COMPANY_ID),
			"A failed write must be reported rather than swallowed as " +
				"success");

		_collectionCycleStore.setFailWrites(false);

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertFalse(
			collectionCycle.isSignupBannerDismissed(),
			"A failed write must not be reflected in the stored cycle " +
				"either");
	}

	@Test
	public void reopeningStartsANewCycleWithANewStartDate() {
		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 42L);

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T09:31:00Z"));

		_collectionCycleStatusImpl.closeCycle(_COMPANY_ID);

		_now = _now.plus(Duration.ofDays(10));

		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 43L);

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-11T09:31:00Z"));

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertEquals(
			LocalDate.parse("2026-03-11"),
			collectionCycle.getCollectionStartDate());
		assertEquals(43L, collectionCycle.getEnabledByUserId());
	}

	/**
	 * A save that leaves logging enabled is not a new cycle. Reopening in
	 * place would reset the start date every time an unrelated setting
	 * changed, and an identity already recorded is never replaced, so an
	 * unrelated save cannot re-attribute a cycle somebody else started.
	 */
	@Test
	public void openingAnAlreadyOpenCycleChangesNothing() {
		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 42L);

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T09:31:00Z"));

		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 99L);

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertEquals(
			LocalDate.parse("2026-03-01"),
			collectionCycle.getCollectionStartDate());
		assertEquals(42L, collectionCycle.getEnabledByUserId());
	}

	/**
	 * The daily job opens any cycle it finds missing and has no administrator
	 * to name it with. Without a way to fill that in later, such a cycle would
	 * notify every administrator on the instance for its whole life, even
	 * after a configuration save arrived carrying the right user.
	 */
	@Test
	public void anUnknownEnablingUserCanBeFilledInLater() {
		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 0);

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T09:31:00Z"));

		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 42L);

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertEquals(42L, collectionCycle.getEnabledByUserId());
		assertEquals(
			LocalDate.parse("2026-03-01"),
			collectionCycle.getCollectionStartDate(),
			"Filling in the enabling user must not disturb the collection " +
				"start date");
		assertTrue(collectionCycle.isOpen());
	}

	/**
	 * An event that lands just after logging was switched off must not reopen
	 * the cycle it was collected in.
	 */
	@Test
	public void anEventArrivingAfterADisableDoesNotReopenTheCycle() {
		when(
			_searchEvalLoggerConfiguration.enabled()
		).thenReturn(
			false
		);

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T09:31:00Z"));

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertFalse(collectionCycle.isOpen());
		assertNull(collectionCycle.getCollectionStartDate());
	}

	/**
	 * The listener that opens a cycle on a configuration save may never run
	 * (EC-14). An event arriving while logging is on is itself proof that a
	 * cycle should be open, so it opens one rather than dropping the date.
	 */
	@Test
	public void anEventOpensACycleWhenNoneWasObserved() {
		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T09:31:00Z"));

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertTrue(collectionCycle.isOpen());
		assertEquals(
			LocalDate.parse("2026-03-01"),
			collectionCycle.getCollectionStartDate());
	}

	/**
	 * A failed write must not arm the marker: the next event has to try again,
	 * or a transient store failure would lose the start date for the whole
	 * cycle.
	 */
	@Test
	public void aFailedWriteIsRetriedByTheNextEvent() {
		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 0);

		_collectionCycleStore.setFailWrites(true);

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T09:31:00Z"));

		_collectionCycleStore.setFailWrites(false);

		_collectionCycleStatusImpl.recordPersistedEvent(
			_COMPANY_ID, _date("2026-03-01T09:32:00Z"));

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertEquals(
			LocalDate.parse("2026-03-01"),
			collectionCycle.getCollectionStartDate());
	}

	/**
	 * TO-112: the lost update a concurrent {@link
	 * CollectionCycleStatusImpl#dismiss} can cause without the per-company
	 * lock. The daily job's thread is paused with the readiness marker's
	 * record read but not yet saved, which is exactly the window a web
	 * request thread's dismiss used to be able to run entirely inside -
	 * reading the same old record, saving its own single change, and
	 * silently erasing whatever the first thread was about to save. If the
	 * lock in <code>CollectionCycleStatusImpl</code> is ever removed, the
	 * dismiss thread runs inside that window instead of blocking on it, and
	 * this test starts failing on the first assertion.
	 */
	@Test
	public void concurrentDismissCannotLoseAReadinessMarker()
		throws InterruptedException {

		_collectionCycleStatusImpl.openCycle(_COMPANY_ID, 42L);

		CountDownLatch readinessIsMidWrite = new CountDownLatch(1);
		CountDownLatch dismissIsDone = new CountDownLatch(1);

		_collectionCycleStore.pauseAfterNextGet(
			() -> {
				readinessIsMidWrite.countDown();

				try {
					dismissIsDone.await(2, TimeUnit.SECONDS);
				}
				catch (InterruptedException interruptedException) {
					Thread.currentThread(
					).interrupt();
				}
			});

		Thread dismissThread = new Thread(
			() -> {
				try {
					readinessIsMidWrite.await(2, TimeUnit.SECONDS);
				}
				catch (InterruptedException interruptedException) {
					Thread.currentThread(
					).interrupt();
				}

				_collectionCycleStatusImpl.dismiss(_COMPANY_ID);

				dismissIsDone.countDown();
			});

		dismissThread.start();

		_collectionCycleStatusImpl.recordReadinessNotified(
			_COMPANY_ID, LocalDate.parse("2026-04-01"));

		dismissThread.join(TimeUnit.SECONDS.toMillis(2));

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertEquals(
			LocalDate.parse("2026-04-01"),
			collectionCycle.getReadinessNotifiedDate(),
			"A concurrent dismiss erased the readiness marker, which would " +
				"re-send a notification that already went out");
		assertTrue(
			collectionCycle.isSignupBannerDismissed(),
			"The dismissal itself must not be the one lost instead");
	}

	private Date _date(String instant) {
		return Date.from(Instant.parse(instant));
	}

	private Clock _tickingClock() {
		return new Clock() {

			@Override
			public ZoneId getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Instant instant() {
				return _now;
			}

			@Override
			public Clock withZone(ZoneId zoneId) {
				return this;
			}

		};
	}

	private static final long _COMPANY_ID = 20097L;

	private CollectionCycleStatusImpl _collectionCycleStatusImpl;
	private RecordingCollectionCycleStore _collectionCycleStore;
	private CollectionNotifier _collectionNotifier;
	private Instant _now;
	private SearchEvalLoggerConfiguration _searchEvalLoggerConfiguration;

	/**
	 * An in-memory stand-in that also counts, because "does not read the
	 * store" is one of the properties under test and a Mockito verification
	 * of an absence reads worse than a count.
	 */
	private static class RecordingCollectionCycleStore
		extends CollectionCycleStore {

		@Override
		public CollectionCycle get(long companyId) {
			_readCount++;

			CollectionCycle collectionCycle = _collectionCycles.get(companyId);

			if (collectionCycle == null) {
				collectionCycle = CollectionCycle.closed(companyId);
			}

			// After the read, not before: the snapshot about to be returned
			// has to be the stale one a lost update needs, which means the
			// pause happens once this method already has it in hand.

			Runnable pauseAfterGet = _pauseAfterGet.getAndSet(null);

			if (pauseAfterGet != null) {
				pauseAfterGet.run();
			}

			return collectionCycle;
		}

		/**
		 * Runs once, on the very next {@link #get}, then clears itself. Lets a
		 * test force a concurrent mutator to run inside the gap between this
		 * read and the save that follows it, which is exactly the window a
		 * lost update needs.
		 */
		private void pauseAfterNextGet(Runnable runnable) {
			_pauseAfterGet.set(runnable);
		}

		@Override
		public boolean save(CollectionCycle collectionCycle) {
			_writeCount++;

			if (_failWrites) {
				return false;
			}

			_collectionCycles.put(
				collectionCycle.getCompanyId(), collectionCycle);

			return true;
		}

		private int getReadCount() {
			return _readCount;
		}

		private int getWriteCount() {
			return _writeCount;
		}

		private void resetCounts() {
			_readCount = 0;
			_writeCount = 0;
		}

		private void setFailWrites(boolean failWrites) {
			_failWrites = failWrites;
		}

		private final Map<Long, CollectionCycle> _collectionCycles =
			new HashMap<>();
		private boolean _failWrites;
		private final AtomicReference<Runnable> _pauseAfterGet =
			new AtomicReference<>();
		private int _readCount;
		private int _writeCount;

	}

}
