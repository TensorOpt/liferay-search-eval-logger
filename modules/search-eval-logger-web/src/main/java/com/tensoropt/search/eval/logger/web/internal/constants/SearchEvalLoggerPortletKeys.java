/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.web.internal.constants;

/**
 * Names that the portlet, its permissions and its background task have to agree
 * on.
 */
public class SearchEvalLoggerPortletKeys {

	/**
	 * The dedicated resource permission of DESIGN.md 6.1, so that exporting can
	 * be restricted independently of general portal administration. It gates
	 * both starting an export and downloading a finished archive: an export
	 * nobody may start but anybody may download would not be a restriction.
	 */
	public static final String ACTION_EXPORT = "EXPORT";

	public static final String BACKGROUND_TASK_EXECUTOR_CLASS_NAME =
		"com.tensoropt.search.eval.logger.web.internal.background.task." +
			"SearchEvalExportBackgroundTaskExecutor";

	public static final String MVC_COMMAND_NAME_EXPORT =
		"/search_eval_logger/export";

	public static final String PORTLET_NAME =
		"com_tensoropt_search_eval_logger_web_internal_portlet_" +
			"SearchEvalLoggerPortlet";

	public static final String TASK_CONTEXT_END_TIME = "endTime";

	public static final String TASK_CONTEXT_START_TIME = "startTime";

	private SearchEvalLoggerPortletKeys() {
		throw new AssertionError();
	}

}
