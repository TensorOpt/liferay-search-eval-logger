/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.zeroresult;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.tensoropt.sel.api.AudienceType;
import ai.tensoropt.sel.service.SearchEventLocalService;

import com.liferay.petra.function.UnsafeConsumer;
import com.liferay.portal.kernel.cache.PortalCache;
import com.liferay.portal.kernel.exception.SystemException;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Failure isolation (DESIGN.md 6.3): either of the two aggregates
 * {@code SearchEventLocalService} computes can throw, and neither failure
 * may propagate past {@link ZeroResultPanelLoader#load}, since this is one
 * sheet on a screen the rest of which must keep rendering (the export
 * controls, the admission counters).
 */
public class ZeroResultPanelLoaderTest {

	@Test
	public void buildsThePanelWhenBothAggregatesSucceed() {
		SearchEventLocalService searchEventLocalService = mock(
			SearchEventLocalService.class);

		doAnswer(
			invocation -> {
				UnsafeConsumer<String, ?> queryConsumer =
					invocation.getArgument(1);

				queryConsumer.accept("widget");
				queryConsumer.accept("WIDGET");

				return null;
			}
		).when(
			searchEventLocalService
		).forEachZeroResultFirstPageQuery(anyLong(), any());

		when(
			searchEventLocalService.getFirstPageSearchCountsByAudienceType(
				anyLong())
		).thenReturn(
			Map.of(AudienceType.GUEST.name(), new long[] {2, 2})
		);

		ZeroResultPanel zeroResultPanel = ZeroResultPanelLoader.load(
			_emptyPortalCache(), searchEventLocalService, 123L);

		assertArrayEquals(
			new String[] {"2", "2", "100.0"},
			zeroResultPanel.getOverallMessageArguments());
		assertEquals(1, zeroResultPanel.getTopQueries().size());
		assertEquals(
			"widget", zeroResultPanel.getTopQueries().get(0).getQuery());
		assertEquals(
			2, zeroResultPanel.getTopQueries().get(0).getSearchCount());
	}

	@Test
	public void returnsNullRatherThanThrowingWhenTheQueryStreamFails() {
		SearchEventLocalService searchEventLocalService = mock(
			SearchEventLocalService.class);

		doThrow(
			new SystemException("Query timed out")
		).when(
			searchEventLocalService
		).forEachZeroResultFirstPageQuery(anyLong(), any());

		assertNull(
			ZeroResultPanelLoader.load(
				_emptyPortalCache(), searchEventLocalService, 123L));
	}

	@Test
	public void returnsNullRatherThanThrowingWhenTheAudienceCountsFail() {
		SearchEventLocalService searchEventLocalService = mock(
			SearchEventLocalService.class);

		when(
			searchEventLocalService.getFirstPageSearchCountsByAudienceType(
				anyLong())
		).thenThrow(
			new RuntimeException("Unsupported query on this database")
		);

		assertNull(
			ZeroResultPanelLoader.load(
				_emptyPortalCache(), searchEventLocalService, 123L));
	}

	/**
	 * D11 (TO-144): a distinct normalized query past
	 * {@link ZeroResultQueryCounts#MAX_DISTINCT_QUERIES} must be dropped, not
	 * merely tolerated, and the table must say so. The dropped key is
	 * searched far more than any admitted one so that, if it were wrongly
	 * kept, it would be impossible to miss at the top of the table; this is
	 * what makes its absence below evidence of the cap rather than a
	 * coincidence of row counts.
	 */
	@Test
	public void aDistinctQueryPastTheCapIsDroppedAndTheTableIsMarkedApproximate() {
		SearchEventLocalService searchEventLocalService = mock(
			SearchEventLocalService.class);

		String droppedQuery = "query one too many";

		doAnswer(
			invocation -> {
				UnsafeConsumer<String, ?> queryConsumer =
					invocation.getArgument(1);

				for (int i = 0; i < ZeroResultQueryCounts.MAX_DISTINCT_QUERIES;
					 i++) {

					queryConsumer.accept("query-" + i);
				}

				for (int i = 0; i < 50; i++) {
					queryConsumer.accept(droppedQuery);
				}

				return null;
			}
		).when(
			searchEventLocalService
		).forEachZeroResultFirstPageQuery(anyLong(), any());

		when(
			searchEventLocalService.getFirstPageSearchCountsByAudienceType(
				anyLong())
		).thenReturn(
			Map.of(
				AudienceType.GUEST.name(),
				new long[] {
					ZeroResultQueryCounts.MAX_DISTINCT_QUERIES + 50,
					ZeroResultQueryCounts.MAX_DISTINCT_QUERIES + 50
				})
		);

		ZeroResultPanel zeroResultPanel = ZeroResultPanelLoader.load(
			_emptyPortalCache(), searchEventLocalService, 123L);

		assertTrue(
			zeroResultPanel.isTopQueriesApproximate(),
			"A distinct query past the cap must mark the table approximate");

		boolean droppedQuerySurfaced = false;

		for (ZeroResultQuery zeroResultQuery :
				zeroResultPanel.getTopQueries()) {

			if (droppedQuery.equals(zeroResultQuery.getQuery())) {
				droppedQuerySurfaced = true;
			}
		}

		assertFalse(
			droppedQuerySurfaced,
			"The dropped key must not appear in the table even though it " +
				"was searched far more than any admitted key, which would " +
					"otherwise place it at the top");
	}

	@Test
	public void aCachedPanelIsServedWithoutReadingTheLog() {
		SearchEventLocalService searchEventLocalService = mock(
			SearchEventLocalService.class);
		PortalCache<Long, ZeroResultPanel> portalCache = _emptyPortalCache();
		ZeroResultPanel cachedZeroResultPanel = ZeroResultPanel.build(
			Map.of(), Map.of());

		when(
			portalCache.get(123L)
		).thenReturn(
			cachedZeroResultPanel
		);

		assertSame(
			cachedZeroResultPanel,
			ZeroResultPanelLoader.load(
				portalCache, searchEventLocalService, 123L));

		verifyNoInteractions(searchEventLocalService);
	}

	@Test
	public void aComputedPanelIsCachedForAnHour() {
		SearchEventLocalService searchEventLocalService = mock(
			SearchEventLocalService.class);

		when(
			searchEventLocalService.getFirstPageSearchCountsByAudienceType(
				anyLong())
		).thenReturn(
			Map.of()
		);

		PortalCache<Long, ZeroResultPanel> portalCache = _emptyPortalCache();

		ZeroResultPanel zeroResultPanel = ZeroResultPanelLoader.load(
			portalCache, searchEventLocalService, 123L);

		verify(
			portalCache
		).put(
			123L, zeroResultPanel, 3600
		);
	}

	@Test
	public void aFailedComputationIsNotCached() {
		SearchEventLocalService searchEventLocalService = mock(
			SearchEventLocalService.class);

		when(
			searchEventLocalService.getFirstPageSearchCountsByAudienceType(
				anyLong())
		).thenThrow(
			new SystemException("database unavailable")
		);

		PortalCache<Long, ZeroResultPanel> portalCache = _emptyPortalCache();

		assertNull(
			ZeroResultPanelLoader.load(
				portalCache, searchEventLocalService, 123L));

		verify(
			portalCache, never()
		).put(
			eq(123L), any(), anyInt()
		);
	}

	@SuppressWarnings("unchecked")
	private PortalCache<Long, ZeroResultPanel> _emptyPortalCache() {
		return mock(PortalCache.class);
	}

}
