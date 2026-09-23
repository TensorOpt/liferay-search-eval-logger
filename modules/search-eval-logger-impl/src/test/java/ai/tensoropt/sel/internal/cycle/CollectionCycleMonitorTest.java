/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.cycle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.tensoropt.sel.api.CollectionCycle;
import ai.tensoropt.sel.api.SearchInterceptionStatus;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.configuration.SearchEvalLoggerConfigurationRegistry;
import ai.tensoropt.sel.internal.notifications.CollectionNotifier;
import ai.tensoropt.sel.service.SearchEventLocalService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The daily check of DESIGN.md 3.6. Both notifications are specified as "sent
 * once per cycle", which is the property most likely to be broken by a later
 * change and the one an administrator would notice first, as a notification
 * arriving every morning for a month.
 */
public class CollectionCycleMonitorTest {

	@BeforeEach
	public void setUp() {
		_collectionCycleStatusImpl = mock(CollectionCycleStatusImpl.class);
		_collectionNotifier = mock(CollectionNotifier.class);
		_searchEventLocalService = mock(SearchEventLocalService.class);
		_searchInterceptionStatus = mock(SearchInterceptionStatus.class);

		when(
			_searchInterceptionStatus.isIntercepting()
		).thenReturn(
			true
		);

		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration = mock(
			SearchEvalLoggerConfiguration.class);

		when(
			searchEvalLoggerConfiguration.enabled()
		).thenReturn(
			true
		);
		when(
			searchEvalLoggerConfiguration.readinessMinimumDays()
		).thenReturn(
			30
		);
		when(
			searchEvalLoggerConfiguration.readinessMinimumEvents()
		).thenReturn(
			500
		);

		_searchEvalLoggerConfiguration = searchEvalLoggerConfiguration;

		SearchEvalLoggerConfigurationRegistry
			searchEvalLoggerConfigurationRegistry = mock(
				SearchEvalLoggerConfigurationRegistry.class);

		when(
			searchEvalLoggerConfigurationRegistry.getConfiguration(_COMPANY_ID)
		).thenReturn(
			searchEvalLoggerConfiguration
		);

		_collectionCycleMonitor = new CollectionCycleMonitor();

		_collectionCycleMonitor.setClock(
			Clock.fixed(Instant.parse("2026-06-01T02:00:00Z"), ZoneOffset.UTC));
		_collectionCycleMonitor.setCollaborators(
			_collectionCycleStatusImpl, _collectionNotifier,
			searchEvalLoggerConfigurationRegistry, _searchEventLocalService,
			_searchInterceptionStatus);
	}

	/**
	 * DESIGN.md 3.6: disabling logging clears the collection start date. The
	 * configuration listener does this the moment the setting is saved; this
	 * is the reconciliation that covers the listener never running (EC-14).
	 */
	@Test
	public void disabledLoggingClosesTheCycle() {
		when(
			_searchEvalLoggerConfiguration.enabled()
		).thenReturn(
			false
		);

		_collectionCycleMonitor.check(_COMPANY_ID);

		verify(_collectionCycleStatusImpl).closeCycle(_COMPANY_ID);
		verify(_collectionNotifier, never()).notifyStall(anyLong(), anyLong());
		verify(
			_collectionNotifier, never()
		).notifyReadiness(
			anyLong(), anyLong()
		);
	}

	@Test
	public void enabledLoggingOpensACycleThatWasNeverObserved() {
		_setCycle(CollectionCycle.open(_COMPANY_ID, 0));

		_collectionCycleMonitor.check(_COMPANY_ID);

		verify(_collectionCycleStatusImpl).openCycle(_COMPANY_ID, 0);
	}

	@Test
	public void aBypassedInstanceCollectingNothingIsNotified() {
		when(
			_searchInterceptionStatus.isIntercepting()
		).thenReturn(
			false
		);

		_setCycle(CollectionCycle.open(_COMPANY_ID, 42L));

		_collectionCycleMonitor.check(_COMPANY_ID);

		verify(_collectionNotifier).notifyStall(_COMPANY_ID, 42L);
		verify(
			_collectionCycleStatusImpl
		).recordStallNotified(
			_COMPANY_ID, LocalDate.parse("2026-06-01")
		);
	}

	@Test
	public void theStallNotificationIsSentOnlyOncePerCycle() {
		when(
			_searchInterceptionStatus.isIntercepting()
		).thenReturn(
			false
		);

		CollectionCycle collectionCycle = CollectionCycle.open(_COMPANY_ID, 42L);

		_setCycle(
			collectionCycle.withStallNotifiedDate(
				LocalDate.parse("2026-05-01")));

		_collectionCycleMonitor.check(_COMPANY_ID);

		verify(_collectionNotifier, never()).notifyStall(anyLong(), anyLong());
	}

