# SPDX-License-Identifier: Apache-2.0
"""Process control, logging and bounded waiting.

Every wait in this harness goes through wait_for, and every wait_for has a
deadline and a message. A CI job that hangs is worse than one that fails: the
first burns a runner until somebody notices, the second tells you what went
wrong.
"""

import subprocess
import sys
import time


class HarnessError(Exception):
    """A failure in the harness itself or in the thing it is testing."""


class TimeoutError_(HarnessError):
    """A bounded wait ran out."""


_START = time.monotonic()


def log(message):
    elapsed = time.monotonic() - _START
    sys.stdout.write("[%7.1fs] %s\n" % (elapsed, message))
    sys.stdout.flush()


def run(args, check=True, capture=True, timeout=600, input_=None, env=None):
    """Runs a command and returns (returncode, stdout, stderr).

    Nothing here is run through a shell, so no argument needs quoting and no
    value taken from a test can be read as shell syntax.
    """
    completed = subprocess.run(
        args,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
        input=input_.encode("utf-8") if input_ is not None else None,
        timeout=timeout,
        env=env,
    )

    stdout = completed.stdout.decode("utf-8", "replace") if capture else ""
    stderr = completed.stderr.decode("utf-8", "replace") if capture else ""

    if check and completed.returncode != 0:
        raise HarnessError(
            "Command failed (%d): %s\nstdout:\n%s\nstderr:\n%s"
            % (completed.returncode, " ".join(args), stdout[-4000:], stderr[-4000:])
        )

    return completed.returncode, stdout, stderr


def wait_for(description, predicate, timeout, interval=2.0, stable=1):
    """Waits until predicate() returns a truthy value.

    stable is how many consecutive successes are required. Liferay answers on
    its HTTP port before it is finished starting, so a single success is not
    evidence that it is up; asking again a moment later is.

    Returns the last truthy value, so a predicate can double as the getter.
    """
    deadline = time.monotonic() + timeout
    successes = 0
    value = None
    last_error = None

    while time.monotonic() < deadline:
        try:
            value = predicate()
            last_error = None
        except Exception as exception:  # noqa: BLE001 - reported on timeout
            value = None
            last_error = exception

        if value:
            successes += 1

            if successes >= stable:
                return value
        else:
            successes = 0

        time.sleep(interval)

    raise TimeoutError_(
        "Timed out after %ds waiting for %s%s"
        % (timeout, description, "" if last_error is None else (": %r" % (last_error,)))
    )


class Stopwatch:
    """Measures a step so the report can carry real numbers."""

    def __init__(self):
        self.start = time.monotonic()

    def seconds(self):
        return time.monotonic() - self.start
