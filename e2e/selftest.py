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

from harness import checks, pluginconfig, zeroresult  # noqa: E402
from harness.portal import (  # noqa: E402
    parse_admission_counters,
    parse_background_tasks,
    parse_result_count,
    parse_zero_result_panel,
)
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
        "blueprint_external_reference_code": None,
        "audience_type": "AUTHENTICATED",
        "cohort_hash": "3585671aa51e6cd46efff007511fe851",
        "requested_size": 20,
        "requested_from": 0,
        "total_hits": 249,
        "logged_hit_count": 2,
        "source_type": "WIDGET",
        "hits": [hit_object(index, rank) for rank in range(1, 3)],
    }

    record.update(overrides)

    return record


def hit_object(index, rank):
    """One hit, 1-based rank per TO-133: the caller passes the
    exported value directly, counting from requested_from + 1."""
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
<a href="https://tensoropt.ai/dxp-sel-signup?started=2026-09-15&amp;utm_source=liferay-plugin&amp;utm_medium=admin-screen" rel="noopener noreferrer" target="_blank">
What to look for in your search log (free guide, optional reminder)
</a>
</p>
<h3>Run an Export</h3>
<p>
<a href="https://tensoropt.ai/dxp-sel-booking?utm_source=liferay-plugin&amp;utm_medium=export-complete" rel="noopener noreferrer" target="_blank">
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


def check_hit_ranks():
    """TO-133: ranks are 1-based and absolute across pages, with no gaps and
    in order.

    A 0-based numbering must be actively caught rather than merely not
    required: a reader given 0-based ranks would silently mis-score every
    reciprocal-rank and nDCG computation.
    """
    good = [event_line(index) for index in range(3)]

    expect_accepted(
        "ranks run 1, 2 with requested_from 0", stream(good)
    )

    page_two = [
        event_line(
            0,
            requested_from=20,
            hits=[hit_object(0, rank) for rank in range(21, 23)],
        )
    ]

    expect_accepted(
        "a page 2 event with requested_from 20 starts its ranks at 21",
        stream(page_two),
    )

    zero_hits = [event_line(index, requested_from=0, hits=[]) for index in range(2)]

    expect_accepted(
        "an event with zero hits carries no rank sequence to break",
        stream(zero_hits),
    )

    zero_based = [event_line(index) for index in range(3)]

    for record in zero_based:
        for hit, rank in zip(record["hits"], range(2)):
            hit["rank"] = rank

    expect_rejected(
        "ranks numbered from 0",
        stream(zero_based),
    )

    gapped = [event_line(index) for index in range(3)]

    gapped[1]["hits"][1]["rank"] = 3

    expect_rejected("a gap in the rank sequence", stream(gapped))

    out_of_order = [event_line(index) for index in range(3)]

    out_of_order[2]["hits"].reverse()

    expect_rejected(
        "hits present but not ordered by rank", stream(out_of_order)
    )


