#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Tests the tests.

Every check in this harness is a claim that some deviation would be caught.
This file makes each of those claims falsifiable: it builds a good fixture,
mutates it in the specific way the check exists to notice, and fails if the
check accepts the mutation.

It exists because several of the original checks did not discriminate. A key
set collected from line one only accepted a file that dropped three keys from
line six; a "the term appears on the page" probe accepted a search that matched
nothing, because Liferay echoes the query back three times outside the result
list; an element scan keyed on the literal "{{" accepted an image pointing at a
real vendor host. Each of those passed a full run against a real portal while
testing nothing, so a green suite is not by itself evidence that a suite works.

Runs in about a second, needs no Docker and no portal, and is meant to run
alongside the unit tests rather than only before a release:

    python3 e2e/selftest.py
"""

import io
import json
import os
import sys
import time
import traceback
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from harness import util as harness_util  # noqa: E402

harness_util.log = lambda message: None

from harness import checks  # noqa: E402
from harness.portal import parse_result_count  # noqa: E402
from harness.results import Results  # noqa: E402
from harness.util import HarnessError  # noqa: E402

DIRECTORY = os.path.dirname(os.path.abspath(__file__))

FAILURES = []

CHECKED = []


def expect_rejected(description, function):
    """The mutation must be caught. Silence is the failure."""
    try:
        function()
    except (AssertionError, HarnessError, ValueError, KeyError, TypeError):
        CHECKED.append("caught: %s" % description)

        return

    FAILURES.append("NOT CAUGHT: %s" % description)


def expect_accepted(description, function):
    """The unmutated fixture must pass, or the check above proves nothing."""
    try:
        function()
    except Exception:  # noqa: BLE001 - reported below
        FAILURES.append(
            "good fixture rejected: %s\n%s" % (description, traceback.format_exc())
        )

        return

    CHECKED.append("accepted: %s" % description)


# Fixtures


def event_line(index, **overrides):
    """One events.jsonl record in the shape DESIGN.md 6.2 specifies."""
    record = {
        "event_id": "e2e-%016d" % index,
        "created_at": "2026-09-%02dT10:00:%02dZ" % (1 + index // 60, index % 60),
        "query": "generated query %d" % index,
        "query_truncated": False,
        "locale": "en_US",
        "scope_group_ids": [20118],
        "entry_class_names": ["com.liferay.journal.model.JournalArticle"],
        "applied_facets": None,
        "facet_capture_status": "NONE_APPLIED",
        "blueprint_id": None,
        "audience_type": "AUTHENTICATED",
        "cohort_hash": "3585671aa51e6cd46efff007511fe851",
        "requested_size": 20,
        "requested_from": 0,
        "total_hits": 249,
        "logged_hit_count": 2,
        "source_type": "WIDGET",
        "hits": [hit_object(index, rank) for rank in range(2)],
    }

    record.update(overrides)

    return record


def hit_object(index, rank):
    return {
        "rank": rank,
        "score": 10.0 - rank,
        "doc_uid": "com.liferay.journal.model.JournalArticle_PORTLET_%d" % index,
        "entry_class_name": "com.liferay.journal.model.JournalArticle",
        "entry_class_pk": index * 100 + rank,
        "title": "Generated title %d-%d" % (index, rank),
        "snippet": None,
        "extra_fields": {},
    }


def archive(records, readme="# Search Evaluation Export\n\nCaveats.\n",
            manifest=None):
    """Builds an export archive in memory."""
    buffer = io.BytesIO()

    hits = sum(len(record.get("hits", [])) for record in records)

    with zipfile.ZipFile(buffer, "w") as zip_file:
        zip_file.writestr(
            "events.jsonl",
            "\n".join(json.dumps(record) for record in records) + "\n",
        )
        zip_file.writestr(
            "manifest.json",
            json.dumps(
                manifest
                or {
                    "company_id": 123,
                    "counts": {"events": len(records), "hits": hits},
                }
            ),
        )
        zip_file.writestr("README.md", readme)

    buffer.seek(0)

    return buffer


def stream(records):
    return lambda: checks._stream_events(zipfile.ZipFile(archive(records)))


SEARCH_PAGE_WITH_RESULTS = """
<html><head><script>
var Liferay = {currentURL: '/web/guest/search?q=zarvexium',
 currentURLEncoded: '%2Fweb%2Fguest%2Fsearch%3Fq%3Dzarvexium'};
</script></head><body>
<a href="/group/control_panel?redirect=%2Fweb%2Fguest%2Fsearch%3Fq%3Dzarvexium">Menu</a>
<div class="c-mb-4 c-mt-4 search-total-label">
    2 Results for <strong>zarvexium</strong>
