/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.searcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.liferay.portal.search.searcher.Searcher;

import java.lang.reflect.Field;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.component.runtime.dto.ReferenceDTO;
import org.osgi.service.component.runtime.dto.SatisfiedReferenceDTO;

/**
 * Two separate questions, deliberately not conflated (TO-140): whether the
 * entry points this plugin rebinds automatically are intercepting, and
 * whether some other, secondary consumer still needs a restart. A test that
 * only covered one could not catch the two being accidentally merged back
 * into one answer.
 */
public class SearchInterceptionStatusImplTest {

	@Test
	public void aBundleThatItselfRegistersASearcherIsNotReportedAsSecondary()
		throws Exception {

		// A second wrapper chained below ranking 100 (DESIGN.md 3.1) binds
		// the portal's own searcher as its own delegate, forever, the same
		// way this plugin's bundle does. Without the registering-bundle
		// exclusion, that bundle would show up here permanently, not
		// because it is awaiting a restart but because it is itself part
		// of the wrapper chain.

		Bundle ownBundle = mock(Bundle.class);
		Bundle secondWrapperBundle = mock(Bundle.class);

		when(
			secondWrapperBundle.getSymbolicName()
		).thenReturn(
			"com.example.other.wrapper"
		);

		ServiceReference<Searcher> portalSearcherReference =
			_searchReference(null, secondWrapperBundle, mock(Bundle.class));
		ServiceReference<Searcher> secondWrapperReference = _searchReference(
			null, null, secondWrapperBundle);

		BundleContext bundleContext = mock(BundleContext.class);

		when(
			bundleContext.getBundle()
		).thenReturn(
			ownBundle
		);
		when(
			bundleContext.getServiceReferences(Searcher.class, (String)null)
		).thenReturn(
			List.of(portalSearcherReference, secondWrapperReference)
		);

		_inject("_bundleContext", bundleContext);

		assertFalse(
			_searchInterceptionStatusImpl.hasOtherConsumersAwaitingRestart(),
			"A bundle that registers its own Searcher service must not be "
				+ "reported as a secondary consumer awaiting restart");
	}

	@Test
	public void aBypassingEntryPointComponentMeansNotIntercepting() {
		ComponentDescriptionDTO description = _searcherComponent(
			"SearchDisplayContextFactoryImpl");

		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of(description)
		);
		when(
			_serviceComponentRuntime.getComponentConfigurationDTOs(
				description)
		).thenReturn(
			List.of(_configurationBoundTo(description, false))
		);

