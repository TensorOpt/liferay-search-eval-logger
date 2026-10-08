/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The locale detection rule (TO-115) matches the installation's own language
 * ids rather than a hand-written pattern, specifically so that it does not
 * misparse a field name that merely looks locale-shaped. Each case below is
 * one way a regex-based rule has been seen to get this wrong.
 */
public class LocalizedFieldNameTest {

	@Test
	public void aDdmFieldNameSplitsOnlyItsOwnLocaleSuffix() {
		LocalizedFieldName localizedFieldName = LocalizedFieldName.parse(
			"ddm__text__35202__content_en_US", _LANGUAGE_IDS);

		assertEquals(
			"ddm__text__35202__content", localizedFieldName.getFieldName());
		assertEquals("en_US", localizedFieldName.getLocale());
	}

	@Test
	public void aThreePartLanguageIdIsMatchedWhole() {
		LocalizedFieldName localizedFieldName = LocalizedFieldName.parse(
			"title_ca_ES_VALENCIA", _LANGUAGE_IDS);

		assertEquals("title", localizedFieldName.getFieldName());
		assertEquals("ca_ES_VALENCIA", localizedFieldName.getLocale());
	}

	@Test
	public void aFieldThatIsOnlyALocaleIdStaysUnderDefault() {
		LocalizedFieldName localizedFieldName = LocalizedFieldName.parse(
			"en_US", _LANGUAGE_IDS);

		assertEquals("en_US", localizedFieldName.getFieldName());
		assertEquals(LocalizedFieldName.DEFAULT_LOCALE,
			localizedFieldName.getLocale());
	}

	/**
	 * "xx_YY" looks exactly as locale-shaped as "en_US" to a hand-written
	 * pattern, but it is not one of the installation's locales, so it must
	 * not be split off.
	 */
	@Test
	public void aFieldThatOnlyLooksLocaleLikeStaysUnderDefault() {
		LocalizedFieldName localizedFieldName = LocalizedFieldName.parse(
			"content_xx_YY", _LANGUAGE_IDS);

		assertEquals("content_xx_YY", localizedFieldName.getFieldName());
		assertEquals(LocalizedFieldName.DEFAULT_LOCALE,
			localizedFieldName.getLocale());
	}

	@Test
	public void aPlainFieldWithNoSuffixStaysUnderDefault() {
		LocalizedFieldName localizedFieldName = LocalizedFieldName.parse(
			"assetTagNames", _LANGUAGE_IDS);

		assertEquals("assetTagNames", localizedFieldName.getFieldName());
		assertEquals(LocalizedFieldName.DEFAULT_LOCALE,
			localizedFieldName.getLocale());
	}

	private static final Set<String> _LANGUAGE_IDS = Set.of(
		"en_US", "sv_SE", "fi_FI", "ca_ES", "ca_ES_VALENCIA");

}
