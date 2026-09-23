/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.api;

import java.time.LocalDate;

import java.util.Objects;

/**
 * One collection cycle of DESIGN.md 3.6: the span between logging being
 * switched on and switched off again, and what has been observed and notified
 * within it.
 *
 * <p>
 * The dates are UTC days rather than instants on purpose. Everything that reads
 * them works in days: the readiness threshold is configured in days, the
 * once-per-cycle markers only need to record that a day has been used, and the
 * collection start date is carried at day granularity to the signup page
 * (10.1). Storing an instant would mean rounding it back to a day at every one
 * of those points, with a chance of rounding differently each time.
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
		return new CollectionCycle(companyId, false, 0, null, null, null);
	}

	public static CollectionCycle open(long companyId, long enabledByUserId) {
		return new CollectionCycle(
			companyId, true, enabledByUserId, null, null, null);
	}

	public CollectionCycle(
		long companyId, boolean open, long enabledByUserId,
		LocalDate collectionStartDate, LocalDate readinessNotifiedDate,
		LocalDate stallNotifiedDate) {

		_companyId = companyId;
		_open = open;
		_enabledByUserId = enabledByUserId;
		_collectionStartDate = collectionStartDate;
		_readinessNotifiedDate = readinessNotifiedDate;
		_stallNotifiedDate = stallNotifiedDate;
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
				_collectionStartDate, collectionCycle._collectionStartDate) &&
			Objects.equals(
				_readinessNotifiedDate,
				collectionCycle._readinessNotifiedDate) &&
			Objects.equals(
				_stallNotifiedDate, collectionCycle._stallNotifiedDate)) {

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

	public long getCompanyId() {
		return _companyId;
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
			_companyId, _open, _enabledByUserId, _collectionStartDate,
			_readinessNotifiedDate, _stallNotifiedDate);
	}

	public boolean isCollectionStarted() {
		return _collectionStartDate != null;
	}

	public boolean isOpen() {
		return _open;
	}

	public boolean isReadinessNotified() {
		return _readinessNotifiedDate != null;
	}

	public boolean isStallNotified() {
		return _stallNotifiedDate != null;
	}

	public CollectionCycle withCollectionStartDate(
		LocalDate collectionStartDate) {

		return new CollectionCycle(
			_companyId, true, _enabledByUserId, collectionStartDate,
			_readinessNotifiedDate, _stallNotifiedDate);
	}

	public CollectionCycle withEnabledByUserId(long enabledByUserId) {
		return new CollectionCycle(
			_companyId, _open, enabledByUserId, _collectionStartDate,
			_readinessNotifiedDate, _stallNotifiedDate);
	}

	public CollectionCycle withReadinessNotifiedDate(
		LocalDate readinessNotifiedDate) {

		return new CollectionCycle(
			_companyId, _open, _enabledByUserId, _collectionStartDate,
			readinessNotifiedDate, _stallNotifiedDate);
	}

	public CollectionCycle withStallNotifiedDate(LocalDate stallNotifiedDate) {
		return new CollectionCycle(
			_companyId, _open, _enabledByUserId, _collectionStartDate,
			_readinessNotifiedDate, stallNotifiedDate);
	}

	private final LocalDate _collectionStartDate;
	private final long _companyId;
	private final long _enabledByUserId;
	private final boolean _open;
	private final LocalDate _readinessNotifiedDate;
	private final LocalDate _stallNotifiedDate;

}
