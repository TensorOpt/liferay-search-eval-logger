/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.searcher;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.search.searcher.Searcher;

import ai.tensoropt.sel.api.SearchInterceptionStatus;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;

/**
 * Detects whether searches actually reach {@link LoggingSearcher} (TO-140).
 *
 * <p>
 * {@link #isIntercepting()} judges the entry points precisely, through
 * {@link EntryPointSearchConsumers} plus the components
 * {@link #recordFailedToReenable(List)} recorded that are still disabled, and
 * {@link #hasOtherConsumersAwaitingRestart()} judges every other consumer
 * more coarsely, by bundle. See the javadoc on {@link EntryPointSearchConsumers}
 * and on each method below for why the two answers are kept apart rather
 * than folded into one.
 * </p>
 */
@Component(service = {SearchInterceptionStatus.class, SearchInterceptionStatusImpl.class})
public class SearchInterceptionStatusImpl implements SearchInterceptionStatus {

	@Override
	public boolean hasOtherConsumersAwaitingRestart() {
		try {
			return _hasBypassingSecondaryConsumer();
		}
		catch (Throwable throwable) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Unable to determine whether other consumers are " +
						"awaiting restart",
					throwable);
			}

			// Unprovable is reported as "nothing pending", the same
			// direction isIntercepting() errs in and for the same reason: a
			// spurious note on a production admin screen is worse than a
			// missing one.

			return false;
		}
	}

	@Override
	public boolean isIntercepting() {
		try {
			for (ComponentDescriptionDTO componentDescriptionDTO :
					_failedToReenable) {

				if (!_serviceComponentRuntime.isComponentEnabled(
						componentDescriptionDTO)) {

					return false;
				}
			}

			return EntryPointSearchConsumers.findBypassing(
					_serviceComponentRuntime, _bundleContext
				).isEmpty();
		}
		catch (Throwable throwable) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Unable to determine whether interception is active",
					throwable);
			}

			// Unprovable is reported as working. A false warning telling an
			// administrator to restart a production portal is worse than no
			// warning, and the export manifest reports what was actually
			// collected either way.

			return true;
		}
	}

	/**
	 * Called by {@code SearchConsumerRebinder} once per rebind attempt,
	 * with the entry point components it disabled and then failed to
	 * re-enable, or an empty list if it either rebound everything or found
	 * nothing bypassing to begin with.
	 *
	 * <p>
	 * A component the rebinder disabled and could not bring back is left
	 * disabled, with no configuration for {@link EntryPointSearchConsumers}
	 * to read at all, so without this {@code isIntercepting()} would read
	 * that case as "no bypass" while the entry point is actually down.
	 * This is tracked here, separately from
	 * {@link EntryPointSearchConsumers#findBypassing}, specifically so that
	 * a component disabled by something else entirely, such as Liferay's
	 * own Component Blacklist, never ends up in it: only the rebinder ever
	 * calls this, and only with components it itself tried to re-enable.
	 * </p>
	 *
	 * <p>
	 * Accumulated, and read back only as "still disabled": a later rebind
	 * cannot see a component left disabled, so replacing the record would
	 * forget a failure that still holds, and a component an administrator
	 * has since enabled again stops counting without waiting for another
	 * rebind. Never persisted: an SCR disable is not persistent, so a
	 * restart clears a genuine failure on its own.
	 * </p>
	 */
	void recordFailedToReenable(List<ComponentDescriptionDTO> components) {
		_failedToReenable.addAll(components);
	}

	@Activate
	protected void activate(BundleContext bundleContext) {
		_bundleContext = bundleContext;
	}

	/**
	 * Bundle-level bypass: whether some bundle other than this plugin's own
	 * is still using a {@code Searcher} without the marker property.
	 *
	 * <p>
	 * Excludes three things, each for its own reason. This plugin's own
	 * bundle, whose delegate reference legitimately binds the portal's
	 * searcher. The entry point bundles {@link #isIntercepting()} already
	 * judges precisely at the component level: a bundle-level reading of
	 * them disagrees with it after a forced rebind, still listing the
	 * bundle against the portal's own, now-unused service even though the
	 * entry point component itself is correctly bound to the wrapper (a
	 * measured, benign artifact of {@code disableComponent}/
	 * {@code enableComponent}, documented in {@link EntryPointSearchConsumers}
	 * and in DESIGN.md 3.1; on 2026.Q3.8 those two bundles declare no other
	 * {@code Searcher}-referencing component to worry about counting). And
	 * any bundle that itself registers a {@code Searcher} service: a second
	 * wrapper chained below this one at a lower ranking (DESIGN.md 3.1)
	 * legitimately binds the portal's own searcher as its own delegate,
	 * forever, the same way this plugin's bundle does, and is not a
	 * consumer awaiting a restart.
	 * </p>
	 */
	private boolean _hasBypassingSecondaryConsumer() throws Exception {
		Bundle bundle = _bundleContext.getBundle();

		Collection<ServiceReference<Searcher>> serviceReferences =
			_bundleContext.getServiceReferences(Searcher.class, null);

		Set<Bundle> registeringBundles = new HashSet<>();

		for (ServiceReference<Searcher> serviceReference : serviceReferences) {
			registeringBundles.add(serviceReference.getBundle());
		}

		for (ServiceReference<Searcher> serviceReference : serviceReferences) {
			if (serviceReference.getProperty(
					LoggingSearcher.MARKER_PROPERTY_NAME) != null) {

				continue;
			}

			Bundle[] usingBundles = serviceReference.getUsingBundles();

			if (usingBundles == null) {
				continue;
			}

			for (Bundle usingBundle : usingBundles) {
				if (bundle.equals(usingBundle) ||
					EntryPointSearchConsumers.BUNDLE_SYMBOLIC_NAMES.contains(
						usingBundle.getSymbolicName()) ||
					registeringBundles.contains(usingBundle)) {

					continue;
				}

				return true;
			}
		}

		return false;
	}

	private static final Log _log = LogFactoryUtil.getLog(
		SearchInterceptionStatusImpl.class);

	private BundleContext _bundleContext;

	/**
	 * Written by {@code SearchConsumerRebinder}'s own background thread,
	 * read by {@link #isIntercepting()} on whatever thread renders the
	 * admin screen or runs the daily job.
	 */
	private final List<ComponentDescriptionDTO> _failedToReenable =
		new CopyOnWriteArrayList<>();

	@Reference
	private ServiceComponentRuntime _serviceComponentRuntime;

}
