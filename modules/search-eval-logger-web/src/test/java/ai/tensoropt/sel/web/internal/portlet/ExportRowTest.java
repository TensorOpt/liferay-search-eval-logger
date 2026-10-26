/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.portlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskConstants;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * F2-28: the three cases the constructor's branching and formatting actually
 * depend on, beyond the straightforward completed-task case other code
 * already exercises indirectly.
 */
public class ExportRowTest {

	/**
	 * A task still running, or one whose attachment was never recorded, has
	 * no completion date at all, and the duration is only meaningful once
	 * there is one.
	 */
	@Test
	public void aNullCompletionDateLeavesCompletionDateAndDurationNull() {
		BackgroundTask backgroundTask = _backgroundTask(
			Date.from(Instant.parse("2026-09-24T10:00:00Z")), null,
			BackgroundTaskConstants.STATUS_IN_PROGRESS);

		ExportRow exportRow = new ExportRow(backgroundTask, _DATE_TIME_FORMATTER);

		assertNull(exportRow.getCompletionDate());
		assertNull(exportRow.getDuration());
	}

	/**
	 * Duration.toHours() alone would read back as 0 past 24 hours; the
	 * formatted string must carry the full hour count rather than wrapping
	 * at a day boundary the way a plain hour-of-day field would.
	 */
	@Test
	public void aDurationOfAtLeastADayFormatsTheFullHourCount() {
		BackgroundTask backgroundTask = _backgroundTask(
			Date.from(Instant.parse("2026-09-24T10:00:00Z")),
			Date.from(Instant.parse("2026-09-25T13:15:30Z")),
			BackgroundTaskConstants.STATUS_SUCCESSFUL);

		ExportRow exportRow = new ExportRow(backgroundTask, _DATE_TIME_FORMATTER);

		assertEquals("27:15:30", exportRow.getDuration());
	}

	/**
	 * createDate and completionDate are both formatted through the same
	 * DateTimeFormatter the caller supplied, in the zone that formatter
	 * carries, not the JVM default or UTC.
	 */
	@Test
	public void datesAreFormattedInTheFormattersOwnZone() {
		DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(
			"yyyy-MM-dd HH:mm"
		).withZone(
			ZoneOffset.ofHours(-5)
		);

		BackgroundTask backgroundTask = _backgroundTask(
			Date.from(Instant.parse("2026-09-24T10:00:00Z")),
			Date.from(Instant.parse("2026-09-24T12:00:00Z")),
			BackgroundTaskConstants.STATUS_SUCCESSFUL);

		ExportRow exportRow = new ExportRow(backgroundTask, dateTimeFormatter);

		assertEquals("2026-09-24 05:00", exportRow.getCreateDate());
		assertEquals("2026-09-24 07:00", exportRow.getCompletionDate());
	}

	private BackgroundTask _backgroundTask(
		Date createDate, Date completionDate, int status) {

		BackgroundTask backgroundTask = mock(BackgroundTask.class);

		when(backgroundTask.getCreateDate()).thenReturn(createDate);
		when(backgroundTask.getCompletionDate()).thenReturn(completionDate);
		when(backgroundTask.getStatus()).thenReturn(status);
		when(backgroundTask.getStatusLabel()).thenReturn("status");

		return backgroundTask;
	}

	private static final DateTimeFormatter _DATE_TIME_FORMATTER =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

}
