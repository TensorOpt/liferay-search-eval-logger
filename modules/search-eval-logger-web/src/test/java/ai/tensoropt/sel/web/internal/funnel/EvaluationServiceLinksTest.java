/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.funnel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import java.time.LocalDate;

import java.util.Arrays;

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

	/**
	 * TO-112: the signup banner follows the same base rule as the link it
	 * replaces.
	 */
	@Test
	public void theSignupBannerNeedsTheSameConditionsAsTheLinkItReplaces() {
		assertFalse(
			EvaluationServiceLinks.isSignupBannerVisible(
				false, true, _COLLECTION_START_DATE, false));
		assertFalse(
			EvaluationServiceLinks.isSignupBannerVisible(
				true, false, _COLLECTION_START_DATE, false));
		assertFalse(
			EvaluationServiceLinks.isSignupBannerVisible(
				true, true, null, false));
		assertTrue(
			EvaluationServiceLinks.isSignupBannerVisible(
				true, true, _COLLECTION_START_DATE, false));
	}

	/**
	 * TO-112: shown until clicked or dismissed in the current cycle. Both
	 * actions are recorded as the same local flag, so either suppresses the
	 * banner for the rest of the cycle.
	 */
	@Test
	public void theSignupBannerIsHiddenOnceDismissedEvenWhenOtherwiseDue() {
		assertFalse(
			EvaluationServiceLinks.isSignupBannerVisible(
				true, true, _COLLECTION_START_DATE, true));
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
			"https://tensoropt.ai/dxp-sel-signup?started=2026-03-01" +
				"&utm_source=liferay-plugin&utm_medium=admin-screen",
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
			"https://tensoropt.ai/dxp-sel-booking?utm_source=liferay-plugin" +
				"&utm_medium=export-complete",
			EvaluationServiceLinks.getExportCompleteURL());
	}

	/**
	 * TO-113: the released v1.0.0 addresses (10.4). Asserted on scheme, host,
	 * path and the absence of a query or a fragment, rather than on the whole
	 * literal string, so this test does not just restate
	 * {@link #theCollectionStartURLCarriesTheDateAndTheUTMTagsOnly} and
	 * {@link #theExportCompleteURLCarriesTheUTMTagsOnly} above.
	 */
	@Test
	public void bothURLsAreTheReleasedAddresses() {
		for (String url :
				Arrays.asList(
					EvaluationServiceLinks.SIGNUP_URL,
					EvaluationServiceLinks.BOOKING_URL)) {

			URI uri = URI.create(url);

			assertEquals("https", uri.getScheme());
			assertEquals("tensoropt.ai", uri.getHost());
			assertNull(uri.getQuery());
			assertNull(uri.getFragment());
		}

		assertEquals(
			"https://tensoropt.ai/dxp-sel-signup", EvaluationServiceLinks.SIGNUP_URL);
		assertEquals(
			"https://tensoropt.ai/dxp-sel-booking",
			EvaluationServiceLinks.BOOKING_URL);
	}

	private static final LocalDate _COLLECTION_START_DATE = LocalDate.parse(
		"2026-03-01");

}