</div>
<ul id="search-results-display-list"><li>Zarvexium policy note 0</li></ul>
</body></html>
"""

# The page a search that matches nothing actually produces, copied from the
# running portal: the term is still echoed back three times, there is no total
# label at all, and the widget says so in words. The original probe returned
# true on this page, which is how a wait that never waited went unnoticed.
SEARCH_PAGE_WITHOUT_RESULTS = """
<html><head><script>
var Liferay = {currentURL: '/web/guest/search?q=qqzzxxnothinghere',
 currentURLEncoded: '%2Fweb%2Fguest%2Fsearch%3Fq%3Dqqzzxxnothinghere'};
</script></head><body>
<a href="/group/control_panel?redirect=%2Fweb%2Fguest%2Fsearch%3Fq%3Dqqzzxxnothinghere">Menu</a>
<div>Search Results No results were found.
No results were found that matched the keywords: qqzzxxnothinghere .</div>
</body></html>
"""

SEARCH_PAGE_ZERO_LABEL = SEARCH_PAGE_WITH_RESULTS.replace(
    "2 Results for", "0 Results for"
)

# Neither a total label nor the no-results wording: not a rendered search at
# all, which has to stay distinguishable from an honest zero.
NOT_A_SEARCH_PAGE = "<html><body><p>Sign in to continue.</p></body></html>"

ADMIN_SCREEN = """
<html><body>
<link rel="stylesheet" href="/o/classic-theme/css/main.css">
<script src="/o/js/loader.js"></script>
<img src="/documents/logo.png">
<div class="alert alert-info">Collection settings live in Configuration.
<p class="mb-0">Collecting since 2026-09-15, the day the first search was recorded.</p></div>
<p>
<a href="{{SIGNUP_URL}}?started=2026-09-15&amp;utm_source=liferay-plugin&amp;utm_medium=admin-screen" rel="noopener noreferrer" target="_blank">
Get an email reminder when your search log is ready to export (optional).
</a>
</p>
<h3>Run an Export</h3>
<p>
<a href="{{BOOKING_URL}}?utm_source=liferay-plugin&amp;utm_medium=export-complete" rel="noopener noreferrer" target="_blank">
Want this dataset evaluated? Book a call to scope the analysis.
</a>
</p>
<h3>Admission Counters</h3>
</body></html>
"""


class FakePortal:
    def __init__(self, screen):
        self.screen = screen
        self.base_url = "http://localhost:18080"

    def admin_screen(self):
        return self.screen


class FakeContext:
    def __init__(self, screen=ADMIN_SCREEN, archive_path=None):
        self.portal = FakePortal(screen)
        self.company_id = 92102577642293
        self.group_id = 20118
        self.collection_start_date = "2026-09-15"
        self.archive_path = archive_path
        self.archive_name = "search-eval-export-92102577642293-20260823-20260923.zip"
        self.export_range = ("2026-08-23", "2026-09-23")
        self.manifest = {
            "counts": {"events": 50014, "hits": 500110},
            "plugin_version": "1.0.0",
        }


class FakeCase:
    def __init__(self):
        self.stdout = []

    def note(self, message):
        self.stdout.append(str(message))


def write_archive(records, path):
    with open(path, "wb") as file_:
        file_.write(archive(records).getvalue())

    return path


# The checks, and the mutations each one claims to catch


def check_search_probe():
    expect_accepted(
        "search probe reads 2 from a page with results",
        lambda: _assert(parse_result_count(SEARCH_PAGE_WITH_RESULTS) == 2),
    )

    # The mutation that matters: the old probe was "the term appears in the
    # page", and on this page it does, three times.
    expect_rejected(
        "search probe treats a real no-results page as having results",
        lambda: _assert(parse_result_count(SEARCH_PAGE_WITHOUT_RESULTS)),
    )
    expect_accepted(
        "search probe reports 0, not None, for a real no-results page",
        lambda: _assert(parse_result_count(SEARCH_PAGE_WITHOUT_RESULTS) == 0),
    )
    expect_accepted(
        "the marker term is present on the no-results page it rejects",
        lambda: _assert("qqzzxxnothinghere" in SEARCH_PAGE_WITHOUT_RESULTS),
    )
    expect_rejected(
        "search probe treats an explicit zero total as having results",
        lambda: _assert(parse_result_count(SEARCH_PAGE_ZERO_LABEL)),
    )
    expect_accepted(
        "search probe reports None for a page that is not a search result",
        lambda: _assert(parse_result_count(NOT_A_SEARCH_PAGE) is None),
    )


def check_headline_matching():
    """The result row check must survive the widget's highlight markup."""
    from harness.checks import visible_text

    highlighted = (
        '<ul id="search-results-display-list"><li>'
        '<span class="highlight mark">Zarvexium</span> policy note 0</li></ul>'
    )

    expect_accepted(
        "a headline split by highlight markup is still found",
        lambda: _assert(
            "zarvexium policy note 0" in visible_text(highlighted).lower()
        ),
    )

    expect_rejected(
        "a page without the created headline is not accepted",
        lambda: _assert(
            "zarvexium policy note 0"
            in visible_text(SEARCH_PAGE_WITHOUT_RESULTS).lower()
        ),
    )

    expect_accepted(
        "script contents are not mistaken for visible text",
        lambda: _assert(
            "currenturl" not in visible_text(SEARCH_PAGE_WITH_RESULTS).lower()
        ),
    )


