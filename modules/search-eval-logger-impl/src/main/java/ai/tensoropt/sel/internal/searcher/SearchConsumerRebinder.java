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
import org.osgi.service.component.annotations.Deactivate;
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
 * A failed rebind must never prevent the portal from serving search. It is
 * retried a few times, a short pause apart, because during a redeploy the
 * component registry is still settling: Felix SCR's own
 * {@code ComponentRegistry.getComponentHolders} threw a
 * {@code NullPointerException} on the first read after a reinstall in one
 * CI run, and without a retry the widget stayed bypassed until the next
 * restart. After the last attempt the admin screen and the log take over;
 * nothing is escalated further.
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

		_thread = new Thread(
			this::rebindWithRetries, "search-eval-logger-consumer-rebinder");

		_thread.setDaemon(true);

		_thread.start();
	}

	/**
	 * Stops a rebind still in progress, so a thread started by an instance
	 * that a redeploy or an uninstall has since replaced cannot keep bouncing
	 * components or log a restart warning on behalf of a component that no
	 * longer exists.
	 */
	@Deactivate
	protected void deactivate() {
		_deactivated = true;

		_thread.interrupt();
	}

	/**
	 * What the background thread runs: {@link #rebind(List)} up to
	 * {@link #_MAX_ATTEMPTS} times, {@link #_retryDelayMillis} apart, while an
	 * attempt fails, meaning its registry read threw or it disabled a
	 * component it could not re-enable. Each later attempt first re-enables
	 * what an earlier one left disabled. An attempt that completes and still
	 * finds an entry point bypassing is not repeated: bouncing the component
	 * again would not change what its reference resolves to, and every bounce
	 * costs a few failed searches (DESIGN.md 3.1). Visible for testing.
	 */
	void rebindWithRetries() {
		List<ComponentDescriptionDTO> failedToReenable = new ArrayList<>();
		Throwable lastFailure = null;

		for (int attempt = 1; attempt <= _MAX_ATTEMPTS; attempt++) {
			if (attempt > 1) {
				try {
					Thread.sleep(_retryDelayMillis);
				}
				catch (InterruptedException interruptedException) {
					Thread.currentThread().interrupt();

					return;
				}
			}

			if (_deactivated) {
				return;
			}

			try {
				failedToReenable.removeIf(this::_enable);

				List<ComponentDescriptionDTO> stillBypassing = rebind(
					failedToReenable);

				lastFailure = null;

				if (failedToReenable.isEmpty()) {
					if (!stillBypassing.isEmpty() && _log.isWarnEnabled()) {
						_log.warn(_RESTART_REQUIRED_WARNING);
					}

					return;
				}
			}
			catch (Throwable throwable) {
				lastFailure = throwable;

				if (_log.isDebugEnabled()) {
					_log.debug(
						"Rebind attempt " + attempt + " of " + _MAX_ATTEMPTS +
							" failed",
						throwable);
				}
			}
		}

		// Every attempt failed: the registry never became readable, or a
		// component this loop disabled could not be brought back. Logged
		// here, after the attempts, and not unconditionally on activation:
		// every fresh install and every redeploy runs this automatic rebind
		// first, so warning before it has had a chance to run would report a
		// restart as needed when the rebind was about to make it unnecessary.

		if (!_log.isWarnEnabled()) {
			return;
		}

		if (lastFailure != null) {
			_log.warn(
				"Unable to rebind search entry point components onto the " +
					"search eval logger wrapper. " + _RESTART_REQUIRED_WARNING,
				lastFailure);
		}
		else {
			_log.warn(
				"Unable to re-enable " + _names(failedToReenable) + ". " +
					_RESTART_REQUIRED_WARNING);
		}
	}

	/**
	 * One attempt, as a test calls it: the components still bypassing once
	 * it is done, including any it disabled and could not re-enable.
	 */
	List<ComponentDescriptionDTO> rebind() {
		List<ComponentDescriptionDTO> failedToReenable = new ArrayList<>();

		List<ComponentDescriptionDTO> stillBypassing = new ArrayList<>(
			rebind(failedToReenable));

		stillBypassing.addAll(failedToReenable);

		return stillBypassing;
	}

	/**
	 * One attempt. Adds every component it disabled and could not re-enable
	 * to <code>failedToReenable</code>, and returns the entry point components
	 * the registry still reports as bypassing afterwards. A registry read
	 * failure propagates, so {@link #rebindWithRetries()} can tell a failed
	 * attempt from a clean one.
	 */
	private List<ComponentDescriptionDTO> rebind(
		List<ComponentDescriptionDTO> failedToReenable) {

		List<ComponentDescriptionDTO> bypassing =
			EntryPointSearchConsumers.findBypassing(
				_serviceComponentRuntime, _bundleContext);

		long startTime = System.nanoTime();

		List<ComponentDescriptionDTO> failedNow = new ArrayList<>();

		for (ComponentDescriptionDTO description : bypassing) {
			if (!_rebind(description)) {
				failedNow.add(description);
			}
		}

		if (!bypassing.isEmpty() && _log.isInfoEnabled()) {
			long elapsedMillis = (System.nanoTime() - startTime) / 1000000;

			_log.info(
				"Rebound " + bypassing.size() +
					" search entry point component(s) onto the search eval " +
						"logger wrapper in " + elapsedMillis + "ms: " +
							_names(bypassing));
		}

		// Reported here, not folded into EntryPointSearchConsumers: a
		// component the rebinder itself disabled and could not bring back
		// needs to count as bypassing even though it now has no
		// configuration for that class to read, but a component something
		// else disabled, such as Liferay's own Component Blacklist, must
		// never be reported this way or touched again by the loop above.
		// Only this class ever calls this, and only with components it
		// itself tried to re-enable.

		_searchInterceptionStatusImpl.recordFailedToReenable(failedNow);

		failedToReenable.addAll(failedNow);

		return EntryPointSearchConsumers.findBypassing(
			_serviceComponentRuntime, _bundleContext);
	}

	/**
	 * Re-enables one component. Never throws: a failure is logged and
	 * reported as <code>false</code>, so the caller can retry it.
	 */
	private boolean _enable(ComponentDescriptionDTO description) {
		try {
			_serviceComponentRuntime.enableComponent(
				description
			).getValue();

			return true;
		}
		catch (Throwable throwable) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to re-enable " + description.name, throwable);
			}

			return false;
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
	 * from being attempted. The enable runs whatever the disable did, so a
	 * component is never left disabled just because its own disable call
	 * blew up. Returns whether the component was re-enabled.
	 */
	private boolean _rebind(ComponentDescriptionDTO description) {
		try {
			_serviceComponentRuntime.disableComponent(
				description
			).getValue();
		}
		catch (Throwable throwable) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to disable " + description.name +
						" before re-enabling it",
					throwable);
			}
		}

		return _enable(description);
	}

	private static final int _MAX_ATTEMPTS = 5;

	private static final String _RESTART_REQUIRED_WARNING =
		"Search interception is still bypassed by one or more entry " +
			"point components after an automatic rebind attempt. Restart " +
				"the portal. Nothing will be collected from them until " +
					"then, whatever the configuration says.";

	private static final Log _log = LogFactoryUtil.getLog(
		SearchConsumerRebinder.class);

	private BundleContext _bundleContext;

	private volatile boolean _deactivated;

	/**
	 * Pause between attempts. Not final so a test can set it to zero.
	 */
	private long _retryDelayMillis = 2000;

	@Reference(target = "(" + LoggingSearcher.MARKER_PROPERTY_NAME + "=true)")
	private Searcher _searcher;

	@Reference
	private SearchInterceptionStatusImpl _searchInterceptionStatusImpl;

	@Reference
	private ServiceComponentRuntime _serviceComponentRuntime;

	private Thread _thread;

}
