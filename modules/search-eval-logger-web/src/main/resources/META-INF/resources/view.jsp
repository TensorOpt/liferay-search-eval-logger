<%--
SPDX-License-Identifier: Apache-2.0
--%>

<%@ include file="/init.jsp" %>

<c:if test="${not intercepting}">
	<div class="alert alert-danger">
		<strong><liferay-ui:message key="restart-required" /></strong>

		<p class="mb-0"><liferay-ui:message key="restart-required-detail" /></p>
	</div>
</c:if>

<%--
The readiness state of DESIGN.md 3.6, rendered here whether or not the
notification that announces it was delivered. EC-14 leaves that open, and this
screen is the fallback it names: the banner reads the cycle record the daily
job writes, not the notification.
--%>

<c:if test="${not empty readinessReachedDate}">
	<div class="alert alert-success">
		<strong><liferay-ui:message key="collection-ready" /></strong>

		<p class="mb-0">
			<liferay-ui:message arguments="${readinessReachedDate}" key="collection-ready-detail" />
		</p>
	</div>
</c:if>

<liferay-ui:success key="export-started" message="export-started" />

<liferay-ui:error key="invalid-date-range" message="invalid-date-range" />

<div class="container-fluid-1280">
	<div class="alert alert-info">
		<liferay-ui:message key="collection-settings-note" />

		<c:if test="${not empty collectionStartDate}">
			<p class="mb-0">
				<liferay-ui:message arguments="${collectionStartDate}" key="collecting-since-x" />
			</p>
		</c:if>
	</div>

	<%--
	DESIGN.md 10.1. A plain anchor, opened by a person, in a new tab. No
	prefetch, no preload, no iframe, no image, no script: nothing here or
	anywhere else in this plugin contacts this address until somebody clicks it
	(D9). It is rendered only once a collection start date exists, so the date
	it carries is the date collection actually began, and it is suppressed
	while the bypass warning above is showing.

	rel="noopener noreferrer" so the new tab gets no handle on this window and
	no referrer: the target learns that someone clicked, and nothing about
	where from beyond the UTM tags in the address itself.
	--%>

	<c:if test="${not empty collectionStartURL}">
		<p>
			<a href="${collectionStartURL}" rel="noopener noreferrer" target="_blank">
				<liferay-ui:message key="collection-start-link" />
			</a>
		</p>
	</c:if>

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

	<%--
	DESIGN.md 10.3, under the same rules as the link above: a plain anchor,
	carrying the UTM tags and nothing about the dataset. Neither link appears
	inside the archive (10.4); the manifest, the archive README and
	events.jsonl stay vendor-neutral.
	--%>

	<c:if test="${showExportCompleteLink}">
		<p>
			<a href="${exportCompleteURL}" rel="noopener noreferrer" target="_blank">
				<liferay-ui:message key="export-complete-link" />
			</a>
		</p>
	</c:if>

	<h3><liferay-ui:message key="admission-counters" /></h3>

	<p class="text-muted"><liferay-ui:message key="admission-counters-help" /></p>

	<table class="table table-autofit table-list">
		<tbody>
			<tr>
				<td><liferay-ui:message key="searches-observed" /></td>
				<td>${statistics.observedSearchCount}</td>
			</tr>
			<tr>
				<td><liferay-ui:message key="searches-with-keywords" /></td>
				<td>${statistics.keywordSearchCount}</td>
			</tr>
			<tr>
				<td><liferay-ui:message key="searches-admitted" /></td>
				<td>${statistics.admittedSearchCount}</td>
			</tr>
			<tr>
				<td><liferay-ui:message key="events-dispatched" /></td>
				<td>${statistics.dispatchedEventCount}</td>
			</tr>
			<tr>
				<td><liferay-ui:message key="events-dropped" /></td>
				<td>${statistics.droppedEventCount}</td>
			</tr>
			<tr>
				<td><liferay-ui:message key="events-persisted" /></td>
				<td>${statistics.persistedEventCount}</td>
			</tr>
		</tbody>
	</table>

	<h3><liferay-ui:message key="recent-exports" /></h3>

	<c:choose>
		<c:when test="${empty exportRows}">
			<div class="alert alert-info">
				<liferay-ui:message key="no-exports-yet" />
			</div>
		</c:when>
		<c:otherwise>
			<table class="table table-autofit table-hover table-list">
				<thead>
					<tr>
						<th><liferay-ui:message key="started" /></th>
						<th><liferay-ui:message key="finished" /></th>
						<th><liferay-ui:message key="duration" /></th>
						<th><liferay-ui:message key="status" /></th>
						<th><liferay-ui:message key="download" /></th>
					</tr>
				</thead>
				<tbody>
					<c:forEach items="${exportRows}" var="exportRow">
						<tr>
							<td>${exportRow.createDate}</td>
							<td>${empty exportRow.completionDate ? '&mdash;' : exportRow.completionDate}</td>
							<td>${empty exportRow.duration ? '&mdash;' : exportRow.duration}</td>
							<td>
								<liferay-ui:message key="${exportRow.statusLabel}" />
							</td>
							<td>
								<c:choose>
									<c:when test="${exportRow.successful and hasExportPermission}">
										<portlet:resourceURL var="downloadURL">
											<portlet:param name="backgroundTaskId" value="${exportRow.backgroundTaskId}" />
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
