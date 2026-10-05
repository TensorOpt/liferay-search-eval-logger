/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

import java.time.Instant;
import java.time.LocalDate;

import java.util.Objects;

/**
 * One collection cycle of DESIGN.md 3.6: the span between logging being
 * switched on and switched off again, and what has been observed and notified
 * within it.
 *
 * <p>
 * The notification markers and the collection start date are UTC days rather
 * than instants on purpose. Everything that reads them works in days: the
 * readiness threshold is configured in days, the once-per-cycle markers only
 * need to record that a day has been used, and the collection start date is
 * carried at day granularity to the signup page (10.1). <code>cycleOpenedDate
 * </code> is the one exception: the stall grace period (TO-112) is specified in
 * hours, not days, and the daily job's own fixed time of day means a day-level
 * reading of "opened" would under-count elapsed time for a cycle opened late in
 * the UTC day by as much as the gap between the job's trigger and midnight.
 * Storing an instant avoids that, at the cost of being the one field here that
 * is not a <code>LocalDate</code>.
 * </p>
 *
 * <p>
 * <code>enabledByUserId</code> is the administrator who switched logging on, so
 * that a notification can reach the person who is expecting the data. It
 * identifies an administrator rather than a search user, which puts it outside
 * D3, but it stays local and is never exported all the same. It is zero when
 * the enabling user could not be determined, which is not an error: the
 * notification then goes to the instance administrators.
 * </p>
 */
public final class CollectionCycle {

	/**
	 * No cycle is open for this company: logging is off, or has never been
	 * switched on.
	 */
	public static CollectionCycle closed(long companyId) {
		return new CollectionCycle(
			companyId, false, 0, null, null, null, null, null, false);
	}

	/**
	 * <code>cycleOpenedDate</code> is the instant the cycle opened, which is
	 * what the stall grace period of TO-112 counts from. It is required rather
	 * than defaulted, so that no caller can open a cycle without the value the
	 * grace period depends on.
	 */
	public static CollectionCycle open(
		long companyId, long enabledByUserId, Instant cycleOpenedDate) {

		return new CollectionCycle(
			companyId, true, enabledByUserId, cycleOpenedDate, null, null,
			null, null, false);
	}

	public CollectionCycle(
		long companyId, boolean open, long enabledByUserId,
		Instant cycleOpenedDate, LocalDate collectionStartDate,
		LocalDate collectionStartedNotifiedDate,
		LocalDate readinessNotifiedDate, LocalDate stallNotifiedDate,
		boolean signupBannerDismissed) {

		_companyId = companyId;
		_open = open;
		_enabledByUserId = enabledByUserId;
		_cycleOpenedDate = cycleOpenedDate;
		_collectionStartDate = collectionStartDate;
		_collectionStartedNotifiedDate = collectionStartedNotifiedDate;
		_readinessNotifiedDate = readinessNotifiedDate;
		_stallNotifiedDate = stallNotifiedDate;
		_signupBannerDismissed = signupBannerDismissed;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}

		if (!(object instanceof CollectionCycle)) {
			return false;
		}

		CollectionCycle collectionCycle = (CollectionCycle)object;

		if ((_companyId == collectionCycle._companyId) &&
			(_open == collectionCycle._open) &&
			(_enabledByUserId == collectionCycle._enabledByUserId) &&
			Objects.equals(
				_cycleOpenedDate, collectionCycle._cycleOpenedDate) &&
			Objects.equals(
				_collectionStartDate, collectionCycle._collectionStartDate) &&
			Objects.equals(
				_collectionStartedNotifiedDate,
				collectionCycle._collectionStartedNotifiedDate) &&
			Objects.equals(
				_readinessNotifiedDate,
				collectionCycle._readinessNotifiedDate) &&
			Objects.equals(
				_stallNotifiedDate, collectionCycle._stallNotifiedDate) &&
			(_signupBannerDismissed ==
				collectionCycle._signupBannerDismissed)) {

			return true;
		}

