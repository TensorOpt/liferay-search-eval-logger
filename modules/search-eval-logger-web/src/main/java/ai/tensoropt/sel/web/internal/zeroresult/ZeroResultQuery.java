/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.zeroresult;

/**
 * One row of the zero-result panel's query table (DESIGN.md 6): a normalized
 * query and how many zero-result first-page searches grouped into it.
 *
 * <p>
 * The query here is already the normalized form, lower-ASCII-cased and
 * trimmed, matching what the acceptance check's jq recipe displays for a
 * group (<code>.[0]</code> of the group, itself already normalized). See
 * {@link ZeroResultQueryNormalizer}.
 * </p>
 *
 * <p>
 * Plain getters, not a record: the JSP reads this through EL, which on this
 * platform's Tomcat resolves JavaBeans properties only (see
 * <code>ai.tensoropt.sel.web.internal.portlet.ExportRow</code>).
 * </p>
 */
public class ZeroResultQuery {

	public ZeroResultQuery(String query, long searchCount) {
		_query = query;
		_searchCount = searchCount;
	}

	public String getQuery() {
		return _query;
	}

	public long getSearchCount() {
		return _searchCount;
	}

	private final String _query;
	private final long _searchCount;

}
