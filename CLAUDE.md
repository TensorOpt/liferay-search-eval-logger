# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

**Scaffold in progress.** The Liferay Workspace root (`settings.gradle`, `build.gradle`, `gradle.properties`, Gradle wrapper), `modules/search-eval-logger-api` and `modules/search-eval-logger-service` exist. Nothing captures, persists or exports anything yet: the impl and web modules do not exist. The workspace plugin auto-includes any directory under `modules/` that has a `build.gradle`, so no `settings.gradle` change is needed when they land.

The service module's persisted schema is complete and real: `service.xml` and `portlet-model-hints.xml` are the hand-authored source of truth, `buildService` has been run against the target platform, and its output (model, `*LocalService`, persistence, `META-INF/sql/*.sql`) is generated code that is committed and must never be hand-edited. Re-run `./gradlew :modules:search-eval-logger-service:buildService` after any change to those two files. Two traps: `buildService` rewrites `portlet-model-hints.xml`, so XML comments there are lost (comments belong in `service.xml`, which is preserved); and the generated model type `com.tensoropt.search.eval.logger.model.SearchHit` collides by simple name with Liferay's `com.liferay.portal.search.hits.SearchHit`, so code that touches both must fully qualify one of them.

Business methods that span both entities — notably the single-transaction write of one event plus its hits (§3.3) — belong in `SearchEventLocalServiceImpl` in the service module, not in the impl module, and require a `buildService` re-run to appear on the service interface.

The export permission of §6.1 is deliberately **not** in the service module: it gates an admin screen action on a portlet resource, not per-row entity access, so `resource-actions.xml` belongs to the web module in round 4. The entities have no model resources and no per-row permission checks.

Build with `./gradlew build` and deploy with `./gradlew deploy` (needs network access to `repository-cdn.liferay.com`; the target DXP update level is `liferay.workspace.product` in `gradle.properties`, and everything else about the target platform derives from it). Modules compile to Java 8 bytecode, pinned in the root `build.gradle` so bnd does not stamp a JDK 11+ `osgi.ee` requirement onto a bundle meant to resolve on a JDK 8 install.

Two conventions the api module already fixed and later modules must follow: package versions come from `packageinfo` resource files, not `package-info.java` annotations; and the configuration interface (impl module) uses Liferay's `@Meta.OCD`/`@Meta.AD` with `@ExtendedObjectClassDefinition` for per-virtual-instance scoping, whose `deflt` is always a `String` — so its defaults cannot reference the typed constants in `SearchEvalLoggerConstants` and must be kept in sync with them by review.

`DESIGN.md` is the source of truth for this project. Read it in full before implementing anything — it is long but every section is load-bearing (numbered design decisions D1–D8, architecture, data model, configuration, export format, and a table of open empirical checks EC-1–EC-13 that must be validated against a running Liferay instance). Do not summarize or work from partial recall of it; re-read the relevant section when in doubt, since decisions reference each other (e.g., 3.2 references D2, 4.3 references D3).

## What this project is

A Liferay DXP 7.4 OSGi plugin that passively logs `(query, result[])` search interaction records for offline relevance evaluation. It intercepts the public `Searcher` service (never connector internals, never `portal-impl`), filters out non-user-originated traffic, asynchronously persists admitted events via Message Bus, and lets an admin export a time-ranged JSONL archive. See `DESIGN.md` §1–2 for full scope and the eight settled design constraints (D1–D8) that any implementation must satisfy — in particular: never modify the outgoing `SearchRequest` (D2), never persist raw user identifiers (D3), never re-query to deepen capture (D4), and export is always a manual admin action (D5).

## Planned architecture (from DESIGN.md §3.5)

Single Liferay Workspace project, one codebase for both base DXP and LES (no branching by subscription tier):

```
modules/
  search-eval-logger-api/        Shared interfaces and value objects
  search-eval-logger-service/    Service Builder, entities, persistence
  search-eval-logger-impl/       Searcher wrapper, admission filter, listener, purge
  search-eval-logger-web/        Admin portlet (config + export)
```

Key mechanisms to know before touching any of these:
- **Interception**: an OSGi component wraps `Searcher` via a higher `service.ranking` (§3.1). Failure isolation is non-negotiable — no logging failure may ever propagate into the search response.
- **Admission filter**: synchronous, cheap, allowlist-based (§3.2) — admits only requests with non-empty user keywords (plus suggestion/exclusion/sampling checks). This is the plugin's main defense against logging internal Liferay traffic.
- **Async dispatch**: Message Bus serial destination, fire-and-forget, drop-under-backpressure rather than block or queue unboundedly (§3.3).
- **Retention purge**: batched deletes on a recurring job, never one large `DELETE` (§3.4).
- **Data model**: two tables, `SEL_SearchEvent` and `SEL_SearchHit`, joined by `searchEventUuid` (§4). Every text column needs an explicit `max-length` in `portlet-model-hints.xml` or must be a `Clob` — Service Builder's default `VARCHAR(75)` silently truncates otherwise.
- **Export**: must stream (`ActionableDynamicQuery` or raw JDBC cursor → JSONL → `ZipOutputStream`) — never load the full dataset into memory. JSONL, not CSV, because snippets contain quotes/newlines/highlight markup. See §6 for exact archive layout and `manifest.json` contents.

## Before implementing a component

Check `DESIGN.md` §7 (Open Questions and Empirical Checks) for any EC item relevant to what you're building. EC-1 is blocking — it must be confirmed (does the Search Results widget actually route through `Searcher`?) before the interception approach is trusted. Several other ECs (facet extraction paths, suggestion-traffic classification, ThreadLocal availability) directly determine what a given code path can and cannot reliably read — don't assume a field is available without checking the relevant EC.

## Available user-level agent configs

This machine has a Codex CLI config (`~/.codex`) and a Gemini CLI config (`~/.gemini`) that have not been imported into Claude Code. Run `/import` to see what's importable (MCP servers, slash commands, subagents, skills, instructions).
