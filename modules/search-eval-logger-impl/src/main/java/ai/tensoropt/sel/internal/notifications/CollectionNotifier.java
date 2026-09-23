/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.notifications;

import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.UserNotificationDeliveryConstants;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.notifications.NotificationEvent;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.service.UserNotificationEventLocalService;

import ai.tensoropt.sel.api.SearchEvalLoggerConstants;

import java.time.Clock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Delivers the two local notifications of DESIGN.md 3.6.
 *
 * <p>
 * Notifications go to the administrator who switched logging on, because that
 * is the person waiting for the data. When that user cannot be resolved, or is
 * no longer active, they go to the instance administrators instead; an
 * unresolvable recipient is expected rather than exceptional, since
 * <code>enabledByUserId</code> is only recorded when the portal made the
 * enabling user's identity available on the configuration save (EC-14).
 * </p>
 *
 * <p>
 * Neither notification carries an external link (10.2). They point at this
 * plugin's own screens or at nothing.
 * </p>
 *
 * <p>
 * <b>This is best effort.</b> EC-14 is open: the user notification framework is
 * public API on the target platform and compiles, but nothing here has been
 * observed delivering a notification on a running instance. A failure is logged
 * and swallowed, and the cycle is still marked as notified, so a framework that
 * turns out not to work degrades to the fallback 3.6 names rather than
 * re-sending daily forever: the same two states are rendered as banners on the
 * admin screen, which is the channel that does not depend on any of this.
 * </p>
 */
@Component(service = CollectionNotifier.class)
public class CollectionNotifier {

	public void notifyReadiness(long companyId, long enabledByUserId) {
		_notify(
			companyId, enabledByUserId,
			SearchEvalLoggerConstants.NOTIFICATION_TYPE_READINESS);
	}

	public void notifyStall(long companyId, long enabledByUserId) {
		_notify(
			companyId, enabledByUserId,
			SearchEvalLoggerConstants.NOTIFICATION_TYPE_STALL);
	}

	/**
	 * Visible for testing, so a notification's timestamp can be asserted.
	 */
	void setClock(Clock clock) {
		_clock = clock;
	}

	private List<User> _getAdministrators(long companyId) {
		Role role = _roleLocalService.fetchRole(
			companyId, RoleConstants.ADMINISTRATOR);

		if (role == null) {
			return Collections.emptyList();
		}

		List<User> users = new ArrayList<>();

		for (User user : _userLocalService.getRoleUsers(role.getRoleId())) {
			if (user.isActive()) {
				users.add(user);
			}
		}

		return users;
	}

	private List<User> _getRecipients(long companyId, long enabledByUserId) {
		if (enabledByUserId > 0) {
			User user = _userLocalService.fetchUser(enabledByUserId);

			if ((user != null) && user.isActive() && !user.isGuestUser()) {
				return Collections.singletonList(user);
			}
		}

		return _getAdministrators(companyId);
	}

	private void _notify(
		long companyId, long enabledByUserId, String notificationType) {

		try {
			JSONObject payloadJSONObject = _jsonFactory.createJSONObject();

			payloadJSONObject.put(
				SearchEvalLoggerConstants.NOTIFICATION_PAYLOAD_KEY_TYPE,
				notificationType);

			List<User> users = _getRecipients(companyId, enabledByUserId);

			if (users.isEmpty() && _log.isWarnEnabled()) {
				_log.warn(
					"No recipient for the " + notificationType +
						" notification of company " + companyId);
			}

			for (User user : users) {

				// A distinct event per recipient. Sharing one would give them
				// the same uuid, and Liferay treats that as the same
				// notification.

				NotificationEvent notificationEvent = new NotificationEvent(
					_clock.millis(),
					SearchEvalLoggerConstants.ADMIN_PORTLET_NAME,
					payloadJSONObject);

				notificationEvent.setDeliveryType(
					UserNotificationDeliveryConstants.TYPE_WEBSITE);

				_userNotificationEventLocalService.addUserNotificationEvent(
					user.getUserId(), false, false, notificationEvent);
			}
		}
		catch (Throwable throwable) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to send the " + notificationType +
						" notification of company " + companyId +
							". The admin screen reports the same state.",
					throwable);
			}
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CollectionNotifier.class);

	private Clock _clock = Clock.systemUTC();

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private RoleLocalService _roleLocalService;

	@Reference
	private UserLocalService _userLocalService;

	@Reference
	private UserNotificationEventLocalService
		_userNotificationEventLocalService;

}
