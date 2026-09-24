# SPDX-License-Identifier: Apache-2.0
"""The test steps.

Each function is one JUnit case. They assert against DESIGN.md rather than
against the implementation where the two could differ: the manifest keys come
from 6.2, the purge behaviour from 3.4, the funnel link shapes from 10.1 and
10.3, the restart constraint from 3.1.
"""

import io
import json
import os
import re
import statistics
import threading
import time
import urllib.parse
import urllib.request
import zipfile

from datetime import datetime, timedelta, timezone

from . import pluginconfig, scripts
from .generate import SNIPPET_COVERAGE, TITLE_COVERAGE, Generator
from .portal import ADMIN_PORTLET_ID
from .util import HarnessError, Stopwatch, log, wait_for

CYCLE_PORTLET_ID = "ai.tensoropt.sel.collection.cycle"

BUNDLE_SYMBOLIC_NAMES = (
    "ai.tensoropt.sel.api",
    "ai.tensoropt.sel.service",
    "ai.tensoropt.sel.impl",
    "ai.tensoropt.sel.web",
)

MARKER_TERM = "zarvexium"

# A term nothing can match, used as a live control on the search probe. If a
# search for this returns results, the probe is reading something other than
# the result set and every capture assertion downstream is worthless.
CONTROL_TERM = "qqzzxxnothinghere"

# The floor and the margin for the purge case's ambiguity band, in seconds.
# The band itself is derived at runtime from how long the counts actually take;
# see retention_purge. These only bound it from below.
SLACK_FLOOR_SECONDS = 120

SLACK_MARGIN_SECONDS = 60

# What a purge with nothing to delete may cost. Loose on purpose: it exists to
# catch a delete that stopped using its index, not to measure performance.
EMPTY_PURGE_BUDGET_MS = 5000

# Cases that fail because of a defect in the plugin rather than in the test.
#
# Naming them here rather than in prose is what lets a gate run today: the
# suite exits 0 on this exact set, an entry that starts passing is reported as
# a failure so it gets retired, and a run that breaks early cannot be mistaken
# for the baseline because its known failures turned into skips.
#
# Empty since TO-90 fixed D-4, the last of the defects the TO-84 run found.
# The mechanism stays: a new known defect is entered here with its ticket and
# retired in the change that fixes it.
EXPECTED_FAILURES = {}


# The record shape of DESIGN.md 6.2, exactly. Both sets are asserted on every
# line and every hit rather than sampled, and extra keys fail as well as
# missing ones: a field that appears without being specified is a schema change
# an evaluator has to be told about.
EVENT_KEYS = {
    "event_id",
    "created_at",
    "query",
    "query_truncated",
    "locale",
    "scope_group_ids",
    "entry_class_names",
    "applied_facets",
    "facet_capture_status",
    "blueprint_id",
    "audience_type",
    "cohort_hash",
    "requested_size",
    "requested_from",
    "total_hits",
    "logged_hit_count",
    "source_type",
    "hits",
}

HIT_KEYS = {
    "rank",
    "score",
    "doc_uid",
    "entry_class_name",
    "entry_class_pk",
    "title",
    "snippet",
    "extra_fields",
}

# SearchEvalLogDestinationConfigurator's _MAXIMUM_QUEUE_SIZE: how many events
# the destination holds before its rejection handler drops them.
QUEUE_SIZE = 2000

# Searches the queue-overflow case sends while the consumer is blocked, and
# from how many clients. Enough past QUEUE_SIZE that a drop count of zero
# cannot be an accident of timing.
OVERFLOW_SEARCHES = 2600

OVERFLOW_CLIENTS = 16

# What the foreign task's attachment contains, so a download that served it can
# be recognised whatever else the response carries.
FOREIGN_ATTACHMENT_CONTENT = "E2E-FOREIGN-ATTACHMENT-CONTENT"

# The rendered bodies of the two notifications, from the web module's
# Language.properties. The notifications list shows the body, not the title.
STALL_NOTIFICATION_TEXT = "Restart the portal to begin collecting."

READINESS_NOTIFICATION_TEXT = "Open Search Eval Export to produce the archive."

# Keys that would satisfy the shape checks while carrying nothing. A file whose
# every row has a null query is not a (query, result[]) log.
REQUIRED_NON_NULL_EVENT_KEYS = ("event_id", "query", "source_type")

# What "an address belonging to the evaluation service" looks like.
#
# The placeholders are what ships today. The hosts are listed as well, and that
# is the point: a release that substitutes real URLs must not quietly turn
# every egress assertion into a check that nothing spells "{{SIGNUP_URL}}" any
# more. Whoever substitutes them adds the real host here.
#
# The bare word "tensoropt" is deliberately not in this set. It is the
# plugin's own package name, so it appears in the portlet id, in every
# namespaced form field and in every portlet URL on its own screen; treating
# it as a vendor address on a page would fail on the plugin simply being
# installed.
FUNNEL_PLACEHOLDERS = ("{{SIGNUP_URL}}", "{{BOOKING_URL}}")

VENDOR_HOSTS = ("tensoropt.ai", "tensoropt.com", "tensoropt.io")

VENDOR_ADDRESSES = FUNNEL_PLACEHOLDERS + VENDOR_HOSTS

# Inside an archive the bare word is fair game and worth keeping. DESIGN.md
# 10.4 says the dataset stays vendor neutral whoever evaluates it, so the
# plugin naming itself in a manifest would be a finding rather than a false
# positive. Nothing in an archive contains it today.
ARCHIVE_FORBIDDEN = VENDOR_ADDRESSES + (
    "tensoropt",
    "utm_source",
    "utm_medium",
)

# The bundle DESIGN.md 3.1 and EC-3 name first among the consumers that bind
# Searcher statically and reluctantly. It is the one that serves the Search
# Results widget, which is the path EC-1 confirmed and the path this harness
# searches on, so it is the consumer whose binding decides whether anything is
# captured at all.
SEARCH_CONSUMER_BUNDLE = "com.liferay.portal.search.web"

# How long to give a background task to appear before concluding it never
# will. Used on both sides of the permission case, so the refusal and the
# positive control are measured the same way.
PERMISSION_SETTLE_SECONDS = 30


def acceptable_utc_dates(now):
    """Today and yesterday, as ISO dates."""
    return [now.isoformat(), (now - timedelta(days=1)).isoformat()]


def is_recent_utc_date(value, now):
    return value in acceptable_utc_dates(now)


def plugin_configuration(directory):
    """The collector's configuration, read from the file that sets it."""
    return pluginconfig.read(directory)


def assert_true(condition, message):
    if not condition:
        raise AssertionError(message)


def assert_equal(actual, expected, message):
    if actual != expected:
        raise AssertionError("%s: expected %r, got %r" % (message, expected, actual))


class Context:
    """Everything the steps share, and nothing else.

    The collector's settings are read from the .config file this run installs,
    not restated here. A test that hard coded them would compare one copy of a
    literal against another and keep passing after the file changed.
    """

    def __init__(self, stack, portal, options):
        self.stack = stack
        self.portal = portal
        self.options = options
        self.company_id = None
        self.group_id = None
        self.archive_path = None
        self.archive_name = None
        self.download_url = None
        self.export_range = None
        self.manifest = None
        self.collection_start_date = None

        self.configuration = plugin_configuration(options.directory)

        self.retention_days = int(self.configuration["retentionDays"])
        self.readiness_minimum_days = int(
            self.configuration["readinessMinimumDays"]
        )
        self.readiness_minimum_events = int(
            self.configuration["readinessMinimumEvents"]
        )


# Setup


def stack_up(context, case):
    stack = context.stack

    stack.up()
    stack.wait_for_postgres(timeout=300)

    context.portal.wait_until_serving(timeout=context.options.boot_timeout)

    context.company_id = context.portal.company_id()
    context.group_id = context.portal.group_id(context.company_id)

    case.note("company id %d, guest group id %d" % (context.company_id, context.group_id))

    log_text = stack.liferay_log()

    licences = re.findall(r"License registered for ([^\r\n]+)", log_text)

    case.note("license mode: %s" % (licences[-1].strip() if licences else "none logged"))

    assert_true(
        "Liferay Digital Experience Platform 2025.Q1.27" in log_text,
        "The portal did not report the expected product version",
    )

    # One search before the plugin exists, so Liferay's search consumers bind
    # the portal's own Searcher. That is what "installed onto a running
    # portal" means in DESIGN.md 3.1, and without it the registry check has
    # nothing to find: a consumer that has never activated is not yet bound to
    # anything, and the plugin would correctly report no bypass.

    response = context.portal.search("warm up the search path")

    assert_equal(
        response.status, 200, "The Search Results widget page did not answer"
    )


def plugin_absent(context, case):
    """The plugin is not installed yet, which is the starting state EC-3 needs."""
    count = context.stack.psql_long(
        "select count(*) from information_schema.tables "
        "where table_schema = 'public' and table_name ilike 'sel_search%'"
    )

    assert_equal(count, 0, "SEL tables exist before the plugin was installed")


def install_onto_running_portal(context, case):
    """Installs the four bundles onto a portal that is already serving.

    This is the case DESIGN.md 3.1 warns about, and the harness reproduces it
    on purpose rather than avoiding it: the next step asserts that the plugin
    notices.
    """
    deploy_directory = os.path.join(context.options.directory, ".work", "deploy")

    os.makedirs(deploy_directory, exist_ok=True)

    # One anchor for every bundle, taken before the first jar lands. See
    # Stack.wait_for_bundle_started.

    deployed_at = time.monotonic()

    for jar in context.options.jars:
        target = os.path.join(deploy_directory, os.path.basename(jar))

        with open(jar, "rb") as source:
            payload = source.read()

        with open(target, "wb") as destination:
            destination.write(payload)

        case.note("deployed %s (%d bytes)" % (os.path.basename(jar), len(payload)))

    for symbolic_name in BUNDLE_SYMBOLIC_NAMES:
        context.stack.wait_for_bundle_started(
            symbolic_name, deployed_at, timeout=context.options.deploy_timeout
        )

    def tables_created():
        return context.stack.psql_long(
            "select count(*) from information_schema.tables "
            "where table_schema = 'public' and table_name ilike 'sel_search%'"
        ) == 2

    wait_for("the SEL tables to be created", tables_created, timeout=300, interval=3.0)

    indexes = context.stack.psql_long(
        "select count(*) from pg_indexes where schemaname = 'public' "
        "and tablename ilike 'sel_search%'"
    )

    case.note("two tables and %d indexes created by Service Builder" % indexes)


