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

	public static final String FIELD_SNIPPET = "snippet";

	public static final String FIELD_TITLE = "title";

	/**
	 * Key under which a notification's payload carries which of the two
	 * conditions in DESIGN.md 3.6 fired.
	 */
	public static final String NOTIFICATION_PAYLOAD_KEY_TYPE =
		"notificationType";

	public static final String NOTIFICATION_TYPE_READINESS = "READINESS";

	public static final String NOTIFICATION_TYPE_STALL = "STALL";

	private SearchEvalLoggerConstants() {
		throw new AssertionError();
	}

}