def check_events_jsonl():
    good = [event_line(index) for index in range(10)]

    expect_accepted("events.jsonl fixture is accepted", stream(good))

    dropped = [event_line(index) for index in range(10)]

    for key in ("query", "cohort_hash", "source_type"):
        del dropped[5][key]

    expect_rejected(
        "three keys dropped from line six", stream(dropped)
    )

    replaced = [event_line(index) for index in range(10)]

    for record in replaced:
        record["hits"] = [{"zzz": 1} for _ in record["hits"]]

    expect_rejected(
        "every hit object on every line replaced with {'zzz': 1}",
        stream(replaced),
    )

    partial_hit = [event_line(index) for index in range(10)]

    del partial_hit[7]["hits"][1]["entry_class_pk"]

    expect_rejected(
        "one hit missing entry_class_pk on one line", stream(partial_hit)
    )

    nulled = [event_line(index) for index in range(10)]

    for record in nulled:
        for key in record:
            if key not in ("created_at", "hits"):
                record[key] = None

    expect_rejected(
        "every non-date top level value nulled on every line", stream(nulled)
    )

    empty_query = [event_line(index) for index in range(10)]

    for record in empty_query:
        record["query"] = ""

    expect_rejected("query empty on every line", stream(empty_query))

    extra = [event_line(index) for index in range(10)]

    extra[3]["surprise"] = 1

    expect_rejected("an undocumented key added to one line", stream(extra))

    unordered = [event_line(index) for index in range(10)]

    unordered[4]["created_at"] = "2020-01-01T00:00:00Z"

    expect_rejected("one line out of chronological order", stream(unordered))

    no_hits_array = [event_line(index) for index in range(3)]

    no_hits_array[1]["hits"] = "not an array"

    expect_rejected("hits replaced with a string", stream(no_hits_array))


def check_vendor_neutrality(tmp_directory):
    good = write_archive(
        [event_line(index) for index in range(5)],
        os.path.join(tmp_directory, "good.zip"),
    )

    expect_accepted(
        "a clean archive passes the privacy gate",
        lambda: checks.archive_is_vendor_neutral(
            FakeContext(archive_path=good), FakeCase()
        ),
    )

    # A vendor token placed so that it straddles the one megabyte boundary the
    # scanner reads in. This is the case the overlap exists for, and the
    # version without it passed.
    chunk = 1 << 20
    token = "tensoropt"
    padding = "x" * (chunk - 4)

    straddling = os.path.join(tmp_directory, "straddle.zip")

    with open(straddling, "wb") as file_:
        file_.write(
            archive(
                [event_line(index) for index in range(2)],
                readme=padding + token + "y" * 100,
            ).getvalue()
        )

    expect_rejected(
        "a vendor token straddling the 1 MiB read boundary",
        lambda: checks.archive_is_vendor_neutral(
            FakeContext(archive_path=straddling), FakeCase()
        ),
    )

    utm = os.path.join(tmp_directory, "utm.zip")

    with open(utm, "wb") as file_:
        file_.write(
            archive(
                [event_line(index) for index in range(2)],
                readme="See ?utm_source=liferay-plugin for more.",
            ).getvalue()
        )

    expect_rejected(
        "a UTM tag inside the archive README",
        lambda: checks.archive_is_vendor_neutral(
            FakeContext(archive_path=utm), FakeCase()
        ),
    )


