# End to end functional test (TO-84)

An unattended, Docker Compose driven functional test of the whole plugin. It
brings up a database and Liferay DXP, installs the plugin onto the running
portal, confirms the automatic rebind of DESIGN.md 3.1 (TO-140) picks up the
search entry points without a restart, forces a genuine bypass of those same
entry points, standing in for a rebind that failed, and confirms a restart is
still what clears that, creates content, runs real searches, generates a
scale corpus, then exercises and asserts on the retention purge, the export
archive and the funnel integration of DESIGN.md 10.

It is built for CI first. Every wait is bounded and named, teardown runs on
failure as well as on success, results come out as JUnit XML, and nothing in
it touches a container it did not create.

## Running it

`make test` runs it on every database, ending with an `.lpkg` install, and
`make e2e DB=mysql` on one; both are in the root `Makefile`. Directly:

```
e2e/run.sh                      # smoke tier, the default
e2e/run.sh --scale full         # 1,000,000 events and 10,000,000 hits
e2e/run.sh --keep               # leave the stack up afterwards for debugging
e2e/run.sh --no-build           # use modules/*/build/libs as they are
e2e/run.sh --database mysql     # MySQL instead of PostgreSQL; see "Databases"
e2e/run.sh --lpkg <path>        # also install this .lpkg as the last case
python3 e2e/selftest.py         # test the tests; no Docker, about a second
```

A run always destroys any stack left from a previous one before it starts, so
running it twice, or after a run that failed halfway, does the same thing both
times.

There is deliberately no flag to reuse a stack. A reused stack has the plugin
already installed, its admission counters already moved and its generated rows
already sitting at the primary keys the generator starts from, so the early
cases fail and the failures look exactly like product bugs. A debugging aid
that fails in ways indistinguishable from the thing it is debugging is worse
than not having one, and `--keep` already covers inspecting a stack after a
run.

Exit codes: `0` every case passed or failed as a registered known defect, `1`
something else failed, `2` the harness could not run at all (Docker missing, a
host port taken, no bundles and no way to build them).

Results land in `e2e/results`:

- `junit.xml`, for the CI system
- `report.txt`, the same thing for a person
- `logs/`, the Liferay and database container logs, collected on failure
- `export.zip` and `export-range.zip`, the archives the export cases produced

## Prerequisites

- Docker with Compose v2, and roughly 7 GB of memory available to it. The
  portal alone asks for a 2 GB heap and runs an Elasticsearch sidecar beside it.
- `python3`. Standard library only: no `pip install` step, deliberately, so a
  runner needs nothing prepared.
- Outbound network access to Docker Hub. On the first run that is about 2.4 GB
  for `liferay/dxp` plus the database image.
- With `--database mysql` only, outbound access to `repo1.maven.org` for MySQL
  Connector/J, once; see "Databases".
- Outbound network access to `repository-cdn.liferay.com`, but only when the
  harness has to build the bundles. If `modules/*/build/libs` already holds the
  four jars it uses them.

There is no dependency on anything in a developer's home directory. The cached
bundle archive under `~/.liferay/bundles` is not used and is not needed.

## What runs where, and why

### The Liferay image comes from Docker Hub

`liferay/dxp:2026.q3.8` is pullable anonymously, and the image is
published for both `linux/amd64` and `linux/arm64`. It is pinned here by digest
as well as by tag, because a tag on Docker Hub is mutable and a CI run whose
target platform changes underneath it is not a regression test.

An earlier reading of this said the image needed a Liferay subscription login.
That came from `docker manifest inspect` failing, which on an older Docker
client it does for any OCI image index, authenticated or not. Pulling works.

`SEL_E2E_LIFERAY_IMAGE` is not an option today; if the registry ever starts
asking for credentials, the fallback is to build an image from the bundle
archive `liferay.workspace.product` names and point `docker-compose.yml` at it.
That fallback is written down rather than shipped, because shipping an untested
path is worse than naming a tested one.

### The portal runs on a Development license

The image ships no activation key, and the portal logs `License registered for
DXP Development` on startup. Nothing the harness asserts depends on a licensed
feature: interception, capture, the purge, the export and the funnel links are
all base DXP. Blueprints run on this license too: EC-6 and EC-13 in DESIGN.md
section 7 were measured on this stack by hand for the widget-applied case.
The suite itself exercises the TO-141 Blueprint editor Preview exclusion and
one headless Blueprint-driven search (`blueprint-preview-excluded`).

### Collection is enabled from a file, and then proved

