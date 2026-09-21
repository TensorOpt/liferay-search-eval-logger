# Liferay Search Eval Logger: Design Doc

**Status:** Draft, pre-implementation (rev 5, post-review)
**Date:** 2026-09-17
**Target:** Liferay DXP 7.4
**License:** Apache 2.0
**Distribution:** Public GitHub repository

---

## 1. Purpose and Scope

Liferay provides no persistent, queryable log of user search queries. Base DXP offers only DEBUG-level server logging and the Search Insights widget (both diagnostic, not analytical). Liferay Enterprise Search adds Blueprints, Semantic Search, Learning to Rank and Elasticsearch monitoring, but no search-term log. Liferay Analytics Cloud records search terms as aggregated frequency counts parsed from page-view URLs, with no link between a query and the result set that was returned.

Relevance evaluation requires the missing artifact: a structured `(query, result[])` interaction log.

This plugin produces that log. An administrator installs it on a production Liferay instance, enables collection, lets it run for a period, then exports a time-bounded dataset. The export is intended to be handed to an external party (for example, an independent evaluator) for relevance analysis.

**In scope**

- Passive, transparent capture of user-originated search queries and their returned result sets
- Asynchronous persistence with no measurable impact on search latency
- Manual, admin-triggered, time-ranged export
- Configurable capture depth, retention, and captured field list

**Out of scope for v1**

- Relevance judgments. This produces an interaction log, not a TREC-style QRel file. Grading happens downstream, from sampled human review or from click signals joined back to the log. The two are deliberately separate.
- Click-through capture. See Section 8.
- Any UI for analysis, dashboards, or reporting inside Liferay.

---

## 2. Settled Design Decisions

These are the fixed constraints. Everything below must satisfy them.

| # | Decision | Rationale |
|---|---|---|
| D1 | Hook only `com.liferay.portal.search.*` (public API). Never connector internals or `portal-impl`. | Same contract in base DXP and LES, across Elasticsearch/OpenSearch connectors and versions. No branch between subscription tiers. |
| D2 | Never modify the outgoing `SearchRequest`. | A logger that changes search behavior is not a passive observer. Forcing highlighting would add per-query cost to live traffic and could override a caller's own highlight configuration. |
| D3 | No raw user identifiers persisted. | Reduces the data-protection surface so an admin can approve installation without a legal review cycle. |
| D4 | Capture depth is `min(K, hits returned)`. Never re-query to deepen. | The response is bounded by the caller's requested page size. Re-querying to reach K would double search engine load, reintroducing the capacity objection D2 exists to avoid. |
| D5 | Export is a manual admin action, never an automatic callback. | Nothing leaves the customer's infrastructure without an explicit human action. |
| D6 | No query-text redaction in v1. | Deferred deliberately (see Section 8). Target workloads are document, web content and knowledge-base search, where queries are predominantly non-PII. Revisit on first concrete customer objection. |
| D7 | Capture is allowlist-based: only user-originated searches are logged. | Liferay uses the search engine heavily for internal work. A denylist would leak administrative traffic into the dataset. See 3.2. |
| D8 | Whatever is not already in the `SearchResponse` is not captured. | Direct consequence of D2. The plugin reads what the caller asked for and nothing else. |

---

## 3. Architecture

### 3.1 Interception point

A single OSGi component wraps the `Searcher` service using a higher `service.ranking`, Liferay's supported mechanism for transparently overriding a registered service. All search entry points that resolve `Searcher` through the OSGi registry (search widgets, headless REST, custom portlets) are then covered by one hook.

```java
@Component(
    property = "service.ranking:Integer=100",
    service = Searcher.class
)
public class LoggingSearcher implements Searcher {

    @Reference(target = "(!(service.ranking=100))")
    private Searcher _delegate;

    @Override
    public SearchResponse search(SearchRequest searchRequest) {
        SearchResponse searchResponse = _delegate.search(searchRequest);

        // Never throws into the caller. Never blocks.
        _dispatcher.dispatch(searchRequest, searchResponse);

        return searchResponse;
    }
}
```

The reference filter must be tuned so the ranked component does not bind to itself. Exact filter semantics are an empirical check (EC-3).

