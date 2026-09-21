/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.web.internal.portlet;

import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskManagerUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.portlet.PortletResponseUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.WebKeys;

import com.tensoropt.search.eval.logger.api.SearchInterceptionStatus;
import com.tensoropt.search.eval.logger.web.internal.constants.SearchEvalLoggerPortletKeys;
import com.tensoropt.search.eval.logger.web.internal.security.permission.resource.SearchEvalLoggerPortletPermission;

import java.io.IOException;
import java.io.InputStream;

import java.util.List;

import javax.portlet.Portlet;
import javax.portlet.PortletException;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Control Panel screen for running and retrieving exports (DESIGN.md 6.1).
 *
 * <p>
 * Collection settings are not duplicated here. They are declared as an
 * <code>&#64;Meta.OCD</code> configuration scoped per virtual instance, which
 * Liferay already renders in Control Panel under Configuration, with validation
 * and per-instance scoping it would only be possible to reimplement worse. This
 * screen covers the one thing Configuration Admin cannot express: an action.
 * </p>
 *
 * <p>
 * The download is served here rather than as a direct document link so that it
 * passes through the same dedicated permission as starting an export. Nothing
 * leaves the instance without an explicit human action (D5), and the archive is
 * streamed from the attachment rather than read into memory.
 * </p>
 */
@Component(
	property = {
		"com.liferay.portlet.add-default-resource=true",
		"com.liferay.portlet.css-class-wrapper=search-eval-logger-portlet",
		"com.liferay.portlet.display-category=category.hidden",
		"com.liferay.portlet.private-request-attributes=false",
		"com.liferay.portlet.private-session-attributes=false",
		"com.liferay.portlet.render-weight=50",
		"com.liferay.portlet.scopeable=true",
		"com.liferay.portlet.use-default-template=true",
		"javax.portlet.display-name=Search Eval Logger",
		"javax.portlet.expiration-cache=0",
		"javax.portlet.init-param.template-path=/",
		"javax.portlet.init-param.view-template=/view.jsp",
		"javax.portlet.name=" + SearchEvalLoggerPortletKeys.PORTLET_NAME,
		"javax.portlet.resource-bundle=content.Language",
		"javax.portlet.security-role-ref=administrator",
		"javax.portlet.supports.mime-type=text/html",
		"javax.portlet.version=3.0"
	},
	service = Portlet.class
)
public class SearchEvalLoggerPortlet extends MVCPortlet {

	/**
	 * Interception can be registered and healthy yet never called, when the
	 * plugin was installed onto a portal whose search consumers had already
	 * bound the portal's own searcher. Collection then silently records
	 * nothing, so the state is put in front of the administrator here rather
	 * than left to be discovered as an empty export.
	 */
	@Override
	public void doView(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		renderRequest.setAttribute(
			"intercepting", _searchInterceptionStatus.isIntercepting());

		super.doView(renderRequest, renderResponse);
	}

	@Override
	public void serveResource(
			ResourceRequest resourceRequest, ResourceResponse resourceResponse)
		throws IOException, PortletException {

		try {
			_serveArchive(resourceRequest, resourceResponse);
		}
		catch (PortalException portalException) {
			throw new PortletException(portalException);
		}
	}

	private void _serveArchive(
			ResourceRequest resourceRequest, ResourceResponse resourceResponse)
		throws IOException, PortalException {

		ThemeDisplay themeDisplay = (ThemeDisplay)resourceRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		if (!SearchEvalLoggerPortletPermission.contains(
				themeDisplay, SearchEvalLoggerPortletKeys.ACTION_EXPORT)) {

			throw new PrincipalException.MustHavePermission(
				themeDisplay.getUserId(),
				SearchEvalLoggerPortletKeys.ACTION_EXPORT);
		}

		long backgroundTaskId = ParamUtil.getLong(
			resourceRequest, "backgroundTaskId");

		BackgroundTask backgroundTask =
			BackgroundTaskManagerUtil.getBackgroundTask(backgroundTaskId);

		if (backgroundTask.getCompanyId() != themeDisplay.getCompanyId()) {
			throw new PrincipalException.MustHavePermission(
				themeDisplay.getUserId(),
				SearchEvalLoggerPortletKeys.ACTION_EXPORT);
		}

		List<FileEntry> fileEntries =
			backgroundTask.getAttachmentsFileEntries();

		if (fileEntries.isEmpty()) {
			return;
		}

		FileEntry fileEntry = fileEntries.get(0);

		try (InputStream inputStream = fileEntry.getContentStream()) {
			PortletResponseUtil.sendFile(
				resourceRequest, resourceResponse, fileEntry.getFileName(),
				inputStream, ContentTypes.APPLICATION_ZIP);
		}
	}

	@Reference
	private SearchInterceptionStatus _searchInterceptionStatus;

}
