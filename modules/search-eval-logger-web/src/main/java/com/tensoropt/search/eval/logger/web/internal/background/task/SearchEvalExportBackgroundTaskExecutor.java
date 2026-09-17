/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.web.internal.background.task;

import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskExecutor;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskResult;
import com.liferay.portal.kernel.backgroundtask.BaseBackgroundTaskExecutor;
import com.liferay.portal.kernel.backgroundtask.display.BackgroundTaskDisplay;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.GetterUtil;

import com.tensoropt.search.eval.logger.configuration.SearchEvalLoggerConfiguration;
import com.tensoropt.search.eval.logger.web.internal.constants.SearchEvalLoggerPortletKeys;
import com.tensoropt.search.eval.logger.web.internal.export.SearchEvalExportResult;
import com.tensoropt.search.eval.logger.web.internal.export.SearchEvalExportWriter;
import com.tensoropt.search.eval.logger.web.internal.export.SearchEvalExportConfigurationProvider;

import java.io.File;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import java.util.Date;
import java.util.Map;
import java.io.Serializable;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Runs the export as a background task (DESIGN.md 6.1).
 *
 * <p>
 * A background task rather than a scheduled job or an inline request: the
 * export is started by a person who then waits for it, and Control Panel gives
 * that person visible progress and execution history. On a production install,
 * where the objection to this plugin is trust rather than function, being able
 * to see exactly when an export ran and who ran it is a small but real part of
 * the answer. That argument runs the other way for the retention purge, which
 * is unattended and lives on the scheduler.
 * </p>
 *
 * <p>
 * The archive is assembled in a temporary file and only then attached to the
 * task, so a full-window export never has to fit in the heap, and a failed run
 * leaves no half-written archive to download.
 * </p>
 */
@Component(
	property = "background.task.executor.class.name=com.tensoropt.search.eval.logger.web.internal.background.task.SearchEvalExportBackgroundTaskExecutor",
	service = BackgroundTaskExecutor.class
)
public class SearchEvalExportBackgroundTaskExecutor
	extends BaseBackgroundTaskExecutor {

	@Override
	public BackgroundTaskExecutor clone() {
		return this;
	}

	@Override
	public BackgroundTaskResult execute(BackgroundTask backgroundTask)
		throws Exception {

		Map<String, Serializable> taskContextMap =
			backgroundTask.getTaskContextMap();

		long companyId = backgroundTask.getCompanyId();

		Date startDate = _getDate(
			taskContextMap.get(
				SearchEvalLoggerPortletKeys.TASK_CONTEXT_START_TIME));
		Date endDate = _getDate(
			taskContextMap.get(
				SearchEvalLoggerPortletKeys.TASK_CONTEXT_END_TIME));

		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration =
			_searchEvalExportConfigurationProvider.getConfiguration(companyId);

		File file = FileUtil.createTempFile("zip");

		try {
			SearchEvalExportResult searchEvalExportResult =
				_searchEvalExportWriter.write(
					file, companyId, startDate, endDate,
					searchEvalLoggerConfiguration);

			backgroundTask.addAttachment(
				backgroundTask.getUserId(),
				_getFileName(companyId, startDate, endDate), file);

			if (_log.isInfoEnabled()) {
				_log.info(
					"Exported " + searchEvalExportResult.getEventCount() +
						" search events and " +
							searchEvalExportResult.getHitCount() +
								" hits for company " + companyId);
			}

			return BackgroundTaskResult.SUCCESS;
		}
		finally {
			FileUtil.delete(file);
		}
	}

	@Override
	public BackgroundTaskDisplay getBackgroundTaskDisplay(
		BackgroundTask backgroundTask) {

		return null;
	}

	/**
	 * Serial, because an export is IO heavy and an administrator who clicks
	 * twice should queue a second run rather than have two full-window scans
	 * compete for the same database.
	 */
	@Override
	public boolean isSerial() {
		return true;
	}

	private Date _getDate(Serializable value) {
		long time = GetterUtil.getLong(value);

		if (time <= 0) {
			return null;
		}

		return new Date(time);
	}

	private String _getFileName(
		long companyId, Date startDate, Date endDate) {

		return "search-eval-export-" + companyId + "-" + _format(startDate) +
			"-" + _format(endDate) + ".zip";
	}

	private String _format(Date date) {
		if (date == null) {
			return "all";
		}

		Instant instant = date.toInstant();

		return _DATE_TIME_FORMATTER.format(
			instant.truncatedTo(ChronoUnit.DAYS));
	}

	private static final DateTimeFormatter _DATE_TIME_FORMATTER =
		DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

	private static final Log _log = LogFactoryUtil.getLog(
		SearchEvalExportBackgroundTaskExecutor.class);

	@Reference
	private SearchEvalExportConfigurationProvider
		_searchEvalExportConfigurationProvider;

	@Reference
	private SearchEvalExportWriter _searchEvalExportWriter;

}