**A higher ranking only wins at bind time.** Liferay's search consumers declare a plain `@Reference` to `Searcher`, which is static and reluctant, so a consumer that has already bound the portal's own searcher does not rebind when this wrapper appears. Installing the plugin onto a running portal leaves it registered, healthy, and never called: collection records nothing at all, with no error anywhere, until the portal restarts and those consumers bind again. **The portal must be restarted once after installation.** That is a property of how Liferay binds the service, not something the plugin can work around from inside its own bundle: there is no response-side extension point in `com.liferay.portal.search.spi.searcher` to switch to (only `SearchRequestContributor`, which is request-only and would violate D2), and the alternative — having this plugin restart Liferay's own search bundles to force a rebind — trades a documented one-time step for exactly the kind of invasive behavior Section 9 says must not survive a security review.

Because the failure is invisible, it is detected rather than left to be discovered as an empty table. The plugin asks the OSGi registry whether any bundle other than its own is still using a `Searcher` that is not the wrapper; if so, interception is being bypassed. That state is logged as a warning on activation and shown in the admin screen.

**Failure isolation is non-negotiable.** Every path from `dispatch(...)` inward is wrapped so that no logging failure (full queue, DB unavailable, serialization error, missing context) can propagate into the search response. A broken logger must degrade to logging nothing, never to a broken search page.

### 3.2 Admission filter (allowlist)

This runs first, synchronously, inside the wrapper, and must be cheap. It exists because wrapping `Searcher` catches far more than user search: Asset Publisher dynamic collections, Control Panel entity listings, workflow lookups, DDM resolution and various background checks all issue searches through the same service. Logging them would bury genuine user queries under administrative noise and inflate the tables by orders of magnitude.

A request is admitted only if **all** of the following hold:

1. Non-empty user keywords are present on the request's `SearchContext`. Internal callers overwhelmingly issue structured queries with no keywords, or use `emptySearchEnabled`. This single condition removes most internal traffic, and is deliberately the filter's strongest discriminator.
2. The request is not classified as suggestion traffic (see 5.1), when suggestion exclusion is enabled.
3. The requested entry class names are not in the configured exclusion list.
4. The sampling draw passes, when sampling is below 1.0.

Everything else is dropped before dispatch, with no allocation beyond the check itself. The realistic share of internal traffic that survives condition 1 is unknown and must be measured (EC-10); if it is material, condition 1 gains a companion requirement that a web request context is present.

**Facet-driven interactions.** Applied facet selections are recorded wherever they can be obtained (4.1), because the same query text under different facets returns a different result list. Without them, an evaluator sees what looks like nondeterminism for a single query and cannot compare rows correctly.

Extraction is not uniformly possible, and this is expected to be the hardest part of the implementation (EC-12). On requests carrying a `SearchContext`, facet state is reachable through it. On headless and Blueprint-driven paths, user selections may already have been translated into query clauses before the `SearchRequest` was finalized, leaving no clean facet representation to read.

Because of that, every event records a `facetCaptureStatus` of `NONE_APPLIED`, `CAPTURED` or `UNAVAILABLE`. This distinction is not cosmetic: an empty facet field could otherwise mean either that the user applied no facets or that facets were applied and could not be read, and those two are not interchangeable. Silently merging them reintroduces exactly the apparent-nondeterminism problem facet capture exists to prevent. An evaluator can segregate or discard `UNAVAILABLE` rows; they cannot recover from a conflated field.

**Origin classification.** `sourceType` is derived from the current request, read defensively:

1. No HTTP request available on the thread: `UNKNOWN`.
2. Request URI in the headless namespace (`/o/...`): `HEADLESS`.
3. Otherwise, with a portlet or `ThemeDisplay` context present: `WIDGET`.
4. Anything else: `OTHER`.

Stack trace inspection is explicitly ruled out: it is too expensive for a path on every search. `sourceType` is descriptive metadata and is *not* an input to the admission filter, so a misclassification degrades dataset quality without affecting what is captured. It records `UNKNOWN` rather than guessing. (Suggestion classification in 5.1 also reads the URI, and that use *is* load-bearing; it is covered by EC-2.)

