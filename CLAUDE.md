# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Engineering principles

Every change, and every review, is held to these:

1. **YAGNI.** If it does not need to exist, remove it: unused constants, speculative options, abstractions with one caller and no second in sight.
2. **DRY.** If the codebase already does it, reuse that rather than writing a second copy. Two copies drift, and in this project drift is usually silent (see the column-width constants and the `@Meta.AD` defaults below, the two duplications kept on purpose).
3. **SOLID.** One reason to change per class (SRP); extend by adding, not by editing what works (OCP); a subtype must honour its supertype's contract (LSP); narrow interfaces over wide ones (ISP); depend on OSGi service interfaces, not implementations (DIP).
4. **No unnecessary dependencies.** If the JDK or the Liferay API already provides it, use that. A new third-party jar needs a reason that neither can meet.
5. **Succinct, readable code.** If it fits on one line, it is one line. No ceremony that does not carry meaning.
6. **Simple, minimal solutions that work.** Prefer the smallest change that solves the problem and is verified, over a general one that might.

## Project status

**All four modules implemented and building together; nothing validated against a running instance.** The api, service, impl and web modules exist, and `./gradlew build` from the repo root builds all four in one pass. Collection (§3, §5) and export (§6) are implemented end to end.

What is *not* done: every empirical check in DESIGN.md §7 is still open except EC-1 and EC-3. **EC-1 is confirmed:** the Search Results widget does route through `Searcher`, and events are captured end to end against a real DXP instance. **EC-3 is resolved with a constraint:** the `service.ranking` override only wins for consumers that bind after the wrapper registers, and Liferay's search consumers use static reluctant references, so the portal must be restarted after installing **and after every redeploy of the impl bundle** or nothing is collected at all — silently. That is detected (`SearchInterceptionStatus`) and surfaced in the log and the admin screen. What remains open: EC-2 (are suggestions classifiable), EC-11 (which ThreadLocals are populated) and EC-12 (where facets are readable) each decide how much of the captured data is real rather than empty. EC-4 is partly answered: title and highlighted snippets do come back on the widget path, the latter via `SearchHit#getHighlightFieldsMap()` rather than any field inside the `Document`. Those are open by design, not gaps to close from a desk. The capture path is proven end to end; the export path has still never been run against real data.

### First things to know

