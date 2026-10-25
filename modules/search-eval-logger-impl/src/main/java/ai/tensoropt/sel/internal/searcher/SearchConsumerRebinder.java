/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.searcher;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.search.searcher.Searcher;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;

/**
 * Rebinds Liferay's search entry points onto {@link LoggingSearcher}
 * automatically, so that neither installing this plugin nor redeploying it
 * leaves collection silently stalled until the next portal restart (TO-140).
 *
 * <p>
 * DESIGN.md 3.1 used to require a restart after every install and every
 * redeploy: Liferay's search consumers declare a plain, static, reluctant
 * {@code @Reference} to {@code Searcher}, so a consumer already bound to the
 * portal's own searcher never rebinds when a higher-ranked one appears.
 * Bouncing <em>every</em> such consumer to force a rebind is ruled out by a
 * spike this plugin does not repeat at runtime (68.6s and 4,043 of 4,642
 * concurrent searches failing, because several are service-layer components
 * whose own dependents cascade through the same bounce). This instead
 * targets only {@link EntryPointSearchConsumers}, which a spike measured at
 * 100ms total with 1 of 334 concurrent searches failing and nothing logged
 * at ERROR or WARN. See that class for the full reasoning and the
 * component names.
 * </p>
 *
 * <p>
 * Activated by a static reference to the wrapper itself, filtered to its
 * own marker property, so this component (re)activates exactly when
 * {@code LoggingSearcher} (re)registers: on first install, and again on
 * every redeploy, which unregisters and re-registers it the same way.
 * {@code service = {}} declares no service of its own, which the DS
 * specification makes immediate by default: a component that provides a
 * service only activates once something actually resolves that service,
 * and nothing ever resolves this one, so declaring none is what makes it
 * run as soon as it is satisfied rather than sit merely satisfied forever.
 * The rebind itself runs off the activation thread, on a short-lived daemon
 * thread: {@code activate} must return immediately rather than block SCR on
 * another component's own enable/disable promises, which is exactly the
 * mistake that would turn a redeploy into a deadlock.
 * </p>
 *
 * <p>
 * <strong>Failure isolation is non-negotiable here as everywhere else.</strong>
 * A failed rebind must never prevent the portal from serving search, and is
 * left to the admin screen and the log rather than retried or escalated.
 * </p>
 *
 * <p>
 * <strong>A failed re-enable, not just a failed disable.</strong> The
 * {@code finally} in {@link #_rebind(ComponentDescriptionDTO)} already
 * means a component is never left disabled just because its own disable
 * call blew up, but {@code enableComponent} itself can still fail. That
 * leaves the component genuinely disabled, with no configuration for
 * {@link EntryPointSearchConsumers} to read at all, which would otherwise
 * look exactly like no bypass. Every component this re-enable step fails
 * for is handed to {@link SearchInterceptionStatusImpl#recordFailedToReenable(List)},
 * which is what lets {@code isIntercepting()} still answer correctly.
 * </p>
 */
@Component(service = {})
public class SearchConsumerRebinder {

	@Activate
	protected void activate(BundleContext bundleContext) {
		_bundleContext = bundleContext;

		Thread thread = new Thread(
			this::rebind, "search-eval-logger-consumer-rebinder");

		thread.setDaemon(true);

		thread.start();
	}

