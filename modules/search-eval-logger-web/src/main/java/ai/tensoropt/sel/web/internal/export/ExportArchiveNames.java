/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.export;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import java.util.Date;

/**
 * Names an export for the range the administrator asked for (DESIGN.md 6.2,
 * <code>search-eval-export-&lt;instance&gt;-&lt;from&gt;-&lt;to&gt;.zip</code>).
 *
 * <p>
 * <strong>The end bound that reaches here is exclusive, and the name must not
 * be.</strong> The export screen turns "to the 23rd" into the start of the
 * 24th so the whole of the 23rd is included, and that exclusive instant is
 * what the task carries. Formatting it as it stands names an export of the 1st
 * to the 23rd "...-20260924.zip" (D-4). The range exported was always right;
 * the label on it, which is what the archive is filed under once it leaves the
 * instance, was not. The name therefore carries the last day the range
 * includes, which is the day holding the last instant before the bound.
 * </p>
 *
 * <p>
 * The README and the manifest keep the exclusive instant and say so, because
 * they state a range precisely. A file name states which days it covers.
 * </p>
 */
public class ExportArchiveNames {

	public static String getFileName(
		long companyId, Date startDate, Date endDate) {

		return _PREFIX + companyId + "-" + _formatStart(startDate) + "-" +
			_formatEnd(endDate) + ".zip";
	}

	/**
	 * The background task's name, shown in the task history on the export
	 * screen. The same days as the file name, without the instance, which the
	 * screen already implies.
	 */
	public static String getTaskName(Date startDate, Date endDate) {
		return _PREFIX + _formatStart(startDate) + "-" + _formatEnd(endDate);
	}

	private static String _format(Instant instant) {
		return _DATE_TIME_FORMATTER.format(
			instant.truncatedTo(ChronoUnit.DAYS));
	}

	/**
	 * One millisecond before the bound, rather than one day, so that a bound
	 * that is not a midnight still names the day it falls in. The screen only
	 * ever produces midnights; this does not rely on it.
	 */
	private static String _formatEnd(Date endDate) {
		if (endDate == null) {
			return _UNBOUNDED;
		}

		Instant instant = endDate.toInstant();

		return _format(instant.minusMillis(1));
	}

	private static String _formatStart(Date startDate) {
		if (startDate == null) {
			return _UNBOUNDED;
		}

		return _format(startDate.toInstant());
	}

	private static final DateTimeFormatter _DATE_TIME_FORMATTER =
		DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

	private static final String _PREFIX = "search-eval-export-";

	private static final String _UNBOUNDED = "all";

}
