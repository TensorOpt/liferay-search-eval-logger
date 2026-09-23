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
import java.util.Objects;
import java.util.ResourceBundle;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Renders the two notifications of DESIGN.md 3.6 in Liferay's notification
 * feed.
 *
 * <p>
 * Liferay resolves a stored notification to its handler by matching the event's
 * type against a registered handler's portlet id, so this handler answers for
 * the admin portlet's name and the sender types its events with the same
 * constant.
 * </p>
 *
 * <p>
 * Neither notification carries an external link (10.2). The readiness one links
 * to this plugin's own export screen; the stall one links nowhere, because the
 * thing it is asking for is a portal restart, which is not something a link can
 * do.
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

		if (_isStall(userNotificationEvent)) {
			return _get(serviceContext, "notification-stall-body");
		}

		return _get(serviceContext, "notification-readiness-body");
	}

	@Override
	protected String getLink(
		UserNotificationEvent userNotificationEvent,
		ServiceContext serviceContext) {

		if (_isStall(userNotificationEvent)) {
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

		if (_isStall(userNotificationEvent)) {
			return _get(serviceContext, "notification-stall-title");
		}

		return _get(serviceContext, "notification-readiness-title");
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
	 * Resolved against this bundle's own resource bundle rather than the
	 * portal's. A module's language keys are not in the global one, so a plain
	 * lookup would render the key itself as the notification text.
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
				locale, SearchEvalLoggerUserNotificationHandler.class);

			return _language.get(resourceBundle, key);
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug("Unable to resolve " + key, exception);
			}

			return key;
		}
	}

	/**
	 * Whether to render the stall message rather than the readiness one.
	 *
	 * <p>
	 * Only a payload that explicitly says so is rendered as readiness.
	 * Anything else - a payload that will not parse, one carrying a type this
	 * version does not know, one written by a future version - falls to the
	 * stall message. The two are not interchangeable and the asymmetry is the
	 * point: rendering an unreadable payload as readiness would tell an
	 * administrator their search log is ready to export on an instance that
	 * may have collected nothing at all, and they would find out by running an
	 * empty export. The stall message costs them a restart they may not have
	 * needed.
	 * </p>
	 */
	private boolean _isStall(UserNotificationEvent userNotificationEvent) {
		try {
			JSONObject payloadJSONObject = _jsonFactory.createJSONObject(
				userNotificationEvent.getPayload());

			return !Objects.equals(
				SearchEvalLoggerConstants.NOTIFICATION_TYPE_READINESS,
				payloadJSONObject.getString(
					SearchEvalLoggerConstants.NOTIFICATION_PAYLOAD_KEY_TYPE));
		}
		catch (Exception exception) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to read a search eval notification payload, " +
						"showing it as a collection stall",
					exception);
			}

			return true;
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		SearchEvalLoggerUserNotificationHandler.class);

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private Language _language;

	@Reference
	private Portal _portal;

}
