# End to end functional test (TO-84)

An unattended, Docker Compose driven functional test of the whole plugin. It
brings up PostgreSQL and Liferay DXP, installs the plugin onto the running
portal, restarts it as DESIGN.md 3.1 requires, creates content, runs real
searches, generates a scale corpus, then exercises and asserts on the retention
purge, the export archive and the funnel integration of DESIGN.md 10.

It is built for CI first. Every wait is bounded and named, teardown runs on
failure as well as on success, results come out as JUnit XML, and nothing in
it touches a container it did not create.

## Running it

```
e2e/run.sh                      # smoke tier, the default
e2e/run.sh --scale full         # 1,000,000 events and 10,000,000 hits
e2e/run.sh --keep               # leave the stack up afterwards for debugging
e2e/run.sh --no-build           # use modules/*/build/libs as they are
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
- `logs/`, the Liferay and PostgreSQL container logs, collected on failure
- `export.zip` and `export-range.zip`, the archives the export cases produced

## Prerequisites

- Docker with Compose v2, and roughly 6 GB of memory available to it. The
  portal alone asks for a 2 GB heap and runs an Elasticsearch sidecar beside it.
- `python3`. Standard library only: no `pip install` step, deliberately, so a
  runner needs nothing prepared.
- Outbound network access to Docker Hub. On the first run that is about 2.4 GB
  for `liferay/dxp` plus PostgreSQL.
- Outbound network access to `repository-cdn.liferay.com`, but only when the
  harness has to build the bundles. If `modules/*/build/libs` already holds the
  four jars it uses them.

There is no dependency on anything in a developer's home directory. The cached
bundle archive under `~/.liferay/bundles` is not used and is not needed.

## What runs where, and why

### The Liferay image comes from Docker Hub

`liferay/dxp:2025.q1.27-lts` is pullable anonymously, and the image is
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
all base DXP. The two Enterprise Search items in DESIGN.md section 7, EC-6 and
EC-13, are out of scope here for the same reason they are open there.

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
`SchedulerJobConfiguration` jobs on a one day trigger. A CI run cannot wait a
day, and neither job has, or should have, a trigger of its own for tests to
call.

The harness uses Server Administration's Groovy script console, which is a
stock DXP administration feature, to look each service up in the OSGi registry
by name and invoke it reflectively. `search-eval-logger-impl` exports nothing,
so reflection is what makes its services reachable without exporting them. The
production bundles are exactly the ones that ship; the code under test is
entered the same way the scheduler enters it.

The console carries a CAPTCHA.
`liferay/files/osgi/configs/com.liferay.captcha.configuration.CaptchaConfiguration.config`
sets `maxChallenges` to a negative value, which means never check. That belongs
to the test portal and to nothing else, which is why the stack binds every
published port to `127.0.0.1` and is destroyed at the end of a run.

One more property of the console is worth knowing before debugging it: Liferay
rotates the session's CSRF token across a portlet action, so an auth token read
once is good for exactly one action. The harness re-reads the token before each
script, which costs one page load. It does **not** also sign in again. Signing
in would stay inside the CAPTCHA's stock allowance of one challenge-free check
per session and would make the config file above unnecessary, but keeping both
would leave two mechanisms for one problem and no way to tell which was
working. The config file is the mechanism; `portal.py` says so too.

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
the figures DESIGN.md 3.4 and the repository README already record from the
developer's own instance (about 13 s to purge a large backlog, about two and a
half minutes to export 600,000 events).

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

`EvaluationServiceLinks` holds `{{SIGNUP_URL}}` and `{{BOOKING_URL}}` as
literal placeholder tokens on purpose. The test asserts what DESIGN.md 10
specifies around them: the collection start link carries `started` at day
granularity plus the two UTM tags and nothing else, the export complete link
carries the UTM tags alone, both are plain anchors with `target="_blank"` and
`rel` containing `noopener`, and neither token nor any UTM tag appears anywhere
inside the export archive.

Readiness needs its two thresholds met. Rather than lower them, the harness
moves the stored `collectionStartDate` back past `readinessMinimumDays`,
through the same `PortletPreferencesLocalService` the plugin writes it with, and
relies on the scale corpus for the event count. The comparison being exercised
is then the one a real installation makes.

## What it asserts, case by case

| Case | What it proves |
|---|---|
| `stack-up` | The stack boots, the portal serves, an administrator can sign in, and the product version and license mode are what was expected |
| `plugin-absent-baseline` | The SEL tables do not exist before installation, so their later presence means something |
| `install-onto-running-portal` | All four bundles start on a portal that is already serving, and Service Builder creates both tables and their indexes |
| `ec3-bypass-detected` | DESIGN.md 3.1: installed onto a running portal, the wrapper is registered and never called, and the plugin says so in the log and on its screen rather than leaving an empty table to be discovered |
| `stall-notification` | DESIGN.md 3.6 condition 1 fires, the notification reaches `UserNotificationEvent`, and it appears in the administrator's own notifications list, which is the runtime half of EC-14 |
| `restart-clears-bypass` | The restart is what makes Liferay's search consumers bind the wrapper |
| `daily-jobs-scheduled` | After that restart, the retention purge and the collection cycle check are scheduled at 03:00 and 03:30 and then daily, not one day after the portal started |
| `content-and-searches` | Content created through the headless API becomes searchable, and searches run through the Search Results widget path EC-1 confirmed |
| `capture` | Admitted searches are persisted with their hits, the admission funnel is monotonic and adds up (admitted = persisted + dropped once settled), nothing is dropped at rest, `sourceType` is `WIDGET`, and no hit is orphaned |
| `collection-start-recorded` | DESIGN.md 3.6: the cycle records the UTC day of the first persisted event |
| `scale-data` | The corpus is written, and every hit's duplicated `companyId` and `createDate` match its event's |
| `retention-purge` | DESIGN.md 3.4: everything past the window goes, everything inside it stays, no orphan hits are left, and a run with nothing to delete finishes inside a loose bound that a delete no longer using its index would break |
| `funnel-readiness` | DESIGN.md 3.6 condition 2, against the configured thresholds, with the notification stored, shown in the administrator's notifications list, and the banner rendered |
| `export` | DESIGN.md 6.1: the action starts a background task that completes and offers a download |
| `export-archive-name` | DESIGN.md 6.2 names the archive for the range that was requested, asserted as an exact string |
| `export-archive-contents` | DESIGN.md 6.2, entry by entry and key by key, including the effective configuration read back, and every id and count in the manifest a JSON number |
| `export-range-is-honoured` | DESIGN.md 6.1: a narrow window exports exactly the rows that window holds, counted independently in the database |
| `export-archive-vendor-neutral` | DESIGN.md 10.4: no evaluation service link and no UTM tag inside the archive |
| `funnel-links` | DESIGN.md 10.1 and 10.3 |
| `funnel-zero-egress` | D9, as far as a rendered page can be inspected |
| `export-foreign-download` | TO-91: the export's download URL, given another background task's id, does not serve that task's attachment, while its own id still serves the archive |
| `export-permission-refused` | DESIGN.md 6.1: a user who can open the screen but does not hold `EXPORT` is refused, on the screen and on a direct action post. See below for what counts as evidence there |
| `export-jsonl-number-types` | DESIGN.md 6.2 types `total_hits`, `entry_class_pk` and each of `scope_group_ids` as JSON numbers |
| `export-unbounded-range` | DESIGN.md 6.1: an export with either date left empty, in all three combinations, each one asserted against the rows its range holds, the range its manifest reports and the range line in its README |
| `queue-overflow` | TO-92: with `SEL_SearchEvent` locked so the listener cannot write, 2,600 searches from 16 clients all answer HTTP 200, the events past the queue's 2,000 are dropped, each counted once (dispatched equals persisted), and the funnel adds up once the lock is released. Search latency is recorded with the queue draining and with it full |
| `uninstall-and-reinstall` | TO-111, last in the run: with the four bundles removed from the running portal, search still answers, nothing is logged at ERROR and the data is untouched; reinstalled and restarted, interception is active, collection resumes and the collection start date is unchanged |

## Reading the EXPORT permission case

The repository README records that the `EXPORT` permission has only ever been
exercised by an administrator who holds it, and that refusing a user who does
not was untested. This case tests it, and the shape of the evidence matters
because the obvious reading of the result is wrong.

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

`selftest.py` runs 98 discrimination checks in about a second, with no Docker
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
- **EC-5, EC-6, EC-13.** The headless Search API is behind feature flag
  `LPS-179669` and Blueprints need Liferay Enterprise Search. Both are open in
  DESIGN.md section 7 for the same reasons.
- **Notification delivery beyond the website.** The two notification cases
  read the administrator's notifications list, which is where the plugin
  delivers. Email or any other delivery channel is not configured and not
  checked.
- **Cluster behaviour.** One node, one JVM. The collection cycle's expiring
  marker (DESIGN.md 3.6) exists for the multi node case, which this does not
  reach.
- **Anything `selftest.py` does not have a mutation for.** It covers the
  assertions that were found to be vacuous and the ones added since. It is a
  floor, not a proof of completeness.
- **The scheduler firing a job.** `daily-jobs-scheduled` proves each job's
  trigger is set to its time of day, and the purge and cycle cases invoke the
  jobs directly, but no run waits until 03:00 to watch Liferay fire one.

## Not colliding with a developer's own instance

The stack uses its own project name (`sel-e2e`), its own container names, its
own volumes, and host ports 18080 and 15432 rather than 8080 and 5432. Before
anything starts, the harness checks that neither published port already belongs
to a container it did not create, and stops with that container's name if it
does. Ports are overridable with `--http-port` and `--postgres-port` or the
matching `SEL_E2E_*` variables.

Teardown removes only what the project created. It never runs against a
container by name.

## Running it in CI

```yaml
- run: e2e/run.sh --scale smoke
- uses: actions/upload-artifact@v4
  if: always()
  with:
    name: e2e-results
    path: e2e/results
```

`run.sh` exits 0 on the known defect state, so a gate that fails on a non zero
exit is correct from the first run. The three known defects are reported as
skipped in the JUnit XML, with their tracebacks in `system-out`. If one of them
starts passing, the run goes red on purpose.

Run `selftest.py` alongside the unit tests. It needs no Docker, takes about a
second, and is what keeps the assertions above honest.

The heavy tier belongs on a schedule rather than on a pull request, and needs
around 10 GB free on the Docker filesystem:

```yaml
- run: e2e/run.sh --scale full --export-timeout 7200
```

## Debugging a failure

`--keep` leaves the stack up and prints the teardown command. With it running:

```
open http://localhost:18080                     # test@liferay.com / test
docker logs -f sel-e2e-liferay
docker exec -it sel-e2e-postgres psql -U lportal -d lportal
```

A failing run without `--keep` still writes the container logs to
`e2e/results/logs`.
