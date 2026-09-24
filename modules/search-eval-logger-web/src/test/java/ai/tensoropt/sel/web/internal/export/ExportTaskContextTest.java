/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import ai.tensoropt.sel.web.internal.constants.SearchEvalLoggerPortletKeys;

import java.io.Serializable;

import java.time.Instant;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The range an administrator typed has to arrive at the exporter as the range
 * they typed, and "they left it empty" has to stay distinguishable from every
 * date they could have typed instead.
 */
public class ExportTaskContextTest {

	@Test
	public void bothBoundsRoundTrip() {
		Map<String, Serializable> taskContextMap = _putRange(
			_START_DATE, _END_DATE);

		assertEquals(_START_DATE, ExportTaskContext.getStartDate(taskContextMap));
		assertEquals(_END_DATE, ExportTaskContext.getEndDate(taskContextMap));
	}

	@Test
	public void absentBoundsRoundTripAsAbsent() {
		Map<String, Serializable> taskContextMap = _putRange(null, null);

		assertNull(ExportTaskContext.getStartDate(taskContextMap));
		assertNull(ExportTaskContext.getEndDate(taskContextMap));
	}

	@Test
	public void oneAbsentBoundLeavesTheOtherAlone() {
		Map<String, Serializable> taskContextMap = _putRange(null, _END_DATE);

		assertNull(ExportTaskContext.getStartDate(taskContextMap));
		assertEquals(_END_DATE, ExportTaskContext.getEndDate(taskContextMap));

		taskContextMap = _putRange(_START_DATE, null);

		assertEquals(_START_DATE, ExportTaskContext.getStartDate(taskContextMap));
		assertNull(ExportTaskContext.getEndDate(taskContextMap));
	}

	/**
	 * An absent bound is carried out of band, so it cannot collide with a date
	 * someone could actually ask for.
	 */
	@Test
	public void anAbsentBoundWritesNoKeyAtAll() {
		Map<String, Serializable> taskContextMap = _putRange(null, null);

		assertFalse(
			taskContextMap.containsKey(
				SearchEvalLoggerPortletKeys.TASK_CONTEXT_START_TIME),
			"An absent start bound must not be encoded as a value");
		assertFalse(
			taskContextMap.containsKey(
				SearchEvalLoggerPortletKeys.TASK_CONTEXT_END_TIME),
			"An absent end bound must not be encoded as a value");
	}

	/**
	 * The collision that made an in band sentinel unsafe.
	 *
	 * <p>
	 * An export "to 1969-12-31" becomes an exclusive end of the epoch itself,
	 * whose time is exactly zero. Read as a sentinel that is "no end bound",
	 * and the export that should return nothing returns every event in the
	 * instance instead.
	 * </p>
	 */
	@Test
	public void theEpochIsADateAndNotAnAbsentBound() {
		Date epoch = new Date(0);

		Map<String, Serializable> taskContextMap = _putRange(null, epoch);

		assertEquals(epoch, ExportTaskContext.getEndDate(taskContextMap));
	}

	/**
	 * Same argument, one day earlier, where the encoded value is negative.
	 */
	@Test
	public void aPreEpochBoundIsADateAndNotAnAbsentBound() {
		Date preEpoch = Date.from(Instant.parse("1969-12-31T00:00:00Z"));

		Map<String, Serializable> taskContextMap = _putRange(preEpoch, null);

		assertEquals(
			preEpoch, ExportTaskContext.getStartDate(taskContextMap));
	}

	/**
	 * A task queued before this encoding changed carries the key with a zero.
	 * That has to read as some date rather than throwing, and the epoch is the
	 * reading that fails visibly: as a start bound it selects what it always
	 * selected, and as an end bound it yields an empty export rather than the
	 * whole instance.
	 */
	@Test
	public void aTaskQueuedByTheOldEncodingStillReads() {
		Map<String, Serializable> taskContextMap = new HashMap<>();

		taskContextMap.put(
			SearchEvalLoggerPortletKeys.TASK_CONTEXT_START_TIME, 0L);
		taskContextMap.put(
			SearchEvalLoggerPortletKeys.TASK_CONTEXT_END_TIME, 0L);

		assertEquals(
			new Date(0), ExportTaskContext.getStartDate(taskContextMap));
		assertEquals(new Date(0), ExportTaskContext.getEndDate(taskContextMap));
	}

	private Map<String, Serializable> _putRange(Date startDate, Date endDate) {
		Map<String, Serializable> taskContextMap = new HashMap<>();

		ExportTaskContext.putRange(taskContextMap, startDate, endDate);

		return taskContextMap;
	}

	private static final Date _END_DATE = Date.from(
		Instant.parse("2026-09-24T00:00:00Z"));

	private static final Date _START_DATE = Date.from(
		Instant.parse("2026-08-24T00:00:00Z"));

}
