# SPDX-License-Identifier: Apache-2.0
"""Pure statistics, shared between the search-overhead e2e case (DESIGN.md
7, D8) and scripts/loadtest.py (D8), so both read a timing sample the same
way.

Nothing here touches the network, the portal or the filesystem: every
function takes a plain list or two of numbers and returns a plain number,
which is what makes this importable from e2e/selftest.py with no running
stack and no portal to talk to.
"""

import statistics

# median is the standard library's own (already used elsewhere in this
# harness, for the queue-overflow case's latencies); re-exported here so
# every timing measurement in this project, the e2e case and
# scripts/loadtest.py alike, reads it from one place rather than some
# callers importing the stdlib module directly and others importing this
# one.
median = statistics.median


def ratio(enabled_median, disabled_median):
    """enabled's median over disabled's median: 1.10 means enabled took 10%
    longer. The one number both the e2e case's 10% gate and loadtest.py's
    confidence interval are built from, so both read an overhead the same
    way."""
    return enabled_median / disabled_median
