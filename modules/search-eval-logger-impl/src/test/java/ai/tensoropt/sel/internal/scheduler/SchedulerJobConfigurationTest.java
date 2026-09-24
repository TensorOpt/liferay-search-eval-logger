/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.liferay.portal.kernel.scheduler.TriggerConfiguration;

import org.junit.jupiter.api.Test;

/**
 * Both daily jobs fire at a time of day, not one interval after they
 * register. An interval trigger restarts its count at every portal start, so
 * an instance restarted at least daily never ran either job (TO-110).
 */
public class SchedulerJobConfigurationTest {

	@Test
	public void theRetentionPurgeRunsAt0300() {
		_assertDailyAt(
			"0 0 3 * * ?",
			new RetentionPurgeSchedulerJobConfiguration().
				getTriggerConfiguration());
	}

	@Test
	public void theCollectionCycleCheckRunsAt0330() {
		_assertDailyAt(
			"0 30 3 * * ?",
			new CollectionCycleSchedulerJobConfiguration().
				getTriggerConfiguration());
	}

	private void _assertDailyAt(
		String cronExpression, TriggerConfiguration triggerConfiguration) {

		assertEquals(cronExpression, triggerConfiguration.getCronExpression());
		assertEquals(0, triggerConfiguration.getInterval());
	}

}
