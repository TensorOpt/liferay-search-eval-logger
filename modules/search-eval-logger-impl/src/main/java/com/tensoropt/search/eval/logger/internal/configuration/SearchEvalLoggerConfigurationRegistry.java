/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.internal.configuration;

import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import com.tensoropt.search.eval.logger.api.SearchEvalLoggerConstants;
import com.tensoropt.search.eval.logger.configuration.SearchEvalLoggerConfiguration;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Reads the per virtual instance configuration on the search thread.
 *
 * <p>
 * Never throws and never returns null. A configuration that cannot be read is
 * not an error worth failing a search over, so lookup failures fall back to the
 * built-in defaults, which have collection disabled. The failure direction
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

			return _DISABLED_CONFIGURATION;
		}
	}

	private static final SearchEvalLoggerConfiguration _DISABLED_CONFIGURATION =
		new DefaultSearchEvalLoggerConfiguration();

	private static final Log _log = LogFactoryUtil.getLog(
		SearchEvalLoggerConfigurationRegistry.class);

	@Reference
	private ConfigurationProvider _configurationProvider;

	private static class DefaultSearchEvalLoggerConfiguration
		implements SearchEvalLoggerConfiguration {

		@Override
		public boolean admitFacetOnlySearches() {
			return SearchEvalLoggerConstants.
				DEFAULT_ADMIT_FACET_ONLY_SEARCHES;
		}

		@Override
		public int captureDepth() {
			return SearchEvalLoggerConstants.DEFAULT_CAPTURE_DEPTH;
		}

		@Override
		public String[] capturedFieldNames() {
			return SearchEvalLoggerConstants.DEFAULT_CAPTURED_FIELD_NAMES.toArray(
				new String[0]);
		}

		@Override
		public int cohortSaltRotationDays() {
			return SearchEvalLoggerConstants.
				DEFAULT_COHORT_SALT_ROTATION_DAYS;
		}

		@Override
		public boolean enabled() {
			return SearchEvalLoggerConstants.DEFAULT_ENABLED;
		}

		@Override
		public String[] excludedEntryClassNames() {
			return new String[0];
		}

		@Override
		public boolean excludeSuggestionTraffic() {
			return SearchEvalLoggerConstants.
				DEFAULT_EXCLUDE_SUGGESTION_TRAFFIC;
		}

		@Override
		public int queryTextCap() {
			return SearchEvalLoggerConstants.DEFAULT_QUERY_TEXT_CAP;
		}

		@Override
		public boolean requireWebRequestContext() {
			return SearchEvalLoggerConstants.
				DEFAULT_REQUIRE_WEB_REQUEST_CONTEXT;
		}

		@Override
		public int retentionDays() {
			return SearchEvalLoggerConstants.DEFAULT_RETENTION_DAYS;
		}

		@Override
		public double samplingRate() {
			return SearchEvalLoggerConstants.DEFAULT_SAMPLING_RATE;
		}

	}

}
