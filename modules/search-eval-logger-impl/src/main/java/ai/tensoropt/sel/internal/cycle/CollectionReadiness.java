/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.cycle;

import ai.tensoropt.sel.api.CollectionCycle;

import java.time.Duration;
import java.time.Instant;
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
	 * Whether logging is enabled, nothing has been collected in this cycle,
	 * searches are bypassing the wrapper (the silent failure of 3.1, otherwise
	 * visible only to someone who happens to open the admin screen), and the
	 * cycle has been open long enough that the bypass is unlikely to be a
	 * restart the administrator was already about to do (TO-112).
	 *
	 * <p>
	 * The grace period is fixed at 24 hours rather than exposed as a setting.
	 * It exists to suppress exactly one false alarm, the daily job landing
	 * between enabling and a restart already in progress, and a fixed value is
	 * enough for that; there is no evidence yet that an installation needs a
	 * different number, and a setting nobody has asked to tune is the kind of
	 * surface CLAUDE.md's YAGNI principle exists to keep out.
	 * </p>
	 *
	 * <p>
	 * <code>cycleOpenedDate</code> is <code>null</code> for a cycle opened
	 * before TO-112, which this reads as "grace period not over" rather than
	 * guessing: the cycle is reconciled with a real value the next time
	 * {@code CollectionCycleStatusImpl} sees it, and erring toward silence here
	 * costs at most one day's delay on a notification, the same safe direction
	 * 3.6 already takes when dispatch drops an event.
	 * </p>
	 */
	public static boolean isStalled(
		CollectionCycle collectionCycle, boolean intercepting, Instant now) {

		if (!collectionCycle.isOpen() || collectionCycle.isCollectionStarted() ||
			intercepting) {

			return false;
		}

		Instant cycleOpenedDate = collectionCycle.getCycleOpenedDate();

		if (cycleOpenedDate == null) {
			return false;
		}

		Duration elapsed = Duration.between(cycleOpenedDate, now);

		return !elapsed.minus(_STALL_GRACE_PERIOD).isNegative();
	}

	private static final Duration _STALL_GRACE_PERIOD = Duration.ofHours(24);

	private CollectionReadiness() {
		throw new AssertionError();
	}

}
