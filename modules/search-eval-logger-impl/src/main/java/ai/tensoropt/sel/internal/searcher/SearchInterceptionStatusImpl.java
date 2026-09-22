/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.searcher;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.search.searcher.Searcher;

import ai.tensoropt.sel.api.SearchInterceptionStatus;

import java.util.Collection;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/**
 * Detects whether searches actually reach {@link LoggingSearcher}.
 *
 * <p>
 * Two signals, in order of strength. A search that has already passed through
 * the wrapper proves interception outright. Failing that, the OSGi registry is
 * asked directly: if a bundle other than this one is still using a
 * {@code Searcher} that is not the wrapper, that bundle bound the portal's own
 * searcher before this plugin was installed and, being a static reluctant
 * reference, will never rebind. Its searches bypass the wrapper entirely until
 * the portal restarts.
 * </p>
 *
 * <p>
 * The wrapper itself holds the delegate, so this bundle is expected among the
 * users of the portal's searcher and is excluded from the check.
 * </p>
 */
@Component(service = {SearchInterceptionStatus.class, SearchInterceptionStatusImpl.class})
public class SearchInterceptionStatusImpl implements SearchInterceptionStatus {

	@Override
	public boolean isIntercepting() {
		if (_intercepted) {
			return true;
		}

		try {
			return !_hasBypassingConsumer();
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

	public void recordInterception() {
		if (!_intercepted) {
			_intercepted = true;
		}
	}

	@Activate
	protected void activate(BundleContext bundleContext) {
		_bundleContext = bundleContext;

		if (!isIntercepting() && _log.isWarnEnabled()) {
			_log.warn(
				"Search interception is not active: Liferay's search " +
					"consumers are still bound to the portal's own searcher " +
						"because they activated before this plugin was " +
							"installed. Restart the portal. Nothing will be " +
								"collected until then, whatever the " +
									"configuration says.");
		}
	}

	private boolean _hasBypassingConsumer() throws Exception {
		Bundle bundle = _bundleContext.getBundle();

		Collection<ServiceReference<Searcher>> serviceReferences =
			_bundleContext.getServiceReferences(Searcher.class, null);

		for (ServiceReference<Searcher> serviceReference : serviceReferences) {
			if (serviceReference.getProperty(_MARKER_PROPERTY_NAME) != null) {
				continue;
			}

			Bundle[] usingBundles = serviceReference.getUsingBundles();

			if (usingBundles == null) {
				continue;
			}

			for (Bundle usingBundle : usingBundles) {
				if (!bundle.equals(usingBundle)) {
					return true;
				}
			}
		}

		return false;
	}

	private static final String _MARKER_PROPERTY_NAME = "search.eval.logger";

	private static final Log _log = LogFactoryUtil.getLog(
		SearchInterceptionStatusImpl.class);

	private BundleContext _bundleContext;
	private volatile boolean _intercepted;

}
