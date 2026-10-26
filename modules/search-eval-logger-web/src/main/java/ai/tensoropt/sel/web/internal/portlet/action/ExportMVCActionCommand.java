/**
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.tensoropt.sel.web.internal.portlet.action;

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

import ai.tensoropt.sel.web.internal.constants.SearchEvalLoggerPortletKeys;
import ai.tensoropt.sel.web.internal.export.ExportArchiveNames;
import ai.tensoropt.sel.web.internal.export.ExportTaskContext;
import ai.tensoropt.sel.web.internal.security.permission.resource.SearchEvalLoggerPortletPermission;

import java.io.Serializable;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import jakarta.portlet.ActionRequest;
import jakarta.portlet.ActionResponse;

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
		"jakarta.portlet.name=" + SearchEvalLoggerPortletKeys.PORTLET_NAME,
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

		Date[] dateRange;

		try {
			dateRange = parseDateRange(
				ParamUtil.getString(actionRequest, "startDate"),
				ParamUtil.getString(actionRequest, "endDate"));
		}
		catch (DateTimeParseException | IllegalArgumentException exception) {
			SessionErrors.add(actionRequest, "invalid-date-range");

			return;
		}

		Date startDate = dateRange[0];
		Date endDate = dateRange[1];

		Map<String, Serializable> taskContextMap = new HashMap<>();

		ExportTaskContext.putRange(taskContextMap, startDate, endDate);

		ServiceContext serviceContext = ServiceContextFactory.getInstance(
			actionRequest);

		BackgroundTaskManagerUtil.addBackgroundTask(
			themeDisplay.getUserId(), themeDisplay.getScopeGroupId(),
			ExportArchiveNames.getTaskName(startDate, endDate),
			SearchEvalLoggerPortletKeys.BACKGROUND_TASK_EXECUTOR_CLASS_NAME,
			taskContextMap, serviceContext);

		SessionMessages.add(actionRequest, "export-started");
	}

	/**
	 * Parses both bounds and validates the range they form.
	 *
	 * <p>
	 * Validation happens here, against the same exclusive end bound the rest
	 * of the export path uses, rather than against the raw submitted day: the
	 * exclusive end of a one-day range (start and end submitted as the same
	 * day) lands one day after the start, so comparing it with
	 * {@link Date#before} alone would also accept a submitted end one day
	 * <em>before</em> the start, since its exclusive bound then equals the
	 * start exactly and "before" is false for equal instants. Rejecting
	 * anything whose exclusive end does not come after the start catches that
	 * case while still accepting the equal-day one.
	 * </p>
	 *
	 * <p>
	 * Package-private so the validation can be exercised without an action
	 * command.
	 * </p>
	 *
	 * @throws DateTimeParseException if either bound does not parse
	 * @throws IllegalArgumentException if the range is empty or reversed
	 */
	static Date[] parseDateRange(String startDateString, String endDateString) {
		Date startDate = _parseDate(startDateString, false);
		Date endDate = _parseDate(endDateString, true);

		if ((startDate != null) && (endDate != null) &&
			!endDate.after(startDate)) {

			throw new IllegalArgumentException(
				"endDate " + endDateString + " is before startDate " +
					startDateString);
		}

		return new Date[] {startDate, endDate};
	}

	/**
	 * Interprets the submitted day in UTC, and treats the end day as exclusive
	 * by taking the start of the following day, so an export "to the 14th"
	 * includes everything logged on the 14th. Events are stored in UTC, so
	 * reading the range in any other zone would silently shift the window.
	 */
	private static Date _parseDate(String value, boolean exclusiveEnd) {
		if (Validator.isNull(value)) {
			return null;
		}

		LocalDate localDate = LocalDate.parse(value.trim());

		if (exclusiveEnd) {
			localDate = localDate.plusDays(1);
		}

		return Date.from(localDate.atStartOfDay(ZoneOffset.UTC).toInstant());
	}

}
