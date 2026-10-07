# Security Review

This document is written to be forwarded unedited to whoever has to approve
installing this plugin on a production Liferay instance. Every claim below
names the `DESIGN.md` section, class, or end-to-end (e2e) test case it comes
from, so a reviewer can verify it against the source rather than take it on
faith. It makes no claims beyond what is referenced.

## 1. What this is

Search Eval Logger is a Liferay DXP OSGi plugin that passively records
`(query, result[])` pairs from user-originated searches, so relevance can be
evaluated offline against real traffic (`DESIGN.md` §1). It wraps Liferay's
public `Searcher` service, filters out non-user traffic before anything is
written, and persists admitted events asynchronously (`DESIGN.md` §3). An
administrator exports a time-ranged archive manually; nothing leaves the
instance on its own (`DESIGN.md` §6, D5).

## 2. Network behaviour

**No outbound requests to TensorOpt, from the server or from the browser
(D9).** The plugin never initiates a network call to TensorOpt or to any
other third party. The only network-facing things it renders are two static
`<a href target="_blank">` anchors pointing at TensorOpt's website
(`DESIGN.md` §10); nothing else in the plugin's markup or code touches an
external address, apart from the notification email described next.

**The one outbound call this plugin does make: notification email, through
the portal's own mail server (D9's explicit carve-out).** `EmailDeliveryImpl`
calls `MailServiceUtil.sendEmail` to deliver the collection-started, stall,
and readiness notifications (`DESIGN.md` §3.6) as email, in addition to the
website notification. This is a real network call, but D9 names it
explicitly as not being egress to TensorOpt: it goes through the portal's
*own* configured mail server, to the portal's *own* users, never to
TensorOpt or any address the plugin invented, and the body carries only an
internal admin-screen link — the stall email carries no link at all. Two
separate pieces of portal configuration are involved, and this plugin's own
code touches only one of them: `EmailDeliveryImpl._from` reads just the
*From name and address* via `PrefsPropsUtil` (`PropsKeys.ADMIN_EMAIL_FROM_NAME`/
`ADMIN_EMAIL_FROM_ADDRESS` — Instance Settings > Email > Email Sender, per
company). The *SMTP server itself* — host, port, credentials — is a separate
setting this plugin never reads (Instance Settings > Email > Mail Settings,
the `mail.session.mail.smtp.*` properties); `MailServiceUtil.sendEmail`
resolves and uses that mail session internally, inside
`com.liferay.mail.service.impl.MailServiceImpl`, confirmed by decompiling
the shipped `portal-impl.jar`. If no per-company value is set there,
`MailServiceImpl` falls back to the portal-level `mail.session.*` properties
(portal properties, editable system-wide under Server Administration > Mail)
— also confirmed by decompiling `PrefsPropsImpl.getString`, which resolves a
company preference against `PropsUtil.get(key)` as its default. Either way,
the server is one this portal's own administrators configured, never one
this plugin names. Delivery is also gated per recipient by their own
notification preference (`EmailDeliveryImpl#isWanted`, backed by
`UserNotificationManagerUtil.isDeliver`), decided by `CollectionNotifier`.
See `DESIGN.md` §3.6 and EC-15; the preference gate is exercised by e2e case
`email-delivery-preference`.

How this is checked:

