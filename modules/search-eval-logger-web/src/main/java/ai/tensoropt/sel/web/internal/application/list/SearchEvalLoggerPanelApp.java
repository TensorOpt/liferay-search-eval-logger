/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.application.list;

import com.liferay.application.list.BasePanelApp;
import com.liferay.application.list.PanelApp;
import com.liferay.application.list.constants.PanelCategoryKeys;
import com.liferay.portal.kernel.model.Portlet;

import ai.tensoropt.sel.web.internal.constants.SearchEvalLoggerPortletKeys;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Places the export screen in Control Panel, under Configuration, next to the
 * collector's own settings screen.
 */
@Component(
	property = {
		"panel.app.order:Integer=700",
		"panel.category.key=" + PanelCategoryKeys.CONTROL_PANEL_CONFIGURATION
	},
	service = PanelApp.class
)
public class SearchEvalLoggerPanelApp extends BasePanelApp {

	@Override
	public Portlet getPortlet() {
		return _portlet;
	}

	@Override
	public String getPortletId() {
		return SearchEvalLoggerPortletKeys.PORTLET_NAME;
	}

	@Reference(
		target = "(javax.portlet.name=" + SearchEvalLoggerPortletKeys.PORTLET_NAME + ")"
	)
	private Portlet _portlet;

}
