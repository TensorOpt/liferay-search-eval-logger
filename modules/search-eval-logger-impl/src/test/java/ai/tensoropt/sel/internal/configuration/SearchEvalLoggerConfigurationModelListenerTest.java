/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.configuration;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.petra.function.UnsafeConsumer;
import com.liferay.portal.kernel.service.CompanyLocalService;

import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.cycle.CollectionCycleStatusImpl;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A system-scoped save is the dangerous case, and it is dangerous in one
 * direction only.
 *
 * <p>
 * Closing a cycle clears its collection start date, and nothing can put that
 * back: a later reconciliation reopens the cycle, but the date it would need is
 * the day of the first event of a cycle that has already been running for
 * weeks. So a save that turns logging off at system scope must not be allowed
 * to reach a company that has its own setting turning it on. The saved
 * dictionary cannot answer that question - it says what was typed, not what
 * each company now resolves to - which is why every company is asked for its
 * own effective configuration.
 * </p>
 */
public class SearchEvalLoggerConfigurationModelListenerTest {

	@BeforeEach
	public void setUp() {
		_collectionCycleStatusImpl = mock(CollectionCycleStatusImpl.class);

		CompanyLocalService companyLocalService = mock(
			CompanyLocalService.class);

		doAnswer(
			invocation -> {
				UnsafeConsumer<Long, Exception> unsafeConsumer =
					invocation.getArgument(0);

				for (long companyId : _COMPANY_IDS) {
					unsafeConsumer.accept(companyId);
				}

				return null;
			}
		).when(
			companyLocalService
		).forEachCompanyId(
			org.mockito.ArgumentMatchers.any()
		);

		_searchEvalLoggerConfigurationRegistry = mock(
			SearchEvalLoggerConfigurationRegistry.class);

		_searchEvalLoggerConfigurationModelListener =
			new SearchEvalLoggerConfigurationModelListener();

		_searchEvalLoggerConfigurationModelListener.setCollaborators(
			_collectionCycleStatusImpl, companyLocalService,
			_searchEvalLoggerConfigurationRegistry);
	}

	/**
	 * A company-scoped save is about exactly one company, and the dictionary it
	 * carries is that company's configuration, so it is taken at face value.
	 */
	@Test
	public void aCompanyScopedSaveAppliesToThatCompanyOnly() {
		_setEffectiveEnabled(_COMPANY_A, false);
		_setEffectiveEnabled(_COMPANY_B, false);

		_onAfterSave(_properties(true, _COMPANY_A));

		verify(_collectionCycleStatusImpl).openCycle(_COMPANY_A, 0);
		verify(_collectionCycleStatusImpl, never()).openCycle(_COMPANY_B, 0);
		verify(_collectionCycleStatusImpl, never()).closeCycle(_COMPANY_B);
	}

	@Test
	public void aCompanyScopedDisableClosesThatCompanysCycle() {
		_onAfterSave(_properties(false, _COMPANY_A));

		verify(_collectionCycleStatusImpl).closeCycle(_COMPANY_A);
		verify(_collectionCycleStatusImpl, never()).closeCycle(_COMPANY_B);
	}

	/**
	 * The regression this class exists for. Turning logging off at system
	 * scope must not close the cycle of a company running with its own
	 * <code>enabled=true</code>, because closing it destroys a collection start
	 * date that cannot be recovered.
	 */
	@Test
	public void aSystemDisableSparesACompanyThatEnabledItself() {
		_setEffectiveEnabled(_COMPANY_A, false);
		_setEffectiveEnabled(_COMPANY_B, true);

		_onAfterSave(_properties(false, 0));

		verify(_collectionCycleStatusImpl).closeCycle(_COMPANY_A);
		verify(
			_collectionCycleStatusImpl, never()
		).closeCycle(
			_COMPANY_B
		);
		verify(_collectionCycleStatusImpl).openCycle(_COMPANY_B, 0);
	}

	/**
	 * The mirror image: turning logging on at system scope must not open a
	 * cycle for a company that has switched it off for itself, or that company
	 * would start reporting a collection cycle it never asked for.
	 */
	@Test
	public void aSystemEnableSparesACompanyThatDisabledItself() {
		_setEffectiveEnabled(_COMPANY_A, true);
		_setEffectiveEnabled(_COMPANY_B, false);

		_onAfterSave(_properties(true, 0));

		verify(_collectionCycleStatusImpl).openCycle(_COMPANY_A, 0);
		verify(_collectionCycleStatusImpl).closeCycle(_COMPANY_B);
		verify(_collectionCycleStatusImpl, never()).openCycle(_COMPANY_B, 0);
	}

	/**
	 * With no per-company overrides anywhere, a system save is the effective
	 * setting for every instance, which is the ordinary single-instance case.
	 */
	@Test
	public void aSystemSaveReachesEveryCompanyWithNoOverride() {
		_setEffectiveEnabled(_COMPANY_A, true);
		_setEffectiveEnabled(_COMPANY_B, true);

		_onAfterSave(_properties(true, 0));

		verify(_collectionCycleStatusImpl).openCycle(_COMPANY_A, 0);
		verify(_collectionCycleStatusImpl).openCycle(_COMPANY_B, 0);
	}

	/**
	 * A listener that threw would abort the administrator's save, so this
	 * plugin's funnel bookkeeping could stop somebody turning logging off.
	 */
	@Test
	public void aFailureDoesNotEscapeIntoTheSave() {
		when(
			_searchEvalLoggerConfigurationRegistry.getConfiguration(anyLong())
		).thenThrow(
			new RuntimeException("registry is down")
		);

		_onAfterSave(_properties(true, 0));
	}

	private void _onAfterSave(Dictionary<String, Object> properties) {
		_searchEvalLoggerConfigurationModelListener.onAfterSave(
			"ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration",
			properties);
	}

	private Dictionary<String, Object> _properties(
		boolean enabled, long companyId) {

		Dictionary<String, Object> properties = new Hashtable<>();

		properties.put("enabled", enabled);

		if (companyId > 0) {
			properties.put("companyId", companyId);
		}

		return properties;
	}

	private void _setEffectiveEnabled(long companyId, boolean enabled) {
		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration = mock(
			SearchEvalLoggerConfiguration.class);

		when(
			searchEvalLoggerConfiguration.enabled()
		).thenReturn(
			enabled
		);

		when(
			_searchEvalLoggerConfigurationRegistry.getConfiguration(companyId)
		).thenReturn(
			searchEvalLoggerConfiguration
		);
	}

	private static final long _COMPANY_A = 20097L;

	private static final long _COMPANY_B = 30188L;

	private static final List<Long> _COMPANY_IDS = List.of(
		_COMPANY_A, _COMPANY_B);

	private CollectionCycleStatusImpl _collectionCycleStatusImpl;
	private SearchEvalLoggerConfigurationModelListener
		_searchEvalLoggerConfigurationModelListener;
	private SearchEvalLoggerConfigurationRegistry
		_searchEvalLoggerConfigurationRegistry;

}
