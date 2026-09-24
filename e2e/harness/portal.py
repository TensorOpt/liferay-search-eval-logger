# SPDX-License-Identifier: Apache-2.0
"""An HTTP client for the portal under test.

Everything the harness does to Liferay it does the way a person or a CI job
would: over HTTP, as a signed-in user, against stock endpoints. Nothing here
requires a test hook inside the plugin.

Two facilities deserve a note.

The Groovy runner drives Server Administration's script console, which is a
stock DXP administration feature. It is how the harness triggers the two daily
scheduled jobs on demand and how it reads and writes the collection cycle
record. The alternative was to add a trigger to the plugin for the tests to
call, which would have meant shipping production code that exists only for
testing.

Script output is collected from a file inside the container rather than from
the console's own rendered output. The console returns its output through the
render phase after a redirect, so reading it means parsing a 100 KB HTML page
for a value; a file written by the script and read back with docker exec has
one failure mode instead of several.
"""

import base64
import html as html_module
import http.cookiejar
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

from .util import HarnessError, log, wait_for

ADMIN_PORTLET_ID = "ai_tensoropt_sel_web_internal_portlet_SearchEvalLoggerPortlet"

SERVER_ADMIN_PORTLET_ID = "com_liferay_server_admin_web_portlet_ServerAdminPortlet"

LOGIN_PORTLET_ID = "com_liferay_login_web_portlet_LoginPortlet"

NOTIFICATIONS_PORTLET_ID = "com_liferay_notifications_web_portlet_NotificationsPortlet"

SCRIPT_OUTPUT_DIRECTORY = "/opt/liferay/data/e2e"

# How long an export may take to show up as a background task. Distinct
# from how long it may take to finish, which is measured in thousands of
# seconds at the heavy tier.
EXPORT_APPEARANCE_TIMEOUT = 60

# The value SearchEvalLoggerPortletKeys declares, which is what Liferay
# stores in BackgroundTask.taskExecutorClassName.
EXPORT_EXECUTOR_CLASS_NAME = (
    "ai.tensoropt.sel.web.internal.background.task."
    "SearchEvalExportBackgroundTaskExecutor"
)


class Response:
    def __init__(self, status, url, body, headers):
        self.status = status
        self.url = url
        self.body = body
        self.headers = headers

    @property
    def text(self):
        return self.body.decode("utf-8", "replace")


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