def check_zero_egress():
    expect_accepted(
        "the real admin screen passes the egress gate",
        lambda: checks.zero_egress(FakeContext(), FakeCase()),
    )

    pixel = ADMIN_SCREEN.replace(
        "<img src=\"/documents/logo.png\">",
        "<img src=\"https://tensoropt.ai/px.gif\">",
    )

    expect_rejected(
        "a tracking pixel pointing at a real vendor host",
        lambda: checks.zero_egress(FakeContext(screen=pixel), FakeCase()),
    )

    third_party = ADMIN_SCREEN.replace(
        "<script src=\"/o/js/loader.js\"></script>",
        "<script src=\"https://cdn.example.com/analytics.js\"></script>",
    )

    expect_rejected(
        "a script element fetching from any third party host",
        lambda: checks.zero_egress(FakeContext(screen=third_party), FakeCase()),
    )

    preload = ADMIN_SCREEN.replace(
        "<link rel=\"stylesheet\" href=\"/o/classic-theme/css/main.css\">",
        "<link rel=\"preconnect\" href=\"https://tensoropt.ai\">",
    )

    expect_rejected(
        "a preconnect to a vendor host",
        lambda: checks.zero_egress(FakeContext(screen=preload), FakeCase()),
    )

    loose = ADMIN_SCREEN.replace(
        "<h3>Admission Counters</h3>",
        "<h3>Admission Counters</h3><p>Visit {{SIGNUP_URL}} to sign up.</p>",
    )

    expect_rejected(
        "a vendor placeholder in page text rather than in an anchor href",
        lambda: checks.zero_egress(FakeContext(screen=loose), FakeCase()),
    )

    real_host_loose = ADMIN_SCREEN.replace(
        "<h3>Admission Counters</h3>",
        "<h3>Admission Counters</h3><p>Ask us at tensoropt.ai for help.</p>",
    )

    expect_rejected(
        "a real vendor host in page text after the placeholders are replaced",
        lambda: checks.zero_egress(
            FakeContext(screen=real_host_loose), FakeCase()
        ),
    )

    # The plugin's own package name is "ai.tensoropt.sel", so its portlet id
    # is on its own screen dozens of times. That must not read as a vendor
    # address, or the case fails on the plugin merely being installed.

    portlet_id = ADMIN_SCREEN.replace(
        "<h3>Admission Counters</h3>",
        '<h3>Admission Counters</h3><form id="_ai_tensoropt_sel_web_internal'
        '_portlet_SearchEvalLoggerPortlet_fm"></form>',
    )

    expect_accepted(
        "the plugin's own portlet id is not mistaken for a vendor address",
        lambda: checks.zero_egress(FakeContext(screen=portlet_id), FakeCase()),
    )


def check_funnel_links():
    expect_accepted(
        "the real admin screen passes the funnel link rules",
        lambda: checks.funnel_links(FakeContext(), FakeCase()),
    )

    extra_parameter = ADMIN_SCREEN.replace(
        "utm_medium=admin-screen", "utm_medium=admin-screen&amp;instance=abc123"
    )

    expect_rejected(
        "an extra query parameter on the collection start link",
        lambda: checks.funnel_links(
            FakeContext(screen=extra_parameter), FakeCase()
        ),
    )

    identifying_path = ADMIN_SCREEN.replace(
        "{{SIGNUP_URL}}?started=", "{{SIGNUP_URL}}/92102577642293/signup?started="
    )

    expect_rejected(
        "the company id carried in the link's path rather than its query",
        lambda: checks.funnel_links(
            FakeContext(screen=identifying_path), FakeCase()
        ),
    )

    counts_in_url = ADMIN_SCREEN.replace(
        "utm_medium=export-complete", "utm_medium=export-complete&amp;n=500110"
    )

    expect_rejected(
        "a row count added to the export complete link",
        lambda: checks.funnel_links(
            FakeContext(screen=counts_in_url), FakeCase()
        ),
    )

    no_noopener = ADMIN_SCREEN.replace('rel="noopener noreferrer" ', "", 1)

    expect_rejected(
        "the collection start link losing rel=noopener",
        lambda: checks.funnel_links(FakeContext(screen=no_noopener), FakeCase()),
    )

    wrong_date = ADMIN_SCREEN.replace("started=2026-09-15", "started=2020-01-01")

    expect_rejected(
        "a collection start date the cycle never recorded",
        lambda: checks.funnel_links(FakeContext(screen=wrong_date), FakeCase()),
    )

    no_link = ADMIN_SCREEN.replace(
        "Get an email reminder when your search log is ready to export", "Nothing"
    )

    expect_rejected(
        "the collection start link missing entirely",
        lambda: checks.funnel_links(FakeContext(screen=no_link), FakeCase()),
    )


def check_archive_name():
    context = FakeContext()

    expect_accepted(
        "an archive named for the requested range",
        lambda: checks.export_archive_name(context, FakeCase()),
    )

    off_by_one = FakeContext()
    off_by_one.archive_name = (
        "search-eval-export-92102577642293-20260823-20260924.zip"
    )

    expect_rejected(
        "an archive whose end date is a day past the request (defect D-4)",
        lambda: checks.export_archive_name(off_by_one, FakeCase()),
    )

    wrong_instance = FakeContext()
    wrong_instance.archive_name = (
        "search-eval-export-1-20260823-20260923.zip"
    )

    expect_rejected(
        "an archive named for another virtual instance",
        lambda: checks.export_archive_name(wrong_instance, FakeCase()),
    )


