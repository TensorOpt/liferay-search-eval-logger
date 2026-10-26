/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.service.persistence.impl.constants;

/**
 * @author Brian Wing Shun Chan
 * @generated
 */
public class SELPersistenceConstants {

	public static final String BUNDLE_SYMBOLIC_NAME =
		"ai.tensoropt.sel.service";

	public static final String ORIGIN_BUNDLE_SYMBOLIC_NAME_FILTER =
		"(origin.bundle.symbolic.name=" + BUNDLE_SYMBOLIC_NAME + ")";

	public static final String SERVICE_CONFIGURATION_FILTER =
		"(&" + ORIGIN_BUNDLE_SYMBOLIC_NAME_FILTER + "(name=service))";

}
// LIFERAY-SERVICE-BUILDER-HASH:1176877650