Searches with facet selections but *no* keywords are a separate question. They are genuine user interactions, but they carry no query string, so they are not `(query, result[])` records and cannot be scored with rank metrics that presuppose a query. Admitting them also means relaxing condition 1, which is the filter's main defense against internal traffic that likewise issues keyword-free structured queries. They are therefore admitted only when explicitly enabled (Section 5), off by default, and EC-10 measures what enabling it would cost in noise.

**Context extraction is defensive throughout.** `Searcher` can be invoked from background threads where `ThemeDisplay` and `PermissionThreadLocal` are not populated. Every ThreadLocal read is null-guarded, and absence of context is treated as a reason to drop the request rather than as an error. No context extraction may throw.

### 3.3 Asynchronous dispatch and persistence

The wrapper publishes to a dedicated Message Bus destination and returns immediately. Message Bus asynchronous sending is fire-and-forget: the sender continues processing without waiting for listeners.

- Destination created via `DestinationConfiguration.createSerialDestinationConfiguration("tensoropt/search-eval-log")` and registered through `DestinationFactory` as a `Destination` service keyed on the `destination.name` property.
- Consumer is an ordinary `@Component(property = "destination.name=tensoropt/search-eval-log", service = MessageListener.class)`.
- `DestinationConfiguration` exposes maximum queue size, thread pool core and maximum size, and the rejected execution handler. These are the backpressure controls: under load the destination must **drop** log messages rather than queue unboundedly or block producers. Dropping log rows is always preferable to degrading production search. Drops are counted and reported in the export manifest.

The listener writes each message directly to the database on receipt. There is no cross-event in-memory buffer and no scheduled flush job: Message Bus already provides the queue and the backpressure, and an additional buffer would only add complexity and a data-loss window on unexpected shutdown.

Batching does happen, but only *within* a message: one search event and its up-to-K hit rows are written in a single transaction. A serial destination consumes single-threaded, which naturally rate-limits database write pressure.

Implementation note: Service Builder's generated add methods issue individual `INSERT` statements. A `@Transactional` boundary provides atomicity but not a JDBC batch, so at K=100 the database still receives 101 statements inside one transaction. That is acceptable here, because the work happens on an async serial consumer and never touches a search thread. If database IO becomes a bottleneck on a high-volume instance, the remedy is a custom finder performing a true bulk insert in `SearchEventLocalServiceImpl`. Deferred until measurement shows it is needed; it also matters less in practice than it looks, since realistic capture depth is 10 to 20 rows rather than 100.

Note: synchronous Message Bus sending was removed in DXP 7.4 U49 / Portal 7.4 GA49 and above. Only the asynchronous path is used, so this is not a constraint.

### 3.4 Retention purge

A single recurring job deletes events and hits older than the retention window. It deletes in bounded batches (a few thousand rows per pass, with a pause between passes) rather than issuing one large DELETE, so it cannot lock tables or time out once the dataset reaches millions of rows. Both tables are indexed on the columns the purge predicates on (see 4.5).

Candidate mechanisms: `com.liferay.portal.kernel.scheduler` (`SchedulerEngineHelper`) for the recurring purge, and `com.liferay.portal.kernel.backgroundtask` (`BackgroundTaskManager`, `BackgroundTaskExecutor`) for the export, where visible execution history in Control Panel is a small but real trust win on a production install. Final selection is an empirical check (EC-7).

### 3.5 Module layout

Single Liferay Workspace project, deployed identically to base DXP and LES. No separate LES build.

```
liferay-search-eval-logger/
  modules/
    search-eval-logger-api/        Shared interfaces and value objects
    search-eval-logger-service/    Service Builder, entities, persistence
    search-eval-logger-impl/       Searcher wrapper, admission filter, listener, purge
    search-eval-logger-web/        Admin portlet (config + export)
```

---

## 4. Data Model

Two tables. One row per admitted search event, one row per captured hit.

### 4.1 `SEL_SearchEvent`

