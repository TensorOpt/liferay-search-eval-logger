/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.capture;

import java.util.Set;

/**
 * Splits a document or highlight field name into the field it names and the
 * locale it is written in (TO-115), by matching one of the installation's
 * own language ids that the name ends with.
 *
 * <p>
 * A hand-written pattern such as "two letters, underscore, two letters" both
 * over- and under-matches real field names. Liferay field names carry
 * underscores of their own: a DDM structured content field reads
 * <code>ddm__text__35202__content_en_US</code>, where only the trailing
 * <code>_en_US</code> is a locale. And a language id is not always two-part;
 * <code>ca_ES_VALENCIA</code> is one Liferay ships. Matching against the set
 * of language ids the installation actually has configured side-steps both
 * problems: only a real locale id is ever split off the end, whatever its
 * shape.
 * </p>
 *
 * <p>
 * Matching the first candidate found is enough, with no tie-break between
 * several: no Liferay-assigned language id is itself a suffix of another
 * one (a three-part id's variant, such as <code>_VALENCIA</code>, never
 * reproduces the two-part id it extends), so at most one id in the set can
 * ever match a given name at once.
 * </p>
 *
 * <p>
 * A name with no recognized locale suffix &mdash; including a name that is
 * itself nothing but a locale id, which would leave no field name behind if
 * split &mdash; is returned unchanged, under {@link #DEFAULT_LOCALE}.
 * </p>
 */
final class LocalizedFieldName {

	static final String DEFAULT_LOCALE = "_default";

	static LocalizedFieldName parse(String fieldName, Set<String> languageIds) {
		for (String languageId : languageIds) {
			if (_endsWithLocale(fieldName, languageId)) {
				return new LocalizedFieldName(
					fieldName.substring(
						0, (fieldName.length() - languageId.length()) - 1),
					languageId);
			}
		}

		return new LocalizedFieldName(fieldName, DEFAULT_LOCALE);
	}

	/**
	 * True only when stripping the suffix would leave a non-empty field name
	 * behind. A field named exactly after a locale id, with nothing before
	 * it, is not a localized field with an empty name; it is left under
	 * {@link #DEFAULT_LOCALE}, unchanged.
	 */
	private static boolean _endsWithLocale(String fieldName, String languageId) {
		int suffixLength = languageId.length() + 1;

		return (fieldName.length() > suffixLength) &&
			fieldName.endsWith(_UNDERLINE + languageId);
	}

	private LocalizedFieldName(String fieldName, String locale) {
		_fieldName = fieldName;
		_locale = locale;
	}

	String getFieldName() {
		return _fieldName;
	}

	String getLocale() {
		return _locale;
	}

	private static final String _UNDERLINE = "_";

	private final String _fieldName;
	private final String _locale;

}