def ec3_bypass_detected(context, case):
    """DESIGN.md 3.1: installed onto a running portal, nothing is intercepted.

    The assertion is on the plugin's own detector rather than on an empty
    table, because an empty table is also what a plugin that simply does not
    work looks like.
    """
    registry = _searcher_registry(context, case)

    wrapper = [entry for entry in registry if entry["marker"]]

    assert_true(
        wrapper,
        "The wrapper did not register a Searcher with its marker property",
    )

    assert_equal(
        wrapper[0]["ranking"],
        "100",
        "The wrapper is not registered at the ranking DESIGN.md 3.1 specifies",
    )

    # The registry, not the detector's opinion of it. This is the whole of
    # EC-3 in one reading: the widget's own bundle is holding the portal's
    # searcher, the wrapper has no consumers at all, and a static reluctant
    # reference will never move.

    assert_true(
        SEARCH_CONSUMER_BUNDLE in _users_of_portal_searcher(registry),
        "%s is not bound to the portal's own Searcher, so this portal never "
        "served a search before the plugin was installed and there is no "
        "bypass to detect" % SEARCH_CONSUMER_BUNDLE,
    )

    assert_true(
        SEARCH_CONSUMER_BUNDLE not in wrapper[0]["users"],
        "%s is already using the wrapper before any restart" % SEARCH_CONSUMER_BUNDLE,
    )

    case.note(
        "%s is bound to the portal's own Searcher and the wrapper has %d "
        "consumers" % (SEARCH_CONSUMER_BUNDLE, len(wrapper[0]["users"]))
    )

    intercepting = context.portal.run_script(scripts.INTERCEPTION_STATUS)

    assert_equal(
        intercepting,
        "false",
        "SearchInterceptionStatus reported interception as live on a portal "
        "that was not restarted after the plugin was installed",
    )

    text = context.portal.admin_screen()

    assert_true(
        "Restart required" in text,
        "The admin screen did not show the restart warning of DESIGN.md 3.1",
    )

    log_text = context.stack.liferay_log()

    assert_true(
        "Search interception is not active" in log_text,
        "The activation warning of DESIGN.md 3.1 is not in the portal log",
    )


def stall_notification(context, case):
    """DESIGN.md 3.6 condition 1, and the runtime half of EC-14.

    Running the daily check by hand is the only way to see this inside a CI
    run. It opens the cycle, finds interception bypassed and nothing collected,
    and notifies once.
    """
    context.portal.run_script(
        scripts.CYCLE_CHECK % {"company_id": context.company_id}
    )

    cycle = _read_cycle(context)

    case.note("cycle after the check: %s" % cycle)

    assert_equal(cycle.get("open"), "true", "The daily check did not open a cycle")
    assert_true(
        cycle.get("stallNotifiedDate"),
        "No stall notification was recorded while interception was bypassed",
    )

    payloads = _notification_payloads(context)

    case.note("stored notifications: %s" % payloads)

    assert_true(
        any("STALL" in payload for payload in payloads),
        "EC-14: no stall notification reached UserNotificationEvent",
    )

    assert_notification_listed(
        context.portal.notifications_list(), STALL_NOTIFICATION_TEXT
    )


# When each daily job fires, in the portal's time zone, which is UTC in the
# test stack. A time of day rather than an interval (TO-110).
DAILY_JOB_TIMES = {
    "ai.tensoropt.sel.internal.scheduler.RetentionPurgeSchedulerJobConfiguration":
        "03:00:00",
    "ai.tensoropt.sel.internal.scheduler.CollectionCycleSchedulerJobConfiguration":
        "03:30:00",
}


def daily_jobs_scheduled(context, case):
    """TO-110: the daily jobs fire at a time of day, whenever the portal started.

    Runs after restart-clears-bypass, so the jobs have just been registered
    again. A one-day interval trigger would now next fire 24 hours after that
    restart, at whatever time of day it happened; a restart at least daily
    would then keep either job from ever running.
    """
    jobs = json.loads(context.portal.run_script(scripts.SCHEDULED_JOBS))

    for job in jobs:
        case.note("%s next %s, then %s" % (job["job"], job["next"], job["following"]))

    assert_daily_schedule(jobs, datetime.now(timezone.utc))


def assert_daily_schedule(jobs, now):
    """Each job next fires within a day, at its time of day, then daily."""
    by_name = {job["job"]: job for job in jobs}

    for name, time_of_day in DAILY_JOB_TIMES.items():
        assert_true(name in by_name, "%s is not scheduled" % name)

        job = by_name[name]
        next_fire = _parse_instant(job["next"])
        following = _parse_instant(job["following"])

        assert_true(
            now < next_fire <= now + timedelta(days=1),
            "%s next fires at %s, not within a day of %s" % (name, next_fire, now),
        )
        assert_equal(
            next_fire.strftime("%H:%M:%S"),
            time_of_day,
            "%s fires at the wrong time of day, which is what an interval "
            "counted from the last restart looks like" % name,
        )
        assert_equal(
            following - next_fire,
            timedelta(days=1),
            "%s does not fire daily" % name,
        )


def _parse_instant(value):
    return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(
        tzinfo=timezone.utc
    )


def restart_clears_bypass(context, case):
    """DESIGN.md 3.1: the restart is what makes the consumers bind the wrapper."""
    stopwatch = Stopwatch()

    context.stack.restart_liferay()

    context.portal.wait_until_serving(timeout=context.options.boot_timeout)

    case.note("restart to serving: %.0fs" % stopwatch.seconds())

    intercepting = context.portal.run_script(scripts.INTERCEPTION_STATUS)

    assert_equal(
        intercepting, "true", "Interception is still bypassed after a restart"
    )

    # The other half of the EC-3 reading. Before the restart the widget's
    # bundle was a consumer of the portal's own searcher; after it, it has to
    # be a consumer of the wrapper. Asserting only the detector's answer would
    # pass on a portal where the search consumers had simply never activated.

    registry = _searcher_registry(context, case)

    wrapper = [entry for entry in registry if entry["marker"]]

    assert_true(wrapper, "The wrapper is no longer registered after the restart")

    assert_true(
        SEARCH_CONSUMER_BUNDLE in wrapper[0]["users"],
        "%s did not move onto the wrapper across the restart, so searches "
        "from the widget still bypass it" % SEARCH_CONSUMER_BUNDLE,
    )

    case.note(
        "%s is now a consumer of the wrapper" % SEARCH_CONSUMER_BUNDLE
    )

    text = context.portal.admin_screen()

    assert_true(
        "Restart required" not in text,
        "The admin screen still shows the restart warning after a restart",
    )


def content_and_searches(context, case):
    """Creates content, waits until it is findable, then runs real searches.

    The searches go to the Search Results widget page, which is the path EC-1
    confirmed and the only one available without the feature flag EC-5 is
    blocked on.
    """
    created = 0

    for index in range(context.options.content_count):
        status, _ = context.portal.headless(
            "POST",
            "/o/headless-delivery/v1.0/sites/%d/blog-postings" % context.group_id,
            {
                "headline": "%s policy note %d" % (MARKER_TERM.title(), index),
                "articleBody": (
                    "%s is the marker term this end to end run searches for. "
                    "Note %d covers leave, expenses and travel."
                    % (MARKER_TERM.title(), index)
                ),
            },
        )

        assert_equal(status, 200, "Creating a blog posting failed")

        created += 1

    case.note("created %d blog postings" % created)

    # A live control on the probe itself, before the probe is trusted.
    #
    # The earlier version of this step waited for the marker term to appear
    # anywhere in the page, which is satisfied by a search that matches
    # nothing: Liferay echoes the query back in Liferay.currentURL, in
    # currentURLEncoded and in the product menu's redirect parameter. The wait
    # therefore returned on its first poll and never waited, and on a runner
    # where indexing lags the headless POST the five searches below would have
    # returned nothing and the capture case would have blamed the plugin.
    #
    # Asking for a term nothing can match, and requiring the widget to report
    # zero, is what makes the count below evidence rather than an assumption.

    control_count, control_response = context.portal.search_result_count(
        CONTROL_TERM
    )

    assert_true(
        CONTROL_TERM in control_response.text,
        "The control term is not echoed back at all, so this page is not the "
        "search page and the probe is reading the wrong thing",
    )

    assert_equal(
        control_count,
        0,
        "A search for a term nothing can match did not report zero results, "
        "so the result count is not being read from the widget's own total",
    )

    case.note(
        "control search for %r reported %d results while its term appears in "
        "the page" % (CONTROL_TERM, control_count)
    )

    headline = "%s policy note 0" % MARKER_TERM.title()

    def findable():
        count, response = context.portal.search_result_count(MARKER_TERM)

        if not count:
            return None

        # The count alone would pass against any indexed content that happens
        # to match. The headline has to be in a result row as well, which is
        # what ties the searches to the postings this run created.
        #
        # Matched against the page's visible text rather than its markup. The
        # widget wraps every matched term in <span class="highlight mark">, so
        # "Zarvexium policy note 0" is never contiguous in the source and a
        # substring test against the raw HTML waits for something that will
        # never appear.

        if headline.lower() not in visible_text(response.text).lower():
            return None

        return count

    found = wait_for(
        "the created content to become searchable",
        findable,
        timeout=context.options.index_timeout,
        interval=3.0,
    )

    case.note("marker search reports %d results including %r" % (found, headline))

    for index in range(context.options.search_count):
        count, _ = context.portal.search_result_count(
            "%s note %d" % (MARKER_TERM, index)
        )

        assert_true(
            count,
            "Search %d returned no results, so there is nothing for the "
            "plugin to capture hits from" % index,
        )

    case.note(
        "ran %d searches through the Search Results widget, all with results"
        % context.options.search_count
    )