| Column | Type | Notes |
|---|---|---|
| `searchEventId` | long | PK |
| `uuid` | String | Stable ID joining hits to the event |
| `companyId` | long | Virtual instance |
| `createDate` | Date | Event timestamp, UTC. Indexed. |
| `queryText` | String (2000) | Raw keywords, truncated at the configured cap |
| `queryTruncated` | boolean | True if the cap was applied, so truncation is never silent |
| `locale` | String (20) | Request locale |
| `scopeGroupIds` | String (500) | Comma-separated site scope |
| `entryClassNames` | String (2000) | Asset types requested, if constrained |
| `appliedFacets` | Clob | Facet and filter selections active on this request, JSON-encoded |
| `facetCaptureStatus` | String (20) | `NONE_APPLIED`, `CAPTURED` or `UNAVAILABLE`. See 3.2. |
| `blueprintId` | String (100) | Blueprint driving this query, when detectable. See 8. Subject to EC-13. |
| `audienceType` | String (20) | `GUEST` or `AUTHENTICATED` |
| `cohortHash` | String (64) | See 4.3 |
| `requestedSize` | int | Page size the caller asked for |
| `requestedFrom` | int | Offset the caller asked for |
| `totalHits` | long | Total matches reported by the engine |
| `loggedHitCount` | int | Rows actually written for this event |
| `sourceType` | String (20) | `WIDGET`, `HEADLESS`, `OTHER`, `UNKNOWN`. Heuristic defined in 3.2. |

`requestedSize`, `requestedFrom`, `totalHits` and `loggedHitCount` together make truncation explicit and structural. An analyst can distinguish "only 10 results existed" from "only 10 results were captured because the widget paginates at 10", which is the difference between a valid metric and a misleading one.

### 4.2 `SEL_SearchHit`

| Column | Type | Notes |
|---|---|---|
| `searchHitId` | long | PK |
| `searchEventUuid` | String | FK to the event. Indexed. |
| `rank` | int | Absolute rank: `requestedFrom + position` |
| `score` | double | From `SearchHit.getScore()` |
| `docUid` | String (500) | From `document.getString(Field.UID)` |
| `entryClassName` | String (200) | |
| `entryClassPK` | long | Subject to EC-8 |
| `title` | String (1000) | Captured if present in the response and in the configured field list |
| `snippet` | Clob | Best-effort, see 4.4 |
| `extraFields` | Clob | Whitelisted additional fields, JSON-encoded |

### 4.3 Evaluation cohorts

Liferay filters results by the requesting user's permissions. Two users issuing the same query receive different result lists. Without accounting for this, an aggregate relevance metric computed over the log measures access control as much as ranking quality.

An earlier draft proposed fingerprinting the permission-determining inputs directly (effective role IDs, group and site memberships). That is dropped: extracting effective roles reliably across versions is complex, and it depends on ThreadLocals that are not always populated when `Searcher` is invoked.

Instead, two cheap and reliably available fields:

- **`audienceType`**: `GUEST` or `AUTHENTICATED`. Always available, zero risk.
- **`cohortHash`**: a salted hash of the user ID, read directly from the `SearchContext` with no ThreadLocal dependency. **The salt rotates on a fixed schedule (weekly by default).** Superseded salts are discarded, never archived. The current salt is stored locally and never exported.

`cohortHash` partitions *finer* than a true permission cohort: every user with identical permissions gets a distinct value. That is the safe direction of error. Over-partitioning costs statistical power but never produces an invalid comparison, whereas under-partitioning silently mixes incomparable rows. An evaluator can restrict to guests for a fully comparable population, or group by `cohortHash` and compute within-group metrics.

**Why the salt rotates.** A static salt would produce a stable pseudonymous identifier spanning the full 90-day retention window. Pseudonymous data is still personal data under GDPR, so that would work directly against D3's purpose of keeping the install decision out of legal review. Rotation caps the linkable span at one rotation period.

This is a deliberate trade of statistical power for privacy surface. Within a rotation window, rows sharing a `cohortHash` are comparable and groupable. Across windows they are not, so an evaluator cannot aggregate a single cohort over the whole retention period. On low-traffic instances a weekly window may yield groups too small to be useful, in which case the period is lengthened knowingly rather than by default. The period is configurable; the default is the conservative end.

Session-ID hashing was considered as an alternative and rejected: session context is not reliably available on every path reaching `Searcher` (EC-11), whereas a rotating salt has no context dependency at all.

### 4.4 Snippet and field capture

