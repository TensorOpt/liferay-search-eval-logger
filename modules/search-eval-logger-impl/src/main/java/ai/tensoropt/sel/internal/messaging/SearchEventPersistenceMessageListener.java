/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.messaging;

import ai.tensoropt.sel.model.SearchHit;
import com.liferay.counter.kernel.service.CounterLocalService;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageListener;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.transaction.Propagation;
import com.liferay.portal.kernel.transaction.TransactionConfig;
import com.liferay.portal.kernel.transaction.TransactionInvokerUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.uuid.PortalUUIDUtil;

import ai.tensoropt.sel.api.AudienceType;
import ai.tensoropt.sel.api.SearchEvalLoggerConstants;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.internal.capture.CapturedSearchEvent;
import ai.tensoropt.sel.internal.capture.CapturedSearchHit;
import ai.tensoropt.sel.internal.cohort.CohortSaltRegistry;
import ai.tensoropt.sel.internal.configuration.SearchEvalLoggerConfigurationRegistry;
import ai.tensoropt.sel.internal.cycle.CollectionCycleStatusImpl;
import ai.tensoropt.sel.internal.statistics.SearchEvalLoggerStatisticsImpl;
import ai.tensoropt.sel.model.SearchEvent;
import ai.tensoropt.sel.service.SearchEventLocalService;
import ai.tensoropt.sel.service.SearchHitLocalService;

import java.util.Date;
import java.util.List;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Writes one captured event and its hits to the database (DESIGN.md 3.3).
 *
 * <p>
 * There is no cross-event buffer and no scheduled flush. Message Bus already
 * provides the queue and the backpressure, and a second buffer would add a
 * data-loss window on unexpected shutdown in exchange for nothing.
 * </p>
 *
 * <p>
 * The event and its up-to-K hits are written in one transaction, so a partially
 * written event cannot appear in an export. The transaction is opened
 * explicitly through {@link TransactionInvokerUtil} rather than declared with
 * <code>&#64;Transactional</code>, because this is a plain component: only
 * Service Builder services are proxied for that annotation, so here it would
 * compile, read as correct, and do nothing.
 * </p>
 *
 * <p>
 * The audience type and cohort hash are resolved here, on the consumer thread,
 * rather than at capture time. Both need a user lookup, and this keeps that
 * lookup off the search thread. The raw user ID travels in memory and is never
 * written (D3).
 * </p>
 */
@Component(
	property = "destination.name=" + SearchEvalLoggerConstants.DESTINATION_NAME,
	service = MessageListener.class
)
public class SearchEventPersistenceMessageListener implements MessageListener {

	@Override
	public void receive(Message message) {
		Object payload = message.getPayload();

		if (!(payload instanceof CapturedSearchEvent)) {
			return;
		}

		CapturedSearchEvent capturedSearchEvent = (CapturedSearchEvent)payload;

		try {
			TransactionInvokerUtil.invoke(
				_transactionConfig,
				() -> {
					_addSearchEvent(capturedSearchEvent);

					return null;
				});
		}
		catch (Throwable throwable) {
			_searchEvalLoggerStatisticsImpl.incrementDroppedEventCount();

			if (_log.isWarnEnabled()) {
				_log.warn("Unable to persist a search event", throwable);
			}

			return;
		}

		_searchEvalLoggerStatisticsImpl.incrementPersistedEventCount();

		// Outside the transaction, and outside the catch above. The collection
		// start date of 3.6 records that an event was persisted, so it must
		// not be written by a transaction that then rolls back; and a failure
		// here is not a failed write, so it must not be able to count an event
		// that did commit as dropped as well as persisted. The funnel in the
		// export manifest has to add up.
		//
		// Cheap enough to sit on this path: once the date is known it is a
		// hash lookup and a clock read per event.

		_collectionCycleStatusImpl.recordPersistedEvent(
			capturedSearchEvent.getCompanyId(),
			new Date(capturedSearchEvent.getCreateTime()));
	}

