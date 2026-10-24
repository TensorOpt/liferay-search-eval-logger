/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.zeroresult;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.tensoropt.sel.api.AudienceType;
import ai.tensoropt.sel.service.SearchEventLocalService;

import com.liferay.petra.function.UnsafeConsumer;
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
			searchEventLocalService, 123L);

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
			ZeroResultPanelLoader.load(searchEventLocalService, 123L));
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
			ZeroResultPanelLoader.load(searchEventLocalService, 123L));
	}

}
