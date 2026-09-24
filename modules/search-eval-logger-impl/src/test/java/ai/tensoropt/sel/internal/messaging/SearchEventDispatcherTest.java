/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.messaging.Destination;
import com.liferay.portal.kernel.messaging.DestinationConfiguration;
import com.liferay.portal.kernel.messaging.DestinationFactory;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageBus;
import com.liferay.portal.kernel.messaging.MessageListener;
import com.liferay.portal.kernel.messaging.MessageListenerRegistry;

import ai.tensoropt.sel.internal.capture.CapturedSearchEvent;
import ai.tensoropt.sel.internal.statistics.SearchEvalLoggerStatisticsImpl;

import java.lang.reflect.Field;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.RejectedExecutionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.osgi.framework.BundleContext;

/**
 * The manifest's funnel only adds up if every event is counted once, as
 * dispatched or as dropped (TO-92). A full queue is the case that broke it:
 * Liferay runs the rejection handler synchronously inside sendMessage, which
 * then returns normally.
 */
public class SearchEventDispatcherTest {

	@BeforeEach
	public void setUp() throws Exception {
		_messageBus = mock(MessageBus.class);
		_messageListenerRegistry = mock(MessageListenerRegistry.class);
		_statistics = new SearchEvalLoggerStatisticsImpl();

		_setListeners(List.of(mock(MessageListener.class)));

		_searchEventDispatcher = new SearchEventDispatcher();

		_inject(_searchEventDispatcher, "_messageBus", _messageBus);
		_inject(
			_searchEventDispatcher, "_messageListenerRegistry",
			_messageListenerRegistry);
		_inject(
			_searchEventDispatcher, "_searchEvalLoggerStatisticsImpl",
			_statistics);
	}

	@Test
	public void anAcceptedEventIsDispatched() {
		_searchEventDispatcher.dispatch(new CapturedSearchEvent());

		assertEquals(1, _statistics.getDispatchedEventCount());
		assertEquals(0, _statistics.getDroppedEventCount());
	}

	/**
	 * Drives the rejection handler the destination is actually configured
	 * with, on the sending thread and before sendMessage returns, which is
	 * how Liferay's serial destination runs it.
	 */
	@Test
	public void anEventTheQueueRejectsIsDroppedNotAlsoDispatched()
		throws Exception {

		RejectedExecutionHandler rejectedExecutionHandler =
			_getConfiguredRejectedExecutionHandler();

		doAnswer(
			invocation -> {
				rejectedExecutionHandler.rejectedExecution(null, null);

				return null;
			}
		).when(
			_messageBus
		).sendMessage(anyString(), any(Message.class));

		_searchEventDispatcher.dispatch(new CapturedSearchEvent());

		assertEquals(0, _statistics.getDispatchedEventCount());
		assertEquals(1, _statistics.getDroppedEventCount());
	}

	@Test
	public void aSendThatThrowsIsDroppedNotAlsoDispatched() {
		doThrow(
			new IllegalStateException("shut down")
		).when(
			_messageBus
		).sendMessage(anyString(), any(Message.class));

		_searchEventDispatcher.dispatch(new CapturedSearchEvent());

		assertEquals(0, _statistics.getDispatchedEventCount());
		assertEquals(1, _statistics.getDroppedEventCount());
	}

	@Test
	public void anEventWithNoListenerIsDropped() {
		_setListeners(Collections.emptyList());

		_searchEventDispatcher.dispatch(new CapturedSearchEvent());

		assertEquals(0, _statistics.getDispatchedEventCount());
		assertEquals(1, _statistics.getDroppedEventCount());
	}

	private RejectedExecutionHandler _getConfiguredRejectedExecutionHandler()
		throws Exception {

		DestinationFactory destinationFactory = mock(DestinationFactory.class);

		when(
			destinationFactory.createDestination(any())
		).thenReturn(
			mock(Destination.class)
		);

		SearchEvalLogDestinationConfigurator
			searchEvalLogDestinationConfigurator =
				new SearchEvalLogDestinationConfigurator();

		_inject(
			searchEvalLogDestinationConfigurator, "_destinationFactory",
			destinationFactory);
		_inject(
			searchEvalLogDestinationConfigurator,
			"_searchEvalLoggerStatisticsImpl", _statistics);

		searchEvalLogDestinationConfigurator.activate(
			mock(BundleContext.class));

		ArgumentCaptor<DestinationConfiguration> argumentCaptor =
			ArgumentCaptor.forClass(DestinationConfiguration.class);

		verify(
			destinationFactory
		).createDestination(
			argumentCaptor.capture()
		);

		DestinationConfiguration destinationConfiguration =
			argumentCaptor.getValue();

		return destinationConfiguration.getRejectedExecutionHandler();
	}

	private void _inject(Object target, String name, Object value)
		throws Exception {

		Field field = target.getClass().getDeclaredField(name);

		field.setAccessible(true);

		field.set(target, value);
	}

	private void _setListeners(List<MessageListener> messageListeners) {
		when(
			_messageListenerRegistry.getMessageListeners(anyString())
		).thenReturn(
			messageListeners
		);
	}

	private MessageBus _messageBus;
	private MessageListenerRegistry _messageListenerRegistry;
	private SearchEventDispatcher _searchEventDispatcher;
	private SearchEvalLoggerStatisticsImpl _statistics;

}
