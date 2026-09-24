/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;

import java.time.Instant;

import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * The README's range header is the first line an evaluator reads, and it is
 * the one part of the file that varies with what the administrator asked for.
 */
public class SearchEvalExportReadmeBuilderTest {

	@Test
	public void rangeHeaderCarriesBothBounds() {
		assertEquals(
			"Range: 2026-08-24T00:00:00Z to 2026-09-24T00:00:00Z " +
				"(UTC, end exclusive)",
			_getRangeLine(_START_DATE, _END_DATE));
	}

	/**
	 * An absent bound formats as null, and appending that would have put the
	 * word "null" in front of an evaluator as though it were the date. The
	 * export screen offers leaving a bound empty, so this is the header of the
	 * first export many installations will produce.
	 */
	@Test
	public void unboundedEndsAreNamedRatherThanPrintedAsNull() {
		assertEquals(
			"Range: unbounded to unbounded (UTC)", _getRangeLine(null, null));
	}

	@Test
	public void unboundedStartLeavesTheEndAlone() {
		assertEquals(
			"Range: unbounded to 2026-09-24T00:00:00Z (UTC, end exclusive)",
			_getRangeLine(null, _END_DATE));
	}

	/**
	 * Everything from a date onward, the likelier of the two half bounded
	 * exports.
	 */
	@Test
	public void unboundedEndLeavesTheStartAlone() {
		assertEquals(
			"Range: 2026-08-24T00:00:00Z to unbounded (UTC)",
			_getRangeLine(_START_DATE, null));
	}

	/**
	 * Scoped to the range line on purpose. The caveats below it are free to
	 * say that a field may be null, and DESIGN.md 6.2 requires some of them
	 * to; what must never happen is a null standing where a date belongs.
	 */
	@Test
	public void theRangeLineNeverShowsANull() {
		Date[][] combinations = {
			{null, null}, {null, _END_DATE}, {_START_DATE, null},
			{_START_DATE, _END_DATE}
		};

		for (Date[] combination : combinations) {
			String line = _getRangeLine(combination[0], combination[1]);

			assertFalse(
				line.contains("null"),
				"A null stands where a date belongs: " + line);
		}
	}

	private String _build(Date startDate, Date endDate) {
		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration = mock(
			SearchEvalLoggerConfiguration.class);

		when(searchEvalLoggerConfiguration.captureDepth()).thenReturn(20);
		when(searchEvalLoggerConfiguration.cohortSaltRotationDays()).thenReturn(
			7);

		SearchEvalExportReadmeBuilder searchEvalExportReadmeBuilder =
			new SearchEvalExportReadmeBuilder();

		return searchEvalExportReadmeBuilder.build(
			startDate, endDate, new SearchEvalExportResult(),
			searchEvalLoggerConfiguration);
	}

	/**
	 * Returns the header line without its trailing Markdown line break, which
	 * is two spaces.
	 */
	private String _getRangeLine(Date startDate, Date endDate) {
		String readme = _build(startDate, endDate);

		for (String line : readme.split("\n")) {
			if (line.startsWith("Range: ")) {
				return line.trim();
			}
		}

		return fail("The README carries no range line:\n" + readme);
	}

	private static final Date _END_DATE = Date.from(
		Instant.parse("2026-09-24T00:00:00Z"));

	private static final Date _START_DATE = Date.from(
		Instant.parse("2026-08-24T00:00:00Z"));

}
