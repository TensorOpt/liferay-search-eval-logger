# Liferay Search Eval Logger

A Liferay DXP plugin that produces the artifact relevance evaluation needs and
base DXP does not provide: a persistent, structured `(query, result[])` interaction
log.

The intended workflow: an administrator installs it on a production instance,
enables collection, lets it run, then exports a time-bounded dataset for offline
relevance analysis. Capture is designed to be passive — it wraps the public
`Searcher` service, never modifies the outgoing search request, and never blocks or
fails a search.

See [DESIGN.md](DESIGN.md) for the full design, including the settled constraints
(D1-D8) that any change must satisfy and the empirical checks (EC-1-EC-13), each
recorded there with what running the plugin actually showed.

## Status

**Running and measured on a real instance. Not released.**

Every part of the pipeline — capture, filtering, asynchronous persistence, the
retention purge, the collection cycle and its notifications, the admin screen
and the export — has been deployed to and exercised against Liferay DXP
2025.Q1.27 LTS on PostgreSQL 15. The figures below are observed rather than
projected.

Verified:

- **Interception** (EC-1, EC-2). The Search Results widget routes through
  `Searcher`; searches issued from a browser are captured, persisted and exported.
  Suggestion traffic reaches the wrapper too and is classifiable, so it can be
  excluded rather than silently counted as user intent.
