/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class AudienceTypeTest {

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
