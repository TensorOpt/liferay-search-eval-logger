# SPDX-License-Identifier: Apache-2.0
"""Bulk generation of search events and hits.

Real searches cannot produce a million events, so the scale tier is written
straight into the two tables. Three properties of the generated rows are what
make the later assertions mean anything.

Hits carry their event's own companyId and createDate, taken from the event row
rather than recomputed, because DESIGN.md 3.4 has the purge delete events and
hits as two independent statements and a drifting date on either side would
make the purge look correct while leaving orphans.

The createDates straddle the retention cutoff on purpose. A purge run against
rows that are all inside the window deletes nothing and passes; the interesting
assertion is that it deletes exactly the rows outside the window and leaves the
rest.

Title and snippet coverage are set to fixed ratios, so the coverage rates the
export manifest reports can be checked against a number this file decided
rather than against itself.
"""

from .util import Stopwatch, log

TITLE_COVERAGE = 0.9

SNIPPET_COVERAGE = 0.1

# Well above anything Liferay's counter will hand out on a fresh instance, so
# generated rows cannot collide with rows written by a real search. The Counter
# rows are moved up afterwards as well, for an instance that is kept running.
EVENT_ID_BASE = 900000000000000

HIT_ID_BASE = 900000000000000


class Generator:
    def __init__(self, stack, company_id, group_id):
        self.stack = stack
        self.company_id = company_id
        self.group_id = group_id

    def generate(self, events, hits_per_event, days_span, chunk=100000, note=log):
        """Writes `events` events, each with `hits_per_event` hits.

        Returns a dict of timings and counts.
        """
        stopwatch = Stopwatch()

        written_events = 0

        for low in range(0, events, chunk):
            high = min(low + chunk, events)

            self.stack.psql(
                _EVENTS_SQL
                % {
                    "company_id": self.company_id,
                    "group_id": self.group_id,
                    "event_id_base": EVENT_ID_BASE,
                    "low": low + 1,
                    "high": high,
                    "days_span": days_span,
                    "hits_per_event": hits_per_event,
                },
                timeout=7200,
            )

            self.stack.psql(
                _HITS_SQL
                % {
                    "event_id_base": EVENT_ID_BASE,
                    "hit_id_base": HIT_ID_BASE,
                    "hits_per_event": hits_per_event,
                    "low": low + 1,
                    "high": high,
                    "title_modulus": int(round(1 / (1 - TITLE_COVERAGE))),
                    "snippet_modulus": int(round(1 / SNIPPET_COVERAGE)),
                },
                timeout=7200,
            )

            written_events = high

            note(
                "generated %d/%d events (%.0fs elapsed)"
                % (written_events, events, stopwatch.seconds())
            )

        self.stack.psql(
            _COUNTER_SQL
            % {
                "event_ceiling": EVENT_ID_BASE + events + 1,
                "hit_ceiling": HIT_ID_BASE + events * hits_per_event + 1,
            }
        )

        return {
            "events": events,
            "hits": events * hits_per_event,
            "seconds": stopwatch.seconds(),
        }

    def analyze(self):
        """Keeps the purge's plan honest on a table that just grew by millions."""
        stopwatch = Stopwatch()

        self.stack.psql(
            "analyze SEL_SearchEvent; analyze SEL_SearchHit;", timeout=3600
        )

        return stopwatch.seconds()


# One row per event. createDate walks backwards from now in whole seconds, so a
# run is reproducible to the day and the rows spread evenly across the span
# rather than clustering.
_EVENTS_SQL = """
insert into SEL_SearchEvent (
    mvccVersion, uuid_, searchEventId, companyId, createDate, queryText,
    queryTruncated, locale, scopeGroupIds, entryClassNames, appliedFacets,
    facetCaptureStatus, blueprintId, audienceType, cohortHash, requestedSize,
    requestedFrom, totalHits, loggedHitCount, sourceType)
select
    0,
    'e2e-' || lpad(g::text, 16, '0'),
    %(event_id_base)d + g,
    %(company_id)d,
    now() at time zone 'UTC'
        - ((g %% %(days_span)d) || ' days')::interval
        - (((g * 37) %% 86400) || ' seconds')::interval,
    'generated query ' || (g %% 5000),
    false,
    'en_US',
    '%(group_id)d',
    'com.liferay.journal.model.JournalArticle',
    case when g %% 3 = 0
        then '{"category":["Generated ' || (g %% 7) || '"]}'
        else null
    end,
    case when g %% 3 = 0 then 'CAPTURED' else 'NONE_APPLIED' end,
    null,
    case when g %% 4 = 0 then 'GUEST' else 'AUTHENTICATED' end,
    md5('cohort' || (g %% 97)),
    20,
    0,
    100 + (g %% 900),
    %(hits_per_event)d,
    'WIDGET'
from generate_series(%(low)d, %(high)d) as g;
"""

# Hits are joined to the events just written rather than recomputed, so uuid,
# companyId and createDate are the event's own by construction.
_HITS_SQL = """
insert into SEL_SearchHit (
    mvccVersion, searchHitId, searchEventUuid, companyId, createDate, rank_,
    score, docUid, entryClassName, entryClassPK, title, snippet, extraFields)
select
    0,
    %(hit_id_base)d +
        ((e.searchEventId - %(event_id_base)d - 1) * %(hits_per_event)d) + r,
    e.uuid_,
    e.companyId,
    e.createDate,
    r - 1,
    10.0 - (r * 0.1),
    'com.liferay.journal.model.JournalArticle_PORTLET_' ||
        ((e.searchEventId - %(event_id_base)d) * 100 + r),
    'com.liferay.journal.model.JournalArticle',
    (e.searchEventId - %(event_id_base)d) * 100 + r,
    case
        when (((e.searchEventId - %(event_id_base)d) * %(hits_per_event)d + r)
            %% %(title_modulus)d) <> 0
        then 'Generated title ' ||
            (e.searchEventId - %(event_id_base)d) || '-' || r
        else null
    end,
    case
        when (((e.searchEventId - %(event_id_base)d) * %(hits_per_event)d + r)
            %% %(snippet_modulus)d) = 0
        then 'a fragment mentioning <liferay-hl>generated</liferay-hl> text'
        else null
    end,
    null
from SEL_SearchEvent e, generate_series(1, %(hits_per_event)d) as r
where e.searchEventId > %(event_id_base)d + %(low)d - 1
  and e.searchEventId <= %(event_id_base)d + %(high)d;
"""

# Service Builder allocates primary keys from the Counter table. Generated rows
# sit far above anything allocated so far, so the counters are moved past them:
# without this an instance kept alive after a run would eventually hand out a
# key that already exists.
_COUNTER_SQL = """
insert into Counter (name, currentId)
    values ('ai.tensoropt.sel.model.SearchEvent', %(event_ceiling)d)
    on conflict (name) do update
    set currentId = greatest(Counter.currentId, excluded.currentId);

insert into Counter (name, currentId)
    values ('ai.tensoropt.sel.model.SearchHit', %(hit_ceiling)d)
    on conflict (name) do update
    set currentId = greatest(Counter.currentId, excluded.currentId);
"""
