#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Measures DESIGN.md 3's "no measurable impact on search latency" claim,
with a confidence interval, against a portal already started by `make run`.

    scripts/loadtest.py [--database postgres] [--http-port 8080]
                        [--blocks 10] [--searches 30] [--seed 1]
                        [--term TERM] [--json]

Where the e2e suite's search-overhead case (DESIGN.md 7, D8) is a coarse
pass/fail gate sized to tolerate a busy CI runner, this script is the actual
measurement: run it by hand on a quiet, otherwise idle machine, where a
shared or busy one would widen the interval without saying anything about
the plugin.

This refuses, before touching anything, if no portal answers at
--database/--http-port's address: it does not start Docker itself, the same
way scripts/portal.py's own up/down split leaves starting and stopping as a
separate, explicit step from exercising what is already running.

It reuses the e2e harness's Portal and Stack classes, and the
SET_CONFIGURATION_ENABLED/CONFIGURATION_ENABLED Groovy scripts the
search-overhead case itself uses, the same way scripts/portal.py reuses the
harness rather than keeping a second copy of it.

**Side effects on the portal it runs against.** This is written for a
throwaway `make run` portal, not a shared or production one:

- With no `--term`, it seeds `_SEED_COUNT` blog postings carrying a marker
  term through the headless API (the same mechanism the e2e suite's
  `content_and_searches` uses), idempotently: a run against a portal
  already seeded by an earlier run finds the content through a search
  first and skips seeding. That is deliberate, not an incidental
  side-effect to tolerate: searching a term that matches nothing (what
  this script measured before this change) skips the per-hit capture cost
  `SearchEventCaptor` runs synchronously on the search thread, up to
  `captureDepth` hits, for every hit in the response, so it only measured
  the admission filter's cost on a guaranteed miss, not the cost that
  actually scales with what a search returns.
- Every search this script runs while collection is enabled writes an
  event (and its hits) to the plugin's tables.
- Each block disables and re-enables collection once, which closes and
  reopens the collection cycle (DESIGN.md 3.6) and sends a "collection
  started" notification to the signed-in administrator on each reopen.