def check_snippet_shape():
    """TO-115: `snippet` is a JSON object keyed by locale then field.

    Each mutation below is one way the pre-TO-115 column, or a half-migrated
    one, would still look plausible to a check that only confirmed the key
    was present.
    """
    good = [event_line(index) for index in range(3)]

    good[0]["hits"][0]["snippet"] = {
        "en_US": {"content": ["a <liferay-hl>fragment</liferay-hl>"]},
        "_default": {"assetTagNames": ["generated"]},
    }

    expect_accepted("a well-shaped snippet object is accepted", stream(good))

    joined_string = [event_line(index) for index in range(3)]

    joined_string[0]["hits"][0]["snippet"] = (
        "a <liferay-hl>fragment</liferay-hl> joined into one string"
    )

    expect_rejected(
        "a snippet that is still a joined string, not an object",
        stream(joined_string),
    )

    fragment_not_wrapped = [event_line(index) for index in range(3)]

    fragment_not_wrapped[0]["hits"][0]["snippet"] = {
        "en_US": {"content": "a fragment, not wrapped in an array"}
    }

    expect_rejected(
        "a snippet field whose fragments are not wrapped in an array",
        stream(fragment_not_wrapped),
    )

    bad_locale_key = [event_line(index) for index in range(3)]

    bad_locale_key[0]["hits"][0]["snippet"] = {
        "not a locale at all": {"content": ["fragment"]}
    }

    expect_rejected(
        "a snippet locale key that is neither a locale nor _default",
        stream(bad_locale_key),
    )

    empty_fragments = [event_line(index) for index in range(3)]

    empty_fragments[0]["hits"][0]["snippet"] = {"_default": {"content": []}}

    expect_rejected(
        "a snippet field whose fragment array is empty",
        stream(empty_fragments),
    )

    null_snippet = [event_line(index) for index in range(3)]

    null_snippet[0]["hits"][0]["snippet"] = None

    expect_accepted(
        "a null snippet (no highlights captured) is still accepted",
        stream(null_snippet),
    )


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

    # The reviewer's finding: with the signup anchor gone, a plain substring
    # search for the signup URL would still be "found" inside any longer URL
    # it prefixes (say, a page nested under the signup address), and the
    # "must appear" assertion would pass vacuously. The signup anchor is
    # swapped for one to such a longer URL, so this fixture only proves
    # anything if matching stops at a URL boundary.
    signup_link_removed = ADMIN_SCREEN.replace(
        '<a href="https://tensoropt.ai/dxp-sel-signup?started=2026-09-15'
        '&amp;utm_source=liferay-plugin&amp;utm_medium=admin-screen" ',
        '<a href="https://tensoropt.ai/dxp-sel-signup/archive" ',
    )

    _assert(
        signup_link_removed != ADMIN_SCREEN,
        "the fixture's replace() did not match anything in ADMIN_SCREEN",
    )

    expect_rejected(
        "the signup link replaced by a longer URL sharing its prefix",
        lambda: checks.zero_egress(
            FakeContext(screen=signup_link_removed), FakeCase()
        ),
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
        "<h3>Admission Counters</h3><p>Visit https://tensoropt.ai/dxp-sel-signup "
        "to sign up.</p>",
    )

    expect_rejected(
        "the released signup URL in page text rather than in an anchor href",
        lambda: checks.zero_egress(FakeContext(screen=loose), FakeCase()),
    )

    real_host_loose = ADMIN_SCREEN.replace(
        "<h3>Admission Counters</h3>",
        "<h3>Admission Counters</h3><p>Ask us at tensoropt.ai for help.</p>",
    )

    expect_rejected(
        "the bare vendor host in page text outside any anchor",
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
        "https://tensoropt.ai/dxp-sel-signup?started=",
        "https://tensoropt.ai/dxp-sel-signup/92102577642293/signup?started=",
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

    # noopener kept, noreferrer alone dropped: the referrer leak this check
    # exists to catch. Without a dedicated noreferrer assertion this fixture
    # would pass, since rel="noopener" alone still satisfies the noopener
    # check above.
    no_noreferrer = ADMIN_SCREEN.replace(
        'rel="noopener noreferrer"', 'rel="noopener"', 1
    )

    expect_rejected(
        "the collection start link losing rel=noreferrer while keeping noopener",
        lambda: checks.funnel_links(FakeContext(screen=no_noreferrer), FakeCase()),
    )

    wrong_date = ADMIN_SCREEN.replace("started=2026-09-15", "started=2020-01-01")

    expect_rejected(
        "a collection start date the cycle never recorded",
        lambda: checks.funnel_links(FakeContext(screen=wrong_date), FakeCase()),
    )

    no_link = ADMIN_SCREEN.replace(
        "What to look for in your search log", "Nothing"
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


def _zero_result_event(
    query, requested_from=0, total_hits=0, audience_type="GUEST"
):
    return {
        "query": query,
        "requested_from": requested_from,
        "total_hits": total_hits,
        "audience_type": audience_type,
    }


def check_zero_result_recipes():
    """TO-135: harness.zeroresult's Python port of the acceptance jq recipes
    Z1 and Z2 discriminates a wrong count, a wrong order and a non-ASCII
    fold, the same way check_funnel_counters above proves
    assert_funnel_adds_up does for the admission funnel.
    """

    # ascii_downcase folds only ASCII. A full Unicode fold would merge this
    # accented query with a plain-ASCII one that is not actually the same
    # string under the recipe.

    expect_accepted(
        "normalize_query leaves a non-ASCII letter's case alone",
        lambda: _assert(
            zeroresult.normalize_query("CAFÉ") == "cafÉ"
        ),
    )
    expect_rejected(
        "a full Unicode fold of the same string is caught as wrong",
        lambda: _assert(
            zeroresult.normalize_query("CAFÉ") == "café"
        ),
    )

    # Oniguruma's \s, confirmed against jq, strips a non-breaking space
    # (U+00A0) and an ideographic space (U+3000) at the edges.

    expect_accepted(
        "normalize_query strips a non-breaking space like jq's \\s does",
        lambda: _assert(
            zeroresult.normalize_query(" hello ") == "hello"
        ),
    )
    expect_accepted(
        "normalize_query strips an ideographic space like jq's \\s does",
        lambda: _assert(
            zeroresult.normalize_query("　hello　") == "hello"
        ),
    )

    # Also confirmed against jq: U+001C-U+001F (the legacy FS/GS/RS/US
    # separator controls) are NOT in Unicode's White_Space property, and
    # jq's \s does not strip them. Python's own str.strip() and re's \s
    # both strip them anyway (both follow str.isspace(), a wider,
    # non-standard set), which is exactly the bug an earlier version of
    # normalize_query had: it relied on str.strip() and over-trimmed
    # relative to jq. This has to stay rejected, not accepted, or that
    # regression goes unnoticed again.

    expect_rejected(
        "stripping U+001C-U+001F (the pre-fix str.strip() over-trim) is "
        "caught as disagreeing with jq",
        lambda: _assert(
            zeroresult.normalize_query("hello") == "hello"
        ),
    )
    expect_accepted(
        "normalize_query correctly leaves U+001C-U+001F in place, matching jq",
        lambda: _assert(
            zeroresult.normalize_query("hello") == "hello"
        ),
    )

    events = [
        _zero_result_event("  Nothingmatches "),
        _zero_result_event("nothingmatches"),
        _zero_result_event("NOTHINGMATCHES", audience_type="AUTHENTICATED"),
        _zero_result_event("other", total_hits=5),
        _zero_result_event("page two", requested_from=10),
    ]

    z1 = zeroresult.compute_z1(events)

    expect_accepted(
        "Z1 counts only first-page searches, and only the zero-result ones "
        "as zero",
        lambda: _assert(z1["searches"] == 4 and z1["zero_result"] == 3),
    )
    expect_rejected(
        "Z1 with a wrong total search count is caught",
        lambda: _assert(z1["searches"] == 999),
    )
    expect_rejected(
        "Z1 with a wrong zero-result count is caught",
        lambda: _assert(z1["zero_result"] == 0),
    )

    by_audience = {row["audience_type"]: row for row in z1["by_audience"]}

    expect_accepted(
        "Z1's by_audience splits the same rows guests and authenticated "
        "users actually produced",
        lambda: _assert(
            by_audience["GUEST"]["searches"] == 3
            and by_audience["AUTHENTICATED"]["searches"] == 1
        ),
    )
    expect_rejected(
        "a guest count attributed to the wrong audience is caught",
        lambda: _assert(by_audience["GUEST"]["searches"] == 4),
    )

    z2 = zeroresult.compute_z2(events, limit=20)

    expect_accepted(
        "Z2 merges case and whitespace variants into one normalized group",
        lambda: _assert(
            z2[0] == {"query": "nothingmatches", "searches": 3}
        ),
    )
    expect_rejected(
        "Z2 with the wrong count for the merged group is caught",
        lambda: _assert(z2[0]["searches"] == 2),
    )

    tied = [
        _zero_result_event("zebra"), _zero_result_event("apple"),
        _zero_result_event("mango"),
    ]

    tied_z2 = zeroresult.compute_z2(tied, limit=20)

    expect_accepted(
        "equal counts break ties by ascending code point order, as a "
        "stable sort over group_by's own key order would",
        lambda: _assert(
            [row["query"] for row in tied_z2] == ["apple", "mango", "zebra"]
        ),
    )
    expect_rejected(
        "the reverse (descending key) tie order is caught as wrong",
        lambda: _assert(
            [row["query"] for row in tied_z2] == ["zebra", "mango", "apple"]
        ),
    )

    many = [
        _zero_result_event("query%02d" % index)
        for index in range(25)
    ]

    # Every normalized query here is distinct, so every one has a count of
    # 1 and ties break by ascending code point order: "query00" sorts before
    # "query01", ..., before "query24", and the first 20 of that order are
    # query00 through query19.

    cut = zeroresult.compute_z2(many, limit=20)

    expect_accepted(
        "the top-20 cut keeps exactly 20 rows, in order, from 25 distinct "
        "queries",
        lambda: _assert(
            len(cut) == 20 and cut[0]["query"] == "query00"
            and cut[19]["query"] == "query19"
        ),
    )
    expect_rejected(
        "a cut that let a 21st row through is caught",
        lambda: _assert(len(cut) == 21),
    )

    expect_accepted(
        "round_half_up matches ZeroResultRate.formatPercent's rounding",
        lambda: _assert(zeroresult.round_half_up(25, 2000) == "1.3"),
    )
    expect_rejected(
        "round-half-even's answer for the same boundary is caught as wrong",
        lambda: _assert(zeroresult.round_half_up(25, 2000) == "1.2"),
    )
    expect_accepted(
        "round_half_up is 0.0 for a zero denominator, not a division by "
        "zero",
        lambda: _assert(zeroresult.round_half_up(0, 0) == "0.0"),
    )


ZERO_RESULT_PANEL_SCREEN = """
<html><body>
<h2 class="sheet-title">Searches with no results</h2>
<p>3 of 10 searches (30.0%) in the log returned no results.</p>
<p>Guests: 2 of 6 searches (33.3%).<br>
Signed-in users: 1 of 4 searches (25.0%).</p>
<table><thead><tr><th>Query</th><th>Searches</th></tr></thead>
<tbody>
<tr><td>nothingmatches</td><td>3</td></tr>
<tr><td>other term</td><td>1</td></tr>
</tbody></table>
<p class="text-muted">Queries are grouped after trimming spaces and
ignoring case.</p>
</body></html>
<h2 class="sheet-title">Admission Counters</h2>
"""

ZERO_RESULT_PANEL_EMPTY_SCREEN = """
<html><body>
<h2 class="sheet-title">Searches with no results</h2>
<p>No searches in the log yet.</p>
<h2 class="sheet-title">Admission Counters</h2>
</body></html>
"""

# No zero-result searches yet: the rate lines render (all at 0 (0.0%)), but
# the table does not, and "Admission Counters" below opens straight into its
# own <tbody> with no <thead> first - the exact shape that would fool a
# parser that searched forward for the nearest <tbody> instead of stopping at
# this panel's own boundary.
ZERO_RESULT_PANEL_NO_ZERO_RESULTS_SCREEN = """
<html><body>
<h2 class="sheet-title">Searches with no results</h2>
<p>0 of 12 searches (0.0%) in the log returned no results.</p>
<p>Guests: 0 of 8 searches (0.0%).<br>
Signed-in users: 0 of 4 searches (0.0%).</p>
<h2 class="sheet-title">Admission Counters</h2>
<table class="table table-autofit table-list">
<tbody>
<tr><td>Searches observed while enabled</td><td>12</td></tr>
</tbody></table>
</body></html>
"""


def check_zero_result_panel_parsing():
    """TO-135: harness.portal.parse_zero_result_panel reads the panel's own
    rendered text, not a coincidentally-matching number.
    """
    panel = parse_zero_result_panel(ZERO_RESULT_PANEL_SCREEN)

    expect_accepted(
        "a populated panel parses overall, both audiences and the table",
        lambda: _assert(
            panel["overall"] == {"zero_result": 3, "searches": 10, "rate": "30.0"}
            and panel["guests"]
            == {"zero_result": 2, "searches": 6, "rate": "33.3"}
            and panel["signed_in"]
            == {"zero_result": 1, "searches": 4, "rate": "25.0"}
            and panel["top_queries"]
            == [
                {"query": "nothingmatches", "searches": 3},
                {"query": "other term", "searches": 1},
            ]
        ),
    )

    wrong_overall = dict(panel["overall"], searches=999)

    expect_rejected(
        "a wrong overall search count is caught",
        lambda: _assert(panel["overall"] == wrong_overall),
    )

    reordered = list(reversed(panel["top_queries"]))

    expect_rejected(
        "the table rows in the wrong order are caught",
        lambda: _assert(panel["top_queries"] == reordered),
    )

    empty_panel = parse_zero_result_panel(ZERO_RESULT_PANEL_EMPTY_SCREEN)

    expect_accepted(
        "an empty log parses as no_searches_logged, not a crash or a zero "
        "rate",
        lambda: _assert(empty_panel == {"no_searches_logged": True}),
    )

    # Regression for the bug this module's first version had: with no table
    # of its own, a non-greedy search from the heading for the nearest
    # <tbody> would latch onto Admission Counters' table instead, which also
    # opens straight into <tbody> with no <thead>. Bounding the parse to this
    # panel's own slice of the page (up to the next sheet's heading) is what
    # keeps that from happening.

    no_zero_results_panel = parse_zero_result_panel(
        ZERO_RESULT_PANEL_NO_ZERO_RESULTS_SCREEN
    )

    expect_accepted(
        "no zero-result searches yet still renders the rate lines, all at "
        "zero, and no table - not Admission Counters' table read as this "
        "panel's own",
        lambda: _assert(
            no_zero_results_panel["overall"]
            == {"zero_result": 0, "searches": 12, "rate": "0.0"}
            and no_zero_results_panel["top_queries"] == []
        ),
    )
    expect_rejected(
        "Admission Counters' row mistaken for a zero-result query is "
        "caught",
        lambda: _assert(
            no_zero_results_panel["top_queries"]
            == [
                {
                    "query": "Searches observed while enabled",
                    "searches": 12,
                }
            ]
        ),
    )

    # A screen with none of this panel's lines at all (every other sheet
    # removed) must fail to parse rather than silently report zeroes: a
    # wrong page read as an empty log would hide a real regression behind a
    # result that looks like the one legitimate empty-log case.

    expect_rejected(
        "a page with none of the panel's own lines raises rather than "
        "reporting zeroes",
        lambda: parse_zero_result_panel("<html><body>Sign in.</body></html>"),
    )


def check_zero_result_panel_matches_events():
    """TO-135: checks.assert_zero_result_panel_matches, the function the real
    `zero_result_panel` e2e case calls, discriminates the panel disagreeing
    with the recipes computed over the same events - the point of the whole
    check.
    """
    events = [
        _zero_result_event("widget", audience_type="GUEST"),
        _zero_result_event("widget", audience_type="AUTHENTICATED"),
        _zero_result_event("other", total_hits=4, audience_type="GUEST"),
    ]

    matching_panel = {
        "no_searches_logged": False,
        "overall": {"zero_result": 2, "searches": 3, "rate": "66.7"},
        "guests": {"zero_result": 1, "searches": 2, "rate": "50.0"},
        "signed_in": {"zero_result": 1, "searches": 1, "rate": "100.0"},
        "top_queries": [{"query": "widget", "searches": 2}],
    }

    expect_accepted(
        "a panel whose figures match the recipes is accepted",
        lambda: checks.assert_zero_result_panel_matches(matching_panel, events),
    )

    wrong_overall = dict(
        matching_panel,
        overall=dict(matching_panel["overall"], zero_result=0),
    )

    expect_rejected(
        "a panel whose overall zero-result count disagrees with Z1 is "
        "caught",
        lambda: checks.assert_zero_result_panel_matches(wrong_overall, events),
    )

    wrong_audience = dict(
        matching_panel,
        guests=dict(matching_panel["guests"], searches=99),
    )

    expect_rejected(
        "a panel whose guest count disagrees with Z1's by_audience is "
        "caught",
        lambda: checks.assert_zero_result_panel_matches(
            wrong_audience, events
        ),
    )

    wrong_rate = dict(
        matching_panel,
        overall=dict(matching_panel["overall"], rate="0.0"),
    )

    expect_rejected(
        "a panel whose rate disagrees with round_half_up is caught",
        lambda: checks.assert_zero_result_panel_matches(wrong_rate, events),
    )

    wrong_table = dict(matching_panel, top_queries=[])

    expect_rejected(
        "a panel whose top-queries table disagrees with Z2 is caught",
        lambda: checks.assert_zero_result_panel_matches(wrong_table, events),
    )


def check_zero_result_panel_xss_escaping():
    """TO-135, stored XSS (blocker review finding): a zero-result query is
    guest-controlled input rendered into an administrator's own page, and
    checks.assert_query_text_is_html_escaped is what the real
    `zero_result_panel` e2e case calls to prove it reaches that page as
    escaped text rather than as markup. This proves the assertion itself
    discriminates: a raw, unescaped tag must be rejected, not silently
    accepted because some escaped text happens to appear elsewhere on the
    page too.
    """
    raw_markup = "qqzzxx<b>xss</b>"

    escaped_page = (
        "<html><body><table><tr><td>qqzzxx&lt;b&gt;xss&lt;/b&gt;</td>"
        "<td>1</td></tr></table></body></html>"
    )

    expect_accepted(
        "a page that rendered the query through HTML escaping is accepted",
        lambda: checks.assert_query_text_is_html_escaped(
            escaped_page, raw_markup),
    )

    unescaped_page = (
        "<html><body><table><tr><td>qqzzxx<b>xss</b></td>"
        "<td>1</td></tr></table></body></html>"
    )

    expect_rejected(
        "a page that rendered the raw, unescaped tag is caught (the "
        "stored XSS this finding is about)",
        lambda: checks.assert_query_text_is_html_escaped(
            unescaped_page, raw_markup),
    )

    neither_page = "<html><body>Sign in.</body></html>"

    expect_rejected(
        "a page carrying neither the escaped nor the raw form is caught, "
        "not silently accepted as "
        "\"at least it is not the raw tag\"",
        lambda: checks.assert_query_text_is_html_escaped(
            neither_page, raw_markup),
    )


NOTIFICATIONS_EMPTY = """
<div id="p_p_id_com_liferay_notifications_web_portlet_NotificationsPortlet_">
<div class="portlet-body"> Notifications List (0) Requests List (0) Filter
You do not have any notifications. </div></div>
"""

NOTIFICATIONS_LISTED = """
<div id="p_p_id_com_liferay_notifications_web_portlet_NotificationsPortlet_">
<div class="portlet-body"> Notifications List (1) <script>var x = 1;</script>
<p>Logging is switched on but nothing is being recorded: the automatic
rebind of the search widgets and search bar suggestions onto this plugin
did not succeed, so they are still bound to the portal&#39;s own search
service. <strong>Restart the portal to begin collecting.</strong></p>
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


def check_queue_overflow():
    """TO-108: an overflow drops what the queue cannot hold, counted once."""
    good = {"admitted": 2600, "dispatched": 2002, "dropped": 598,
            "persisted": 2002}

    expect_accepted(
        "598 of 2,600 dropped past a 2,000 event queue, each counted once",
        lambda: checks.assert_queue_overflow(good, 2600),
    )

    for description, overrides in (
        (
            "rejected events counted as dispatched too (the pre-TO-92 dispatcher)",
            {"dispatched": 2600},
        ),
        (
            "a queue that never filled, so nothing was exercised",
            {"dispatched": 2600, "dropped": 0, "persisted": 2600},
        ),
        (
            "admitted events lost between the counters",
            {"dropped": 500},
        ),
        ("overflow searches not admitted", {"admitted": 2000}),
    ):
        delta = dict(good, **overrides)

        expect_rejected(
            description,
            lambda delta=delta: checks.assert_queue_overflow(delta, 2600),
        )


def check_log_clean():
    """TO-111: an ERROR in the portal log fails the uninstall case."""
    clean = (
        "2026-09-24 10:49:51.729 INFO  [fileinstall-directory-watcher]"
        "[BundleStartStopLogger:68] STOPPED ai.tensoropt.sel.web_1.0.0 [2927]\n"
        "2026-09-24 10:49:52.001 WARN  [main][Foo:1] Something was slow\n"
    )

    expect_accepted(
        "bundles stopping with nothing worse than a warning",
        lambda: checks.assert_log_clean(clean, "uninstalling"),
    )

    expect_rejected(
        "an ERROR line among them",
        lambda: checks.assert_log_clean(
            clean + "2026-09-24 10:49:53.100 ERROR [http-nio-8080-exec-2]"
            "[SearchDisplayContext:1] java.lang.NullPointerException\n",
            "uninstalling",
        ),
    )


    objects_error = (
        "2026-10-08 12:02:05.252 ERROR [Framework Event Dispatcher: Equinox "
        "Container: bf9f][Framework:47] FrameworkEvent ERROR\n"
        "org.osgi.service.component.ComponentException: Failed activating "
        "component\n"
        "\tat com.liferay.object.rest.internal.deployer."
        "ObjectDefinitionDeployerImpl.lambda$_initSystemObjectDefinition$8"
        "(ObjectDefinitionDeployerImpl.java:843)\n"
        "2026-10-08 12:02:05.322 INFO  [x][PanelAppRegistry:61] duplicate\n"
    )
    tolerated = (
        (
            ("FrameworkEvent ERROR",),
            ("Failed activating component", "ObjectDefinitionDeployerImpl"),
        ),
    )

    expect_accepted(
        "Liferay's Objects redeploy error, which the rule names by its trace",
        lambda: checks.assert_log_clean(
            clean + objects_error, "uninstalling", tolerated=tolerated
        ),
    )

    expect_rejected(
        "a FrameworkEvent ERROR whose trace is something else",
        lambda: checks.assert_log_clean(
            clean + objects_error.replace(
                "ObjectDefinitionDeployerImpl", "SomethingElseImpl"
            ),
            "uninstalling",
            tolerated=tolerated,
        ),
    )

    expect_rejected(
        "the deployer named only by a later entry, not this error's trace",
        lambda: checks.assert_log_clean(
            clean
            + "2026-10-08 12:02:05.000 ERROR [x][Framework:47] FrameworkEvent "
            "ERROR\n"
            "org.osgi.service.component.ComponentException: Failed activating "
            "component\n"
            "2026-10-08 12:02:05.100 INFO  [x][Y:1] "
            "ObjectDefinitionDeployerImpl redeployed\n",
            "uninstalling",
            tolerated=tolerated,
        ),
    )

    # The real rules, against the three victims of the AopServiceManager
    # cascade seen so far, their traces trimmed to the frames that matter.
    def cascade_error(first_frame, extra_frame=""):
        return (
            "2026-10-09 19:28:02.305 ERROR [Framework Event Dispatcher: "
            "Equinox Container: d1e4][Framework:47] FrameworkEvent ERROR\n"
            "java.lang.NullPointerException\n"
            "\tat %s\n%s"
            "\tat com.liferay.portal.aop.internal.AopServiceRegistrar.register"
            "(AopServiceRegistrar.java:78)\n"
            "\tat com.liferay.portal.aop.internal.AopServiceManager$"
            "AopServiceServiceTrackerCustomizer.lambda$addingService$0"
            "(AopServiceManager.java:275)\n"
            "2026-10-09 19:28:02.400 INFO  [x][Y:1] next\n"
        ) % (first_frame, extra_frame)

    for victim in (
        "com.liferay.object.rest.internal.deployer.ObjectDefinitionDeployerImpl"
        "._initSystemObjectDefinition(ObjectDefinitionDeployerImpl.java:840)",
        "com.liferay.portal.vulcan.internal.fields.NestedFieldsSetterUtil$"
        "NestedFieldServiceTrackerCustomizer.addingService"
        "(NestedFieldsSetterUtil.java:335)",
        "com.liferay.exportimport.internal.data.handler."
        "BatchEnginePortletDataHandlerRegistrar$"
        "VulcanBatchEngineTaskItemDelegateServiceTrackerCustomizer.addingService"
        "(BatchEnginePortletDataHandlerRegistrar.java:120)",
    ):
        expect_accepted(
            "Liferay's AOP re-registration cascade: %s" % victim.split("(")[0],
            lambda victim=victim: checks.assert_log_clean(
                clean + cascade_error(victim),
                "uninstalling",
                tolerated=checks.UNINSTALL_TOLERATED,
            ),
        )

    expect_rejected(
        "the same cascade with this plugin's code in the trace",
        lambda: checks.assert_log_clean(
            clean + cascade_error(
                "com.liferay.portal.vulcan.internal.fields.NestedFieldsSetterUtil"
                ".addingService(NestedFieldsSetterUtil.java:335)",
                "\tat ai.tensoropt.sel.internal.SearcherWrapper.search"
                "(SearcherWrapper.java:90)\n",
            ),
            "uninstalling",
            tolerated=checks.UNINSTALL_TOLERATED,
        ),
    )

    expect_rejected(
        "a FrameworkEvent ERROR outside the AOP re-registration cascade",
        lambda: checks.assert_log_clean(
            clean + objects_error,
            "uninstalling",
            tolerated=checks.UNINSTALL_TOLERATED,
        ),
    )

    # TO-144: the CI signature that UNINSTALL_TOLERATED's AopServiceManager
    # rule does not cover, trimmed from
    # build/gitlab/e2e/results/mysql-mariadb-driver/logs/sel-e2e-liferay.log
    # (mysql-mariadb-driver, dxp-2025.q1, 2026-10-26 06:34:03). Aries' own
    # Whiteboard logger, not Equinox's FrameworkEvent dispatcher, reports
    # CxfJaxrsServiceRegistrator.rewire failing on Liferay's vulcan NPE, with
    # no "FrameworkEvent" marker on the line at all.
    whiteboard_error = (
        "2026-10-26 06:34:03.084 ERROR [SystemExecutorServiceUtil-8]"
        "[Whiteboard:80] ServiceReference CachingServiceReference "
        "{...AccountResource...} for endpoint produced error: {} [Sanitized]\n"
        "org.apache.cxf.service.factory.ServiceConstructionException: null\n"
        "\tat org.apache.cxf.jaxrs.JAXRSServerFactoryBean.create"
        "(JAXRSServerFactoryBean.java:220)\n"
        "\tat org.apache.aries.jax.rs.whiteboard.internal.cxf."
        "CxfJaxrsServiceRegistrator.doRewire(CxfJaxrsServiceRegistrator.java:533)\n"
        "\tat org.apache.aries.jax.rs.whiteboard.internal.cxf."
        "CxfJaxrsServiceRegistrator.rewire(CxfJaxrsServiceRegistrator.java:354)\n"
        "\tat org.apache.aries.jax.rs.whiteboard.internal.cxf."
        "CxfJaxrsServiceRegistrator.remove(CxfJaxrsServiceRegistrator.java:339)\n"
        "\tat org.apache.aries.jax.rs.whiteboard.internal.cxf."
        "CxfJaxrsServiceRegistrator.add(CxfJaxrsServiceRegistrator.java:109)\n"
        "Caused by: java.lang.NullPointerException: Cannot invoke "
        "\"Object.getClass()\" because \"resource\" is null\n"
        "\tat com.liferay.portal.vulcan.internal.jaxrs.writer.interceptor."
        "NestedFieldsWriterInterceptor$NestedFieldServiceTrackerCustomizer."
        "addingService(NestedFieldsWriterInterceptor.java:149)\n"
        "2026-10-26 06:34:03.114 WARN  [Framework Event Dispatcher: Equinox "
        "Container: 85df][Framework:53] FrameworkEvent WARNING\n"
    )

    expect_accepted(
        "the exact CI Whiteboard/vulcan signature",
        lambda: checks.assert_log_clean(
            clean + whiteboard_error,
            "uninstalling",
            tolerated=checks.UNINSTALL_TOLERATED,
        ),
    )

    expect_rejected(
        "the same Whiteboard line without the vulcan cause",
        lambda: checks.assert_log_clean(
            clean + whiteboard_error.replace(
                "Caused by: java.lang.NullPointerException: Cannot invoke "
                "\"Object.getClass()\" because \"resource\" is null\n"
                "\tat com.liferay.portal.vulcan.internal.jaxrs.writer.interceptor."
                "NestedFieldsWriterInterceptor$NestedFieldServiceTrackerCustomizer."
                "addingService(NestedFieldsWriterInterceptor.java:149)\n",
                "Caused by: java.lang.IllegalStateException: something else "
                "entirely\n",
            ),
            "uninstalling",
            tolerated=checks.UNINSTALL_TOLERATED,
        ),
    )

    expect_rejected(
        "the same Whiteboard/vulcan signature with this plugin's code in "
        "the trace",
        lambda: checks.assert_log_clean(
            clean + whiteboard_error.replace(
                "\tat org.apache.aries.jax.rs.whiteboard.internal.cxf."
                "CxfJaxrsServiceRegistrator.add(CxfJaxrsServiceRegistrator."
                "java:109)\n",
                "\tat org.apache.aries.jax.rs.whiteboard.internal.cxf."
                "CxfJaxrsServiceRegistrator.add(CxfJaxrsServiceRegistrator."
                "java:109)\n"
                "\tat ai.tensoropt.sel.internal.searcher.LoggingSearcher.search"
                "(LoggingSearcher.java:90)\n",
            ),
            "uninstalling",
            tolerated=checks.UNINSTALL_TOLERATED,
        ),
    )

    expect_rejected(
        "the same vulcan cause without the rewire frame",
        lambda: checks.assert_log_clean(
            clean + whiteboard_error.replace(
                "\tat org.apache.aries.jax.rs.whiteboard.internal.cxf."
                "CxfJaxrsServiceRegistrator.rewire(CxfJaxrsServiceRegistrator."
                "java:354)\n",
                "",
            ),
            "uninstalling",
            tolerated=checks.UNINSTALL_TOLERATED,
        ),
    )

def check_format_version():
    """TO-122: the archive format is named by the plugin's major.minor."""
    expect_accepted(
        "format 1.0 from plugin 1.0.1",
        lambda: checks.assert_format_version(
            {"format_version": "1.0", "plugin_version": "1.0.1"}
        ),
    )

    for description, manifest in (
        ("format_version missing", {"plugin_version": "1.0.1"}),
        (
            "the full plugin version used as the format",
            {"format_version": "1.0.1", "plugin_version": "1.0.1"},
        ),
        (
            "a format that is not this plugin version's",
            {"format_version": "1.0", "plugin_version": "1.1.0"},
        ),
    ):
        expect_rejected(
            description,
            lambda manifest=manifest: checks.assert_format_version(manifest),
        )


def check_widget_difference():
    """The uninstall case's widget wait names what differs on timeout."""
    description = checks.describe_widget_difference(
        ["search_bar", "search_results", "type_facet"],
        ["search_bar", "search_results", "site_facet"],
    )

    _assert(
        "missing compared with before: ['type_facet']" in description
        and "extra compared with before: ['site_facet']" in description,
        "the widget difference does not name the missing and extra widgets: "
        + description,
    )

    CHECKED.append("accepted: a widget timeout names the missing and extra widgets")


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


def check_result_count_parsing():
    """parse_result_count: the thousands separator and the two renderings
    the Search Results widget uses when it shows no total label at all."""
    expect_accepted(
        "a result count with a thousands separator is read as one integer",
        lambda: _assert(
            parse_result_count(
                '<span class="search-total-label">1,234 Results for '
                "<strong>annual report</strong></span>"
            )
            == 1234
        ),
    )

    expect_accepted(
        "no results were found renders as zero, not as a missing count",
        lambda: _assert(
            parse_result_count("<p>No results were found.</p>") == 0
        ),
    )

    expect_accepted(
        "a page with neither rendering is None, distinct from an honest zero",
        lambda: _assert(
            parse_result_count("<p>This is not a search results page.</p>")
            is None
        ),
    )


def check_admission_counters_and_background_tasks_parsing():
    """parse_admission_counters and parse_background_tasks: the two admin
    screen parsers that used to be inline Portal methods reachable only
    through a live screen, now module level functions driven here against
    saved markup."""
    counters_html = """
    <table>
    <tr><td>Searches observed while enabled</td><td>18</td></tr>
    <tr><td>Searches passing condition 1 (keywords, or facet selections when enabled)</td><td>10</td></tr>
    <tr><td>Searches admitted (all conditions)</td><td>9</td></tr>
    <tr><td>Events handed to the message bus</td><td>9</td></tr>
    <tr><td>Events dropped</td><td>1</td></tr>
    <tr><td>Events written to the database</td><td>8</td></tr>
    </table>
    """

    expect_accepted(
        "every admission counter label is read off the admin screen",
        lambda: _assert(
            parse_admission_counters(counters_html)
            == {
                "observed": 18,
                "keywords": 10,
                "admitted": 9,
                "dispatched": 9,
                "dropped": 1,
                "persisted": 8,
            }
        ),
    )

    expect_rejected(
        "a missing admission counter label raises rather than returning a"
        " partial result",
        lambda: parse_admission_counters("<table></table>"),
    )

    tasks_html = """
    <h3>Recent Exports</h3>
    <table>
    <tbody>
    <tr>
    <td>2026-09-24 10:00</td><td>2026-09-24 10:01</td><td>1m</td>
    <td>Completed</td>
    <td><a href="/download?a=1&amp;b=2">Download</a></td>
    </tr>
    </tbody>
    </table>
    """

    expect_accepted(
        "an export row's cells and escaped download URL are read back",
        lambda: _assert(
            parse_background_tasks(tasks_html)
            == [
                {
                    "started": "2026-09-24 10:00",
                    "finished": "2026-09-24 10:01",
                    "duration": "1m",
                    "status": "Completed",
                    "download_url": "/download?a=1&b=2",
                }
            ]
        ),
    )

    expect_accepted(
        "no Recent Exports table at all reads as no rows, not an error",
        lambda: _assert(parse_background_tasks("<p>Nothing here.</p>") == []),
    )


def check_zeroresult_none_handling():
    """compute_z1 groups by audience_type, which jq's group_by sees as a
    perfectly ordinary key even when it is null; compute_z2 skips a
    zero-result first-page event whose query itself is null rather than
    normalizing None and crashing."""
    events_with_null_audience = [
        {"requested_from": 0, "total_hits": 0, "audience_type": None},
        {"requested_from": 0, "total_hits": 3, "audience_type": "GUEST"},
    ]

    expect_accepted(
        "a null audience_type groups under its own key instead of raising",
        lambda: _assert(
            {
                entry["audience_type"]: entry["searches"]
                for entry in zeroresult.compute_z1(
                    events_with_null_audience
                )["by_audience"]
            }
            == {None: 1, "GUEST": 1}
        ),
    )

    events_with_null_query = [
        {"requested_from": 0, "total_hits": 0, "query": None},
        {"requested_from": 0, "total_hits": 0, "query": "annual report"},
    ]

    expect_accepted(
        "a null query is skipped by Z2 rather than normalized and counted",
        lambda: _assert(
            [
                entry["query"]
                for entry in zeroresult.compute_z2(events_with_null_query)
            ]
            == ["annual report"]
        ),
    )


def check_pluginconfig_reading():
    """pluginconfig.read: Felix's typed configuration syntax, the entry
    format it insists on, and the manifest key mapping it hands back."""

    counter = [0]

    def _config(contents):
        counter[0] += 1

        directory = os.path.join(TMP, "pluginconfig-%d" % counter[0])

        path = os.path.join(directory, pluginconfig.CONFIGURATION_FILE)

        os.makedirs(os.path.dirname(path), exist_ok=True)

        with open(path, "w", encoding="utf-8") as file_:
            file_.write(contents)

        return directory

    expect_accepted(
        "a boolean and an integer entry are read with their real types",
        lambda: _assert(
            pluginconfig.read(
                _config('enabled=B"true"\ncaptureDepth=I"100"\n')
            )
            == {"enabled": True, "captureDepth": 100}
        ),
    )

    expect_rejected(
        "a value typed with a code this reader does not know raises",
        lambda: pluginconfig.read(_config('retentionDays=L"90"\n')),
    )

    expect_rejected(
        "a line that is not a configuration entry raises",
        lambda: pluginconfig.read(_config('enabled=B"true"\nnot an entry\n')),
    )

    expect_rejected(
        "a configuration file that sets nothing raises",
        lambda: pluginconfig.read(_config("")),
    )

    expect_accepted(
        "manifest_expectations maps only the attributes the manifest reports",
        lambda: _assert(
            pluginconfig.manifest_expectations(
                {
                    "enabled": True,
                    "captureDepth": 100,
                    "readinessMinimumDays": 30,
                }
            )
            == {"enabled": True, "capture_depth": 100}
        ),
    )


def check_results_trim_and_junit_outcomes():
    """results._trim's line budget, and that write_junit reports an
    EXPECTED_FAILURE as a skip carrying the whole traceback and an
    UNEXPECTED_PASS as a failure, the two outcomes write_text and write_junit
    agree about nowhere else in this file.

    Each scenario below is handed to expect_accepted rather than asserted
    bare: a bare _assert's failure would raise past this function and abort
    the whole run instead of being recorded as one FAIL among the tally."""
    from harness.results import _REPORT_FAILURE_LINES, _trim

    short = ["line %d" % index for index in range(5)]

    def short_failure_untouched():
        _assert(
            _trim("\n".join(short)) == short,
            "a failure within the line budget must be returned unchanged",
        )

    expect_accepted("a short failure is not trimmed at all", short_failure_untouched)

    long_failure = ["line %d" % index for index in range(_REPORT_FAILURE_LINES + 10)]

    def long_failure_trimmed():
        trimmed = _trim("\n".join(long_failure))

        _assert(
            trimmed[:6] == long_failure[:6], "the first six lines must survive"
        )
        _assert(
            trimmed[-(_REPORT_FAILURE_LINES - 6):]
            == long_failure[-(_REPORT_FAILURE_LINES - 6):],
            "the last lines must survive",
        )
        _assert(
            "lines omitted" in trimmed[6],
            "an omission marker must separate the head and the tail",
        )

    expect_accepted(
        "a long failure keeps its first six and last lines, with an "
        "omission marker between them",
        long_failure_trimmed,
    )

    import xml.etree.ElementTree as ElementTree

    def fails(case):
        raise AssertionError("boom")

    def passes(case):
        return True

    def expected_and_genuine_failures_reported_correctly():
        results = Results(expected_failures={"known": "D-1, a known defect"})

        results.run("known", fails)
        results.run("healthy", fails)

        path = os.path.join(TMP, "junit-outcomes.xml")

        results.write_junit(path)

        tree = ElementTree.parse(path)
        root = tree.getroot()

        by_name = {
            element.get("name"): element for element in root.findall("testcase")
        }

        known_element = by_name["known"]
        healthy_element = by_name["healthy"]

        _assert(
            known_element.find("skipped") is not None
            and known_element.find("failure") is None,
            "an EXPECTED_FAILURE must be reported as skipped, not as a "
            "failure",
        )
        _assert(
            "boom" in (known_element.find("system-out").text or ""),
            "an EXPECTED_FAILURE's traceback must survive in system-out",
        )
        _assert(
            healthy_element.find("failure") is not None,
            "a genuine failure must still be reported as a failure",
        )

    expect_accepted(
        "write_junit reports an expected failure as skipped with its "
        "traceback kept, and a genuine failure as a failure",
        expected_and_genuine_failures_reported_correctly,
    )

    def unexpected_pass_reported_as_failure():
        results = Results(expected_failures={"known": "D-1, a known defect"})

        results.run("known", passes)

        path = os.path.join(TMP, "junit-unexpected-pass.xml")

        results.write_junit(path)

        tree = ElementTree.parse(path)

        known_element = tree.getroot().find("testcase")

        _assert(
            known_element.find("failure") is not None,
            "an UNEXPECTED_PASS must be reported as a failure, not silently",
        )

    expect_accepted(
        "write_junit reports an unexpected pass as a failure",
        unexpected_pass_reported_as_failure,
    )


def check_wait_for_stability_and_exceptions():
    """util.wait_for: stable requires consecutive successes, a flap resets
    the count, and an exception from the predicate is reported on timeout
    rather than swallowed. Each scenario goes through expect_accepted, the
    same as every other check in this file, so a failure here is recorded
    as one FAIL rather than aborting the whole run."""
    from harness.util import TimeoutError_, wait_for

    calls = []

    def flapping():
        calls.append(1)

        # True, False, True, True: stable=2 must not accept the first True,
        # since the very next call flaps back to False.

        return [True, False, True, True][len(calls) - 1]

    def stable_requires_every_consecutive_check():
        value = wait_for(
            "a flapping condition", flapping, timeout=5, interval=0.01, stable=2
        )

        _assert(value is True)
        _assert(
            len(calls) == 4,
            "stable=2 must not accept the first success before the flap",
        )

    expect_accepted(
        "a condition is not accepted until it holds for every required "
        "consecutive check",
        stable_requires_every_consecutive_check,
    )

    def always_raises():
        raise RuntimeError("boom from the predicate")

    def timeout_reports_the_predicates_own_exception():
        try:
            wait_for(
                "a predicate that always raises", always_raises, timeout=0.05,
                interval=0.01,
            )
        except TimeoutError_ as timeout_error:
            _assert(
                "boom from the predicate" in str(timeout_error),
                "the predicate's own exception must be reported on "
                "timeout: %s" % timeout_error,
            )

            return

        raise AssertionError(
            "a predicate that always raises must time out"
        )

    expect_accepted(
        "a timeout reports the predicate's own exception",
        timeout_reports_the_predicates_own_exception,
    )


def check_remaining_harness_parsers():
    """foreign_task_url, _stream_events, assert_zero_result_panel_matches and
    describe_widget_difference's remaining branches."""
    expect_rejected(
        "a download URL naming two backgroundTaskId parameters",
        lambda: checks.foreign_task_url(
            "/export?backgroundTaskId=1&backgroundTaskId=2", 99
        ),
    )

    non_int_from = [
        event_line(0, requested_from="not-an-int", hits=[hit_object(0, 1)])
    ]

    expect_accepted(
        "requested_from that is not an int is treated as 0",
        stream(non_int_from),
    )

    def none_after_describes_unreadable_widgets():
        description = checks.describe_widget_difference(
            ["search_bar", "search_results"], None
        )

        _assert(
            "could not be read" in description
            and "search_bar" in description,
            "a None 'after' must say the widgets could not be read, and "
            "still name what 'before' held: %s" % description,
        )

    expect_accepted(
        "a None 'after' is described as widgets that could not be read, "
        "not a crash",
        none_after_describes_unreadable_widgets,
    )

    guest_only_events = [
        _zero_result_event("widget", audience_type="GUEST"),
    ]

    empty_audience_panel = {
        "no_searches_logged": False,
        "overall": {"zero_result": 1, "searches": 1, "rate": "100.0"},
        "guests": {"zero_result": 1, "searches": 1, "rate": "100.0"},
        "signed_in": {"zero_result": 0, "searches": 0, "rate": "0.0"},
        "top_queries": [{"query": "widget", "searches": 1}],
    }

    expect_accepted(
        "a panel with no authenticated events at all reports the "
        "signed-in audience as zero rather than raising",
        lambda: checks.assert_zero_result_panel_matches(
            empty_audience_panel, guest_only_events
        ),
    )

    wrong_empty_audience_panel = dict(
        empty_audience_panel,
        signed_in=dict(empty_audience_panel["signed_in"], searches=5),
    )

    expect_rejected(
        "a panel misreporting the empty signed-in audience is caught",
        lambda: checks.assert_zero_result_panel_matches(
            wrong_empty_audience_panel, guest_only_events
        ),
    )


def check_stack_and_generator_parsing():
    """Stack.sql's NULL and blank line handling on mysql, Stack._port_owner's
    `docker ps` parsing, and Generator._sql's MariaDB substitution, all
    driven by monkeypatching harness.stack.run rather than a real daemon.
    Each scenario goes through expect_accepted rather than a bare _assert,
    so a failure is recorded as one FAIL rather than aborting the run; the
    monkeypatch is still restored in a finally either way."""
    from harness import generate, stack as stack_module

    mysql_stack = stack_module.Stack(
        TMP, "selftest-mysql", 1, 1, database="mysql"
    )

    original_run = stack_module.run

    def sql_skips_blanks_and_reads_null_as_empty():
        stack_module.run = lambda *args, **kwargs: (
            0, "1\tfirst\n\n2\tNULL\n", ""
        )

        rows = mysql_stack.sql("select a, b from t")

        _assert(
            rows == [["1", "first"], ["2", ""]],
            "a blank line must be skipped and NULL must read as empty on "
            "mysql: %r" % rows,
        )

    def port_owner_parses_docker_ps_columns():
        stack_module.run = lambda *args, **kwargs: (
            0,
            "my-liferay\tmy-project\t0.0.0.0:8080->8080/tcp, "
            "0.0.0.0:9999->9999/tcp\nother\t\t0.0.0.0:7000->7000/tcp\n",
            "",
        )

        _assert(
            mysql_stack._port_owner(8080) == ("my-liferay", "my-project"),
            "the container and project publishing the port must be parsed "
            "off docker ps's own columns",
        )
        _assert(
            mysql_stack._port_owner(7000) == ("other", None),
            "an empty compose project label must read as None, not as an "
            "empty string",
        )
        _assert(
            mysql_stack._port_owner(1) == (None, None),
            "a port nothing publishes must read as not found",
        )

    try:
        expect_accepted(
            "Stack.sql skips blank lines and reads NULL as empty on mysql",
            sql_skips_blanks_and_reads_null_as_empty,
        )
        expect_accepted(
            "_port_owner parses docker ps's name, project and ports columns",
            port_owner_parses_docker_ps_columns,
        )
    finally:
        stack_module.run = original_run

    class _StackStub:
        dialect = "mysql"
        database = "mariadb"

    class _MysqlStackStub:
        dialect = "mysql"
        database = "mysql"

    def generator_sql_substitutes_mariadb_session_variable():
        mariadb_sql = generate.Generator(_StackStub(), 1, 1)._sql("EVENTS")

        _assert(
            "max_recursive_iterations" in mariadb_sql
            and "cte_max_recursion_depth" not in mariadb_sql,
            "MariaDB's session variable name must replace MySQL's",
        )

        mysql_sql = generate.Generator(_MysqlStackStub(), 1, 1)._sql("EVENTS")

        _assert(
            "cte_max_recursion_depth" in mysql_sql,
            "plain mysql must keep its own session variable name",
        )

    expect_accepted(
        "Generator._sql substitutes MariaDB's session variable name and "
        "leaves plain mysql alone",
        generator_sql_substitutes_mariadb_session_variable,
    )


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
        check_hit_ranks()
        check_snippet_shape()
        check_vendor_neutrality(tmp)
        check_zero_egress()
        check_funnel_links()
        check_archive_name()
        check_number_types()
        check_widget_difference()
        check_format_version()
        check_funnel_counters()
        check_zero_result_recipes()
        check_zero_result_panel_parsing()
        check_zero_result_panel_matches_events()
        check_zero_result_panel_xss_escaping()
        check_notification_listed()
        check_daily_schedule()
        check_foreign_download()
        check_queue_overflow()
        check_log_clean()
        check_unbounded_archive()
        check_bundle_wait_is_anchored()
        check_export_form_anchor()
        check_collection_start_date()
        check_expected_failures()
        check_xml_sanitising()
        check_result_count_parsing()
        check_admission_counters_and_background_tasks_parsing()
        check_zeroresult_none_handling()
        check_pluginconfig_reading()
        check_results_trim_and_junit_outcomes()
        check_wait_for_stability_and_exceptions()
        check_remaining_harness_parsers()
        check_stack_and_generator_parsing()

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
