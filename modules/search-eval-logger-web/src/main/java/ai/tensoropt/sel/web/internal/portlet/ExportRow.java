/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.portlet;

import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskConstants;

import java.time.Duration;
import java.time.format.DateTimeFormatter;

import java.util.Date;

/**
 * One export in the recent-exports table, already formatted for display.
 *
 * <p>
 * Plain getters rather than a record, because the JSP reads it through EL,
 * which on this platform's Tomcat resolves JavaBeans properties only.
 * </p>
 */
public class ExportRow {

	public ExportRow(
		BackgroundTask backgroundTask, DateTimeFormatter dateTimeFormatter) {

		Date createDate = backgroundTask.getCreateDate();
		Date completionDate = backgroundTask.getCompletionDate();

		_backgroundTaskId = backgroundTask.getBackgroundTaskId();
		_createDate = dateTimeFormatter.format(createDate.toInstant());
		_statusLabel = backgroundTask.getStatusLabel();

		// isCompleted() is true for a failed task as well as a successful one,
		// and only a successful run has an archive attached.

		_successful =
			backgroundTask.getStatus() ==
				BackgroundTaskConstants.STATUS_SUCCESSFUL;

		if (completionDate == null) {
			_completionDate = null;
			_duration = null;

			return;
		}

		_completionDate = dateTimeFormatter.format(completionDate.toInstant());

		Duration duration = Duration.between(
			createDate.toInstant(), completionDate.toInstant());

		_duration = String.format(
			"%d:%02d:%02d", duration.toHours(), duration.toMinutesPart(),
			duration.toSecondsPart());
	}

	public long getBackgroundTaskId() {
		return _backgroundTaskId;
	}

	public String getCompletionDate() {
		return _completionDate;
	}

	public String getCreateDate() {
		return _createDate;
	}

	public String getDuration() {
		return _duration;
	}

	public String getStatusLabel() {
		return _statusLabel;
	}

	public boolean isSuccessful() {
		return _successful;
	}

	private final long _backgroundTaskId;
	private final String _completionDate;
	private final String _createDate;
	private final String _duration;
	private final String _statusLabel;
	private final boolean _successful;

}
