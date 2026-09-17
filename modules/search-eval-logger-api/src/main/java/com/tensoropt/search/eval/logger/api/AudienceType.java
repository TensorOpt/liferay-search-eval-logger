/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.api;

/**
 * Coarse evaluation cohort of the user who issued a search, as persisted in
 * <code>SEL_SearchEvent.audienceType</code>.
 *
 * <p>
 * Guests are the only fully comparable population in a captured dataset,
 * because Liferay filters results by the requester's permissions: two
 * authenticated users running the same query can receive different result
 * lists. See DESIGN.md 4.3.
 * </p>
 *
 * <p>
 * {@link #name()} is the persisted string form.
 * </p>
 */
public enum AudienceType {

	AUTHENTICATED, GUEST;

	/**
	 * Resolves a persisted value, falling back instead of throwing so that a
	 * value written by a newer version of this plugin cannot break an export or
	 * a purge pass.
	 */
	public static AudienceType parse(
		String value, AudienceType defaultAudienceType) {

		for (AudienceType audienceType : values()) {
			if (audienceType.name().equals(value)) {
				return audienceType;
			}
		}

		return defaultAudienceType;
	}

}