`liferay/files/osgi/configs/ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration.config`
is copied into Liferay Home before the first boot. The file name is the
`@Meta.OCD` id exactly. The configuration is declared
`@ExtendedObjectClassDefinition(scope = COMPANY)`, and Liferay resolves a
company scoped configuration by falling back to the system scoped record when
the company has none of its own, so this one file is the default every virtual
instance inherits.

A configuration file at the wrong pid, or carrying a scope suffix for a company
that does not exist, is accepted by Configuration Admin and then read by
nothing. So the harness does not trust the file to have landed. It proves it
twice: the admission counters on the plugin's own screen only move while
collection is enabled, and the export manifest reports the effective
configuration, which the test compares field by field against what the file
says.

**`.config` files must carry at most one comment line.** Felix's
`TypedProperties` parser rejects a file with a multi line comment block, with
`ERROR [TypedProperties:276] Multiple comment lines found`, and then applies
nothing from it. The first version of both config files here had a prose header
and was silently ignored. The rationale lives in this README instead.

### The daily jobs are triggered through the script console

The retention purge and the collection cycle check are both
`SchedulerJobConfiguration` jobs on a daily cron trigger (03:00 and 03:30
portal time respectively; this stack runs at UTC, so that is also what the
jobs and the table below mean by those times). A CI run cannot wait a day,
and neither job has, or should have, a trigger of its own for tests to call.

The harness uses Server Administration's Groovy script console, which is a
stock DXP administration feature, to look each service up in the OSGi registry
by name and invoke it reflectively. `search-eval-logger-impl` exports nothing,
so reflection is what makes its services reachable without exporting them. The
production bundles are exactly the ones that ship; the code under test is
entered the same way the scheduler enters it.

The console carries a CAPTCHA, and turning it off takes two settings.
`liferay/files/osgi/configs/com.liferay.captcha.configuration.CaptchaConfiguration.config`
sets `maxChallenges` to a negative value, which means never check. On its own
that lasts one script per session: every render of the Server Administration
screen, including the one that answers a script, calls
`CaptchaUtil.enforceCaptcha`, which stores a per-session override that wins over
`maxChallenges`. The second script of a session was therefore rejected, logged
`CAPTCHA text is null` at ERROR, and only got through on the harness's retry
with a fresh session. The portal property `captcha.enforce.disabled`, set in
`docker-compose.yml`, makes `enforceCaptcha` a no-op, so the configuration is
what decides. Both belong to the test portal and to nothing else, which is why
the stack binds every published port to `127.0.0.1` and is destroyed at the end
of a run.

One more property of the console is worth knowing before debugging it: Liferay
rotates the session's CSRF token across a portlet action, so an auth token read
once is good for exactly one action. The harness re-reads the token before each
script, which costs one page load. It does **not** sign in again before each
script to stay inside the CAPTCHA's one challenge-free check per session: that
would leave two mechanisms for one problem and no way to tell which was
working. The two settings above are the mechanism; `portal.py` says so too, and
its one retry with a fresh session is for a session invalidated underneath it,
not for the CAPTCHA.

### Scale data is written with SQL, in chunks

A million events cannot be produced by searching. `harness/generate.py` writes
them with `insert ... select` over `generate_series`, in chunks, so the write
is bounded and progress is reported.

Three properties of the generated rows are what make the later assertions mean
something:

- Hits take `companyId` and `createDate` from their event row rather than
  recomputing them, because DESIGN.md 3.4 has the purge delete events and hits
  as two independent statements. A drifting date on either side would leave the
  purge looking correct and the orphans invisible.
- Event timestamps straddle the retention cutoff. A purge that finds everything
  inside the window deletes nothing and passes; the assertion worth making is
  that it removes exactly what is outside and leaves the rest.
- Title and snippet coverage are fixed at 0.9 and 0.1, so the coverage rates the
  export manifest reports are checked against a ratio this generator chose
  rather than against themselves. The two are complementary, which is also what
  EC-4 observed on real data.

`Counter` is moved past the generated primary keys afterwards, so an instance
kept alive with `--keep` cannot later hand out a key that already exists.

Tiers: `smoke` (6,000 events, 60,000 hits) is the default and is what a pull
request should run. `medium` is 100,000 and 1,000,000. `full` is 1,000,000 and
10,000,000, and is opt in.

### Which tiers have actually been run

`smoke` and `medium` have both been run end to end against the stack this
directory describes. `full` has not, and the reason is disk rather than
anything about the code.

Measured at `medium`, with `--chunk 20000` so the generator's chunk loop ran
five times rather than once:

