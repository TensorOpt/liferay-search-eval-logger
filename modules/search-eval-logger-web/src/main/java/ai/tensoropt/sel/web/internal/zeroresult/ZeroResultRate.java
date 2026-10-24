/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.zeroresult;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One-decimal-place percentages for the zero-result panel (DESIGN.md 6).
 *
 * <p>
 * Rounded half up: a rate that lands exactly on a half of the last displayed
 * digit, such as 25 of 2000 (1.25%), displays as 1.3, not the 1.2 that
 * round-half-even (Java's default <code>double</code> formatting, and
 * Python's built-in <code>round()</code>) would give. This is a display-only
 * choice; nothing persisted or exported is rounded, and the e2e check that
 * reproduces the acceptance jq recipes in Python rounds the same way so the
 * two sides cannot disagree at the boundary.
 * </p>
 */
final class ZeroResultRate {

	/**
	 * <code>"0.0"</code> for a zero denominator (an empty log, or an
	 * audience with no first-page searches yet), never a division by zero.
	 */
	static String formatPercent(long numerator, long denominator) {
		if (denominator == 0) {
			return "0.0";
		}

		BigDecimal percent = BigDecimal.valueOf(
			numerator
		).multiply(
			_ONE_HUNDRED
		).divide(
			BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP
		);

		return percent.toPlainString();
	}

	private static final BigDecimal _ONE_HUNDRED = BigDecimal.valueOf(100);

	private ZeroResultRate() {
		throw new AssertionError();
	}

}