def check_funnel_counters():
    """TO-92: every admitted event is persisted or dropped, and once only."""
    good = {
        "observed": 18,
        "keywords": 10,
        "admitted": 9,
        "dispatched": 8,
        "dropped": 1,
        "persisted": 8,
    }

    expect_accepted(
        "a funnel where the one dropped event was rejected by the queue",
        lambda: checks.assert_funnel_adds_up(good),
    )

    for description, overrides in (
        ("more dispatched than admitted", {"dispatched": 10}),
        (
            "an admitted event whose capture failed and was counted nowhere",
            {"dropped": 0},
        ),
        ("more admitted than carried keywords", {"admitted": 11}),
        ("more persisted than dispatched", {"persisted": 9, "dropped": 0}),
    ):
        counters = dict(good, **overrides)

        expect_rejected(
            description,
            lambda counters=counters: checks.assert_funnel_adds_up(counters),
        )


NOTIFICATIONS_EMPTY = """
<div id="p_p_id_com_liferay_notifications_web_portlet_NotificationsPortlet_">
<div class="portlet-body"> Notifications List (0) Requests List (0) Filter
You do not have any notifications. </div></div>
"""

NOTIFICATIONS_LISTED = """
<div id="p_p_id_com_liferay_notifications_web_portlet_NotificationsPortlet_">
<div class="portlet-body"> Notifications List (1) <script>var x = 1;</script>
<p>Logging is switched on but nothing is being recorded, because
Liferay&#39;s search components bound their searcher before this plugin was
installed. <strong>Restart the portal to begin collecting.</strong></p>
</div></div>
"""


def check_notification_listed():
    """TO-110: stored is not shown; the list is what the administrator sees."""
    from harness.portal import notifications_list_text

    expect_accepted(
        "a stall notification in the administrator's notifications list",
        lambda: checks.assert_notification_listed(
            notifications_list_text(NOTIFICATIONS_LISTED),
            checks.STALL_NOTIFICATION_TEXT,
        ),
    )

    expect_rejected(
        "an empty notifications list (the pre-TO-110 undelivered event)",
        lambda: checks.assert_notification_listed(
            notifications_list_text(NOTIFICATIONS_EMPTY),
            checks.STALL_NOTIFICATION_TEXT,
        ),
    )

    expect_rejected(
        "a list showing the other notification only",
        lambda: checks.assert_notification_listed(
            notifications_list_text(NOTIFICATIONS_LISTED),
            checks.READINESS_NOTIFICATION_TEXT,
        ),
    )

    expect_rejected(
        "a page where the notifications portlet did not render at all",
        lambda: notifications_list_text("<html><body>Error</body></html>"),
    )


def check_daily_schedule():
    """TO-110: the daily jobs fire at a time of day, not a day after startup."""
    from datetime import datetime, timezone

    now = datetime(2026, 9, 24, 10, 40, 11, tzinfo=timezone.utc)

    purge = "ai.tensoropt.sel.internal.scheduler.RetentionPurgeSchedulerJobConfiguration"
    cycle = "ai.tensoropt.sel.internal.scheduler.CollectionCycleSchedulerJobConfiguration"

    def jobs(purge_next="2026-09-25T03:00:00Z",
             purge_following="2026-09-26T03:00:00Z", include_cycle=True):
        listed = [{"job": purge, "next": purge_next, "following": purge_following}]

        if include_cycle:
            listed.append({"job": cycle, "next": "2026-09-25T03:30:00Z",
                           "following": "2026-09-26T03:30:00Z"})

        return listed

    expect_accepted(
        "both jobs at their time of day, then daily",
        lambda: checks.assert_daily_schedule(jobs(), now),
    )

    expect_rejected(
        "a purge next due one day after the restart (the pre-TO-110 trigger)",
        lambda: checks.assert_daily_schedule(
            jobs("2026-09-25T10:40:11Z", "2026-09-26T10:40:11Z"), now),
    )

    expect_rejected(
        "the collection cycle check not scheduled at all",
        lambda: checks.assert_daily_schedule(jobs(include_cycle=False), now),
    )

    expect_rejected(
        "a purge that fires every other day",
        lambda: checks.assert_daily_schedule(
            jobs(purge_following="2026-09-27T03:00:00Z"), now),
    )

    expect_rejected(
        "a purge not due for more than a day",
        lambda: checks.assert_daily_schedule(
            jobs("2026-09-26T03:00:00Z", "2026-09-27T03:00:00Z"), now),
    )


