/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.cycle;

import ai.tensoropt.sel.api.CollectionCycle;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The two conditions of DESIGN.md 3.6, kept apart from the job that acts on
 * them so that both can be exercised without a scheduler, a database or a
 * notification framework.
 */
public final class CollectionReadiness {

	/**
	 * Whether the log has collected long enough and widely enough to be worth
	 * exporting.
	 *
	 * <p>
	 * Both thresholds are inclusive: a minimum of thirty days is met on the
	 * thirtieth day, not the thirty-first. They are read from the
	 * configuration, where an administrator who wants the notification sooner
	 * can lower either, so an off-by-one here would quietly contradict the
	 * number they typed.
	 * </p>
	 *
	 * <p>
	 * The elapsed days are counted from the collection start date, which is the
	 * first event actually persisted rather than the day logging was switched
	 * on. On an instance that was never restarted after installation (3.1) the
	 * two differ by however long the bypass lasted, and anchoring on the toggle
	 * would announce a dataset that does not exist.
	 * </p>
	 */
	public static boolean isReady(
		CollectionCycle collectionCycle, LocalDate today, int minimumDays,
		long eventCount, int minimumEvents) {

		if (!hasCollectedLongEnough(collectionCycle, today, minimumDays)) {
			return false;
		}

		return eventCount >= minimumEvents;
	}

	/**
	 * The date half of {@link #isReady}, separate because it is the cheap half:
	 * counting events costs a query, and there is no reason to run it on an
	 * instance that started collecting yesterday.
	 */
	public static boolean hasCollectedLongEnough(
		CollectionCycle collectionCycle, LocalDate today, int minimumDays) {

		LocalDate collectionStartDate =
			collectionCycle.getCollectionStartDate();

		if (collectionStartDate == null) {
			return false;
		}

		long elapsedDays = ChronoUnit.DAYS.between(collectionStartDate, today);

		return elapsedDays >= Math.max(minimumDays, 0);
	}

	/**
	 * Whether logging is enabled, nothing has been collected in this cycle, and
	 * searches are bypassing the wrapper: the silent failure of 3.1, which is
	 * otherwise visible only to someone who happens to open the admin screen.
	 */
	public static boolean isStalled(
		CollectionCycle collectionCycle, boolean intercepting) {

		return collectionCycle.isOpen() &&
			!collectionCycle.isCollectionStarted() && !intercepting;
	}

	private CollectionReadiness() {
		throw new AssertionError();
	}

}
