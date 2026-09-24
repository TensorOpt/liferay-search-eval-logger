/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.capture;

import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.search.searcher.SearchRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The asset types a search is constrained to, read the same way by the
 * admission filter that excludes on them and by the capture that records them.
 */
public final class EntryClassNames {

	/**
	 * The request's own list when it has one, since that is what the search
	 * actually ran with; otherwise the legacy search context's. Empty when
	 * neither constrains the search.
	 */
	public static List<String> get(
		SearchRequest searchRequest, SearchContext searchContext) {

		List<String> entryClassNames = searchRequest.getEntryClassNames();

		if ((entryClassNames != null) && !entryClassNames.isEmpty()) {
			return entryClassNames;
		}

		String[] contextEntryClassNames = searchContext.getEntryClassNames();

		if (contextEntryClassNames == null) {
			return Collections.emptyList();
		}

		return Arrays.asList(contextEntryClassNames);
	}

	private EntryClassNames() {
	}

}
