#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""End to end functional test for the Liferay Search Eval Logger (TO-84).

Brings up PostgreSQL and Liferay DXP in Docker, installs the plugin onto the
running portal, restarts as DESIGN.md 3.1 requires, creates content, runs real
searches, generates a scale corpus, then exercises the retention purge, the
export and the funnel integration.

Exit codes: 0 every case passed, 1 at least one case failed, 2 the harness
could not run at all.
"""

import argparse
import glob
import os
import shutil
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from harness import checks  # noqa: E402
from harness.portal import Portal  # noqa: E402
from harness.results import Results  # noqa: E402
from harness.stack import Stack  # noqa: E402
from harness.util import HarnessError, log, run  # noqa: E402

DIRECTORY = os.path.dirname(os.path.abspath(__file__))

REPOSITORY = os.path.dirname(DIRECTORY)

MODULES = (
    "search-eval-logger-api",
    "search-eval-logger-service",
    "search-eval-logger-impl",
    "search-eval-logger-web",
)

# Scale tiers. The heavy one is opt in: ten million hit rows is not something
# to put on every pull request, and the smoke tier exercises every code path
# the heavy one does.
# The smoke tier's event count is not arbitrary. Events are spread evenly over
# the day span, and the readiness check of DESIGN.md 3.6 counts only those on or
# after a collection start date set readinessMinimumDays back, so the tier has
# to hold more than readinessMinimumEvents inside that window after the purge
# has taken everything past the retention window. 6,000 over 60 days leaves
# about 800 in the last 8 days against a threshold of 500.
SCALES = {
    "smoke": {"events": 6000, "hits_per_event": 10, "days_span": 60},
    "medium": {"events": 100000, "hits_per_event": 10, "days_span": 60},
    "full": {"events": 1000000, "hits_per_event": 10, "days_span": 60},
}

# Pinned by digest as well as by tag. This image compiles the bundles that are
# then tested, so an unpinned tag would let the artefact under test change
# without anything in this repository changing.
BUILD_IMAGE = (
    "eclipse-temurin:21-jdk@sha256:"
    "92a2a4d7a928d057e7bd999c418d66c26a34eb9a0442f3ab67721c3f88110b2d"
)


def parse_arguments(argv):
    parser = argparse.ArgumentParser(
        description="End to end functional test for search-eval-logger"
    )

    parser.add_argument("--project", default=os.environ.get("SEL_E2E_PROJECT", "sel-e2e"))
    parser.add_argument(
        "--http-port", type=int, default=int(os.environ.get("SEL_E2E_HTTP_PORT", "18080"))
    )
    parser.add_argument(
        "--postgres-port",
        type=int,
        default=int(os.environ.get("SEL_E2E_POSTGRES_PORT", "15432")),
    )
    parser.add_argument(
        "--scale",
        choices=sorted(SCALES),
        default=os.environ.get("SEL_E2E_SCALE", "smoke"),
        help="smoke is the default; full is 1,000,000 events and 10,000,000 hits",
    )
    parser.add_argument("--events", type=int)
    parser.add_argument("--hits-per-event", type=int)
    parser.add_argument("--days-span", type=int)
    parser.add_argument(
        "--chunk",
        type=int,
        default=100000,
        help="events per bulk insert statement",
    )
    parser.add_argument("--content-count", type=int, default=5)
    parser.add_argument("--search-count", type=int, default=5)
    parser.add_argument(
        "--results",
        default=os.environ.get(
            "SEL_E2E_RESULTS", os.path.join(DIRECTORY, "results")
        ),
    )
    parser.add_argument(
        "--keep",
        action="store_true",
        help="leave the stack running after the run, for debugging",
    )
    parser.add_argument(
        "--build",
        dest="build",
        action="store_true",
        default=None,
        help="always rebuild the bundles in a container",
    )
    parser.add_argument(
        "--no-build",
        dest="build",
        action="store_false",
        help="fail rather than build; use the jars already in modules/*/build/libs",
    )
    parser.add_argument("--boot-timeout", type=int, default=900)
    parser.add_argument("--deploy-timeout", type=int, default=600)
    parser.add_argument("--index-timeout", type=int, default=300)
    parser.add_argument("--export-timeout", type=int, default=3600)

    options = parser.parse_args(argv)

    scale = SCALES[options.scale]

    options.events = options.events or scale["events"]
    options.hits_per_event = options.hits_per_event or scale["hits_per_event"]
    options.days_span = options.days_span or scale["days_span"]
    options.directory = DIRECTORY
    options.results_directory = options.results

    return options


def find_jars():
    jars = []

    for module in MODULES:
        pattern = os.path.join(REPOSITORY, "modules", module, "build", "libs", "*.jar")

        matches = [
            match for match in glob.glob(pattern) if not match.endswith("-sources.jar")
        ]

        if matches:
            jars.append(sorted(matches)[-1])

    return jars


def build_bundles(project):
    """Builds in a container, so the harness needs no JDK on the host.

    The build needs network access to repository-cdn.liferay.com for the target
    platform artifacts. A named volume holds the Gradle cache, so the second
    run does not download them again.

    The volume is named after the project and is deliberately not removed by
    teardown: it holds hundreds of megabytes of target platform artifacts and
    exists to be reused. `docker volume rm <project>-gradle` is the way to
    reclaim it, and the README says so rather than leaving it to be found.
    """
    log("Building the bundles in %s" % BUILD_IMAGE)

    user = "%d:%d" % (os.getuid(), os.getgid())

    volume = "%s-gradle" % project

    run(["docker", "volume", "create", volume], timeout=120)

    run(
        [
            "docker",
            "run",
            "--rm",
            "-u",
            user,
            "-v",
            "%s:/workspace" % REPOSITORY,
            "-v",
            "%s:/gradle" % volume,
            "-e",
            "GRADLE_USER_HOME=/gradle",
            "-w",
            "/workspace",
            BUILD_IMAGE,
            "./gradlew",
            "--no-daemon",
            "build",
            "-x",
            "test",
        ],
        timeout=3600,
        capture=False,
    )


def main(argv):
    options = parse_arguments(argv)

    os.makedirs(options.results_directory, exist_ok=True)

    stack = Stack(
        DIRECTORY, options.project, options.http_port, options.postgres_port
    )

    try:
        stack.preflight()
    except HarnessError as error:
        log("Preflight failed: %s" % error)

        return 2

    jars = find_jars()

    if options.build or (options.build is None and len(jars) != len(MODULES)):
        if options.build is False:
            log("Missing bundles and --no-build was given")

            return 2

        try:
            build_bundles(options.project)
        except HarnessError as error:
            log("Build failed: %s" % error)

            return 2

        jars = find_jars()

    if len(jars) != len(MODULES):
        log("Expected %d bundles, found %d: %s" % (len(MODULES), len(jars), jars))

        return 2

    options.jars = jars

    deploy_directory = os.path.join(DIRECTORY, ".work", "deploy")

    # Always from a destroyed stack. There is deliberately no flag to reuse
    # one: a reused stack has the plugin installed, its counters moved and its
    # generated rows already at the primary keys the generator starts from, so
    # the first cases fail and the failures look like product bugs. A
    # debugging aid that fails in ways indistinguishable from the thing it is
    # debugging is worse than not having it, and --keep already covers
    # inspecting a stack after a run.

    stack.down(volumes=True)

    # The deploy directory is bind mounted, so it outlives the containers.
    # Clearing it is what makes a second run start from the same state as the
    # first, including after a run that failed halfway.

    if os.path.isdir(deploy_directory):
        shutil.rmtree(deploy_directory)

    os.makedirs(deploy_directory, exist_ok=True)

    portal = Portal(stack)

    context = checks.Context(stack, portal, options)

    results = Results(expected_failures=checks.EXPECTED_FAILURES)

    started = time.time()

    try:
        results.run("stack-up", lambda case: checks.stack_up(context, case))

        results.run(
            "plugin-absent-baseline",
            lambda case: checks.plugin_absent(context, case),
            requires=["stack-up"],
        )
        results.run(
            "install-onto-running-portal",
            lambda case: checks.install_onto_running_portal(context, case),
            requires=["stack-up"],
        )
        results.run(
            "ec3-bypass-detected",
            lambda case: checks.ec3_bypass_detected(context, case),
            requires=["install-onto-running-portal"],
        )
        results.run(
            "stall-notification",
            lambda case: checks.stall_notification(context, case),
            requires=["ec3-bypass-detected"],
        )
        results.run(
            "restart-clears-bypass",
            lambda case: checks.restart_clears_bypass(context, case),
            requires=["install-onto-running-portal"],
        )
        results.run(
            "daily-jobs-scheduled",
            lambda case: checks.daily_jobs_scheduled(context, case),
            requires=["restart-clears-bypass"],
        )
        results.run(
            "content-and-searches",
            lambda case: checks.content_and_searches(context, case),
            requires=["restart-clears-bypass"],
        )
        results.run(
            "capture",
            lambda case: checks.capture(context, case),
            requires=["content-and-searches"],
        )
        results.run(
            "collection-start-recorded",
            lambda case: checks.collection_start_recorded(context, case),
            requires=["capture"],
        )
        results.run(
            "scale-data",
            lambda case: checks.generate_scale_data(context, case),
            requires=["install-onto-running-portal"],
        )
        results.run(
            "retention-purge",
            lambda case: checks.retention_purge(context, case),
            requires=["scale-data"],
        )
        results.run(
            "funnel-readiness",
            lambda case: checks.funnel_readiness(context, case),
            requires=["retention-purge", "collection-start-recorded"],
        )
        results.run(
            "export",
            lambda case: checks.run_export(context, case),
            requires=["retention-purge"],
        )
        results.run(
            "export-archive-name",
            lambda case: checks.export_archive_name(context, case),
            requires=["export"],
        )
        results.run(
            "export-archive-contents",
            lambda case: checks.validate_archive(context, case),
            requires=["export"],
        )
        results.run(
            "export-range-is-honoured",
            lambda case: checks.export_range_is_honoured(context, case),
            requires=["export"],
        )
        results.run(
            "export-jsonl-number-types",
            lambda case: checks.export_jsonl_number_types(context, case),
            requires=["export"],
        )
        results.run(
            "export-archive-vendor-neutral",
            lambda case: checks.archive_is_vendor_neutral(context, case),
            requires=["export"],
        )
        results.run(
            "funnel-links",
            lambda case: checks.funnel_links(context, case),
            requires=["export", "funnel-readiness"],
        )
        results.run(
            "funnel-zero-egress",
            lambda case: checks.zero_egress(context, case),
            requires=["funnel-links"],
        )
        results.run(
            "export-foreign-download",
            lambda case: checks.export_foreign_download(context, case),
            requires=["export"],
        )
        results.run(
            "export-permission-refused",
            lambda case: checks.export_permission_refused(context, case),
            requires=["export"],
        )
        results.run(
            "export-unbounded-range",
            lambda case: checks.export_unbounded_range(context, case),
            requires=["export"],
        )
        results.run(
            "queue-overflow",
            lambda case: checks.queue_overflow(context, case),
            requires=["capture"],
        )

        # Last: it uninstalls the plugin and restarts the portal.

        results.run(
            "uninstall-and-reinstall",
            lambda case: checks.uninstall_and_reinstall(context, case),
            requires=["export", "capture"],
        )
    finally:
        junit_path = os.path.join(options.results_directory, "junit.xml")
        report_path = os.path.join(options.results_directory, "report.txt")

        results.write_junit(junit_path)

        report = results.write_text(report_path)

        log("Total wall clock: %.0fs" % (time.time() - started))
        sys.stdout.write("\n" + report + "\n")
        sys.stdout.write("JUnit XML: %s\n" % junit_path)

        if results.expected_failure_count:
            sys.stdout.write(
                "\n%d case(s) failed as known defects and are excluded from "
                "the exit code. They are named in checks.EXPECTED_FAILURES.\n"
                % results.expected_failure_count
            )

        if results.unexpected_pass_count:
            sys.stdout.write(
                "\n%d case(s) registered as known defects PASSED. Either the "
                "defect was fixed and the entry should be retired, or the case "
                "stopped testing what it claims to. This fails the run on "
                "purpose.\n" % results.unexpected_pass_count
            )

        if results.failure_count or options.keep:
            try:
                stack.save_logs(os.path.join(options.results_directory, "logs"))
                log("Container logs written to %s" % os.path.join(
                    options.results_directory, "logs"))
            except Exception as error:  # noqa: BLE001 - best effort
                log("Could not collect container logs: %r" % error)

        if options.keep:
            log(
                "Leaving the stack up. Portal at %s, PostgreSQL on %d. Tear it "
                "down with: docker compose -p %s -f %s/docker-compose.yml down -v"
                % (stack.base_url, options.postgres_port, options.project, DIRECTORY)
            )
        else:
            stack.down(volumes=True)

    return 1 if results.failure_count else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
