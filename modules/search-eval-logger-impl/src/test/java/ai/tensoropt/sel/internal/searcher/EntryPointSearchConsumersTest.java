/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.searcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.liferay.portal.search.searcher.Searcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.component.runtime.dto.ReferenceDTO;
import org.osgi.service.component.runtime.dto.SatisfiedReferenceDTO;

/**
 * {@link EntryPointSearchConsumers} is read by both
 * {@link SearchInterceptionStatusImpl} (what to report) and
 * {@link SearchConsumerRebinder} (what to rebind), so a mistake here would
 * make detection and the fix it drives disagree silently (TO-140).
 */
public class EntryPointSearchConsumersTest {

	@Test
	public void aComponentWithNoSatisfiedReferenceYetIsNotBypassing() {

		// Not satisfied is not the same as bypassing: a component that is
		// merely still waiting on some other reference to bind must not be
		// rebound, since there is nothing for the rebind to fix.

		ComponentDescriptionDTO description = _searcherComponent(
			"com.liferay.portal.search", "BasicSuggestionsContributor");

		ComponentConfigurationDTO configuration =
			new ComponentConfigurationDTO();

		configuration.satisfiedReferences = new SatisfiedReferenceDTO[0];

		ServiceComponentRuntime serviceComponentRuntime = mock(
			ServiceComponentRuntime.class);

		when(
			serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of(description)
		);
		when(
			serviceComponentRuntime.getComponentConfigurationDTOs(
				description)
		).thenReturn(
			List.of(configuration)
		);

		assertEquals(
			List.of(),
			EntryPointSearchConsumers.findBypassing(
				serviceComponentRuntime,
				_bundleContext("com.liferay.portal.search")));
	}

	@Test
	public void aComponentDisabledBySomethingElseIsNeitherBypassingNorTouched() {

		// Liferay's own Component Blacklist (System Settings > Module
		// Container) disables a component through this same
		// ServiceComponentRuntime API, and only re-enables it when removed
		// from the blacklist. A disabled component has no configuration at
		// all for the reading below to see, the same shape as a component
		// that is merely unsatisfied, and findBypassing must not tell the
		// two apart: reporting a blacklisted component as bypassing would
		// make SearchConsumerRebinder, which only ever disables and
		// re-enables what this method returns, silently undo an
		// administrator's own choice on every install, redeploy and
		// startup. A component the rebinder itself disabled and failed to
		// bring back is tracked separately, by SearchConsumerRebinder, not
		// here.

		ComponentDescriptionDTO description = _searcherComponent(
			"com.liferay.portal.search.web",
			"SearchDisplayContextFactoryImpl");

		ServiceComponentRuntime serviceComponentRuntime = mock(
			ServiceComponentRuntime.class);

		when(
			serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of(description)
		);
		when(
			serviceComponentRuntime.getComponentConfigurationDTOs(
				description)
		).thenReturn(
			List.of()
		);

		assertEquals(
			List.of(),
			EntryPointSearchConsumers.findBypassing(
				serviceComponentRuntime,
				_bundleContext("com.liferay.portal.search.web")),
			"A component disabled by something other than this plugin's "
				+ "own rebinder must never be reported as bypassing, which "
				+ "is what keeps the rebinder from ever touching it");
	}

	@Test
	public void aComponentWithoutASearcherReferenceIsNeverReported() {
		ComponentDescriptionDTO description = new ComponentDescriptionDTO();

		description.name = "SomeUnrelatedComponent";
		description.references = new ReferenceDTO[0];

		ServiceComponentRuntime serviceComponentRuntime = mock(
			ServiceComponentRuntime.class);

		when(
			serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of(description)
		);

		assertEquals(
			List.of(),
			EntryPointSearchConsumers.findBypassing(
				serviceComponentRuntime,
				_bundleContext("com.liferay.portal.search.web")));
	}

