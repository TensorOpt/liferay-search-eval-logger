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

import ai.tensoropt.sel.internal.cycle.CollectionCycleMonitor;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Schedules the daily check of DESIGN.md 3.6, on the same mechanism as the
 * retention purge (3.4, EC-7) and for the same reason: unattended work that
 * dispatches per virtual instance, because both of its thresholds are
 * configured per instance.
 *
 * <p>
 * Daily, because both conditions it evaluates are measured in days and neither
 * is urgent. A stalled instance has already been stalled since its last
 * restart, and a log that became ready overnight is no less ready in the
 * morning.
 * </p>
 */
@Component(service = SchedulerJobConfiguration.class)
public class CollectionCycleSchedulerJobConfiguration
	implements SchedulerJobConfiguration {

	@Override
	public UnsafeConsumer<Long, Exception> getCompanyJobExecutorUnsafeConsumer() {
		return companyId -> _collectionCycleMonitor.check(companyId);
	}

	@Override
	public UnsafeRunnable<Exception> getJobExecutorUnsafeRunnable() {
		return () -> _companyLocalService.forEachCompanyId(
			companyId -> _collectionCycleMonitor.check(companyId));
	}

	@Override
	public TriggerConfiguration getTriggerConfiguration() {
		return TriggerConfiguration.createTriggerConfiguration(1, TimeUnit.DAY);
	}

	@Reference
	private CollectionCycleMonitor _collectionCycleMonitor;

	@Reference
	private CompanyLocalService _companyLocalService;

}
