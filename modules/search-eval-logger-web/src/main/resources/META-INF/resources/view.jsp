<%--
SPDX-License-Identifier: Apache-2.0
--%>

<%@ include file="/init.jsp" %>

<div class="container-fluid container-fluid-max-xl container-view">
	<c:if test="${not intercepting}">
		<div class="alert alert-danger">
			<strong><liferay-ui:message key="restart-required" /></strong>

			<p class="mb-0"><liferay-ui:message key="restart-required-detail" /></p>
		</div>
	</c:if>

	<%--
	TO-140: a smaller, secondary note. The entry points above (the Search
	Results widget, shared search requests, search bar suggestions) rebind
	onto the wrapper automatically, without a restart; a handful of other
	consumers of the search service do not and still need one. Shown only
	once the danger alert above is already clear, so the two never both
	talk about the same restart.
	--%>

	<c:if test="${otherConsumersAwaitingRestart}">
		<div class="alert alert-info">
			<liferay-ui:message key="secondary-consumers-restart-note" />
		</div>
	</c:if>

	<%--
	The readiness state of DESIGN.md 3.6, rendered here whether or not the
	notification that announces it was delivered. EC-14: delivery is confirmed;
	the admin screen remains the channel that does not depend on it, since the
	banner reads the cycle record the daily job writes, not the notification.
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

	<div class="alert alert-info">
		<liferay-ui:message key="collection-settings-note" />

		<c:if test="${not empty collectionStartDate}">
			<p class="mb-0">
				<liferay-ui:message arguments="${collectionStartDate}" key="collecting-since-x" />
			</p>
		</c:if>
	</div>

	<%--
	DESIGN.md 10.1/10.2 (TO-112). The signup banner replaces the plain inline
	link: same visibility rule (not shown until a collection start date exists
	and the bypass warning above is clear), plus a local dismiss control.
	Clicking the link or the dismiss control both hide the banner for the rest
	of this cycle; see SignupBannerDismissal. Nothing about either action is
	transmitted anywhere - the fetch below targets this portlet's own resource
	URL, same origin, nothing else.

	The anchor itself: a plain link, opened by a person, in a new tab. No
	prefetch, no preload, no iframe, no image, no script contacts
	${collectionStartURL} until somebody clicks it (D9). rel="noopener
	noreferrer" so the new tab gets no handle on this window and no referrer:
	the target learns that someone clicked, and nothing about where from
	beyond the UTM tags in the address itself.
	--%>

	<c:if test="${showSignupBanner}">
		<portlet:resourceURL id="<%= SearchEvalLoggerPortletKeys.RESOURCE_ID_DISMISS_SIGNUP_BANNER %>" var="dismissSignupBannerURL" />

		<div class="alert alert-info" id="<portlet:namespace />selSignupBanner">
			<p>
				<liferay-ui:message key="signup-banner-benefit" />
			</p>

			<p class="mb-0">
				<a href="${collectionStartURL}" id="<portlet:namespace />selSignupBannerLink" rel="noopener noreferrer" target="_blank">
					<liferay-ui:message key="collection-start-link" />
				</a>

				&#160;&#8226;&#160;

				<button class="btn btn-link" id="<portlet:namespace />selSignupBannerDismiss" type="button">
					<liferay-ui:message key="dismiss" />
				</button>
			</p>

			<p class="text-muted small mb-0">
				<liferay-ui:message key="signup-banner-destination-note" />
			</p>
		</div>

		<%--
		aui:script rather than a plain script tag: Liferay's own tag renders
		it with whatever CSP nonce the request needs, which a literal
		<script> block does not get. The fetch below is same origin - this
		portlet's own resource URL - which is what keeps it on the right side
		of D9; nothing here ever references an external host.
		--%>

		<aui:script>
			(function() {
				var banner = document.getElementById('<portlet:namespace />selSignupBanner');

				function dismiss() {
					if (banner) {
						banner.style.display = 'none';
					}

					try {
						fetch(
							'${dismissSignupBannerURL}',
							{credentials: 'same-origin', method: 'POST'}
						).catch(function() {});
					}
					catch (error) {
					}
				}

				var link = document.getElementById('<portlet:namespace />selSignupBannerLink');

				if (link) {
					link.addEventListener('click', dismiss);
				}

				var dismissButton = document.getElementById('<portlet:namespace />selSignupBannerDismiss');

				if (dismissButton) {
					dismissButton.addEventListener('click', dismiss);
				}
			})();
		</aui:script>
	</c:if>

	<div class="sheet">
		<c:choose>
			<c:when test="${not hasExportPermission}">
				<div class="alert alert-warning mb-0">
					<liferay-ui:message key="you-do-not-have-permission-to-export" />
				</div>
			</c:when>
			<c:otherwise>
				<div class="sheet-header">
					<h2 class="sheet-title"><liferay-ui:message key="run-export" /></h2>

					<div class="sheet-text"><liferay-ui:message key="export-description" /></div>
				</div>

				<portlet:actionURL
					name="<%= SearchEvalLoggerPortletKeys.MVC_COMMAND_NAME_EXPORT %>"
					var="exportURL"
				/>

				<aui:form action="<%= exportURL %>" method="post" name="fm">
					<div class="row">
						<div class="col-md-6">
							<aui:input label="start-date" name="startDate" placeholder="YYYY-MM-DD" type="text" />
						</div>

						<div class="col-md-6">
							<aui:input label="end-date" name="endDate" placeholder="YYYY-MM-DD" type="text" />
						</div>
					</div>

					<p class="text-muted"><liferay-ui:message key="date-format-help" /></p>

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
			<p class="mb-0 mt-3">
				<a href="${exportCompleteURL}" rel="noopener noreferrer" target="_blank">
					<liferay-ui:message key="export-complete-link" />
				</a>
			</p>
		</c:if>
	</div>

	<%--
	DESIGN.md 6 (TO-135): computed from the existing log only, for the whole
	retention window (there is no date picker here, unlike the export above;
	the retention purge already bounds what the log holds), and cached for up
	to an hour per virtual instance (ZeroResultPanelLoader), so this render
	may show a figure computed on an earlier one. Nothing new is collected and
	nothing is sent anywhere. Gated on "zeroResultPanel" being present, not directly on
	hasExportPermission: the attribute is only ever set when the viewer holds
	that permission (see SearchEvalLoggerPortlet#doView), so a viewer without
	it still cannot reach this sheet's data by any route, not only by the
	JSP hiding it, but the same absent-attribute path also covers
	ZeroResultPanelLoader failing for a permitted viewer (a timeout, an
	unsupported query on some database). Gating on hasExportPermission alone
	would render this sheet's heading and an empty "{0} of {1} searches
	({2}%)..." - liferay-ui:message falls back to the bare key text when its
	arguments are null - for exactly that failure case, which is worse than
	the sheet simply not being there.
	--%>

	<c:if test="${not empty zeroResultPanel}">
		<div class="sheet">
			<div class="sheet-header">
				<h2 class="sheet-title"><liferay-ui:message key="zero-result-title" /></h2>
			</div>

			<c:choose>
				<c:when test="${zeroResultPanel.noSearchesLogged}">
					<p class="mb-0"><liferay-ui:message key="zero-result-no-searches-yet" /></p>
				</c:when>
				<c:otherwise>
					<p>
						<liferay-ui:message
							arguments="${zeroResultPanel.overallMessageArguments}"
							key="zero-result-overall"
						/>
					</p>

					<p>
						<liferay-ui:message
							arguments="${zeroResultPanel.guestMessageArguments}"
							key="zero-result-guests"
						/>

						<br />

						<liferay-ui:message
							arguments="${zeroResultPanel.signedInMessageArguments}"
							key="zero-result-signed-in-users"
						/>
					</p>

					<c:if test="${not empty zeroResultPanel.topQueries}">
						<table class="table table-autofit table-list">
							<thead>
								<tr>
									<th><liferay-ui:message key="zero-result-query" /></th>
									<th><liferay-ui:message key="zero-result-searches" /></th>
								</tr>
							</thead>
							<tbody>
								<c:forEach items="${zeroResultPanel.topQueries}" var="zeroResultQuery">
									<tr>
										<td><c:out value="${zeroResultQuery.query}" /></td>
										<td>${zeroResultQuery.searchCount}</td>
									</tr>
								</c:forEach>
							</tbody>
						</table>

						<p class="text-muted small mb-0">
							<liferay-ui:message key="zero-result-grouping-note" />
						</p>

						<c:if test="${zeroResultPanel.topQueriesApproximate}">
							<p class="text-muted small mb-0">
								<liferay-ui:message key="zero-result-approximate-note" />
							</p>
						</c:if>
					</c:if>
				</c:otherwise>
			</c:choose>
		</div>
	</c:if>

	<div class="sheet">
		<div class="sheet-header">
			<h2 class="sheet-title"><liferay-ui:message key="admission-counters" /></h2>

			<div class="sheet-text"><liferay-ui:message key="admission-counters-help" /></div>
		</div>

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
	</div>

	<div class="sheet">
		<div class="sheet-header">
			<h2 class="sheet-title"><liferay-ui:message key="recent-exports" /></h2>
		</div>

		<c:choose>
			<c:when test="${empty exportRows}">
				<div class="alert alert-info mb-0">
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
</div>
