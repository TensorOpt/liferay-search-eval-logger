/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.portlet;

import com.liferay.portal.background.task.util.comparator.BackgroundTaskCreateDateComparator;
import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskManagerUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.portlet.PortletResponseUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.WebKeys;

import ai.tensoropt.sel.api.CollectionCycle;
import ai.tensoropt.sel.api.CollectionCycleStatus;
import ai.tensoropt.sel.api.SearchEvalLoggerStatistics;
import ai.tensoropt.sel.api.SearchInterceptionStatus;
import ai.tensoropt.sel.api.SignupBannerDismissal;
import ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration;
import ai.tensoropt.sel.web.internal.constants.SearchEvalLoggerPortletKeys;
import ai.tensoropt.sel.web.internal.funnel.EvaluationServiceLinks;
import ai.tensoropt.sel.web.internal.security.permission.resource.SearchEvalLoggerPortletPermission;

import java.io.IOException;
import java.io.InputStream;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;

import javax.portlet.Portlet;
import javax.portlet.PortletException;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;

import javax.servlet.http.HttpServletResponse;

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
	 *
	 * <p>
	 * The collection cycle of DESIGN.md 3.6 is put here for the same reason,
	 * and for one more: this screen is the fallback delivery channel named in
	 * EC-14. If Liferay's notification framework turns out not to work for a
	 * plugin-originated notification, the two states it would have announced
	 * are still visible, because they are read from the same record the daily
	 * job writes rather than from whether a notification was delivered.
	 * </p>
	 */
	@Override
	public void doView(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		ThemeDisplay themeDisplay = (ThemeDisplay)renderRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		boolean intercepting = _searchInterceptionStatus.isIntercepting();

		CollectionCycle collectionCycle =
			_collectionCycleStatus.getCollectionCycle(
				themeDisplay.getCompanyId());

		SearchEvalLoggerConfiguration searchEvalLoggerConfiguration =
			_getSearchEvalLoggerConfiguration(themeDisplay.getCompanyId());

		boolean showEvaluationServiceLinks =
			(searchEvalLoggerConfiguration != null) &&
				searchEvalLoggerConfiguration.showEvaluationServiceLinks();

		renderRequest.setAttribute("intercepting", intercepting);
		renderRequest.setAttribute("statistics", _searchEvalLoggerStatistics);

		renderRequest.setAttribute(
			"collectionStartDate",
			_toString(collectionCycle.getCollectionStartDate()));
		renderRequest.setAttribute(
			"readinessReachedDate",
			_toString(collectionCycle.getReadinessNotifiedDate()));
		renderRequest.setAttribute(
			"showEvaluationServiceLinks", showEvaluationServiceLinks);

		// DESIGN.md 10.1/10.2 (TO-112): the banner replaces the plain inline
		// link, under the same visibility rule plus "not already dismissed or
		// clicked this cycle".

		boolean showSignupBanner = EvaluationServiceLinks.isSignupBannerVisible(
			showEvaluationServiceLinks, intercepting,
			collectionCycle.getCollectionStartDate(),
			collectionCycle.isSignupBannerDismissed());

		renderRequest.setAttribute("showSignupBanner", showSignupBanner);

		if (showSignupBanner) {
			LocalDate collectionStartDate =
				collectionCycle.getCollectionStartDate();

			renderRequest.setAttribute(
				"collectionStartURL",
				EvaluationServiceLinks.getCollectionStartURL(
					collectionStartDate));

			// TO-112: a cycle running long enough to have already passed its
			// own readiness threshold must not promise a reminder "around" a
			// date that has already gone by; the generic phrasing covers
			// that instead.

			LocalDate readyAroundDate = collectionStartDate.plusDays(
				searchEvalLoggerConfiguration.readinessMinimumDays());

			if (EvaluationServiceLinks.isSignupBannerDateMeaningful(
					collectionCycle.isReadinessNotified(), readyAroundDate,
					LocalDate.now(ZoneOffset.UTC))) {

				renderRequest.setAttribute(
					"signupBannerReadyAroundDate", _toString(readyAroundDate));
			}
		}

		renderRequest.setAttribute(
			"exportCompleteURL",
			EvaluationServiceLinks.getExportCompleteURL());

		renderRequest.setAttribute(
			"hasExportPermission", _hasExportPermission(themeDisplay));

		List<ExportRow> exportRows = _getExportRows(themeDisplay);

		renderRequest.setAttribute("exportRows", exportRows);

		// DESIGN.md 10.3 places the booking link on the export screen "after a
		// successful export". The rule as built: at least one of the exports
		// listed succeeded. Not "the most recent run succeeded", which would
		// make the link flicker away the moment somebody started another
		// export. The list is the most recent runs only, so an instance that
		// fails that many in a row loses the link until its next success, and
		// has a more pressing problem than a missing link.

		renderRequest.setAttribute(
			"showExportCompleteLink",
			EvaluationServiceLinks.isExportCompleteLinkVisible(
				showEvaluationServiceLinks,
				exportRows.stream(
				).anyMatch(
					ExportRow::isSuccessful
				)));

		super.doView(renderRequest, renderResponse);
	}

	@Override
	public void serveResource(
			ResourceRequest resourceRequest, ResourceResponse resourceResponse)
		throws IOException, PortletException {

		if (SearchEvalLoggerPortletKeys.RESOURCE_ID_DISMISS_SIGNUP_BANNER.
				equals(resourceRequest.getResourceID())) {

			_dismissSignupBanner(resourceRequest, resourceResponse);

			return;
		}

		try {
			_serveArchive(resourceRequest, resourceResponse);
		}
		catch (PortalException portalException) {
			throw new PortletException(portalException);
		}
	}

	/**
	 * TO-112: dismissing the signup banner, or clicking its link, both reach
	 * here. No permission check beyond ordinary portlet access, because this
	 * only hides a banner for the viewing company's administrators and touches
	 * nothing an export-specific check guards.
	 *
	 * <p>
	 * <b>POST only.</b> A resource URL is otherwise reachable by a plain GET,
	 * which is exactly what a browser's own link prefetch, a mail client's
	 * image proxy, or a passive crawler following the rendered page can issue
	 * without anybody actually clicking anything. This plugin's own
	 * <code>&#64;Component</code> property leaves the default resource weight,
	 * so nothing else here distinguishes a real click from one of those; the
	 * method is what does. <code>p_auth</code> is deliberately not also
	 * checked: Liferay does not require it for a resource request the way it
	 * does for an action (the export download resource above never has), and
	 * the only thing a forged cross-site POST here could do is hide a banner
	 * for the viewing company's own administrators - not worth the added
	 * surface for a check this plugin would be the only thing relying on.
	 * </p>
	 */
	private void _dismissSignupBanner(
		ResourceRequest resourceRequest, ResourceResponse resourceResponse) {

		if (!"POST".equalsIgnoreCase(resourceRequest.getMethod())) {
			resourceResponse.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);

			return;
		}

		ThemeDisplay themeDisplay = (ThemeDisplay)resourceRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		try {
			boolean dismissed = _signupBannerDismissal.dismiss(
				themeDisplay.getCompanyId());

			if (!dismissed) {
				resourceResponse.setStatus(
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

				if (_log.isWarnEnabled()) {
					_log.warn(
						"Unable to dismiss the signup banner for company " +
							themeDisplay.getCompanyId());
				}
			}
		}
		catch (Exception exception) {
			resourceResponse.setStatus(
				HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

			if (_log.isWarnEnabled()) {
				_log.warn("Unable to dismiss the signup banner", exception);
			}
		}
	}

	/**
	 * In the viewing user's time zone. The server's default zone, which the
	 * list used before, labels every run in a zone the reader cannot see.
	 */
	private List<ExportRow> _getExportRows(ThemeDisplay themeDisplay) {
		DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(
			"yyyy-MM-dd HH:mm"
		).withZone(
			themeDisplay.getTimeZone(
			).toZoneId()
		);

		List<ExportRow> exportRows = new ArrayList<>();

		for (BackgroundTask backgroundTask :
				BackgroundTaskManagerUtil.getBackgroundTasks(
					themeDisplay.getScopeGroupId(),
					SearchEvalLoggerPortletKeys.
						BACKGROUND_TASK_EXECUTOR_CLASS_NAME,
					0, _RECENT_EXPORTS_COUNT,
					BackgroundTaskCreateDateComparator.getInstance(false))) {

			exportRows.add(new ExportRow(backgroundTask, dateTimeFormatter));
		}

		return exportRows;
	}

	private boolean _hasExportPermission(ThemeDisplay themeDisplay) {
		try {
			return SearchEvalLoggerPortletPermission.contains(
				themeDisplay, SearchEvalLoggerPortletKeys.ACTION_EXPORT);
		}
		catch (PortalException portalException) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Unable to check the export permission", portalException);
			}

			return false;
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

		// The id comes from the request, so it can name any background task.
		// EXPORT grants this plugin's archives, not every attachment in the
		// company, such as a site export another administrator ran.

		if ((backgroundTask.getCompanyId() != themeDisplay.getCompanyId()) ||
			!SearchEvalLoggerPortletKeys.BACKGROUND_TASK_EXECUTOR_CLASS_NAME.
				equals(backgroundTask.getTaskExecutorClassName())) {
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

	/**
	 * <code>null</code> when the configuration cannot be read. The setting
	 * that hides the funnel links exists so an administrator can remove them
	 * without forking, so an unreadable configuration must not put them back;
	 * every caller here treats <code>null</code> the same way "links off"
	 * would be treated.
	 */
	private SearchEvalLoggerConfiguration _getSearchEvalLoggerConfiguration(
		long companyId) {

		try {
			return _configurationProvider.getCompanyConfiguration(
				SearchEvalLoggerConfiguration.class, companyId);
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Unable to read the search eval logger configuration for " +
						"company " + companyId,
					exception);
			}

			return null;
		}
	}

	private String _toString(LocalDate localDate) {
		if (localDate == null) {
			return null;
		}

		return localDate.toString();
	}

	private static final int _RECENT_EXPORTS_COUNT = 20;

	private static final Log _log = LogFactoryUtil.getLog(
		SearchEvalLoggerPortlet.class);

	@Reference
	private CollectionCycleStatus _collectionCycleStatus;

	@Reference
	private ConfigurationProvider _configurationProvider;

	@Reference
	private SearchEvalLoggerStatistics _searchEvalLoggerStatistics;

	@Reference
	private SearchInterceptionStatus _searchInterceptionStatus;

	@Reference
	private SignupBannerDismissal _signupBannerDismissal;

}