	@Test
	public void aDynamicReferenceIsNeverReportedAsBypassing() {

		// Only a static reference is a candidate: a dynamic one already
		// rebinds on its own, so forcing it through disableComponent/
		// enableComponent would be pointless, and DESIGN.md 3.1 documents
		// the mechanism as targeting a static reluctant reference
		// specifically.

		ComponentDescriptionDTO description = _searcherComponent(
			"com.liferay.portal.search.web",
			"SearchDisplayContextFactoryImpl", "dynamic");

		ServiceComponentRuntime serviceComponentRuntime = mock(
			ServiceComponentRuntime.class);

		when(
			serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of(description)
		);

		assertEquals(
			List.of(),
			EntryPointSearchConsumers.findBypassing(
				serviceComponentRuntime,
				_bundleContext("com.liferay.portal.search.web")),
			"A dynamic reference must never be reported as bypassing, "
				+ "whatever it is bound to");
	}

	@Test
	public void componentBoundToThePortalSearcherIsBypassing() {
		ComponentDescriptionDTO description = _searcherComponent(
			"com.liferay.portal.search.web",
			"SearchDisplayContextFactoryImpl");

		ServiceComponentRuntime serviceComponentRuntime =
			_serviceComponentRuntime(description, false);

		List<ComponentDescriptionDTO> bypassing =
			EntryPointSearchConsumers.findBypassing(
				serviceComponentRuntime,
				_bundleContext("com.liferay.portal.search.web"));

		assertEquals(1, bypassing.size());
		assertEquals(description, bypassing.get(0));
	}

	@Test
	public void componentBoundToTheWrapperIsNotBypassing() {
		ComponentDescriptionDTO description = _searcherComponent(
			"com.liferay.portal.search.web",
			"SearchDisplayContextFactoryImpl");

		ServiceComponentRuntime serviceComponentRuntime =
			_serviceComponentRuntime(description, true);

		assertEquals(
			List.of(),
			EntryPointSearchConsumers.findBypassing(
				serviceComponentRuntime,
				_bundleContext("com.liferay.portal.search.web")));
	}

	@Test
	public void noEntryPointBundleInstalledShortCircuitsWithoutQueryingTheRuntime() {
		ServiceComponentRuntime serviceComponentRuntime = mock(
			ServiceComponentRuntime.class);

		List<ComponentDescriptionDTO> bypassing =
			EntryPointSearchConsumers.findBypassing(
				serviceComponentRuntime,
				_bundleContext("com.liferay.portal.object.service"));

		assertEquals(
			List.of(), bypassing,
			"A service-layer bundle outside the entry point bundles must "
				+ "never produce a bypass report");
		verifyNoInteractions(serviceComponentRuntime);
	}

	@Test
	public void onlyTheBypassingComponentIsReportedAmongSeveral() {
		ComponentDescriptionDTO bound = _searcherComponent(
			"com.liferay.portal.search.web",
			"SearchDisplayContextFactoryImpl");
		ComponentDescriptionDTO bypassing = _searcherComponent(
			"com.liferay.portal.search", "BasicSuggestionsContributor");

		ServiceComponentRuntime serviceComponentRuntime = mock(
			ServiceComponentRuntime.class);

		when(
			serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of(bound, bypassing)
		);

		_stubConfiguration(serviceComponentRuntime, bound, true);
		_stubConfiguration(serviceComponentRuntime, bypassing, false);

		List<ComponentDescriptionDTO> result =
			EntryPointSearchConsumers.findBypassing(
				serviceComponentRuntime,
				_bundleContext(
					"com.liferay.portal.search.web",
					"com.liferay.portal.search"));

		assertEquals(1, result.size());
		assertTrue(result.contains(bypassing));
	}

