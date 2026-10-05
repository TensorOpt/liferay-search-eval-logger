/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.notifications;

import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.UserNotificationDeliveryConstants;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.notifications.NotificationEvent;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.service.UserNotificationEventLocalService;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.ResourceBundleUtil;

import ai.tensoropt.sel.api.NotificationLanguageKeys;
import ai.tensoropt.sel.api.SearchEvalLoggerConstants;

import java.time.Clock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

import javax.mail.internet.InternetAddress;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Delivers the three local notifications of DESIGN.md 3.6 (collection started,
 * stall, readiness) - the only three in the plugin; the export-complete link
 * of 10.3 and the signup banner of 10.1 are static anchors, never a
 * <code>UserNotificationEvent</code>, so there is no fourth to deliver.
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
 * None of the three notifications carries an external link (10.2). They point
 * at this plugin's own screens or at nothing.
 * </p>
 *
 * <p>
 * <b>Website delivery is best effort.</b> EC-14 confirms delivery on a running
 * DXP 2025.Q1.27 instance for the stall and readiness notifications; the
 * collection-started one added by TO-112 uses the same delivery path and has
 * not separately been observed running. A failure here is logged and
 * swallowed, and the cycle is still marked as notified, so a delivery failure
 * degrades to the fallback 3.6 and 10.1 name rather than re-sending daily
 * forever: the same states are rendered on the admin screen, which is the
 * channel that does not depend on any of this.
 * </p>
 *
 * <p>
 * <b>Email delivery (TO-112, EC-15).</b> Each recipient also gets an email
 * through {@link EmailDelivery}, through the portal's own configured mail
 * server (D9), when {@link EmailDelivery#isWanted} says they want it -
 * respecting whatever they set under My Account &gt; Notifications for this
 * plugin, via the registered {@link
 * SearchEvalLoggerUserNotificationDefinition}. The email path is wrapped on
 * its own, separately from the website path above: a failure building or
 * sending it must never be able to undo the website delivery that already
 * happened for the same recipient, nor stop the loop from reaching the next
 * one. The subject and body are read from the same three
 * <code>content/Language.properties</code> keys the website notification
 * uses, in the recipient's own locale, resolved against the <b>api</b>
 * module's bundle rather than this one's: this module exports nothing and
 * the web module does not depend on it, so the two cannot share a resource
 * bundle belonging to either of them, and api is the one place both already
 * depend on. It contains only an internal admin-screen link, never an
 * external one.
 * </p>
 */
@Component(service = CollectionNotifier.class)
public class CollectionNotifier {

	/**
	 * Fired once per cycle from the persistence path (TO-112), the moment
	 * <code>collectionStartDate</code> is first set, rather than from the daily
	 * job: confirming a restart worked is only useful if it arrives close to
	 * when it happened, not up to 24 hours later.
	 */
	public void notifyCollectionStarted(long companyId, long enabledByUserId) {
		_notify(
			companyId, enabledByUserId,
			SearchEvalLoggerConstants.NOTIFICATION_TYPE_COLLECTION_STARTED);
	}

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

				// Delivered, because Liferay's notifications list and its
				// counter only show delivered website events. Stored as
				// undelivered, a notification exists in the database and
				// reaches nobody (TO-110).

				_userNotificationEventLocalService.addUserNotificationEvent(
					user.getUserId(), true, false, notificationEvent);

				_sendEmailIfWanted(companyId, user, notificationType);
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

	/**
	 * The email half of TO-112 / EC-15, isolated in its own try/catch so that
	 * a failure here - building the recipient's address, resolving the
	 * control panel link, or the send itself - can never undo the website
	 * delivery {@link #_notify} already completed for this same recipient, or
	 * stop it moving on to the next one.
	 */
	private void _sendEmailIfWanted(
		long companyId, User user, String notificationType) {

		try {
			if (!_emailDelivery.isWanted(user.getUserId(), companyId)) {
				return;
			}

			InternetAddress to = new InternetAddress(
				user.getEmailAddress(), user.getFullName());

			Locale locale = user.getLocale();

			if (locale == null) {
				locale = LocaleUtil.getDefault();
			}

			_emailDelivery.send(
				companyId, to, _emailSubject(locale, notificationType),
				_emailBody(companyId, locale, notificationType));
		}
		catch (Throwable throwable) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to email the " + notificationType +
						" notification to user " + user.getUserId(),
					throwable);
			}
		}
	}

	/**
	 * <code>null</code> when the link cannot be built, which {@link
	 * #_emailBody} reads as "no link" rather than failing the whole email: a
	 * reminder to restart is still useful without one, and the body already
	 * says that nothing a link can do is what is being asked for.
	 */
	private String _controlPanelURL(long companyId) {
		try {
			Group group = _groupLocalService.getCompanyGroup(companyId);

			return _portal.getControlPanelFullURL(
				group.getGroupId(), SearchEvalLoggerConstants.ADMIN_PORTLET_NAME,
				null);
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Unable to build a control panel link for company " +
						companyId,
					exception);
			}

			return null;
		}
	}

	private String _emailBody(
		long companyId, Locale locale, String notificationType) {

		String body = _message(
			locale, NotificationLanguageKeys.body(notificationType));

		if (Objects.equals(
				SearchEvalLoggerConstants.NOTIFICATION_TYPE_STALL,
				notificationType)) {

			// No link, same as the website notification (10.2): what this
			// one is asking for is a portal restart, not something a link
			// can do.

			return body;
		}

		String controlPanelURL = _controlPanelURL(companyId);

		if (controlPanelURL == null) {
			return body;
		}

		return body + " " + controlPanelURL;
	}

	private String _emailSubject(Locale locale, String notificationType) {
		return _message(
			locale, NotificationLanguageKeys.title(notificationType));
	}

	/**
	 * Resolved against the <b>api</b> module's resource bundle; see the class
	 * comment for why. Falls back to the key itself on any failure, the same
	 * fallback <code>SearchEvalLoggerUserNotificationHandler</code> uses for
	 * the website path, so an unresolvable message degrades to a readable
	 * (if unlocalised) key rather than failing the email outright.
	 */
	private String _message(Locale locale, String key) {
		try {
			ResourceBundle resourceBundle = ResourceBundleUtil.getBundle(
				locale, SearchEvalLoggerConstants.class);

			return _language.get(resourceBundle, key);
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug("Unable to resolve " + key, exception);
			}

			return key;
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CollectionNotifier.class);

	private Clock _clock = Clock.systemUTC();

	@Reference
	private EmailDelivery _emailDelivery;

	@Reference
	private GroupLocalService _groupLocalService;

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private Language _language;

	@Reference
	private Portal _portal;

	@Reference
	private RoleLocalService _roleLocalService;

	@Reference
	private UserLocalService _userLocalService;

	@Reference
	private UserNotificationEventLocalService
		_userNotificationEventLocalService;

}
