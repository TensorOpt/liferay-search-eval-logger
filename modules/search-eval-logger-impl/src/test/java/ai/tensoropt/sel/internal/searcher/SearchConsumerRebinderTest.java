/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal.searcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.portal.search.searcher.Searcher;

import ai.tensoropt.sel.internal.TestInjection;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.InOrder;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.component.runtime.dto.ReferenceDTO;
import org.osgi.service.component.runtime.dto.SatisfiedReferenceDTO;
import org.osgi.util.promise.Promises;

/**
 * TO-140: {@link SearchConsumerRebinder#rebindWithRetries()} must never
 * throw, whatever {@code ServiceComponentRuntime} does, and {@link SearchConsumerRebinder#activate(BundleContext)}
 * must never block waiting for it. The components {@code rebind()} reports
 * still bypassing once it is done are both the test's hook and exactly what
 * drives the restart warning in production, so asserting on that return
 * value is what makes a test here able to fail if that logic regresses; the
 * warning text itself is not asserted on, the same as no other log message
 * in this module. A component whose own re-enable fails is handed to
 * {@link SearchInterceptionStatusImpl#recordFailedToReenable(List)}, which
 * {@link SearchInterceptionStatusImplTest} covers from the reading side.
 */
public class SearchConsumerRebinderTest {

	@BeforeEach
	public void setUp() throws Exception {
		_serviceComponentRuntime = mock(ServiceComponentRuntime.class);
		_searchInterceptionStatusImpl = mock(
			SearchInterceptionStatusImpl.class);
		_bundleContext = _bundleContext("com.liferay.portal.search.web");

		TestInjection.set(
			_searchConsumerRebinder, "_serviceComponentRuntime",
			_serviceComponentRuntime);
		TestInjection.set(
			_searchConsumerRebinder, "_searchInterceptionStatusImpl",
			_searchInterceptionStatusImpl);
		TestInjection.set(
			_searchConsumerRebinder, "_bundleContext", _bundleContext);
	}

	@Test
	public void aBypassingComponentIsDisabledThenReenabled() {
		ComponentDescriptionDTO description = _bypassingEntryPointComponent();

		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of(description)
		);
		when(
			_serviceComponentRuntime.disableComponent(description)
		).thenReturn(
			Promises.resolved(null)
		);
		when(
			_serviceComponentRuntime.enableComponent(description)
		).thenReturn(
			Promises.resolved(null)
		);

		_searchConsumerRebinder.rebind();

		InOrder inOrder = inOrder(_serviceComponentRuntime);