- **The restart constraint** (EC-3). Overriding `Searcher` by `service.ranking`
  works, but only for consumers that bind after the wrapper registers — see
  [Restart the portal after installing and after every redeploy](#restart-the-portal-after-installing-and-after-every-redeploy).
  The plugin's detection of that state has been confirmed firing in the admin
  screen.
- **Field availability** (EC-4), and thinner than the design assumed. Title and a
  highlighted snippet are both present on the widget path, but the snippet comes
  from `SearchHit#getHighlightFieldsMap()`, not from any field inside the
  `Document`.
- **Export at scale.** On a corpus of 1,000,000 events and 10,000,000 hits, the
  retention purge removes the 500,008 events and 5,000,080 hits past a 30-day
  window in 14 s, and the remaining month exports in two and a half minutes to a
  66 MB archive, streamed rather than loaded. The archive is checked rather than
  assumed: one JSONL line per event, every `manifest.json` key from DESIGN.md 6.2
  present, ids and counts as JSON numbers, the file named for the requested
  range, and exactly the rows the range holds.
- **Streaming on each database's driver.** The export's statement, run against
  real servers with one million hit rows on a 256 MB heap, streams on PostgreSQL
  and on MariaDB with the drivers DXP ships, and on MySQL only with
  `useCursorFetch=true` — see [Database](#database). That was measured at the
  JDBC level; the plugin as a whole has only run on PostgreSQL.
- **Search is not slowed by collection.** Over 180 searches each way, collection
  on added 4 ms to the median search round trip (64 ms against 60 ms). With the
  persistence listener blocked, 2,600 searches from 16 clients all answered
  normally: the 598 past the queue's 2,000 events were dropped and counted once,
  and search latency was no worse than with the queue draining.
- **The collection cycle.** The daily purge and cycle check are scheduled for
  03:00 and 03:30 every day, whenever the portal last restarted. The stall and
  readiness notifications appear in the administrator's own notifications list.
- **Uninstalling and reinstalling.** Removing the four bundles from a running
  portal leaves search answering with no restart, logs no error and leaves the
  data in place; reinstalling picks up the existing tables and the collection
  start date, and collection resumes after the restart.
- **Unit tests.** 128, run by `./gradlew test`: 5 in api, 6 in service, 70 in
  impl, 47 in web. The service module's are for the export query builder; the rest
  of that module is Service Builder output.
- **End-to-end suite.** `e2e/run.sh` brings up PostgreSQL 15 and DXP 2025.Q1.27
  LTS in Docker, installs the plugin onto the running portal, and runs 26 cases
  covering everything above that the PostgreSQL stack can reach, including the
  `EXPORT` permission refusing a user who does not hold it and the export's
  download URL refusing another background task's attachment. All 26 passed at
  full scale (`--scale full`). See [e2e/README.md](e2e/README.md).

Not verified:

- Blueprints (EC-6, EC-13) and the headless Search API (EC-5). The first needs
  Liferay Enterprise Search; the second is gated behind feature flag `LPS-179669`,
  which could not be opened on the test instance. Code inspection says the headless
  path resolves `Searcher` through the registry and would be wrapped, inheriting
  EC-3's constraint, but that is reasoning, not a measurement.
- DXP Cloud (EC-9). Never deployed there.
- The whole plugin on anything but PostgreSQL. MySQL and MariaDB were measured
  for the export's streaming only; Oracle, SQL Server and DB2 not at all.
- Clusters. Everything has run on one node; the collection cycle's per-node
  marker (DESIGN.md 3.6) exists for the multi-node case and has never met one.
- The scheduler firing a job. Both jobs' triggers are confirmed set to their
  time of day, and the jobs themselves are exercised directly, but no test has
  waited until 03:00 to watch Liferay fire one.
- The internal-traffic ratio (EC-10) was measured only on an idle instance, where
  the denominator was dominated by test searches. It is not a production figure.
- The two evaluation service links still carry the placeholder addresses
  `{{SIGNUP_URL}}` and `{{BOOKING_URL}}`; they are replaced when a release is cut
  (TO-107).

See DESIGN.md section 7 for each check in full.

## What is collected

- The user's query text, locale, scope and requested asset types
- Applied facet selections, where the search path exposes them. Where they cannot
  be read, the event records `facetCaptureStatus` of `UNAVAILABLE` rather than
  looking like a search with no facets. Selections are read from the search
  context, which the widget path populates; on other paths they may already have
  been translated into query clauses, and those events are recorded as
  `UNAVAILABLE` rather than as "no facets applied" (DESIGN.md 3.2, EC-12)
- The result set that was returned: rank, score, document UID, entry class, and the
  whitelisted fields (`title` and `snippet` by default) when the response already
  contains them. `snippet` is not one of the indexed fields — it is read from the
  response's highlight fragments, so it is present only for searches the UI ran
  with highlighting on, and absent rather than empty otherwise (EC-4)
- Pagination context (`requestedSize`, `requestedFrom`, `totalHits`,
  `loggedHitCount`), so a capped result set is never mistaken for a short one
- A coarse evaluation cohort: `GUEST` or `AUTHENTICATED`, plus a salted hash of the
  user ID. The salt is held in memory only, never exported, and rotates on a
  configurable period (weekly by default) and on restart; superseded salts are
  discarded, so a cohort cannot be followed across a rotation

Events and hits older than the retention window (90 days by default) are deleted by
a purge that runs every day at 03:00, in the portal's time zone.

## What is not collected

- No raw user identifiers, names, emails or IP addresses
- No document bodies. Field capture is an explicit whitelist, never "whatever the
  response contains"
- Nothing in the portal log. Query text and result snippets are written to the
  plugin's own tables and nowhere else, so the retention window is a guarantee the
  plugin can actually keep: a log file it does not control would outlive the purge
  and travel to wherever logs are shipped
- Nothing the caller did not already request. Snippets appear only where the search
  UI already enabled highlighting; the plugin never widens a query to capture more
- Most internal Liferay traffic. Only searches carrying user keywords are admitted,
  which is the strongest available discriminator against Asset Publisher
  collections, Control Panel listings, workflow and DDM lookups. It is a filter,
  not a guarantee: on an otherwise idle instance the keyword condition removed 8 of
  18 observed searches, so internal keyword-free traffic is real and non-trivial
  even at rest, and the share on a busy instance is unmeasured (DESIGN.md 3.2,
  EC-10). Both the admission rules in force and the resulting funnel are recorded
  in each export's manifest
- Nothing is transmitted anywhere. Export is a manual admin action against the
  local instance

Collection is **off on install**. An administrator opts in explicitly.

## The two links to the evaluation service

The plugin is free and the collector is the instrument, not the product. What it
does contain is two links to TensorOpt, the evaluation service this was built to
feed, and they are documented here rather than left to be discovered in the
source.

**The plugin never makes an outbound network request.** Not from the server, not
from your administrators' browsers. There are no install pings, no usage counts,
no export notifications, no update checks. TensorOpt does not learn that your
installation exists unless a person clicks one of these two links. Both are
ordinary `<a href target="_blank">` anchors: there is no prefetch, no preload, no
iframe, no image, no tracking pixel and no script that touches either address.
Both are defined as constants in a single class,
`EvaluationServiceLinks` in `search-eval-logger-web`, so you can read and change
them in one place.

| Link | Where | What it carries |
|---|---|---|
| "Get an email reminder when your search log is ready to export" | Search Eval Export screen, once collection has demonstrably started | The date collection started, at day granularity, plus UTM tags. Nothing else: no hostname, instance ID, company name, plugin version, event counts or user data |
| "Want this dataset evaluated? Book a call" | Search Eval Export screen, after a successful export | UTM tags only. No row counts, coverage figures or date range |

Both disappear when **Show evaluation service links** is switched off in
Configuration, which is there so you can remove them without forking.

Neither link appears anywhere inside an export archive. The `manifest.json`, the
archive `README.md` and `events.jsonl` are vendor-neutral, so a dataset can be
handed to any evaluator without carrying an advertisement for a particular one.

The plugin also raises two **local** notifications — one when logging is enabled
but nothing is being collected, one when the log is large enough to be worth
exporting. Those go to a Liferay user through Liferay's own notification
framework, stay on your instance, and contain no external link.

## What an export contains

An export runs as a background task and produces a ZIP named
`search-eval-export-<companyId>-<from>-<to>.zip`, where the instance token is the
numeric virtual instance ID, not a site or host name. Inside:

- `events.jsonl` — one JSON object per line: a search event with its hits nested,
  so each line is a complete `(query, result[])` record with no join to reconstruct
- `manifest.json` — the range, event and hit counts, plugin and Liferay versions,
  the collector's configuration at export time, the admission counters (searches
  observed, of those carrying keywords, of those admitted, then dispatched, dropped
  and persisted) and per-field coverage rates
- `README.md` — the structural caveats in plain language, so the archive can be
  read correctly on its own

Coverage rates matter before anything else: how often a title or snippet is
actually present depends on the installation's search UI, not on this plugin.

## Building

Prerequisites:

- This repository is a Liferay Workspace; `./gradlew` bootstraps Gradle itself
- JDK 17 or 21. DXP 2025.Q1 LTS ships Java 17 bytecode, so an older JDK cannot
  compile against it; modules target Java 17, pinned in the root `build.gradle`
- Network access to `repository-cdn.liferay.com` for the target platform artifacts
- A Liferay DXP 2025.Q1 LTS instance to deploy to (set by `liferay.workspace.product`)

```
./gradlew build          # build every module
./gradlew deploy         # build and copy the JARs to the bundle's deploy folder
```

The target DXP update level is `liferay.workspace.product` in `gradle.properties`;
everything else about the target platform is derived from it.

Once deployed, collection settings appear in Control Panel under Configuration, and
the export screen under Configuration as Search Eval Export. Collection stays off
until an administrator enables it, and exporting is gated by its own `EXPORT`
permission, granted to nobody by default.

## Database

The export streams its result set out of the database rather than loading it,
which is what keeps a multi-million-row export in constant memory. That depends
on the JDBC driver honouring a fetch size, and not every driver does by default.

| Database | Driver | Streams |
|---|---|---|
| PostgreSQL | bundled with DXP | Yes, as shipped |
| MariaDB | MariaDB Connector/J, bundled with DXP | Yes, as shipped |
| MySQL | MySQL Connector/J | **Only with `useCursorFetch=true` on the JDBC URL** |
| Oracle, SQL Server, DB2 | | Untested |

**On MySQL, `useCursorFetch=true` is required.** Without it Connector/J reads
the entire result set into memory whatever fetch size the plugin asks for, and a
full-window export can exhaust the portal's heap. Measured with Connector/J 8.4
against MySQL 8.4: exporting one million hit rows took about 800 MB of heap
without the property and 77 MB with it. Add it to the URL Liferay connects with:

```
jdbc.default.url=jdbc:mysql://localhost/lportal?useCursorFetch=true
```

or, on the Docker image, to `LIFERAY_JDBC_PERIOD_DEFAULT_PERIOD_URL`. The
property is connection wide: every statement in the portal that sets a fetch
size then reads through a server-side cursor. Statements that set none are
unaffected. Its effect on the rest of the portal has not been measured.

## Restart the portal after installing, and after every redeploy

**Installing onto a running portal is not enough. Restart it before enabling
collection, or nothing will be recorded.**

Liferay's search components bind the `Searcher` service once, when they start, with
a reference that does not switch to a higher-ranked one that appears later. A plugin
installed onto an already-running portal is registered correctly and simply never
called: searches are served normally, no error is logged, and the tables stay empty
no matter what the configuration says. Restarting makes the search components bind
the wrapper as they start.

This is not a one-time installation step. For `search-eval-logger-impl`, the bundle
holding the wrapper, a redeploy is the same event as an install: refreshing it
unregisters the wrapper and registers a new instance, while the already-bound
consumers keep the reference they resolved at their own activation — by then, the
portal's own searcher again. Anyone developing against the capture path is on a
deploy, restart, test loop, and a `./gradlew deploy` that reports success against a
portal still happily serving searches will collect nothing until the restart.

The other three modules are ordinary bundles. Redeploying `search-eval-logger-web`
— the admin screen and the export — takes effect immediately and needs no restart.

The plugin detects the state rather than leaving it to be found as an empty export:
it logs a warning on startup and shows it on the export screen. If that warning is
present, a restart is still outstanding.

A restart of the whole portal is the supported route. Refreshing only
`com.liferay.portal.search.web`, `com.liferay.portal.search.rest.impl` and
`com.liferay.portal.search` has the same effect for their respective search paths
and avoids downtime, but it is not something this plugin does on an administrator's
behalf: a logging tool restarting core portal bundles is not a thing an operations
team should have to approve.

## Modules

| Module | Contents |
|---|---|
| `search-eval-logger-api` | Shared enums, constants and configuration interface, plus the generated model and service interfaces |
| `search-eval-logger-service` | Service Builder entities and persistence |
| `search-eval-logger-impl` | `Searcher` wrapper, admission filter, capture, async listener, retention purge |
| `search-eval-logger-web` | Admin portlet, export background task and download |

## Data protection

Once enabled, the plugin creates a table of user-submitted text on a production
system. Whether that constitutes personal data is the operator's determination. If
it does, handing an export to an external evaluator makes that party a processor,
and the appropriate instrument is a data processing agreement rather than an NDA.
DESIGN.md section 8 states this in full.

## License

Apache License 2.0. See [LICENSE](LICENSE).