def capture(context, case):
    """DESIGN.md 3.2 and 3.3: admitted searches reach the database.

    The counters are read off the plugin's own screen. Non-zero counters also
    settle whether the configuration file took effect, because the wrapper
    increments nothing at all while collection is disabled.
    """

    # Settled as well as persisted: admitted = persisted + dropped only holds
    # once nothing is in flight, and the searches that other cases issue can
    # still be on the queue when the marker searches have landed.

    def persisted():
        counters = context.portal.admission_counters()

        if (counters["persisted"] >= context.options.search_count) and (
            counters["admitted"] == counters["persisted"] + counters["dropped"]
        ):
            return counters

        return None

    counters = wait_for(
        "the admitted searches to be persisted and the funnel to settle",
        persisted,
        timeout=180,
        interval=3.0,
    )

    case.note("admission counters: %s" % counters)

    assert_funnel_adds_up(counters)
    assert_equal(counters["dropped"], 0, "Events were dropped under no load")

    rows = context.stack.psql(
        "select count(*), min(sourceType), min(audienceType), "
        "count(distinct cohortHash) from SEL_SearchEvent "
        "where queryText like '%%%s%%'" % MARKER_TERM
    )

    count, source_type, audience_type, cohorts = rows[0]

    case.note(
        "captured %s marker events, sourceType=%s audienceType=%s cohorts=%s"
        % (count, source_type, audience_type, cohorts)
    )

    assert_true(int(count) >= context.options.search_count, "Marker events missing")
    assert_equal(source_type, "WIDGET", "DESIGN.md 3.2 sourceType classification")

    hits = context.stack.psql_long(
        "select count(*) from SEL_SearchHit h join SEL_SearchEvent e "
        "on h.searchEventUuid = e.uuid_ where e.queryText like '%%%s%%'"
        % MARKER_TERM
    )

    case.note("captured %d hits for the marker events" % hits)

    assert_true(hits > 0, "No hits were captured for searches that returned results")

    titles = context.stack.psql_long(
        "select count(*) from SEL_SearchHit h join SEL_SearchEvent e "
        "on h.searchEventUuid = e.uuid_ where e.queryText like '%%%s%%' "
        "and h.title is not null and h.title <> ''" % MARKER_TERM
    )

    case.note("EC-4: %d of %d captured hits carry a title" % (titles, hits))

    orphans = context.stack.psql_long(
        "select count(*) from SEL_SearchHit h left join SEL_SearchEvent e "
        "on h.searchEventUuid = e.uuid_ where e.uuid_ is null"
    )

    assert_equal(orphans, 0, "Captured hits with no event")


def assert_funnel_adds_up(counters):
    """The admin screen's six counters, as TO-92 defines them.

    Each stage is a subset of the one before, and every admitted event ends
    up persisted or dropped: one admitted and then counted nowhere breaks the
    last equation. A queue rejection counted as dispatched as well as dropped
    does not, since it leaves that sum intact; SearchEventDispatcherTest
    covers it, and a run under no load never fills the queue anyway.
    """
    assert_true(
        counters["observed"] >= counters["keywords"] >= counters["admitted"],
        "The admission funnel is not monotonic",
    )
    assert_true(
        counters["admitted"] >= counters["dispatched"] >= counters["persisted"],
        "More events dispatched than admitted, or persisted than dispatched",
    )
    assert_equal(
        counters["admitted"],
        counters["persisted"] + counters["dropped"],
        "Admitted events are neither persisted nor dropped, or counted as both",
    )


def collection_start_recorded(context, case):
    """DESIGN.md 3.6: the cycle records the day the first event was persisted."""

    def started():
        cycle = _read_cycle(context)

        if cycle.get("collectionStartDate"):
            return cycle

        return None

    cycle = wait_for(
        "the collection start date to be recorded", started, timeout=180, interval=3.0
    )

    context.collection_start_date = cycle["collectionStartDate"]

    case.note("collectionStartDate = %s" % context.collection_start_date)

    # Today or yesterday, in UTC.
    #
    # The date is written when the first event is persisted and read back
    # here, minutes later. A run that straddles UTC midnight reads a date that
    # is correct and is not the one computed after the fact, and at this
    # duration that is a few runs in a thousand. A test that is wrong once a
    # year at 00:00 is a flake, and a flake in a gate is worse than a slightly
    # looser assertion.

    now = datetime.now(timezone.utc).date()

    assert_true(
        is_recent_utc_date(context.collection_start_date, now),
        "The collection start date %r is neither today nor yesterday in UTC "
        "(%s)" % (
            context.collection_start_date,
            " or ".join(acceptable_utc_dates(now)),
        ),
    )


# Scale, purge, export


def generate_scale_data(context, case):
    generator = Generator(context.stack, context.company_id, context.group_id)

    result = generator.generate(
        context.options.events,
        context.options.hits_per_event,
        context.options.days_span,
        chunk=context.options.chunk,
        note=case.note,
    )

    case.note(
        "generated %d events and %d hits in %.1fs"
        % (result["events"], result["hits"], result["seconds"])
    )

    seconds = generator.analyze()

    case.note("analyze took %.1fs" % seconds)

    events = context.stack.psql_long("select count(*) from SEL_SearchEvent")
    hits = context.stack.psql_long("select count(*) from SEL_SearchHit")

    case.note("table totals: %d events, %d hits" % (events, hits))

    assert_true(events >= result["events"], "Generated events are not all present")
    assert_true(hits >= result["hits"], "Generated hits are not all present")

    mismatched = context.stack.psql_long(
        "select count(*) from SEL_SearchHit h join SEL_SearchEvent e "
        "on h.searchEventUuid = e.uuid_ "
        "where h.createDate <> e.createDate or h.companyId <> e.companyId"
    )

    assert_equal(
        mismatched,
        0,
        "DESIGN.md 4.2: a hit's duplicated companyId and createDate drifted "
        "from its event's",
    )


def retention_purge(context, case):
    """DESIGN.md 3.4, against a corpus that straddles the retention cutoff.

    The cutoff is read from the database once and then used as a literal on
    both sides of the purge. Writing the predicate as "older than now() minus
    thirty days" instead would move it between the reading before and the
    reading after, and a row sitting near the boundary would change category
    without anything having deleted it.

    A band either side of the cutoff is left unasserted, because the purge
    takes its own clock reading when it runs, some time after this one, so
    rows inside that band are legitimately ambiguous. Everything outside it has
    an unambiguous expected fate.

    **The band is measured, not assumed.** What separates the harness's clock
    reading from the purge's is four full table counts and a script console
    round trip, and at the heavy tier those counts are over a million events
    and ten million hits. A fixed two minutes would be generous at the smoke
    tier and too small at the heavy one, and the way it fails is the worst
    kind: kept_events stops matching newer_events because the purge correctly
    took rows the harness had already counted as safe, and the case reports a
    purge that deleted inside its window. So the first counts are timed, and
    the band is derived from what they cost.
    """
    stopwatch = Stopwatch()

    cutoff = context.stack.psql_scalar(
        "select to_char((now() at time zone 'UTC') - interval '%d days', "
        "'YYYY-MM-DD HH24:MI:SS')" % context.retention_days
    )

    older_events = _count_older_than(context, "SEL_SearchEvent", cutoff, 0)
    older_hits = _count_older_than(context, "SEL_SearchHit", cutoff, 0)

    assert_true(
        older_events > 0 and older_hits > 0,
        "Nothing was older than the retention window, so the purge would pass "
        "by doing nothing",
    )

    # Two counts cost this much, so allow for two more plus the round trip,
    # with a floor for the case where everything is fast.

    measured = stopwatch.seconds()

    band = max(SLACK_FLOOR_SECONDS, int(measured * 2) + SLACK_MARGIN_SECONDS)

    case.note(
        "retention cutoff %s UTC; two counts took %.1fs, so the ambiguity "
        "band is %ds" % (cutoff, measured, band)
    )

    newer_events = _count_newer_than(context, "SEL_SearchEvent", cutoff, band)
    newer_hits = _count_newer_than(context, "SEL_SearchHit", cutoff, band)

    case.note(
        "before: %d events and %d hits past the %d day window, %d and %d "
        "safely inside"
        % (older_events, older_hits, context.retention_days, newer_events,
           newer_hits)
    )

    first = int(
        context.portal.run_script(
            scripts.PURGE % {"company_id": context.company_id}
        )
    )

    case.note(
        "purge of %d events and %d hits reported %d ms"
        % (older_events, older_hits, first)
    )

    assert_true(
        stopwatch.seconds() < band,
        "The counts and the purge together took longer than the %ds "
        "ambiguity band they are measured against, so rows near the cutoff "
        "cannot be classified and the comparison below would be unsound" % band,
    )

    remaining_events = _count_older_than(context, "SEL_SearchEvent", cutoff, band)
    remaining_hits = _count_older_than(context, "SEL_SearchHit", cutoff, band)

    assert_equal(remaining_events, 0, "Events past the retention window survived")
    assert_equal(remaining_hits, 0, "Hits past the retention window survived")

    kept_events = _count_newer_than(context, "SEL_SearchEvent", cutoff, band)
    kept_hits = _count_newer_than(context, "SEL_SearchHit", cutoff, band)

    case.note("after: %d events and %d hits inside the window" % (kept_events, kept_hits))

    assert_equal(
        kept_events, newer_events, "The purge deleted events inside the window"
    )
    assert_equal(kept_hits, newer_hits, "The purge deleted hits inside the window")

    orphans = context.stack.psql_long(
        "select count(*) from SEL_SearchHit h left join SEL_SearchEvent e "
        "on h.searchEventUuid = e.uuid_ where e.uuid_ is null"
    )

    assert_equal(orphans, 0, "The purge left hits whose event is gone")

    second = int(
        context.portal.run_script(
            scripts.PURGE % {"company_id": context.company_id}
        )
    )

    case.note("second purge, nothing to delete: %d ms" % second)

    # DESIGN.md 3.4 says the steady state is a job that deletes roughly one
    # day of rows, and the repository README reports 13 ms for a run with
    # nothing to do. The bound below is deliberately loose and is not a
    # performance test: it is there so that a purge which started scanning the
    # table instead of using the (companyId, createDate) index is visible.
    # Two set based deletes matching no rows cannot take seconds at any scale
    # this harness generates.

    assert_true(
        second < EMPTY_PURGE_BUDGET_MS,
        "A purge with nothing to delete took %d ms, over the %d ms this case "
        "allows. Two indexed deletes matching nothing should be immediate at "
        "any scale, so this suggests the delete is no longer index driven"
        % (second, EMPTY_PURGE_BUDGET_MS),
    )


