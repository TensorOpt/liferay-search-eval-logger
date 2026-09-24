/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.export;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counts accumulated while the export streams, so the manifest can report them
 * without a second pass over the data.
 *
 * <p>
 * Per-field coverage is the figure an evaluator needs before designing a
 * judging pass: coverage of snippets and auxiliary fields is a property of the
 * installation's search UI, not of this plugin, and it varies (DESIGN.md 4.4).
 * </p>
 */
public class SearchEvalExportResult {

	public long getEventCount() {
		return _eventCount;
	}

	/**
	 * Fraction of exported hits that carried a value for each whitelisted
	 * field, between 0 and 1. An empty export reports no coverage rather than
	 * a misleading zero.
	 */
	public Map<String, Double> getFieldCoverageRates() {
		Map<String, Double> fieldCoverageRates = new LinkedHashMap<>();

		if (_hitCount == 0) {
			return fieldCoverageRates;
		}

		for (Map.Entry<String, Long> entry : _fieldCounts.entrySet()) {
			Long count = entry.getValue();

			fieldCoverageRates.put(
				entry.getKey(), count.doubleValue() / _hitCount);
		}

		return fieldCoverageRates;
	}

	public long getHitCount() {
		return _hitCount;
	}

	public void incrementEventCount() {
		_eventCount++;
	}

	public void incrementFieldCount(String fieldName) {
		_fieldCounts.merge(fieldName, 1L, Long::sum);
	}

	public void incrementHitCount() {
		_hitCount++;
	}

	/**
	 * Registers a field so that a whitelisted field no hit carried is reported
	 * as zero coverage rather than being missing from the manifest. The
	 * difference matters: absent reads as "not asked for", zero reads as
	 * "asked for and never present", and only the second tells an evaluator
	 * that the installation's search UI does not return it.
	 */
	public void registerField(String fieldName) {
		_fieldCounts.putIfAbsent(fieldName, 0L);
	}

	private long _eventCount;
	private final Map<String, Long> _fieldCounts = new LinkedHashMap<>();
	private long _hitCount;

}
