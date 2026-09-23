/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.configuration;

import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;
import com.liferay.portal.configuration.persistence.listener.ConfigurationModelListener;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.security.auth.PrincipalThreadLocal;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.util.GetterUtil;

import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.cycle.CollectionCycleStatusImpl;

import java.util.Dictionary;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Starts and ends a collection cycle the moment logging is switched on or off
 * (DESIGN.md 3.6).
 *
 * <p>
 * This is where the enabling administrator's identity comes from, and it is the
 * only place it can come from. Configuration Admin delivers a changed
 * configuration to its consumers on its own thread, long after the request that
 * saved it has finished, so a component reacting to the change through
 * <code>&#64;Modified</code> sees no principal. A configuration model listener
 * runs inside the save itself, on the administrator's own request thread, which
 * is what makes <code>PrincipalThreadLocal</code> readable here.
 * </p>
 *
 * <p>
 * That is the theory of it, and EC-14 has not confirmed it on a running
 * instance. Everything downstream is built to survive being wrong about it: an
 * unresolved user id is zero, notifications then go to the instance
 * administrators, and the daily job (3.6) reconciles the cycle with the
 * configuration whether or not this listener was ever called.
 * </p>
 *
 * <p>
 * The cycle is only opened or closed, never reset in place. A save that leaves
 * logging enabled is not a new cycle, whatever else it changed, so the
 * collection start date survives an unrelated settings change.
 * </p>
 */
@Component(
	property = "model.class.name=ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration",
	service = ConfigurationModelListener.class
)
public class SearchEvalLoggerConfigurationModelListener
	implements ConfigurationModelListener {

	@Override
	public void onAfterSave(String pid, Dictionary<String, Object> properties) {
		try {
			boolean enabled = GetterUtil.getBoolean(properties.get("enabled"));

			long companyId = GetterUtil.getLong(
				properties.get(
					ExtendedObjectClassDefinition.Scope.COMPANY.
						getPropertyKey()));

			if (companyId > 0) {

				// The saved dictionary is that company's configuration, so it
				// is the authority on what that company now resolves to.

				_apply(companyId, enabled);

				return;
			}

			// Saved at system scope. That sets the default for every virtual
			// instance that has no configuration of its own and changes
			// nothing for one that does, so the saved value cannot be applied
			// to all of them: it says what was typed, not what each company
			// now resolves to. Each is asked for its own effective setting
			// instead, the same way the daily reconciliation does.
			//
			// The difference is destructive rather than cosmetic. Closing a
			// cycle clears its collection start date, and nothing can restore
			// it: a later reconciliation reopens the cycle but the date it
			// would need is the day of the first event of a cycle that has
			// already been collecting for weeks. A company running with its
			// own "enabled" setting would silently lose that history, push its
			// readiness out by another full minimum period, and start handing
			// the signup link (10.1) a date that is not when collection began
			// - which is the one thing "collection start, not enable date"
			// exists to prevent.

			_companyLocalService.forEachCompanyId(
				forEachCompanyId -> _apply(
					forEachCompanyId, _isEnabled(forEachCompanyId)));
		}
		catch (Throwable throwable) {

			// Never rethrown as a ConfigurationModelListenerException. That
			// would abort the administrator's save, which would mean this
			// plugin's funnel bookkeeping could stop them turning logging off.

			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to update the collection cycle after saving " + pid,
					throwable);
			}
		}
	}

	/**
	 * Visible for testing. The runtime binds the fields directly; there are no
	 * production callers.
	 */
	void setCollaborators(
		CollectionCycleStatusImpl collectionCycleStatusImpl,
		CompanyLocalService companyLocalService,
		SearchEvalLoggerConfigurationRegistry
			searchEvalLoggerConfigurationRegistry) {

		_collectionCycleStatusImpl = collectionCycleStatusImpl;
		_companyLocalService = companyLocalService;
		_searchEvalLoggerConfigurationRegistry =
			searchEvalLoggerConfigurationRegistry;
	}

	private void _apply(long companyId, boolean enabled) {
		if (!enabled) {
			_collectionCycleStatusImpl.closeCycle(companyId);

			return;
		}

		_collectionCycleStatusImpl.openCycle(
			companyId, PrincipalThreadLocal.getUserId());
	}

	/**
	 * One company's effective setting, resolved rather than assumed.
	 *
	 * <p>
	 * This is read immediately after the save, so a company whose effective
	 * value changed because of it may still resolve to the previous one if
	 * Configuration Admin has not finished propagating. That error is bounded
	 * and self-correcting: the daily reconciliation applies the right value
	 * within a day, and a stale read can only delay a transition, never
	 * discard a collection start date that belongs to a company the save was
	 * not about.
	 * </p>
	 */
	private boolean _isEnabled(long companyId) {
		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration =
			_searchEvalLoggerConfigurationRegistry.getConfiguration(companyId);

		return searchEvalLoggerConfiguration.enabled();
	}

	private static final Log _log = LogFactoryUtil.getLog(
		SearchEvalLoggerConfigurationModelListener.class);

	@Reference
	private CollectionCycleStatusImpl _collectionCycleStatusImpl;

	@Reference
	private CompanyLocalService _companyLocalService;

	@Reference
	private SearchEvalLoggerConfigurationRegistry
		_searchEvalLoggerConfigurationRegistry;

}
