/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.funnel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * DESIGN.md 10 is a short section that is almost entirely constraints, and
 * every one of them is a rule about when a link is <em>not</em> shown or what
 * it does <em>not</em> carry. Those are the assertions here.
 */
public class EvaluationServiceLinksTest {

	@Test
	public void bothLinksAreHiddenWhenTheSettingIsOff() {
		assertFalse(
			EvaluationServiceLinks.isCollectionStartLinkVisible(
				false, true, _COLLECTION_START_DATE));
		assertFalse(
			EvaluationServiceLinks.isExportCompleteLinkVisible(false, true));
	}

	/**
	 * 10.1: shown only once a collection start date exists, which means an
	 * event has actually been persisted.
	 */
	@Test
	public void theCollectionStartLinkNeedsACollectionStartDate() {
		assertFalse(
			EvaluationServiceLinks.isCollectionStartLinkVisible(
				true, true, null));
		assertTrue(
			EvaluationServiceLinks.isCollectionStartLinkVisible(
				true, true, _COLLECTION_START_DATE));
	}

	/**
	 * 10.1: hidden while the bypass warning of 3.1 is active. Offering it
	 * there would schedule a reminder against a dataset that will never
	 * exist.
	 */
	@Test
	public void theCollectionStartLinkIsHiddenWhileInterceptionIsBypassed() {
		assertFalse(
			EvaluationServiceLinks.isCollectionStartLinkVisible(
				true, false, _COLLECTION_START_DATE));
	}

	@Test
	public void theExportCompleteLinkNeedsASuccessfulExport() {
		assertFalse(
			EvaluationServiceLinks.isExportCompleteLinkVisible(true, false));
		assertTrue(
			EvaluationServiceLinks.isExportCompleteLinkVisible(true, true));
	}

	/**
	 * 10.1 lists the parameters exhaustively: the collection start date at day
	 * granularity and the UTM tags. Nothing else may appear, so the whole
	 * address is asserted rather than its parts.
	 */
	@Test
	public void theCollectionStartURLCarriesTheDateAndTheUTMTagsOnly() {
		assertEquals(
			"{{SIGNUP_URL}}?started=2026-03-01&utm_source=liferay-plugin" +
				"&utm_medium=admin-screen",
			EvaluationServiceLinks.getCollectionStartURL(
				_COLLECTION_START_DATE));
	}

	/**
	 * 10.3: UTM tags only. No row counts, coverage figures or date range: the
	 * administrator already has the dataset, and nothing about it goes ahead
	 * of them.
	 */
	@Test
	public void theExportCompleteURLCarriesTheUTMTagsOnly() {
		assertEquals(
			"{{BOOKING_URL}}?utm_source=liferay-plugin" +
				"&utm_medium=export-complete",
			EvaluationServiceLinks.getExportCompleteURL());
	}

	/**
	 * The placeholders are deliberate (10.4). A real address appearing here
	 * would mean a release was cut by editing something other than this class,
	 * which is the arrangement that section exists to prevent.
	 */
	@Test
	public void bothURLsAreStillPlaceholders() {
		assertEquals("{{SIGNUP_URL}}", EvaluationServiceLinks.SIGNUP_URL);
		assertEquals("{{BOOKING_URL}}", EvaluationServiceLinks.BOOKING_URL);
	}

	private static final LocalDate _COLLECTION_START_DATE = LocalDate.parse(
		"2026-03-01");

}