		inOrder.verify(
			_serviceComponentRuntime
		).disableComponent(
			description
		);
		inOrder.verify(
			_serviceComponentRuntime
		).enableComponent(
			description
		);
	}

	@Test
	public void aComponentNotBypassingIsNeverTouched() {
		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of()
		);

		_searchConsumerRebinder.rebind();

		verify(_serviceComponentRuntime, never()).disableComponent(any());
		verify(_serviceComponentRuntime, never()).enableComponent(any());
	}

	@Test
	public void aFailingDisableStillAttemptsEnableAndNeverThrows() {
		ComponentDescriptionDTO description = _bypassingEntryPointComponent();

		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of(description)
		);
		when(
			_serviceComponentRuntime.disableComponent(description)
		).thenThrow(
			new IllegalStateException("disable failed")
		);
		when(
			_serviceComponentRuntime.enableComponent(description)
		).thenReturn(
			Promises.resolved(null)
		);

		_searchConsumerRebinder.rebind();

		// The enable runs whatever the disable did, so a half-disabled
		// component is not left disabled forever.

		verify(_serviceComponentRuntime).enableComponent(description);
	}

	@Test
	public void aFailingEnableIsReturnedAsStillBypassing() {

		// EntryPointSearchConsumers never sees this: a disabled component
		// has no configuration for it to read, which is exactly why this
		// has to be tracked here instead, and handed to
		// SearchInterceptionStatusImpl rather than silently dropped.

		ComponentDescriptionDTO description = _bypassingEntryPointComponent();

		// Found on the first read only, as a component left disabled is:
		// the second read, after the rebind, no longer sees it.

		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of(description)
		).thenReturn(
			List.of()
		);
		when(
			_serviceComponentRuntime.disableComponent(description)
		).thenReturn(
			Promises.resolved(null)
		);
		when(
			_serviceComponentRuntime.enableComponent(description)
		).thenThrow(
			new IllegalStateException("enable failed")
		);

		List<ComponentDescriptionDTO> stillBypassing =
			_searchConsumerRebinder.rebind();

		assertTrue(
			stillBypassing.contains(description),
			"A component the rebinder disabled but failed to re-enable "
				+ "must still be reported as bypassing");
		verify(
			_searchInterceptionStatusImpl
		).recordFailedToReenable(
			eq(List.of(description))
		);
	}

	@Test
	public void aFailingComponentDoesNotStopTheOthersFromBeingRebound() {
		ComponentDescriptionDTO failing = _bypassingEntryPointComponent(
			"SearchDisplayContextFactoryImpl");
		ComponentDescriptionDTO succeeding = _bypassingEntryPointComponent(
			"BasicSuggestionsContributor");

		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of(failing, succeeding)
		);
		when(
			_serviceComponentRuntime.disableComponent(failing)
		).thenThrow(
			new IllegalStateException("disable failed")
		);
		when(
			_serviceComponentRuntime.enableComponent(failing)
		).thenThrow(
			new IllegalStateException("enable failed too")
		);
		when(
			_serviceComponentRuntime.disableComponent(succeeding)
		).thenReturn(
			Promises.resolved(null)
		);
		when(
			_serviceComponentRuntime.enableComponent(succeeding)
		).thenReturn(
			Promises.resolved(null)
		);

		_searchConsumerRebinder.rebind();

		// The component whose disable and enable both failed must not have
		// stopped the loop: the second, unrelated component still gets its
		// own attempt.

		verify(_serviceComponentRuntime).disableComponent(succeeding);
		verify(_serviceComponentRuntime).enableComponent(succeeding);
	}

	@Test
	public void aRebindThatDoesNotClearTheBypassReturnsTheStillBypassingComponents() {

		// Every call the production code makes to findBypassing sees the
		// same bypassing component, as if disable/enable had no effect
		// (DESIGN.md 3.1: a reference whose target filter is itself wrong,
		// for instance). The returned value is exactly what the production
		// code's restart warning is gated on, so asserting on it is what
		// makes this test able to fail if that gate breaks.

		ComponentDescriptionDTO description = _bypassingEntryPointComponent();

		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of(description)
		);
		when(
			_serviceComponentRuntime.disableComponent(description)
		).thenReturn(
			Promises.resolved(null)
		);
		when(
			_serviceComponentRuntime.enableComponent(description)
		).thenReturn(
			Promises.resolved(null)
		);

		List<ComponentDescriptionDTO> stillBypassing =
			_searchConsumerRebinder.rebind();

		assertEquals(List.of(description), stillBypassing);
		verify(_serviceComponentRuntime).disableComponent(description);
		verify(_serviceComponentRuntime).enableComponent(description);
	}

	@Test
	public void aRebindThatClearsTheBypassReturnsNoStillBypassingComponents() {
		ComponentDescriptionDTO description = _bypassingEntryPointComponent();

		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of(description)
		).thenReturn(
			List.of()
		);
		when(
			_serviceComponentRuntime.disableComponent(description)
		).thenReturn(
			Promises.resolved(null)
		);
		when(
			_serviceComponentRuntime.enableComponent(description)
		).thenReturn(
			Promises.resolved(null)
		);

		assertEquals(List.of(), _searchConsumerRebinder.rebind());
	}

	/**
	 * TO-144: during a redeploy the component registry read can throw while
	 * SCR is still settling (seen once in CI as an NPE inside Felix SCR on
	 * a reinstall). One failed attempt must not end the rebind; the next
	 * attempt has to do the work.
	 */
	@Test
	public void aRegistryReadFailureIsRetriedAndTheNextAttemptRebinds()
		throws Exception {

		ComponentDescriptionDTO description = _bypassingEntryPointComponent();

		TestInjection.set(_searchConsumerRebinder, "_retryDelayMillis", 0L);

		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenThrow(
			new IllegalStateException("registry unreadable")
		).thenReturn(
			List.of(description)
		).thenReturn(
			List.of()
		);
		when(
			_serviceComponentRuntime.disableComponent(description)
		).thenReturn(
			Promises.resolved(null)
		);
		when(
			_serviceComponentRuntime.enableComponent(description)
		).thenReturn(
			Promises.resolved(null)
		);

		_searchConsumerRebinder.rebindWithRetries();

		verify(_serviceComponentRuntime).disableComponent(description);
		verify(_serviceComponentRuntime).enableComponent(description);
	}

	/**
	 * A registry that never becomes readable ends after the fixed number of
	 * attempts, without throwing off the thread, leaving the warning and the
	 * admin screen to report it.
	 */
	@Test
	public void aPersistentRegistryFailureGivesUpAfterFiveAttempts()
		throws Exception {

		TestInjection.set(_searchConsumerRebinder, "_retryDelayMillis", 0L);

		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenThrow(
			new IllegalStateException("registry unreadable")
		);

		_searchConsumerRebinder.rebindWithRetries();

		verify(
			_serviceComponentRuntime, times(5)
		).getComponentDescriptionDTOs(
			any(Bundle[].class)
		);
		verify(_serviceComponentRuntime, never()).disableComponent(any());
	}

	/**
	 * A component this loop disabled but could not re-enable is invisible to
	 * the next registry read, so the loop has to remember it and re-enable
	 * it itself on the next attempt, rather than conclude the run was clean.
	 */
	@Test
	public void aFailedReenableIsRetriedOnTheNextAttempt() throws Exception {
		ComponentDescriptionDTO description = _bypassingEntryPointComponent();

		TestInjection.set(_searchConsumerRebinder, "_retryDelayMillis", 0L);

		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of(description)
		).thenReturn(
			List.of()
		);
		when(
			_serviceComponentRuntime.disableComponent(description)
		).thenReturn(
			Promises.resolved(null)
		);
		when(
			_serviceComponentRuntime.enableComponent(description)
		).thenThrow(
			new IllegalStateException("enable failed")
		).thenReturn(
			Promises.resolved(null)
		);

		_searchConsumerRebinder.rebindWithRetries();

		verify(_serviceComponentRuntime).disableComponent(description);
		verify(
			_serviceComponentRuntime, times(2)
		).enableComponent(
			description
		);
	}

	@Test
	public void aPersistentlyFailingReenableGivesUpAfterFiveAttempts()
		throws Exception {

		ComponentDescriptionDTO description = _bypassingEntryPointComponent();

		TestInjection.set(_searchConsumerRebinder, "_retryDelayMillis", 0L);

		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of(description)
		).thenReturn(
			List.of()
		);
		when(
			_serviceComponentRuntime.disableComponent(description)
		).thenReturn(
			Promises.resolved(null)
		);
		when(
			_serviceComponentRuntime.enableComponent(description)
		).thenThrow(
			new IllegalStateException("enable failed")
		);

		_searchConsumerRebinder.rebindWithRetries();

		verify(
			_serviceComponentRuntime, times(5)
		).enableComponent(
			description
		);
		verify(
			_searchInterceptionStatusImpl
		).recordFailedToReenable(
			eq(List.of(description))
		);
	}

	/**
	 * An attempt that completes but still finds the entry point bypassing is
	 * not repeated: another bounce would resolve the same reference to the
	 * same service, and each one costs a few failed searches.
	 */
	@Test
	public void aCompletedAttemptThatStillFindsABypassIsNotRetried()
		throws Exception {

		ComponentDescriptionDTO description = _bypassingEntryPointComponent();

		TestInjection.set(_searchConsumerRebinder, "_retryDelayMillis", 0L);

		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of(description)
		);
		when(
			_serviceComponentRuntime.disableComponent(description)
		).thenReturn(
			Promises.resolved(null)
		);
		when(
			_serviceComponentRuntime.enableComponent(description)
		).thenReturn(
			Promises.resolved(null)
		);

		_searchConsumerRebinder.rebindWithRetries();

		verify(_serviceComponentRuntime).disableComponent(description);
		verify(
			_serviceComponentRuntime, times(2)
		).getComponentDescriptionDTOs(
			any(Bundle[].class)
		);
	}

	@Test
	public void aCleanFirstAttemptMakesNoFurtherReads() throws Exception {
		TestInjection.set(_searchConsumerRebinder, "_retryDelayMillis", 0L);

		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenReturn(
			List.of()
		);

		_searchConsumerRebinder.rebindWithRetries();

		verify(
			_serviceComponentRuntime, times(2)
		).getComponentDescriptionDTOs(
			any(Bundle[].class)
		);
	}

	@Test
	public void aDeactivatedRebinderMakesNoAttempt() throws Exception {
		TestInjection.set(_searchConsumerRebinder, "_deactivated", true);

		_searchConsumerRebinder.rebindWithRetries();

		verify(
			_serviceComponentRuntime, never()
		).getComponentDescriptionDTOs(
			any(Bundle[].class)
		);
	}

	/**
	 * A single attempt lets the failure propagate: that is how the retry
	 * loop tells a failed attempt from a clean one.
	 */
	@Test
	public void aSingleAttemptPropagatesARegistryReadFailure() {
		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenThrow(
			new IllegalStateException("registry unreadable")
		);

		assertThrows(
			IllegalStateException.class, _searchConsumerRebinder::rebind);
	}

	@Test
	public void activateDoesNotBlockWaitingForTheRebind() throws Exception {
		CountDownLatch rebindStarted = new CountDownLatch(1);
		CountDownLatch rebindMayFinish = new CountDownLatch(1);

		when(
			_serviceComponentRuntime.getComponentDescriptionDTOs(
				any(Bundle[].class))
		).thenAnswer(
			invocation -> {
				rebindStarted.countDown();

				rebindMayFinish.await(5, TimeUnit.SECONDS);

				return List.of();
			}
		);

		long startTime = System.nanoTime();

		_searchConsumerRebinder.activate(_bundleContext);

		long elapsedMillis = (System.nanoTime() - startTime) / 1000000;

		assertTrue(
			elapsedMillis < 1000,
			"activate() blocked waiting for the rebind instead of "
				+ "returning immediately");
		assertTrue(
			rebindStarted.await(2, TimeUnit.SECONDS),
			"the rebind never started on its own thread");

		rebindMayFinish.countDown();
	}

	private BundleContext _bundleContext(String... symbolicNames) {
		Bundle[] bundles = new Bundle[symbolicNames.length];

		for (int i = 0; i < symbolicNames.length; i++) {
			Bundle bundle = mock(Bundle.class);

			when(bundle.getSymbolicName()).thenReturn(symbolicNames[i]);

			bundles[i] = bundle;
		}

		BundleContext bundleContext = mock(BundleContext.class);

		when(bundleContext.getBundles()).thenReturn(bundles);

		return bundleContext;
	}

	private ComponentDescriptionDTO _bypassingEntryPointComponent() {
		return _bypassingEntryPointComponent("SearchDisplayContextFactoryImpl");
	}

	private ComponentDescriptionDTO _bypassingEntryPointComponent(
		String name) {

		ComponentDescriptionDTO description = new ComponentDescriptionDTO();

		description.name = name;

		ReferenceDTO reference = new ReferenceDTO();

		reference.name = "_searcher";
		reference.interfaceName = Searcher.class.getName();
		reference.policy = "static";

		description.references = new ReferenceDTO[] {reference};

		ServiceReferenceDTO boundService = new ServiceReferenceDTO();

		boundService.properties = new HashMap<>();

		SatisfiedReferenceDTO satisfiedReference =
			new SatisfiedReferenceDTO();

		satisfiedReference.name = "_searcher";
		satisfiedReference.boundServices =
			new ServiceReferenceDTO[] {boundService};

		ComponentConfigurationDTO configuration =
			new ComponentConfigurationDTO();

		configuration.satisfiedReferences =
			new SatisfiedReferenceDTO[] {satisfiedReference};

		when(
			_serviceComponentRuntime.getComponentConfigurationDTOs(
				description)
		).thenReturn(
			List.of(configuration)
		);

		return description;
	}

	private BundleContext _bundleContext;
	private final SearchConsumerRebinder _searchConsumerRebinder =
		new SearchConsumerRebinder();
	private SearchInterceptionStatusImpl _searchInterceptionStatusImpl;
	private ServiceComponentRuntime _serviceComponentRuntime;

}
