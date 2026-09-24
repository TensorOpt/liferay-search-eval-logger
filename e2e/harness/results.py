# SPDX-License-Identifier: Apache-2.0
"""Test results, in a form a CI system reads and a person reads.

JUnit XML because every CI system already understands it, and a plain text
report alongside it because a developer reading a terminal should not have to
open XML to find out what broke.

**Known defects are a mechanism here, not a paragraph in a README.** A case
named in `expected_failures` that fails is recorded as an expected failure: it
does not count toward the exit code, so the known state of the plugin exits 0
and a gate can be switched on today. The same case *passing* is recorded as a
real failure, because a defect that quietly went away has to be noticed and the
entry retired.

The reason that has to be a mechanism rather than a rule a human applies is
that "expect exactly two failures" is not a rule about the thing it sounds
like. Kill the database halfway through a run and the two known failures become
skips, because their prerequisites never passed; the run then shows zero
failures and reads as better than the baseline while actually being broken.
Naming the cases removes that whole class of misreading.
"""

import os
import re
import time
import traceback
import xml.etree.ElementTree as ElementTree

from .util import Stopwatch, log

_REPORT_FAILURE_LINES = 24

# XML 1.0 allows tab, newline and carriage return, and nothing else below 0x20.
# Container logs and psql output reach these documents through failure
# messages, and one stray byte from either would make junit.xml unparseable,
# which loses every result in the run rather than the one that produced it.
_ILLEGAL_XML = re.compile(
    "[^"
    "\\u0009\\u000a\\u000d\\u0020-\\ud7ff\\ue000-\\ufffd"
    "\\U00010000-\\U0010ffff"
    "]"
)

PASSED = "passed"

FAILED = "failed"

SKIPPED = "skipped"

EXPECTED_FAILURE = "expected failure"

UNEXPECTED_PASS = "unexpected pass"


def xml_safe(text):
    """Replaces characters XML cannot carry with an escaped form."""
    if text is None:
        return None

    return _ILLEGAL_XML.sub(
        lambda match: "\\x%02x" % ord(match.group()), str(text)
    )


class Case:
    def __init__(self, name, classname):
        self.name = name
        self.classname = classname
        self.seconds = 0.0
        self.status = PASSED
        self.failure = None
        self.skipped = None
        self.known_defect = None
        self.stdout = []

    def note(self, message):
        self.stdout.append(str(message))
        log("        %s" % message)


