/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.web.internal.export;

import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;

import com.tensoropt.search.eval.logger.configuration.SearchEvalLoggerConfiguration;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Reads the collector's per virtual instance configuration so the export can
 * record it.
 *
 * <p>
 * Unlike the collector's own reader, this one does not swallow failures. The
 * manifest states the configuration an export was produced under, and an export
 * that quietly reported defaults it was not actually running would be worse
 * than one that failed.
 * </p>
 */
@Component(service = SearchEvalExportConfigurationProvider.class)
public class SearchEvalExportConfigurationProvider {

	public SearchEvalLoggerConfiguration getConfiguration(long companyId)
		throws Exception {

		return _configurationProvider.getCompanyConfiguration(
			SearchEvalLoggerConfiguration.class, companyId);
	}

	@Reference
	private ConfigurationProvider _configurationProvider;

}
