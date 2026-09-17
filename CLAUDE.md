# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

**All four modules implemented and building together; nothing validated against a running instance.** The api, service, impl and web modules exist, and `./gradlew build` from the repo root builds all four in one pass. Collection (§3, §5) and export (§6) are implemented end to end.

What is *not* done: this has never been deployed to a real Liferay, or run at all. Every empirical check in DESIGN.md §7 is still open, EC-1 above all — if the Search Results widget does not route through `Searcher`, the interception approach needs rework and nothing downstream of it matters. EC-2 (are suggestions classifiable), EC-4 (which fields the response actually carries), EC-11 (which ThreadLocals are populated) and EC-12 (where facets are readable) each decide how much of the captured data is real rather than empty. Those are open by design, not gaps to close from a desk. Treat the code as a well-formed hypothesis, not as working software.

### First things to know

- **Build**: `./gradlew build` from the root. Any JDK Gradle 8.5 supports will do; modules compile to Java 8 bytecode, pinned in the root `build.gradle` so bnd cannot stamp a JDK 11+ `osgi.ee` requirement onto a bundle meant to resolve on a JDK 8 install. Needs network access to `repository-cdn.liferay.com`. Root `./gradlew clean` fails without Docker (the workspace plugin's `:stopDockerContainer`); clean per module instead.
- **Target platform**: `liferay.workspace.product=dxp-7.4-u112` in `gradle.properties` is the single source — the target platform version, bundle URL and Docker image all derive from it, so nothing else is pinned. It started at u92 and had to move: Service Builder 1.0.510 generates `Snapshot`-based `*Util` classes that u92's portal-kernel does not have. If the product key moves again, re-run a full build before assuming the generated code still compiles.
- **Naming collision**: the generated model type `com.tensoropt.search.eval.logger.model.SearchHit` collides by simple name with Liferay's `com.liferay.portal.search.hits.SearchHit`. Any code touching both must fully qualify one; the impl and web modules both do.
- **Generated code**: `service.xml` and `portlet-model-hints.xml` are the hand-authored source of truth. Everything Service Builder emits (model, `*LocalService`, persistence, `META-INF/sql/*.sql`) is committed and must never be hand-edited — re-run `./gradlew :modules:search-eval-logger-service:buildService` after changing either file. `buildService` rewrites `portlet-model-hints.xml` and strips its XML comments, so comments belong in `service.xml`, which it preserves.

### Module boundaries

- **api** holds what more than one bundle needs: the enums, `SearchEvalLoggerConstants`, `SearchEvalLoggerConfiguration` (the `@Meta.OCD` interface — it lives here, not in impl, because the web module reads the same settings the collector applies), `SearchEvalLoggerStatistics`, and the generated model and service interfaces.
- **service** is the schema and persistence, nothing else.
- **impl** is entirely under `com.tensoropt.search.eval.logger.internal` and exports nothing.
- **web** owns the export: a `BackgroundTaskExecutor` that streams `ActionableDynamicQuery` → JSONL → `ZipOutputStream` into a temp file, the manifest and archive README builders, the admin portlet, and `resource-actions/default.xml` defining the dedicated `EXPORT` permission (granted to nobody by default, and checked on both the trigger and the download). It deliberately has **no** configuration screen: `@Meta.OCD` with `@ExtendedObjectClassDefinition(scope = COMPANY)` already yields Liferay's own System Settings UI, scoped per virtual instance.

### Decisions that look wrong until you know why

- The single-transaction write of one event plus its hits lives in the impl module's Message Bus listener and opens its transaction through `TransactionInvokerUtil`, **not** through `@Transactional`. Only Service Builder services are proxied for that annotation, so on a plain component it would compile, read as correct, and silently do nothing.
- `@Meta.AD`'s `deflt` is a `String` whatever the setting's type, so the eleven defaults in `SearchEvalLoggerConfiguration` duplicate the typed constants in `SearchEvalLoggerConstants` and are kept in sync by review. Change one, change the other.
- Package versions come from `packageinfo` resource files, not `package-info.java` annotations, so the api module compiles without OSGi annotation jars.
- The impl module truncates every string to its column width before persisting, using private constants that mirror `portlet-model-hints.xml`. Those two must move together; nothing enforces it.
- `SearchEvalLoggerStatistics` counters are **process-wide and reset on restart**. They cannot be attributed to an export's time range, and the manifest says so explicitly rather than presenting them as a figure for the period.
- The admission filter resolves the request origin itself, after the keyword condition passes, and returns it for reuse. Resolving earlier would make every internal keyword-free search pay for it on the way to being dropped.

### Export invariants

Two things a change elsewhere could silently break: nothing in the export path may collect rows into a list (a full-window export is millions of hits, and §6.1 calls streaming a hard requirement), and per-field coverage must be counted while streaming rather than re-queried.

`DESIGN.md` is the source of truth for this project. Read it in full before implementing anything — it is long but every section is load-bearing (numbered design decisions D1–D8, architecture, data model, configuration, export format, and a table of open empirical checks EC-1–EC-13 that must be validated against a running Liferay instance). Do not summarize or work from partial recall of it; re-read the relevant section when in doubt, since decisions reference each other (e.g., 3.2 references D2, 4.3 references D3).

## What this project is

A Liferay DXP 7.4 OSGi plugin that passively logs `(query, result[])` search interaction records for offline relevance evaluation. It intercepts the public `Searcher` service (never connector internals, never `portal-impl`), filters out non-user-originated traffic, asynchronously persists admitted events via Message Bus, and lets an admin export a time-ranged JSONL archive. See `DESIGN.md` §1–2 for full scope and the eight settled design constraints (D1–D8) that any implementation must satisfy — in particular: never modify the outgoing `SearchRequest` (D2), never persist raw user identifiers (D3), never re-query to deepen capture (D4), and export is always a manual admin action (D5).

## Architecture (from DESIGN.md §3.5)

Single Liferay Workspace project, one codebase for both base DXP and LES (no branching by subscription tier):

```
modules/
  search-eval-logger-api/        Shared interfaces and value objects
  search-eval-logger-service/    Service Builder, entities, persistence
  search-eval-logger-impl/       Searcher wrapper, admission filter, listener, purge
  search-eval-logger-web/        Admin portlet (export; settings come from Configuration Admin)
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
