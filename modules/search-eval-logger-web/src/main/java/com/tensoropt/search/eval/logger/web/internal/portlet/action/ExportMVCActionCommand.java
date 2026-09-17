/**
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tensoropt.search.eval.logger.web.internal.portlet.action;

import com.liferay.portal.kernel.backgroundtask.BackgroundTaskManagerUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.BaseMVCActionCommand;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCActionCommand;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextFactory;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;

import com.tensoropt.search.eval.logger.web.internal.constants.SearchEvalLoggerPortletKeys;
import com.tensoropt.search.eval.logger.web.internal.security.permission.resource.SearchEvalLoggerPortletPermission;

import java.io.Serializable;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;

import org.osgi.service.component.annotations.Component;

/**
 * Starts an export (DESIGN.md 6.1).
 *
 * <p>
 * Manual and explicit: nothing leaves the customer's infrastructure without a
 * person asking for it (D5). The permission is checked here as well as in the
 * screen that renders the button, because hiding a control is presentation, not
 * access control.
 * </p>
 */
@Component(
	property = {
		"javax.portlet.name=" + SearchEvalLoggerPortletKeys.PORTLET_NAME,
		"mvc.command.name=" + SearchEvalLoggerPortletKeys.MVC_COMMAND_NAME_EXPORT
	},
	service = MVCActionCommand.class
)
public class ExportMVCActionCommand extends BaseMVCActionCommand {

	@Override
	protected void doProcessAction(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)actionRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		if (!SearchEvalLoggerPortletPermission.contains(
				themeDisplay, SearchEvalLoggerPortletKeys.ACTION_EXPORT)) {

			throw new PrincipalException.MustHavePermission(
				themeDisplay.getUserId(),
				SearchEvalLoggerPortletKeys.ACTION_EXPORT);
		}

		Date startDate = null;
		Date endDate = null;

		try {
			startDate = _getDate(
				ParamUtil.getString(actionRequest, "startDate"), false);
			endDate = _getDate(
				ParamUtil.getString(actionRequest, "endDate"), true);
		}
		catch (DateTimeParseException dateTimeParseException) {
			SessionErrors.add(actionRequest, "invalid-date-range");

			return;
		}

		if ((startDate != null) && (endDate != null) &&
			endDate.before(startDate)) {

			SessionErrors.add(actionRequest, "invalid-date-range");

			return;
		}

		Map<String, Serializable> taskContextMap = new HashMap<>();

		taskContextMap.put(
			SearchEvalLoggerPortletKeys.TASK_CONTEXT_START_TIME,
			(startDate == null) ? 0L : startDate.getTime());
		taskContextMap.put(
			SearchEvalLoggerPortletKeys.TASK_CONTEXT_END_TIME,
			(endDate == null) ? 0L : endDate.getTime());

		ServiceContext serviceContext = ServiceContextFactory.getInstance(
			actionRequest);

		BackgroundTaskManagerUtil.addBackgroundTask(
			themeDisplay.getUserId(), themeDisplay.getScopeGroupId(),
			_getName(startDate, endDate),
			SearchEvalLoggerPortletKeys.BACKGROUND_TASK_EXECUTOR_CLASS_NAME,
			taskContextMap, serviceContext);

		SessionMessages.add(actionRequest, "export-started");
	}

	/**
	 * Interprets the submitted day in UTC, and treats the end day as exclusive
	 * by taking the start of the following day, so an export "to the 14th"
	 * includes everything logged on the 14th. Events are stored in UTC, so
	 * reading the range in any other zone would silently shift the window.
	 */
	private Date _getDate(String value, boolean exclusiveEnd) {
		if (Validator.isNull(value)) {
			return null;
		}

		LocalDate localDate = LocalDate.parse(value.trim());

		if (exclusiveEnd) {
			localDate = localDate.plusDays(1);
		}

		return Date.from(localDate.atStartOfDay(ZoneOffset.UTC).toInstant());
	}

	private String _getName(Date startDate, Date endDate) {
		return "search-eval-export-" + ((startDate == null) ? "all" : startDate) +
			"-" + ((endDate == null) ? "all" : endDate);
	}

}
