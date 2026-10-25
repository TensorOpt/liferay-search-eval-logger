/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.context;

import ai.tensoropt.sel.api.SourceType;

/**
 * What could be learned about where a search came from, read once per search
 * and then passed around as a value so no code downstream touches a
 * ThreadLocal twice.
 */
public class SearchRequestOrigin {

	public static final SearchRequestOrigin UNKNOWN = new SearchRequestOrigin(
		SourceType.UNKNOWN, false, false, false);

	public SearchRequestOrigin(
		SourceType sourceType, boolean suggestion, boolean administrative,
		boolean webRequestPresent) {

		_sourceType = sourceType;
		_suggestion = suggestion;
		_administrative = administrative;
		_webRequestPresent = webRequestPresent;
	}

	public SourceType getSourceType() {
		return _sourceType;
	}

	/**
	 * Whether the search came from the Search Experiences administration API,
	 * whose searches are the Blueprint editor's previews (TO-141).
	 */
	public boolean isAdministrative() {
		return _administrative;
	}

	public boolean isSuggestion() {
		return _suggestion;
	}

	public boolean isWebRequestPresent() {
		return _webRequestPresent;
	}

	private final boolean _administrative;
	private final SourceType _sourceType;
	private final boolean _suggestion;
	private final boolean _webRequestPresent;

}