| | |
|---|---|
| Generation | 100,000 events and 1,000,000 hits in 9.2 s, plus 1.7 s to analyze |
| Purge | 49,989 events and 499,890 hits deleted in 1.18 s; a second run with nothing to delete, 77 ms |
| Export | 50,014 events and 500,110 hits, background task 10 s, 7.0 MB archive |
| Coverage reported | title 0.90001, snippet 0.10005, against a generator that wrote 0.9 and 0.1 |
| Whole run | about 3 minutes wall clock |

At that tier the `lportal` database holds 394 MB. Ten times the rows is
therefore around 3.9 GB, against 3.1 GB free on the Docker VM of the machine
this was built on, before counting write ahead log churn and the archive
being written twice. Running `full` there would have hit a full disk, and a
full disk produces a misleading failure rather than a measurement, so it was
not attempted.

**`--scale full` is therefore an unverified path.** What can be said is that
the code it runs is the same code the other two tiers run, with larger numbers
in three parameters, and that the medium tier figures extrapolate to roughly
the figures DESIGN.md 3.4 (the purge) and DESIGN.md 6.1 (the export) already
record from the developer's own instance (about 13 s to purge a large
backlog, about two and a half minutes to export 600,000 events).

Table size is not the only thing to budget for, and probably not the thing that
would stop it first. The export's query ends

```sql
order by e.createDate, e.uuid_, h.rank_
```

over the full join, which at the heavy tier is ten million rows carrying the
title and snippet columns. PostgreSQL will not sort that in `work_mem`; it
spills to temp files under `pgsql_tmp`, and the peak there is the width of the
sorted rows rather than the on-disk table size. Budget for it separately from
the 3.9 GB the tables need. A runner wanting to run this tier should have
**10 GB or more free on the Docker filesystem**, and raising `work_mem` for the
session would reduce the spill but not remove it.

Whether the sort is what stops it is itself unverified. It is the likeliest
candidate and it is recorded here so that whoever runs the tier first knows
where to look rather than reading a full disk as a defect in the exporter.

The smoke tier's event count is not arbitrary. Events spread evenly over the
day span, and the readiness check counts only those on or after a collection
start date set `readinessMinimumDays` back, after the purge has taken
everything past the retention window. 6,000 over 60 days leaves about 800 in
the last 8 days against a threshold of 500. Lowering `--events` below about
4,000 makes `funnel-readiness` unreachable, and the case says so when it
fails.

### Search overhead

`search-overhead` is the e2e suite's own check of DESIGN.md 3's "no
measurable impact on search latency" claim, run on whatever machine the
suite happens to be on. It flips the plugin's `enabled` setting through
`ConfigurationProvider.saveCompanyConfiguration`, the same API
`SearchEvalLoggerConfigurationRegistry` reads, run through the script
console the same way the daily jobs above are: every other configured
value is read off the live configuration first and written straight back,
so toggling `enabled` for this case never resets `captureDepth`,
`retentionDays` or anything else this run configured.

Searches run as a guest, interleaved in blocks (disabled, enabled,
disabled, ...) rather than disabled-then-enabled, specifically so machine
noise, a noisy neighbour on a shared CI runner, a GC pause, a moment of
disk contention, lands on both states about equally instead of all on
whichever one happens to run later. The case asserts the enabled median
is at most 10% over the disabled median; `scripts/loadtest.py` is the
actual measurement, with a bootstrap confidence interval, meant to be run
by hand on a quiet, otherwise idle machine (see its own section below).

**Noise risk.** A CI runner is not a quiet machine, and 10% is a coarse
gate chosen to tolerate that rather than to prove a tight bound: on a
busy shared runner a handful of searches can land slow for reasons that
have nothing to do with this plugin, and the case is sized (90 searches
per state, 6 blocks of 15, under about two minutes total) to keep the
runtime reasonable rather than to drive the gate's variance down further.
A failure here is worth re-running once before treating it as a
regression; a consistent failure across runs is the signal that matters.

### scripts/loadtest.py: the real overhead measurement

Where `search-overhead` is a coarse pass/fail gate, `scripts/loadtest.py`
(`make loadtest DB=... PORT=...`, same arguments as `make run`) is the
measurement: it expects a portal `make run` already started, refuses with
a clear message if none answers, toggles `enabled` the same way, warms
up, then runs interleaved blocks of a guest Search Results widget search
(`--blocks`, `--searches`, both configurable) and reports samples,
median and p90 per state, the overhead ratio, and a 95% confidence
interval for that ratio from a bootstrap over the per-request samples
(`--seed` makes a run reproducible). Run it on a quiet, otherwise idle
machine; a shared or busy one widens the interval without telling you
anything about the plugin.

### The export is driven through its own screen

The harness reads the export form's action URL off the rendered admin screen
rather than constructing it, which means the screen has to have rendered. That
matters here: JSPs compile at render time, so a green Gradle build says nothing
about `view.jsp`, and this is the step that would catch what the 2025.Q1.27
retarget caught by hand.

