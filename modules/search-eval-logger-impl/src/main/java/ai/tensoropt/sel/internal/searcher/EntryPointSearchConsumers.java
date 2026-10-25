/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.searcher;

import com.liferay.portal.search.searcher.Searcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.component.runtime.dto.ReferenceDTO;
import org.osgi.service.component.runtime.dto.SatisfiedReferenceDTO;

/**
 * Names Liferay's search entry point components, and finds the ones among
 * them still bound to the portal's own {@code Searcher} instead of the
 * wrapper (TO-140).
 *
 * <p>
 * Shared by {@link SearchConsumerRebinder}, which rebinds exactly these
 * components, and {@link SearchInterceptionStatusImpl}, which judges bypass
 * by exactly the same set, read through {@code ServiceComponentRuntime}
 * DTOs rather than bundle-level {@code ServiceReference#getUsingBundles()}.
 * The two disagree after a forced rebind: a component-level DTO reports the
 * reference actually bound to the wrapper, while the bundle-level API can
 * still list the bundle against the portal's own, now-unused service, a
 * measured, benign bookkeeping artifact of {@code disableComponent}/
 * {@code enableComponent} rather than a real restart (DESIGN.md 3.1). Only
 * the component-level reading is precise enough to drive the rebind and the
 * primary detection; the bundle-level one is still useful for the coarser,
 * secondary question {@code hasOtherConsumersAwaitingRestart()} answers.
 * </p>
 *
 * <p>
 * <strong>Why only these components.</strong> A spike measured on a
 * 2026.Q3.8 dev portal (TO-140) found 32 Declarative Services components
 * across the portal that declare a {@code Searcher} reference. Disabling
 * and re-enabling all of them to force a rebind took 68.6 seconds and
 * failed 4,043 of 4,642 concurrent searches, because several are
 * service-layer components (<code>ObjectEntryLocalServiceImpl</code>,
 * <code>AccountEntryLocalServiceImpl</code>,
 * <code>AssetHelperImpl</code>, <code>CommerceCurrencyLocalServiceImpl</code>,
 * <code>KaleoDefinitionVersionLocalServiceImpl</code>, and others) whose own
 * dependents cascade through the same bounce. None of those live in the two
 * bundles below: measured on 2026.Q3.8, {@code com.liferay.portal.search.web}
 * and {@code com.liferay.portal.search} declare exactly the three entry
 * point components named there, nothing else. Narrowing the bounce to just
 * those three took 100ms total and failed 1 of 334 concurrent searches,
 * with no ERROR or WARN logged. Everything else needs no rebind at all: the
 * headless Search API (<code>SearchResultResourceImpl</code>,
 * <code>FacetResponseProcessor</code> in
 * <code>com.liferay.portal.search.rest.impl</code>) and the Search
 * Experiences REST API are created per request and resolve the
 * highest-ranked {@code Searcher} fresh every time.
 * </p>
 */
public class EntryPointSearchConsumers {

	/**
	 * The bundles declaring the entry point components:
	 * {@code com.liferay.portal.search.web}
	 * (<code>SearchDisplayContextFactoryImpl</code>,
	 * <code>PortletSharedSearchRequestImpl</code>) and
	 * {@code com.liferay.portal.search} (<code>BasicSuggestionsContributor</code>).
	 * Narrower than "every bundle with a {@code Searcher} reference"; see the
	 * class comment for why that distinction matters.
	 */
	public static final Set<String> BUNDLE_SYMBOLIC_NAMES = Set.of(
		"com.liferay.portal.search.web", "com.liferay.portal.search");

