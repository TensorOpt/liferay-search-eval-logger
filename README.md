# Liferay Search Eval Logger

A Liferay DXP 7.4 plugin that produces the artifact relevance evaluation needs and
base DXP does not provide: a persistent, structured `(query, result[])` interaction
log.

The intended workflow: an administrator installs it on a production instance,
enables collection, lets it run, then exports a time-bounded dataset for offline
relevance analysis. Capture is designed to be passive — it wraps the public
`Searcher` service, never modifies the outgoing search request, and never blocks or
fails a search.

See [DESIGN.md](DESIGN.md) for the full design, including the settled constraints
(D1-D8) that any change must satisfy and the open empirical checks (EC-1-EC-13)
that still have to be validated against a running instance.

## Status

**Feature complete, unverified. Not released.** Capture, filtering, asynchronous
persistence, the retention purge, the admin screen and the export are all
implemented, and the four modules build together as one workspace. None of it has
yet been deployed to or run against a real Liferay instance, and the open empirical
checks in DESIGN.md section 7 are exactly that work: until EC-1 is confirmed on a
live instance, even the interception point this plugin is built on is an
assumption. Treat everything below as designed behaviour rather than observed
behaviour.

## What is intended to be collected

- The user's query text, locale, scope and requested asset types
- Applied facet selections, where the search path exposes them. Where they cannot
  be read, the event records `facetCaptureStatus` of `UNAVAILABLE` rather than
  looking like a search with no facets. Selections are read from the search
  context, which the widget path populates; on other paths they may already have
  been translated into query clauses, and those events are recorded as
  `UNAVAILABLE` rather than as "no facets applied" (DESIGN.md 3.2, EC-12)
- The result set that was returned: rank, score, document UID, entry class, and the
  whitelisted fields (`title` and `snippet` by default) when the response already
  contains them
- Pagination context (`requestedSize`, `requestedFrom`, `totalHits`,
  `loggedHitCount`), so a capped result set is never mistaken for a short one
- A coarse evaluation cohort: `GUEST` or `AUTHENTICATED`, plus a salted hash of the
  user ID. The salt is held in memory only, never exported, and rotates on a
  configurable period (weekly by default) and on restart; superseded salts are
  discarded, so a cohort cannot be followed across a rotation

Events and hits older than the retention window (90 days by default) are deleted by
a daily purge.

## What is not collected

- No raw user identifiers, names, emails or IP addresses
- No document bodies. Field capture is an explicit whitelist, never "whatever the
  response contains"
- Nothing the caller did not already request. Snippets appear only where the search
  UI already enabled highlighting; the plugin never widens a query to capture more
- Most internal Liferay traffic. Only searches carrying user keywords are admitted,
  which is the strongest available discriminator against Asset Publisher
  collections, Control Panel listings, workflow and DDM lookups. It is a filter,
  not a guarantee: what share of internal traffic survives it has not yet been
  measured on a real instance (DESIGN.md 3.2, EC-10), and the admission rules in
  force are recorded in each export's manifest
- Nothing is transmitted anywhere. Export is a manual admin action against the
  local instance

Collection is **off on install**. An administrator opts in explicitly.

## What an export contains

An export runs as a background task and produces a ZIP named
`search-eval-export-<companyId>-<from>-<to>.zip`, where the instance token is the
numeric virtual instance ID, not a site or host name. Inside:

- `events.jsonl` — one JSON object per line: a search event with its hits nested,
  so each line is a complete `(query, result[])` record with no join to reconstruct
- `manifest.json` — the range, event and hit counts, plugin and Liferay versions,
  the collector's configuration at export time, backpressure counts and per-field
  coverage rates
- `README.md` — the structural caveats in plain language, so the archive can be
  read correctly on its own

Coverage rates matter before anything else: how often a title or snippet is
actually present depends on the installation's search UI, not on this plugin.

## Building

Prerequisites:

- This repository is a Liferay Workspace; `./gradlew` bootstraps Gradle itself
- Any JDK that Gradle 8.5 supports. Modules compile to Java 8 bytecode, pinned in
  the root `build.gradle`, so the bundles resolve on a DXP 7.4 install running
  JDK 8 or 11 whatever the build JDK was
- Network access to `repository-cdn.liferay.com` for the target platform artifacts
- A Liferay DXP 7.4 instance to deploy to

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
