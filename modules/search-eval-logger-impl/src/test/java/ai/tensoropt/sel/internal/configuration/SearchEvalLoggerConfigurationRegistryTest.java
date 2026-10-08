/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.configuration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.module.configuration.ConfigurationException;

import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The fallback for an unreadable configuration is built from the
 * <code>&#64;Meta.AD</code> defaults, the only copy of them (TO-95). This pins
 * those defaults to the table in DESIGN.md section 5, so a change to one is a
 * deliberate edit here too.
 */
public class SearchEvalLoggerConfigurationRegistryTest {

	@BeforeEach
	public void setUp() throws Exception {
		_registry = new SearchEvalLoggerConfigurationRegistry();

		ConfigurationProvider configurationProvider = mock(
			ConfigurationProvider.class);

		when(
			configurationProvider.getCompanyConfiguration(
				eq(SearchEvalLoggerConfiguration.class), anyLong())
		).thenThrow(
			new ConfigurationException("unreadable")
		);

		Field field = SearchEvalLoggerConfigurationRegistry.class.
			getDeclaredField("_configurationProvider");

		field.setAccessible(true);

		field.set(_registry, configurationProvider);
	}

	/**
	 * The one default whose direction matters most: an unreadable
	 * configuration must never turn collection on.
	 */
	@Test
	public void anUnreadableConfigurationFallsBackToCollectionOff() {
		assertFalse(_registry.getConfiguration(1L).enabled());
	}

	@Test
	public void theFallbackIsTheDesignDocumentDefaults() {
		SearchEvalLoggerConfiguration configuration =
			_registry.getConfiguration(1L);

		assertEquals(100, configuration.captureDepth());
		assertEquals(90, configuration.retentionDays());
		assertArrayEquals(
			new String[] {"title", "snippet"},
			configuration.capturedFieldNames());
		assertTrue(configuration.excludeSuggestionTraffic());
		assertFalse(configuration.admitFacetOnlySearches());
		assertEquals(7, configuration.cohortSaltRotationDays());
		assertFalse(configuration.requireWebRequestContext());
		assertEquals(2000, configuration.queryTextCap());
		assertEquals(1.0, configuration.samplingRate(), 0.0);
		assertEquals(0, configuration.excludedEntryClassNames().length);
		assertEquals(30, configuration.readinessMinimumDays());
		assertEquals(500, configuration.readinessMinimumEvents());
		assertTrue(configuration.showEvaluationServiceLinks());
	}

	private SearchEvalLoggerConfigurationRegistry _registry;

}
