/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.tensoropt.sel.api.CollectionCycle;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.configuration.SearchEvalLoggerConfigurationRegistry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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

		_collectionCycleStatusImpl = new CollectionCycleStatusImpl();

		_collectionCycleStatusImpl.setClock(_tickingClock());
		_collectionCycleStatusImpl.setCollectionCycleStore(
			_collectionCycleStore);
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

		_collectionCycleStatusImpl.closeCycle(_COMPANY_ID);

		CollectionCycle collectionCycle =
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID);

		assertFalse(collectionCycle.isOpen());
		assertNull(collectionCycle.getCollectionStartDate());
		assertNull(collectionCycle.getReadinessNotifiedDate());
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
				return CollectionCycle.closed(companyId);
			}

			return collectionCycle;
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
		private int _readCount;
		private int _writeCount;

	}

}
