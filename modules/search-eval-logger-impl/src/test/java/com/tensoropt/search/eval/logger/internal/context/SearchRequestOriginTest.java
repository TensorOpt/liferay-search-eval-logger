/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.internal.context;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tensoropt.search.eval.logger.api.SourceType;

import org.junit.jupiter.api.Test;

public class SearchRequestOriginTest {

	/**
	 * A search reached from a background thread has no request to classify.
	 * DESIGN.md 3.2 requires that to record UNKNOWN rather than a guess.
	 */
	@Test
	public void unknownCarriesNoRequestAndIsNotASuggestion() {
		assertSame(SourceType.UNKNOWN, SearchRequestOrigin.UNKNOWN.getSourceType());
		assertFalse(SearchRequestOrigin.UNKNOWN.isWebRequestPresent());
		assertFalse(SearchRequestOrigin.UNKNOWN.isSuggestion());
	}

	@Test
	public void accessorsReturnWhatWasConstructed() {
		SearchRequestOrigin searchRequestOrigin = new SearchRequestOrigin(
			SourceType.HEADLESS, true, true);

		assertSame(SourceType.HEADLESS, searchRequestOrigin.getSourceType());
		assertTrue(searchRequestOrigin.isSuggestion());
		assertTrue(searchRequestOrigin.isWebRequestPresent());
	}

	/**
	 * Suggestion traffic is only ever classified from a request URI (5.1), so
	 * a suggestion without a request would mean the classification was
	 * guessed.
	 */
	@Test
	public void widgetTrafficIsNeverFlaggedAsSuggestion() {
		SearchRequestOrigin searchRequestOrigin = new SearchRequestOrigin(
			SourceType.WIDGET, false, true);

		assertFalse(searchRequestOrigin.isSuggestion());
	}

}
