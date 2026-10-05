/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

/**
 * Values that more than one module has to agree on.
 *
 * <p>
 * The producer, the consumer, the purge job and the admin portlet live in
 * separate bundles, and the values below are the ones a mismatch would break
 * silently rather than loudly: a destination name typo detaches the listener
 * from the wrapper with no error. Keeping them here lets those modules agree
 * without depending on each other.
 * </p>
 *
 * <p>
 * Configuration defaults are deliberately not here. They are declared once,
 * as the <code>&#64;Meta.AD</code> defaults of
 * <code>SearchEvalLoggerConfiguration</code>, and anything that needs them
 * typed builds them from there with <code>ConfigurableUtil</code>.
 * </p>
 */
public final class SearchEvalLoggerConstants {

	/**
	 * Name of the admin portlet. It is also the <code>type</code> of the
	 * notifications in DESIGN.md 3.6, because Liferay resolves a notification
	 * to its handler by matching the event type against the handler's portlet
	 * id. The sender is in the impl bundle and the handler in the web bundle,
	 * so a typo would leave notifications stored and uninterpretable, showing
	 * as "no interpreter found" in the log and nothing at all to the
	 * administrator.
	 */
	public static final String ADMIN_PORTLET_NAME =
		"ai_tensoropt_sel_web_internal_portlet_SearchEvalLoggerPortlet";

	/**
	 * Message Bus destination carrying search events from the
	 * <code>Searcher</code> wrapper to the persisting listener. Registered as a
	 * serial destination so consumption is single threaded, which rate limits
	 * database write pressure on its own. See DESIGN.md 3.3.
	 */
	public static final String DESTINATION_NAME = "tensoropt/search-eval-log";

	/**
	 * The <code>classNameId</code> the three notifications of DESIGN.md 3.6
	 * register their email delivery preference under (TO-112, EC-15). Zero
	 * rather than a resolved class name id, because none of the three is
	 * backed by a real Liferay model the way a Message Boards post is;
	 * Liferay's own <code>MBAddEntryUserNotificationDefinition</code> uses the
	 * same zero for exactly that reason. Shared between the web module, which
	 * registers the <code>UserNotificationDefinition</code>, and the impl
	 * module, which checks <code>UserNotificationManagerUtil.isDeliver</code>
	 * against it: the two have to agree, or every check silently answers as if
	 * no definition were registered at all.
	 */
	public static final long DELIVERY_PREFERENCE_CLASS_NAME_ID = 0L;

	/**
	 * The <code>notificationType</code> paired with {@link
	 * #DELIVERY_PREFERENCE_CLASS_NAME_ID}. One value for all three
	 * notifications: they share a single email/website preference rather than
	 * three independently toggleable ones, which is the simpler choice absent
	 * any evidence an administrator wants to tell them apart.
	 */
	public static final int DELIVERY_PREFERENCE_NOTIFICATION_TYPE = 0;

	public static final String FIELD_SNIPPET = "snippet";

	public static final String FIELD_TITLE = "title";

	/**
	 * Key under which a notification's payload carries which of the two
	 * conditions in DESIGN.md 3.6 fired.
	 */
	public static final String NOTIFICATION_PAYLOAD_KEY_TYPE =
		"notificationType";

	/**
	 * Fired once per cycle from the persistence path the moment
	 * <code>collectionStartDate</code> is first set (TO-112), rather than from
	 * the daily job, so an administrator learns the restart worked without
	 * waiting up to 24 hours for the next run.
	 */
	public static final String NOTIFICATION_TYPE_COLLECTION_STARTED =
		"COLLECTION_STARTED";

	public static final String NOTIFICATION_TYPE_READINESS = "READINESS";

	public static final String NOTIFICATION_TYPE_STALL = "STALL";

	private SearchEvalLoggerConstants() {
		throw new AssertionError();
	}

}