Per D8, the plugin captures only what the caller already requested. Elasticsearch returns only the fields named in the request (via `_source` filtering or selected field names), and Liferay's search widgets frequently do not request full document content, instead resolving result summaries through the Indexer. Consequently:

- **Snippets** are captured only when the calling code already enabled highlighting, which most user-facing search UIs do in order to bold matched terms. Where it did not, `snippet` is null.
- **Titles and whitelisted extra fields** are captured only when present in the returned `Document`. A configured field that the caller did not request is simply absent.

There is no fallback. An earlier draft proposed excerpting from a stored content or description field inside the async listener; that is not possible, because such a field is only in the response if the caller requested it, and causing it to be requested would violate D2.

The practical consequence for downstream judging: coverage of snippet and auxiliary fields is a property of the installation's search UI, not of this plugin, and it varies. The export manifest reports per-field coverage rates so an evaluator knows what they actually received before designing a judging pass. If coverage turns out to be too thin for LLM-based judging on a given install, the fix is a change to that installation's search configuration, not to this plugin.

### 4.5 Storage and field sizing

Service Builder maps `String` to `VARCHAR(75)` by default. Every text column above therefore requires an explicit `max-length` in `portlet-model-hints.xml`, or `Clob` where the content is unbounded. Without this, pasted search input and long snippets cause silent truncation or SQL exceptions.

Query text is additionally capped in application code (default 2000 characters) with the `queryTruncated` flag set, so an oversized paste produces a bounded row and a visible marker rather than either a database error or invisible data loss.

Indexes: `SEL_SearchEvent(companyId, createDate)` for both export range selection and the retention purge; `SEL_SearchHit(searchEventUuid)` for the join and for cascading deletes.

Volume: at 20 captured hits per event (a realistic figure given widget pagination) and 5,000 admitted searches per day, the hit table grows by roughly 100,000 rows per day and holds around 9 million rows at a 90-day retention window. Manageable, but only with the indexing and batched purge above.

---

## 5. Configuration

Exposed through a Configuration Admin-backed admin screen (`@Meta.OCD`), scoped per virtual instance.

| Setting | Default | Notes |
|---|---|---|
| Logging enabled | `false` | **Off on install.** Installing the plugin must not silently begin collecting data. An admin opts in explicitly. |
| Capture depth (K) | `100` | A ceiling, not a target. Actual capture is `min(K, hits returned)`. With typical widget pagination expect 10 to 20 rows per event. |
| Retention window | `90 days` | Purge deletes events and hits older than this, in bounded batches. |
| Captured field list | `title`, `snippet` | Whitelist. Additional fields are opt-in per installation. Never "whatever the response contains". |
| Exclude suggestion traffic | `true` | See 5.1. |
| Admit facet-only searches | `false` | Keyword-free, facet-driven interactions. Off by default: no query string to evaluate, and admitting them weakens the admission filter (3.2). |
| Cohort salt rotation period | `7 days` | Caps how long a `cohortHash` remains linkable. Lengthen only if cohort groups prove too small (4.3). |
| Require web request context | `false` | Tightens the admission filter when internal traffic proves noisy (EC-10). |
| Query text cap | `2000` chars | See 4.5. |
| Sampling rate | `1.0` | Escape hatch for very high-volume instances. |
| Excluded asset types | empty | Skips noisy or sensitive content types. |

The whitelist matters more than it looks. It is the difference between an export an admin approves after one read and an export that goes to legal. Full document bodies are never captured.

### 5.1 Suggestion traffic exclusion

Search Bar Suggestions (DXP 7.4 U36+/GA36+) issues a fresh query each time the configured character threshold is reached as the user types. Left unfiltered, typeahead requests would dominate the log and swamp genuine user-submitted queries.

Suggestions run through a distinct headless endpoint, `/o/search/v1.0/suggestions` (previously `/o/portal-search-rest/v1.0/suggestions`), driven by named contributors (`basic`, or `sxpBlueprint` on LES). That distinct entry point is the filtering handle: the wrapper classifies the request via the current request thread-local and drops it when exclusion is on.

Whether suggestion requests reach `Searcher` at all, and whether the thread-local is reliably populated on that path, is an empirical check (EC-2). Because context extraction is defensive (3.2), an unpopulated ThreadLocal results in the request being dropped rather than misclassified, which fails in the safe direction.

