/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.zeroresult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * DESIGN.md 6.3 (D11): the 10,000-entry cap on distinct normalized
 * zero-result queries {@link ZeroResultPanelLoader} keeps in memory.
 */
public class ZeroResultQueryCountsTest {

	/**
	 * D11 (TO-144): every other test here derives its row counts from
	 * {@link ZeroResultQueryCounts#MAX_DISTINCT_QUERIES} itself, which
	 * passes regardless of what the constant is actually set to. Pinning
	 * the value DESIGN.md 6.3 documents (10,000) is what makes raising it
	 * a change this suite notices.
	 */
	@Test
	public void theCapIsTenThousand() {
		assertEquals(10_000, ZeroResultQueryCounts.MAX_DISTINCT_QUERIES);
	}

	/**
	 * D11 (TO-144): the "zero-result-approximate-note" language key states
	 * the cap as plain text, since the JSP renders it through
	 * {@code liferay-ui:message} with no arguments, not interpolated from
	 * the constant (Language.properties is a resource bundle, not Java).
	 * This is the DRY alternative the finding allows: a constant raised
	 * without updating the text fails here instead of only going stale
	 * silently on the rendered screen.
	 */
	@Test
	public void theApproximateNoteNamesTheCurrentCap() throws IOException {
		Properties properties = new Properties();

		try (InputStream inputStream =
				ZeroResultQueryCountsTest.class.getClassLoader(
				).getResourceAsStream("content/Language.properties")) {

			assertNotNull(
				inputStream,
				"content/Language.properties was not found on the test " +
					"classpath");

			properties.load(inputStream);
		}

		String note = properties.getProperty("zero-result-approximate-note");

		assertNotNull(note, "zero-result-approximate-note is not defined");

		String formattedCap = String.format(
			"%,d", ZeroResultQueryCounts.MAX_DISTINCT_QUERIES);

		assertTrue(
			note.contains(formattedCap),
			"zero-result-approximate-note does not mention the current " +
				"cap (" + formattedCap + "): " + note);
	}

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