class Results:
    """Collects cases and writes them out.

    A step that raises records a failure and the run carries on wherever the
    remaining steps still mean something. A step that cannot mean anything
    after an earlier failure declares that step as a prerequisite and is
    recorded as skipped rather than as a second, derived failure: one broken
    thing should produce one red line, not ten.
    """

    def __init__(self, suite_name="search-eval-logger-e2e", expected_failures=None):
        self.suite_name = suite_name
        self.expected_failures = dict(expected_failures or {})
        self.cases = []
        self._passed = set()

    def run(self, name, function, requires=(), classname="e2e", skip=None):
        """Runs one step. Returns the step's return value, or None.

        `skip` states a reason the step does not apply to this run at all, as
        distinct from `requires`, which is a step that should have run and did
        not.
        """
        case = Case(name, classname)

        known_defect = self.expected_failures.get(name)

        missing = [required for required in requires if required not in self._passed]

        if skip or missing:
            case.status = SKIPPED
            case.skipped = skip or "requires %s" % ", ".join(missing)
            log("SKIP %s (%s)" % (name, case.skipped))
            self._record(case)

            return None

        log("RUN  %s" % name)

        stopwatch = Stopwatch()
        value = None

        try:
            value = function(case)
        except Exception:  # noqa: BLE001 - every failure is a test result
            case.seconds = stopwatch.seconds()
            case.failure = traceback.format_exc()

            if known_defect:
                case.status = EXPECTED_FAILURE
                case.known_defect = known_defect
                log("XFAIL %s (%.2fs) %s" % (name, case.seconds, known_defect))
            else:
                case.status = FAILED
                log("FAIL %s (%.2fs)" % (name, case.seconds))

            self._record(case)

            return None

        case.seconds = stopwatch.seconds()

        if known_defect:

            # A case named as a known defect that passes is news, and the kind
            # of news that goes stale silently if nobody is told. Either the
            # defect was fixed and this entry should be retired, or the case
            # stopped testing what it used to.

            case.status = UNEXPECTED_PASS
            case.known_defect = known_defect
            case.failure = (
                "This case is registered as a known defect (%s) and it passed. "
                "Either the defect was fixed, in which case remove it from "
                "EXPECTED_FAILURES, or the case no longer tests what it "
                "claims to." % known_defect
            )
            log("XPASS %s (%.2fs) %s" % (name, case.seconds, known_defect))
            self._record(case)

            return value

        case.status = PASSED
        log("PASS %s (%.2fs)" % (name, case.seconds))
        self._record(case)

        return value

    def passed(self, name):
        return name in self._passed

    def _count(self, status):
        return len([case for case in self.cases if case.status == status])

    @property
    def failure_count(self):
        """What the exit code is built from.

        An expected failure is deliberately not in here. An unexpected pass
        deliberately is.
        """
        return self._count(FAILED) + self._count(UNEXPECTED_PASS)

    @property
    def skipped_count(self):
        return self._count(SKIPPED)

    @property
    def expected_failure_count(self):
        return self._count(EXPECTED_FAILURE)

    @property
    def unexpected_pass_count(self):
        return self._count(UNEXPECTED_PASS)

    def summary(self):
        return (
            "%d cases, %d passed, %d failed, %d skipped, %d expected failures, "
            "%d unexpected passes"
            % (
                len(self.cases),
                self._count(PASSED),
                self._count(FAILED),
                self._count(SKIPPED),
                self._count(EXPECTED_FAILURE),
                self._count(UNEXPECTED_PASS),
            )
        )

    def write_junit(self, path):
        testsuite = ElementTree.Element(
            "testsuite",
            {
                "name": self.suite_name,
                "tests": str(len(self.cases)),
                "failures": str(self.failure_count),
                "errors": "0",
                "skipped": str(self.skipped_count + self.expected_failure_count),
                "time": "%.3f" % sum(case.seconds for case in self.cases),
                "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime()),
            },
        )

        for case in self.cases:
            element = ElementTree.SubElement(
                testsuite,
                "testcase",
                {
                    "classname": case.classname,
                    "name": case.name,
                    "time": "%.3f" % case.seconds,
                },
            )

            system_out = list(case.stdout)

            if case.status in (FAILED, UNEXPECTED_PASS):
                failure = ElementTree.SubElement(
                    element,
                    "failure",
                    {"message": xml_safe(_headline(case.failure))},
                )
                failure.text = xml_safe(case.failure)
            elif case.status == EXPECTED_FAILURE:

                # Reported as skipped so the suite is green on the known state,
                # with the whole traceback kept in system-out rather than
                # thrown away: an expected failure that starts failing for a
                # new reason should still be readable.

                ElementTree.SubElement(
                    element,
                    "skipped",
                    {"message": xml_safe("known defect: %s" % case.known_defect)},
                )
                system_out.append("known defect: %s" % case.known_defect)
                system_out.append(case.failure or "")
            elif case.status == SKIPPED:
                ElementTree.SubElement(
                    element, "skipped", {"message": xml_safe(case.skipped)}
                )

            if system_out:
                ElementTree.SubElement(element, "system-out").text = xml_safe(
                    "\n".join(system_out)
                )

        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)

        ElementTree.ElementTree(testsuite).write(
            path, encoding="utf-8", xml_declaration=True
        )

    def write_text(self, path):
        labels = {
            PASSED: "PASS",
            FAILED: "FAIL",
            SKIPPED: "SKIP",
            EXPECTED_FAILURE: "XFAIL",
            UNEXPECTED_PASS: "XPASS",
        }

        lines = []

        for case in self.cases:
            lines.append(
                "%-5s %-46s %8.2fs%s"
                % (
                    labels[case.status],
                    case.name,
                    case.seconds,
                    "  (%s)" % case.known_defect if case.known_defect else "",
                )
            )

            for note in case.stdout:
                lines.append("          %s" % note)

            if case.status in (FAILED, UNEXPECTED_PASS):
                for failure_line in _trim(case.failure):
                    lines.append("      !   %s" % failure_line)

            if case.status == EXPECTED_FAILURE:
                for failure_line in _trim(case.failure)[-6:]:
                    lines.append("      x   %s" % failure_line)

            if case.status == SKIPPED:
                lines.append("      -   %s" % case.skipped)

        lines.append("")
        lines.append(self.summary())

        text = "\n".join(lines) + "\n"

        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)

        with open(path, "w", encoding="utf-8") as file_:
            file_.write(text)

        return text

    def _record(self, case):
        self.cases.append(case)

        if case.status == PASSED:
            self._passed.add(case.name)


def _headline(failure):
    if not failure:
        return "failed"

    return failure.strip().splitlines()[-1][:400]


def _trim(failure):
    lines = (failure or "").strip().splitlines()

    if len(lines) <= _REPORT_FAILURE_LINES:
        return lines

    return (
        lines[:6]
        + [
            "... %d lines omitted, see junit.xml"
            % (len(lines) - _REPORT_FAILURE_LINES)
        ]
        + lines[-(_REPORT_FAILURE_LINES - 6):]
    )