- `ZeroEgressTest` (`modules/search-eval-logger-web/src/test/java/ai/tensoropt/sel/web/internal/egress/ZeroEgressTest.java`)
  statically scans every `.java` file in `search-eval-logger-web` and
  `search-eval-logger-impl` for an import or call capable of making an
  outbound connection (`java.net.http.*`, `HttpURLConnection`, `Socket`,
  common HTTP client libraries, Liferay's own `HttpUtil`/`Http`), and every
  `.jsp`/`.js` file under `search-eval-logger-web`'s `META-INF/resources` for
  a `fetch`/`XMLHttpRequest` call whose target is a literal external address
  rather than a server-rendered portlet URL.
- e2e case `funnel-zero-egress` (`DESIGN.md` §10, `checks.zero_egress`)
  renders the admin screen on a live instance and asserts that every vendor
  token on the page sits inside an anchor `href`, and that no element the
  browser fetches on its own (`<img>`, `<script>`, `<link>`, etc.) points at a
  host other than the portal's own.
- e2e case `funnel-links` (`checks.funnel_links`) asserts the structure and
  parameters of both anchors directly (below).

**What a click reveals (`DESIGN.md` §10.5).** Only that someone clicked, plus
whatever the clicked link carries. The collection-start link carries the date
collection started, at day granularity, and two UTM tags
(`utm_source=liferay-plugin`, `utm_medium=admin-screen`); the export-complete
link carries the two UTM tags alone. Neither carries a hostname, instance ID,
company name, plugin version, row count, coverage figure, or date range
(`DESIGN.md` §10.1, §10.3; `EvaluationServiceLinks` in
`search-eval-logger-web`). Both anchors carry `rel="noopener noreferrer"`, so
the new tab gets no handle on the admin screen's window and the browser sends
no `Referer` header at all — without it, the browser would send this admin
screen's own origin, which is often an internal hostname. Asserted by e2e
cases `funnel-links` and `signup-banner`, both through the shared
`_assert_new_tab_anchor` helper in `e2e/harness/checks.py`, which checks
`noopener` and `noreferrer` independently.

**Both links can be turned off.** The **Show evaluation service links**
setting (`@Meta.OCD`, `DESIGN.md` §5, §10.4) hides both anchors; an
administrator can remove them without forking the plugin.

## 3. What is stored

Two tables, `SEL_SearchEvent` and `SEL_SearchHit`, joined by
`searchEventUuid` (`DESIGN.md` §4). `SEL_SearchEvent` holds the query text
(capped, default 2000 characters, with a `queryTruncated` flag so a cap is
never silent — `DESIGN.md` §4.5), locale, scope, requested asset types,
applied facets, an audience type, a cohort hash, and pagination context.
`SEL_SearchHit` holds rank, score, document UID, entry class, and whatever
fields from the configured whitelist (`title` and `snippet` by default,
`DESIGN.md` §5) were already present in the response.

**No raw user identifiers (D3).** The only per-user signal stored is
`cohortHash`, a salted hash of the user ID read directly from the search
context, plus `audienceType` (`GUEST`/`AUTHENTICATED`) (`DESIGN.md` §4.3).
**The salt rotates on a configurable schedule, weekly by default**
(`CohortSaltRegistry`), held in memory only and never exported; superseded
salts are discarded, which caps how long a `cohortHash` stays linkable to one
rotation window rather than the full retention window (`DESIGN.md` §4.3).

**Captured fields are an explicit whitelist** (`title`, `snippet` by
default), configurable per installation, never "whatever the response
contains" (`DESIGN.md` §5). Document bodies are not captured **by default**
— see §4 below for what the whitelist does and does not prevent.

## 4. What is never captured or changed

- **The outgoing search request is never modified (D2).** The plugin reads
  the response the caller already produced; it never adds highlighting,
  widens a query, or changes behavior to capture more.
- **Capture depth never triggers a second query (D4).** What is logged is
  `min(K, hits returned)` from the one response already produced; the plugin
  never re-queries to deepen capture.
- **Only what is already in the response is captured (D8).** Direct
  consequence of D2: if the caller did not request a field (for example, a
  title or a highlighted snippet), it is simply absent from the captured row,
  with no fallback to a stored content or description field (`DESIGN.md`
  §4.4).
- **Document bodies are not captured by default, but the whitelist is not
  restricted to safe fields.** The default whitelist is `title` and
  `snippet` (`DESIGN.md` §5). Adding a body field — `content`, for example —
  to the **Captured Fields** setting captures it like any other configured
  name, whenever the response already carries it: `SearchEventCaptor`
  (`_captureHit`) reads every name in `capturedFieldNames()` from the
  `Document` with no distinction between a short field and a body field, and
  stores whatever it finds in `extraFields` (D8 bounds *whether* a field is
  there, not *which* field names are safe to ask for). This is a change the
  administrator has to make deliberately and can see in Configuration; it is
  not something this plugin does on its own with the default settings.

## 5. Retention and purge

A single recurring job deletes events and hits older than the configured
retention window (90 days by default), as two set-based `DELETE` statements
in one transaction, scoped by `companyId` so a purge never crosses virtual
instances (`DESIGN.md` §3.4). Measured on a corpus of 1,000,000 events and
10,000,000 hits, deleting a 400,000-event backlog took about 13 seconds.

**What is and is not confirmed about this job.** The delete logic itself —
correctness, and the timing above — was exercised directly, by invoking it
rather than by waiting for the scheduled trigger. The scheduler
configuration is confirmed registered at the right time of day
(`RetentionPurgeSchedulerJobConfiguration`). Whether Liferay's scheduler
actually fires the job at that time on its own, in production, has not been
observed (`DESIGN.md` §7, EC-7); see §9 below.

## 6. Export

**Manual only (D5).** An administrator opens the plugin's own Control Panel
screen, picks a date range, and runs the export as a background task; the
resulting archive is offered as a download. Nothing is transmitted anywhere
automatically (`DESIGN.md` §6.1).

**Gated by a dedicated permission, granted to nobody by default.** The
`EXPORT` action is a separate resource permission
(`modules/search-eval-logger-web/src/main/resources/resource-actions/default.xml`)
with no site-member or guest defaults, so exporting has to be granted
deliberately, independent of general portal administration. Both the trigger
(`ExportMVCActionCommand`) and the download (`SearchEvalLoggerPortlet#_serveArchive`)
check it, through the shared `SearchEvalLoggerPortletPermission`. Checked by
e2e cases `export-permission-refused` (a user without the permission cannot
start an export) and `export-foreign-download` (the download URL cannot be
redirected to another background task's attachment).

**Archive contents** (`DESIGN.md` §6.2): `events.jsonl` (one JSON object per
line, a search event with its hits nested), `manifest.json` (export range,
counts, plugin configuration, admission counters, per-field coverage rates),
and `README.md` (the structural caveats in plain language).

**Vendor-neutral regardless of who evaluates it.** Neither link, nor the
word "tensoropt", nor a UTM tag appears anywhere inside the archive
(`DESIGN.md` §10.4). Checked by e2e case `export-archive-vendor-neutral`
(`checks.archive_is_vendor_neutral`), which scans every archive entry in
1 MiB chunks with overlap so a token straddling a chunk boundary is still
caught.

## 7. Operational impact

**Dispatch is asynchronous and fire-and-forget.** The wrapper
(`LoggingSearcher`) publishes to a dedicated Message Bus destination and
returns immediately; the sender never waits for the listener
(`SearchEventPersistenceMessageListener`) to finish (`DESIGN.md` §3.3).

**Under load, the plugin drops log rows rather than queue unboundedly or
block the search path.** Message Bus's own backpressure controls (max queue
size, thread pool bounds) are configured to drop; drops are counted and
reported in the export manifest, never silently lost (`DESIGN.md` §3.3). e2e
case `queue-overflow` (`checks.queue_overflow`) blocks the persistence
listener, drives 2,600 searches from 16 clients through a 2,000-event queue,
and confirms every one of the 598 that overflowed is counted once as dropped
while search itself keeps answering.

**A logging failure never fails a search.** Every path from dispatch inward
is isolated so that a full queue, an unavailable database, a serialization
error, or missing request context degrades to logging nothing, never to a
broken search response (`DESIGN.md` §3.1).

**The restart requirement.** Liferay's own search consumers bind `Searcher`
once, with a static reference that does not rebind when a higher-ranked
wrapper registers later. Installing this plugin onto a running portal
registers the wrapper correctly but leaves it uncalled — collection records
nothing, with no error anywhere — until the portal restarts, and the same is
true after every redeploy of `search-eval-logger-impl` (`DESIGN.md` §3.1,
EC-3). The plugin detects this state through the OSGi service registry
(`SearchInterceptionStatusImpl`) rather than leaving it to be discovered as
an empty export, and shows it as a warning banner on its own admin screen.

## 8. Data protection

Once enabled, the plugin creates a table of user-submitted text on a
production system. Whether that constitutes personal data is the operator's
own determination; this document takes no position on it. If it does, handing
an export to an external evaluator makes that party a processor, and the
appropriate instrument is a data processing agreement, not an NDA — an NDA
addresses confidentiality between the parties, but it does not establish a
lawful basis for the transfer (`DESIGN.md` §8).

No query-text redaction is performed in this version (D6): target workloads
(document, web content, knowledge-base search) are expected to be
predominantly non-PII, and redaction is deferred rather than built
speculatively ahead of a concrete objection (`DESIGN.md` §8).

## 9. What is not verified

This plugin has been exercised end to end against one DXP 2025.Q1.27 LTS
instance on PostgreSQL. The following are explicitly **not** verified, and
this section does not soften any of them:

- **EC-5 — the headless Search API** (`/o/search/v1.0/...`). Blocked by
  feature flag `LPS-179669`, which could not be opened in the test
  environment. Code inspection says the endpoint resolves `Searcher` through
  the registry and would inherit EC-3's restart constraint, but that is
  reasoning, not a measurement (`DESIGN.md` §7, EC-5).
- **EC-6 — Liferay Enterprise Search (Blueprints) interception.** Not
  measured; needs an LES instance (`DESIGN.md` §7, EC-6).
- **EC-9 — DXP Cloud.** Never deployed there; verified only against a
  self-managed DXP 2025.Q1.27 LTS instance (`DESIGN.md` §7, EC-9).
- **EC-13 — Blueprint identifier capture.** Blocked by the same dependency as
  EC-6 (`DESIGN.md` §7, EC-13).
- **The runtime half of EC-14 — whose identity a notification resolves to.**
  Delivery itself is confirmed; whether `PrincipalThreadLocal` reliably
  carries the enabling administrator inside the configuration listener that
  supplies it has not been observed, only reasoned about (`DESIGN.md` §7,
  EC-14). It fails safe: an unresolved identity routes notifications to the
  instance administrators instead.
- **Email delivery (EC-15).** The preference gate that decides whether an
  email is sent is confirmed on a running instance. An email actually
  arriving in an inbox is not: the e2e stack has no SMTP sink to observe it
  (`DESIGN.md` §7, EC-15).
- **Clusters.** Everything has run on a single node. The collection cycle's
  mutators are synchronized per company within one JVM (`CollectionCycleStatusImpl`,
  `DESIGN.md` §3.6); a cluster-wide "exactly once" for the collection-started
  notification is explicitly not claimed, only a within-node one, and that
  has never been tested against a second node.
- **Databases other than PostgreSQL.** The export's streaming behavior was
  measured at the JDBC level against MySQL and MariaDB drivers as well, but
  the plugin as a whole — capture, persistence, purge, export together — has
  only run against PostgreSQL (`DESIGN.md` §6.1, README "Database").
- **EC-7 — the scheduler actually firing a job.** Both the purge and the
  daily collection-cycle check are confirmed scheduled at the right time of
  day, and both jobs' logic is exercised directly, but no test has waited
  until the scheduled time to observe Liferay's own scheduler fire either one
  (`DESIGN.md` §7, EC-7; README "Status", "Not verified").
- **EC-10 — how much of a production instance's traffic is internal.** The
  admission filter's keyword condition was measured only on an otherwise idle
  instance (18 searches observed, 8 removed by the keyword condition), where
  the denominator is dominated by test searches rather than real traffic.
  Whether that ratio holds, is better, or is worse on a busy production
  instance is not known (`DESIGN.md` §7, EC-10).

This plugin does not claim reproducible builds.

## 10. License and rebuilding from source

Apache License 2.0 (`DESIGN.md` §9, `LICENSE`). The plugin is source-available
in this repository specifically so a security reviewer can read it rather than
rely on a marketplace listing's review (`DESIGN.md` §9). See `README.md`
"Building" for the exact prerequisites and commands to rebuild every module
from source.