def funnel_readiness(context, case):
    """DESIGN.md 3.6 condition 2, against the configured thresholds.

    The collection start date is moved back rather than the thresholds moved
    down. Lowering readinessMinimumDays to zero would exercise a comparison
    against zero, which is not the comparison an installation makes.
    """
    backdated = (
        datetime.now(timezone.utc).date()
        - timedelta(days=context.readiness_minimum_days + 1)
    ).isoformat()

    context.portal.run_script(
        scripts.CYCLE_BACKDATE
        % {
            "company_id": context.company_id,
            "portlet_id": CYCLE_PORTLET_ID,
            "collection_start_date": backdated,
        }
    )

    context.collection_start_date = backdated

    case.note("collectionStartDate moved back to %s" % backdated)

    countable = context.stack.psql_long(
        "select count(*) from SEL_SearchEvent where companyId = %d "
        "and createDate >= timestamp '%s 00:00:00'" % (context.company_id, backdated)
    )

    case.note(
        "%d events on or after the collection start date, threshold %d"
        % (countable, context.readiness_minimum_events)
    )

    assert_true(
        countable >= context.readiness_minimum_events,
        "Too few surviving events to reach the readiness threshold. DESIGN.md "
        "3.6 notes that the retention purge caps this count; raise the scale "
        "tier or shorten the day span.",
    )

    context.portal.run_script(
        scripts.CYCLE_CHECK % {"company_id": context.company_id}
    )

    cycle = _read_cycle(context)

    case.note("cycle after the readiness check: %s" % cycle)

    assert_true(
        cycle.get("readinessNotifiedDate"),
        "The readiness notification was not recorded",
    )

    payloads = _notification_payloads(context)

    assert_true(
        any("READINESS" in payload for payload in payloads),
        "EC-14: no readiness notification reached UserNotificationEvent",
    )

    assert_notification_listed(
        context.portal.notifications_list(), READINESS_NOTIFICATION_TEXT
    )

    text = context.portal.admin_screen()

    assert_true(
        "Your search log is ready to export" in text,
        "The readiness banner of DESIGN.md 3.6 is not on the admin screen",
    )


def run_export(context, case):
    """DESIGN.md 6.1: an admin action, a background task, a downloadable archive.

    The range is stated explicitly rather than left open. Leaving both dates
    empty is what an administrator would do for a full window export, and on
    PostgreSQL it fails; that is asserted on its own in
    export_unbounded_range, so it is recorded as the defect it is instead of
    taking the rest of the export path down with it.

    The window starts one day before the retention cutoff, so it covers
    everything the purge left and nothing has to be excluded from the count
    assertions.
    """
    today = datetime.now(timezone.utc).date()

    start_date = (
        today - timedelta(days=context.retention_days + 1)
    ).isoformat()
    end_date = today.isoformat()

    case.note("export range %s to %s" % (start_date, end_date))

    before = context.portal.start_export(
        start_date=start_date, end_date=end_date
    )

    row = context.portal.wait_for_export(
        timeout=context.options.export_timeout, after=before
    )

    case.note("background task: %s" % row)

    assert_true(row["download_url"], "A successful export offered no download")

    destination = os.path.join(context.options.results_directory, "export.zip")

    stopwatch = Stopwatch()

    size, file_name = context.portal.download(row["download_url"], destination)

    case.note(
        "downloaded %s, %.1f MB in %.1fs"
        % (file_name, size / (1024.0 * 1024.0), stopwatch.seconds())
    )

    context.archive_path = destination
    context.archive_name = file_name
    context.download_url = row["download_url"]
    context.export_range = (start_date, end_date)


def export_archive_name(context, case):
    """DESIGN.md 6.2 names the archive search-eval-export-<instance>-<from>-<to>.

    The name is asserted exactly, against the dates the administrator asked
    for. A pattern of two runs of eight digits would accept any pair of dates,
    which is how the deviation below stayed invisible.

    This was D-4, fixed by TO-90. The action makes the end date exclusive by
    adding a day before handing it to the background task, and the task used
    to format that same value into the file name, so an export requested "to
    2026-09-23" arrived called ...-20260924.zip. The range was right and the
    label on it was not, which matters because the name is what the archive is
    filed under once it leaves the instance. ExportArchiveNames now names the
    last day the range includes.
    """
    start_date, end_date = context.export_range

    expected = "search-eval-export-%d-%s-%s.zip" % (
        context.company_id,
        start_date.replace("-", ""),
        end_date.replace("-", ""),
    )

    case.note("requested %s to %s" % (start_date, end_date))
    case.note("offered   %s" % context.archive_name)
    case.note("expected  %s" % expected)

    assert_equal(
        context.archive_name,
        expected,
        "The archive name does not carry the requested range",
    )


def validate_archive(context, case):
    """DESIGN.md 6.2, entry by entry and key by key."""
    with zipfile.ZipFile(context.archive_path) as archive:
        names = sorted(archive.namelist())

        assert_equal(
            names,
            ["README.md", "events.jsonl", "manifest.json"],
            "The archive does not hold exactly the three entries of DESIGN.md 6.2",
        )

        manifest = json.loads(archive.read("manifest.json").decode("utf-8"))

        context.manifest = manifest

        for key in (
            "company_id",
            "exported_at",
            "export_range",
            "counts",
            "plugin_version",
            "liferay_version",
            "configuration",
            "admission_counters",
            "field_coverage_rates",
        ):
            assert_true(key in manifest, "manifest.json has no %r" % key)

        for key in ("events", "hits"):
            assert_true(key in manifest["counts"], "counts has no %r" % key)

        for key in (
            "observed_search_count",
            "keyword_search_count",
            "admitted_search_count",
            "dispatched_event_count",
            "dropped_event_count",
            "persisted_event_count",
            "scope",
        ):
            assert_true(
                key in manifest["admission_counters"],
                "admission_counters has no %r" % key,
            )

        manifest_events = int(manifest["counts"]["events"])
        manifest_hits = int(manifest["counts"]["hits"])

        case.note(
            "manifest counts: %d events, %d hits" % (manifest_events, manifest_hits)
        )

        assert_manifest_numbers(manifest)

        expectations = pluginconfig.manifest_expectations(context.configuration)

        assert_true(
            expectations,
            "The configuration file set nothing the manifest reports back, so "
            "this comparison would prove nothing",
        )

        for key, expected in expectations.items():
            assert_equal(
                manifest["configuration"][key],
                expected,
                "The effective configuration read back from the manifest does "
                "not match the .config file, so the file landed at a pid or "
                "scope nothing reads (%s)" % key,
            )

        case.note(
            "effective configuration matches the osgi/configs file on %d "
            "settings" % len(expectations)
        )

        readme = archive.read("README.md").decode("utf-8")

        assert_true(len(readme) > 200, "The archive README is empty")

        events, hits, _ = _stream_events(archive, case)

        case.note(
            "events.jsonl: %d lines, %d nested hits, every line and every hit "
            "carrying the DESIGN.md 6.2 key set" % (events, hits)
        )

        assert_equal(
            events, manifest_events, "Line count does not match the manifest"
        )
        assert_equal(hits, manifest_hits, "Hit count does not match the manifest")

    database_events = context.stack.psql_long(
        "select count(*) from SEL_SearchEvent where companyId = %d"
        % context.company_id
    )

    assert_equal(
        int(manifest["counts"]["events"]),
        database_events,
        "The export did not cover every row the purge left in the table",
    )

    coverage = manifest["field_coverage_rates"]

    case.note("field coverage: %s" % coverage)

    assert_true(
        abs(coverage.get("title", 0) - TITLE_COVERAGE) < 0.02,
        "Reported title coverage is not the ratio the generator wrote",
    )
    assert_true(
        abs(coverage.get("snippet", 0) - SNIPPET_COVERAGE) < 0.02,
        "Reported snippet coverage is not the ratio the generator wrote",
    )


def export_range_is_honoured(context, case):
    """DESIGN.md 6.1: the export covers the range the administrator chose.

    Nothing else in the suite would notice an exporter that ignored its start
    and end dates: the wide export is checked against the whole table, which is
    exactly what an exporter that ignored the range would produce. A narrow
    window with an independently known row count is what makes the range mean
    something.
    """
    end = datetime.now(timezone.utc).date()
    start = end - timedelta(days=2)

    start_date = start.isoformat()
    end_date = end.isoformat()

    expected = context.stack.psql_long(
        "select count(*) from SEL_SearchEvent where companyId = %d "
        "and createDate >= timestamp '%s 00:00:00' "
        "and createDate < timestamp '%s 00:00:00'"
        % (context.company_id, start_date, (end + timedelta(days=1)).isoformat())
    )

    total = context.stack.psql_long(
        "select count(*) from SEL_SearchEvent where companyId = %d"
        % context.company_id
    )

    case.note(
        "range %s to %s holds %d of %d events" % (start_date, end_date, expected, total)
    )

    assert_true(
        0 < expected < total,
        "The narrow window is either empty or the whole table, so it cannot "
        "distinguish an exporter that honours the range from one that ignores "
        "it",
    )

    before = context.portal.start_export(
        start_date=start_date, end_date=end_date
    )

    row = context.portal.wait_for_export(
        timeout=context.options.export_timeout, after=before
    )

    destination = os.path.join(context.options.results_directory, "export-range.zip")

    size, file_name = context.portal.download(row["download_url"], destination)

    case.note("downloaded %s, %d bytes" % (file_name, size))

    with zipfile.ZipFile(destination) as archive:
        manifest = json.loads(archive.read("manifest.json").decode("utf-8"))

        counted = int(manifest["counts"]["events"])

        lower = "%sT00:00:00Z" % start_date
        upper = "%sT00:00:00Z" % (end + timedelta(days=1)).isoformat()

        outside = 0
        lines = 0

        with archive.open("events.jsonl") as entry:
            for raw_line in io.TextIOWrapper(entry, encoding="utf-8"):
                if not raw_line.strip():
                    continue

                lines += 1

                created_at = json.loads(raw_line).get("created_at")

                if created_at is None or not (lower <= created_at < upper):
                    outside += 1

    case.note("manifest reports %d events, file holds %d lines" % (counted, lines))

    assert_equal(
        counted, expected, "The export did not cover exactly the chosen range"
    )
    assert_equal(lines, expected, "events.jsonl does not hold the chosen range")
    assert_equal(
        outside, 0, "events.jsonl carries rows from outside the chosen range"
    )