	private void _addSearchEvent(CapturedSearchEvent capturedSearchEvent) {
		long companyId = capturedSearchEvent.getCompanyId();

		SearchEvent searchEvent = _searchEventLocalService.createSearchEvent(
			_counterLocalService.increment(SearchEvent.class.getName()));

		String uuid = PortalUUIDUtil.generate();

		searchEvent.setUuid(uuid);
		searchEvent.setCompanyId(companyId);
		searchEvent.setCreateDate(new Date(capturedSearchEvent.getCreateTime()));
		searchEvent.setQueryText(capturedSearchEvent.getQueryText());
		searchEvent.setQueryTruncated(capturedSearchEvent.isQueryTruncated());
		searchEvent.setLocale(capturedSearchEvent.getLocale());
		searchEvent.setScopeGroupIds(capturedSearchEvent.getScopeGroupIds());
		searchEvent.setEntryClassNames(capturedSearchEvent.getEntryClassNames());
		searchEvent.setAppliedFacets(capturedSearchEvent.getAppliedFacets());
		searchEvent.setFacetCaptureStatus(
			capturedSearchEvent.getFacetCaptureStatus(
			).name());
		searchEvent.setBlueprintId(capturedSearchEvent.getBlueprintId());

		long userId = capturedSearchEvent.getUserId();

		AudienceType audienceType = _getAudienceType(userId);

		searchEvent.setAudienceType(audienceType.name());
		searchEvent.setCohortHash(_getCohortHash(companyId, userId));

		searchEvent.setRequestedSize(capturedSearchEvent.getRequestedSize());
		searchEvent.setRequestedFrom(capturedSearchEvent.getRequestedFrom());
		searchEvent.setTotalHits(capturedSearchEvent.getTotalHits());

		List<CapturedSearchHit> capturedSearchHits =
			capturedSearchEvent.getHits();

		searchEvent.setLoggedHitCount(capturedSearchHits.size());

		searchEvent.setSourceType(capturedSearchEvent.getSourceType(
		).name());

		_searchEventLocalService.addSearchEvent(searchEvent);

		for (CapturedSearchHit capturedSearchHit : capturedSearchHits) {
			_addSearchHit(
				uuid, companyId, searchEvent.getCreateDate(),
				capturedSearchHit);
		}

		// The query text itself is deliberately not logged. It is user input,
		// and the portal log is outside everything that governs this data:
		// retention purges the table but not the log, and log aggregation
		// ships it somewhere this plugin has no say over. The uuid is here
		// instead, which is enough to join back to the row for as long as the
		// row is supposed to exist.

		if (_log.isDebugEnabled()) {
			_log.debug(
				StringBundler.concat(
					"Persisted search event uuid=", uuid, ", companyId=",
					String.valueOf(companyId), ", queryLength=",
					String.valueOf(_length(searchEvent.getQueryText())),
					", queryTruncated=",
					String.valueOf(searchEvent.isQueryTruncated()),
					", audienceType=", audienceType.name(), ", sourceType=",
					searchEvent.getSourceType(), ", totalHits=",
					String.valueOf(searchEvent.getTotalHits()),
					", loggedHitCount=",
					String.valueOf(searchEvent.getLoggedHitCount())));
		}
	}

	private void _addSearchHit(
		String searchEventUuid, long companyId, Date createDate,
		CapturedSearchHit capturedSearchHit) {

		SearchHit searchHit =
			_searchHitLocalService.createSearchHit(
				_counterLocalService.increment(
					SearchHit.class.
						getName()));

		searchHit.setSearchEventUuid(searchEventUuid);

		// Duplicated from the event so the purge can delete hits without
		// resolving their parents first. Same transaction, never updated.

		searchHit.setCompanyId(companyId);
		searchHit.setCreateDate(createDate);
		searchHit.setRank(capturedSearchHit.getRank());
		searchHit.setScore(capturedSearchHit.getScore());
		searchHit.setDocUid(capturedSearchHit.getDocUid());
		searchHit.setEntryClassName(capturedSearchHit.getEntryClassName());
		searchHit.setEntryClassPK(capturedSearchHit.getEntryClassPK());
		searchHit.setTitle(capturedSearchHit.getTitle());
		searchHit.setSnippet(capturedSearchHit.getSnippet());
		searchHit.setExtraFields(capturedSearchHit.getExtraFields());

		_searchHitLocalService.addSearchHit(searchHit);

		if (_log.isDebugEnabled()) {
			_log.debug(
				StringBundler.concat(
					"Persisted search hit searchEventUuid=", searchEventUuid,
					", rank=", String.valueOf(searchHit.getRank()),
					", docUid=", searchHit.getDocUid(), ", title=",
					String.valueOf(Validator.isNotNull(searchHit.getTitle())),
					", snippet=",
					String.valueOf(
						Validator.isNotNull(searchHit.getSnippet())),
					", extraFields=",
					String.valueOf(
						Validator.isNotNull(searchHit.getExtraFields()))));
		}
	}

	/**
	 * A guest is anyone with no user ID on the request and anyone resolving to
	 * the instance's guest user. Unresolvable IDs count as guests, which
	 * over-reports the population whose results are fully comparable rather
	 * than inventing an authenticated one.
	 */
	private AudienceType _getAudienceType(long userId) {
		if (userId <= 0) {
			return AudienceType.GUEST;
		}

		User user = _userLocalService.fetchUser(userId);

		if ((user == null) || user.isGuestUser()) {
			return AudienceType.GUEST;
		}

		return AudienceType.AUTHENTICATED;
	}

	private String _getCohortHash(long companyId, long userId) {
		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration =
			_searchEvalLoggerConfigurationRegistry.getConfiguration(companyId);

		return _cohortSaltRegistry.hash(
			companyId, userId,
			searchEvalLoggerConfiguration.cohortSaltRotationDays());
	}

	private int _length(String value) {
		if (value == null) {
			return 0;
		}

		return value.length();
	}

	private static final Log _log = LogFactoryUtil.getLog(
		SearchEventPersistenceMessageListener.class);

	@Reference
	private CohortSaltRegistry _cohortSaltRegistry;

	@Reference
	private CollectionCycleStatusImpl _collectionCycleStatusImpl;

	@Reference
	private CounterLocalService _counterLocalService;

	@Reference
	private SearchEvalLoggerConfigurationRegistry
		_searchEvalLoggerConfigurationRegistry;

	@Reference
	private SearchEvalLoggerStatisticsImpl _searchEvalLoggerStatisticsImpl;

	@Reference
	private SearchEventLocalService _searchEventLocalService;

	@Reference
	private SearchHitLocalService _searchHitLocalService;

	private final TransactionConfig _transactionConfig =
		TransactionConfig.Factory.create(
			Propagation.REQUIRED, new Class<?>[] {Exception.class});

	@Reference
	private UserLocalService _userLocalService;

}
