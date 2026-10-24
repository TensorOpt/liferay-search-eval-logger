/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.zeroresult;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ZeroResultRateTest {

	@Test
	public void zeroDenominatorIsZeroPointZeroPercentNotADivideByZero() {
		assertEquals("0.0", ZeroResultRate.formatPercent(0, 0));

		// A zero numerator with a non-zero denominator is a legitimate rate,
		// distinguishable in the acceptance checks from the empty-log case
		// above, which this exercises explicitly.

		assertEquals("0.0", ZeroResultRate.formatPercent(0, 100));
	}

	@Test
	public void roundsHalfUpToOneDecimalPlace() {

		// 25 of 2000 is exactly 1.25%, which rounds to 1.3 under half-up,
		// not the 1.2 that half-even (Java's default double formatting,
		// and Python's built-in round()) would give. The e2e check that
		// replays the acceptance jq recipes in Python rounds the same way.

		assertEquals("1.3", ZeroResultRate.formatPercent(25, 2000));
	}

	@Test
	public void allZeroResultIsOneHundredPercent() {
		assertEquals("100.0", ZeroResultRate.formatPercent(10, 10));
	}

	@Test
	public void ordinaryRateFormatsToOneDecimalPlace() {
		assertEquals("33.3", ZeroResultRate.formatPercent(1, 3));
		assertEquals("10.0", ZeroResultRate.formatPercent(1, 10));
	}

}
