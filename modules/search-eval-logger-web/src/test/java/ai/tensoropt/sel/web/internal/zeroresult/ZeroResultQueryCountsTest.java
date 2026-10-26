/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.zeroresult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * DESIGN.md 6.3 (D11): the 10,000-entry cap on distinct normalized
 * zero-result queries {@link ZeroResultPanelLoader} keeps in memory.
 */
public class ZeroResultQueryCountsTest {

	@Test
	public void belowTheCapEveryDistinctKeyIsCountedAndNothingIsApproximate() {
		ZeroResultQueryCounts zeroResultQueryCounts = new ZeroResultQueryCounts();

		zeroResultQueryCounts.add("widget");
		zeroResultQueryCounts.add("widget");
		zeroResultQueryCounts.add("gadget");

		assertEquals(
			2L, zeroResultQueryCounts.getCounts().get("widget"));
		assertEquals(
			1L, zeroResultQueryCounts.getCounts().get("gadget"));
		assertFalse(zeroResultQueryCounts.isDistinctQueryCapHit());
	}

	@Test
	public void atTheCapAnExistingKeyStillIncrements() {
		ZeroResultQueryCounts zeroResultQueryCounts = _atTheCap();

		zeroResultQueryCounts.add("query-0");

		assertEquals(
			2L, zeroResultQueryCounts.getCounts().get("query-0"));
		assertEquals(
			ZeroResultQueryCounts.MAX_DISTINCT_QUERIES,
			zeroResultQueryCounts.getCounts().size());
		assertFalse(
			zeroResultQueryCounts.isDistinctQueryCapHit(),
			"Counting an already-present key must not itself set the cap flag");
	}

	@Test
	public void atTheCapANewKeyIsDroppedAndTheFlagIsSet() {
		ZeroResultQueryCounts zeroResultQueryCounts = _atTheCap();

		zeroResultQueryCounts.add("one query too many");

		assertEquals(
			ZeroResultQueryCounts.MAX_DISTINCT_QUERIES,
			zeroResultQueryCounts.getCounts().size(),
			"A new key past the cap must not grow the map");
		assertFalse(
			zeroResultQueryCounts.getCounts().containsKey(
				"one query too many"));
		assertTrue(zeroResultQueryCounts.isDistinctQueryCapHit());
	}

	/**
	 * Exactly {@link ZeroResultQueryCounts#MAX_DISTINCT_QUERIES} distinct
	 * keys, each counted once, with the flag still false: reaching the cap
	 * is not itself hitting it, only a new key past it is.
	 */
	private ZeroResultQueryCounts _atTheCap() {
		ZeroResultQueryCounts zeroResultQueryCounts = new ZeroResultQueryCounts();

		for (int i = 0; i < ZeroResultQueryCounts.MAX_DISTINCT_QUERIES; i++) {
			zeroResultQueryCounts.add("query-" + i);
		}

		assertEquals(
			ZeroResultQueryCounts.MAX_DISTINCT_QUERIES,
			zeroResultQueryCounts.getCounts().size());
		assertFalse(zeroResultQueryCounts.isDistinctQueryCapHit());

		return zeroResultQueryCounts;
	}

}
