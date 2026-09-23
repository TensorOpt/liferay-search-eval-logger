/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.cycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tensoropt.sel.api.CollectionCycle;

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
				CollectionCycle.open(1L, 0), _TODAY, 30, 100000L, 500),
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
	public void stalledWhenEnabledCollectingNothingAndBypassed() {
		assertTrue(
			CollectionReadiness.isStalled(CollectionCycle.open(1L, 0), false));
	}

	@Test
	public void notStalledWhileInterceptionIsWorking() {
		assertFalse(
			CollectionReadiness.isStalled(CollectionCycle.open(1L, 0), true));
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
				_cycleStartedOn(_TODAY.minusDays(1)), false));
	}

	@Test
	public void notStalledWhileNoCycleIsOpen() {
		assertFalse(
			CollectionReadiness.isStalled(CollectionCycle.closed(1L), false));
	}

	private CollectionCycle _cycleStartedOn(LocalDate localDate) {
		CollectionCycle collectionCycle = CollectionCycle.open(1L, 0);

		return collectionCycle.withCollectionStartDate(localDate);
	}

	private static final LocalDate _TODAY = LocalDate.parse("2026-06-01");

}
