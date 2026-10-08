#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""A local portal with the plugin installed, for trying things by hand.

    tools/portal.py up [--database postgres] [--fresh]
    tools/portal.py down [--volumes]

It runs the e2e suite's own Compose files under a project of its own (sel-dev)
and its own ports, so it never touches an e2e run and survives one.

up deploys the bundles from modules/*/build/libs and restarts the portal once:
Liferay's search consumers bind the search service at startup, so a plugin
installed onto a running portal records nothing until a restart (DESIGN.md
3.1). It deploys the jars rather than the .lpkg because Liferay refuses an
.lpkg whose version is already installed, and a change under development
rarely bumps the version; make test covers the .lpkg.

Without --fresh the database and the portal's data are kept from the last run.
"""

import argparse
import os
import sys
import time

REPOSITORY = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
E2E = os.path.join(REPOSITORY, "e2e")

sys.path.insert(0, E2E)

from harness import checks, scripts  # noqa: E402
from harness.portal import Portal  # noqa: E402
from harness.stack import DATABASES, Stack  # noqa: E402
from harness.util import log  # noqa: E402
from run_e2e import MODULES, find_jars  # noqa: E402

PROJECT = "sel-dev"


def stack_for(options):
    # The Gogo shell port is the one port the e2e stack and this one would
    # otherwise share.
    os.environ.setdefault("SEL_E2E_GOGO_PORT", str(options.gogo_port))

    return Stack(
        E2E, PROJECT, options.http_port, options.database_port,
        database=options.database,
    )


def up(options):
    jars = find_jars()

    if len(jars) != len(MODULES):
        sys.exit("Expected %d bundles, found %s. Run make build first." % (len(MODULES), jars))

    stack = stack_for(options)
    portal = Portal(stack)

    stack.preflight()

    if options.fresh:
        log("Removing the previous %s stack and its data" % PROJECT)
        stack.down(volumes=True)

    stack.up()
    stack.wait_for_database(timeout=300)
    portal.wait_until_serving(timeout=900)

    deployed_at = time.monotonic()

    for jar in jars:
        stack.deploy(jar)

    for name in checks.BUNDLE_SYMBOLIC_NAMES:
        stack.wait_for_bundle_started(name, deployed_at, timeout=600)

    stack.restart_liferay()
    portal.wait_until_serving(timeout=900)

    intercepting = portal.run_script(scripts.INTERCEPTION_STATUS) == "true"

    print(
        """
Portal:   %s  (test@liferay.com / test)
Database: %s on %s:%d (lportal / lportal)
Plugin:   installed from modules/*/build/libs, interception %s
Stop it with make stop; make stop VOLUMES=1 also deletes its data.
""" % (
            stack.base_url, options.database, stack.host, options.database_port,
            "active" if intercepting else "NOT active, see the portal log",
        )
    )

    if not intercepting:
        sys.exit(1)


def down(options):
    stack_for(options).down(volumes=options.volumes)


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("command", choices=("up", "down"))
    parser.add_argument("--database", choices=DATABASES, default="postgres")
    parser.add_argument("--fresh", action="store_true", help="start from an empty database")
    parser.add_argument("--volumes", action="store_true", help="down: delete the data too")
    parser.add_argument("--http-port", type=int, default=8080)
    parser.add_argument("--database-port", type=int, default=15433)
    parser.add_argument("--gogo-port", type=int, default=11312)
    options = parser.parse_args(argv)

    {"up": up, "down": down}[options.command](options)


if __name__ == "__main__":
    main(sys.argv[1:])
