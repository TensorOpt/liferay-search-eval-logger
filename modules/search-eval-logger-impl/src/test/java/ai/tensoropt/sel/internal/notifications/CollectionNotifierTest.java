/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.notifications;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.notifications.NotificationEvent;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.service.UserNotificationEventLocalService;
import com.liferay.portal.kernel.util.Portal;

import ai.tensoropt.sel.api.SearchEvalLoggerConstants;

import java.lang.reflect.Field;

import java.util.Locale;
import java.util.ResourceBundle;

import jakarta.mail.internet.InternetAddress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Liferay's notifications list and its counter show only delivered website
 * events. A notification stored as undelivered sits in the database and
 * reaches nobody, which is how both of these shipped until TO-110.
 */
public class CollectionNotifierTest {

	@BeforeEach
	public void setUp() throws Exception {
		JSONFactory jsonFactory = mock(JSONFactory.class);

		when(
			jsonFactory.createJSONObject()
		).thenReturn(
			mock(JSONObject.class)
		);

		User user = mock(User.class);

		when(user.getUserId()).thenReturn(_USER_ID);
		when(user.isActive()).thenReturn(true);
		when(user.getEmailAddress()).thenReturn("admin@example.com");
		when(user.getFullName()).thenReturn("Admin");
		when(user.getLocale()).thenReturn(Locale.US);

		UserLocalService userLocalService = mock(UserLocalService.class);

		when(userLocalService.fetchUser(_USER_ID)).thenReturn(user);

		_userNotificationEventLocalService = mock(
			UserNotificationEventLocalService.class);

		// Not wanted by default. Every existing test that does not care
		// about email keeps asserting only the website path this way,
		// without being tripped up by a null isWanted() here throwing and
		// (harmlessly, but noisily) being swallowed by _sendEmailIfWanted.

		_emailDelivery = mock(EmailDelivery.class);

		_groupLocalService = mock(GroupLocalService.class);
		_portal = mock(Portal.class);

		// Same fallback SearchEvalLoggerUserNotificationHandlerTest uses:
		// returning the key it was asked for, since there is no running
		// portal here to resolve ai.tensoropt.sel.api's real bundle through.
		// What these tests pin is which key is chosen, same as that test.

		Language language = mock(Language.class);

		when(
			language.get(any(ResourceBundle.class), anyString())
		).thenAnswer(
			invocation -> invocation.getArgument(1)
		);

		_inject("_emailDelivery", _emailDelivery);
		_inject("_groupLocalService", _groupLocalService);
		_inject("_jsonFactory", jsonFactory);
		_inject("_language", language);
		_inject("_portal", _portal);
		_inject("_userLocalService", userLocalService);
		_inject(
			"_userNotificationEventLocalService",
			_userNotificationEventLocalService);
	}

	/**
	 * TO-112 / EC-15: the gate. Nothing here mocks
	 * <code>UserNotificationManagerUtil</code> itself - that static call lives
	 * in <code>EmailDeliveryImpl</code>, which has no portal to run against in
	 * a unit test - but the decision this class makes with its answer is
	 * exactly what these pin.
	 */
	@Test
	public void noEmailIsSentWhenTheUserDoesNotWantIt() throws Exception {
		when(_emailDelivery.isWanted(_USER_ID, 1L)).thenReturn(false);

		_collectionNotifier.notifyReadiness(1L, _USER_ID);

		verify(
			_emailDelivery, never()
		).send(
			anyLong(), any(InternetAddress.class), anyString(), anyString()
		);
	}

	@Test
	public void emailIsSentWhenTheUserWantsIt() throws Exception {
		when(_emailDelivery.isWanted(_USER_ID, 1L)).thenReturn(true);

		Group group = mock(Group.class);

		when(group.getGroupId()).thenReturn(99L);
		when(_groupLocalService.getCompanyGroup(1L)).thenReturn(group);
		when(
			_portal.getControlPanelFullURL(
				99L, SearchEvalLoggerConstants.ADMIN_PORTLET_NAME, null)
		).thenReturn(
			"http://portal/group/control_panel/manage"
		);

		_collectionNotifier.notifyReadiness(1L, _USER_ID);

		verify(
			_emailDelivery
		).send(
			eq(1L), any(InternetAddress.class),
			eq("notification-readiness-title"),
			argThat(
				body -> body.contains(
					"http://portal/group/control_panel/manage"))
		);
	}

	/**
	 * The stall email, like the stall website notification (10.2), carries no
	 * link: what it is asking for is a portal restart, not something a link
	 * can do.
	 */
	@Test
	public void theStallEmailCarriesNoLink() throws Exception {
		when(_emailDelivery.isWanted(_USER_ID, 1L)).thenReturn(true);

		_collectionNotifier.notifyStall(1L, _USER_ID);

		verify(
			_emailDelivery
		).send(
			eq(1L), any(InternetAddress.class), anyString(),
			argThat(body -> !body.contains("http"))
		);
		verify(_groupLocalService, never()).getCompanyGroup(anyLong());
	}

	/**
	 * A link that cannot be built is a degraded email, not a failure: the
	 * notification still says what happened, just without the link.
	 */
	@Test
	public void aFailureResolvingTheControlPanelLinkStillSendsTheEmail()
		throws Exception {

		when(_emailDelivery.isWanted(_USER_ID, 1L)).thenReturn(true);
		when(
			_groupLocalService.getCompanyGroup(1L)
		).thenThrow(
			new RuntimeException("boom")
		);

		_collectionNotifier.notifyReadiness(1L, _USER_ID);

		verify(
			_emailDelivery
		).send(
			eq(1L), any(InternetAddress.class), anyString(),
			argThat(body -> !body.contains("http"))
		);
	}

	/**
	 * The failure isolation TO-112 requires: a problem anywhere in the email
	 * path must never be able to undo the website delivery that already
	 * happened for the same recipient.
	 */
	@Test
	public void aFailureInTheEmailPathDoesNotPreventTheWebsiteNotification()
		throws Exception {

		when(
			_emailDelivery.isWanted(_USER_ID, 1L)
		).thenThrow(
			new RuntimeException("boom")
		);

		_collectionNotifier.notifyReadiness(1L, _USER_ID);

		_verifyDelivered();
	}

	@Test
	public void theCollectionStartedNotificationIsStoredAsDelivered()
		throws Exception {

		_collectionNotifier.notifyCollectionStarted(1L, _USER_ID);

		_verifyDelivered();
	}

	@Test
	public void theReadinessNotificationIsStoredAsDelivered()
		throws Exception {

		_collectionNotifier.notifyReadiness(1L, _USER_ID);

		_verifyDelivered();
	}

	@Test
	public void theStallNotificationIsStoredAsDelivered() throws Exception {
		_collectionNotifier.notifyStall(1L, _USER_ID);

		_verifyDelivered();
	}

	private void _inject(String name, Object value) throws Exception {
		Field field = CollectionNotifier.class.getDeclaredField(name);

		field.setAccessible(true);

		field.set(_collectionNotifier, value);
	}

	private void _verifyDelivered() throws Exception {
		verify(
			_userNotificationEventLocalService
		).addUserNotificationEvent(
			eq(_USER_ID), eq(true), anyBoolean(), any(NotificationEvent.class)
		);
	}

	private static final long _USER_ID = 20124L;

	private final CollectionNotifier _collectionNotifier =
		new CollectionNotifier();
	private EmailDelivery _emailDelivery;
	private GroupLocalService _groupLocalService;
	private Portal _portal;
	private UserNotificationEventLocalService
		_userNotificationEventLocalService;

}