---

## 6. Export

### 6.1 Trigger

An admin opens the plugin's Control Panel screen, selects a start and end date, and runs the export. It executes as a background task so progress and history are visible. The resulting archive is offered as a download. Nothing is transmitted anywhere automatically.

Access is gated by a dedicated resource permission, so exporting can be restricted independently of general portal administration.

**The export must stream.** A full-window export can span millions of hit rows (4.5), and administrators will run the full window. Loading those through standard Service Builder finders before serializing would exhaust the heap and take down a production JVM. The background task therefore iterates with `ActionableDynamicQuery` (or a raw JDBC cursor), serializing each event to JSONL and writing straight into the `ZipOutputStream` in small chunks, so memory stays flat regardless of dataset size. The archive is assembled on disk, never in memory. This is a hard implementation requirement, not an optimization.

### 6.2 Archive contents

```
search-eval-export-<instance>-<from>-<to>.zip
  events.jsonl      One JSON object per line: a search event with its hits nested
  manifest.json     Export metadata, coverage statistics, plugin configuration
  README.md         Schema description and known caveats
```

**Format: JSONL, not CSV.** Snippets and titles routinely contain quotes, commas, newlines and highlight markup (`<liferay-hl>` tags by default). Embedding those, plus a JSON extra-fields column, inside CSV reliably breaks downstream parsers. JSONL handles nesting and escaping natively and is the standard interchange format for evaluation datasets.

Nesting also eliminates the join. Each line is exactly the `(query, result[])` record the dataset exists to provide:

```json
{
  "event_id": "9f2c...",
  "created_at": "2026-09-14T10:22:31Z",
  "query": "annual leave policy",
  "locale": "en_US",
  "scope_group_ids": [20121],
  "entry_class_names": ["com.liferay.journal.model.JournalArticle"],
  "applied_facets": {"category": ["HR Policies"]},
  "facet_capture_status": "CAPTURED",
  "blueprint_id": null,
  "audience_type": "AUTHENTICATED",
  "cohort_hash": "3a7f...",
  "requested_size": 20,
  "requested_from": 0,
  "total_hits": 147,
  "logged_hit_count": 20,
  "source_type": "WIDGET",
  "hits": [
    {
      "rank": 0,
      "score": 8.42,
      "doc_uid": "com.liferay.journal.model.JournalArticle_PORTLET_38291",
      "entry_class_name": "com.liferay.journal.model.JournalArticle",
      "entry_class_pk": 38291,
      "title": "Annual Leave Policy 2026",
      "snippet": "... employees accrue <liferay-hl>annual leave</liferay-hl> at ...",
      "extra_fields": {}
    }
  ]
}
```

**`manifest.json`** records the export time range, event and hit counts, plugin version, Liferay version, the plugin's configuration at export time (capture depth, field whitelist, sampling rate, exclusions, admission filter settings), the count of events dropped due to backpressure during the period, and **per-field coverage rates** (what fraction of hits carry a snippet, a title, each whitelisted extra field). An evaluator needs the coverage figures to know whether a judging pass is feasible before starting one, and the drop count to know whether the log is a census or a lossy sample.

`README.md` states the structural caveats in plain language: capture depth is bounded by caller pagination, results are permission-filtered, field coverage depends on the installation's search UI, internal traffic was filtered out by the admission rules recorded in the manifest, facet capture may be unavailable on some paths (`facet_capture_status`), and Blueprints may impose filters not represented in `applied_facets` (see 8).

Liferay's own search configuration (Blueprints, Result Rankings, Synonym Sets, index mappings) is **not** exported. See Section 8.

---

## 7. Open Questions and Empirical Checks

Each must be resolved against a running instance before or during implementation. EC-1 is blocking: if it fails, the interception approach needs rework.

Where a check lists a starting hypothesis, that is a place to look first, not a finding. Nothing here is closed until observed on a running instance. This applies to EC-1 in particular: it is the assumption the entire interception design rests on, and it is cheap to confirm directly.

