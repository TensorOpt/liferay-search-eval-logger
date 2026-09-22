/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.export;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import java.util.Date;

/**
 * The one way this plugin writes a timestamp into an export.
 *
 * <p>
 * Every timestamp an evaluator sees, in the manifest, in the README and on
 * each JSONL line, comes from here, so they cannot drift into different shapes
 * as the three writers change independently. Seconds precision, UTC, ISO-8601,
 * matching the example in DESIGN.md 6.2.
 * </p>
 */
public class ExportTimestamps {

	/**
	 * Returns the ISO-8601 form of the given instant, or <code>null</code> for
	 * a null date, since an absent bound is a meaningful value in an export
	 * range rather than an error.
	 */
	public static String format(Date date) {
		if (date == null) {
			return null;
		}

		return format(date.toInstant());
	}

	public static String format(Instant instant) {
		if (instant == null) {
			return null;
		}

		return _DATE_TIME_FORMATTER.format(
			instant.truncatedTo(ChronoUnit.SECONDS));
	}

	private static final DateTimeFormatter _DATE_TIME_FORMATTER =
		DateTimeFormatter.ISO_INSTANT;

}
