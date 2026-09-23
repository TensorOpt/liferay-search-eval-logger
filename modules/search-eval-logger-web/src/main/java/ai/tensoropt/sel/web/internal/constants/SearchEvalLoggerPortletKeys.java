/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.constants;

import ai.tensoropt.sel.api.SearchEvalLoggerConstants;

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
		"ai.tensoropt.sel.web.internal.background.task." +
			"SearchEvalExportBackgroundTaskExecutor";

	public static final String MVC_COMMAND_NAME_EXPORT =
		"/search_eval_logger/export";

	/**
	 * The impl bundle sends notifications typed with this same name, so it is
	 * defined once in the api bundle and read from there rather than repeated.
	 */
	public static final String PORTLET_NAME =
		SearchEvalLoggerConstants.ADMIN_PORTLET_NAME;

	public static final String TASK_CONTEXT_END_TIME = "endTime";

	public static final String TASK_CONTEXT_START_TIME = "startTime";

	private SearchEvalLoggerPortletKeys() {
		throw new AssertionError();
	}

}