| ID | Check | Why it matters | Status |
|---|---|---|---|
| **EC-1** | Does Liferay's Search Results widget route through `Searcher`, or through the legacy `SearchContext` path (`Indexer.search(SearchContext)`, `FacetedSearcher`, `com.liferay.portal.search.legacy.searcher.SearchRequestBuilderFactory`)? | **Blocking.** If the main site search box bypasses `Searcher`, the plugin logs nothing useful. Method: deploy a no-op ranked wrapper that logs on entry, search from a standard search page, confirm it fires. Hypothesis: it does route through `Searcher`. Confirm by observation only. If it does not fire, investigate the code path; do not assume a configuration toggle exists. | Open |
| **EC-2** | Do Search Bar Suggestions requests reach `Searcher`, and can they be reliably classified from within the wrapper? | Determines whether 5.1 exclusion works by direct signal or needs a heuristic. | Open |
| **EC-3** | Is `Searcher` cleanly overridable by `service.ranking`, and what is the correct `@Reference` target filter to avoid self-binding? | Core mechanism. Also: behavior if a second app wraps `Searcher`. Document a distinct ranking value and make delegate lookup defensive. | **Resolved, with a constraint.** Overriding works, but only for consumers that bind *after* the wrapper is registered. Liferay's search consumers (`SearchDisplayContextFactoryImpl` and `PortletSharedSearchRequestImpl` in `com.liferay.portal.search.web`, `SearchResultResourceImpl` and `FacetResponseProcessor` in `com.liferay.portal.search.rest.impl`, `BasicSuggestionsContributor` in `com.liferay.portal.search`) declare a plain `@Reference`, which is static and reluctant: a consumer already bound to the portal's own `Searcher` never rebinds when a higher-ranked one appears. Installing onto a running portal therefore collects nothing, silently, until a restart. See 3.1. Self-binding is avoided with a marker property rather than a ranking negation, so a second wrapper stays reachable instead of being filtered out. |
| **EC-4** | Confirm which fields are actually present in the `SearchResponse` for a default Search Results widget query: title, snippet, description, custom fields. | Sets realistic expectations for judging (4.4). Determines whether title-plus-snippet is typically available or the dataset is thinner than assumed. | Open |
| **EC-5** | Does the headless Search API (`/o/search/v1.0/...`, GA in DXP 2025.Q4+) route through `Searcher`? | Coverage completeness for API-driven consumers. | Open |
| **EC-6** | Does interception behave identically with LES Blueprints active? | The whole no-branch premise depends on this. | Open |
| **EC-7** | `SchedulerEngineHelper` vs `BackgroundTaskManager` for purge and export. | Section 3.4. Verify both are public kernel API on 7.4 and pick per job type. | Open |
| **EC-8** | Is `entryClassPK` reliably present on the returned `Document` for all asset types? | Affects whether hits resolve back to content. | Open |
| **EC-9** | Deploy and smoke-test on the current DXP Cloud quarterly release, not only numbered 7.4 updates. | PaaS/SaaS customers are on the continuous-release train. | Open |
| **EC-10** | Measure what share of `Searcher` traffic on a realistic instance is internal (Asset Publisher, Control Panel, workflow, DDM), and how much of it survives the keywords-present admission condition. Separately, measure how much internal traffic would be admitted if facet-only searches were allowed. | Determines whether the allowlist in 3.2 is sufficient or needs the web-request-context requirement enabled by default, and whether facet-only admission is viable at all. | Open |
| **EC-12** | Determine on which paths applied facet and filter selections are readable, and from where. Probe in order: `searchContext.getFacets()`, then `searchContext.getAttributes()` (UI components sometimes place raw state there), then the query tree. Expected to be the hardest item here, since facets may already be translated into query clauses before the `SearchRequest` is finalized. | Sets the real coverage of `applied_facets` and how often `facetCaptureStatus` is `UNAVAILABLE` (3.2). Skew toward particular UI types must be reported to the evaluator. **Acceptance:** widget-path capture working is sufficient for v1. Headless paths resolving to `UNAVAILABLE` is an accepted outcome, not a failure; reading facets out of the request payload stream is explicitly out of scope. | Open |
| **EC-13** | Is a Blueprint identifier (for example `searchContext.getAttribute("search.experiences.blueprint.id")`) readable on Blueprint-driven searches? **Test both a widget-applied Blueprint and a globally applied one** (Control Panel, System Settings, Search): the two may resolve differently, and testing only one risks a false pass. | Decides whether the invisible-filter problem in Section 8 gets a partition key or only a README caveat. LES installs only. | Open |
| **EC-11** | Verify which ThreadLocals (`ThemeDisplay`, `PermissionThreadLocal`, request) are populated on each search path that reaches `Searcher`. | Underpins suggestion classification and any context-dependent field. Determines how much is droppable versus recoverable. | Open |