def check_foreign_download():
    """TO-109: the export download URL must not serve another task's file."""
    url = (
        "http://localhost:18080/group/control_panel/manage?p_p_id=x"
        "&p_p_lifecycle=2&_x_backgroundTaskId=33418&p_p_cacheability=c"
    )

    expect_accepted(
        "the export URL pointed at another task by id",
        lambda: _assert(
            "_x_backgroundTaskId=33642&" in checks.foreign_task_url(url, 33642)
        ),
    )

    expect_rejected(
        "a download URL with no task id to replace",
        lambda: checks.foreign_task_url("http://localhost/x?p_p_id=x", 1),
    )

    expect_accepted(
        "an empty refusal",
        lambda: checks.assert_attachment_not_served(b""),
    )

    expect_rejected(
        "the foreign attachment served (the pre-TO-91 handler)",
        lambda: checks.assert_attachment_not_served(
            checks.FOREIGN_ATTACHMENT_CONTENT.encode("utf-8")
        ),
    )

    expect_rejected(
        "some archive served for a task that is not an export",
        lambda: checks.assert_attachment_not_served(b"PK\x03\x04rest"),
    )


def check_number_types():
    """D-2: long valued fields must reach the archive as JSON numbers."""
    good = [event_line(index) for index in range(2)]

    expect_accepted(
        "an events.jsonl line with its long fields unquoted",
        lambda: checks.export_jsonl_number_types(
            FakeContext(archive_path=write_archive(good, _tmp("types-good"))),
            FakeCase(),
        ),
    )

    for description, mutate in (
        ("total_hits quoted", lambda record: record.update(total_hits="249")),
        (
            "entry_class_pk quoted",
            lambda record: record["hits"][0].update(entry_class_pk="100"),
        ),
        (
            "a scope group id quoted inside its array",
            lambda record: record.update(scope_group_ids=["20118"]),
        ),
    ):
        records = [event_line(index) for index in range(2)]

        mutate(records[0])

        path = write_archive(records, _tmp("types-%d" % len(CHECKED)))

        expect_rejected(
            "%s (the pre-TO-88 shape)" % description,
            lambda path=path: checks.export_jsonl_number_types(
                FakeContext(archive_path=path), FakeCase()
            ),
        )

    manifest = {
        "company_id": 123,
        "counts": {"events": 2, "hits": 4},
        "admission_counters": {
            "observed_search_count": 18,
            "persisted_event_count": 8,
            "scope": "Counted since this plugin last started",
        },
    }

    expect_accepted(
        "a manifest with its ids and counts unquoted",
        lambda: checks.assert_manifest_numbers(manifest),
    )

    for description, path_, value in (
        ("company_id quoted", ("company_id",), "123"),
        ("counts.events quoted", ("counts", "events"), "2"),
        (
            "an admission counter quoted",
            ("admission_counters", "persisted_event_count"),
            "8",
        ),
        ("counts.hits a boolean", ("counts", "hits"), True),
    ):
        mutated = json.loads(json.dumps(manifest))

        target = mutated

        for key in path_[:-1]:
            target = target[key]

        target[path_[-1]] = value

        expect_rejected(
            "a manifest with %s" % description,
            lambda mutated=mutated: checks.assert_manifest_numbers(mutated),
        )


def _tmp(name):
    return os.path.join(TMP, "%s.zip" % name)


UNBOUNDED_README = """# Search Evaluation Export

Range: unbounded to unbounded (UTC)  
Events: 2  
Hits: 0

**Blueprints can impose filters that are invisible here.** `blueprint_id` may
be null on those rows.
"""


def unbounded_archive(
    export_range=None, readme=UNBOUNDED_README, events=2, counted=None
):
    """An archive as an export with both bounds left empty should produce it."""
    records = [event_line(index) for index in range(events)]

    if export_range is None:
        export_range = {"from": None, "to": None}

    manifest = {
        "company_id": 123,
        "export_range": export_range,
        "counts": {
            "events": len(records) if counted is None else counted,
            "hits": 0,
        },
    }

    for record in records:
        record["hits"] = []

    return write_archive_with(records, readme, manifest)


def write_archive_with(records, readme, manifest):
    path = os.path.join(TMP, "unbounded-%d.zip" % len(CHECKED))

    with open(path, "wb") as file_:
        file_.write(archive(records, readme=readme, manifest=manifest).getvalue())

    return path