		assertFalse(_searchInterceptionStatusImpl.isIntercepting());
	}

	@Test
	public void aFailedReenableMeansNotIntercepting() {

		// SearchConsumerRebinder calls this once per rebind attempt, with
		// whatever it disabled and failed to bring back. The component has
		// no configuration at all once disabled, so
		// EntryPointSearchConsumers alone would read this as no bypass;
		// this is what makes isIntercepting() answer correctly anyway.

		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of()
		);

		_searchInterceptionStatusImpl.recordFailedToReenable(
			List.of(_searcherComponent("SearchDisplayContextFactoryImpl")));

		assertFalse(_searchInterceptionStatusImpl.isIntercepting());
	}

	@Test
	public void aFailedReenableStopsCountingOnceTheComponentIsEnabledAgain() {
		ComponentDescriptionDTO componentDescriptionDTO = _searcherComponent(
			"SearchDisplayContextFactoryImpl");

		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of()
		);
		when(
			_serviceComponentRuntime.isComponentEnabled(componentDescriptionDTO)
		).thenReturn(
			true
		);

		_searchInterceptionStatusImpl.recordFailedToReenable(
			List.of(componentDescriptionDTO));

		assertTrue(_searchInterceptionStatusImpl.isIntercepting());
	}

	@Test
	public void aFailedReenableIsNotForgottenByALaterRebind() {
		ComponentDescriptionDTO componentDescriptionDTO = _searcherComponent(
			"SearchDisplayContextFactoryImpl");

		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of()
		);

		_searchInterceptionStatusImpl.recordFailedToReenable(
			List.of(componentDescriptionDTO));
		_searchInterceptionStatusImpl.recordFailedToReenable(List.of());

		assertFalse(_searchInterceptionStatusImpl.isIntercepting());
	}

	@Test
	public void aNonMarkerConsumerInAnEntryPointBundleDoesNotCountAsSecondary()
		throws Exception {

		_stubBundleLevelConsumer("com.liferay.portal.search.web");

		assertFalse(
			_searchInterceptionStatusImpl.hasOtherConsumersAwaitingRestart(),
			"An entry point bundle is judged by isIntercepting(), not by "
				+ "the bundle-level secondary check");
	}

	@Test
	public void aNonMarkerConsumerInAnUnrelatedBundleIsReportedAsSecondary()
		throws Exception {

		_stubBundleLevelConsumer("com.liferay.portal.object.service");

		assertTrue(
			_searchInterceptionStatusImpl.hasOtherConsumersAwaitingRestart(),
			"A service-layer consumer outside the entry point bundles, "
				+ "still bound to the portal's own searcher, is exactly "
				+ "what this method exists to report");
	}

	@Test
	public void aNonMarkerConsumerInThisPluginsOwnBundleIsExcluded()
		throws Exception {

		Bundle ownBundle = mock(Bundle.class);

		_stubBundleLevelConsumer(ownBundle, ownBundle);

		assertFalse(
			_searchInterceptionStatusImpl.hasOtherConsumersAwaitingRestart(),
			"The wrapper's own delegate reference must not count as a "
				+ "secondary consumer awaiting restart");
	}

	@Test
	public void anUnreadableRegistryReportsHasOtherConsumersAwaitingRestartAsFalse()
		throws Exception {

		BundleContext bundleContext = mock(BundleContext.class);

		when(
			bundleContext.getBundle()
		).thenReturn(
			mock(Bundle.class)
		);
		when(
			bundleContext.getServiceReferences(Searcher.class, (String)null)
		).thenThrow(
			new InvalidSyntaxException("unreadable", null)
		);

		_inject("_bundleContext", bundleContext);

		assertFalse(
			_searchInterceptionStatusImpl.hasOtherConsumersAwaitingRestart(),
			"An unprovable registry read must not raise a secondary "
				+ "restart note that cannot be explained");
	}

	@Test
	public void anUnreadableRegistryReportsInterceptingAsTrue() {
		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenThrow(
			new IllegalStateException("unreadable")
		);

		assertTrue(
			_searchInterceptionStatusImpl.isIntercepting(),
			"An unprovable registry read must not raise a false restart "
				+ "warning");
	}

	@Test
	public void noBypassingComponentMeansIntercepting() {
		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of()
		);

		assertTrue(_searchInterceptionStatusImpl.isIntercepting());
	}

	private ComponentConfigurationDTO _configurationBoundTo(
		ComponentDescriptionDTO description, boolean wrapper) {

		ServiceReferenceDTO boundService = new ServiceReferenceDTO();

		boundService.properties = new HashMap<>();

		if (wrapper) {
			boundService.properties.put(
				LoggingSearcher.MARKER_PROPERTY_NAME, "true");
		}

		SatisfiedReferenceDTO satisfiedReference =
			new SatisfiedReferenceDTO();

		satisfiedReference.name = description.references[0].name;
		satisfiedReference.boundServices =
			new ServiceReferenceDTO[] {boundService};

		ComponentConfigurationDTO configuration =
			new ComponentConfigurationDTO();

		configuration.satisfiedReferences =
			new SatisfiedReferenceDTO[] {satisfiedReference};

		return configuration;
	}

	/**
	 * A default {@code BundleContext} good enough for {@link
	 * SearchInterceptionStatusImpl#isIntercepting()}: one bundle matching an
	 * entry point name, so {@code EntryPointSearchConsumers} has something to
	 * resolve. Tests of {@link SearchInterceptionStatusImpl#hasOtherConsumersAwaitingRestart()}
	 * replace it with their own, more specific one.
	 */
	private BundleContext _entryPointBundleContext() {
		Bundle bundle = mock(Bundle.class);

		when(
			bundle.getSymbolicName()
		).thenReturn(
			"com.liferay.portal.search.web"
		);

		BundleContext bundleContext = mock(BundleContext.class);

		when(
			bundleContext.getBundles()
		).thenReturn(
			new Bundle[] {bundle}
		);

		return bundleContext;
	}

	private void _inject(String name, Object value) throws Exception {
		_set(_searchInterceptionStatusImpl, name, value);
	}

	private ComponentDescriptionDTO _searcherComponent(String name) {
		ComponentDescriptionDTO description = new ComponentDescriptionDTO();

		description.name = name;

		ReferenceDTO reference = new ReferenceDTO();

		reference.name = "_searcher";
		reference.interfaceName = Searcher.class.getName();
		reference.policy = "static";

		description.references = new ReferenceDTO[] {reference};

		return description;
	}

	@SuppressWarnings("unchecked")
	private ServiceReference<Searcher> _searchReference(
		String markerPropertyValue, Bundle usingBundle,
		Bundle registeringBundle) {

		ServiceReference<Searcher> serviceReference = mock(
			ServiceReference.class);

		when(
			serviceReference.getProperty(
				LoggingSearcher.MARKER_PROPERTY_NAME)
		).thenReturn(
			markerPropertyValue
		);
		when(
			serviceReference.getUsingBundles()
		).thenReturn(
			(usingBundle == null) ? null : new Bundle[] {usingBundle}
		);
		when(
			serviceReference.getBundle()
		).thenReturn(
			registeringBundle
		);

		return serviceReference;
	}

	private void _set(Object target, String name, Object value)
		throws Exception {

		Field field = SearchInterceptionStatusImpl.class.getDeclaredField(
			name);

		field.setAccessible(true);

		field.set(target, value);
	}

	/**
	 * Stubs a non-marker {@code Searcher} service reference whose single
	 * using bundle is some bundle other than this plugin's own, named by
	 * symbolic name, exercising the bundle-level secondary check in
	 * isolation from {@link SearchInterceptionStatusImpl#isIntercepting()}.
	 */
	private void _stubBundleLevelConsumer(String usingBundleSymbolicName)
		throws Exception {

		Bundle usingBundle = mock(Bundle.class);

		when(
			usingBundle.getSymbolicName()
		).thenReturn(
			usingBundleSymbolicName
		);

		_stubBundleLevelConsumer(mock(Bundle.class), usingBundle);
	}

	private void _stubBundleLevelConsumer(Bundle ownBundle, Bundle usingBundle)
		throws Exception {

		ServiceReference<Searcher> serviceReference = _searchReference(
			null, usingBundle, mock(Bundle.class));

		BundleContext bundleContext = mock(BundleContext.class);

		when(
			bundleContext.getBundle()
		).thenReturn(
			ownBundle
		);
		when(
			bundleContext.getServiceReferences(Searcher.class, (String)null)
		).thenReturn(
			List.of(serviceReference)
		);

		_inject("_bundleContext", bundleContext);
	}

	private SearchInterceptionStatusImpl _newInjected() {
		SearchInterceptionStatusImpl impl =
			new SearchInterceptionStatusImpl();

		try {
			_set(impl, "_serviceComponentRuntime", _serviceComponentRuntime);
			_set(impl, "_bundleContext", _entryPointBundleContext());
		}
		catch (Exception exception) {
			throw new RuntimeException(exception);
		}

		return impl;
	}

	private final ServiceComponentRuntime _serviceComponentRuntime = mock(
		ServiceComponentRuntime.class);
	private final SearchInterceptionStatusImpl _searchInterceptionStatusImpl =
		_newInjected();

}
