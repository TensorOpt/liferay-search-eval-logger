/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.internal.searcher;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.security.auth.CompanyThreadLocal;
import com.liferay.portal.search.searcher.SearchRequest;
import com.liferay.portal.search.searcher.SearchRequestBuilder;
import com.liferay.portal.search.searcher.SearchRequestBuilderFactory;
import com.liferay.portal.search.searcher.SearchResponse;
import com.liferay.portal.search.searcher.Searcher;

import com.tensoropt.search.eval.logger.configuration.SearchEvalLoggerConfiguration;
import com.tensoropt.search.eval.logger.internal.admission.AdmissionFilter;
import com.tensoropt.search.eval.logger.internal.capture.CapturedSearchEvent;
import com.tensoropt.search.eval.logger.internal.capture.SearchEventCaptor;
import com.tensoropt.search.eval.logger.internal.configuration.SearchEvalLoggerConfigurationRegistry;
import com.tensoropt.search.eval.logger.internal.context.SearchRequestOrigin;
import com.tensoropt.search.eval.logger.internal.messaging.SearchEventDispatcher;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Wraps the registered {@link Searcher} so every caller that resolves it
 * through the OSGi registry is covered by one hook (DESIGN.md 3.1).
 *
 * <p>
 * The outgoing request is never modified (D2): the delegate runs first, and
 * only the response it already produced is read.
 * </p>
 *
 * <p>
 * <strong>Failure isolation is non-negotiable.</strong> Everything from the
 * admission filter inward is wrapped so that no logging failure, a full queue,
 * an unavailable database, a serialization error, missing context, can reach
 * the caller. A broken logger degrades to logging nothing, never to a broken
 * search page. Only the delegate's own exceptions propagate, because those are
 * the real search failing and hiding them would be worse than useless.
 * </p>
 */
@Component(
	property = {
		"search.eval.logger=true", "service.ranking:Integer=100"
	},
	service = Searcher.class
)
public class LoggingSearcher implements Searcher {

	@Override
	public SearchResponse search(SearchRequest searchRequest) {
		SearchResponse searchResponse = _searcher.search(searchRequest);

		try {
			_capture(searchRequest, searchResponse);
		}
		catch (Throwable throwable) {
			if (_log.isDebugEnabled()) {
				_log.debug("Unable to capture a search event", throwable);
			}
		}

		return searchResponse;
	}

	private void _capture(
		SearchRequest searchRequest, SearchResponse searchResponse) {

		// Cheapest possible exit for the install default, which is disabled:
		// one ThreadLocal read and one cached configuration lookup, before the
		// search context is touched.

		Long threadCompanyId = CompanyThreadLocal.getCompanyId();

		if ((threadCompanyId != null) && (threadCompanyId > 0)) {
			SearchEvalLoggerConfiguration configuration =
				_searchEvalLoggerConfigurationRegistry.getConfiguration(
					threadCompanyId);

			if (!configuration.enabled()) {
				return;
			}
		}

		SearchContext searchContext = _getSearchContext(searchRequest);

		if (searchContext == null) {
			return;
		}

		SearchEvalLoggerConfiguration configuration =
			_searchEvalLoggerConfigurationRegistry.getConfiguration(
				searchContext.getCompanyId());

		if (!configuration.enabled()) {
			return;
		}

		// The filter resolves the origin itself, once condition 1 has passed,
		// and hands it back for capture. See AdmissionFilter#admit.

		SearchRequestOrigin searchRequestOrigin = _admissionFilter.admit(
			searchContext, searchRequest, configuration);

		if (searchRequestOrigin == null) {
			return;
		}

		CapturedSearchEvent capturedSearchEvent = _searchEventCaptor.capture(
			searchRequest, searchResponse, searchContext, configuration,
			searchRequestOrigin);

		_searchEventDispatcher.dispatch(capturedSearchEvent);
	}

	/**
	 * The supported way to reach the legacy search context behind a
	 * {@link SearchRequest}. Only the reading form is used: the consumer form
	 * would hand out a context that could be mutated, and D2 forbids changing
	 * anything about the request.
	 */
	private SearchContext _getSearchContext(SearchRequest searchRequest) {
		try {
			SearchRequestBuilder searchRequestBuilder =
				_searchRequestBuilderFactory.builder(searchRequest);

			return searchRequestBuilder.withSearchContextGet(
				searchContext -> searchContext);
		}
		catch (Throwable throwable) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Unable to read the search context of a search request",
					throwable);
			}

			return null;
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		LoggingSearcher.class);

	@Reference
	private AdmissionFilter _admissionFilter;

	/*
	 * EC-3 is open, and this filter is the judgment call it concerns.
	 *
	 * Binding on the absence of this component's own marker property, rather
	 * than on "(!(service.ranking=100))" as the design doc drafts it, is
	 * deliberate. Filtering by ranking excludes every service at ranking 100,
	 * which is both too much and too little: too much, because a second
	 * plugin that legitimately wraps Searcher at the same ranking would become
	 * invisible to this one instead of being delegated to, breaking the chain;
	 * too little, because it stops protecting against self-binding the moment
	 * this component's own ranking is changed. A marker property cannot match
	 * this component under any ranking, and leaves any other wrapper reachable.
	 *
	 * An absent property makes the negation true in OSGi filter semantics, so
	 * the portal's own unranked Searcher matches.
	 */
	@Reference(target = "(!(search.eval.logger=true))")
	private Searcher _searcher;

	@Reference
	private SearchEvalLoggerConfigurationRegistry
		_searchEvalLoggerConfigurationRegistry;

	@Reference
	private SearchEventCaptor _searchEventCaptor;

	@Reference
	private SearchEventDispatcher _searchEventDispatcher;

	@Reference
	private SearchRequestBuilderFactory _searchRequestBuilderFactory;

}
