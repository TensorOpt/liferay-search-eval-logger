/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.cycle;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.service.PortletPreferencesLocalService;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.PortletKeys;
import com.liferay.portal.kernel.util.Validator;

import ai.tensoropt.sel.api.CollectionCycle;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import javax.portlet.PortletPreferences;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Persists the collection cycle of DESIGN.md 3.6, one record per virtual
 * instance.
 *
 * <p>
 * The three values 3.6 names are state, not settings. Putting them in the
 * <code>&#64;Meta.OCD</code> configuration would have been the shortest route
 * to per-instance persistence, and it would have been wrong: Liferay renders
 * every declared attribute on the System Settings screen, so the collection
 * start date and the notification markers would have appeared there as editable
 * fields. An administrator could then set a collection start date for a cycle
 * that never collected anything, which is the one thing 3.6 exists to prevent.
 * </p>
 *
 * <p>
 * Company-scoped portlet preferences rather than a Service Builder entity,
 * because five scalars per virtual instance do not justify a table. A new table
 * in this plugin's service module means a schema version bump and an upgrade
 * step that an administrator has to run on an instance where the module is
 * already installed; the failure mode if they do not is that the module refuses
 * to start, which is a far worse outcome than the one being solved. Preferences
 * need no DDL, are already per company, and are read at most once a day per
 * instance plus once per cycle on the write path.
 * </p>
 *
 * <p>
 * The owner id is a name of this plugin's own rather than the admin portlet's,
 * so that nothing here can be mistaken for, or wiped along with, portlet
 * configuration.
 * </p>
 *
 * <p>
 * Nothing in this class throws. A preference store that cannot be read reports
 * a closed cycle, which suppresses the funnel link and the notifications rather
 * than fabricating a collection start date; a write that fails is logged and
 * dropped. Neither is worth failing a search, a scheduled job or an admin
 * screen over.
 * </p>
 *
 * <p>
 * <b>The read-at-most-once-per-cycle figure above is the healthy case.</b> The
 * caller arms its in-memory marker only on a successful write, so while writes
 * keep failing every persisted event costs a read here plus a failed write
 * attempt, on the Message Bus consumer thread. That is deliberate: a failure is
 * far more likely to be transient than permanent, and retrying promptly is what
 * keeps a moment's unavailability from costing a cycle its collection start
 * date for good. The blast radius is bounded, because that consumer is already
 * asynchronous and already drops under backpressure, so the cost lands on the
 * log and never on a search. If a permanently broken store ever turns out to
 * matter, the fix is a back-off on the marker rather than a silent give-up.
 * </p>
 */
@Component(service = CollectionCycleStore.class)
public class CollectionCycleStore {

	public CollectionCycle get(long companyId) {
		try {
			PortletPreferences portletPreferences = _getPortletPreferences(
				companyId);

			if (!GetterUtil.getBoolean(
					portletPreferences.getValue(_KEY_OPEN, null))) {

				return CollectionCycle.closed(companyId);
			}

			return new CollectionCycle(
				companyId, true,
				GetterUtil.getLong(
					portletPreferences.getValue(_KEY_ENABLED_BY_USER_ID, null)),
				_getLocalDate(portletPreferences, _KEY_COLLECTION_START_DATE),
				_getLocalDate(portletPreferences, _KEY_READINESS_NOTIFIED_DATE),
				_getLocalDate(portletPreferences, _KEY_STALL_NOTIFIED_DATE));
		}
		catch (Exception exception) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to read the collection cycle of company " +
						companyId,
					exception);
			}

			return CollectionCycle.closed(companyId);
		}
	}

	/**
	 * Returns whether the record was written. A caller that keeps an in-memory
	 * marker must not arm it when this is false, or a failed write would look
	 * like a completed one for the rest of the cycle.
	 */
	public boolean save(CollectionCycle collectionCycle) {
		long companyId = collectionCycle.getCompanyId();

		try {
			PortletPreferences portletPreferences = _getPortletPreferences(
				companyId);

			portletPreferences.setValue(
				_KEY_OPEN, String.valueOf(collectionCycle.isOpen()));
			portletPreferences.setValue(
				_KEY_ENABLED_BY_USER_ID,
				String.valueOf(collectionCycle.getEnabledByUserId()));

			_setLocalDate(
				portletPreferences, _KEY_COLLECTION_START_DATE,
				collectionCycle.getCollectionStartDate());
			_setLocalDate(
				portletPreferences, _KEY_READINESS_NOTIFIED_DATE,
				collectionCycle.getReadinessNotifiedDate());
			_setLocalDate(
				portletPreferences, _KEY_STALL_NOTIFIED_DATE,
				collectionCycle.getStallNotifiedDate());

			_portletPreferencesLocalService.updatePreferences(
				companyId, PortletKeys.PREFS_OWNER_TYPE_COMPANY,
				PortletKeys.PREFS_PLID_SHARED, _PORTLET_ID,
				portletPreferences);

			return true;
		}
		catch (Exception exception) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to write the collection cycle of company " +
						companyId,
					exception);
			}

			return false;
		}
	}

	private LocalDate _getLocalDate(
		PortletPreferences portletPreferences, String key) {

		String value = portletPreferences.getValue(key, null);

		if (Validator.isNull(value)) {
			return null;
		}

		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException dateTimeParseException) {

			// An unparseable date is treated as absent rather than as a
			// failure. The cycle then behaves as if that step had not happened
			// yet, which costs at most one repeated notification.

			if (_log.isWarnEnabled()) {
				_log.warn(
					"Ignoring unparseable collection cycle value for " + key,
					dateTimeParseException);
			}

			return null;
		}
	}

	/**
	 * The owner id of a company-scoped preference record is the company id,
	 * not <code>PREFS_OWNER_ID_DEFAULT</code>. The lookup key is
	 * <code>(ownerId, ownerType, plid, portletId)</code> and does not include
	 * the company id passed alongside it, so a zero owner id would give every
	 * virtual instance on the portal one shared record. Verified against the
	 * running instance: every <code>ownerType=1</code> row there, Liferay's own
	 * company-scoped configuration records included, carries the company id as
	 * its owner id.
	 */
	private PortletPreferences _getPortletPreferences(long companyId) {
		return _portletPreferencesLocalService.getPreferences(
			companyId, companyId, PortletKeys.PREFS_OWNER_TYPE_COMPANY,
			PortletKeys.PREFS_PLID_SHARED, _PORTLET_ID);
	}

	private void _setLocalDate(
			PortletPreferences portletPreferences, String key,
			LocalDate localDate)
		throws Exception {

		// The empty string rather than null. Portlet preferences read a null
		// value back as the caller's default, so the two would be
		// indistinguishable, and clearing a date has to be distinguishable
		// from never having set one.

		if (localDate == null) {
			portletPreferences.setValue(key, "");

			return;
		}

		portletPreferences.setValue(key, localDate.toString());
	}

	private static final String _KEY_COLLECTION_START_DATE =
		"collectionStartDate";

	private static final String _KEY_ENABLED_BY_USER_ID = "enabledByUserId";

	private static final String _KEY_OPEN = "open";

	private static final String _KEY_READINESS_NOTIFIED_DATE =
		"readinessNotifiedDate";

	private static final String _KEY_STALL_NOTIFIED_DATE =
		"stallNotifiedDate";

	private static final String _PORTLET_ID =
		"ai.tensoropt.sel.collection.cycle";

	private static final Log _log = LogFactoryUtil.getLog(
		CollectionCycleStore.class);

	@Reference
	private PortletPreferencesLocalService _portletPreferencesLocalService;

}
