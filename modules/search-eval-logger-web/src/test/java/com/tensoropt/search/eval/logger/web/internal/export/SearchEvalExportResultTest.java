/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.web.internal.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Coverage rates are what an evaluator reads to decide whether a judging pass
 * is feasible before starting one (DESIGN.md 6.2), so a wrong denominator is
 * not a cosmetic bug: it would misrepresent how much of the dataset is
 * usable.
 */
public class SearchEvalExportResultTest {

	@BeforeEach
	public void setUp() {
		_searchEvalExportResult = new SearchEvalExportResult();
	}

	@Test
	public void countsStartAtZero() {
		assertEquals(0, _searchEvalExportResult.getEventCount());
		assertEquals(0, _searchEvalExportResult.getHitCount());
		assertTrue(_searchEvalExportResult.getFieldCoverageRates().isEmpty());
	}

	@Test
	public void coverageIsTheFractionOfHitsCarryingTheField() {
		_searchEvalExportResult.registerField("title");
		_searchEvalExportResult.registerField("snippet");

		for (int i = 0; i < 4; i++) {
			_searchEvalExportResult.incrementHitCount();
			_searchEvalExportResult.incrementFieldCount("title");
		}

		_searchEvalExportResult.incrementFieldCount("snippet");

		Map<String, Double> rates =
			_searchEvalExportResult.getFieldCoverageRates();

		assertEquals(1.0, rates.get("title"), 0.0001);
		assertEquals(0.25, rates.get("snippet"), 0.0001);
	}

	/**
	 * A whitelisted field that nothing carried must report 0%, not vanish.
	 * An absent key reads as "not measured", which is a different and much
	 * more encouraging statement than "measured, and nothing had it".
	 */
	@Test
	public void registeredFieldWithNoOccurrencesReportsZeroRatherThanAbsent() {
		_searchEvalExportResult.registerField("snippet");

		_searchEvalExportResult.incrementHitCount();
		_searchEvalExportResult.incrementHitCount();

		Map<String, Double> rates =
			_searchEvalExportResult.getFieldCoverageRates();

		assertTrue(rates.containsKey("snippet"));
		assertEquals(0.0, rates.get("snippet"), 0.0001);
	}

	/**
	 * An export whose range matched nothing reports no coverage at all, not a
	 * row of zeroes. Zero would read as "measured, and the field was never
	 * present", which is a claim about the installation's search UI that an
	 * empty export has not earned.
	 */
	@Test
	public void emptyExportReportsNoCoverageRatherThanZeroes() {
		_searchEvalExportResult.registerField("title");

		assertTrue(
			_searchEvalExportResult.getFieldCoverageRates().isEmpty(),
			"No hits means no denominator, so no rate can be stated");
	}

	@Test
	public void countersAreIndependent() {
		_searchEvalExportResult.incrementEventCount();
		_searchEvalExportResult.incrementHitCount();
		_searchEvalExportResult.incrementHitCount();

		assertEquals(1, _searchEvalExportResult.getEventCount());
		assertEquals(2, _searchEvalExportResult.getHitCount());
	}

	private SearchEvalExportResult _searchEvalExportResult;

}
