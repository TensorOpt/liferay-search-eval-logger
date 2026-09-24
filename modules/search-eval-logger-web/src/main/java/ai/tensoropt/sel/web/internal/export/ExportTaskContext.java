/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.export;

import ai.tensoropt.sel.web.internal.constants.SearchEvalLoggerPortletKeys;

import java.io.Serializable;

import java.util.Date;
import java.util.Map;

/**
 * The one way an export's date range crosses into a background task.
 *
 * <p>
 * The range is chosen in a portlet action and read back in another bundle
 * component, with a serialized map in between, so the encoding and the
 * decoding are written far apart and are easy to drift. They live together
 * here, and a round trip is unit tested, because getting them out of step is
 * not an error: it is a silently different export.
 * </p>
 *
 * <p>
 * <strong>An absent bound is an absent key, never a magic value.</strong> The
 * first version encoded it as <code>0L</code> and decoded anything
 * <code>&lt;= 0</code> back to "unbounded". That collides with a real date: an
 * end bound of 1969-12-31 becomes an exclusive end of 1970-01-01T00:00:00Z,
 * whose time is exactly zero, and any earlier date is negative. While the
 * unbounded export was broken the collision at least failed loudly, because
 * the substituted extreme timestamp was rejected by the database. Once
 * unbounded exports work, that same input would quietly export the entire
 * instance instead of the empty range the administrator asked for, which on a
 * plugin whose constraints are about user data not leaving without a
 * deliberate act is the worst available outcome. Absence is therefore carried
 * out of band, and a bound that is present is carried exactly as given.
 * </p>
 */
public class ExportTaskContext {

	public static Date getEndDate(Map<String, Serializable> taskContextMap) {
		return _getDate(
			taskContextMap, SearchEvalLoggerPortletKeys.TASK_CONTEXT_END_TIME);
	}

	public static Date getStartDate(Map<String, Serializable> taskContextMap) {
		return _getDate(
			taskContextMap, SearchEvalLoggerPortletKeys.TASK_CONTEXT_START_TIME);
	}

	public static void putRange(
		Map<String, Serializable> taskContextMap, Date startDate,
		Date endDate) {

		_putDate(
			taskContextMap, SearchEvalLoggerPortletKeys.TASK_CONTEXT_START_TIME,
			startDate);
		_putDate(
			taskContextMap, SearchEvalLoggerPortletKeys.TASK_CONTEXT_END_TIME,
			endDate);
	}

	private static Date _getDate(
		Map<String, Serializable> taskContextMap, String key) {

		Serializable value = taskContextMap.get(key);

		// A task queued by an older version of this bundle carries the key with
		// a zero for an absent bound. That reads here as the epoch, which as a
		// start bound selects everything it used to select and as an end bound
		// yields an empty export: both visible, neither a silent full dump.

		if (!(value instanceof Number)) {
			return null;
		}

		Number number = (Number)value;

		return new Date(number.longValue());
	}

	private static void _putDate(
		Map<String, Serializable> taskContextMap, String key, Date date) {

		if (date == null) {
			return;
		}

		taskContextMap.put(key, date.getTime());
	}

}
