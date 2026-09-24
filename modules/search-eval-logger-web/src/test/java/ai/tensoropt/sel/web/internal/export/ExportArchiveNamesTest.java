/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * The archive name is asserted exactly, against the days asked for. D-4
 * survived because the only check on it was a pattern that any pair of dates
 * satisfied.
 */
public class ExportArchiveNamesTest {

	/**
	 * "From the 23rd of August to the 23rd of September" reaches the task as
	 * an exclusive end at the start of the 24th, and must still be named for
	 * the 23rd.
	 */
	@Test
	public void theExclusiveEndIsNamedForTheLastDayItIncludes() {
		assertEquals(
			"search-eval-export-92102577642293-20260823-20260923.zip",
			ExportArchiveNames.getFileName(
				92102577642293L, _date("2026-08-23T00:00:00Z"),
				_date("2026-09-24T00:00:00Z")));
	}

	/**
	 * A single day is the edge a one-day shift in either direction would get
	 * wrong: the name must read that day twice.
	 */
	@Test
	public void aSingleDayExportNamesThatDayTwice() {
		assertEquals(
			"search-eval-export-1-20260923-20260923.zip",
			ExportArchiveNames.getFileName(
				1L, _date("2026-09-23T00:00:00Z"),
				_date("2026-09-24T00:00:00Z")));
	}

	/**
	 * The start bound is inclusive and must not be shifted with the end.
	 */
	@Test
	public void theStartIsNamedAsGiven() {
		assertEquals(
			"search-eval-export-1-20260823-all.zip",
			ExportArchiveNames.getFileName(
				1L, _date("2026-08-23T00:00:00Z"), null));
	}

	@Test
	public void anUnboundedStartIsNamedAll() {
		assertEquals(
			"search-eval-export-1-all-20260923.zip",
			ExportArchiveNames.getFileName(
				1L, null, _date("2026-09-24T00:00:00Z")));
		assertEquals(
			"search-eval-export-1-all-all.zip",
			ExportArchiveNames.getFileName(1L, null, null));
	}

	/**
	 * A bound that is not a midnight names the day it falls in, not the day
	 * before it.
	 */
	@Test
	public void anEndInsideADayNamesThatDay() {
		assertEquals(
			"search-eval-export-1-20260823-20260923.zip",
			ExportArchiveNames.getFileName(
				1L, _date("2026-08-23T00:00:00Z"),
				_date("2026-09-23T12:00:00Z")));
	}

	/**
	 * The task history on the export screen shows the same days as the file,
	 * not a raw Date.toString() of the exclusive bound.
	 */
	@Test
	public void theTaskNameCarriesTheSameDays() {
		assertEquals(
			"search-eval-export-20260823-20260923",
			ExportArchiveNames.getTaskName(
				_date("2026-08-23T00:00:00Z"), _date("2026-09-24T00:00:00Z")));
		assertEquals(
			"search-eval-export-all-all",
			ExportArchiveNames.getTaskName(null, null));
	}

	private static Date _date(String value) {
		return Date.from(Instant.parse(value));
	}

}
