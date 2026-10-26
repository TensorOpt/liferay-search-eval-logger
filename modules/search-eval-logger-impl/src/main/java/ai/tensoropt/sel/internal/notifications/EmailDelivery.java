/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.notifications;

import com.liferay.mail.kernel.model.MailMessage;
import com.liferay.mail.kernel.service.MailServiceUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.UserNotificationDeliveryConstants;
import com.liferay.portal.kernel.notifications.UserNotificationManagerUtil;
import com.liferay.portal.kernel.util.PrefsPropsUtil;
import com.liferay.portal.kernel.util.PropsKeys;

import ai.tensoropt.sel.api.SearchEvalLoggerConstants;

import jakarta.mail.internet.InternetAddress;

import org.osgi.service.component.annotations.Component;

/**
 * The one place this plugin calls <code>UserNotificationManagerUtil</code>
 * and <code>MailServiceUtil</code> (TO-112, EC-15). Deliberately thin and
 * without its own unit test, the same choice already made for
 * <code>CollectionCycleStore</code>: both are two or three lines deep around
 * a single Liferay static call, and what is worth testing is the decision
 * {@link CollectionNotifier} makes with the answer, not this class's ability
 * to call a method. A concrete component with no interface of its own, also
 * like <code>CollectionCycleStore</code>: {@link CollectionNotifier} is its
 * one caller and mocks it directly in tests, so an interface here would
 * exist only to be implemented once.
 *
 * <p>
 * <code>UserNotificationManagerUtil.isDeliver</code> is the exact call
 * <code>SubscriptionSender</code> makes before emailing a subscriber,
 * confirmed by decompiling the shipped <code>portal-kernel.jar</code>: it
 * resolves the registered {@link SearchEvalLoggerUserNotificationDefinition}
 * (via <code>BaseUserNotificationHandler.isDeliver</code>, matched by portlet
 * id) and, through it, the administrator's own
 * <code>UserNotificationDelivery</code> preference, falling back to the
 * delivery type's own default when the administrator never set one.
 * </p>
 */
@Component(service = EmailDelivery.class)
public class EmailDelivery {

	/**
	 * Whether <code>userId</code> wants this plugin's notifications by email,
	 * respecting whatever they set in My Account &gt; Notifications (or the
	 * registered default, if they never touched it). Answers
	 * <code>false</code> on any failure to resolve the preference: an
	 * administrator who never gets an email they wanted notices nothing
	 * different from today; one who gets email they tried to turn off would
	 * notice, so failures fail toward silence.
	 */
	public boolean isWanted(long userId) {
		try {
			return UserNotificationManagerUtil.isDeliver(
				userId, SearchEvalLoggerConstants.ADMIN_PORTLET_NAME,
				SearchEvalLoggerConstants.DELIVERY_PREFERENCE_CLASS_NAME_ID,
				SearchEvalLoggerConstants.DELIVERY_PREFERENCE_NOTIFICATION_TYPE,
				UserNotificationDeliveryConstants.TYPE_EMAIL);
		}
		catch (Throwable throwable) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Unable to read the email delivery preference of user " +
						userId,
					throwable);
			}

			return false;
		}
	}

	/**
	 * Sends one email through the portal's own configured mail server (D9).
	 * Never throws: a failure here must not be able to undo the website
	 * delivery that happens alongside it, so this logs and swallows on its
	 * own rather than leaving that to its caller.
	 */
	public void send(
		long companyId, InternetAddress to, String subject, String body) {

		try {
			MailMessage mailMessage = new MailMessage(
				_from(companyId), to, subject, body, false);

			MailServiceUtil.sendEmail(mailMessage);
		}
		catch (Throwable throwable) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to send a search eval logger notification " +
						"email to " + to,
					throwable);
			}
		}
	}

	/**
	 * The portal's own configured "from" address (Instance Settings &gt;
	 * Email &gt; Email Sender, per company; falls back to the portal
	 * property), the same source Liferay's own system emails use. Never the
	 * installing administrator's own address: this is mail the portal sends
	 * to its own users, not mail attributed to a person.
	 */
	private InternetAddress _from(long companyId) throws Exception {
		return new InternetAddress(
			PrefsPropsUtil.getString(
				companyId, PropsKeys.ADMIN_EMAIL_FROM_ADDRESS),
			PrefsPropsUtil.getString(companyId, PropsKeys.ADMIN_EMAIL_FROM_NAME));
	}

	private static final Log _log = LogFactoryUtil.getLog(
		EmailDelivery.class);

}