def check_unbounded_archive():
    """B-1: the unbounded export case has to assert on what it produced.

    Asserting only that the background task succeeded accepts three separate
    regressions: a manifest that lost a range key because Liferay's JSONObject
    drops nulls, a README printing the word null where a date belongs, and an
    exporter whose predicate went missing in the other direction and returned
    nothing at all.
    """
    expect_accepted(
        "an unbounded archive with both range keys, a named range and its rows",
        lambda: checks._assert_unbounded_archive(
            FakeCase(), unbounded_archive(), None, None, 2
        ),
    )

    expect_rejected(
        "a manifest whose export_range lost the null keys (the pre-TO-87 shape)",
        lambda: checks._assert_unbounded_archive(
            FakeCase(), unbounded_archive(export_range={}), None, None, 2
        ),
    )

    expect_rejected(
        "a manifest that kept only the bound it had",
        lambda: checks._assert_unbounded_archive(
            FakeCase(),
            unbounded_archive(export_range={"to": "2026-09-25T00:00:00Z"}),
            None,
            "2026-09-25T00:00:00Z",
            2,
        ),
    )

    expect_rejected(
        "a manifest reporting a bound the export was not asked for",
        lambda: checks._assert_unbounded_archive(
            FakeCase(),
            unbounded_archive(
                export_range={"from": "2026-08-24T00:00:00Z", "to": None}
            ),
            None,
            None,
            2,
        ),
    )

    expect_rejected(
        "a README printing null where the unbounded end belongs",
        lambda: checks._assert_unbounded_archive(
            FakeCase(),
            unbounded_archive(
                readme=UNBOUNDED_README.replace(
                    "Range: unbounded to unbounded (UTC)",
                    "Range: null to null (UTC, end exclusive)",
                )
            ),
            None,
            None,
            2,
        ),
    )

    expect_rejected(
        "a README still claiming an exclusive end it does not have",
        lambda: checks._assert_unbounded_archive(
            FakeCase(),
            unbounded_archive(
                readme=UNBOUNDED_README.replace(
                    "(UTC)", "(UTC, end exclusive)"
                )
            ),
            None,
            None,
            2,
        ),
    )

    expect_rejected(
        "an export that returned no rows at all (a predicate lost the other way)",
        lambda: checks._assert_unbounded_archive(
            FakeCase(), unbounded_archive(events=0), None, None, 2
        ),
    )

    expect_rejected(
        "an export holding fewer rows than the range does",
        lambda: checks._assert_unbounded_archive(
            FakeCase(), unbounded_archive(events=1), None, None, 2
        ),
    )

    expect_rejected(
        "a manifest count that disagrees with events.jsonl",
        lambda: checks._assert_unbounded_archive(
            FakeCase(), unbounded_archive(events=2, counted=5), None, None, 2
        ),
    )


def check_bundle_wait_is_anchored():
    """S7: the bundle wait must look at new log lines, not at all of history."""
    from harness import stack as stack_module

    calls = []

    def fake_run(args, **kwargs):
        calls.append(args)

        # A log that already contains the line, but only outside the window
        # the wait asked for. Anything reading the whole history would return
        # immediately off these.
        if "--since" in args:
            return 0, "", ""

        return 0, "STARTED ai.tensoropt.sel.impl_1.0.0 [2925]\n", ""

    original = stack_module.run

    stack_module.run = fake_run

    try:
        subject = stack_module.Stack(".", "sel-e2e-selftest", 18080, 15432)

        expect_rejected(
            "a bundle START line from earlier in the run satisfying the wait",
            lambda: subject.wait_for_bundle_started(
                "ai.tensoropt.sel.impl", time.monotonic(), timeout=2
            ),
        )

        expect_accepted(
            "the wait asks the daemon for a bounded window every time",
            lambda: _assert(
                calls and all("--since" in args for args in calls)
            ),
        )

        # And it does return when the line appears inside the window.
        calls.clear()

        stack_module.run = lambda args, **kwargs: (
            0,
            "STARTED ai.tensoropt.sel.impl_1.0.0 [2925]\n",
            "",
        )

        expect_accepted(
            "a bundle START line inside the window satisfies the wait",
            lambda: subject.wait_for_bundle_started(
                "ai.tensoropt.sel.impl", time.monotonic(), timeout=5
            ),
        )

        # TO-93. Four bundles start within milliseconds and are waited for in
        # turn, so by the time a later wait begins its line is already some
        # seconds old. Here it is twenty: visible to a window reaching back to
        # the deploy, invisible to one that opens when the wait does.

        def log_since(args, **kwargs):
            window = int(args[args.index("--since") + 1].rstrip("s"))

            if window >= 20:
                return 0, "STARTED ai.tensoropt.sel.service_1.0.0 [2926]\n", ""

            return 0, "", ""

        stack_module.run = log_since

        expect_accepted(
            "a bundle that started while an earlier wait was polling is seen",
            lambda: subject.wait_for_bundle_started(
                "ai.tensoropt.sel.service", time.monotonic() - 30, timeout=5
            ),
        )

        expect_rejected(
            "a window opened by each wait rather than at the deploy "
            "(the pre-TO-93 behaviour)",
            lambda: subject.wait_for_bundle_started(
                "ai.tensoropt.sel.service", time.monotonic(), timeout=2
            ),
        )
    finally:
        stack_module.run = original


