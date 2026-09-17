/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.internal.messaging;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageBus;

import com.tensoropt.search.eval.logger.api.SearchEvalLoggerConstants;
import com.tensoropt.search.eval.logger.internal.capture.CapturedSearchEvent;
import com.tensoropt.search.eval.logger.internal.statistics.SearchEvalLoggerStatisticsImpl;

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
 */
@Component(service = SearchEventDispatcher.class)
public class SearchEventDispatcher {

	public void dispatch(CapturedSearchEvent capturedSearchEvent) {
		if (capturedSearchEvent == null) {
			return;
		}

		try {
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

	@Reference
	private MessageBus _messageBus;

	@Reference
	private SearchEvalLoggerStatisticsImpl _searchEvalLoggerStatisticsImpl;

}
