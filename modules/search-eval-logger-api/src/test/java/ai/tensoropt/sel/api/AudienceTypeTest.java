/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

public class AudienceTypeTest {

	@Test
	public void parseReturnsTheMatchingConstant() {
		for (AudienceType audienceType : AudienceType.values()) {
			assertSame(
				audienceType,
				AudienceType.parse(audienceType.name(), AudienceType.GUEST));
		}
	}

	@Test
	public void parseFallsBackRatherThanThrowing() {
		assertSame(
			AudienceType.GUEST, AudienceType.parse(null, AudienceType.GUEST));
		assertSame(
			AudienceType.GUEST, AudienceType.parse("  ", AudienceType.GUEST));
		assertSame(
			AudienceType.GUEST,
			AudienceType.parse("ANONYMOUS", AudienceType.GUEST));
	}

	/**
	 * Only two values exist by design (4.3): a finer split would depend on
	 * effective roles, which is what that section rules out.
	 */
	@Test
	public void onlyGuestAndAuthenticatedExist() {
		assertEquals(2, AudienceType.values().length);
		assertEquals("GUEST", AudienceType.GUEST.name());
		assertEquals("AUTHENTICATED", AudienceType.AUTHENTICATED.name());
	}

}
