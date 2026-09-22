/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.messaging;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.messaging.Destination;
import com.liferay.portal.kernel.messaging.DestinationConfiguration;
import com.liferay.portal.kernel.messaging.DestinationFactory;
import com.liferay.portal.kernel.util.MapUtil;

import ai.tensoropt.sel.api.SearchEvalLoggerConstants;
import ai.tensoropt.sel.internal.statistics.SearchEvalLoggerStatisticsImpl;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Creates the destination that carries search events from the wrapper to the
 * listener (DESIGN.md 3.3).
 *
 * <p>
 * Serial, so consumption is single threaded and database write pressure is
 * rate-limited by construction rather than by a buffer this plugin would have
 * to manage.
 * </p>
 *
 * <p>
 * The queue is bounded and the rejection handler discards. That is the whole
 * backpressure design: under load the destination drops log messages rather
 * than queueing unboundedly or blocking the producer, because dropping log rows
 * is always preferable to degrading production search. Every drop is counted,
 * so the export manifest can tell an evaluator that the log is a lossy sample
 * for that period instead of a census.
 * </p>
 */
@Component(service = {})
public class SearchEvalLogDestinationConfigurator {

	@Activate
	protected void activate(BundleContext bundleContext) {
		DestinationConfiguration destinationConfiguration =
			DestinationConfiguration.createSerialDestinationConfiguration(
				SearchEvalLoggerConstants.DESTINATION_NAME);

		destinationConfiguration.setMaximumQueueSize(_MAXIMUM_QUEUE_SIZE);
		destinationConfiguration.setWorkersCoreSize(_WORKERS_CORE_SIZE);
		destinationConfiguration.setWorkersMaxSize(_WORKERS_MAX_SIZE);

		destinationConfiguration.setRejectedExecutionHandler(
			(runnable, threadPoolExecutor) -> {
				_searchEvalLoggerStatisticsImpl.incrementDroppedEventCount();

				if (_log.isDebugEnabled()) {
					_log.debug(
						"Dropped a search event because the destination queue " +
							"is full");
				}
			});

		Destination destination = _destinationFactory.createDestination(
			destinationConfiguration);

		_serviceRegistration = bundleContext.registerService(
			Destination.class, destination,
			MapUtil.singletonDictionary(
				"destination.name", destination.getName()));
	}

	@Deactivate
	protected void deactivate() {
		if (_serviceRegistration != null) {
			_serviceRegistration.unregister();

			_serviceRegistration = null;
		}
	}

	/**
	 * Deep enough to absorb a burst of user searches, shallow enough that a
	 * stalled consumer sheds load quickly instead of holding megabytes of
	 * captured hits alive.
	 */
	private static final int _MAXIMUM_QUEUE_SIZE = 2000;

	private static final int _WORKERS_CORE_SIZE = 1;

	private static final int _WORKERS_MAX_SIZE = 1;

	private static final Log _log = LogFactoryUtil.getLog(
		SearchEvalLogDestinationConfigurator.class);

	@Reference
	private DestinationFactory _destinationFactory;

	@Reference
	private SearchEvalLoggerStatisticsImpl _searchEvalLoggerStatisticsImpl;

	private ServiceRegistration<Destination> _serviceRegistration;

}