def export_unbounded_range(context, case):
    """DESIGN.md 6.1: both dates empty means the full window.

    The export screen says so in as many words ("Leave a field empty to leave
    that end of the range unbounded"), and it is what an administrator running
    their first export will do.

    This was D-1, fixed by TO-87. SearchEventLocalServiceImpl._toTimestamp
    used to substitute new Timestamp(Long.MIN_VALUE) and
    new Timestamp(Long.MAX_VALUE) for a missing bound, and PostgreSQL rejects
    both with "timestamp out of range", so the background task ended as Failed
    and the archive was never written. An absent bound now leaves its predicate
    out of the statement instead.

    All three combinations are still run, because a fix that only covered the
    combination someone happened to try is the same defect with a smaller blast
    radius, and that is worth measuring rather than reasoning about. Each one
    downloads its archive and is asserted against the rows the range actually
    holds: a case that stopped at "the task succeeded" would accept an export
    whose predicate went missing in the other direction and returned nothing.
    """
    today = datetime.now(timezone.utc).date()

    # The half bounded combinations use a bound inside the data rather than
    # one outside it. A bound before the first surviving row selects the whole
    # table, which is also what an exporter that dropped the predicate
    # altogether would return, so the case would not be able to tell them
    # apart. The midpoint makes each half bounded export a strict subset, and
    # the assertion below refuses to run if it is not.

    midpoint = today - timedelta(days=max(1, context.retention_days // 2))

    # The screen makes the end bound exclusive by taking the start of the
    # following day, so that, and not the day the administrator typed, is what
    # the archive has to report and what the row count has to be taken against.

    exclusive_midpoint = (midpoint + timedelta(days=1)).isoformat()

    combinations = (
        ("both bounds empty", None, None, None, None),
        (
            "start empty",
            None,
            midpoint.isoformat(),
            None,
            "%sT00:00:00Z" % exclusive_midpoint,
        ),
        (
            "end empty",
            midpoint.isoformat(),
            None,
            "%sT00:00:00Z" % midpoint.isoformat(),
            None,
        ),
    )

    total = context.stack.psql_long(
        "select count(*) from SEL_SearchEvent where companyId = %d"
        % context.company_id
    )

    failures = []

    for index, combination in enumerate(combinations):
        description, start_date, end_date, from_, to = combination

        # Counted independently of the export, in the terms the fix has to
        # produce. Asserting only that the task succeeded would accept an
        # exporter that answered every unbounded request with no rows at all,
        # which is the failure a dropped predicate would most plausibly cause.

        expected = context.stack.psql_long(
            "select count(*) from SEL_SearchEvent where companyId = %d%s%s"
            % (
                context.company_id,
                ""
                if start_date is None
                else " and createDate >= timestamp '%s 00:00:00'" % start_date,
                ""
                if end_date is None
                else " and createDate < timestamp '%s 00:00:00'"
                % exclusive_midpoint,
            )
        )

        before = context.portal.start_export(
            start_date=start_date, end_date=end_date
        )

        row = context.portal.wait_for_export_result(
            timeout=context.options.export_timeout, after=before
        )

        case.note(
            "%s: %s, %d of %d events in range"
            % (description, row["status"], expected, total)
        )

        if row["status"].lower() != "successful":
            failures.append("%s: the task ended %s" % (description, row["status"]))

            continue

        destination = os.path.join(
            context.options.results_directory,
            "export-unbounded-%d.zip" % index,
        )

        size, file_name = context.portal.download(
            row["download_url"], destination
        )

        case.note("        downloaded %s, %d bytes" % (file_name, size))

        try:
            if start_date is None and end_date is None:
                assert_true(
                    expected == total > 0,
                    "An unbounded export has to be the whole table, and the "
                    "table is empty or the count disagrees with it",
                )
            else:
                assert_true(
                    0 < expected < total,
                    "The half bounded window is either empty or the whole "
                    "table, so this combination cannot distinguish an export "
                    "that honours its one bound from one that ignores it",
                )

            _assert_unbounded_archive(case, destination, from_, to, expected)
        except AssertionError as error:
            failures.append("%s: %s" % (description, error))

    assert_true(
        not failures,
        "An export was wrong for %s. A bound left empty must leave its "
        "predicate out of the statement, and the archive must say so; "
        "substituting an extreme timestamp is what PostgreSQL rejected with "
        '"timestamp out of range" as D-1' % "; ".join(failures),
    )


def _assert_unbounded_archive(case, path, from_, to, expected_events):
    """Asserts what an export with a blank bound actually produced.

    Three things, because a blank bound can go wrong in three places and only
    the first of them stops the task:

    - `manifest.json` carries `export_range` with **both** keys, null where the
      bound was blank. Liferay's JSONObject removes a key whose value is null,
      so the shape to catch is a range object that quietly lost a key and a
      consumer that cannot tell "unbounded" from "not recorded".
    - `README.md` names the unbounded end rather than printing the word null
      where a date belongs. It is the first line an evaluator reads.
    - The rows are the rows the range holds, counted against the database
      rather than against the export. An exporter whose predicate went missing
      in the other direction returns nothing, and every assertion that only
      looks at the task's status accepts that.
    """
    expected_line = "Range: %s to %s%s" % (
        from_ or "unbounded",
        to or "unbounded",
        " (UTC, end exclusive)" if to else " (UTC)",
    )

    with zipfile.ZipFile(path) as archive:
        manifest = json.loads(archive.read("manifest.json").decode("utf-8"))

        export_range = manifest.get("export_range")

        case.note("        manifest export_range: %r" % (export_range,))

        assert_true(
            isinstance(export_range, dict),
            "manifest.json has no export_range object",
        )

        for key in ("from", "to"):
            assert_true(
                key in export_range,
                "export_range has no %r key; an unbounded end has to be "
                "reported as null, not left out" % key,
            )

        assert_equal(
            export_range["from"], from_, "export_range.from is wrong"
        )
        assert_equal(export_range["to"], to, "export_range.to is wrong")

        readme = archive.read("README.md").decode("utf-8")

        range_lines = [
            line.strip()
            for line in readme.splitlines()
            if line.startswith("Range: ")
        ]

        assert_equal(
            len(range_lines), 1, "README.md does not carry one range line"
        )

        case.note("        README range line: %s" % range_lines[0])

        assert_true(
            "null" not in range_lines[0],
            "README.md shows a null where a date belongs: %s" % range_lines[0],
        )
        assert_equal(
            range_lines[0], expected_line, "README.md misreports the range"
        )

        counted = int(manifest["counts"]["events"])

        lines = 0

        with archive.open("events.jsonl") as entry:
            for raw_line in io.TextIOWrapper(entry, encoding="utf-8"):
                if raw_line.strip():
                    lines += 1

        case.note(
            "        manifest reports %d events, file holds %d lines, the "
            "range holds %d" % (counted, lines, expected_events)
        )

        assert_equal(
            counted, expected_events, "The export did not cover the range"
        )
        assert_equal(
            lines, expected_events, "events.jsonl does not hold the range"
        )


def export_jsonl_number_types(context, case):
    """DESIGN.md 6.2 types total_hits and entry_class_pk as JSON numbers.

    Its worked example is explicit: "total_hits": 147 and
    "entry_class_pk": 38291, neither quoted, and "scope_group_ids": [20121]
    is an array of numbers.

    This was D-2, fixed by TO-88. Liferay's JSONObject.put(String, long) and
    JSONArray.put(long) store String.valueOf of the value, so every long column
    came out quoted while the int and double columns beside it did not. The
    exporter now boxes each long so it reaches the Object overload instead.
    The case stays separate from the archive validation so a regression here
    does not take the rest of it down.
    """
    with zipfile.ZipFile(context.archive_path) as archive:
        with archive.open("events.jsonl") as entry:
            line = io.TextIOWrapper(entry, encoding="utf-8").readline()

    record = json.loads(line)

    observed = {
        "total_hits": type(record.get("total_hits")).__name__,
        "requested_size": type(record.get("requested_size")).__name__,
        "logged_hit_count": type(record.get("logged_hit_count")).__name__,
    }

    if record.get("hits"):
        hit = record["hits"][0]

        observed["entry_class_pk"] = type(hit.get("entry_class_pk")).__name__
        observed["rank"] = type(hit.get("rank")).__name__
        observed["score"] = type(hit.get("score")).__name__

    case.note("observed JSON types: %s" % observed)

    assert_true(
        isinstance(record.get("total_hits"), int),
        "total_hits is %r, and DESIGN.md 6.2 shows it unquoted"
        % record.get("total_hits"),
    )

    scope_group_ids = record.get("scope_group_ids") or []

    assert_true(
        all(isinstance(value, int) for value in scope_group_ids),
        "scope_group_ids is %r, and DESIGN.md 6.2 shows an array of numbers"
        % scope_group_ids,
    )

    if record.get("hits"):
        assert_true(
            isinstance(record["hits"][0].get("entry_class_pk"), int),
            "entry_class_pk is %r, and DESIGN.md 6.2 shows it unquoted"
            % record["hits"][0].get("entry_class_pk"),
        )


def assert_manifest_numbers(manifest):
    """Every long valued manifest field is a JSON number.

    DESIGN.md 6.2 does not spell out manifest types, but the manifest came out
    with D-2's split: company_id and every count quoted while capture_depth and
    sampling_rate beside them were not. TO-88 fixed both files together, so
    both are held to it. bool is excluded explicitly because it is an int
    subclass in Python.
    """
    values = {
        "company_id": manifest["company_id"],
        "counts.events": manifest["counts"]["events"],
        "counts.hits": manifest["counts"]["hits"],
    }

    for key, value in manifest.get("admission_counters", {}).items():
        if key != "scope":
            values["admission_counters." + key] = value

    for key, value in values.items():
        assert_true(
            isinstance(value, int) and not isinstance(value, bool),
            "manifest %s is %r, a %s rather than a JSON number"
            % (key, value, type(value).__name__),
        )


def archive_is_vendor_neutral(context, case):
    """DESIGN.md 10.4: no link to the evaluation service inside the archive.

    Scanned with an overlap between chunks. Reading a megabyte at a time and
    testing each chunk on its own lets a token that straddles a boundary
    through, which is the one case a privacy gate must not have: the failure is
    silent and depends on file size rather than on content.
    """
    forbidden = ARCHIVE_FORBIDDEN

    overlap = max(len(token) for token in forbidden) - 1

    with zipfile.ZipFile(context.archive_path) as archive:
        for name in archive.namelist():
            with archive.open(name) as entry:
                reader = io.TextIOWrapper(entry, encoding="utf-8", errors="replace")

                carry = ""

                while True:
                    chunk = reader.read(1 << 20)

                    if not chunk:
                        break

                    window = (carry + chunk).lower()

                    for token in forbidden:
                        assert_true(
                            token.lower() not in window,
                            "%r appears inside %s" % (token, name),
                        )

                    carry = chunk[-overlap:] if overlap else ""

    case.note(
        "no evaluation service token and no UTM tag in any archive entry, "
        "scanned with a %d character overlap between chunks" % overlap
    )


# The labels DESIGN.md 10.1 and 10.3 give the two links, as they appear in the
# web module's Language.properties. The anchors are found by these rather than
# by the address they point at, so the case keeps testing the same two links
# after a release substitutes real URLs for the placeholders.
COLLECTION_START_LABEL = (
    "Get an email reminder when your search log is ready to export"
)

EXPORT_COMPLETE_LABEL = "Want this dataset evaluated? Book a call"


def funnel_links(context, case):
    """DESIGN.md 10.1 and 10.3, on structure and parameters rather than address.

    Three things are checked about each link, and the third is the one that
    survives substitution. The query string carries only the parameters the
    section names. The anchor opens a new tab with no handle on this window.
    And nothing that identifies the installation appears anywhere in the URL,
    path included, which is what 10.1 and 10.3 actually promise: "no hostname,
    instance ID, company name, plugin version, event counts or user data". A
    check that only enumerated query parameters would pass an address that
    carried the instance id in its path.
    """
    text = context.portal.admin_screen()

    href, attributes = _anchor_for(text, COLLECTION_START_LABEL)

    assert_true(href, "The collection start link of DESIGN.md 10.1 is not rendered")

    case.note("collection start link: %s" % href)

    _assert_new_tab_anchor(attributes, "collection start")

    assert_equal(
        _query_parameters(href),
        {
            "started": context.collection_start_date,
            "utm_source": "liferay-plugin",
            "utm_medium": "admin-screen",
        },
        "10.1 allows the collection start date and the two UTM tags, and "
        "nothing else",
    )

    _assert_carries_nothing_identifying(
        context, href, allowed=(context.collection_start_date,), label="10.1"
    )

    href, attributes = _anchor_for(text, EXPORT_COMPLETE_LABEL)

    assert_true(href, "The export complete link of DESIGN.md 10.3 is not rendered")

    case.note("export complete link: %s" % href)

    _assert_new_tab_anchor(attributes, "export complete")

    assert_equal(
        _query_parameters(href),
        {"utm_source": "liferay-plugin", "utm_medium": "export-complete"},
        "10.3 allows the two UTM tags and nothing else",
    )

    _assert_carries_nothing_identifying(context, href, allowed=(), label="10.3")

    case.note(
        "neither address carries the company id, the portal host, a row count "
        "or a plugin version, in the query string or in the path"
    )


def _anchor_for(text, label):
    """Finds the anchor whose visible text contains `label`."""
    for match in re.finditer(
        r'<a\b([^>]*)href\s*=\s*"([^"]*)"([^>]*)>(.*?)</a>', text, re.I | re.S
    ):
        body = re.sub(r"<[^>]+>", " ", match.group(4))

        if label.lower() in " ".join(body.split()).lower():
            return _unescape_html(match.group(2)), match.group(1) + match.group(3)

    return None, None


def _assert_new_tab_anchor(attributes, which):
    assert_true(
        re.search(r'target\s*=\s*"_blank"', attributes or "", re.I),
        "The %s link does not open in a new tab" % which,
    )
    assert_true(
        "noopener" in (attributes or "").lower(),
        "The %s link does not set rel=noopener, so the new tab keeps a handle "
        "on this window" % which,
    )


def _query_parameters(href):
    query = href.split("?", 1)[1] if "?" in href else ""

    return {
        name: value
        for name, value in urllib.parse.parse_qsl(query, keep_blank_values=True)
    }


def _assert_carries_nothing_identifying(context, href, allowed, label):
    """No instance identifying value anywhere in the URL, path included."""
    haystack = href

    for value in allowed:
        haystack = haystack.replace(str(value), "")

    host = urllib.parse.urlparse(context.portal.base_url).netloc

    candidates = {
        "company id": str(context.company_id),
        "portal host": host.split(":")[0],
        "portal origin": context.portal.base_url,
        "group id": str(context.group_id),
    }

    if context.manifest:
        candidates["event count"] = str(context.manifest["counts"]["events"])
        candidates["hit count"] = str(context.manifest["counts"]["hits"])
        candidates["plugin version"] = str(context.manifest["plugin_version"])

    found = [
        name
        for name, value in candidates.items()
        if value and len(value) > 2 and value in haystack
    ]

    assert_true(
        not found,
        "DESIGN.md %s says the link carries nothing about the installation, "
        "and this one carries the %s" % (label, ", ".join(sorted(found))),
    )


# Elements the browser fetches without anybody clicking, and the attribute
# each one fetches from. An address in any of these is egress whether or not
# the page's author thought of it as a link.
_FETCHING_ELEMENTS = (
    ("img", "src"),
    ("image", "href"),
    ("iframe", "src"),
    ("frame", "src"),
    ("script", "src"),
    ("link", "href"),
    ("embed", "src"),
    ("object", "data"),
    ("source", "src"),
    ("track", "src"),
    ("audio", "src"),
    ("video", "src"),
    ("input", "src"),
)

# Schemes that fetch nothing from anywhere.
_INERT_SCHEMES = ("data:", "about:", "blob:", "javascript:", "mailto:", "tel:", "#")


def zero_egress(context, case):
    """DESIGN.md D9, as far as a rendered page can be inspected.

    Two rules, and the second is the one that matters after a release
    substitutes the placeholder URLs.

    First, every occurrence of a vendor token on the screen sits inside an
    anchor's href. Second, and independently of how anything is spelled, no
    element the browser fetches on its own may point at a host other than the
    portal's. An earlier version keyed the element check on the literal "{{",
    so it proved only that nothing fetched a placeholder; an image pointing at
    a real vendor host would have passed it.

    What this still cannot prove is the absence of a request made from
    somewhere the page does not mention. That stays a reading exercise, and
    EvaluationServiceLinks is the one place to read.
    """
    text = context.portal.admin_screen()

    for token in VENDOR_ADDRESSES:
        occurrences = [
            match.start() for match in re.finditer(re.escape(token), text)
        ]

        if token in FUNNEL_PLACEHOLDERS:
            assert_true(
                occurrences, "%s does not appear on the screen at all" % token
            )

        for position in occurrences:
            assert_true(
                _inside_anchor_href(text, position),
                "%s appears on the screen outside an anchor href, at offset %d"
                % (token, position),
            )

    host = urllib.parse.urlparse(context.portal.base_url).netloc

    foreign = []

    for element, attribute in _FETCHING_ELEMENTS:
        pattern = r"<%s\b[^>]*?\b%s\s*=\s*[\"']([^\"']+)" % (element, attribute)

        for match in re.finditer(pattern, text, re.I | re.S):
            url = _unescape_html(match.group(1).strip())

            if not url or url.lower().startswith(_INERT_SCHEMES):
                continue

            parsed = urllib.parse.urlparse(url)

            if not parsed.netloc or parsed.netloc == host:
                continue

            foreign.append("<%s %s=%s>" % (element, attribute, url))

    assert_true(
        not foreign,
        "The screen fetches from a host other than the portal's without "
        "anybody clicking: %s" % "; ".join(sorted(set(foreign))[:5]),
    )

    case.note(
        "every vendor token sits inside an anchor href, and no fetching "
        "element points outside %s" % host
    )


def _inside_anchor_href(text, position):
    """Whether the character at `position` sits inside an <a href="...">."""
    opening = text.rfind("<", 0, position)

    if opening < 0:
        return False

    closing = text.find(">", opening)

    if closing != -1 and closing < position:

        # The token is in the element's text content, not in a tag.

        return False

    tag = text[opening:position]

    if not re.match(r"<a\b", tag, re.I):
        return False

    return re.search(r'href\s*=\s*["\']?[^"\']*$', tag, re.I) is not None


def visible_text(html):
    """The text a reader sees: no scripts, no styles, no tags, one space each.

    Used wherever an assertion is about what the page says rather than how it
    is marked up, so highlight markup inside a matched phrase cannot hide it.
    """
    without_scripts = re.sub(
        r"<(script|style)\b.*?</\1>", " ", html, flags=re.I | re.S
    )

    return " ".join(
        _unescape_html(re.sub(r"<[^>]+>", " ", without_scripts)).split()
    )


def _unescape_html(value):
    return (
        value.replace("&amp;", "&")
        .replace("&quot;", '"')
        .replace("&#034;", '"')
        .replace("&lt;", "<")
        .replace("&gt;", ">")
    )


def queue_overflow(context, case):
    """TO-92, TO-108: a full queue drops events, counts each once, and never
    slows or fails a search.

    An ACCESS EXCLUSIVE lock on SEL_SearchEvent parks the persistence
    listener on its first insert, so the serial destination fills and every
    event past QUEUE_SIZE goes to its rejection handler. That handler runs on
    the searching thread, inside sendMessage, which then returns normally:
    exactly the path TO-92 found counting an event as dispatched and dropped
    both. The same load with the queue draining normally is timed first, so
    the latency under a full queue has something to be compared with.
    """
    normal = _concurrent_searches(context, OVERFLOW_SEARCHES, OVERFLOW_CLIENTS)

    _wait_for_settled_funnel(context)

    before = context.portal.admission_counters()

    with context.stack.lock_table("SEL_SearchEvent"):
        full = _concurrent_searches(
            context, OVERFLOW_SEARCHES, OVERFLOW_CLIENTS
        )

        blocked = context.portal.admission_counters()

    after = _wait_for_settled_funnel(context)

    delta = {key: after[key] - before[key] for key in after}

    case.note(
        "queue draining: median %.0f ms, p95 %.0f ms"
        % (normal["median"], normal["p95"])
    )
    case.note(
        "queue full:     median %.0f ms, p95 %.0f ms, %d failures"
        % (full["median"], full["p95"], len(full["failures"]))
    )
    case.note(
        "while blocked: persisted +%d, dropped +%d; settled delta %s"
        % (
            blocked["persisted"] - before["persisted"],
            blocked["dropped"] - before["dropped"],
            delta,
        )
    )

    assert_equal(
        full["failures"], [], "Searches failed while the queue was full"
    )

    assert_queue_overflow(delta, OVERFLOW_SEARCHES)
    assert_funnel_adds_up(after)


def assert_queue_overflow(delta, searches):
    """The counters of one overflow, as TO-92 defines them.

    Every search is admitted. At most the queue's capacity plus the one event
    each worker holds is dispatched, the rest are dropped, and since no write
    failed, dispatched equals persisted: a rejected event counted as
    dispatched too makes dispatched exceed persisted.
    """
    assert_true(
        delta["admitted"] >= searches,
        "Only %d of %d overflow searches were admitted"
        % (delta["admitted"], searches),
    )
    assert_true(
        delta["dropped"] >= delta["admitted"] - QUEUE_SIZE - 5,
        "The queue did not overflow: %d admitted, %d dropped"
        % (delta["admitted"], delta["dropped"]),
    )
    assert_equal(
        delta["admitted"],
        delta["persisted"] + delta["dropped"],
        "Admitted events are neither persisted nor dropped, or counted as both",
    )
    assert_equal(
        delta["dispatched"],
        delta["persisted"],
        "Dispatched and persisted differ with no failed write, so rejected "
        "events were counted as dispatched",
    )


def _concurrent_searches(context, searches, clients):
    """Guest searches from several clients at once; latencies and failures."""
    url = "%s/web/guest/search?%s" % (
        context.stack.base_url,
        urllib.parse.urlencode({"q": MARKER_TERM}),
    )

    remaining = iter(range(searches))
    latencies = []
    failures = []
    lock = threading.Lock()

    def client():
        while True:
            with lock:
                if next(remaining, None) is None:
                    return

            started = time.perf_counter()

            try:
                with urllib.request.urlopen(url, timeout=300) as response:
                    response.read()
                    status = response.status
            except Exception as exception:  # noqa: BLE001 - reported below
                status = str(exception)

            with lock:
                latencies.append((time.perf_counter() - started) * 1000)

                if status != 200:
                    failures.append(status)

    threads = [threading.Thread(target=client) for _ in range(clients)]

    for thread in threads:
        thread.start()

    for thread in threads:
        thread.join()

    latencies.sort()

    return {
        "median": statistics.median(latencies),
        "p95": latencies[int(len(latencies) * 0.95) - 1],
        "failures": failures,
    }


def _wait_for_settled_funnel(context):
    def settled():
        counters = context.portal.admission_counters()

        if counters["admitted"] == counters["persisted"] + counters["dropped"]:
            return counters

        return None

    return wait_for("the funnel to settle", settled, timeout=900, interval=5.0)


def uninstall_and_reinstall(context, case):
    """TO-111: removing the plugin leaves search and the data intact, and
    installing it again picks both up.

    Uninstalling takes the wrapper out from under Liferay's search consumers,
    which bound it with static reluctant references (EC-3), so the first thing
    checked is that search still answers with no restart. Reinstalling must
    reuse the existing tables and the collection cycle rather than start over,
    and after the restart DESIGN.md 3.1 requires, collection has to resume.

    Last in the run: it removes and restarts the plugin under every other case.
    """
    events_before = context.stack.psql_long("select count(*) from SEL_SearchEvent")
    cycle_before = _read_cycle(context)

    case.note(
        "before: %d events, collection start %s"
        % (events_before, cycle_before.get("collectionStartDate"))
    )

    uninstalled_at = time.monotonic()

    context.stack.liferay_exec(
        "sh", "-c", "rm -f /opt/liferay/osgi/modules/ai.tensoropt.sel.*.jar"
    )

    def stopped():
        log_text = context.stack.liferay_log(
            since="%ds" % (int(time.monotonic() - uninstalled_at) + 2)
        )

        return all(
            ("STOPPED %s_" % name) in log_text for name in BUNDLE_SYMBOLIC_NAMES
        )

    wait_for("the four bundles to stop", stopped, timeout=300, interval=3.0)

    count, _ = context.portal.search_result_count(MARKER_TERM)

    case.note("search after uninstall: %s results" % count)

    assert_true(
        count is not None and count > 0,
        "Search stopped answering when the plugin was uninstalled",
    )

    assert_log_clean(
        context.stack.liferay_log(
            since="%ds" % (int(time.monotonic() - uninstalled_at) + 2)
        ),
        "uninstalling",
    )

    assert_equal(
        context.stack.psql_long("select count(*) from SEL_SearchEvent"),
        events_before,
        "Uninstalling changed the collected data",
    )

    reinstalled_at = time.monotonic()

    deploy_directory = os.path.join(context.options.directory, ".work", "deploy")

    for jar in context.options.jars:
        with open(jar, "rb") as source, open(
            os.path.join(deploy_directory, os.path.basename(jar)), "wb"
        ) as target:
            target.write(source.read())

    for symbolic_name in BUNDLE_SYMBOLIC_NAMES:
        context.stack.wait_for_bundle_started(
            symbolic_name, reinstalled_at, timeout=context.options.deploy_timeout
        )

    assert_log_clean(
        context.stack.liferay_log(
            since="%ds" % (int(time.monotonic() - reinstalled_at) + 2)
        ),
        "reinstalling",
    )

    context.stack.restart_liferay()

    context.portal.wait_until_serving(timeout=context.options.boot_timeout)

    assert_equal(
        context.portal.run_script(scripts.INTERCEPTION_STATUS),
        "true",
        "Interception is not active after reinstalling and restarting",
    )

    for _ in range(5):
        context.portal.search_result_count(MARKER_TERM)

    def resumed():
        events = context.stack.psql_long("select count(*) from SEL_SearchEvent")

        return events if events >= events_before + 5 else None

    events_after = wait_for(
        "collection to resume", resumed, timeout=300, interval=3.0
    )

    cycle_after = _read_cycle(context)

    case.note(
        "after: %d events, collection start %s"
        % (events_after, cycle_after.get("collectionStartDate"))
    )

    assert_equal(
        cycle_after.get("collectionStartDate"),
        cycle_before.get("collectionStartDate"),
        "Reinstalling reset the collection start date",
    )


def assert_log_clean(log_text, while_doing):
    """No ERROR line in a stretch of the portal log."""
    errors = [line for line in log_text.splitlines() if " ERROR " in line]

    assert_equal(
        errors,
        [],
        "The portal logged errors while %s" % while_doing,
    )


def export_foreign_download(context, case):
    """TO-91, TO-109: the export's download URL serves exports and nothing else.

    The URL carries the background task id as a parameter, so a user holding
    EXPORT can put any task's id in it. Before TO-91 it served that task's
    attachment whatever the task was, such as a site export. The control is
    the export's own URL, which has to keep working.
    """
    control = context.portal.get(context.download_url, timeout=3600)

    case.note(
        "control: HTTP %d, %d bytes" % (control.status, len(control.body))
    )

    assert_true(
        control.status == 200 and control.body[:2] == b"PK",
        "The export's own download URL no longer serves its archive",
    )

    task = json.loads(
        context.portal.run_script(
            scripts.FOREIGN_TASK
            % {
                "company_id": context.company_id,
                "group_id": context.group_id,
                "email": context.portal.user,
                "content": FOREIGN_ATTACHMENT_CONTENT,
            }
        )
    )

    assert_equal(
        task["attachments"], 1, "The foreign task did not get its attachment"
    )

    response = context.portal.get(
        foreign_task_url(context.download_url, task["id"]), timeout=300
    )

    case.note(
        "foreign task %d: HTTP %d, %d bytes"
        % (task["id"], response.status, len(response.body))
    )

    assert_attachment_not_served(response.body)


def foreign_task_url(download_url, task_id):
    """The export's download URL, pointed at another background task."""
    url, count = re.subn(
        r"(backgroundTaskId=)\d+", r"\g<1>%d" % task_id, download_url
    )

    if count != 1:
        raise HarnessError(
            "No single backgroundTaskId parameter in %s" % download_url
        )

    return url


def assert_attachment_not_served(body):
    assert_true(
        FOREIGN_ATTACHMENT_CONTENT.encode("utf-8") not in body,
        "The export's download URL served another background task's "
        "attachment",
    )
    assert_true(
        body[:2] != b"PK",
        "The export's download URL served a ZIP for a task that is not an "
        "export",
    )


def export_permission_refused(context, case):
    """DESIGN.md 6.1: the dedicated EXPORT permission actually refuses.

    README records this as never having been exercised against a user who does
    not hold it. The user is given screen access and nothing else, because a
    user who cannot open the screen is stopped by portlet access rather than by
    the permission under test.
    """
    context.portal.run_script(
        scripts.GRANT_SCREEN_ACCESS_TO_USER_ROLE
        % {"company_id": context.company_id, "portlet_id": ADMIN_PORTLET_ID}
    )

    email = "sel-e2e-viewer@liferay.com"
    password = "Testtest1!"

    status, body = context.portal.headless(
        "POST",
        "/o/headless-admin-user/v1.0/user-accounts",
        {
            "alternateName": "seleteviewer",
            "emailAddress": email,
            "familyName": "Viewer",
            "givenName": "Sel",
            "password": password,
        },
    )

    assert_true(
        status in (200, 201),
        "Creating the unprivileged user failed: %s %s" % (status, str(body)[:300]),
    )

    user_id = context.portal.run_script(
        scripts.ACTIVATE_USER
        % {"company_id": context.company_id, "email": email}
    )

    case.note("created user %s without the EXPORT permission" % user_id)

    from .portal import Portal

    viewer = Portal(context.stack, user=email, password=password)

    viewer.login()

    text = viewer.admin_screen()

    # The screen has to be the plugin's own, not an access denied page or the
    # sign in form, or the three assertions below would hold for the wrong
    # reason. The counters table is rendered for every viewer, so its presence
    # says the portlet ran.

    assert_true(
        "Admission Counters" in text,
        "The unprivileged user did not reach the plugin's screen at all, so "
        "nothing below would be evidence about the EXPORT permission",
    )

    assert_true(
        "You do not have permission to export" in text,
        "A user without EXPORT was not told so on the screen",
    )

    assert_true(
        "Run an Export" not in text,
        "A user without EXPORT was still offered the export form",
    )

    before = context.portal.background_task_count()

    namespace = "_%s_" % ADMIN_PORTLET_ID

    action = (
        "/group/control_panel/manage?p_p_id=%s&p_p_lifecycle=1"
        "&p_p_state=maximized&%sjavax.portlet.action="
        "%%2Fsearch_eval_logger%%2Fexport&p_auth=%s"
        % (ADMIN_PORTLET_ID, namespace, viewer.auth_token())
    )

    response = viewer.post(
        action, {namespace + "startDate": "", namespace + "endDate": ""}
    )

    # HTTP 200 is not by itself a refusal, and the case does not treat it as
    # one. Liferay renders a portlet action's PrincipalException as a page
    # rather than as a status code, so the status says nothing either way.

    case.note("direct export POST answered HTTP %d" % response.status)

    def appeared(baseline):
        return lambda: context.portal.background_task_count() != baseline

    started = _within(appeared(before), PERMISSION_SETTLE_SECONDS)

    assert_true(
        not started,
        "A user without EXPORT started an export by posting the action "
        "directly. The screen hides the form, but hiding a control is "
        "presentation, not access control",
    )

    case.note(
        "no background task appeared in %ds; the count is still %d"
        % (PERMISSION_SETTLE_SECONDS, before)
    )

    # The positive control, and the reason the assertion above is evidence.
    #
    # "No task appeared" is also what a mistyped action URL, a wrong portlet
    # namespace or a stale CSRF token would produce, and each of those would
    # make this case pass while testing nothing. Posting the identical request
    # as an administrator has to start a task. If it does not, the request
    # itself was broken and the refusal above meant nothing.

    administrator = context.portal

    control_before = administrator.background_task_count()

    control_action = (
        "/group/control_panel/manage?p_p_id=%s&p_p_lifecycle=1"
        "&p_p_state=maximized&%sjavax.portlet.action="
        "%%2Fsearch_eval_logger%%2Fexport&p_auth=%s"
        % (ADMIN_PORTLET_ID, namespace, administrator.auth_token())
    )

    start_date, end_date = context.export_range

    administrator.post(
        control_action,
        {namespace + "startDate": start_date, namespace + "endDate": end_date},
    )

    control_started = _within(
        appeared(control_before), PERMISSION_SETTLE_SECONDS
    )

    assert_true(
        control_started,
        "The same request posted by an administrator did not start an export "
        "either, so this case proves nothing about the EXPORT permission: the "
        "request is broken, not the caller's authority",
    )

    case.note(
        "positive control: the identical request from an administrator did "
        "start a background task"
    )

    context.portal.wait_for_export_result(
        timeout=context.options.export_timeout, after=control_before
    )


# Helpers


def _within(predicate, seconds):
    """True if predicate() became true inside the deadline, False on timeout."""
    try:
        wait_for("a condition", predicate, timeout=seconds, interval=3.0)

        return True
    except HarnessError:
        return False


def _searcher_registry(context, case):
    """Parses the Searcher registry dump into one record per service."""
    dump = str(context.portal.run_script(scripts.SEARCHER_REGISTRY))

    case.note("Searcher registry:")

    entries = []

    for line in dump.splitlines():
        line = line.strip()

        if not line:
            continue

        case.note("  %s" % line)

        match = re.match(
            r"^(?P<name>\S+) ranking=(?P<ranking>\S+) marker=(?P<marker>\S+) "
            r"users=(?P<users>.*)$",
            line,
        )

        assert_true(match, "Unreadable registry line: %r" % line)

        entries.append(
            {
                "name": match.group("name"),
                "ranking": match.group("ranking"),
                "marker": match.group("marker") == "true",
                "users": [
                    user for user in match.group("users").split(",") if user
                ],
            }
        )

    assert_true(entries, "No Searcher service is registered at all")

    return entries


def _users_of_portal_searcher(registry):
    users = set()

    for entry in registry:
        if not entry["marker"]:
            users.update(entry["users"])

    return users


def _read_cycle(context):
    return context.portal.run_script(
        scripts.CYCLE_READ
        % {"company_id": context.company_id, "portlet_id": CYCLE_PORTLET_ID}
    )


def assert_notification_listed(list_text, expected):
    """The notification is in the user's own notifications list.

    Stored is not the same as shown. Liferay's list only shows delivered
    website events, and until TO-110 both notifications were stored as
    undelivered: present in UserNotificationEvent, and in front of nobody.
    """
    assert_true(
        expected in list_text,
        "The notification is stored but not in the administrator's "
        "notifications list, which reads: %r" % list_text[:300],
    )


def _notification_payloads(context):
    rows = context.stack.psql(
        "select payload from usernotificationevent where type_ = '%s'"
        % ADMIN_PORTLET_ID
    )

    return [row[0] for row in rows]


def _count_older_than(context, table, cutoff, band_seconds):
    return context.stack.psql_long(
        "select count(*) from %s where companyId = %d and createDate < "
        "(timestamp '%s' - interval '%d seconds')"
        % (table, context.company_id, cutoff, band_seconds),
        timeout=3600,
    )


def _count_newer_than(context, table, cutoff, band_seconds):
    return context.stack.psql_long(
        "select count(*) from %s where companyId = %d and createDate > "
        "(timestamp '%s' + interval '%d seconds')"
        % (table, context.company_id, cutoff, band_seconds),
        timeout=3600,
    )


def _stream_events(archive, case=None):
    """Reads events.jsonl a line at a time, checking every line.

    Never materialises the file: a full window export at the heavy tier is a
    million lines, and a validator that loads it would fail for the same reason
    DESIGN.md 6.1 forbids the exporter to.

    Every line is checked, and so is every hit on it. An earlier version
    collected the key set from line one only and never looked at a hit at all,
    which meant a file could drop three keys from line six, or replace every
    hit object on every line with {"zzz": 1}, and still pass. DESIGN.md 6.2
    names eight keys on the hit object; all eight are required here.

    Returns a summary rather than raising on the first problem, so a report
    says how widespread a deviation is rather than only where it starts.
    """
    events = 0
    hits = 0
    previous = None

    missing_event_keys = {}
    missing_hit_keys = {}
    extra_event_keys = set()
    extra_hit_keys = set()
    null_counts = dict.fromkeys(REQUIRED_NON_NULL_EVENT_KEYS, 0)

    with archive.open("events.jsonl") as entry:
        for raw_line in io.TextIOWrapper(entry, encoding="utf-8"):
            line = raw_line.strip()

            if not line:
                continue

            record = json.loads(line)

            events += 1

            keys = set(record.keys())

            for key in EVENT_KEYS - keys:
                missing_event_keys.setdefault(key, events)

            extra_event_keys |= keys - EVENT_KEYS

            for key in REQUIRED_NON_NULL_EVENT_KEYS:
                value = record.get(key)

                if value is None or value == "":
                    null_counts[key] += 1

            record_hits = record.get("hits")

            assert_true(
                isinstance(record_hits, list),
                "events.jsonl line %d has no hits array" % events,
            )

            for hit in record_hits:
                hits += 1

                assert_true(
                    isinstance(hit, dict),
                    "events.jsonl line %d holds a hit that is not an object"
                    % events,
                )

                hit_keys = set(hit.keys())

                for key in HIT_KEYS - hit_keys:
                    missing_hit_keys.setdefault(key, events)

                extra_hit_keys |= hit_keys - HIT_KEYS

            created_at = record.get("created_at")

            if previous is not None and created_at is not None:
                if created_at < previous:
                    raise AssertionError(
                        "events.jsonl is not in chronological order at line %d: "
                        "%s follows %s" % (events, created_at, previous)
                    )

            if created_at is not None:
                previous = created_at

    assert_true(events > 0, "events.jsonl is empty")

    assert_true(
        not missing_event_keys,
        "events.jsonl lines are missing keys DESIGN.md 6.2 names: %s"
        % ", ".join(
            "%s (first at line %d)" % (key, line)
            for key, line in sorted(missing_event_keys.items())
        ),
    )

    assert_true(
        not missing_hit_keys,
        "hit objects are missing keys DESIGN.md 6.2 names: %s"
        % ", ".join(
            "%s (first at line %d)" % (key, line)
            for key, line in sorted(missing_hit_keys.items())
        ),
    )

    assert_true(
        not extra_event_keys,
        "events.jsonl carries keys DESIGN.md 6.2 does not name: %s"
        % ", ".join(sorted(extra_event_keys)),
    )

    assert_true(
        not extra_hit_keys,
        "hit objects carry keys DESIGN.md 6.2 does not name: %s"
        % ", ".join(sorted(extra_hit_keys)),
    )

    # Present but null on every row would satisfy the key checks above and
    # carry no data. These three are the ones the dataset exists for: the
    # query, where it came from, and the identifier that joins a row to its
    # hits.

    empty = {key: count for key, count in null_counts.items() if count == events}

    assert_true(
        not empty,
        "every line in events.jsonl has a null or empty %s"
        % ", ".join(sorted(empty)),
    )

    if case is not None:
        for key, count in sorted(null_counts.items()):
            if count:
                case.note(
                    "%d of %d lines carry no %s" % (count, events, key)
                )

    return events, hits, previous
