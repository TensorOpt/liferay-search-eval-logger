<%--
SPDX-License-Identifier: Apache-2.0
--%>

<%@ include file="/init.jsp" %>

<%
boolean hasExportPermission = false;

try {
	hasExportPermission = SearchEvalLoggerPortletPermission.contains(
		themeDisplay, SearchEvalLoggerPortletKeys.ACTION_EXPORT);
}
catch (Exception exception) {
	hasExportPermission = false;
}

List<BackgroundTask> backgroundTasks =
	BackgroundTaskManagerUtil.getBackgroundTasks(
		themeDisplay.getScopeGroupId(),
		SearchEvalLoggerPortletKeys.BACKGROUND_TASK_EXECUTOR_CLASS_NAME, 0, 20,
		null);

SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

Map<Long, String> backgroundTaskCreateDates = new HashMap<>();

for (BackgroundTask backgroundTask : backgroundTasks) {
	backgroundTaskCreateDates.put(
		backgroundTask.getBackgroundTaskId(),
		simpleDateFormat.format(backgroundTask.getCreateDate()));
}

request.setAttribute("backgroundTasks", backgroundTasks);
request.setAttribute("backgroundTaskCreateDates", backgroundTaskCreateDates);
request.setAttribute("hasExportPermission", hasExportPermission);
%>

<liferay-ui:success key="export-started" message="export-started" />

<liferay-ui:error key="invalid-date-range" message="invalid-date-range" />

<div class="container-fluid-1280">
	<div class="alert alert-info">
		<liferay-ui:message key="collection-settings-note" />
	</div>

	<c:choose>
		<c:when test="${not hasExportPermission}">
			<div class="alert alert-warning">
				<liferay-ui:message key="you-do-not-have-permission-to-export" />
			</div>
		</c:when>
		<c:otherwise>
			<h3><liferay-ui:message key="run-export" /></h3>

			<p><liferay-ui:message key="export-description" /></p>

			<portlet:actionURL
				name="<%= SearchEvalLoggerPortletKeys.MVC_COMMAND_NAME_EXPORT %>"
				var="exportURL"
			/>

			<aui:form action="<%= exportURL %>" method="post" name="fm">
				<aui:fieldset>
					<aui:input label="start-date" name="startDate" placeholder="YYYY-MM-DD" type="text" />

					<aui:input label="end-date" name="endDate" placeholder="YYYY-MM-DD" type="text" />

					<p class="text-muted"><liferay-ui:message key="date-format-help" /></p>
				</aui:fieldset>

				<aui:button-row>
					<aui:button type="submit" value="export" />
				</aui:button-row>
			</aui:form>
		</c:otherwise>
	</c:choose>

	<h3><liferay-ui:message key="recent-exports" /></h3>

	<c:choose>
		<c:when test="${empty backgroundTasks}">
			<div class="alert alert-info">
				<liferay-ui:message key="no-exports-yet" />
			</div>
		</c:when>
		<c:otherwise>
			<table class="table table-autofit table-hover table-list">
				<thead>
					<tr>
						<th><liferay-ui:message key="started" /></th>
						<th><liferay-ui:message key="status" /></th>
						<th><liferay-ui:message key="download" /></th>
					</tr>
				</thead>
				<tbody>
					<c:forEach items="${backgroundTasks}" var="backgroundTask">
						<tr>
							<td>
								${backgroundTaskCreateDates[backgroundTask.backgroundTaskId]}
							</td>
							<td>
								<liferay-ui:message key="${backgroundTask.statusLabel}" />
							</td>
							<td>
								<c:choose>
									<c:when test="${backgroundTask.completed and hasExportPermission}">
										<portlet:resourceURL var="downloadURL">
											<portlet:param name="backgroundTaskId" value="${backgroundTask.backgroundTaskId}" />
										</portlet:resourceURL>

										<a href="${downloadURL}"><liferay-ui:message key="download" /></a>
									</c:when>
									<c:otherwise>
										&mdash;
									</c:otherwise>
								</c:choose>
							</td>
						</tr>
					</c:forEach>
				</tbody>
			</table>
		</c:otherwise>
	</c:choose>
</div>
