<%--
SPDX-License-Identifier: Apache-2.0
--%>

<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%@ taglib uri="http://liferay.com/tld/aui" prefix="aui" %>
<%@ taglib uri="http://liferay.com/tld/theme" prefix="liferay-theme" %>
<%@ taglib uri="http://liferay.com/tld/ui" prefix="liferay-ui" %>

<%@ page import="com.liferay.portal.kernel.backgroundtask.BackgroundTask" %>
<%@ page import="com.liferay.portal.kernel.backgroundtask.BackgroundTaskManagerUtil" %>
<%@ page import="com.tensoropt.search.eval.logger.web.internal.constants.SearchEvalLoggerPortletKeys" %>
<%@ page import="com.tensoropt.search.eval.logger.web.internal.security.permission.resource.SearchEvalLoggerPortletPermission" %>

<%@ page import="java.text.SimpleDateFormat" %>

<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>

<liferay-theme:defineObjects />

<portlet:defineObjects />
