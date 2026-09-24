# SPDX-License-Identifier: Apache-2.0
"""Docker Compose stack control: bring up, restart, tear down, read logs, run SQL.

The stack is addressed by an explicit project name everywhere, so it can never
collide with a developer's own containers and so that teardown removes exactly
what this run created.
"""

import json
import math
import os
import shutil
import subprocess
import time

from .util import HarnessError, log, run, wait_for


class Stack:
    def __init__(self, directory, project, http_port, postgres_port, image=None):
        self.directory = directory
        self.project = project
        self.http_port = http_port
        self.postgres_port = postgres_port
        self.image = image
        self.base_url = "http://localhost:%d" % http_port
        self.liferay_container = "%s-liferay" % project
        self.postgres_container = "%s-postgres" % project

    # Compose

    def _compose(self, *args, check=True, timeout=1800, capture=True):
        command = [
            "docker",
            "compose",
            "-p",
            self.project,
            "-f",
            os.path.join(self.directory, "docker-compose.yml"),
        ] + list(args)

        environment = dict(os.environ)
        environment["SEL_E2E_PROJECT"] = self.project
        environment["SEL_E2E_HTTP_PORT"] = str(self.http_port)
        environment["SEL_E2E_POSTGRES_PORT"] = str(self.postgres_port)

        return run(
            command, check=check, timeout=timeout, capture=capture, env=environment
        )

    def preflight(self):
        """Fails early and specifically, rather than halfway through a run."""
        for tool in ("docker",):
            if shutil.which(tool) is None:
                raise HarnessError("%s is not on PATH" % tool)

        code, _, stderr = run(["docker", "info"], check=False, timeout=60)

        if code != 0:
            raise HarnessError("Docker is not usable: %s" % stderr.strip()[:500])

        code, _, _ = run(
            ["docker", "compose", "version"], check=False, timeout=60
        )

        if code != 0:
            raise HarnessError("docker compose v2 is not available")

        for port in (self.http_port, self.postgres_port):
            owner, project = self._port_owner(port)

            if owner is None:
                continue

            # Ownership is decided by Compose's own project label, not by the
            # container's name. A name prefix comparison reads
            # "liferay-postgres" as ours whenever the project is called
            # "liferay", which is exactly the container this harness must never
            # touch.

            if project != self.project:
                raise HarnessError(
                    "Host port %d is already published by container %r "
                    "(compose project %r, this run is %r). Set "
                    "SEL_E2E_HTTP_PORT or SEL_E2E_POSTGRES_PORT to move out "
                    "of its way; this harness will not touch a container it "
                    "did not create."
                    % (port, owner, project or "none", self.project)
                )

    def _port_owner(self, port):
        """Returns (container name, compose project) publishing `port`."""
        _, stdout, _ = run(
            [
                "docker",
                "ps",
                "--format",
                '{{.Names}}\t{{.Label "com.docker.compose.project"}}\t{{.Ports}}',
            ],
            timeout=60,
        )

        for line in stdout.splitlines():
            parts = line.split("\t")

            if len(parts) < 3:
                continue

            name, project, ports = parts[0], parts[1], parts[2]

            if ":%d->" % port in ports:
                return name, project or None

        return None, None

    def down(self, volumes=True):
        log("Tearing down stack %s%s" % (self.project, " with volumes" if volumes else ""))

        args = ["down", "--remove-orphans"]

        if volumes:
            args.append("--volumes")

        code, _, stderr = self._compose(*args, check=False, timeout=600)

        if code != 0:
            log(
                "Teardown of %s exited %d: %s"
                % (self.project, code, stderr.strip()[:300])
            )

    def up(self):
        """Creates and starts the containers, and does not wait for health.

        Readiness is established by the probes in Portal.wait_until_serving,
        which ask the portal a question rather than trust a health check, so
        "--wait" here would only add a second timeout with a worse failure
        message. A non zero exit is a real failure and is raised with compose's
        own stderr attached.
        """
        # Pulled as its own step, with docker's progress going straight to the
        # console. A first run fetches about 2.4 GB, and without this the run
        # sits silent for minutes with nothing to distinguish a slow pull from
        # a hung one.

        log("Pulling images for %s (about 2.4 GB on a cold cache)" % self.project)

        self._compose("pull", capture=False, check=False, timeout=3600)

        log("Starting stack %s" % self.project)

        self._compose("up", "-d", timeout=1800)

    def restart_liferay(self):
        log("Restarting Liferay")

        self._compose("restart", "liferay", timeout=600)

    def container_running(self, container):
        code, stdout, _ = run(
            ["docker", "inspect", "-f", "{{.State.Running}}", container],
            check=False,
            timeout=60,
        )

        return code == 0 and stdout.strip() == "true"

    # Logs

    def liferay_log(self, since=None):
        args = ["docker", "logs", self.liferay_container]

        if since:
            args += ["--since", since]

        _, stdout, stderr = run(args, check=False, timeout=300)

        return stdout + stderr

    def save_logs(self, directory):
        os.makedirs(directory, exist_ok=True)

        for container in (self.liferay_container, self.postgres_container):
            _, stdout, stderr = run(
                ["docker", "logs", container], check=False, timeout=300
            )

            with open(
                os.path.join(directory, "%s.log" % container), "w", encoding="utf-8"
            ) as file_:
                file_.write(stdout)
                file_.write(stderr)

        _, stdout, _ = self._compose("ps", "--format", "json", check=False, timeout=120)

        with open(
            os.path.join(directory, "compose-ps.json"), "w", encoding="utf-8"
        ) as file_:
            file_.write(stdout)

    # Exec

    def liferay_exec(self, *args, check=True, timeout=600):
        return run(
            ["docker", "exec", self.liferay_container] + list(args),
            check=check,
            timeout=timeout,
        )

    def read_container_file(self, path):
        code, stdout, _ = run(
            ["docker", "exec", self.liferay_container, "cat", path],
            check=False,
            timeout=120,
        )

        if code != 0:
            return None

        return stdout

    def copy_to_liferay(self, host_path, container_path):
        run(
            ["docker", "cp", host_path, "%s:%s" % (self.liferay_container, container_path)],
            timeout=600,
        )

    # SQL

    def psql(self, sql, timeout=3600):
        """Runs SQL and returns rows as lists of strings."""
        _, stdout, _ = run(
            [
                "docker",
                "exec",
                "-i",
                self.postgres_container,
                "psql",
                "-v",
                "ON_ERROR_STOP=1",
                "-q",
                "-t",
                "-A",
                "-F",
                "\t",
                "-U",
                "lportal",
                "-d",
                "lportal",
            ],
            input_=sql,
            timeout=timeout,
        )

        rows = []

        for line in stdout.splitlines():
            if line.strip() == "":
                continue

            rows.append(line.split("\t"))

        return rows

    def psql_scalar(self, sql, timeout=3600):
        rows = self.psql(sql, timeout=timeout)

        if not rows:
            raise HarnessError("Query returned no rows: %s" % sql[:200])

        return rows[0][0]

    def psql_long(self, sql, timeout=3600):
        return int(self.psql_scalar(sql, timeout=timeout))

    # Readiness

    def wait_for_postgres(self, timeout=180):
        def ready():
            code, _, _ = run(
                [
                    "docker",
                    "exec",
                    self.postgres_container,
                    "pg_isready",
                    "-U",
                    "lportal",
                    "-d",
                    "lportal",
                ],
                check=False,
                timeout=60,
            )

            return code == 0

        wait_for("PostgreSQL to accept connections", ready, timeout=timeout)

    def wait_for_bundle_started(self, symbolic_name, since, timeout=600):
        """Waits for Liferay to report a bundle as started, now rather than ever.

        The portal log is the signal because it is the one place that reports
        the module framework's own view. An OSGi state read over HTTP would
        need the plugin to be working already, which is the thing being waited
        for.

        Only lines written after this call began count. Searching the whole log
        looks for a substring of everything ever logged rather than for an
        event, and is satisfied by history: a restart inside the same run
        leaves the previous start's STARTED lines in place, so the wait would
        return immediately and the install it was supposed to be waiting on
        would never be observed at all.

        The window is expressed as a duration rather than a timestamp, so it
        does not depend on the host and the daemon agreeing about the clock.

        It opens at <since>, a time.monotonic() value the caller takes before
        deploying, not when this call begins. Several bundles are waited for
        one after another, and they start within milliseconds of each other:
        a window opened by each wait misses every bundle that started while
        an earlier wait was still polling, and then times out (TO-93).
        """

        def started():
            window = int(math.ceil(time.monotonic() - since)) + 1

            log_text = self.liferay_log(since="%ds" % window)

            return ("STARTED %s_" % symbolic_name) in log_text

        wait_for(
            "bundle %s to start" % symbolic_name,
            started,
            timeout=timeout,
            interval=3.0,
        )

    def lock_table(self, table):
        """Holds an ACCESS EXCLUSIVE lock on a table until the lock is closed.

        The lock is taken in its own psql session, which then sleeps inside
        the transaction; closing terminates that session, which releases it.
        Use as a context manager so a failing case cannot leave it held.
        """
        return _TableLock(self, table)

    def background_task_count(self, executor_class_name):
        """How many background tasks exist for one executor, from the database.

        Counted here rather than from the admin screen because that screen
        renders the twenty most recent runs. A guard of the form "one more row
        than before" silently stops working on the twenty first export, and
        would then let a case validate the previous run's archive.
        """
        return self.psql_long(
            "select count(*) from backgroundtask "
            "where taskexecutorclassname = '%s'" % executor_class_name,
            timeout=300,
        )

    def compose_ps(self):
        _, stdout, _ = self._compose("ps", "--format", "json", check=False, timeout=120)

        entries = []

        for line in stdout.splitlines():
            line = line.strip()

            if not line:
                continue

            try:
                parsed = json.loads(line)
            except ValueError:
                continue

            if isinstance(parsed, list):
                entries.extend(parsed)
            else:
                entries.append(parsed)

        return entries


class _TableLock:

    _TAG = "e2e-table-lock"

    def __init__(self, stack, table):
        self._stack = stack
        self._table = table
        self._process = None

    def __enter__(self):
        self._process = subprocess.Popen(
            [
                "docker", "exec", "-i", self._stack.postgres_container, "psql",
                "-U", "lportal", "-d", "lportal",
            ],
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            text=True,
        )

        self._process.stdin.write(
            "begin; lock table %s in access exclusive mode; "
            "select '%s', pg_sleep(3600);\n" % (self._table, self._TAG)
        )
        self._process.stdin.flush()

        def held():
            return self._stack.psql_long(
                "select count(*) from pg_locks l join pg_class c on "
                "c.oid = l.relation where c.relname = '%s' and "
                "l.mode = 'AccessExclusiveLock' and l.granted"
                % self._table.lower()
            ) > 0

        wait_for("the lock on %s" % self._table, held, timeout=60, interval=1.0)

        return self

    def __exit__(self, *exc_info):
        self._stack.psql(
            "select pg_terminate_backend(pid) from pg_stat_activity "
            "where query like '%%%s%%' and pid <> pg_backend_pid()" % self._TAG
        )

        self._process.stdin.close()
        self._process.wait(timeout=60)

        return False
