/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The enums are parsed from values read out of a database column, so parsing
 * has to survive anything that column can hold. DESIGN.md 3.2 makes the
 * defensive-read rule explicit: absence of a usable value is a finding, never
 * an exception.
 */
public class SourceTypeTest {

	@Test
	public void namesAreTheStoredForm() {
		assertEquals("WIDGET", SourceType.WIDGET.name());
		assertEquals("HEADLESS", SourceType.HEADLESS.name());
		assertEquals("OTHER", SourceType.OTHER.name());
		assertEquals("UNKNOWN", SourceType.UNKNOWN.name());
		assertEquals(4, SourceType.values().length);
	}

}
