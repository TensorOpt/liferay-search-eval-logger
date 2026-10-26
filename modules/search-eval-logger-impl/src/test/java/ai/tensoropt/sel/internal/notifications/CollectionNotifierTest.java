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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.notifications.NotificationEvent;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.service.UserNotificationEventLocalService;
import com.liferay.portal.kernel.util.Portal;

import ai.tensoropt.sel.api.SearchEvalLoggerConstants;
import ai.tensoropt.sel.internal.TestInjection;

import java.util.Arrays;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.mail.internet.InternetAddress;

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
		_roleLocalService = mock(RoleLocalService.class);
		_userLocalService = userLocalService;

		// Same fallback SearchEvalLoggerUserNotificationHandlerTest uses:
		// returning the key it was asked for, since there is no running
		// portal here to resolve ai.tensoropt.sel.api's real bundle through.
		// What these tests pin is which key is chosen, same as that test.

		_language = mock(Language.class);

		when(
			_language.get(any(ResourceBundle.class), anyString())
		).thenAnswer(
			invocation -> invocation.getArgument(1)
		);

		TestInjection.set(_collectionNotifier, "_emailDelivery", _emailDelivery);
		TestInjection.set(
			_collectionNotifier, "_groupLocalService", _groupLocalService);
		TestInjection.set(_collectionNotifier, "_jsonFactory", jsonFactory);
		TestInjection.set(_collectionNotifier, "_language", _language);
		TestInjection.set(_collectionNotifier, "_portal", _portal);
		TestInjection.set(
			_collectionNotifier, "_roleLocalService", _roleLocalService);
		TestInjection.set(
			_collectionNotifier, "_userLocalService", userLocalService);
		TestInjection.set(
			_collectionNotifier, "_userNotificationEventLocalService",
			_userNotificationEventLocalService);
	}

	/**
	 * TO-112 / EC-15: the gate. Nothing here mocks
	 * <code>UserNotificationManagerUtil</code> itself - that static call lives
	 * in <code>EmailDelivery</code>, which has no portal to run against in
	 * a unit test - but the decision this class makes with its answer is
	 * exactly what these pin.
	 */
	@Test
	public void noEmailIsSentWhenTheUserDoesNotWantIt() throws Exception {
		when(_emailDelivery.isWanted(_USER_ID)).thenReturn(false);

		_collectionNotifier.notifyReadiness(1L, _USER_ID);

		verify(
			_emailDelivery, never()
		).send(
			anyLong(), any(InternetAddress.class), anyString(), anyString()
		);
	}

	@Test
	public void emailIsSentWhenTheUserWantsIt() throws Exception {
		when(_emailDelivery.isWanted(_USER_ID)).thenReturn(true);

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
		when(_emailDelivery.isWanted(_USER_ID)).thenReturn(true);

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

		when(_emailDelivery.isWanted(_USER_ID)).thenReturn(true);
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
			_emailDelivery.isWanted(_USER_ID)
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

	/**
	 * F2-24: no enabling user is recorded at all (EC-14), so the fallback the
	 * class comment describes is exercised: the company's active
	 * administrators.
	 */
	@Test
	public void enabledByUserIdZeroFallsBackToActiveAdministrators()
		throws Exception {

		User administrator = _mockUser(30001L, true, false);

		_mockAdministrators(administrator);

		_collectionNotifier.notifyReadiness(1L, 0L);

		verify(
			_userNotificationEventLocalService
		).addUserNotificationEvent(
			eq(30001L), eq(true), anyBoolean(), any(NotificationEvent.class)
		);
	}

	/**
	 * The recorded enabling user no longer being active is exactly the kind
	 * of unresolvable recipient the class comment says is expected, not
	 * exceptional.
	 */
	@Test
	public void anInactiveEnablingUserFallsBackToActiveAdministrators()
		throws Exception {

		User inactiveUser = _mockUser(_USER_ID, false, false);
		User administrator = _mockUser(30002L, true, false);

		when(_userLocalService.fetchUser(_USER_ID)).thenReturn(inactiveUser);

		_mockAdministrators(administrator);

		_collectionNotifier.notifyReadiness(1L, _USER_ID);

		verify(
			_userNotificationEventLocalService
		).addUserNotificationEvent(
			eq(30002L), eq(true), anyBoolean(), any(NotificationEvent.class)
		);
		verify(
			_userNotificationEventLocalService, never()
		).addUserNotificationEvent(
			eq(_USER_ID), anyBoolean(), anyBoolean(),
			any(NotificationEvent.class)
		);
	}

	/**
	 * A recorded enabling user who resolves to the instance's guest user is
	 * unresolvable the same way an inactive one is.
	 */
	@Test
	public void aGuestEnablingUserFallsBackToActiveAdministrators()
		throws Exception {

		User guestUser = _mockUser(_USER_ID, true, true);
		User administrator = _mockUser(30003L, true, false);

		when(_userLocalService.fetchUser(_USER_ID)).thenReturn(guestUser);

		_mockAdministrators(administrator);

		_collectionNotifier.notifyReadiness(1L, _USER_ID);

		verify(
			_userNotificationEventLocalService
		).addUserNotificationEvent(
			eq(30003L), eq(true), anyBoolean(), any(NotificationEvent.class)
		);
	}

	/**
	 * Neither the enabling user nor any administrator resolves to anyone: the
	 * notification is skipped rather than sent to nobody. The warning itself
	 * is not independently asserted here; there is no running portal in this
	 * test to observe a log line against, so what is pinned is the
	 * observable behaviour the warning describes, that nothing is delivered.
	 */
	@Test
	public void noRecipientsSendsNoNotificationAndNoEmail() throws Exception {
		_collectionNotifier.notifyReadiness(1L, 0L);

		verify(
			_userNotificationEventLocalService, never()
		).addUserNotificationEvent(
			anyLong(), anyBoolean(), anyBoolean(), any(NotificationEvent.class)
		);
		verify(
			_emailDelivery, never()
		).send(
			anyLong(), any(InternetAddress.class), anyString(), anyString()
		);
	}

	/**
	 * A recipient with no recorded locale must not fail the email path; it
	 * falls back to the platform default rather than propagating a failure
	 * that would also undo the website delivery already completed for the
	 * same recipient.
	 *
	 * <p>
	 * Asserting only that send() was called cannot fail here:
	 * {@code _language.get} is mocked to return whatever key it was asked
	 * for regardless of the resource bundle passed in, so a subject or body
	 * built by actually resolving a null-safe locale and one built by
	 * {@code _message}'s own exception fallback (triggered by passing a
	 * null {@code Locale} straight to {@code ResourceBundleUtil.getBundle},
	 * which throws) come out as the exact same key text either way.
	 * Verifying {@code _language.get} itself was reached tells them apart:
	 * it is skipped entirely on the exception path.
	 * </p>
	 */
	@Test
	public void aRecipientWithNoLocaleStillGetsTheEmail() throws Exception {
		User user = _mockUser(_USER_ID, true, false);

		when(user.getLocale()).thenReturn(null);

		when(_userLocalService.fetchUser(_USER_ID)).thenReturn(user);
		when(_emailDelivery.isWanted(_USER_ID)).thenReturn(true);

		_collectionNotifier.notifyReadiness(1L, _USER_ID);

		verify(
			_emailDelivery
		).send(
			eq(1L), any(InternetAddress.class), anyString(), anyString()
		);
		verify(
			_language, atLeastOnce()
		).get(
			any(ResourceBundle.class), anyString()
		);
	}

	/**
	 * A failure recording the website notification must not propagate: the
	 * caller is unaffected (implicitly pinned by this test not itself
	 * throwing). {@code isWanted} is stubbed <code>true</code>, rather
	 * than left at Mockito's own default <code>false</code>: the website
	 * notification throws before {@code _sendEmailIfWanted} is ever
	 * reached for this recipient, so {@code never().send} would otherwise
	 * hold regardless of whether the exception were actually swallowed
	 * here or allowed to propagate past the per-recipient email step too.
	 */
	@Test
	public void addUserNotificationEventThrowingIsSwallowed() throws Exception {
		when(_emailDelivery.isWanted(_USER_ID)).thenReturn(true);

		doThrow(
			new RuntimeException("boom")
		).when(
			_userNotificationEventLocalService
		).addUserNotificationEvent(
			anyLong(), anyBoolean(), anyBoolean(), any(NotificationEvent.class)
		);

		_collectionNotifier.notifyReadiness(1L, _USER_ID);

		verify(
			_emailDelivery, never()
		).send(
			anyLong(), any(InternetAddress.class), anyString(), anyString()
		);
	}

	private void _mockAdministrators(User... administrators) {
		Role role = mock(Role.class);

		when(role.getRoleId()).thenReturn(_ADMINISTRATOR_ROLE_ID);
		when(
			_roleLocalService.fetchRole(1L, RoleConstants.ADMINISTRATOR)
		).thenReturn(
			role
		);
		when(
			_userLocalService.getRoleUsers(_ADMINISTRATOR_ROLE_ID)
		).thenReturn(
			Arrays.asList(administrators)
		);
	}

	private User _mockUser(long userId, boolean active, boolean guest) {
		User user = mock(User.class);

		when(user.getUserId()).thenReturn(userId);
		when(user.isActive()).thenReturn(active);
		when(user.isGuestUser()).thenReturn(guest);
		when(user.getEmailAddress()).thenReturn("user" + userId + "@example.com");
		when(user.getFullName()).thenReturn("User " + userId);
		when(user.getLocale()).thenReturn(Locale.US);

		return user;
	}

	private void _verifyDelivered() throws Exception {
		verify(
			_userNotificationEventLocalService
		).addUserNotificationEvent(
			eq(_USER_ID), eq(true), anyBoolean(), any(NotificationEvent.class)
		);
	}

	private static final long _ADMINISTRATOR_ROLE_ID = 10001L;

	private static final long _USER_ID = 20124L;

	private final CollectionNotifier _collectionNotifier =
		new CollectionNotifier();
	private EmailDelivery _emailDelivery;
	private GroupLocalService _groupLocalService;
	private Language _language;
	private Portal _portal;
	private RoleLocalService _roleLocalService;
	private UserLocalService _userLocalService;
	private UserNotificationEventLocalService
		_userNotificationEventLocalService;

}