class Portal:
    def __init__(self, stack, user="test@liferay.com", password="test"):
        self.stack = stack
        self.base_url = stack.base_url
        self.user = user
        self.password = password
        self._cookies = http.cookiejar.CookieJar()
        self._opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self._cookies)
        )
        self._no_redirect_opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self._cookies), _NoRedirect
        )
        self._auth_token = None

    # Plumbing

    def request(self, path, data=None, follow=True, timeout=300, headers=None):
        url = path if path.startswith("http") else self.base_url + path

        body = None

        if data is not None:
            body = urllib.parse.urlencode(data, doseq=True).encode("utf-8")

        request = urllib.request.Request(url, data=body)
        request.add_header("User-Agent", "search-eval-logger-e2e/1.0")

        for name, value in (headers or {}).items():
            request.add_header(name, value)

        opener = self._opener if follow else self._no_redirect_opener

        try:
            with opener.open(request, timeout=timeout) as connection:
                return Response(
                    connection.status,
                    connection.url,
                    connection.read(),
                    dict(connection.headers),
                )
        except urllib.error.HTTPError as error:
            return Response(error.code, url, error.read(), dict(error.headers))

    def get(self, path, **kwargs):
        return self.request(path, **kwargs)

    def post(self, path, data, **kwargs):
        return self.request(path, data=data, **kwargs)

    # Readiness and sign in

    def wait_until_serving(self, timeout=900):
        """Composite probe: HTTP, then a real page, then a signed-in session.

        "Server startup in" is deliberately not used. It does not appear in
        this project's logs, and a log line would in any case say only that
        Tomcat finished, not that the portal can serve a page to a signed-in
        administrator, which is what every later step needs.
        """

        def robots():
            return self.get("/c/portal/robots", timeout=30).status == 200

        wait_for(
            "the portal to answer on HTTP", robots, timeout=timeout, interval=5.0,
            stable=2,
        )

        def signed_in():
            self._reset_session()
            self.login()

            return self.auth_token() is not None

        wait_for(
            "the portal to accept an administrator sign in",
            signed_in,
            timeout=timeout,
            interval=5.0,
        )

    def _reset_session(self):
        self._cookies.clear()
        self._auth_token = None

    def login(self):
        response = self.get("/c/portal/login")

        p_auth = _first(
            re.findall(r"p_auth=([A-Za-z0-9]+)", response.text),
            "no p_auth on the login page",
        )

        action = (
            "/home?p_p_id=%s&p_p_lifecycle=1&p_p_state=maximized"
            "&_%s_javax.portlet.action=%%2Flogin%%2Flogin&p_auth=%s"
            % (LOGIN_PORTLET_ID, LOGIN_PORTLET_ID, p_auth)
        )

        self.post(
            action,
            {
                "_%s_login" % LOGIN_PORTLET_ID: self.user,
                "_%s_password" % LOGIN_PORTLET_ID: self.password,
            },
            follow=False,
        )

        self._auth_token = None

        return self.auth_token()

    def auth_token(self):
        if self._auth_token:
            return self._auth_token

        response = self.get("/group/control_panel")

        matches = re.findall(r"Liferay\.authToken\s*=\s*'([^']+)'", response.text)

        if not matches:
            return None

        self._auth_token = matches[0]

        return self._auth_token

    # Script console

    def run_script(self, body, timeout=1800):
        """Runs a Groovy script and returns whatever it assigned to `result`.

        Raises with the script's own stack trace when it throws, so a broken
        script reads as a broken script rather than as a missing file.

        One retry, with a fresh session, because the auth token belongs to the
        session and a session that has been invalidated underneath the harness
        would otherwise be reported as a script failure.
        """
        try:
            return self._run_script(body, timeout)
        except HarnessError as error:
            if "produced no result file" not in str(error):
                raise

            log("Script console produced nothing; signing in again and retrying")

            self._reset_session()
            self.login()

            return self._run_script(body, timeout)

    def _run_script(self, body, timeout):
        identifier = uuid.uuid4().hex
        container_path = "%s/%s.json" % (SCRIPT_OUTPUT_DIRECTORY, identifier)

        script = _SCRIPT_TEMPLATE % {
            "path": container_path,
            "body": body,
        }

        # A freshly read CSRF token for every script.
        #
        # Liferay rotates the session's auth token across a portlet action, so
        # a token read once is good for exactly one action and the second
        # script of any pair would be rejected with nothing written anywhere.
        # Re-reading it costs one page load.
        #
        # The console's CAPTCHA is a separate matter and is turned off for the
        # test portal by osgi/configs/com.liferay.captcha.configuration.
        # CaptchaConfiguration.config. Signing in again here would also avoid
        # it, since the first action of a session is exempt, but relying on
        # that while the config file is also present would leave two mechanisms
        # for one problem and no way to tell which was working.

        self._auth_token = None

        auth_token = self.auth_token()

        if not auth_token:
            raise HarnessError("Not signed in: no auth token available")

        namespace = "_%s_" % SERVER_ADMIN_PORTLET_ID

        action = (
            "/group/control_panel/manage?p_p_id=%s&p_p_lifecycle=1"
            "&p_p_state=maximized&%sjavax.portlet.action="
            "%%2Fserver_admin%%2Fedit_server&p_auth=%s"
            % (SERVER_ADMIN_PORTLET_ID, namespace, auth_token)
        )

        response = self.post(
            action,
            {
                namespace + "cmd": "runScript",
                namespace + "language": "groovy",
                namespace + "script": script,
            },
            timeout=timeout,
        )

        raw = None

        for _ in range(5):
            raw = self.stack.read_container_file(container_path)

            if raw is not None:
                break

            time.sleep(1.0)

        if raw is None:
            hint = ""

            # A script that fails to compile throws before the wrapper's own
            # try block exists, so there is no result file to read and the only
            # account of what happened is the ScriptingException the console
            # renders. Surfacing it here is the difference between "no file"
            # and "line 4: unexpected token".

            reported = re.findall(r"<pre>(.*?)</pre>", response.text, re.S)

            if reported:
                lines = _unescape(reported[0]).strip().splitlines()

                hint = " Script console reported:\n%s" % "\n".join(lines[:12])

            if not hint and (
                "CAPTCHA" in response.text or "captcha" in response.text
            ):
                hint = (
                    " The script console rejected the request on its CAPTCHA "
                    "check, which means this was not the first action of its "
                    "session."
                )

            raise HarnessError(
                "The script console produced no result file (HTTP %d).%s"
                % (response.status, hint)
            )

        self.stack.liferay_exec("rm", "-f", container_path, check=False)

        parsed = json.loads(raw)

        if not parsed.get("ok"):

            # The first few frames say what went wrong; the eighty below them
            # are Tomcat's filter chain and make a report unreadable.

            trace = str(parsed.get("error") or "").splitlines()

            raise HarnessError(
                "Groovy script failed:\n%s%s"
                % (
                    "\n".join(trace[:12]),
                    "" if len(trace) <= 12 else
                    "\n  ... %d more frames" % (len(trace) - 12),
                )
            )

        return parsed.get("result")

    # Facts about the instance

    def company_id(self):
        from . import scripts

        return int(self.run_script(scripts.COMPANY_ID))

    def group_id(self, company_id, group_key="Guest"):
        from . import scripts

        return int(
            self.run_script(
                scripts.GROUP_ID
                % {"company_id": company_id, "group_key": group_key}
            )
        )

    # The plugin's own screen

    def admin_screen(self):
        response = self.get(
            "/group/control_panel/manage?p_p_id=%s&p_p_lifecycle=0"
            "&p_p_state=maximized" % ADMIN_PORTLET_ID
        )

        if response.status != 200:
            raise HarnessError(
                "The Search Eval Export screen answered HTTP %d" % response.status
            )

        text = response.text

        if "is temporarily unavailable" in text:
            raise HarnessError(
                "The Search Eval Export screen rendered its error page. The "
                "JSP failed to compile; the cause is in the portal log."
            )

        return text

    def notifications_list(self):
        """The signed-in user's notifications list, as plain text.

        This is the list the product menu's notifications link opens. Reading
        it, rather than the UserNotificationEvent table, is what shows whether
        a notification actually reaches the person: the list only shows
        delivered website events, and a stored notification that is not
        delivered never appears (TO-110).
        """
        response = self.get(
            "/group/control_panel/manage?p_p_id=%s&p_p_lifecycle=0"
            "&p_p_state=maximized" % NOTIFICATIONS_PORTLET_ID
        )

        if response.status != 200:
            raise HarnessError(
                "The notifications list answered HTTP %d" % response.status
            )

        return notifications_list_text(response.text)

    def admission_counters(self):
        """Reads the six counters off the admin screen.

        They are the plugin's own published figures, so reading them here tests
        the same numbers an administrator sees rather than a private counter.
        """
        text = self.admin_screen()

        labels = {
            "observed": "Searches observed while enabled",
            "keywords": "Searches carrying user keywords",
            "admitted": "Searches admitted (all conditions)",
            "dispatched": "Events handed to the message bus",
            "dropped": "Events dropped",
            "persisted": "Events written to the database",
        }

        counters = {}

        for key, label in labels.items():
            match = re.search(
                re.escape(label) + r"\s*</td>\s*<td>\s*([0-9]+)\s*</td>", text
            )

            if match is None:
                raise HarnessError(
                    "Counter %r not found on the admin screen" % label
                )

            counters[key] = int(match.group(1))

        return counters

    # Searching

    def search(self, keywords, path="/web/guest/search"):
        query = urllib.parse.urlencode({"q": keywords})

        return self.get("%s?%s" % (path, query))

    def search_result_count(self, keywords, path="/web/guest/search"):
        """Runs a search and returns how many results the widget reported.

        Reads the Search Results widget's own total label, "N Results for
        <strong>term</strong>". Matching the query term anywhere in the page is
        not a usable signal and was the bug this replaced: Liferay echoes the
        term back in Liferay.currentURL, in currentURLEncoded and in the
        product menu's redirect parameter, so a search for a term that matches
        nothing still contains it several times over.

        Returns (count, response). A count of None means the widget rendered no
        total label at all, which is distinct from a total of zero and is
        reported as such rather than folded into it.
        """
        response = self.search(keywords, path=path)

        if response.status != 200:
            raise HarnessError(
                "The search page answered HTTP %d for %r"
                % (response.status, keywords)
            )

        return parse_result_count(response.text), response

    # Export

    def export_action_url(self, text=None):
        text = self.admin_screen() if text is None else text

        # Anchored to this portlet's namespace and to its own action name.
        # Matching any lifecycle=1 form works only because there happens to be
        # one form on the screen today.

        match = re.search(
            r'<form[^>]*action="([^"]*p_p_id=%s[^"]*'
            r'javax\.portlet\.action=%%2Fsearch_eval_logger%%2Fexport[^"]*)"'
            % re.escape(ADMIN_PORTLET_ID),
            text,
        )

        if match is None:
            raise HarnessError(
                "No export form on the admin screen. Either the EXPORT "
                "permission is missing or the screen did not render its form."
            )

        return _unescape(match.group(1))

    def start_export(self, start_date=None, end_date=None, expect_task=True):
        """Submits the export form and, by default, waits for a task to appear.

        The appearance wait is short and separate from the completion wait on
        purpose. An export that is rejected before it starts, by an invalid
        date range, by a permission regression, or by a background task
        executor that is not registered, creates no task at all; without this
        the run would sit on the completion timeout, which is measured in the
        thousands of seconds, and a single regression would cost a CI runner an
        hour before saying anything.

        Any SessionErrors the screen reports are raised here, because the
        portlet answers a rejected submission with a rendered page rather than
        a status code.
        """
        before = self.background_task_count()

        action = self.export_action_url()

        namespace = "_%s_" % ADMIN_PORTLET_ID

        response = self.post(
            action,
            {
                namespace + "startDate": start_date or "",
                namespace + "endDate": end_date or "",
            },
        )

        errors = self.session_errors(response.text)

        if errors:
            raise HarnessError(
                "The export screen rejected the request: %s" % ", ".join(errors)
            )

        if not expect_task:
            return before

        def appeared():
            return self.background_task_count() > before

        try:
            wait_for(
                "an export background task to appear",
                appeared,
                timeout=EXPORT_APPEARANCE_TIMEOUT,
                interval=3.0,
            )
        except Exception as error:
            raise HarnessError(
                "No export background task appeared within %ds of submitting "
                "the form. The action was accepted with no visible error, so "
                "the likely causes are a permission regression or a background "
                "task executor that is not registered under %r. Not waiting on "
                "the completion timeout for this. (%s)"
                % (
                    EXPORT_APPEARANCE_TIMEOUT,
                    "SearchEvalExportBackgroundTaskExecutor",
                    error,
                )
            )

        # The caller gets the count it has to beat, not the response. Every
        # export case then waits for a task that is new rather than for the
        # newest task to be finished, which on a retry or a reordering would
        # be the previous case's.

        return before

    def background_task_count(self):
        return self.stack.background_task_count(EXPORT_EXECUTOR_CLASS_NAME)

    def session_errors(self, text):
        """The error keys the portlet's screen renders, as plain strings.

        Liferay renders a portlet action's rejection as a page, so there is no
        status code to read. The two the export screen can raise are declared
        in its Language.properties.
        """
        found = []

        for key, message in (
            ("invalid-date-range", "Enter valid dates"),
            ("permission", "You do not have permission to export"),
        ):
            if message in text:
                found.append(key)

        return found

    def background_tasks(self):
        """Reads the export list off the admin screen.

        Returns a list of dicts with status, duration and download URL.
        """
        text = self.admin_screen()

        rows = []

        table = re.search(
            r"Recent Exports.*?<tbody>(.*?)</tbody>", text, re.S
        )

        if table is None:
            return rows

        for row_html in re.findall(r"<tr>(.*?)</tr>", table.group(1), re.S):
            cells = [
                re.sub(r"<[^>]+>", " ", cell).strip()
                for cell in re.findall(r"<td[^>]*>(.*?)</td>", row_html, re.S)
            ]

            download = re.search(r'<a href="([^"]+)"', row_html)

            rows.append(
                {
                    "started": cells[0] if len(cells) > 0 else "",
                    "finished": cells[1] if len(cells) > 1 else "",
                    "duration": cells[2] if len(cells) > 2 else "",
                    "status": cells[3] if len(cells) > 3 else "",
                    "download_url": _unescape(download.group(1)) if download else None,
                }
            )

        return rows

    def wait_for_export_result(self, timeout=1800, after=None):
        """Waits for a new export to reach a terminal status, any status.

        `after` is the task count from before the export was started. With it,
        the wait is for a task that did not exist yet; without it, the newest
        row on the screen would do, which is only safe for the first export of
        a run.
        """

        def finished():
            if after is not None and self.background_task_count() <= after:
                return None

            rows = self.background_tasks()

            if not rows:
                return None

            newest = rows[0]

            if newest["status"].lower() in ("successful", "failed"):
                return newest

            return None

        return wait_for(
            "the export background task to finish",
            finished,
            timeout=timeout,
            interval=5.0,
        )

    def wait_for_export(self, timeout=1800, after=None):
        row = self.wait_for_export_result(timeout=timeout, after=after)

        if row["status"].lower() != "successful":
            raise HarnessError(
                "The export background task finished with status %r" % row["status"]
            )

        return row

    def download(self, url, destination):
        """Returns (bytes written, the file name the portal offered)."""
        response = self.get(url, timeout=3600)

        if response.status != 200:
            raise HarnessError("Download answered HTTP %d" % response.status)

        with open(destination, "wb") as file_:
            file_.write(response.body)

        disposition = ""

        for name, value in response.headers.items():
            if name.lower() == "content-disposition":
                disposition = value

        match = re.search(r'filename="?([^";]+)', disposition)

        return len(response.body), match.group(1) if match else ""

    # Headless content

    def headless(self, method, path, payload=None):
        url = self.base_url + path

        data = None
        headers = {"Accept": "application/json"}

        if payload is not None:
            data = json.dumps(payload).encode("utf-8")
            headers["Content-Type"] = "application/json"

        request = urllib.request.Request(url, data=data, method=method)

        token = base64.b64encode(
            ("%s:%s" % (self.user, self.password)).encode("utf-8")
        ).decode("ascii")

        request.add_header("Authorization", "Basic %s" % token)

        for name, value in headers.items():
            request.add_header(name, value)

        try:
            with urllib.request.urlopen(request, timeout=300) as connection:
                body = connection.read()

                return connection.status, json.loads(body) if body else None
        except urllib.error.HTTPError as error:
            return error.code, error.read().decode("utf-8", "replace")


