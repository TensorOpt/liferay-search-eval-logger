/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.purge;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.transaction.TransactionInvoker;

import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.TestInjection;
import ai.tensoropt.sel.internal.configuration.SearchEvalLoggerConfigurationRegistry;
import ai.tensoropt.sel.service.SearchEventLocalService;
import ai.tensoropt.sel.service.SearchHitLocalService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Deletes events and hits past the retention window (DESIGN.md 3.4). A
 * stubbed {@link TransactionInvoker} stands in for
 * <code>TransactionInvokerUtil</code>, whose own static invoker is unset
 * outside a running portal, so every case needs one.
 */
public class RetentionPurgerTest {

	@BeforeEach
	public void setUp() throws Exception {
		_retentionPurger = new RetentionPurger();

		_searchEvalLoggerConfigurationRegistry = mock(
			SearchEvalLoggerConfigurationRegistry.class);
		_searchEventLocalService = mock(SearchEventLocalService.class);
		_searchHitLocalService = mock(SearchHitLocalService.class);

		TestInjection.set(
			_retentionPurger, "_searchEvalLoggerConfigurationRegistry",
			_searchEvalLoggerConfigurationRegistry);
		TestInjection.set(
			_retentionPurger, "_searchEventLocalService",
			_searchEventLocalService);
		TestInjection.set(
			_retentionPurger, "_searchHitLocalService",
			_searchHitLocalService);

		_retentionPurger.setClock(Clock.fixed(_NOW, ZoneOffset.UTC));

		TestInjection.set(
			_retentionPurger, "_transactionInvoker",
			TestInjection.PASSTHROUGH_TRANSACTION_INVOKER);
	}

	@Test
	public void retentionOfZeroIsClampedToOneDay() throws Exception {
		_stubRetentionDays(0);

		_retentionPurger.purge(_COMPANY_ID);

		Date expectedCutoff = Date.from(_NOW.minus(Duration.ofDays(1)));

		verify(
			_searchEventLocalService
		).deleteByCompanyIdAndCreateDateBefore(
			_COMPANY_ID, expectedCutoff
		);
	}

	@Test
	public void negativeRetentionIsClampedToOneDay() throws Exception {
		_stubRetentionDays(-30);

		_retentionPurger.purge(_COMPANY_ID);

		Date expectedCutoff = Date.from(_NOW.minus(Duration.ofDays(1)));

		verify(
			_searchEventLocalService
		).deleteByCompanyIdAndCreateDateBefore(
			_COMPANY_ID, expectedCutoff
		);
	}

	@Test
	public void theSameCutoffReachesBothDeletes() throws Exception {
		_stubRetentionDays(90);

		_retentionPurger.purge(_COMPANY_ID);

		Date expectedCutoff = Date.from(_NOW.minus(Duration.ofDays(90)));

		verify(
			_searchEventLocalService
		).deleteByCompanyIdAndCreateDateBefore(
			_COMPANY_ID, expectedCutoff
		);
		verify(
			_searchHitLocalService
		).deleteByCompanyIdAndCreateDateBefore(
			_COMPANY_ID, expectedCutoff
		);
	}

	@Test
	public void aFailingDeleteIsRethrownRatherThanSwallowed() {
		_stubRetentionDays(90);

		when(
			_searchEventLocalService.deleteByCompanyIdAndCreateDateBefore(
				anyLong(), any(Date.class))
		).thenThrow(
			new RuntimeException("boom")
		);

		assertThrows(
			RuntimeException.class,
			() -> _retentionPurger.purge(_COMPANY_ID));
	}

	/**
	 * The scenario the log line's own fix (TO-144) guards: zero events
	 * deleted but hits still were, which must not throw and must still
	 * reach both deletes with the same cutoff. The log line's own guard,
	 * {@code (deleted[0] + deleted[1]) > 0}, is deliberately left unpinned
	 * here: RetentionPurger reads its Log through LogFactoryUtil.getLog, a
	 * static Liferay facade, and pinning that one log line would need
	 * log4j-core at test compile scope plus an appender, which CLAUDE.md's
	 * YAGNI does not justify.
	 */
	@Test
	public void purgeSucceedsWhenOnlyOrphanHitsAreDeleted() throws Exception {
		_stubRetentionDays(90);

		when(
			_searchEventLocalService.deleteByCompanyIdAndCreateDateBefore(
				anyLong(), any(Date.class))
		).thenReturn(
			0
		);
		when(
			_searchHitLocalService.deleteByCompanyIdAndCreateDateBefore(
				anyLong(), any(Date.class))
		).thenReturn(
			5
		);

		_retentionPurger.purge(_COMPANY_ID);

		Date expectedCutoff = Date.from(_NOW.minus(Duration.ofDays(90)));

		verify(
			_searchEventLocalService
		).deleteByCompanyIdAndCreateDateBefore(
			_COMPANY_ID, expectedCutoff
		);
		verify(
			_searchHitLocalService
		).deleteByCompanyIdAndCreateDateBefore(
			_COMPANY_ID, expectedCutoff
		);
	}

	private void _stubRetentionDays(int retentionDays) {
		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration = mock(
			SearchEvalLoggerConfiguration.class);

		when(
			searchEvalLoggerConfiguration.retentionDays()
		).thenReturn(
			retentionDays
		);
		when(
			_searchEvalLoggerConfigurationRegistry.getConfiguration(
				_COMPANY_ID)
		).thenReturn(
			searchEvalLoggerConfiguration
		);
	}

	private static final long _COMPANY_ID = 20097L;

	private static final Instant _NOW = Instant.parse(
		"2026-06-01T03:30:00Z");

	private RetentionPurger _retentionPurger;
	private SearchEvalLoggerConfigurationRegistry
		_searchEvalLoggerConfigurationRegistry;
	private SearchEventLocalService _searchEventLocalService;
	private SearchHitLocalService _searchHitLocalService;

}
