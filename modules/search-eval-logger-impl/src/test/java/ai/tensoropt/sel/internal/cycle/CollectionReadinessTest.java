/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.cycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tensoropt.sel.api.CollectionCycle;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * The thresholds in DESIGN.md 3.6 are what decide whether an administrator is
 * told their log is worth exporting, and an off-by-one in either would
 * contradict the number they configured. Both boundaries are pinned here.
 */
public class CollectionReadinessTest {

	@Test
	public void notReadyWhileNothingHasBeenCollected() {
		assertFalse(
			CollectionReadiness.isReady(
				CollectionCycle.open(1L, 0, _OPENED), _TODAY, 30, 100000L,
				500),
			"A cycle with no collection start date has collected nothing, " +
				"whatever else is true of it");
	}

	@Test
	public void readyOnTheDayBothThresholdsAreMet() {
		assertTrue(
			CollectionReadiness.isReady(
				_cycleStartedOn(_TODAY.minusDays(30)), _TODAY, 30, 500L, 500));
	}

	@Test
	public void notReadyOnTheDayBeforeTheDayThreshold() {
		assertFalse(
			CollectionReadiness.isReady(
				_cycleStartedOn(_TODAY.minusDays(29)), _TODAY, 30, 500L, 500));
	}

	@Test
	public void notReadyOneEventShortOfTheEventThreshold() {
		assertFalse(
			CollectionReadiness.isReady(
				_cycleStartedOn(_TODAY.minusDays(30)), _TODAY, 30, 499L, 500));
	}

	/**
	 * Both conditions, not either. A log that has run for months but recorded
	 * almost nothing is exactly the case 3.6's event threshold exists for.
	 */
	@Test
	public void bothThresholdsAreRequired() {
		assertFalse(
			CollectionReadiness.isReady(
				_cycleStartedOn(_TODAY.minusDays(365)), _TODAY, 30, 12L, 500));
		assertFalse(
			CollectionReadiness.isReady(
				_cycleStartedOn(_TODAY), _TODAY, 30, 1000000L, 500));
	}

	/**
	 * Zero for both means "notify as soon as anything has been collected",
	 * which is a legitimate setting on a busy instance and must not be read as
	 * "never".
	 */
	@Test
	public void zeroThresholdsAreMetOnTheFirstDay() {
		assertTrue(
			CollectionReadiness.isReady(
				_cycleStartedOn(_TODAY), _TODAY, 0, 0L, 0));
	}

	/**
	 * A start date in the future can only come from a clock that moved
	 * backwards. Counting the negative elapsed days as met would announce a
	 * dataset that does not exist yet.
	 */
	@Test
	public void aFutureStartDateIsNotReady() {
		assertFalse(
			CollectionReadiness.isReady(
				_cycleStartedOn(_TODAY.plusDays(1)), _TODAY, 0, 1000L, 0));
	}

	@Test
	public void stalledWhenEnabledCollectingNothingBypassedAndPastGrace() {
		assertTrue(
			CollectionReadiness.isStalled(
				CollectionCycle.open(1L, 0, _OPENED_OVER_A_DAY_AGO), false,
				_NOW));
	}

	@Test
	public void notStalledWhileInterceptionIsWorking() {
		assertFalse(
			CollectionReadiness.isStalled(
				CollectionCycle.open(1L, 0, _OPENED_OVER_A_DAY_AGO), true,
				_NOW));
	}

	/**
	 * The point of the stall notification is an instance that has collected
	 * nothing. Once something has been recorded, a later registry reading has
	 * nothing to warn about.
	 */
	@Test
	public void notStalledOnceSomethingHasBeenCollected() {
		assertFalse(
			CollectionReadiness.isStalled(
				_cycleStartedOn(_TODAY.minusDays(1)), false, _NOW));
	}

	@Test
	public void notStalledWhileNoCycleIsOpen() {
		assertFalse(
			CollectionReadiness.isStalled(CollectionCycle.closed(1L), false, _NOW));
	}

	/**
	 * TO-112: the grace period this guards against is exactly the daily job
	 * landing between enabling and a restart the administrator was already
	 * about to do.
	 */
	@Test
	public void notStalledWithinTheGracePeriod() {
		assertFalse(
			CollectionReadiness.isStalled(
				CollectionCycle.open(1L, 0, _NOW.minus(Duration.ofHours(23))),
				false, _NOW));
	}

	@Test
	public void stalledExactlyAtTheGracePeriodBoundary() {
		assertTrue(
			CollectionReadiness.isStalled(
				CollectionCycle.open(1L, 0, _NOW.minus(Duration.ofHours(24))),
				false, _NOW));
	}

	/**
	 * A cycle opened before TO-112 added this field has no recorded open
	 * instant. Treating that as "grace period not over" is the safe
	 * direction: it costs a delayed notification rather than a false one, and
	 * {@code CollectionCycleStatusImpl} backfills the field on the next
	 * reconciliation.
	 */
	@Test
	public void notStalledWithNoRecordedOpenInstant() {
		assertFalse(
			CollectionReadiness.isStalled(
				new CollectionCycle(
					1L, true, 0, null, null, null, null, null, false),
				false, _NOW));
	}

	private CollectionCycle _cycleStartedOn(LocalDate localDate) {
		CollectionCycle collectionCycle = CollectionCycle.open(1L, 0, _OPENED);

		return collectionCycle.withCollectionStartDate(localDate);
	}

	private static final Instant _NOW = Instant.parse("2026-06-01T03:30:00Z");

	private static final Instant _OPENED = _NOW.minus(Duration.ofDays(60));

	private static final Instant _OPENED_OVER_A_DAY_AGO = _NOW.minus(
		Duration.ofHours(25));

	private static final LocalDate _TODAY = LocalDate.parse("2026-06-01");

}
