/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.service.PortletPreferencesLocalService;

import ai.tensoropt.sel.api.CollectionCycle;
import ai.tensoropt.sel.internal.TestInjection;

import javax.portlet.PortletPreferences;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The collection cycle of DESIGN.md 3.6, stored as company-scoped portlet
 * preferences rather than a Service Builder entity. Nothing here throws, so
 * every failure mode has to degrade to a specific, deliberate fallback
 * rather than propagate.
 */
public class CollectionCycleStoreTest {

	@BeforeEach
	public void setUp() throws Exception {
		_collectionCycleStore = new CollectionCycleStore();

		_portletPreferencesLocalService = mock(
			PortletPreferencesLocalService.class);

		TestInjection.set(
			_collectionCycleStore, "_portletPreferencesLocalService",
			_portletPreferencesLocalService);

		_portletPreferences = mock(PortletPreferences.class);

		when(
			_portletPreferencesLocalService.getPreferences(
				anyLong(), anyLong(), anyInt(), anyLong(), anyString())
		).thenReturn(
			_portletPreferences
		);
	}

	@Test
	public void missingOpenReadsAsClosed() {
		_stub("open", null);

		CollectionCycle collectionCycle = _collectionCycleStore.get(
			_COMPANY_ID);

		assertFalse(collectionCycle.isOpen());
		assertEquals(
			CollectionCycle.closed(_COMPANY_ID), collectionCycle);
	}

	@Test
	public void anUnparseableDateReadsAsNullButKeepsTheOtherFields() {
		_stub("open", "true");
		_stub("enabledByUserId", "42");
		_stub("collectionStartDate", "not-a-date");

		CollectionCycle collectionCycle = _collectionCycleStore.get(
			_COMPANY_ID);

		assertTrue(collectionCycle.isOpen());
		assertEquals(42L, collectionCycle.getEnabledByUserId());
		assertNull(collectionCycle.getCollectionStartDate());
	}

	@Test
	public void anEmptyStringDateReadsAsNull() {
		_stub("open", "true");
		_stub("collectionStartDate", "");

		CollectionCycle collectionCycle = _collectionCycleStore.get(
			_COMPANY_ID);

		assertNull(collectionCycle.getCollectionStartDate());
	}

	@Test
	public void aGetPreferencesFailureReadsAsClosed() {
		when(
			_portletPreferencesLocalService.getPreferences(
				anyLong(), anyLong(), anyInt(), anyLong(), anyString())
		).thenThrow(
			new RuntimeException("boom")
		);

		CollectionCycle collectionCycle = _collectionCycleStore.get(
			_COMPANY_ID);

		assertEquals(CollectionCycle.closed(_COMPANY_ID), collectionCycle);
	}

	@Test
	public void anUpdatePreferencesFailureFailsTheSave() {
		when(
			_portletPreferencesLocalService.updatePreferences(
				anyLong(), anyInt(), anyLong(), anyString(),
				any(PortletPreferences.class))
		).thenThrow(
			new RuntimeException("boom")
		);

		boolean saved = _collectionCycleStore.save(
			CollectionCycle.closed(_COMPANY_ID));

		assertFalse(saved);
	}

	@Test
	public void theOwnerIdIsTheCompanyIdNotADefault() {
		_collectionCycleStore.get(_COMPANY_ID);

		verify(
			_portletPreferencesLocalService
		).getPreferences(
			eq(_COMPANY_ID), eq(_COMPANY_ID), anyInt(), anyLong(),
			anyString()
		);
	}

	private void _stub(String key, String value) {
		when(
			_portletPreferences.getValue(eq(key), isNull())
		).thenReturn(
			value
		);
	}

	private static final long _COMPANY_ID = 20097L;

	private CollectionCycleStore _collectionCycleStore;
	private PortletPreferences _portletPreferences;
	private PortletPreferencesLocalService _portletPreferencesLocalService;

}
