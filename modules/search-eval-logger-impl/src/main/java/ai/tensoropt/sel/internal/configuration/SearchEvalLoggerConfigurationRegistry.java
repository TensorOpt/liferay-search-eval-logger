/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.configuration;

import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;

import java.util.Collections;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Reads the per virtual instance configuration on the search thread.
 *
 * <p>
 * Never throws and never returns null. A configuration that cannot be read is
 * not an error worth failing a search over, so lookup failures fall back to the
 * declared defaults, which have collection disabled. The failure direction
 * matters: an unreadable configuration must not turn collection on somewhere it
 * was never enabled.
 * </p>
 */
@Component(service = SearchEvalLoggerConfigurationRegistry.class)
public class SearchEvalLoggerConfigurationRegistry {

	public SearchEvalLoggerConfiguration getConfiguration(long companyId) {
		try {
			return _configurationProvider.getCompanyConfiguration(
				SearchEvalLoggerConfiguration.class, companyId);
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Unable to read the search eval logger configuration for " +
						"company " + companyId,
					exception);
			}

			return _defaultConfiguration;
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		SearchEvalLoggerConfigurationRegistry.class);

	@Reference
	private ConfigurationProvider _configurationProvider;

	/**
	 * The <code>&#64;Meta.AD</code> defaults, built from the interface itself
	 * so the fallback cannot drift from what System Settings shows. Collection
	 * is off by default, which is the direction this fallback has to fail in.
	 */
	private final SearchEvalLoggerConfiguration _defaultConfiguration =
		ConfigurableUtil.createConfigurable(
			SearchEvalLoggerConfiguration.class, Collections.emptyMap());

}
