/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

/**
 * Origin of a captured search, as persisted in
 * <code>SEL_SearchEvent.sourceType</code>.
 *
 * <p>
 * Derived defensively from the current HTTP request: no request on the thread
 * yields {@link #UNKNOWN}, a URI in the headless namespace yields
 * {@link #HEADLESS}, a portlet or <code>ThemeDisplay</code> context yields
 * {@link #WIDGET}, and anything else yields {@link #OTHER}. See DESIGN.md 3.2.
 * </p>
 *
 * <p>
 * This is descriptive metadata only and never an input to the admission filter,
 * so a misclassification degrades dataset quality without changing what is
 * captured. {@link #UNKNOWN} is recorded rather than guessed.
 * </p>
 *
 * <p>
 * {@link #name()} is the persisted string form.
 * </p>
 */
public enum SourceType {

	HEADLESS, OTHER, UNKNOWN, WIDGET;

	/**
	 * Resolves a persisted value, falling back instead of throwing so that a
	 * value written by a newer version of this plugin cannot break an export or
	 * a purge pass.
	 */
	public static SourceType parse(String value, SourceType defaultSourceType) {
		for (SourceType sourceType : values()) {
			if (sourceType.name().equals(value)) {
				return sourceType;
			}
		}

		return defaultSourceType;
	}

}