The archive is then downloaded through the same resource URL an administrator
would click, and validated against DESIGN.md 6.2: exactly three entries, every
manifest key present, one JSONL line per event, chronological order end to end,
nested hit counts matching the manifest, and per field coverage matching the
generator. `events.jsonl` is validated a line at a time, because a validator
that loaded a full window export would fail for the same reason 6.1 forbids the
exporter to.

### The funnel is asserted on structure, not on an address

`EvaluationServiceLinks` held `{{SIGNUP_URL}}` and `{{BOOKING_URL}}` as literal
placeholder tokens until the TO-113 release cut over to the real addresses
(`https://tensoropt.ai/dxp-sel-signup` and `https://tensoropt.ai/dxp-sel-booking`).
The test asserts what DESIGN.md 10 specifies around them, by structure rather
than by address, which is exactly what let it keep working across that cutover
without a rewrite: the collection start link carries `started` at day
granularity plus the two UTM tags and nothing else, the export complete link
carries the UTM tags alone, both are plain anchors with `target="_blank"` and
`rel` containing `noopener noreferrer`, and neither address nor any UTM tag
appears anywhere inside the export archive.

Readiness needs its two thresholds met. Rather than lower them, the harness
moves the stored `collectionStartDate` back past `readinessMinimumDays`,
through the same `PortletPreferencesLocalService` the plugin writes it with, and
relies on the scale corpus for the event count. The comparison being exercised
is then the one a real installation makes.

### Databases

`--database` chooses what the portal runs on. Each choice is one Compose file,
`docker-compose.<database>.yml`, which adds the database service and the
portal's JDBC settings to the neutral `docker-compose.yml`. Every case is the
same on all of them; the harness speaks each dialect where its own SQL differs.

| `--database` | Database | JDBC driver |
|---|---|---|
| `postgres` (default) | PostgreSQL 15.19 | PostgreSQL, shipped with DXP |
| `mysql` | MySQL 8.4.11 | MySQL Connector/J 9.4.0, with `useCursorFetch=true` |
| `mysql-mariadb-driver` | MySQL 8.4.11 | MariaDB, shipped with DXP |
| `mariadb` | MariaDB 11.4.13 | MariaDB, shipped with DXP |

The DXP image ships no Connector/J. For `mysql` the harness downloads the
pinned version from Maven Central into `.work/`, once, and checks its SHA-256
on every run before the stack starts.

The two MySQL drivers are both here because they behave differently where the
plugin depends on it. The export reads its rows through a cursor with a fetch
size (DESIGN.md 6.1). Connector/J ignores the fetch size unless the URL carries
`useCursorFetch=true`: without it the whole export is buffered in the portal's
heap, which is why the root README tells MySQL users to set it. The MariaDB
driver streams with no flag, against MariaDB and against MySQL alike. A green
run without the flag would not show this, because the smoke tier's export fits
in memory either way; it was established by reading the driver's result set
class during an export, on each of the three MySQL family configurations.

All four configurations pass every case. The harness's own SQL keeps
Liferay's mixed case table names (`UserNotificationEvent`, not
`usernotificationevent`): PostgreSQL folds an unquoted name to lower case and
MySQL on Linux does not.

## What it asserts, case by case

