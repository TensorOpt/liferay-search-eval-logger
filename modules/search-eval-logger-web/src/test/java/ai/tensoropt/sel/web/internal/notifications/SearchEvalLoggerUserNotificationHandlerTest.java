/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.UserNotificationEvent;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.Portal;

import ai.tensoropt.sel.api.SearchEvalLoggerConstants;

import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The two notifications say opposite things, so which one an unreadable
 * payload renders as is a decision, not a detail.
 *
 * <p>
 * Reading it as readiness would tell an administrator their search log is ready
 * to export on an instance that may have collected nothing, and they would find
 * out by running an empty export. Reading it as a stall costs them a restart
 * they may not have needed. Only an explicit readiness payload gets the
 * reassuring message.
 * </p>
 *
 * <p>
 * What these assertions pin is <em>which</em> message is selected, by its key,
 * not how that key is localised. Outside a portal there is no bundle to resolve
 * against, so every case here runs through the handler's own fallback of
 * returning the key it asked for - which is itself worth knowing, because that
 * fallback is the guarantee that an unresolvable message still produces a
 * notification rather than an exception.
 * </p>
 */
public class SearchEvalLoggerUserNotificationHandlerTest {

	@BeforeEach
	public void setUp() {
		_jsonFactory = mock(JSONFactory.class);

		Language language = mock(Language.class);

		when(
			language.get(any(ResourceBundle.class), anyString())
		).thenAnswer(
			invocation -> invocation.getArgument(1)
		);

		_searchEvalLoggerUserNotificationHandler =
			new SearchEvalLoggerUserNotificationHandler();

		_searchEvalLoggerUserNotificationHandler.setCollaborators(
			_jsonFactory, language, mock(Portal.class));

		_serviceContext = new ServiceContext();

		_serviceContext.setLanguageId("en_US");

		_userNotificationEvent = mock(UserNotificationEvent.class);
	}

	@Test
	public void anExplicitReadinessPayloadRendersAsReadiness() throws Exception {
		_setPayloadType(
			SearchEvalLoggerConstants.NOTIFICATION_TYPE_READINESS);

		assertEquals(
			"notification-readiness-title",
			_searchEvalLoggerUserNotificationHandler.getTitle(
				_userNotificationEvent, _serviceContext));
	}

	@Test
	public void anExplicitStallPayloadRendersAsAStall() throws Exception {
		_setPayloadType(SearchEvalLoggerConstants.NOTIFICATION_TYPE_STALL);

		assertEquals(
			"notification-stall-title",
			_searchEvalLoggerUserNotificationHandler.getTitle(
				_userNotificationEvent, _serviceContext));
	}

	@Test
	public void anUnparseablePayloadRendersAsAStall() throws Exception {
		when(
			_jsonFactory.createJSONObject(anyString())
		).thenThrow(
			new RuntimeException("not JSON")
		);

		when(
			_userNotificationEvent.getPayload()
		).thenReturn(
			"{ this is not json"
		);

		assertEquals(
			"notification-stall-title",
			_searchEvalLoggerUserNotificationHandler.getTitle(
				_userNotificationEvent, _serviceContext),
			"An unreadable payload must never say the log is ready to export");
	}

	/**
	 * A payload written by a future version, carrying a condition this one
	 * does not know about, is in the same position as an unreadable one.
	 */
	@Test
	public void anUnrecognisedPayloadTypeRendersAsAStall() throws Exception {
		_setPayloadType("SOME_LATER_CONDITION");

		assertEquals(
			"notification-stall-title",
			_searchEvalLoggerUserNotificationHandler.getTitle(
				_userNotificationEvent, _serviceContext));
	}

	/**
	 * <code>BaseUserNotificationHandler</code> catches whatever comes out of
	 * here and drops the whole feed entry, so an unguarded locale would turn
	 * an absent one into a notification that silently does not exist.
	 */
	@Test
	public void anAbsentLocaleStillRendersAMessage() throws Exception {
		_setPayloadType(
			SearchEvalLoggerConstants.NOTIFICATION_TYPE_READINESS);

		ServiceContext serviceContext = mock(ServiceContext.class);

		when(
			serviceContext.getLocale()
		).thenReturn(
			(Locale)null
		);

		assertEquals(
			"notification-readiness-title",
			_searchEvalLoggerUserNotificationHandler.getTitle(
				_userNotificationEvent, serviceContext));
	}

	private void _setPayloadType(String notificationType)
		throws Exception {

		JSONObject payloadJSONObject = mock(JSONObject.class);

		when(
			payloadJSONObject.getString(
				SearchEvalLoggerConstants.NOTIFICATION_PAYLOAD_KEY_TYPE)
		).thenReturn(
			notificationType
		);

		when(
			_userNotificationEvent.getPayload()
		).thenReturn(
			"{}"
		);

		when(
			_jsonFactory.createJSONObject(anyString())
		).thenReturn(
			payloadJSONObject
		);
	}

	private JSONFactory _jsonFactory;
	private SearchEvalLoggerUserNotificationHandler
		_searchEvalLoggerUserNotificationHandler;
	private ServiceContext _serviceContext;
	private UserNotificationEvent _userNotificationEvent;

}
