# SPDX-License-Identifier: Apache-2.0
"""A Python port of the two acceptance jq recipes for the zero-result panel
(DESIGN.md 6, TO-135):

    Z1 (rate, overall and by audience)::

        jq -s 'map(select(.requested_from == 0)) |
          def zero: map(select(.total_hits == 0)) | length;
          {searches: length, zero_result: zero, rate: (zero / length),
           by_audience: (group_by(.audience_type) |
             map({audience_type: .[0].audience_type, searches: length,
                  zero_result: zero, rate: (zero / length)}))}' events.jsonl

    Z2 (top 20 zero-result queries, normalized)::

        jq -s '[.[] | select(.requested_from == 0 and .total_hits == 0) |
          .query | ascii_downcase | gsub("^\\s+|\\s+$"; "")] |
          group_by(.) | map({query: .[0], searches: length}) |
          sort_by(-.searches) | .[:20]' events.jsonl

This exists to let the e2e run compare the plugin's own panel against the
same recipe the acceptance criteria name, without shelling out to jq (which
may not be installed wherever the suite runs) and without re-deriving the
recipe from scratch at every call site. It is a port, not a reimplementation
of the idea: every departure from the obvious Python spelling is here because
jq does something the obvious spelling would not reproduce, and is commented
at that line.
"""

import decimal
import re

# The Unicode White_Space property, as a character class: U+0009-U+000D,
# U+0020, U+0085, U+00A0, U+1680, U+2000-U+200A, U+2028, U+2029, U+202F,
# U+205F, U+3000. This is deliberately not Python's own \s or str.strip():
# both of those also strip U+001C-U+001F (the legacy FS/GS/RS/US separator
# controls), which are not in White_Space and which jq's \s (confirmed
# empirically, see normalize_query below) does not strip either. Matches
# ai.tensoropt.sel.web.internal.zeroresult.ZeroResultQueryNormalizer's
# Pattern.UNICODE_CHARACTER_CLASS \s exactly, character for character,
# verified against both the ten characters below and these four.
_WHITE_SPACE = (
    "\u0009-\u000d \u0085   -    "
    " 　"
)

_TRIM_PATTERN = re.compile(
    "^[%s]+|[%s]+$" % (_WHITE_SPACE, _WHITE_SPACE))


def normalize_query(query):
    """jq's ``ascii_downcase | gsub("^\\s+|\\s+$"; "")``.

    ``ascii_downcase`` folds only ASCII ``A``-``Z``; Python's ``str.lower()``
    case-folds the whole Unicode range and would merge queries the recipe
    keeps apart, so the fold is done by hand, one character at a time.

    ``gsub``'s ``\\s`` runs on Oniguruma, confirmed empirically (``jq`` is
    installed where this was written) to match the full Unicode
    ``White_Space`` property at both ends of a string, not only the ASCII
    set: it strips a tab, a non-breaking space (U+00A0) and an ideographic
    space (U+3000), among others. It was also confirmed, against
    U+001C-U+001F, **not** to strip those: they are not in White_Space.
    Python's own ``str.strip()`` and ``re``'s ``\\s`` both strip those four
    anyway (both follow ``str.isspace()``, a wider, non-standard set), so
    neither is a safe substitute here; ``_TRIM_PATTERN`` spells out the
    Unicode property's codepoints explicitly instead.
    """
    folded = "".join(
        chr(ord(character) + 32) if ("A" <= character <= "Z") else character
        for character in query
    )

    return _TRIM_PATTERN.sub("", folded)


def round_half_up(numerator, denominator, places=1):
    """One-decimal-place percentage, rounded half up.

    Matches
    ``ai.tensoropt.sel.web.internal.zeroresult.ZeroResultRate.formatPercent``
    exactly: both round half up from the raw integer counts through a decimal
    type, not through a binary float, and not through round-half-even (Java's
    default double formatting, and Python's own built-in ``round()``), so the
    two sides cannot disagree at a rounding boundary such as 25 of 2000
    (1.25%, which is 1.3 under half-up and 1.2 under half-even).
    """
    if denominator == 0:
        return "0.0"

    quotient = (decimal.Decimal(numerator) * 100) / decimal.Decimal(denominator)

    return str(
        quotient.quantize(
            decimal.Decimal("1." + ("0" * places)),
            rounding=decimal.ROUND_HALF_UP,
        )
    )


def compute_z1(events):
    """Port of the acceptance Z1 recipe.

    ``events`` is the list of event records decoded from ``events.jsonl``
    (``harness.checks._stream_events`` already does that decoding).
    """
    first_page = [event for event in events if event.get("requested_from") == 0]

    def summarize(subset):
        zero_result = sum(
            1 for event in subset if event.get("total_hits") == 0
        )

        return {
            "searches": len(subset),
            "zero_result": zero_result,

            # jq divides raw counts, never guarding the empty case: an empty
            # input list never reaches this recipe's `zero / length` at all,
            # because `group_by` and the top level `length` would both be 0
            # and nothing calls this division instance for an empty group.
            # Guarded here anyway, because unlike jq's pipeline, a caller can
            # pass an empty subset directly.
            "rate": (zero_result / len(subset)) if subset else 0.0,
        }

    overall = summarize(first_page)

    by_audience_type = {}

    for event in first_page:
        by_audience_type.setdefault(event.get("audience_type"), []).append(
            event
        )

    # group_by sorts its groups by key (jq orders by Unicode code point, and
    # Python's own string ordering already is code point order), so the list
    # is built in that order rather than first-seen order.

    overall["by_audience"] = [
        dict(summarize(subset), audience_type=audience_type)
        for audience_type, subset in sorted(
            by_audience_type.items(),
            key=lambda pair: (pair[0] is None, pair[0]),
        )
    ]

    return overall


def compute_z2(events, limit=20):
    """Port of the acceptance Z2 recipe: the top ``limit`` zero-result,
    first-page queries, normalized, counted and ordered exactly as
    ``group_by``/``sort_by`` order them: count descending, ties broken by
    ascending Unicode code point order of the normalized query (which is
    exactly how two Python strings already compare with ``<``)."""
    counts = {}

    for event in events:
        if (event.get("requested_from") != 0) or (event.get("total_hits") != 0):
            continue

        query = event.get("query")

        if query is None:
            continue

        normalized = normalize_query(query)

        counts[normalized] = counts.get(normalized, 0) + 1

    ordered = sorted(counts.items(), key=lambda pair: (-pair[1], pair[0]))

    return [
        {"query": query, "searches": searches}
        for query, searches in ordered[:limit]
    ]