| Case | What it proves |
|---|---|
| `stack-up` | The stack boots, the portal serves, an administrator can sign in, and the product version and license mode are what was expected |
| `plugin-absent-baseline` | The SEL tables do not exist before installation, so their later presence means something |
| `install-onto-running-portal` | All four bundles start on a portal that is already serving, and Service Builder creates both tables and their indexes |
| `install-intercepts-without-restart` | TO-140: SearchConsumerRebinder rebinds the search entry points onto the wrapper automatically, off its own activation thread, with no restart; the registry shows the widget bundle on the wrapper, the admin screen shows no restart warning but does show the secondary-consumer info note, since service-layer consumers that activated before install are never rebound, the log carries the rebind's own INFO summary line and is otherwise clean, and the zero-result panel still renders its empty-log message, since nothing has searched yet |
| `ec3-bypass-detected` | DESIGN.md 3.1/EC-3: standing in for a rebind that failed, this case forces the entry points off the wrapper itself, from outside the plugin, through the OSGi runtime (disabling the rebinder, then bouncing `LoggingSearcher`), and confirms the plugin still says so on its screen, rather than leaving an empty table to be discovered; nothing is logged on this path, since the rebinder that would log it was disabled first |
| `stall-grace-period` | TO-112: the stall notification is suppressed while the cycle is within its first 24 hours, the false alarm the grace period exists to prevent |
| `stall-notification` | DESIGN.md 3.6 notification 2 (stall) fires once the grace period has passed, the notification reaches `UserNotificationEvent`, and it appears in the administrator's own notifications list, which is EC-14 delivery |
| `restart-clears-bypass` | The restart is what makes the entry points `ec3-bypass-detected` forced onto the portal's own searcher bind the wrapper again, and it also clears the secondary-consumer info note, since a restart moves every consumer, not only the entry points |
| `daily-jobs-scheduled` | After that restart, the retention purge and the collection cycle check are scheduled at 03:00 and 03:30 and then daily, not one day after the portal started |
| `content-and-searches` | Content created through the headless API becomes searchable, and searches run through the Search Results widget path EC-1 confirmed |
| `capture` | Admitted searches are persisted with their hits, the admission funnel is monotonic and adds up (admitted = persisted + dropped once settled), nothing is dropped at rest, `sourceType` is `WIDGET`, and no hit is orphaned |
| `search-overhead` | DESIGN.md 3: guest Search Results widget searches run in interleaved blocks with collection disabled and enabled; the enabled median must be at most 10% over the disabled median. A coarse gate, not a measurement; see "Search overhead" below |
| `blueprint-preview-excluded` | TO-141: a search from the Blueprint editor's Preview reaches the wrapper but is not logged; where the headless Search API is open, a Blueprint-driven search through it is logged with its Blueprint |
| `zero-result-panel-seed` | TO-135: deliberate zero-result searches, as a guest and as a signed-in user, exercising the panel's grouping rule: the ASCII case fold (merged) and the accented trio (kept apart); verified once the `zero-result-panel` case downloads the archive these searches landed in |
| `collection-start-recorded` | DESIGN.md 3.6: the cycle records the UTC day of the first persisted event |
| `collection-started-notification` | TO-112: exactly one "collection started" notification per cycle, fired from the persistence path rather than the daily job, none for a later event in the same cycle, and a new cycle re-arms it |
| `email-delivery-preference` | EC-15 (TO-112): `UserNotificationManagerUtil.fetchUserNotificationDefinition` finds the registered definition and `isDeliver` answers true for email for an administrator who never set a preference. Not evidence of delivery - the stack has no SMTP sink - only of the gate `CollectionNotifier` checks |
| `scale-data` | The corpus is written, and every hit's duplicated `companyId` and `createDate` match its event's |
| `retention-purge` | DESIGN.md 3.4: everything past the window goes, everything inside it stays, no orphan hits are left, and a run with nothing to delete finishes inside a loose bound that a delete no longer using its index would break |
| `funnel-readiness` | DESIGN.md 3.6 notification 3 (readiness), against the configured thresholds, with the notification stored, shown in the administrator's notifications list, and the banner rendered |
| `export` | DESIGN.md 6.1: the action starts a background task that completes and offers a download |
| `export-archive-name` | DESIGN.md 6.2 names the archive for the range that was requested, asserted as an exact string |
| `export-archive-contents` | DESIGN.md 6.2, entry by entry and key by key, including the effective configuration read back, and every id and count in the manifest a JSON number |
| `zero-result-panel` | DESIGN.md 6/TO-135: the zero-result panel's own figures against the acceptance recipes, over the whole retention window including every other case's rows, and the one-hour cache proven by emptying it and re-rendering |
| `export-range-is-honoured` | DESIGN.md 6.1: a narrow window exports exactly the rows that window holds, counted independently in the database |
| `export-jsonl-number-types` | DESIGN.md 6.2 types `total_hits`, `entry_class_pk` and each of `scope_group_ids` as JSON numbers |
| `export-archive-vendor-neutral` | DESIGN.md 10.4: no evaluation service link and no UTM tag inside the archive |
| `funnel-links` | DESIGN.md 10.1 and 10.3 |
| `funnel-zero-egress` | D9, as far as a rendered page can be inspected |
| `signup-banner` | DESIGN.md 10.1/10.2 (TO-112): the signup banner is shown under the same conditions as the link it replaces, and dismissing it through its own resource URL hides it and persists `signupBannerDismissed` |
| `export-foreign-download` | TO-91: the export's download URL, given another background task's id, does not serve that task's attachment, while its own id still serves the archive |
| `export-permission-refused` | DESIGN.md 6.1: a user who can open the screen but does not hold `EXPORT` is refused, on the screen and on a direct action post. See below for what counts as evidence there |
| `export-unbounded-range` | DESIGN.md 6.1: an export with either date left empty, in all three combinations, each one asserted against the rows its range holds, the range its manifest reports and the range line in its README |
| `queue-overflow` | TO-92: with `SEL_SearchEvent` locked so the listener cannot write, 2,600 searches from 16 clients all answer HTTP 200, the events past the queue's 2,000 are dropped, each counted once (dispatched equals persisted), and the funnel adds up once the lock is released. Search latency is recorded with the queue draining and with it full |
| `uninstall-and-reinstall` | TO-111/TO-140: with the four bundles removed from the running portal, search still answers, nothing is logged at ERROR beyond `UNINSTALL_TOLERATED`'s narrowly named rules (see below), and the data is untouched; reinstalled, without a restart, the automatic rebind clears the bypass again, and exactly N Search Results widget searches for a distinct term produce exactly N newly captured events through `PortletSharedSearchRequestImpl`, settling the benign bundle-level artifact with evidence rather than assertion for that one entry point component; the legacy `SearchPortlet` and search bar suggestions are not exercised here and rest on their own component DTOs and the bytecode finding that neither bundle acquires a `Searcher` outside Declarative Services, and the collection start date is unchanged |
| `lpkg-install` | TO-116, last, only with `--lpkg`: the jars removed and an `.lpkg` of the same jars dropped into the deploy folder, the way a Liferay Marketplace download is installed; after the restart Liferay itself requires for an `.lpkg`, the plugin's four bundles are Active and loaded from inside the `.lpkg`, interception is active, and searches are recorded |

