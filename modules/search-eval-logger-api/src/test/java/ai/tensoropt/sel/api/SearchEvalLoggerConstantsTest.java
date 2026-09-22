/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * These values are duplicated as strings in the configuration interface's
 * <code>&#64;Meta.AD</code> defaults, which cannot reference a constant. This
 * test is the thing that makes that duplication safe: it pins each value to
 * the table in DESIGN.md section 5, so a drift shows up here rather than as an
 * install collecting the wrong thing.
 */
public class SearchEvalLoggerConstantsTest {

	@Test
	public void collectionIsOffOnInstall() {
		assertFalse(
			SearchEvalLoggerConstants.DEFAULT_ENABLED,
			"Installing must never silently begin collecting (section 5)");
	}

	@Test
	public void defaultsMatchTheDesignDocument() {
		assertEquals(100, SearchEvalLoggerConstants.DEFAULT_CAPTURE_DEPTH);
		assertEquals(90, SearchEvalLoggerConstants.DEFAULT_RETENTION_DAYS);
		assertEquals(2000, SearchEvalLoggerConstants.DEFAULT_QUERY_TEXT_CAP);
		assertEquals(
			7, SearchEvalLoggerConstants.DEFAULT_COHORT_SALT_ROTATION_DAYS);
		assertEquals(
			1.0, SearchEvalLoggerConstants.DEFAULT_SAMPLING_RATE, 0.0);
		assertTrue(
			SearchEvalLoggerConstants.DEFAULT_EXCLUDE_SUGGESTION_TRAFFIC);
		assertFalse(
			SearchEvalLoggerConstants.DEFAULT_ADMIT_FACET_ONLY_SEARCHES);
		assertFalse(
			SearchEvalLoggerConstants.DEFAULT_REQUIRE_WEB_REQUEST_CONTEXT);
	}

	@Test
	public void capturedFieldWhitelistIsTitleAndSnippetOnly() {
		assertEquals(
			List.of("title", "snippet"),
			SearchEvalLoggerConstants.DEFAULT_CAPTURED_FIELD_NAMES);
		assertEquals("title", SearchEvalLoggerConstants.FIELD_TITLE);
		assertEquals("snippet", SearchEvalLoggerConstants.FIELD_SNIPPET);
	}

	/**
	 * The whitelist is the difference between an export an administrator
	 * approves after one read and one that goes to legal (section 5), so it
	 * must not be mutable through the shared constant.
	 */
	@Test
	public void capturedFieldWhitelistCannotBeMutatedByCallers() {
		assertThrows(
			UnsupportedOperationException.class,
			() -> SearchEvalLoggerConstants.DEFAULT_CAPTURED_FIELD_NAMES.add(
				"content"));
	}

	/**
	 * The producer and the consumer are wired by this string alone, in
	 * different bundles. A typo would silently route events nowhere.
	 */
	@Test
	public void destinationNameMatchesTheMessageListenerRegistration() {
		assertEquals(
			"tensoropt/search-eval-log",
			SearchEvalLoggerConstants.DESTINATION_NAME);
	}

}
