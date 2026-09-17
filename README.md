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

**Scaffold in progress. Not installable yet.** The Liferay Workspace and the `api`
module exist; the modules that do the actual capturing, persisting and exporting do
not. See the module table below for what is present. Everything the next two
sections describe is the designed behaviour, not shipped behaviour.

## What is intended to be collected

- The user's query text, locale, scope and requested asset types
- Applied facet selections, where the search path exposes them. Where they cannot
  be read, the event says so explicitly (`facetCaptureStatus` of `UNAVAILABLE`)
  rather than looking like a search with no facets. Coverage is expected to be good
  on widget paths and poor on headless ones (DESIGN.md 3.2, EC-12)
- The result set that was returned: rank, score, document UID, entry class, and the
  whitelisted fields (`title` and `snippet` by default) when the response already
  contains them
- Pagination context (`requestedSize`, `requestedFrom`, `totalHits`,
  `loggedHitCount`), so a capped result set is never mistaken for a short one
- A coarse evaluation cohort: `GUEST` or `AUTHENTICATED`, plus a salted hash of the
  user ID whose salt rotates weekly and whose superseded salts are discarded

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

## Building

Prerequisites:

- This repository is a Liferay Workspace; `./gradlew` bootstraps Gradle itself
- A JDK for the build. Modules compile to Java 8 bytecode, pinned in the root
  `build.gradle`, so the bundles resolve on a DXP 7.4 install running JDK 8 or 11
- Network access to `repository-cdn.liferay.com` for the target platform artifacts
- A Liferay DXP 7.4 instance to deploy to

```
./gradlew build          # build every module that exists
./gradlew deploy         # build and copy the JARs to the bundle's deploy folder
```

The target DXP update level is `liferay.workspace.product` in `gradle.properties`;
everything else about the target platform is derived from it.

## Modules

| Module | Contents | Status |
|---|---|---|
| `search-eval-logger-api` | Shared enums and cross-module constants | Present |
| `search-eval-logger-service` | Service Builder entities and persistence | Planned |
| `search-eval-logger-impl` | `Searcher` wrapper, admission filter, listener, purge | Planned |
| `search-eval-logger-web` | Admin portlet: configuration and export | Planned |

## Data protection

Once enabled, the plugin creates a table of user-submitted text on a production
system. Whether that constitutes personal data is the operator's determination. If
it does, handing an export to an external evaluator makes that party a processor,
and the appropriate instrument is a data processing agreement rather than an NDA.
DESIGN.md section 8 states this in full.

## License

Apache License 2.0. See [LICENSE](LICENSE).