## Reading the uninstall-and-reinstall case

Removing the plugin's bundles unregisters the wrapper and sets off a
Declarative Services cascade through every component actually bound to it
(`DESIGN.md` §3.1), measured once in CI at about 22 seconds (the
after-restart case, the normal production state once the plugin has been
installed through at least one restart); it varies.
During that window Liferay's own REST layer is expected to log transient
errors of its own that it is expected to recover from, so `assert_log_clean`
accepts exactly three, narrowly named shapes through
`checks.UNINSTALL_TOLERATED`, never an error whose trace runs through this
plugin's own code:

- `[PortletTracker: ... is already in use`, with no trace requirement: the
  new portlet instance registers before the old one is fully gone.
- A `FrameworkEvent ERROR` whose trace carries
  `com.liferay.portal.aop.internal.AopServiceManager`: AOP re-registration
  failing a tracker downstream of it on a service that is already gone.
  Which component it fails on varies run to run (Objects' own component
  activation and, separately, Liferay's own vulcan NPE further down the same
  cascade have both been seen). Of the 50 `FrameworkEvent ERROR` entries seen
  in the CI log, 49 carried both `AopServiceManager` and the vulcan NPE in
  their own trace; the fiftieth, `ObjectDefinitionDeployerImpl`'s own
  `ComponentException`, carried no vulcan NPE, but its trace still carried
  `AopServiceManager` (its registration of an AOP service set off the
  deployment whose component activation failed), which is the only reason
  the rule tolerated it too, and is also why the rule names the cascade
  rather than each victim.
- Aries' own Whiteboard logger, not Equinox's `FrameworkEvent` dispatcher,
  reporting `CxfJaxrsServiceRegistrator.rewire` failing on that same vulcan
  NPE (`NestedFieldsWriterInterceptor` seeing a null prototype-scoped
  resource): a line shape the `AopServiceManager` rule above cannot see,
  because it carries no `FrameworkEvent` marker at all. Added for TO-144
  after it failed a CI run (`mysql-mariadb-driver`, `dxp-2025.q1`) that a
  slower, Docker-in-Docker runner exposed and 24 local runs across three
  branches and four databases never did.

Tolerating those log lines is not the same as the whiteboard actually having
recovered, so the case also polls
`org.osgi.service.jaxrs.runtime.JaxrsServiceRuntime#getRuntimeDTO()`, through
the script console, for the counts of `failedApplicationDTOs`,
`failedResourceDTOs` and `failedExtensionDTOs`, both after the uninstall
settles and again after the reinstall, and asserts each count is no higher
than a baseline taken before the uninstall, rather than asserting the counts
are zero, since a stock portal can carry its own pre-existing failures. A
tolerance rule can never stand in for this positive check: it only says a
known, transient log line is not itself a failure, while this says the
whiteboard the cascade is expected to have disturbed came back. Whether it
actually recovers from the race that motivated the tolerance rule above is
still unconfirmed: the CI run that hit the race failed before this check
existed, and no run since, with the check in place, has hit it. The
reinstall-side poll normally passes on its first sample, because reinstalling
bounces no REST components on its own; it stays in place as a baseline
regression guard, not as evidence of a reinstall-time cascade. The
comparison is by count, not by which application, resource or extension
failed, so a baseline failure clearing while an unrelated new one appears at
the same count would still pass; that gap is accepted, not closed.

