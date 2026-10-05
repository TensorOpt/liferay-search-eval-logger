/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

/**
 * The language keys of the three notifications of DESIGN.md 3.6, which live in
 * this bundle's <code>content/Language.properties</code>. One mapping for both
 * the website handler and the email sender, so a new notification type cannot
 * be added to one and silently fall back to the stall text in the other.
 *
 * <p>
 * Any type other than readiness or collection started maps to the stall
 * message, for the reason given on the website handler's type resolution.
 * </p>
 */
public final class NotificationLanguageKeys {

	public static String body(String notificationType) {
		return _prefix(notificationType) + "-body";
	}

	public static String title(String notificationType) {
		return _prefix(notificationType) + "-title";
	}

	private static String _prefix(String notificationType) {
		if (SearchEvalLoggerConstants.NOTIFICATION_TYPE_READINESS.equals(
				notificationType)) {

			return "notification-readiness";
		}

		if (SearchEvalLoggerConstants.NOTIFICATION_TYPE_COLLECTION_STARTED.
				equals(notificationType)) {

			return "notification-collection-started";
		}

		return "notification-stall";
	}

	private NotificationLanguageKeys() {
		throw new AssertionError();
	}

}