	/**
	 * Returns the entry point component descriptions whose static
	 * {@code Searcher} reference is currently bound to a service without
	 * {@value LoggingSearcher#MARKER_PROPERTY_NAME}, meaning a search issued
	 * through that component bypasses this plugin entirely.
	 *
	 * <p>
	 * Deliberately silent about whether a component is enabled at all. A
	 * component can be disabled by something other than
	 * {@code SearchConsumerRebinder}, such as Liferay's own Component
	 * Blacklist (System Settings &gt; Module Container), and a disabled
	 * component must stay invisible to this reading either way: reporting
	 * it as bypassing would make the rebinder re-enable a component an
	 * administrator deliberately disabled, on every install, redeploy and
	 * startup. A component the rebinder itself disabled and failed to
	 * re-enable is tracked separately, by {@code SearchConsumerRebinder}
	 * itself, and reported through {@link SearchInterceptionStatusImpl}
	 * rather than through this method.
	 * </p>
	 *
	 * <p>
	 * Resolves {@link #BUNDLE_SYMBOLIC_NAMES} to {@link Bundle} instances
	 * first and asks {@code ServiceComponentRuntime} for only their
	 * descriptions, rather than every active bundle's: this runs on every
	 * admin screen render, every daily job tick and twice per rebind, and
	 * the portal carries thousands of component descriptions overall.
	 * </p>
	 */
	public static List<ComponentDescriptionDTO> findBypassing(
		ServiceComponentRuntime serviceComponentRuntime,
		BundleContext bundleContext) {

		Bundle[] bundles = _entryPointBundles(bundleContext);

		if (bundles.length == 0) {
			return new ArrayList<>();
		}

		List<ComponentDescriptionDTO> bypassing = new ArrayList<>();

		for (ComponentDescriptionDTO description :
				serviceComponentRuntime.getComponentDescriptionDTOs(
					bundles)) {

			String referenceName = _searcherReferenceName(description);

			if ((referenceName != null) &&
				_isBypassing(
					serviceComponentRuntime, description, referenceName)) {

				bypassing.add(description);
			}
		}

		return bypassing;
	}

	private static Bundle[] _entryPointBundles(BundleContext bundleContext) {
		List<Bundle> bundles = new ArrayList<>();

		for (Bundle bundle : bundleContext.getBundles()) {
			if (BUNDLE_SYMBOLIC_NAMES.contains(bundle.getSymbolicName())) {
				bundles.add(bundle);
			}
		}

		return bundles.toArray(new Bundle[0]);
	}

	private static boolean _hasMarker(
		ServiceReferenceDTO serviceReferenceDTO) {

		if (serviceReferenceDTO.properties == null) {
			return false;
		}

		return "true".equals(
			serviceReferenceDTO.properties.get(
				LoggingSearcher.MARKER_PROPERTY_NAME));
	}

	private static boolean _isBypassing(
		ServiceComponentRuntime serviceComponentRuntime,
		ComponentDescriptionDTO description, String referenceName) {

		for (ComponentConfigurationDTO configuration :
				serviceComponentRuntime.getComponentConfigurationDTOs(
					description)) {

			if (configuration.satisfiedReferences == null) {
				continue;
			}

			for (SatisfiedReferenceDTO satisfiedReference :
					configuration.satisfiedReferences) {

				if (!referenceName.equals(satisfiedReference.name) ||
					(satisfiedReference.boundServices == null)) {

					continue;
				}

				for (ServiceReferenceDTO boundService :
						satisfiedReference.boundServices) {

					if (!_hasMarker(boundService)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * Returns the name of the component's static {@code Searcher}
	 * reference, or {@code null} if it has none. Only a static reference is
	 * a candidate: a dynamic one would already rebind on its own when a
	 * higher-ranked service appears, so forcing it through
	 * {@code disableComponent}/{@code enableComponent} would be pointless at
	 * best (DESIGN.md 3.1 documents the mechanism as targeting a static
	 * reluctant reference specifically).
	 */
	private static String _searcherReferenceName(
		ComponentDescriptionDTO description) {

		if (description.references == null) {
			return null;
		}

		for (ReferenceDTO reference : description.references) {
			if (_SEARCHER_INTERFACE_NAME.equals(reference.interfaceName) &&
				_STATIC_POLICY.equals(reference.policy)) {

				return reference.name;
			}
		}

		return null;
	}

	private static final String _SEARCHER_INTERFACE_NAME =
		Searcher.class.getName();

	private static final String _STATIC_POLICY = "static";

	private EntryPointSearchConsumers() {
	}

}
