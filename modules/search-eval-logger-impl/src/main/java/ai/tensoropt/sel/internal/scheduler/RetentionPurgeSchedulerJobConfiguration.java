/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.scheduler;

import com.liferay.petra.function.UnsafeConsumer;
import com.liferay.petra.function.UnsafeRunnable;
import com.liferay.portal.kernel.scheduler.SchedulerJobConfiguration;
import com.liferay.portal.kernel.scheduler.TimeUnit;
import com.liferay.portal.kernel.scheduler.TriggerConfiguration;
import com.liferay.portal.kernel.service.CompanyLocalService;

import ai.tensoropt.sel.internal.purge.RetentionPurger;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Schedules the retention purge (DESIGN.md 3.4).
 *
 * <p>
 * EC-7 asks whether the purge belongs on <code>SchedulerEngineHelper</code> or
 * on <code>BackgroundTaskManager</code>. This is the scheduler side of that
 * answer: the purge is unattended maintenance that nobody watches, so it wants
 * a recurring trigger, not a task with visible execution history. That argument
 * runs the other way for the export, which an admin starts and waits on.
 * {@link SchedulerJobConfiguration} is the 7.4 form of registering with the
 * scheduler engine, and it dispatches per virtual instance, which the purge
 * needs because the retention window is configured per instance.
 * </p>
 *
 * <p>
 * Daily, because the retention window is measured in days: a finer period would
 * delete the same rows at a finer granularity for no benefit, and a coarser one
 * would let a day's worth of expired rows sit around.
 * </p>
 */
@Component(service = SchedulerJobConfiguration.class)
public class RetentionPurgeSchedulerJobConfiguration
	implements SchedulerJobConfiguration {

	@Override
	public UnsafeConsumer<Long, Exception> getCompanyJobExecutorUnsafeConsumer() {
		return companyId -> _retentionPurger.purge(companyId);
	}

	@Override
	public UnsafeRunnable<Exception> getJobExecutorUnsafeRunnable() {
		return () -> _companyLocalService.forEachCompanyId(
			companyId -> _retentionPurger.purge(companyId));
	}

	@Override
	public TriggerConfiguration getTriggerConfiguration() {
		return TriggerConfiguration.createTriggerConfiguration(1, TimeUnit.DAY);
	}

	@Reference
	private CompanyLocalService _companyLocalService;

	@Reference
	private RetentionPurger _retentionPurger;

}
