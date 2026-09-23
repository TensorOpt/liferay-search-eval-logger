/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.funnel;

import java.time.LocalDate;

/**
 * The whole of DESIGN.md 10: two URLs and the rules for when each is shown.
 *
 * <p>
 * Both URLs are here, in one class, so that a reviewer or a fork has one place
 * to read and one place to change (10.4). Nothing else in the plugin contains
 * either of them: not the export archive, not the manifest, not the archive
 * README, and not the local notifications, which link only to this plugin's own
 * screens.
 * </p>
 *
 * <p>
 * <b>The values below are placeholders and are meant to be.</b> They are the
 * literal tokens from the design document rather than real addresses, so that
 * an unfinished deployment produces a visibly broken link instead of a
 * plausible wrong one. Substituting them is a deliberate act by whoever ships a
 * release.
 * </p>
 *
 * <p>
 * <b>D9, zero egress.</b> These strings become the <code>href</code> of an
 * ordinary anchor that opens in a new tab, and nothing else. The plugin never
 * fetches either address, from the server or from the browser: there is no
 * prefetch, no preload, no iframe, no image, no tracking pixel and no script
 * that touches them. Nothing about the instance travels either way. The signup
 * link carries one parameter, the collection start date at day granularity,
 * and the UTM tags; the booking link carries the UTM tags alone. No hostname,
 * instance id, company name, plugin version, row count, coverage figure or
 * date range appears in either.
 * </p>
 */
public final class EvaluationServiceLinks {

	/**
	 * Placeholder. See the class comment: this is the design document's token,
	 * not an address, and it is replaced when a release is cut.
	 */
	public static final String BOOKING_URL = "{{BOOKING_URL}}";

	/**
	 * Placeholder. See the class comment: this is the design document's token,
	 * not an address, and it is replaced when a release is cut.
	 */
	public static final String SIGNUP_URL = "{{SIGNUP_URL}}";

	/**
	 * The collection-start link of 10.1. <code>started</code> is the date
	 * collection actually began, which is what lets the reminder on the far
	 * side be scheduled from the start of the data rather than from the day
	 * somebody signed up.
	 *
	 * <p>
	 * <code>utm_medium</code> is <code>admin-screen</code>, not the
	 * <code>config-screen</code> 10.1 first wrote. The section paired that tag
	 * with a location on Liferay's generated configuration screen, which this
	 * plugin cannot render into; since the location half could not be built,
	 * keeping the tag half would have preserved an inaccuracy rather than a
	 * contract, and the first data the receiving end ever sees would have been
	 * mislabelled.
	 * </p>
	 */
	public static String getCollectionStartURL(LocalDate collectionStartDate) {
		return SIGNUP_URL + "?started=" + collectionStartDate +
			"&utm_source=liferay-plugin&utm_medium=admin-screen";
	}

	/**
	 * The export-complete link of 10.3. UTM tags only: at this point the
	 * administrator has the dataset in hand, and nothing about it needs to
	 * travel ahead of them.
	 */
	public static String getExportCompleteURL() {
		return BOOKING_URL +
			"?utm_source=liferay-plugin&utm_medium=export-complete";
	}

	/**
	 * Shown only once collection is demonstrably working: a start date exists,
	 * so an event has been persisted, and interception is not being bypassed.
	 *
	 * <p>
	 * Both conditions matter for the same reason. The date carried to the
	 * signup page is the date collection began, so offering the link on an
	 * instance that is enabled but bypassed (3.1) would schedule a reminder
	 * against a dataset that will never exist.
	 * </p>
	 */
	public static boolean isCollectionStartLinkVisible(
		boolean showEvaluationServiceLinks, boolean intercepting,
		LocalDate collectionStartDate) {

		if (!showEvaluationServiceLinks || !intercepting) {
			return false;
		}

		return collectionStartDate != null;
	}

	/**
	 * <code>exportSucceeded</code> means "at least one of the exports listed
	 * on the screen succeeded", which is the bounded reading of 10.3's "after
	 * a successful export" that the caller can answer from the list it has
	 * already loaded. See the comment at that call site.
	 */
	public static boolean isExportCompleteLinkVisible(
		boolean showEvaluationServiceLinks, boolean exportSucceeded) {

		return showEvaluationServiceLinks && exportSucceeded;
	}

	private EvaluationServiceLinks() {
		throw new AssertionError();
	}

}
