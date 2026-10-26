/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.portlet.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * F2-3: the end bound reaches {@link ExportMVCActionCommand#parseDateRange}
 * already made exclusive, which means a naive "end before start" check also
 * accepts a submitted end one day before the start, since the two then land
 * on the same exclusive instant. These cases pin the boundary the fix
 * actually depends on, not just the headline bug.
 */
public class ExportMVCActionCommandTest {

	@Test
	public void emptyBoundsParseAsNull() {
		Date[] dateRange = ExportMVCActionCommand.parseDateRange("", "");

		assertNull(dateRange[0]);
		assertNull(dateRange[1]);
	}

	@Test
	public void whitespaceIsTrimmedBeforeParsing() {
		Date[] dateRange = ExportMVCActionCommand.parseDateRange(
			"  2026-01-05  ", "  2026-01-05  ");

		assertEquals(
			Instant.parse("2026-01-05T00:00:00Z"), dateRange[0].toInstant());
	}

	@Test
	public void aMalformedDateFailsToParse() {
		assertThrows(
			DateTimeParseException.class,
			() -> ExportMVCActionCommand.parseDateRange("not-a-date", ""));
		assertThrows(
			DateTimeParseException.class,
			() -> ExportMVCActionCommand.parseDateRange("", "not-a-date"));
	}

	/**
	 * A one day range: the end bound submitted equals the start bound. The
	 * exclusive end then lands one day after the start, so this must be
	 * accepted.
	 */
	@Test
	public void endEqualToStartIsAccepted() {
		Date[] dateRange = ExportMVCActionCommand.parseDateRange(
			"2026-01-05", "2026-01-05");

		assertEquals(
			Instant.parse("2026-01-05T00:00:00Z"), dateRange[0].toInstant());
		assertEquals(
			Instant.parse("2026-01-06T00:00:00Z"), dateRange[1].toInstant());
	}

	/**
	 * The bug: a submitted end one day before the start lands, once made
	 * exclusive, on the exact same instant as the start, so a check relying on
	 * {@link Date#before} alone misses it entirely.
	 */
	@Test
	public void endOneDayBeforeStartIsRejected() {
		assertThrows(
			IllegalArgumentException.class,
			() -> ExportMVCActionCommand.parseDateRange(
				"2026-01-06", "2026-01-05"));
	}

	@Test
	public void endBeforeStartIsRejected() {
		assertThrows(
			IllegalArgumentException.class,
			() -> ExportMVCActionCommand.parseDateRange(
				"2026-01-06", "2026-01-01"));
	}

	@Test
	public void anOpenBoundNeverTriggersTheRangeCheck() {
		Date[] dateRange = ExportMVCActionCommand.parseDateRange(
			"", "2026-01-05");

		assertNull(dateRange[0]);
		assertEquals(
			Instant.parse("2026-01-06T00:00:00Z"), dateRange[1].toInstant());

		dateRange = ExportMVCActionCommand.parseDateRange("2026-01-05", "");

		assertEquals(
			Instant.parse("2026-01-05T00:00:00Z"), dateRange[0].toInstant());
		assertNull(dateRange[1]);
	}

}
