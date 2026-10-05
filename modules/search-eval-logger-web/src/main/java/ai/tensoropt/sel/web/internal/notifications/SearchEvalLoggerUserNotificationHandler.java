/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.notifications;

import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.UserNotificationEvent;
import com.liferay.portal.kernel.notifications.BaseUserNotificationHandler;
import com.liferay.portal.kernel.notifications.UserNotificationHandler;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.ResourceBundleUtil;

import ai.tensoropt.sel.api.SearchEvalLoggerConstants;
import ai.tensoropt.sel.web.internal.constants.SearchEvalLoggerPortletKeys;

import java.util.Locale;
import java.util.ResourceBundle;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Renders the three notifications of DESIGN.md 3.6 in Liferay's notification
 * feed: collection started, stall, and readiness (TO-112 adds the first).
 *
 * <p>
 * Liferay resolves a stored notification to its handler by matching the event's
 * type against a registered handler's portlet id, so this handler answers for
 * the admin portlet's name and the sender types its events with the same
 * constant.
 * </p>
 *
 * <p>
 * None of the three carries an external link (10.2). The readiness and
 * collection-started ones link to this plugin's own admin screen; the stall
 * one links nowhere, because the thing it is asking for is a portal restart,
 * which is not something a link can do.
 * </p>
 */
@Component(service = UserNotificationHandler.class)
public class SearchEvalLoggerUserNotificationHandler
	extends BaseUserNotificationHandler {

	public SearchEvalLoggerUserNotificationHandler() {
		setPortletId(SearchEvalLoggerPortletKeys.PORTLET_NAME);
	}

	@Override
	protected String getBody(
		UserNotificationEvent userNotificationEvent,
		ServiceContext serviceContext) {

		return _get(
			serviceContext,
			_bodyKey(_getNotificationType(userNotificationEvent)));
	}

	@Override
	protected String getLink(
		UserNotificationEvent userNotificationEvent,
		ServiceContext serviceContext) {

		if (_STALL.equals(_getNotificationType(userNotificationEvent))) {
			return "";
		}

		try {
			return _portal.getControlPanelFullURL(
				serviceContext.getScopeGroupId(),
				SearchEvalLoggerPortletKeys.PORTLET_NAME, null);
		}
		catch (Exception exception) {

			// A notification without a link still says what happened. Failing
			// to build one must not turn the whole feed entry into an error.

			if (_log.isDebugEnabled()) {
				_log.debug(
					"Unable to build a link to the export screen", exception);
			}

			return "";
		}
	}

	@Override
	protected String getTitle(
		UserNotificationEvent userNotificationEvent,
		ServiceContext serviceContext) {

		return _get(
			serviceContext,
			_titleKey(_getNotificationType(userNotificationEvent)));
	}

	/**
	 * Visible for testing. The runtime binds the fields directly; there are no
	 * production callers.
	 */
	void setCollaborators(
		JSONFactory jsonFactory, Language language, Portal portal) {

		_jsonFactory = jsonFactory;
		_language = language;
		_portal = portal;
	}

	/**
	 * Resolved against the <b>api</b> module's resource bundle, not this one's
	 * own (TO-112): <code>ResourceBundleUtil.getBundle(Locale, Class)</code>
	 * loads <code>content/Language.properties</code> from whichever OSGi
	 * bundle the passed class belongs to, and the three notification keys
	 * live in the api module's copy, alongside the
	 * <code>&#64;Meta.OCD</code> labels that already prove this mechanism
	 * resolves correctly against that bundle. They live there rather than
	 * here because {@link ai.tensoropt.sel.internal.notifications.
	 * CollectionNotifier}, in the impl module, needs the exact same text for
	 * the email half of these notifications and cannot depend on this
	 * module to read it; api is the one place both already depend on. A
	 * module's language keys are not in the portal's global bundle, so a
	 * plain lookup with no class at all would render the key itself as the
	 * notification text.
	 *
	 * <p>
	 * A missing locale falls back to the portal default instead of reaching
	 * the bundle loader as null. The caller is
	 * <code>BaseUserNotificationHandler</code>, which catches whatever comes
	 * out of here and drops the whole feed entry, so an unguarded lookup would
	 * turn an absent locale into a notification that silently does not exist.
	 * </p>
	 */
	private String _get(ServiceContext serviceContext, String key) {
		try {
			Locale locale = serviceContext.getLocale();

			if (locale == null) {
				locale = LocaleUtil.getDefault();
			}

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

	private String _bodyKey(String notificationType) {
		if (_READINESS.equals(notificationType)) {
			return "notification-readiness-body";
		}

		if (_COLLECTION_STARTED.equals(notificationType)) {
			return "notification-collection-started-body";
		}

		return "notification-stall-body";
	}

	/**
	 * Which of the three messages of DESIGN.md 3.6 to render.
	 *
	 * <p>
	 * Only a payload that explicitly names readiness or collection-started is
	 * rendered as one of those. Anything else - a payload that will not parse,
	 * one carrying a type this version does not know, one written by a future
	 * version - falls to the stall message. The three are not interchangeable
	 * and the asymmetry is the point: rendering an unreadable payload as
	 * readiness would tell an administrator their search log is ready to
	 * export on an instance that may have collected nothing at all, and they
	 * would find out by running an empty export. The stall message costs them
	 * a restart they may not have needed, which is the safer wrong answer of
	 * the three.
	 * </p>
	 */
	private String _getNotificationType(
		UserNotificationEvent userNotificationEvent) {

		try {
			JSONObject payloadJSONObject = _jsonFactory.createJSONObject(
				userNotificationEvent.getPayload());

			String notificationType = payloadJSONObject.getString(
				SearchEvalLoggerConstants.NOTIFICATION_PAYLOAD_KEY_TYPE);

			if (_READINESS.equals(notificationType) ||
				_COLLECTION_STARTED.equals(notificationType)) {

				return notificationType;
			}

			return _STALL;
		}
		catch (Exception exception) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to read a search eval notification payload, " +
						"showing it as a collection stall",
					exception);
			}

			return _STALL;
		}
	}

	private String _titleKey(String notificationType) {
		if (_READINESS.equals(notificationType)) {
			return "notification-readiness-title";
		}

		if (_COLLECTION_STARTED.equals(notificationType)) {
			return "notification-collection-started-title";
		}

		return "notification-stall-title";
	}

	private static final String _COLLECTION_STARTED =
		SearchEvalLoggerConstants.NOTIFICATION_TYPE_COLLECTION_STARTED;

	private static final String _READINESS =
		SearchEvalLoggerConstants.NOTIFICATION_TYPE_READINESS;

	private static final String _STALL =
		SearchEvalLoggerConstants.NOTIFICATION_TYPE_STALL;

	private static final Log _log = LogFactoryUtil.getLog(
		SearchEvalLoggerUserNotificationHandler.class);

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private Language _language;

	@Reference
	private Portal _portal;

}
