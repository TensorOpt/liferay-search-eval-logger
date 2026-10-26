/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.zeroresult;

import java.util.HashMap;
import java.util.Map;

/**
 * The normalized-query-to-count map {@link ZeroResultPanelLoader} builds
 * while streaming zero-result first-page rows (DESIGN.md 6.3), bounded to
 * at most {@link #MAX_DISTINCT_QUERIES} distinct normalized keys.
 *
 * <p>
 * Memory here is otherwise bounded only by the number of distinct
 * normalized queries a company's whole retention window has ever produced,
 * which has no ceiling of its own: a log carrying an unbounded variety of
 * one-off zero-result queries (a vocabulary attack, or simply a very large,
 * very diverse site) could otherwise grow this map without limit on every
 * computation. An existing key is still counted once the cap is reached,
 * since that costs nothing new to track; a key not yet seen is dropped
 * instead of admitted, which is what keeps the map's size bounded rather
 * than merely its growth rate.
 * </p>
 *
 * <p>
 * <b>Why dropped rather than merged into a lossy top-20, or evicted LRU.</b>
 * Either would still give an exact count for whichever queries survive, at
 * the cost of a more elaborate algorithm (a count-min sketch, or a decay
 * scheme) this one screen does not need: once the cap is hit the top-20
 * table is already labelled approximate (see {@link
 * #isDistinctQueryCapHit()}), and anyone who needs an exact count over the
 * whole log has the export instead, streamed without any cap (DESIGN.md
 * 6.1). Simplicity here is a deliberate trade for that known inexactness,
 * not an oversight.
 * </p>
 */
final class ZeroResultQueryCounts {

	/**
	 * Chosen generously above any volume this screen has been measured
	 * against (DESIGN.md 6.3's own measurements are two orders of magnitude
	 * below it), so the cap is a backstop against an unbounded or adversarial
	 * log, not something a normal installation should ever reach.
	 */
	static final int MAX_DISTINCT_QUERIES = 10_000;

	/**
	 * Counts one more occurrence of an already normalized query. A key
	 * already present is counted regardless of the cap; a key not yet
	 * present is admitted only while the map is under the cap, and dropped,
	 * with {@link #isDistinctQueryCapHit()} set, once it is not.
	 */
	void add(String normalizedQuery) {
		if (_counts.containsKey(normalizedQuery)) {
			_counts.merge(normalizedQuery, 1L, Long::sum);

			return;
		}

		if (_counts.size() >= MAX_DISTINCT_QUERIES) {
			_distinctQueryCapHit = true;

			return;
		}

		_counts.put(normalizedQuery, 1L);
	}

	Map<String, Long> getCounts() {
		return _counts;
	}

	/**
	 * Whether a row with a new, not-yet-seen normalized key was dropped
	 * because the cap was already reached. The audience rate figures are
	 * unaffected either way: they come from a separate SQL aggregate, not
	 * from this map.
	 */
	boolean isDistinctQueryCapHit() {
		return _distinctQueryCapHit;
	}

	private final Map<String, Long> _counts = new HashMap<>();
	private boolean _distinctQueryCapHit;

}