- **Build**: `./gradlew build` from the root, on **JDK 17 or 21**. JDK 11 cannot build this: DXP 2025.Q1 LTS ships Java 17 bytecode (class file major 61), and an 11 compiler rejects the target platform jars outright with "class file has wrong version 61.0". Modules compile to Java 17, pinned in the root `build.gradle`, so the bundles advertise `osgi.ee=JavaSE 17`. Needs network access to `repository-cdn.liferay.com`. Root `./gradlew clean` fails without Docker (the workspace plugin's `:stopDockerContainer`); clean per module instead.
- **Target platform**: `liferay.workspace.product=dxp-2025.q1.27-lts` in `gradle.properties` is the single source — the target platform version, bundle URL and Docker image all derive from it, so nothing else is pinned. Two moves so far, both forced rather than cosmetic: u92 to u112, because Service Builder 1.0.510 generates `Snapshot`-based `*Util` classes that u92's portal-kernel lacks; then u112 to 2025.Q1.27 LTS, which brought the Java 17 floor above. 2025.Q1 is still Tomcat 9 and `javax.*`, so it needed no namespace work — 2026.Q1 and later would. If the product key moves again, re-run a full build before assuming the generated code still compiles, and re-check the bytecode level of the new artifacts before assuming the build JDK still works.
- **Naming collision**: the generated model type `ai.tensoropt.sel.model.SearchHit` collides by simple name with Liferay's `com.liferay.portal.search.hits.SearchHit`. Any code touching both must fully qualify one; the impl and web modules both do.
- **Generated code**: `service.xml` and `portlet-model-hints.xml` are the hand-authored source of truth. Everything Service Builder emits (model, `*LocalService`, persistence, `META-INF/sql/*.sql`) is committed and must never be hand-edited — re-run `./gradlew :modules:search-eval-logger-service:buildService` after changing either file. `buildService` rewrites `portlet-model-hints.xml` and strips its XML comments, so comments belong in `service.xml`, which it preserves.

### Module boundaries

- **api** holds what more than one bundle needs: the enums, `SearchEvalLoggerConstants`, `SearchEvalLoggerConfiguration` (the `@Meta.OCD` interface — it lives here, not in impl, because the web module reads the same settings the collector applies), `SearchEvalLoggerStatistics`, and the generated model and service interfaces.
- **service** is the schema and persistence, nothing else.
- **impl** is entirely under `ai.tensoropt.sel.internal` and exports nothing.
- **web** owns the export: a `BackgroundTaskExecutor` that streams a JDBC cursor (`SearchEventLocalService.forEachExportRow`) → JSONL → `ZipOutputStream` into a temp file, the manifest and archive README builders, the admin portlet, and `resource-actions/default.xml` defining the dedicated `EXPORT` permission (granted to nobody by default, and checked on both the trigger and the download). It deliberately has **no** configuration screen: `@Meta.OCD` with `@ExtendedObjectClassDefinition(scope = COMPANY)` already yields Liferay's own System Settings UI, scoped per virtual instance.

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

A Liferay DXP OSGi plugin (currently targeting DXP 2025.Q1 LTS; designed against 7.4, whose public search API it still uses unchanged) that passively logs `(query, result[])` search interaction records for offline relevance evaluation. It intercepts the public `Searcher` service (never connector internals, never `portal-impl`), filters out non-user-originated traffic, asynchronously persists admitted events via Message Bus, and lets an admin export a time-ranged JSONL archive. See `DESIGN.md` §1–2 for full scope and the eight settled design constraints (D1–D8) that any implementation must satisfy — in particular: never modify the outgoing `SearchRequest` (D2), never persist raw user identifiers (D3), never re-query to deepen capture (D4), and export is always a manual admin action (D5).

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
- **Export**: must stream (raw JDBC cursor over the event/hit join → JSONL → `ZipOutputStream`) — never load the full dataset into memory. JSONL, not CSV, because snippets contain quotes/newlines/highlight markup. See §6 for exact archive layout and `manifest.json` contents.

## JSPs compile at render time, so the build cannot vet them

`./gradlew build` never compiles `view.jsp` or `init.jsp`; Jasper does, on first
render. A green build therefore says nothing about them, and the failure surfaces
as "Search Eval Export is temporarily unavailable" with the real cause only in the
log. The 2025.Q1.27 retarget hit exactly this: `BackgroundTaskCreateDateComparator`'s
`(boolean)` constructor became private in favour of a static `getInstance(boolean)`,
which the Java build could not see. After any platform change, render the admin
screen once and check the log before believing the upgrade is clean.

## Before implementing a component

Check `DESIGN.md` §7 (Open Questions and Empirical Checks) for any EC item relevant to what you're building. EC-1 and EC-3 are now answered (see §7). The one that bites during manual testing: after deploying onto a running portal the wrapper is registered but never called, because Liferay's search consumers hold static reluctant `@Reference`s and do not rebind. Restart the portal, or the tables stay empty with no error anywhere. This applies to **every** `gradlew deploy` of the impl bundle, not just the first install — the loop is deploy, restart, then test, and skipping the restart makes a working change look broken. Several other ECs (facet extraction paths, suggestion-traffic classification, ThreadLocal availability) directly determine what a given code path can and cannot reliably read — don't assume a field is available without checking the relevant EC.

## Available user-level agent configs

This machine has a Codex CLI config (`~/.codex`) and a Gemini CLI config (`~/.gemini`) that have not been imported into Claude Code. Run `/import` to see what's importable (MCP servers, slash commands, subagents, skills, instructions).
