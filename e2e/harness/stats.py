# SPDX-License-Identifier: Apache-2.0
"""Pure statistics, shared between the search-overhead e2e case (DESIGN.md
7, D8) and scripts/loadtest.py (D8), so both read a timing sample the same
way.

Nothing here touches the network, the portal or the filesystem: every
function takes a plain list or two of numbers and returns a plain number,
which is what makes this importable from e2e/selftest.py with no running
stack and no portal to talk to.
"""

import random
import statistics

# median is the standard library's own (already used elsewhere in this
# harness, for the queue-overflow case's latencies); re-exported here so
# every timing measurement in this project, the e2e case and
# scripts/loadtest.py alike, reads it from one place rather than some
# callers importing the stdlib module directly and others importing this
# one.
median = statistics.median


def percentile(values, fraction):
    """The value at `fraction` (0 to 1) through a sorted copy, by linear
    interpolation between the two bracketing samples, the same method
    numpy.percentile defaults to: percentile(values, 0.9) is p90,
    percentile(values, 0.025) is the low end of a 95% interval. Standard
    library's statistics module gained quantiles() only in 3.8 and it does
    not interpolate the same way, so this is its own small function rather
    than two slightly different ones depending on which caller needs which
    exact tail.
    """
    ordered = sorted(values)
    count = len(ordered)

    if count == 1:
        return ordered[0]

    position = fraction * (count - 1)
    lower = int(position)
    upper = min(lower + 1, count - 1)
    fractional = position - lower

    return ordered[lower] + (ordered[upper] - ordered[lower]) * fractional


def ratio(enabled_median, disabled_median):
    """enabled's median over disabled's median: 1.10 means enabled took 10%
    longer. The one number both the e2e case's 10% gate and loadtest.py's
    confidence interval are built from, so both read an overhead the same
    way."""
    return enabled_median / disabled_median


def bootstrap_ratio_ci(enabled_samples, disabled_samples, resamples=2000, seed=None):
    """A 95% confidence interval for ratio(median(enabled), median(disabled)),
    by the plain percentile bootstrap: resample each side with replacement,
    recompute the ratio of medians, and take the 2.5th and 97.5th percentile
    of the resampled ratios. Standard library only (random.choices), which
    is what scripts/loadtest.py needs it for; a more elaborate bootstrap
    (bias-corrected, studentized) would need a dependency this project has
    no other reason to take on for one script.

    Returns (low, high). seed makes a run reproducible; left None, the
    interval varies slightly run to run the way any bootstrap does.
    """
    rng = random.Random(seed)

    ratios = []

    for _ in range(resamples):
        enabled_resample = rng.choices(enabled_samples, k=len(enabled_samples))
        disabled_resample = rng.choices(disabled_samples, k=len(disabled_samples))

        ratios.append(
            ratio(median(enabled_resample), median(disabled_resample))
        )

    return percentile(ratios, 0.025), percentile(ratios, 0.975)
