/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.messaging;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.messaging.Destination;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageBus;
import com.liferay.portal.kernel.messaging.MessageListenerRegistry;

import ai.tensoropt.sel.api.SearchEvalLoggerConstants;
import ai.tensoropt.sel.internal.capture.CapturedSearchEvent;
import ai.tensoropt.sel.internal.statistics.SearchEvalLoggerStatisticsImpl;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Hands a captured event to the Message Bus and returns (DESIGN.md 3.3).
 *
 * <p>
 * Asynchronous sending is fire and forget, so this is the last thing that
 * happens on the search thread. Nothing here waits for a listener, and nothing
 * here throws: a send that fails is a dropped log row, counted as such, not an
 * error the search is made to care about.
 * </p>
 *
 * <p>
 * The {@code Destination} reference below is never read. It exists only so
 * Felix SCR will not activate this component, and transitively
 * {@code LoggingSearcher}, until {@code SearchEvalLogDestinationConfigurator}
 * has registered the destination. Without it, a search that races the
 * destination's own {@code Destination} service into existence would find
 * {@link MessageBus#sendMessage(String, Message)} log a warning and return
 * without throwing: a dropped row this class would still have counted as
 * dispatched, since nothing here throws for it to catch. That race is real on
 * a fresh install: the two components have no other dependency forcing an
 * order, and it self-heals invisibly, which is exactly why the bug never
 * gets caught by hand.
 * </p>
 *
 * <p>
 * A second, separate race sits one layer deeper: even once the destination
 * exists, {@code BaseAsyncDestination.send(...)} looks up its listeners via
 * {@link MessageListenerRegistry}, which is populated by its own OSGi service
 * tracker reacting to {@code SearchEventPersistenceMessageListener}'s
 * registration. If that has not propagated yet, <code>send</code> silently
 * drops the message the same way, and there is no public API to gate a
 * component's activation on "a tracker elsewhere has already noticed my
 * service" the way {@code @Reference} can gate on a service simply existing.
 * The registry is checked explicitly below instead, so that window is counted
 * as a real drop rather than a false dispatched count.
 * </p>
 */
@Component(service = SearchEventDispatcher.class)
public class SearchEventDispatcher {

	public void dispatch(CapturedSearchEvent capturedSearchEvent) {
		if (capturedSearchEvent == null) {
			return;
		}

		try {
			if (_messageListenerRegistry.getMessageListeners(
					SearchEvalLoggerConstants.DESTINATION_NAME).isEmpty()) {

				_searchEvalLoggerStatisticsImpl.incrementDroppedEventCount();

				if (_log.isDebugEnabled()) {
					_log.debug(
						"Dropped a search event: no message listener is " +
							"attached to the destination yet");
				}

				return;
			}

			Message message = new Message();

			message.setPayload(capturedSearchEvent);

			_messageBus.sendMessage(
				SearchEvalLoggerConstants.DESTINATION_NAME, message);

			_searchEvalLoggerStatisticsImpl.incrementDispatchedEventCount();
		}
		catch (Throwable throwable) {
			_searchEvalLoggerStatisticsImpl.incrementDroppedEventCount();

			if (_log.isDebugEnabled()) {
				_log.debug("Unable to dispatch a search event", throwable);
			}
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		SearchEventDispatcher.class);

	@Reference(
		target = "(destination.name=" +
			SearchEvalLoggerConstants.DESTINATION_NAME + ")"
	)
	private Destination _destination;

	@Reference
	private MessageBus _messageBus;

	@Reference
	private MessageListenerRegistry _messageListenerRegistry;

	@Reference
	private SearchEvalLoggerStatisticsImpl _searchEvalLoggerStatisticsImpl;

}
