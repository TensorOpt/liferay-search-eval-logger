#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Thin wrapper over run_e2e.py, so a CI job has one command to call and a
# developer does not have to remember which python is on the box.
#
# Everything real is in run_e2e.py, including teardown, which runs in a finally
# block so it happens on failure and on an interrupt as well as on success.

set -euo pipefail

directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v python3 > /dev/null 2>&1; then
	echo "python3 is required and is not on PATH" >&2

	exit 2
fi

exec python3 "${directory}/run_e2e.py" "$@"
