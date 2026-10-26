/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.liferay.counter.kernel.service.CounterLocalService;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.transaction.TransactionConfig;
import com.liferay.portal.kernel.transaction.TransactionInvoker;

import ai.tensoropt.sel.api.AudienceType;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.TestInjection;
import ai.tensoropt.sel.internal.capture.CapturedSearchEvent;
import ai.tensoropt.sel.internal.capture.CapturedSearchHit;
import ai.tensoropt.sel.internal.cohort.CohortSaltRegistry;
import ai.tensoropt.sel.internal.configuration.SearchEvalLoggerConfigurationRegistry;
import ai.tensoropt.sel.internal.cycle.CollectionCycleStatusImpl;
import ai.tensoropt.sel.internal.statistics.SearchEvalLoggerStatisticsImpl;
import ai.tensoropt.sel.model.SearchEvent;
import ai.tensoropt.sel.model.SearchHit;
import ai.tensoropt.sel.service.SearchEventLocalService;
import ai.tensoropt.sel.service.SearchHitLocalService;

import java.util.Date;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The single-transaction write of one event plus its hits (DESIGN.md 3.3),
 * opened through {@link TransactionInvoker} rather than
 * <code>&#64;Transactional</code> because this is a plain component. A
 * stubbed {@link TransactionInvoker} stands in for
 * <code>TransactionInvokerUtil</code> so both the commit and the failure path
 * can be driven without a running portal.
 */
public class SearchEventPersistenceMessageListenerTest {

	@BeforeEach
	public void setUp() throws Exception {
		_listener = new SearchEventPersistenceMessageListener();

		// TransactionInvokerUtil's own static invoker is null outside a
		// running portal, so every test needs a stub; only the failure case
		// below replaces this one with a throwing stub.

		TestInjection.set(
			_listener, "_transactionInvoker",
			TestInjection.PASSTHROUGH_TRANSACTION_INVOKER);

		_cohortSaltRegistry = mock(CohortSaltRegistry.class);
		_collectionCycleStatusImpl = mock(CollectionCycleStatusImpl.class);
		_counterLocalService = mock(CounterLocalService.class);
		_searchEvalLoggerConfigurationRegistry = mock(
			SearchEvalLoggerConfigurationRegistry.class);
		_searchEvalLoggerStatisticsImpl = mock(
			SearchEvalLoggerStatisticsImpl.class);
		_searchEventLocalService = mock(SearchEventLocalService.class);
		_searchHitLocalService = mock(SearchHitLocalService.class);
		_userLocalService = mock(UserLocalService.class);

		TestInjection.set(
			_listener, "_cohortSaltRegistry", _cohortSaltRegistry);
		TestInjection.set(
			_listener, "_collectionCycleStatusImpl",
			_collectionCycleStatusImpl);
		TestInjection.set(
			_listener, "_counterLocalService", _counterLocalService);
		TestInjection.set(
			_listener, "_searchEvalLoggerConfigurationRegistry",
			_searchEvalLoggerConfigurationRegistry);
		TestInjection.set(
			_listener, "_searchEvalLoggerStatisticsImpl",
			_searchEvalLoggerStatisticsImpl);
		TestInjection.set(
			_listener, "_searchEventLocalService", _searchEventLocalService);
		TestInjection.set(
			_listener, "_searchHitLocalService", _searchHitLocalService);
		TestInjection.set(_listener, "_userLocalService", _userLocalService);

		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration = mock(
			SearchEvalLoggerConfiguration.class);

		when(
			searchEvalLoggerConfiguration.cohortSaltRotationDays()
		).thenReturn(
			7
		);
		when(
			_searchEvalLoggerConfigurationRegistry.getConfiguration(anyLong())
		).thenReturn(
			searchEvalLoggerConfiguration
		);

		when(
			_counterLocalService.increment(anyString())
		).thenReturn(
			1L
		);

		_searchEvent = mock(SearchEvent.class);

		when(
			_searchEventLocalService.createSearchEvent(anyLong())
		).thenReturn(
			_searchEvent
		);
		when(_searchEvent.getCreateDate()).thenReturn(_CREATE_DATE);

		_searchHit = mock(SearchHit.class);

		when(
			_searchHitLocalService.createSearchHit(anyLong())
		).thenReturn(
			_searchHit
		);
	}

	@Test
	public void aNonCapturedSearchEventPayloadIsIgnored() {
		Message message = new Message();

		message.setPayload("not a captured search event");

		_listener.receive(message);

		verify(_searchEventLocalService, never()).addSearchEvent(any());
		verify(
			_searchEvalLoggerStatisticsImpl, never()
		).incrementPersistedEventCount();
		verify(
			_searchEvalLoggerStatisticsImpl, never()
		).incrementDroppedEventCount();
	}

	@Test
	public void aTransactionFailureIsDroppedAndNeverRecordsPersistence()
		throws Exception {

		TestInjection.set(
			_listener, "_transactionInvoker", _THROWING_TRANSACTION_INVOKER);

		_listener.receive(_message(_capturedSearchEvent(1L)));

		verify(_searchEvalLoggerStatisticsImpl).incrementDroppedEventCount();
		verify(
			_searchEvalLoggerStatisticsImpl, never()
		).incrementPersistedEventCount();
		verify(
			_collectionCycleStatusImpl, never()
		).recordPersistedEvent(anyLong(), any(Date.class));
	}