---

## 8. Non-Goals and Deferred Items

**Liferay search configuration export.** Capturing Blueprints, Result Rankings, Synonym Sets, index mappings and widget configuration was considered and deliberately dropped from v1. It is not one artifact but several, and the relevant Search Results and Facet widget configuration is per-page, so "the config" is not well defined at instance scope. Where reproducibility matters, configuration is captured manually during the engagement.

**Known consequence: invisible Blueprint filters.** A Blueprint can inject hardcoded filter clauses into a query, for example always constraining a given search page to one tag or category. Those are not user-applied facets, so they do not appear in `applied_facets`, and because the Blueprint configuration is not exported they are invisible in the dataset. The same query text run on a Blueprint-constrained page and on a generic search page produces two rows that look identical but have unrelated result sets.

Mitigation rather than warning: the event records `blueprintId` when detectable, giving the evaluator a partition key to separate those populations instead of a caveat to remember. Whether the identifier is reliably readable from the request is EC-13. If it is not, the README caveat is the fallback, and Blueprint-heavy installs become a known limitation of the dataset rather than a silent corruption of it.

**Query-text redaction.** Regex-based scrubbing of emails, phone patterns and long digit runs was considered and deferred (D6). Target workloads are document, web content and knowledge-base search, where queries are predominantly non-PII, and redacting without a concrete objection is premature. Two things follow and should be stated plainly in the README rather than discovered later:

1. Once enabled, the plugin creates a table of user-submitted text on a production system. Whether that constitutes personal data is the operator's determination.
2. If query text does contain personal data, handing an export to an external evaluator makes that party a processor. The appropriate instrument is a data processing agreement, not an NDA. An NDA addresses confidentiality between the parties; it does not establish a lawful basis for the transfer.

Redaction moves into scope on the first concrete customer objection, and is cheap to add given the field whitelist already exists.

**Click-through capture.** Tagging rendered result links with the event ID and rank, then capturing clicks, would turn the interaction log into implicit relevance judgments and close the loop toward real QRels. It requires touching the result rendering path, which is materially larger and more invasive than a passive service wrapper. Deferred to v2, and only worth doing once v1 has proven acceptable to install.

**Analysis UI.** No dashboards, metrics or reporting inside Liferay. The plugin collects and exports. Analysis happens elsewhere.

---

## 9. Packaging, Licensing, Distribution

**Target:** Liferay DXP 7.4, plus verification against the current DXP Cloud quarterly release (EC-9). Portal CE and pre-7.4 EE are out of scope: low audience overlap, extra maintenance, and Elasticsearch 7 compatibility is being removed from Liferay's support matrix around January 2026, which makes 7.3-era installs a poor investment for a forward-looking tool.

Because the plugin only touches the public Search API, this is one codebase with a compatibility range to test, not a support matrix to branch on.

**License:** Apache 2.0. The explicit patent grant is worth having when corporate legal teams review the dependency.

**Price:** Free. The plugin is the instrument, not the product. Charging for the collector adds friction exactly where adoption depends on having none.

**Distribution:** Public GitHub repository with prebuilt release artifacts (`.lpkg` and raw JAR). Not Liferay Marketplace, at least initially.

The reasoning is specific: trust is the bottleneck, not discovery. A tool that logs production search queries must clear a security review before an ops team approves it on prod, and Liferay's Marketplace review explicitly does not include source code review. Marketplace therefore provides no credibility on the only question that matters to the installing admin, while a public repository lets their own engineers read the code before approving. Distribution is handled by the conference talk and direct outreach.

Marketplace listing remains available later, for free, purely for discoverability once the plugin is stable. It is an optional addition, not the primary channel.

**Repository contents:** source, build, prebuilt artifacts, a README covering what is collected and what is not, the export schema, the data-protection notes from Section 8, and this design document.
