/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.constants;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.tensoropt.sel.web.internal.background.task.SearchEvalExportBackgroundTaskExecutor;

import org.junit.jupiter.api.Test;

/**
 * The executor registers under this name, the action starts tasks with it,
 * and the screen and the download both look tasks up by it. An annotation
 * needs a compile-time constant, so it cannot be the class's getName(); this
 * keeps a rename from silently detaching all four.
 */
public class SearchEvalLoggerPortletKeysTest {

	@Test
	public void theExecutorClassNameIsTheExecutorsClassName() {
		assertEquals(
			SearchEvalExportBackgroundTaskExecutor.class.getName(),
			SearchEvalLoggerPortletKeys.BACKGROUND_TASK_EXECUTOR_CLASS_NAME);
	}

}