	/**
	 * Visible for testing, so a test can call it directly on the calling
	 * thread instead of racing the background one {@link #activate(BundleContext)}
	 * starts. Returns the entry point components still bypassing once the
	 * attempt is done, which is both the test's hook and exactly the
	 * condition the restart warning below is driven by.
	 */
	List<ComponentDescriptionDTO> rebind() {
		try {
			List<ComponentDescriptionDTO> bypassing =
				EntryPointSearchConsumers.findBypassing(
					_serviceComponentRuntime, _bundleContext);

			long startTime = System.nanoTime();

			List<ComponentDescriptionDTO> failedToReenable =
				new ArrayList<>();

			for (ComponentDescriptionDTO description : bypassing) {
				if (!_rebind(description)) {
					failedToReenable.add(description);
				}
			}

			if (!bypassing.isEmpty() && _log.isInfoEnabled()) {
				long elapsedMillis =
					(System.nanoTime() - startTime) / 1000000;

				_log.info(
					"Rebound " + bypassing.size() +
						" search entry point component(s) onto the search " +
							"eval logger wrapper in " + elapsedMillis +
								"ms: " + _names(bypassing));
			}

			// Reported here, not folded into EntryPointSearchConsumers:
			// a component the rebinder itself disabled and could not bring
			// back needs to count as bypassing even though it now has no
			// configuration for that class to read, but a component
			// something else disabled, such as Liferay's own Component
			// Blacklist, must never be reported this way or touched again
			// by the loop above. Only this class ever calls this, and only
			// with components it itself tried to re-enable.

			_searchInterceptionStatusImpl.recordFailedToReenable(
				failedToReenable);

			List<ComponentDescriptionDTO> stillBypassing = new ArrayList<>(
				EntryPointSearchConsumers.findBypassing(
					_serviceComponentRuntime, _bundleContext));

			stillBypassing.addAll(failedToReenable);

			if (!stillBypassing.isEmpty() && _log.isWarnEnabled()) {

				// The two cases the rebind above does not fix: a bypassing
				// entry point component whose disable/enable did not move it
				// onto the wrapper, such as a reference whose target filter
				// was itself the problem, and one this loop disabled but
				// failed to bring back. This is exactly the warning
				// DESIGN.md 3.1 always logged on activation, moved here from
				// SearchInterceptionStatusImpl so it fires only once a
				// rebind has actually been attempted and failed, rather than
				// on every install, before this asynchronous attempt has had
				// a chance to run at all.

				_log.warn(_RESTART_REQUIRED_WARNING);
			}

			return stillBypassing;
		}
		catch (Throwable throwable) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to rebind search entry point components onto " +
						"the search eval logger wrapper. " +
							_RESTART_REQUIRED_WARNING,
					throwable);
			}

			return List.of();
		}
	}

	private String _names(List<ComponentDescriptionDTO> descriptions) {
		return descriptions.stream(
		).map(
			description -> description.name
		).collect(
			Collectors.joining(", ")
		);
	}

	/**
	 * One component's disable/enable, isolated from the others: a failing
	 * disable or enable on one bypassing component must not stop the rest
	 * from being attempted. Returns whether the component was successfully
	 * re-enabled, which is true as soon as {@code enableComponent} itself
	 * returns without throwing, whatever {@code disableComponent} did.
	 */
	private boolean _rebind(ComponentDescriptionDTO description) {
		boolean reenabled = false;

		try {
			try {
				_serviceComponentRuntime.disableComponent(
					description
				).getValue();
			}
			finally {
				_serviceComponentRuntime.enableComponent(
					description
				).getValue();

				reenabled = true;
			}
		}
		catch (Throwable throwable) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to rebind " + description.name +
						" onto the search eval logger wrapper",
					throwable);
			}
		}

		return reenabled;
	}

	private static final String _RESTART_REQUIRED_WARNING =
		"Search interception is still bypassed by one or more entry " +
			"point components after an automatic rebind attempt. Restart " +
				"the portal. Nothing will be collected from them until " +
					"then, whatever the configuration says.";

	private static final Log _log = LogFactoryUtil.getLog(
		SearchConsumerRebinder.class);

	private BundleContext _bundleContext;

	@Reference(target = "(" + LoggingSearcher.MARKER_PROPERTY_NAME + "=true)")
	private Searcher _searcher;

	@Reference
	private SearchInterceptionStatusImpl _searchInterceptionStatusImpl;

	@Reference
	private ServiceComponentRuntime _serviceComponentRuntime;

}