- It leaves a company-scoped `SearchEvalLoggerConfiguration` row behind
  (Configuration Admin's own storage), the same as the e2e suite's
  `SET_CONFIGURATION_ENABLED` does, since the API that reads every other
  configured value and writes it back has no narrower way to flip one
  key.

None of that matters on a portal `make run` starts for exactly this
purpose and `make stop VOLUMES=1` discards afterward; it would on anything
else.
"""

import argparse
import os
import sys
import time

SCRIPTS = os.path.dirname(os.path.abspath(__file__))
REPOSITORY = os.path.dirname(SCRIPTS)
E2E = os.path.join(REPOSITORY, "e2e")

sys.path.insert(0, E2E)
sys.path.insert(0, SCRIPTS)

from harness import scripts, stats  # noqa: E402
from harness.portal import Portal  # noqa: E402
from harness.stack import DATABASES  # noqa: E402
from harness.util import HarnessError, log, wait_for  # noqa: E402
from portal import stack_for  # noqa: E402

# Seeded, by default, as findable content (see _ensure_content_seeded):
# distinctive enough that it cannot collide with anything already on a
# portal somebody runs this against.
# One token, so the search engine cannot split it into common words that
# unrelated content matches.
DEFAULT_SEARCH_TERM = "loadtestprobeterm"

# How many blog postings _ensure_content_seeded creates, each carrying
# DEFAULT_SEARCH_TERM, so each measured search returns this many hits and
# SearchEventCaptor's per-hit capture cost is actually exercised (up to
# captureDepth, default 100, so this stays well under the ceiling).
_SEED_COUNT = 20


def _ensure_portal_running(portal, base_url):
    try:
        wait_for(
            "a portal to answer at %s" % base_url,
            lambda: portal.get("/c/portal/robots", timeout=10).status == 200,
            timeout=15, interval=3.0,
        )
    except HarnessError as error:
        raise HarnessError(
            "No portal is answering at %s (%s). Start one first with "
            "`make run DB=... PORT=...`." % (base_url, error)
        )


def _set_enabled(admin, company_id, enabled):
    """Flips the plugin's enabled setting, leaving every other configured
    value as it was, and waits for the change to be visible. Returns the
    value held before this call. Shared with the e2e search-overhead case
    through the same scripts module, so both toggle the same way."""
    wanted = "true" if enabled else "false"

    previous = admin.run_script(
        scripts.SET_CONFIGURATION_ENABLED % {
            "company_id": company_id, "enabled": wanted,
        }
    ) == "true"

    wait_for(
        "the configuration change to %s collection to take effect"
        % ("enable" if enabled else "disable"),
        lambda: admin.run_script(
            scripts.CONFIGURATION_ENABLED % {"company_id": company_id}
        ) == wanted,
        timeout=15, interval=1.0,
    )

    return previous


def _ensure_content_seeded(admin, company_id, term):
    """Seeds _SEED_COUNT blog postings carrying `term` through the headless
    API, the same way the e2e suite's content_and_searches does, unless a
    search for `term` already finds at least that many: a second run
    against a portal an earlier run already seeded must not keep adding
    more postings forever.

    Waits until the seeded content is actually searchable before
    returning, the same control content_and_searches applies, so a
    measurement that starts immediately after does not undercount hits
    because indexing is still catching up.
    """
    count, _ = admin.search_result_count(term)

    if count is not None and count >= _SEED_COUNT:
        log(
            "%d existing hits for %r already meet the %d needed; not seeding"
            % (count, term, _SEED_COUNT)
        )

        return

    group_id = admin.group_id(company_id)

    log("Seeding %d blog postings carrying %r into group %d" % (
        _SEED_COUNT, term, group_id,
    ))

    for index in range(_SEED_COUNT):
        status, body = admin.headless(
            "POST",
            "/o/headless-delivery/v1.0/sites/%d/blog-postings" % group_id,
            {
                "headline": "%s note %d" % (term, index),
                "viewableBy": "Anyone",
                "articleBody": (
                    "%s is the marker term scripts/loadtest.py measures "
                    "against. Note %d covers leave, expenses and travel."
                    % (term, index)
                ),
            },
        )

        if status != 200:
            raise HarnessError(
                "Creating a loadtest blog posting failed (%d): %r"
                % (status, body)
            )

    wait_for(
        "the seeded content to become searchable for %r" % term,
        lambda: (admin.search_result_count(term)[0] or 0) >= _SEED_COUNT,
        timeout=120, interval=3.0,
    )

    log("Seeded content is now searchable")


def _run_blocks(guest, blocks, searches_per_block, admin, company_id, term):
    disabled_samples = []
    enabled_samples = []

    for block in range(blocks):
        log("block %d/%d: disabled" % (block + 1, blocks))
        _set_enabled(admin, company_id, False)

        for _ in range(searches_per_block):
            started = time.monotonic()
            guest.search(term)
            disabled_samples.append(time.monotonic() - started)

        log("block %d/%d: enabled" % (block + 1, blocks))
        _set_enabled(admin, company_id, True)

        for _ in range(searches_per_block):
            started = time.monotonic()
            guest.search(term)
            enabled_samples.append(time.monotonic() - started)

    return disabled_samples, enabled_samples


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--database", choices=DATABASES, default="postgres")
    parser.add_argument("--http-port", type=int, default=8080)
    parser.add_argument("--database-port", type=int, default=15433)
    parser.add_argument("--gogo-port", type=int, default=11312)
    parser.add_argument(
        "--blocks", type=int, default=10,
        help="interleaved disabled/enabled block pairs (default 10)",
    )
    parser.add_argument(
        "--searches", type=int, default=30,
        help="guest searches per state, per block (default 30)",
    )
    parser.add_argument(
        "--warmup", type=int, default=10,
        help="guest searches run before measuring, to let the JIT and any "
             "caches settle (default 10)",
    )
    parser.add_argument(
        "--resamples", type=int, default=2000,
        help="bootstrap resamples for the confidence interval (default 2000)",
    )
    parser.add_argument("--seed", type=int, default=None, help="seeds the bootstrap")
    parser.add_argument("--json", action="store_true", help="also print a JSON object")
    parser.add_argument(
        "--term", default=None,
        help="search term to measure with, assumed to already return hits "
             "on the target portal (default: %r, seeded by this script if "
             "not already present)" % DEFAULT_SEARCH_TERM,
    )
    options = parser.parse_args(argv)

    term = options.term or DEFAULT_SEARCH_TERM

    stack = stack_for(options)
    admin = Portal(stack)
    guest = Portal(stack)

    _ensure_portal_running(admin, stack.base_url)

    admin.wait_until_serving(timeout=60)

    company_id = admin.company_id()

    if options.term is None:
        _ensure_content_seeded(admin, company_id, term)

    # The measurement runs as a guest, so the hits have to be visible to a
    # guest, not just to the administrator who seeded them.

    guest_hits, _ = guest.search_result_count(term)

    if not guest_hits:
        raise HarnessError(
            "A guest search for %r returns no results, so the measurement "
            "would time searches that capture nothing" % term
        )

    log("a guest search for %r returns %d results" % (term, guest_hits))

    original_enabled = admin.run_script(
        scripts.CONFIGURATION_ENABLED % {"company_id": company_id}
    ) == "true"

    try:
        log("warming up with %d searches" % options.warmup)

        for _ in range(options.warmup):
            guest.search(term)

        disabled_samples, enabled_samples = _run_blocks(
            guest, options.blocks, options.searches, admin, company_id, term
        )
    finally:
        log("restoring enabled=%s" % original_enabled)
        _set_enabled(admin, company_id, original_enabled)

    disabled_median = stats.median(disabled_samples)
    enabled_median = stats.median(enabled_samples)
    disabled_p90 = stats.percentile(disabled_samples, 0.9)
    enabled_p90 = stats.percentile(enabled_samples, 0.9)
    overhead_ratio = stats.ratio(enabled_median, disabled_median)
    overhead_percent = (overhead_ratio - 1) * 100

    low_ratio, high_ratio = stats.bootstrap_ratio_ci(
        enabled_samples, disabled_samples,
        resamples=options.resamples, seed=options.seed,
    )

    report = {
        "term": term,
        "guest_hits": guest_hits,
        "disabled": {
            "samples": len(disabled_samples),
            "median_ms": disabled_median * 1000,
            "p90_ms": disabled_p90 * 1000,
        },
        "enabled": {
            "samples": len(enabled_samples),
            "median_ms": enabled_median * 1000,
            "p90_ms": enabled_p90 * 1000,
        },
        "overhead_percent": overhead_percent,
        "overhead_percent_ci95": [
            (low_ratio - 1) * 100, (high_ratio - 1) * 100,
        ],
    }

    print(
        """
Term:     %(term)r (%(guest_hits)d results for a guest)
Disabled: %(disabled_samples)d samples, median %(disabled_median).1f ms, p90 %(disabled_p90).1f ms
Enabled:  %(enabled_samples)d samples, median %(enabled_median).1f ms, p90 %(enabled_p90).1f ms
Overhead: %(overhead).1f%% (95%% CI %(ci_low).1f%% to %(ci_high).1f%%, %(resamples)d bootstrap resamples)
""" % {
            "term": term,
            "guest_hits": guest_hits,
            "disabled_samples": report["disabled"]["samples"],
            "disabled_median": report["disabled"]["median_ms"],
            "disabled_p90": report["disabled"]["p90_ms"],
            "enabled_samples": report["enabled"]["samples"],
            "enabled_median": report["enabled"]["median_ms"],
            "enabled_p90": report["enabled"]["p90_ms"],
            "overhead": report["overhead_percent"],
            "ci_low": report["overhead_percent_ci95"][0],
            "ci_high": report["overhead_percent_ci95"][1],
            "resamples": options.resamples,
        }
    )

    if options.json:
        import json

        print(json.dumps(report, indent=2))

    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
