/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.zeroresult;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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
