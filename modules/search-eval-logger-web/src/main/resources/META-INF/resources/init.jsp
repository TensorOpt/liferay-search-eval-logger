<%--
SPDX-License-Identifier: Apache-2.0
--%>

<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%@ taglib uri="http://liferay.com/tld/aui" prefix="aui" %>
<%@ taglib uri="http://liferay.com/tld/theme" prefix="liferay-theme" %>
<%@ taglib uri="http://liferay.com/tld/ui" prefix="liferay-ui" %>

<%@ page import="com.liferay.portal.background.task.util.comparator.BackgroundTaskCreateDateComparator" %>
<%@ page import="com.liferay.portal.kernel.backgroundtask.BackgroundTask" %>
<%@ page import="com.liferay.portal.kernel.backgroundtask.BackgroundTaskConstants" %>
<%@ page import="com.liferay.portal.kernel.backgroundtask.BackgroundTaskManagerUtil" %>
<%@ page import="ai.tensoropt.sel.web.internal.constants.SearchEvalLoggerPortletKeys" %>
<%@ page import="ai.tensoropt.sel.web.internal.security.permission.resource.SearchEvalLoggerPortletPermission" %>

<%@ page import="java.text.SimpleDateFormat" %>

<%@ page import="java.util.Date" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>

<liferay-theme:defineObjects />

<portlet:defineObjects />