	@Test
	public void aSuccessfulWriteIsPersistedAndRecordsTheCollectionCycle() {
		CapturedSearchEvent capturedSearchEvent = _capturedSearchEvent(1L);

		capturedSearchEvent.setCompanyId(_COMPANY_ID);

		_listener.receive(_message(capturedSearchEvent));

		verify(_searchEvalLoggerStatisticsImpl).incrementPersistedEventCount();
		verify(
			_searchEvalLoggerStatisticsImpl, never()
		).incrementDroppedEventCount();
		verify(
			_collectionCycleStatusImpl
		).recordPersistedEvent(
			_COMPANY_ID, new Date(_CREATE_TIME)
		);
	}

	@Test
	public void aNonPositiveUserIdIsAGuest() {
		_listener.receive(_message(_capturedSearchEvent(0L)));

		verify(_searchEvent).setAudienceType(AudienceType.GUEST.name());

		// D9: a guest row carries no cohortHash at all, not merely a null
		// one that happened to come back from an unstubbed hash call; the
		// salt hash is never computed for a guest in the first place.
		verify(_searchEvent).setCohortHash(null);
		verifyNoInteractions(_cohortSaltRegistry);
	}

	@Test
	public void anUnresolvableUserIdIsAGuest() {
		when(_userLocalService.fetchUser(7L)).thenReturn(null);

		_listener.receive(_message(_capturedSearchEvent(7L)));

		verify(_searchEvent).setAudienceType(AudienceType.GUEST.name());
	}

	@Test
	public void theGuestUserIsAGuest() {
		User user = mock(User.class);

		when(user.isGuestUser()).thenReturn(true);
		when(_userLocalService.fetchUser(7L)).thenReturn(user);

		_listener.receive(_message(_capturedSearchEvent(7L)));

		verify(_searchEvent).setAudienceType(AudienceType.GUEST.name());
	}

	@Test
	public void anyOtherResolvedUserIsAuthenticated() {
		User user = mock(User.class);

		when(user.isGuestUser()).thenReturn(false);
		when(_userLocalService.fetchUser(7L)).thenReturn(user);
		when(
			_cohortSaltRegistry.hash(anyLong(), anyLong(), anyInt())
		).thenReturn("a1b2c3");

		_listener.receive(_message(_capturedSearchEvent(7L)));

		verify(_searchEvent).setAudienceType(AudienceType.AUTHENTICATED.name());

		// D9: unlike a guest row, an authenticated row carries the salt
		// hash the registry computed, not null.
		verify(_searchEvent).setCohortHash("a1b2c3");
	}

	@Test
	public void everyHitCarriesTheEventsUuidCompanyIdAndCreateDate() {
		CapturedSearchEvent capturedSearchEvent = _capturedSearchEvent(1L);

		capturedSearchEvent.setCompanyId(_COMPANY_ID);
		capturedSearchEvent.addHit(new CapturedSearchHit());
		capturedSearchEvent.addHit(new CapturedSearchHit());

		_listener.receive(_message(capturedSearchEvent));

		ArgumentCaptor<String> uuidCaptor = ArgumentCaptor.forClass(
			String.class);

		verify(_searchEvent).setUuid(uuidCaptor.capture());

		String uuid = uuidCaptor.getValue();

		verify(
			_searchHitLocalService, times(2)
		).createSearchHit(
			anyLong()
		);
		verify(
			_searchHit, times(2)
		).setSearchEventUuid(
			uuid
		);
		verify(
			_searchHit, times(2)
		).setCompanyId(
			_COMPANY_ID
		);
		verify(
			_searchHit, times(2)
		).setCreateDate(
			_CREATE_DATE
		);
	}

	@Test
	public void loggedHitCountMatchesTheNumberOfHits() {
		CapturedSearchEvent capturedSearchEvent = _capturedSearchEvent(1L);

		capturedSearchEvent.addHit(new CapturedSearchHit());
		capturedSearchEvent.addHit(new CapturedSearchHit());
		capturedSearchEvent.addHit(new CapturedSearchHit());

		_listener.receive(_message(capturedSearchEvent));

		verify(_searchEvent).setLoggedHitCount(3);
	}

	private CapturedSearchEvent _capturedSearchEvent(long userId) {
		CapturedSearchEvent capturedSearchEvent = new CapturedSearchEvent();

		capturedSearchEvent.setUserId(userId);
		capturedSearchEvent.setCreateTime(_CREATE_TIME);

		return capturedSearchEvent;
	}

	private Message _message(CapturedSearchEvent capturedSearchEvent) {
		Message message = new Message();

		message.setPayload(capturedSearchEvent);

		return message;
	}

	private static final long _COMPANY_ID = 20097L;

	private static final Date _CREATE_DATE = new Date(1780000000000L);

	private static final long _CREATE_TIME = _CREATE_DATE.getTime();

	/**
	 * Simulates the transaction failing before anything inside it runs, the
	 * same way a rolled back TransactionInvokerUtil.invoke would.
	 */
	private static final TransactionInvoker _THROWING_TRANSACTION_INVOKER =
		new TransactionInvoker() {

			@Override
			public <T> T invoke(
					TransactionConfig transactionConfig,
					Callable<T> callable)
				throws Throwable {

				throw new RuntimeException("boom");
			}

		};

	private CohortSaltRegistry _cohortSaltRegistry;
	private CollectionCycleStatusImpl _collectionCycleStatusImpl;
	private CounterLocalService _counterLocalService;
	private SearchEventPersistenceMessageListener _listener;
	private SearchEvalLoggerConfigurationRegistry
		_searchEvalLoggerConfigurationRegistry;
	private SearchEvalLoggerStatisticsImpl _searchEvalLoggerStatisticsImpl;
	private SearchEvent _searchEvent;
	private SearchEventLocalService _searchEventLocalService;
	private SearchHit _searchHit;
	private SearchHitLocalService _searchHitLocalService;
	private UserLocalService _userLocalService;

}
