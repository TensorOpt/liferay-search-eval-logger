/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.notifications;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.notifications.NotificationEvent;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.service.UserNotificationEventLocalService;

import java.lang.reflect.Field;

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

		UserLocalService userLocalService = mock(UserLocalService.class);

		when(userLocalService.fetchUser(_USER_ID)).thenReturn(user);

		_userNotificationEventLocalService = mock(
			UserNotificationEventLocalService.class);

		_inject("_jsonFactory", jsonFactory);
		_inject("_userLocalService", userLocalService);
		_inject(
			"_userNotificationEventLocalService",
			_userNotificationEventLocalService);
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
	private UserNotificationEventLocalService
		_userNotificationEventLocalService;

}
