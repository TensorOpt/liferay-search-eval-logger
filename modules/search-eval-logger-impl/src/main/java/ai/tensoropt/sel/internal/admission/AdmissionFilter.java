/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.admission;

import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.search.searcher.SearchRequest;

import ai.tensoropt.sel.api.FacetCaptureStatus;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.capture.EntryClassNames;
import ai.tensoropt.sel.internal.capture.FacetCapture;
import ai.tensoropt.sel.internal.capture.FacetExtractor;
import ai.tensoropt.sel.internal.context.SearchRequestOrigin;
import ai.tensoropt.sel.internal.statistics.SearchEvalLoggerStatisticsImpl;
import ai.tensoropt.sel.internal.context.SearchRequestOriginResolver;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Decides whether a search is a user search worth logging (DESIGN.md 3.2).
 *
 * <p>
 * This is an allowlist, not a denylist, and that is the whole point: wrapping
 * <code>Searcher</code> catches Asset Publisher collections, Control Panel
 * listings, workflow lookups and DDM resolution as well as user search. A
 * denylist would leak administrative traffic into the dataset; this admits only
 * what looks like a person typing a query.
 * </p>
 *
 * <p>
 * It runs synchronously on the search thread, so it allocates nothing beyond
 * the checks themselves and returns at the first failing condition.
 * </p>
 */
@Component(service = AdmissionFilter.class)
public class AdmissionFilter {

	/**
	 * Returns the origin of an admitted search, or <code>null</code> when the
	 * search is not admitted.
	 *
	 * <p>
	 * The origin is returned rather than resolved by the caller because the
	 * conditions have to run in the order DESIGN.md 3.2 gives them. Condition 1
	 * is the strongest discriminator and rejects most of what reaches here, so
	 * nothing above it may cost anything: resolving the origin first would make
	 * every internal keyword-free search pay for ThreadLocal reads, URI parsing
	 * and an allocation on its way to being dropped. Resolution therefore
	 * happens inside condition 2, and the caller reuses the result for capture
	 * instead of resolving a second time.
	 * </p>
	 */
	public SearchRequestOrigin admit(
		SearchContext searchContext, SearchRequest searchRequest,
		SearchEvalLoggerConfiguration configuration) {

		if (!_hasAdmissibleKeywords(searchContext, configuration)) {
			return null;
		}

		_searchEvalLoggerStatisticsImpl.incrementKeywordSearchCount();

		SearchRequestOrigin searchRequestOrigin =
			_searchRequestOriginResolver.resolve();

		if (!_hasAdmissibleOrigin(searchRequestOrigin, configuration)) {
			return null;
		}

		if (_isExcludedEntryClassName(
				searchRequest, searchContext, configuration)) {

			return null;
		}

		if (!_passesSamplingDraw(configuration.samplingRate())) {
			return null;
		}

		_searchEvalLoggerStatisticsImpl.incrementAdmittedSearchCount();

		return searchRequestOrigin;
	}

	/**
	 * Condition 1, and deliberately the filter's strongest discriminator:
	 * internal callers overwhelmingly issue structured queries with no
	 * keywords.
	 *
	 * <p>
	 * A keyword-free search is admitted only when facet-only admission is
	 * enabled and facet selections were actually found. Enabling the setting
	 * alone is not enough, because without that second test the relaxation
	 * would admit exactly the keyword-free internal traffic this condition
	 * exists to exclude.
	 * </p>
	 */
	private boolean _hasAdmissibleKeywords(
		SearchContext searchContext,
		SearchEvalLoggerConfiguration configuration) {

		if (Validator.isNotNull(searchContext.getKeywords())) {
			return true;
		}

		if (!configuration.admitFacetOnlySearches()) {
			return false;
		}

		FacetCapture facetCapture = _facetExtractor.extract(
			searchContext, null);

		if (facetCapture.getStatus() == FacetCaptureStatus.CAPTURED) {
			return true;
		}

		return false;
	}

	/**
	 * Condition 2, plus the web request requirement of DESIGN.md 5.
	 *
	 * <p>
	 * When suggestion exclusion is on and the origin could not be read, the
	 * search is dropped rather than admitted. DESIGN.md 5.1 states that
	 * direction explicitly, and 3.2 generalizes it: absence of context is a
	 * reason to drop a request, not an error. Admitting instead would mean that
	 * the one failure mode this exclusion exists to prevent, typeahead traffic
	 * swamping the log, is also the failure mode an unreadable context produces.
	 * </p>
	 */
	private boolean _hasAdmissibleOrigin(
		SearchRequestOrigin searchRequestOrigin,
		SearchEvalLoggerConfiguration configuration) {

		if (configuration.requireWebRequestContext() &&
			!searchRequestOrigin.isWebRequestPresent()) {

			return false;
		}

		if (!configuration.excludeSuggestionTraffic()) {
			return true;
		}

		if (!searchRequestOrigin.isWebRequestPresent()) {
			return false;
		}

		return !searchRequestOrigin.isSuggestion();
	}

	/**
	 * Condition 3. A search constrained to any excluded type is dropped whole:
	 * the excluded types are the noisy or sensitive ones, and a mixed-type
	 * search still returns their rows.
	 */
	private boolean _isExcludedEntryClassName(
		SearchRequest searchRequest, SearchContext searchContext,
		SearchEvalLoggerConfiguration configuration) {

		String[] excludedEntryClassNames =
			configuration.excludedEntryClassNames();

		if ((excludedEntryClassNames == null) ||
			(excludedEntryClassNames.length == 0)) {

			return false;
		}

		List<String> excluded = Arrays.asList(excludedEntryClassNames);

		return EntryClassNames.get(
			searchRequest, searchContext
		).stream(
		).anyMatch(
			excluded::contains
		);
	}

	/**
	 * Condition 4.
	 */
	private boolean _passesSamplingDraw(double samplingRate) {
		if (samplingRate >= 1.0) {
			return true;
		}

		if (samplingRate <= 0.0) {
			return false;
		}

		ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();

		return threadLocalRandom.nextDouble() < samplingRate;
	}

	@Reference
	private SearchEvalLoggerStatisticsImpl _searchEvalLoggerStatisticsImpl;

	@Reference
	private FacetExtractor _facetExtractor;

	@Reference
	private SearchRequestOriginResolver _searchRequestOriginResolver;

}