def check_export_form_anchor():
    """S19: the export form must be found by its own action, not by any form."""
    from harness.portal import Portal, ADMIN_PORTLET_ID

    real = (
        '<form action="http://localhost:18080/group/control_panel/manage'
        '?p_p_id=%s&amp;p_p_lifecycle=1&amp;p_p_state=maximized'
        '&amp;_%s_javax.portlet.action=%%2Fsearch_eval_logger%%2Fexport'
        '&amp;p_auth=abc" method="post"></form>' % (ADMIN_PORTLET_ID, ADMIN_PORTLET_ID)
    )

    expect_accepted(
        "the export form is found by its own portlet action",
        lambda: _assert(
            "search_eval_logger" in Portal.export_action_url(None, text=real)
        ),
    )

    # Another portlet's action form, which the old "any lifecycle=1 form"
    # pattern would have picked up and posted the export parameters to.
    foreign = (
        '<form action="http://localhost:18080/group/control_panel/manage'
        '?p_p_id=com_liferay_server_admin_web_portlet_ServerAdminPortlet'
        '&amp;p_p_lifecycle=1" method="post"></form>'
    )

    expect_rejected(
        "another portlet's action form mistaken for the export form",
        lambda: Portal.export_action_url(None, text=foreign),
    )

    expect_rejected(
        "no export form at all",
        lambda: Portal.export_action_url(None, text="<html></html>"),
    )


def check_collection_start_date():
    """S20: a run crossing UTC midnight must not fail."""
    import datetime as dt

    from harness.checks import is_recent_utc_date

    now = dt.date(2026, 9, 23)

    expect_accepted(
        "today's date is accepted",
        lambda: _assert(is_recent_utc_date("2026-09-23", now)),
    )
    expect_accepted(
        "a run that crossed UTC midnight is accepted",
        lambda: _assert(is_recent_utc_date("2026-09-22", now)),
    )
    expect_rejected(
        "a date the cycle could not have written is accepted",
        lambda: _assert(is_recent_utc_date("2026-09-21", now)),
    )
    expect_rejected(
        "an empty collection start date is accepted",
        lambda: _assert(is_recent_utc_date("", now)),
    )


def check_expected_failures():
    """The xfail mechanism itself."""

    def fails(case):
        raise AssertionError("boom")

    def passes(case):
        return True

    results = Results(expected_failures={"known": "D-1, a known defect"})

    results.run("known", fails)
    results.run("healthy", passes)

    _assert(results.failure_count == 0, "a known defect must not fail the run")
    _assert(results.expected_failure_count == 1)

    CHECKED.append("accepted: a known defect fails without failing the run")

    results = Results(expected_failures={"known": "D-1, a known defect"})

    results.run("known", passes)

    _assert(
        results.failure_count == 1,
        "a known defect that starts passing must fail the run",
    )
    _assert(results.unexpected_pass_count == 1)

    CHECKED.append("caught: a known defect that quietly started passing")

    # The misreading the mechanism exists to prevent: a run that breaks early
    # turns its known failures into skips, and a rule of "expect exactly two
    # failures" would read that as healthier than the baseline.

    results = Results(expected_failures={"known": "D-1, a known defect"})

    results.run("upstream", fails)
    results.run("known", passes, requires=["upstream"])

    _assert(
        results.failure_count == 1,
        "a broken run must still fail even when its known failures were skipped",
    )

    CHECKED.append(
        "caught: a run that broke early and skipped its known failures"
    )


def check_xml_sanitising():
    from harness.results import xml_safe
    import xml.etree.ElementTree as ElementTree

    results = Results()

    def fails(case):
        raise AssertionError("psql said \x00\x08 something\x1b[0m")

    results.run("noisy", fails)

    path = os.path.join(TMP, "junit.xml")

    results.write_junit(path)

    expect_accepted(
        "junit.xml stays parseable after a failure carrying control bytes",
        lambda: ElementTree.parse(path),
    )

    _assert("\x00" not in xml_safe("a\x00b"))


def _assert(condition, message="assertion failed"):
    if not condition:
        raise AssertionError(message)


TMP = None


def main():
    global TMP

    import tempfile

    with tempfile.TemporaryDirectory(prefix="sel-e2e-selftest-") as tmp:
        TMP = tmp

        check_search_probe()
        check_headline_matching()
        check_events_jsonl()
        check_vendor_neutrality(tmp)
        check_zero_egress()
        check_funnel_links()
        check_archive_name()
        check_number_types()
        check_funnel_counters()
        check_notification_listed()
        check_daily_schedule()
        check_foreign_download()
        check_unbounded_archive()
        check_bundle_wait_is_anchored()
        check_export_form_anchor()
        check_collection_start_date()
        check_expected_failures()
        check_xml_sanitising()

    for line in CHECKED:
        sys.stdout.write("  ok    %s\n" % line)

    for line in FAILURES:
        sys.stdout.write("  FAIL  %s\n" % line)

    sys.stdout.write(
        "\n%d discrimination checks, %d failed\n" % (len(CHECKED), len(FAILURES))
    )

    return 1 if FAILURES else 0


if __name__ == "__main__":
    sys.exit(main())
