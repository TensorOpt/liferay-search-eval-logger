/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * These values wire bundles together by string alone, so a change to one is a
 * silent break rather than a compile error. The configuration defaults are
 * pinned by SearchEvalLoggerConfigurationRegistryTest in the impl module,
 * against the annotations that declare them.
 */
public class SearchEvalLoggerConstantsTest {

	@Test
	public void capturedFieldNamesMatchTheConfigurationValues() {
		assertEquals("title", SearchEvalLoggerConstants.FIELD_TITLE);
		assertEquals("snippet", SearchEvalLoggerConstants.FIELD_SNIPPET);
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