## Reading the EXPORT permission case

Until this case existed, the `EXPORT` permission had only ever been exercised
by an administrator who holds it, and refusing a user who does not was
untested. This case tests it, and the shape of the evidence matters because
the obvious reading of the result is wrong.

The direct POST to the export action **answers HTTP 200**. That is not a
refusal by itself and the case does not treat it as one: Liferay renders a
portlet action's `PrincipalException` as a page rather than as a status code,
so the status says nothing either way. What settles it is three things
together:

1. The user reached the plugin's own screen, proved by the admission counters
   table being on it. Without that check, an access denied page or a sign in
   form would satisfy every assertion below for the wrong reason.
2. The success message the action adds on its way out, "The export has
   started", is absent from the response.
3. No background task appeared, waited for over 20 seconds rather than read
   once.

Measured on the medium tier run: the screen said "You do not have permission
to export", offered no form, the POST answered 200 with no success message,
and the background task count stayed at 1. **The gate holds on both paths.**

One related thing this does not settle. The download is served through a
resource URL carrying a `p_p_auth` token, so a second user fetching an
administrator's download link can be refused by that token rather than by the
`EXPORT` permission. Asserting on it would not be evidence about the
permission, so the case does not.

Getting here needed one piece of setup worth knowing about: a user created
through `headless-admin-user` has an unverified email address, and Liferay
sends them to a verification screen instead of to the page they asked for. The
first version of this case read that screen's absence of an export form as a
refusal. `scripts.ACTIVATE_USER` clears the verification, the password reset
flag and the terms of use flag, so the user actually arrives.

## Known defects are a mechanism, not a paragraph

No case currently fails because of a defect in the plugin. The three the TO-84
run caught, D-1, D-2 and D-4, have all since been fixed. A case that does fail
because of a plugin defect is named in `checks.EXPECTED_FAILURES`, and
`Results` treats a named case that fails as an expected failure: it is reported
as skipped in the JUnit XML with the whole traceback kept in `system-out`, it
does not count toward the exit code, and **the suite exits 0 on exactly that
set**, so a gate can stay switched on while a defect is open.

The same case *passing* is recorded as a failure, so an entry cannot go stale:
either the defect was fixed and the entry should be retired, or the case
stopped testing what it claims to. That is how `export-unbounded-range`,
`export-jsonl-number-types` and `export-archive-name` left this list: TO-87
fixed D-1, TO-88 fixed D-2 and TO-90 fixed D-4, each case passed, the run
failed on the unexpected pass, and each entry was retired in the same change
as its fix.

This is a mechanism rather than a rule a person applies, because "expect
exactly N failures" is not a rule about the thing it sounds like. Kill the
database halfway through a run and those N become skips, since their
prerequisites never passed, and the run then shows zero failures and reads as
healthier than the baseline while being entirely broken. `selftest.py`
exercises that exact scenario.

Each case asserts what DESIGN.md says, and the plugin is the side that
disagrees. None of them is rewritten to expect the failure.

## Testing the tests

`selftest.py` runs 163 discrimination checks (at the time of writing) in about a second, with no Docker
and no portal. For each assertion the suite makes, it builds a good fixture,
mutates it in the way the assertion exists to notice, and fails if the
assertion accepts the mutation.

It exists because several of the original checks did not discriminate, and
every one of them passed a full run against a real portal while testing
nothing:

- the searchability wait was "the query term appears in the page", which is
  true of a search that matched nothing, because Liferay echoes the term back
  in `Liferay.currentURL`, in `currentURLEncoded` and in the product menu's
  `redirect` parameter. The wait returned on its first poll and never waited.
- `events.jsonl` validation collected its key set from line one only and never
  looked at a hit, so a file could drop three keys from line six, or replace
  every hit on every line with `{"zzz": 1}`, and pass.
- the egress scan keyed on the literal `{{`, so it proved only that nothing
  fetched a placeholder. An `<img src="https://tensoropt.ai/px.gif">` passed.
- the archive name was matched as two runs of eight digits, which accepted any
  pair of dates and hid defect D-4.
- the vendor token scan read one megabyte at a time with no overlap, so a token
  straddling a boundary escaped.

A green suite is not by itself evidence that a suite works, so run this
alongside the unit tests rather than only before a release.

## What it cannot assert

- **D9 in full.** The test proves that both addresses appear on the screen only
  inside click-only anchors, and that no image, iframe, script, prefetch or
  preload references either. It cannot prove the absence of an outbound request
  made from somewhere the page does not mention. That remains a reading
  exercise, and the one place to read is `EvaluationServiceLinks`.
