/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.background.task;

import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskExecutor;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskResult;
import com.liferay.portal.kernel.backgroundtask.BaseBackgroundTaskExecutor;
import com.liferay.portal.kernel.backgroundtask.display.BackgroundTaskDisplay;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.FileUtil;

import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.web.internal.export.ExportArchiveNames;
import ai.tensoropt.sel.web.internal.export.ExportTaskContext;
import ai.tensoropt.sel.web.internal.export.SearchEvalExportResult;
import ai.tensoropt.sel.web.internal.export.SearchEvalExportWriter;
import ai.tensoropt.sel.web.internal.export.SearchEvalExportConfigurationProvider;

import java.io.File;

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
	property = "background.task.executor.class.name=ai.tensoropt.sel.web.internal.background.task.SearchEvalExportBackgroundTaskExecutor",
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

		Date startDate = ExportTaskContext.getStartDate(taskContextMap);
		Date endDate = ExportTaskContext.getEndDate(taskContextMap);

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
				ExportArchiveNames.getFileName(companyId, startDate, endDate),
				file);

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

	private static final Log _log = LogFactoryUtil.getLog(
		SearchEvalExportBackgroundTaskExecutor.class);

	@Reference
	private SearchEvalExportConfigurationProvider
		_searchEvalExportConfigurationProvider;

	@Reference
	private SearchEvalExportWriter _searchEvalExportWriter;

}
