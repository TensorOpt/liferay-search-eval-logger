/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.notifications;

import javax.mail.internet.InternetAddress;

/**
 * The email half of DESIGN.md 3.6's three notifications (TO-112, EC-15), kept
 * behind an interface for exactly the reason every other Liferay-touching
 * class in this bundle is: <code>UserNotificationManagerUtil</code> and
 * <code>MailServiceUtil</code> are static facades over OSGi-tracked services
 * with no running portal to back them in a plain unit test. The gate that
 * decides <em>whether</em> to call either of these two methods lives in
 * {@link CollectionNotifier}, which is what is actually tested; this
 * interface exists so that decision can be tested with a mock instead of a
 * portal.
 */
public interface EmailDelivery {

	/**
	 * Whether <code>userId</code> wants this plugin's notifications by email,
	 * respecting whatever they set in My Account &gt; Notifications (or the
	 * registered default, if they never touched it). Answers
	 * <code>false</code> on any failure to resolve the preference: an
	 * administrator who never gets an email they wanted notices nothing
	 * different from today; one who gets email they tried to turn off would
	 * notice, so failures fail toward silence.
	 */
	public boolean isWanted(long userId, long companyId);

	/**
	 * Sends one email through the portal's own configured mail server (D9).
	 * Never throws: a failure here must not be able to undo the website
	 * delivery that happens alongside it, so this logs and swallows on its
	 * own rather than leaving that to its caller.
	 */
	public void send(long companyId, InternetAddress to, String subject, String body);

}
