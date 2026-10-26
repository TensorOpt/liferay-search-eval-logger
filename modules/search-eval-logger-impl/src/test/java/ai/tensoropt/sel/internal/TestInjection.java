/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.internal;

import com.liferay.portal.kernel.transaction.TransactionConfig;
import com.liferay.portal.kernel.transaction.TransactionInvoker;

import java.lang.reflect.Field;

import java.util.concurrent.Callable;

/**
 * Sets a private field on a target object by reflection, for a unit test that
 * has to reach a component's collaborator directly because production code
 * carries no constructor or setter for it; this bundle's components are
 * field-injected by OSGi (&#64;Reference), not constructor-injected.
 *
 * <p>
 * Shared across the impl module's test classes so that there is one copy of
 * this mechanism rather than eight near-identical private ones, each of which
 * had already started to drift (a target fixed at construction here, a
 * hardcoded field name there) across the sub-packages that needed it.
 * </p>
 */
public final class TestInjection {

	/**
	 * Stands in for the real <code>TransactionInvokerUtil</code>, whose own
	 * static invoker is unset outside a running portal, in every test that
	 * needs a component's <code>_transactionInvoker</code> field set to
	 * something that simply runs the callable, simulating a transaction that
	 * commits.
	 */
	public static final TransactionInvoker PASSTHROUGH_TRANSACTION_INVOKER =
		new TransactionInvoker() {

			@Override
			public <T> T invoke(
					TransactionConfig transactionConfig, Callable<T> callable)
				throws Throwable {

				return callable.call();
			}

		};

	/**
	 * Sets <code>target</code>'s field named <code>fieldName</code>, declared
	 * directly on <code>target</code>'s own runtime class, to
	 * <code>value</code>.
	 */
	public static void set(Object target, String fieldName, Object value)
		throws Exception {

		Field field = target.getClass().getDeclaredField(fieldName);

		field.setAccessible(true);

		field.set(target, value);
	}

	private TestInjection() {
		throw new AssertionError();
	}

}
