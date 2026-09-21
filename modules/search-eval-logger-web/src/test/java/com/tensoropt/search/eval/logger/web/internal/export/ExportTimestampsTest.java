/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.web.internal.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * Every timestamp in an export goes through here, and the shape is part of
 * the published schema (DESIGN.md 6.2), so it is pinned rather than left to
 * whatever the default formatter happens to do.
 */
public class ExportTimestampsTest {

	@Test
	public void formatsAsIsoInstantInUtc() {
		assertEquals(
			"2026-09-14T10:22:31Z",
			ExportTimestamps.format(
				Date.from(Instant.parse("2026-09-14T10:22:31Z"))));
	}

	/**
	 * ISO_INSTANT would otherwise render sub-second precision for some values
	 * and not others, which makes the column awkward to parse downstream.
	 */
	@Test
	public void truncatesToWholeSeconds() {
		assertEquals(
			"2026-09-14T10:22:31Z",
			ExportTimestamps.format(
				Instant.parse("2026-09-14T10:22:31.987654Z")));
	}

	/**
	 * An unbounded end of an export range is a meaningful value, not an
	 * error, so it must not throw.
	 */
	@Test
	public void nullIsPassedThroughRatherThanThrowing() {
		assertNull(ExportTimestamps.format((Date)null));
		assertNull(ExportTimestamps.format((Instant)null));
	}

	@Test
	public void formatIsIndependentOfTheDefaultTimeZone() {
		String before = ExportTimestamps.format(
			Date.from(Instant.parse("2026-01-01T00:00:00Z")));

		java.util.TimeZone original = java.util.TimeZone.getDefault();

		try {
			java.util.TimeZone.setDefault(
				java.util.TimeZone.getTimeZone("America/Los_Angeles"));

			assertEquals(
				before,
				ExportTimestamps.format(
					Date.from(Instant.parse("2026-01-01T00:00:00Z"))));
		}
		finally {
			java.util.TimeZone.setDefault(original);
		}
	}

}