def _first(values, message):
    if not values:
        raise HarnessError(message)

    return values[0]


def _unescape(value):
    return (
        value.replace("&amp;", "&")
        .replace("&quot;", '"')
        .replace("&#034;", '"')
        .replace("&lt;", "<")
        .replace("&gt;", ">")
    )


_SCRIPT_TEMPLATE = """
import groovy.json.JsonOutput

def __file = new File("%(path)s")

__file.getParentFile().mkdirs()

def result = null

try {
    %(body)s

    __file.text = JsonOutput.toJson([ok: true, result: result])
}
catch (Throwable __throwable) {
    def __writer = new StringWriter()

    __throwable.printStackTrace(new PrintWriter(__writer))

    __file.text = JsonOutput.toJson([ok: false, error: __writer.toString()])
}
"""


def notifications_list_text(html):
    """The notifications portlet's body with markup and scripts removed."""
    portlet = html.find(NOTIFICATIONS_PORTLET_ID)
    start = html.find("portlet-body", portlet) if portlet >= 0 else -1

    if start < 0:
        raise HarnessError("The notifications portlet did not render")

    body = re.sub(r"<script.*?</script>", " ", html[start:], flags=re.S)
    text = html_module.unescape(re.sub(r"<[^>]+>", " ", body))

    return re.sub(r"\s+", " ", text)


def parse_result_count(html):
    """How many results the Search Results widget reported.

    The widget renders one of two things, and neither of them is the query
    term appearing somewhere in the page. With results it prints a total
    label, "N Results for <strong>term</strong>". With none it prints "No
    results were found." and no total label at all.

    Returns 0 for the second, and None only when the page shows neither, which
    means it is not a rendered search result and is kept distinct from an
    honest zero.

    A module level function so it can be exercised against saved markup
    without a portal, which is how the harness proves it discriminates.
    """
    match = re.search(
        r'class="[^"]*search-total-label[^"]*"[^>]*>\s*([0-9,]+)\s+Results?\s+for',
        html,
    )

    if match is not None:
        return int(match.group(1).replace(",", ""))

    if re.search(r"No results were found", html, re.I):
        return 0

    return None
