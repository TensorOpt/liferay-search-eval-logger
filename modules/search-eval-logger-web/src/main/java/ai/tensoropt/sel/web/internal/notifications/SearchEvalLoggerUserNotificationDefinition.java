/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.notifications;

import com.liferay.portal.kernel.model.UserNotificationDeliveryConstants;
import com.liferay.portal.kernel.notifications.UserNotificationDefinition;
import com.liferay.portal.kernel.notifications.UserNotificationDeliveryType;

import ai.tensoropt.sel.api.SearchEvalLoggerConstants;
import ai.tensoropt.sel.web.internal.constants.SearchEvalLoggerPortletKeys;

import org.osgi.service.component.annotations.Component;

/**
 * Registers an email delivery option for the three notifications of
 * DESIGN.md 3.6, next to the website delivery every
 * <code>UserNotificationEvent</code> already gets (TO-112, EC-15).
 *
 * <p>
 * Without this, <code>UserNotificationManagerUtil.isDeliver</code> finds no
 * registered definition for this plugin's (portlet id, classNameId,
 * notificationType) and answers <code>true</code> for email unconditionally -
 * confirmed by reading <code>BaseUserNotificationHandler.isDeliver</code> in
 * the shipped <code>portal-kernel.jar</code> - which would send email whether
 * or not an administrator wants it and give them no preference to turn off.
 * Registering this is what makes the preference real: it appears in My
 * Account &gt; Notifications, and <code>UserNotificationDeliveryLocalService</code>
 * is what then stores whatever an administrator sets it to.
 * </p>
 *
 * <p>
 * <b>Website is registered <code>isModifiable=false</code>, deliberately.</b>
 * Confirmed by reading <code>UserNotificationEventLocalServiceImpl</code>, the
 * concrete class behind <code>addUserNotificationEvent</code>:
 * <code>CollectionNotifier</code>'s website path, nothing there ever calls
 * <code>isDeliver</code> for <code>TYPE_WEBSITE</code> (only
 * <code>SubscriptionSender.sendUserNotification</code> does, and this plugin
 * does not use <code>SubscriptionSender</code>). Registering website as
 * modifiable would put a toggle in My Account &gt; Notifications that does
 * nothing when unticked - the bell notification and the stall/readiness
 * banners on this plugin's own admin screen read the same underlying record
 * and are meant to always be there regardless of a per-user preference, by
 * design (3.6: the admin screen is the fallback delivery channel EC-14
 * names). <code>isModifiable=false</code> is what keeps the UI honest about
 * that rather than offering a control this plugin cannot honour.
 * </p>
 *
 * <p>
 * One definition covers all three notification kinds (collection started,
 * stall, readiness) rather than three independently toggleable ones, the
 * simpler choice absent any evidence an administrator wants to tell them
 * apart; see {@link SearchEvalLoggerConstants#DELIVERY_PREFERENCE_CLASS_NAME_ID}.
 * The tracker that resolves this at runtime,
 * <code>UserNotificationManagerUtil</code>, keys it by the
 * <code>jakarta.portlet.name</code> service property, exactly as Liferay's own
 * <code>MBAddEntryUserNotificationDefinition</code> is keyed; omitting that
 * property leaves this definition registered but never found.
 * </p>
 */
@Component(
	property = "jakarta.portlet.name=" + SearchEvalLoggerPortletKeys.PORTLET_NAME,
	service = UserNotificationDefinition.class
)
public class SearchEvalLoggerUserNotificationDefinition
	extends UserNotificationDefinition {

	public SearchEvalLoggerUserNotificationDefinition() {
		super(
			SearchEvalLoggerPortletKeys.PORTLET_NAME,
			SearchEvalLoggerConstants.DELIVERY_PREFERENCE_CLASS_NAME_ID,
			SearchEvalLoggerConstants.DELIVERY_PREFERENCE_NOTIFICATION_TYPE,
			"search-eval-logger-notifications-description");

		addUserNotificationDeliveryType(
			new UserNotificationDeliveryType(
				"website", UserNotificationDeliveryConstants.TYPE_WEBSITE, true,
				false));
		addUserNotificationDeliveryType(
			new UserNotificationDeliveryType(
				"email", UserNotificationDeliveryConstants.TYPE_EMAIL, true,
				true));
	}

}