	@Test
	public void aLogThatMeetsBothThresholdsIsAnnounced() {
		_setCycle(_cycleStartedOn(LocalDate.parse("2026-05-02")));

		_setEventCount(500L);

		_collectionCycleMonitor.check(_COMPANY_ID);

		verify(_collectionNotifier).notifyReadiness(_COMPANY_ID, 42L);
		verify(
			_collectionCycleStatusImpl
		).recordReadinessNotified(
			_COMPANY_ID, LocalDate.parse("2026-06-01")
		);
	}

	@Test
	public void theReadinessNotificationIsSentOnlyOncePerCycle() {
		CollectionCycle collectionCycle = _cycleStartedOn(
			LocalDate.parse("2026-05-02"));

		_setCycle(
			collectionCycle.withReadinessNotifiedDate(
				LocalDate.parse("2026-05-20")));

		_setEventCount(1000000L);

		_collectionCycleMonitor.check(_COMPANY_ID);

		verify(
			_collectionNotifier, never()
		).notifyReadiness(
			anyLong(), anyLong()
		);
	}

	@Test
	public void aLogShortOfTheEventThresholdIsNotAnnounced() {
		_setCycle(_cycleStartedOn(LocalDate.parse("2026-05-02")));

		_setEventCount(499L);

		_collectionCycleMonitor.check(_COMPANY_ID);

		verify(
			_collectionNotifier, never()
		).notifyReadiness(
			anyLong(), anyLong()
		);
	}

	/**
	 * The count is the only condition that costs a query. On an instance that
	 * started collecting yesterday the day arithmetic settles it, and running
	 * the query anyway would put a daily scan on every instance that will not
	 * be ready for another month.
	 */
	@Test
	public void theEventCountIsNotQueriedBeforeTheDayThresholdIsMet() {
		_setCycle(_cycleStartedOn(LocalDate.parse("2026-05-31")));

		_collectionCycleMonitor.check(_COMPANY_ID);

		verify(
			_searchEventLocalService, never()
		).getCountByCompanyIdAndCreateDateOnOrAfter(
			anyLong(), any(Date.class)
		);
	}

	/**
	 * Events are counted from midnight UTC of the collection start day, which
	 * is the inclusive lower bound the date itself stands for. Counting from
	 * the following midnight would silently drop the first day.
	 */
	@Test
	public void eventsAreCountedFromMidnightOfTheCollectionStartDay() {
		_setCycle(_cycleStartedOn(LocalDate.parse("2026-05-02")));

		_setEventCount(500L);

		_collectionCycleMonitor.check(_COMPANY_ID);

		verify(
			_searchEventLocalService
		).getCountByCompanyIdAndCreateDateOnOrAfter(
			eq(_COMPANY_ID),
			eq(Date.from(Instant.parse("2026-05-02T00:00:00Z")))
		);
	}

	/**
	 * A stalled cycle has collected nothing, so there is nothing to be ready.
	 * Sending both notifications on the same morning would be two ways of
	 * saying opposite things.
	 */
	@Test
	public void aStalledCycleIsNotAlsoAnnouncedAsReady() {
		when(
			_searchInterceptionStatus.isIntercepting()
		).thenReturn(
			false
		);

		_setCycle(CollectionCycle.open(_COMPANY_ID, 42L));

		_collectionCycleMonitor.check(_COMPANY_ID);

		verify(
			_collectionNotifier, never()
		).notifyReadiness(
			anyLong(), anyLong()
		);
	}

	private CollectionCycle _cycleStartedOn(LocalDate localDate) {
		CollectionCycle collectionCycle = CollectionCycle.open(_COMPANY_ID, 42L);

		return collectionCycle.withCollectionStartDate(localDate);
	}

	private void _setCycle(CollectionCycle collectionCycle) {
		when(
			_collectionCycleStatusImpl.getCollectionCycle(_COMPANY_ID)
		).thenReturn(
			collectionCycle
		);
	}

	private void _setEventCount(long eventCount) {
		when(
			_searchEventLocalService.getCountByCompanyIdAndCreateDateOnOrAfter(
				anyLong(), any(Date.class))
		).thenReturn(
			eventCount
		);
	}

	private static final long _COMPANY_ID = 20097L;

	private CollectionCycleMonitor _collectionCycleMonitor;
	private CollectionCycleStatusImpl _collectionCycleStatusImpl;
	private CollectionNotifier _collectionNotifier;
	private SearchEvalLoggerConfiguration _searchEvalLoggerConfiguration;
	private SearchEventLocalService _searchEventLocalService;
	private SearchInterceptionStatus _searchInterceptionStatus;

}