	@Test
	public void resolvesOnlyTheEntryPointBundlesRatherThanEveryActiveBundle() {

		// This runs on every admin screen render, every daily job tick and
		// twice per rebind, and the portal carries thousands of component
		// descriptions overall: asking ServiceComponentRuntime for only the
		// two entry point bundles' descriptions, rather than every active
		// bundle's, is the point of resolving them by name first.

		BundleContext bundleContext = _bundleContext(
			"com.liferay.portal.search.web", "com.liferay.portal.object.service",
			"com.liferay.portal.search");

		ServiceComponentRuntime serviceComponentRuntime = mock(
			ServiceComponentRuntime.class);

		when(
			serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of()
		);

		EntryPointSearchConsumers.findBypassing(
			serviceComponentRuntime, bundleContext);

		ArgumentCaptor<Bundle[]> captor = ArgumentCaptor.forClass(
			Bundle[].class);

		verify(
			serviceComponentRuntime
		).getComponentDescriptionDTOs(
			captor.capture()
		);

		Set<String> resolvedNames = new HashSet<>();

		for (Bundle bundle : captor.getValue()) {
			resolvedNames.add(bundle.getSymbolicName());
		}

		assertEquals(
			Set.of(
				"com.liferay.portal.search.web", "com.liferay.portal.search"),
			resolvedNames,
			"Only the entry point bundles should be resolved and asked "
				+ "for, not the unrelated bundle also installed");
	}

	private BundleContext _bundleContext(String... symbolicNames) {
		List<Bundle> bundles = new ArrayList<>();

		for (String symbolicName : symbolicNames) {
			Bundle bundle = mock(Bundle.class);

			when(bundle.getSymbolicName()).thenReturn(symbolicName);

			bundles.add(bundle);
		}

		BundleContext bundleContext = mock(BundleContext.class);

		when(
			bundleContext.getBundles()
		).thenReturn(
			bundles.toArray(new Bundle[0])
		);

		return bundleContext;
	}

	private ComponentDescriptionDTO _searcherComponent(
		String bundleSymbolicName, String name) {

		return _searcherComponent(bundleSymbolicName, name, "static");
	}

	private ComponentDescriptionDTO _searcherComponent(
		String bundleSymbolicName, String name, String policy) {

		ComponentDescriptionDTO description = new ComponentDescriptionDTO();

		description.name = name;

		ReferenceDTO reference = new ReferenceDTO();

		reference.name = "_searcher";
		reference.interfaceName = Searcher.class.getName();
		reference.policy = policy;

		description.references = new ReferenceDTO[] {reference};

		return description;
	}

	private ServiceComponentRuntime _serviceComponentRuntime(
		ComponentDescriptionDTO description, boolean boundToWrapper) {

		ServiceComponentRuntime serviceComponentRuntime = mock(
			ServiceComponentRuntime.class);

		when(
			serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of(description)
		);

		_stubConfiguration(
			serviceComponentRuntime, description, boundToWrapper);

		return serviceComponentRuntime;
	}

	private void _stubConfiguration(
		ServiceComponentRuntime serviceComponentRuntime,
		ComponentDescriptionDTO description, boolean boundToWrapper) {

		ServiceReferenceDTO boundService = new ServiceReferenceDTO();

		Map<String, Object> properties = new HashMap<>();

		if (boundToWrapper) {
			properties.put(LoggingSearcher.MARKER_PROPERTY_NAME, "true");
		}

		boundService.properties = properties;

		SatisfiedReferenceDTO satisfiedReference =
			new SatisfiedReferenceDTO();

		satisfiedReference.name = description.references[0].name;
		satisfiedReference.boundServices =
			new ServiceReferenceDTO[] {boundService};

		ComponentConfigurationDTO configuration =
			new ComponentConfigurationDTO();

		configuration.satisfiedReferences =
			new SatisfiedReferenceDTO[] {satisfiedReference};

		when(
			serviceComponentRuntime.getComponentConfigurationDTOs(
				description)
		).thenReturn(
			List.of(configuration)
		);
	}

}
