/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.zeroresult;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tensoropt.sel.api.AudienceType;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * {@link ZeroResultPanel#build} assembles the two small aggregates
 * {@code SearchEventLocalService} computes in SQL into what the JSP of
 * DESIGN.md 6 renders. The merge, sort and top-20 cut themselves are
 * {@link ZeroResultQueryNormalizerTest}'s job; this covers the audience
 * mapping, the empty-log and no-zero-result paths, and that overall is a
 * sum across every audience row regardless of which values are present.
 *
 * <p>
 * Asserted through the {@code get*MessageArguments()} getters, the only
 * public surface for a line's numbers: {@code ZeroResultPanel} has no
 * plain-number getters of its own, since the JSP never needs one on its
 * own, only the triple a rendered line needs.
 * </p>
 */
public class ZeroResultPanelTest {

	@Test
	public void anEmptyLogShowsNoSearchesLoggedAndNoTable() {
		ZeroResultPanel zeroResultPanel = ZeroResultPanel.build(
			Map.of(), Map.of());

		assertTrue(zeroResultPanel.isNoSearchesLogged());
		assertTrue(zeroResultPanel.getTopQueries().isEmpty());
		assertArrayEquals(
			new String[] {"0", "0", "0.0"},
			zeroResultPanel.getOverallMessageArguments());
	}

	/**
	 * D11: the two-argument {@code build} overload every other test here
	 * uses, same as {@link ZeroResultPanelLoader} did before the cap, must
	 * still report "not approximate" rather than leaving the flag unset in
	 * some other, surprising way.
	 */
	@Test
	public void theTwoArgumentOverloadIsNeverApproximate() {
		ZeroResultPanel zeroResultPanel = ZeroResultPanel.build(
			Map.of(), Map.of());

		assertFalse(zeroResultPanel.isTopQueriesApproximate());
	}

	/**
	 * D11: the cap is {@link ZeroResultQueryCounts}'s own concern; this only
	 * checks that {@code build} carries the flag it is given through to the
	 * JSP-facing getter unchanged.
	 */
	@Test
	public void theApproximateFlagIsCarriedThrough() {
		ZeroResultPanel zeroResultPanel = ZeroResultPanel.build(
			Map.of(), Map.of(), true);

		assertTrue(zeroResultPanel.isTopQueriesApproximate());
	}

	@Test
	public void noZeroResultSearchesShowsZeroRateButStillRendersTheLines() {
		ZeroResultPanel zeroResultPanel = ZeroResultPanel.build(
			Map.of(
				AudienceType.GUEST.name(), new long[] {10, 0},
				AudienceType.AUTHENTICATED.name(), new long[] {20, 0}),
			Map.of());

		assertFalse(zeroResultPanel.isNoSearchesLogged());
		assertTrue(zeroResultPanel.getTopQueries().isEmpty());
		assertArrayEquals(
			new String[] {"0", "30", "0.0"},
			zeroResultPanel.getOverallMessageArguments());
	}

	@Test
	public void overallIsTheSumAcrossEveryAudienceRow() {
		ZeroResultPanel zeroResultPanel = ZeroResultPanel.build(
			Map.of(
				AudienceType.GUEST.name(), new long[] {40, 10},
				AudienceType.AUTHENTICATED.name(), new long[] {60, 5}),
			Map.of("no results here", 15L));

		assertArrayEquals(
			new String[] {"15", "100", "15.0"},
			zeroResultPanel.getOverallMessageArguments());
	}

	/**
	 * GUEST and AUTHENTICATED map to "Guests" and "Signed-in users"
	 * respectively; the two values {@code AudienceType} actually has.
	 */
	@Test
	public void guestAndAuthenticatedMapToTheirOwnLines() {
		ZeroResultPanel zeroResultPanel = ZeroResultPanel.build(
			Map.of(
				AudienceType.GUEST.name(), new long[] {40, 10},
				AudienceType.AUTHENTICATED.name(), new long[] {60, 5}),
			Map.of());

		assertArrayEquals(
			new String[] {"10", "40", "25.0"},
			zeroResultPanel.getGuestMessageArguments());
		assertArrayEquals(
			new String[] {"5", "60", "8.3"},
			zeroResultPanel.getSignedInMessageArguments());
	}

	/**
	 * An audience type the panel does not know a label for still counts
	 * toward the overall total (nothing is silently dropped from the
	 * denominator), but contributes no separate breakdown line, since the
	 * copy in DESIGN.md 6 only has room for the two values
	 * {@code AudienceType} defines today.
	 */
	@Test
	public void anUnknownAudienceTypeStillCountsTowardOverallOnly() {
		ZeroResultPanel zeroResultPanel = ZeroResultPanel.build(
			Map.of(
				AudienceType.GUEST.name(), new long[] {10, 1},
				"SOMETHING_ELSE", new long[] {5, 2}),
			Map.of());

		assertArrayEquals(
			new String[] {"3", "15", "20.0"},
			zeroResultPanel.getOverallMessageArguments());
		assertArrayEquals(
			new String[] {"1", "10", "10.0"},
			zeroResultPanel.getGuestMessageArguments());
		assertArrayEquals(
			new String[] {"0", "0", "0.0"},
			zeroResultPanel.getSignedInMessageArguments());
	}

	@Test
	public void topQueriesAreOnlyBuiltWhenThereIsAtLeastOneZeroResultSearch() {
		ZeroResultPanel zeroResultPanel = ZeroResultPanel.build(
			Map.of(AudienceType.GUEST.name(), new long[] {10, 1}),
			Map.of("widget", 1L));

		assertFalse(zeroResultPanel.getTopQueries().isEmpty());
		assertEquals(1, zeroResultPanel.getTopQueries().size());
		assertEquals(
			"widget", zeroResultPanel.getTopQueries().get(0).getQuery());
	}

	@Test
	public void messageArgumentsAreARealArrayOfThreeFormattedStrings() {
		ZeroResultPanel zeroResultPanel = ZeroResultPanel.build(
			Map.of(AudienceType.GUEST.name(), new long[] {4, 1}), Map.of());

		Object[] messageArguments = zeroResultPanel.getGuestMessageArguments();

		assertTrue(messageArguments.getClass().isArray());
		assertArrayEquals(
			new String[] {"1", "4", "25.0"}, messageArguments);
	}

}
