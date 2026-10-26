#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Measures DESIGN.md 3's "no measurable impact on search latency" claim,
with a confidence interval, against a portal already started by `make run`.

    scripts/loadtest.py [--database postgres] [--http-port 8080]
                        [--blocks 10] [--searches 30] [--seed 1] [--json]

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

# A term that matches nothing is a defensible, conservative floor: it still
# exercises the admission filter and the configuration check on every
# search, which is where collection's synchronous overhead actually lives
# (DESIGN.md 3.1's LoggingSearcher), even without any guarantee that the
# portal this runs against has indexed content a richer term could match.
# A portal started through `make run` has no content seeded into it, unlike
# the e2e suite's own fixtures.
SEARCH_TERM = "search-eval-logger-loadtest-probe-term"


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


def _run_blocks(guest, blocks, searches_per_block, admin, company_id):
    disabled_samples = []
    enabled_samples = []

    for block in range(blocks):
        log("block %d/%d: disabled" % (block + 1, blocks))
        _set_enabled(admin, company_id, False)

        for _ in range(searches_per_block):
            started = time.monotonic()
            guest.search(SEARCH_TERM)
            disabled_samples.append(time.monotonic() - started)

        log("block %d/%d: enabled" % (block + 1, blocks))
        _set_enabled(admin, company_id, True)

        for _ in range(searches_per_block):
            started = time.monotonic()
            guest.search(SEARCH_TERM)
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
    options = parser.parse_args(argv)

    stack = stack_for(options)
    admin = Portal(stack)
    guest = Portal(stack)

    _ensure_portal_running(admin, stack.base_url)

    admin.wait_until_serving(timeout=60)

    company_id = admin.company_id()

    original_enabled = admin.run_script(
        scripts.CONFIGURATION_ENABLED % {"company_id": company_id}
    ) == "true"

    try:
        log("warming up with %d searches" % options.warmup)

        for _ in range(options.warmup):
            guest.search(SEARCH_TERM)

        disabled_samples, enabled_samples = _run_blocks(
            guest, options.blocks, options.searches, admin, company_id
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
Disabled: %(disabled_samples)d samples, median %(disabled_median).1f ms, p90 %(disabled_p90).1f ms
Enabled:  %(enabled_samples)d samples, median %(enabled_median).1f ms, p90 %(enabled_p90).1f ms
Overhead: %(overhead).1f%% (95%% CI %(ci_low).1f%% to %(ci_high).1f%%, %(resamples)d bootstrap resamples)
""" % {
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