		return false;
	}

	/**
	 * The UTC day of the first event persisted in this cycle, or
	 * <code>null</code> while nothing has been collected yet.
	 *
	 * <p>
	 * This is deliberately not the day logging was enabled. Because of the
	 * restart requirement in 3.1, an instance can have logging enabled and
	 * collect nothing at all, so a date anchored on the toggle would be wrong
	 * exactly in the failure case that matters most.
	 * </p>
	 */
	public LocalDate getCollectionStartDate() {
		return _collectionStartDate;
	}

	/**
	 * Once-per-cycle marker for the "collection started" notification of
	 * TO-112, set by <code>CollectionCycleStatusImpl</code> only after the
	 * notification has been attempted, in its own write separate from {@link
	 * #getCollectionStartDate()}. <code>null</code> while it has not fired
	 * yet, which is also what gates a retry on the next persisted event if
	 * the attempt's own marker write failed, the same way {@link
	 * #getReadinessNotifiedDate()} and {@link #getStallNotifiedDate()} gate
	 * the daily job's retries. Nothing outside the impl bundle reads this
	 * today; it exists so a future consumer - the admin screen, say - has
	 * something meaningful to read rather than inferring "notified" from
	 * <code>collectionStartDate</code>, which only ever meant "collection
	 * started," not "and we told somebody."
	 */
	public LocalDate getCollectionStartedNotifiedDate() {
		return _collectionStartedNotifiedDate;
	}

	public long getCompanyId() {
		return _companyId;
	}

	/**
	 * The instant this cycle opened, which the stall grace period of TO-112
	 * counts from. <code>null</code> for a cycle opened before that field
	 * existed; <code>CollectionReadiness.isStalled</code> treats that as "grace
	 * period not over" rather than guessing, and
	 * <code>CollectionCycleStatusImpl</code> backfills it on the next
	 * reconciliation.
	 */
	public Instant getCycleOpenedDate() {
		return _cycleOpenedDate;
	}

	public long getEnabledByUserId() {
		return _enabledByUserId;
	}

	public LocalDate getReadinessNotifiedDate() {
		return _readinessNotifiedDate;
	}

	public LocalDate getStallNotifiedDate() {
		return _stallNotifiedDate;
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			_companyId, _open, _enabledByUserId, _cycleOpenedDate,
			_collectionStartDate, _collectionStartedNotifiedDate,
			_readinessNotifiedDate, _stallNotifiedDate,
			_signupBannerDismissed);
	}

	public boolean isCollectionStarted() {
		return _collectionStartDate != null;
	}

	public boolean isCollectionStartedNotified() {
		return _collectionStartedNotifiedDate != null;
	}

	public boolean isOpen() {
		return _open;
	}

	public boolean isReadinessNotified() {
		return _readinessNotifiedDate != null;
	}

	/**
	 * Local, in-portal state only (TO-112): set either when an administrator
	 * dismisses the signup banner of 10.1 explicitly, or when they click its
	 * link. Nothing about either action is transmitted; see
	 * <code>SignupBannerDismissal</code>.
	 */
	public boolean isSignupBannerDismissed() {
		return _signupBannerDismissed;
	}

	public boolean isStallNotified() {
		return _stallNotifiedDate != null;
	}

	public CollectionCycle withCollectionStartDate(
		LocalDate collectionStartDate) {

		return new CollectionCycle(
			_companyId, true, _enabledByUserId, _cycleOpenedDate,
			collectionStartDate, _collectionStartedNotifiedDate,
			_readinessNotifiedDate, _stallNotifiedDate,
			_signupBannerDismissed);
	}

	public CollectionCycle withCollectionStartedNotifiedDate(
		LocalDate collectionStartedNotifiedDate) {

		return new CollectionCycle(
			_companyId, _open, _enabledByUserId, _cycleOpenedDate,
			_collectionStartDate, collectionStartedNotifiedDate,
			_readinessNotifiedDate, _stallNotifiedDate,
			_signupBannerDismissed);
	}

	public CollectionCycle withCycleOpenedDate(Instant cycleOpenedDate) {
		return new CollectionCycle(
			_companyId, _open, _enabledByUserId, cycleOpenedDate,
			_collectionStartDate, _collectionStartedNotifiedDate,
			_readinessNotifiedDate, _stallNotifiedDate,
			_signupBannerDismissed);
	}

	public CollectionCycle withEnabledByUserId(long enabledByUserId) {
		return new CollectionCycle(
			_companyId, _open, enabledByUserId, _cycleOpenedDate,
			_collectionStartDate, _collectionStartedNotifiedDate,
			_readinessNotifiedDate, _stallNotifiedDate,
			_signupBannerDismissed);
	}

	public CollectionCycle withReadinessNotifiedDate(
		LocalDate readinessNotifiedDate) {

		return new CollectionCycle(
			_companyId, _open, _enabledByUserId, _cycleOpenedDate,
			_collectionStartDate, _collectionStartedNotifiedDate,
			readinessNotifiedDate, _stallNotifiedDate,
			_signupBannerDismissed);
	}

	public CollectionCycle withSignupBannerDismissed(
		boolean signupBannerDismissed) {

		return new CollectionCycle(
			_companyId, _open, _enabledByUserId, _cycleOpenedDate,
			_collectionStartDate, _collectionStartedNotifiedDate,
			_readinessNotifiedDate, _stallNotifiedDate,
			signupBannerDismissed);
	}

	public CollectionCycle withStallNotifiedDate(LocalDate stallNotifiedDate) {
		return new CollectionCycle(
			_companyId, _open, _enabledByUserId, _cycleOpenedDate,
			_collectionStartDate, _collectionStartedNotifiedDate,
			_readinessNotifiedDate, stallNotifiedDate,
			_signupBannerDismissed);
	}

	private final boolean _open;
	private final boolean _signupBannerDismissed;
	private final Instant _cycleOpenedDate;
	private final LocalDate _collectionStartDate;
	private final LocalDate _collectionStartedNotifiedDate;
	private final LocalDate _readinessNotifiedDate;
	private final LocalDate _stallNotifiedDate;
	private final long _companyId;
	private final long _enabledByUserId;

}
