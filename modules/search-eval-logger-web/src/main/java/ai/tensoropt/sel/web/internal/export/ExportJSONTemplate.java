/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.export;

import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

/**
 * The one way this plugin starts a JSON object from a schema template.
 *
 * <p>
 * Liferay's JSONObject delegates to a library that <em>removes</em> a key
 * whose value is null, so a field that is absent for this row disappears from
 * the object rather than appearing as null, and the schema then varies line by
 * line. Parsing a template that spells the nulls out is the way round that,
 * and both the JSONL writer and the manifest builder need it, so the device
 * lives here rather than being written twice with two sets of failure
 * behaviour.
 * </p>
 *
 * <p>
 * A template is a compile-time constant, so the fallback below is unreachable
 * short of someone editing one into invalid JSON. It degrades to an object
 * with no keys at all, which for the manifest's export range is the shape that
 * D-1's fix removed, so the warning is logged rather than swallowed.
 * </p>
 */
public class ExportJSONTemplate {

	public static JSONObject create(JSONFactory jsonFactory, String template) {
		try {
			return jsonFactory.createJSONObject(template);
		}
		catch (Exception exception) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to parse the JSON template " + template, exception);
			}

			return jsonFactory.createJSONObject();
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		ExportJSONTemplate.class);

}