- **EC-5 on DXP 2025.Q1.** Still unmeasured anywhere (DESIGN.md section 7),
  not just unasserted here: the headless Search API is still behind feature
  flag `LPS-179669` on that line, and the endpoint still answers 404 behind
  the flag.
- **The widget-applied Blueprint case for EC-6 and EC-13.** Measured by hand
  (DESIGN.md section 7), not asserted here: no case here applies a Blueprint
  through the Blueprint Options widget. The suite does cover one headless
  Blueprint-driven search and the TO-141 Blueprint editor Preview exclusion
  (`blueprint-preview-excluded`).
- **Email delivery (TO-112, EC-15).** The notification cases read the
  administrator's notifications list and the stored `UserNotificationEvent`
  row, which cover the website path. The stack has no SMTP sink (no
  MailHog/Mailpit container, no mail server configuration), so nothing here
  observes whether `EmailDelivery.send` was ever called or whether a message
  actually arrived; see DESIGN.md EC-15 for what was established about email
  from the API instead.
- **Cluster behaviour.** One node, one JVM. The collection cycle's expiring
  marker and its per-company lock (DESIGN.md 3.6) exist for the multi node
  case, which this does not
  reach.
- **Anything `selftest.py` does not have a mutation for.** It covers the
  assertions that were found to be vacuous and the ones added since. It is a
  floor, not a proof of completeness.
- **The scheduler firing a job.** `daily-jobs-scheduled` proves each job's
  trigger is set to its time of day, and the purge and cycle cases invoke the
  jobs directly, but no run waits until 03:00 to watch Liferay fire one.

## Not colliding with a developer's own instance

The stack uses its own project name (`sel-e2e`), its own container names, its
own volumes, and host ports 18080 and 15432 rather than 8080 and the
database's own. Before
anything starts, the harness checks that neither published port already belongs
to a container it did not create, and stops with that container's name if it
does. Ports are overridable with `--http-port` and `--database-port` or the
matching `SEL_E2E_*` variables.

Teardown removes only what the project created. It never runs against a
container by name.

## Running it in CI

The pipeline is `.gitlab-ci.yml` at the root: a build job (`make package`),
then this suite on all four databases as parallel jobs (`make e2e-run DB=...`)
against the build job's jars and test `.lpkg`, then a manual release job
(`make publish`) needing both the build job and every e2e matrix job to have
passed, which publishes those same, already-tested jars without rebuilding
or retesting them. `make e2e-run` is the same target `make e2e` and `make
test` use once their own `package` build is done, so the exact command a job
ran is always reproducible by hand with `make`. JUnit XML from both the unit
tests and this suite reaches GitLab's test report.

The jobs run on GitLab.com's shared runners, where the stack runs under
Docker-in-Docker. Three things make that work, and all three also hold on a
workstation:

- **Nothing is bind mounted.** A bind mount is resolved by the Docker daemon,
  which under Docker-in-Docker is the dind service, not the job container that
  holds the checkout. The harness's files and any JDBC driver are baked into a
  thin image (`liferay/Dockerfile`), whose build context travels over the
  Docker API, and bundles are deployed with `docker cp` (`Stack.deploy`).
- **Ports are published on `SEL_E2E_BIND`** (default `127.0.0.1`); the
  pipeline sets `0.0.0.0`.
- **The harness reaches the portal at `SEL_E2E_HOST`** (default `localhost`);
  the pipeline sets `docker`, the dind service.

A deployed file is staged in `/tmp` inside the container, handed to the
`liferay` user and renamed into the deploy folder. `docker cp` keeps the host's
owner, and Liferay's auto deployer, which polls every three seconds, cannot move
a file it does not own: it says only "Unable to write", once per poll.

`run.sh` exits 0 on the known defect state, so a gate that fails on a non zero
exit is correct from the first run. The known defects are reported as skipped
in the JUnit XML, with their tracebacks in `system-out`. If one of them starts
passing, the run goes red on purpose.

The heavy tier belongs on a schedule rather than on every push, and needs
around 10 GB free on the Docker filesystem:
`e2e/run.sh --scale full --export-timeout 7200`.

## Debugging a failure

`--keep` leaves the stack up and prints the teardown command. With it running:

```
open http://localhost:18080                     # test@liferay.com / test
docker logs -f sel-e2e-liferay
docker exec -it sel-e2e-database psql -U lportal -d lportal    # PostgreSQL
docker exec -it sel-e2e-database mysql -ulportal -plportal lportal  # MySQL
```

On MariaDB the client is `mariadb`, with the same arguments as `mysql`.

A failing run without `--keep` still writes the container logs to
`e2e/results/logs`.
