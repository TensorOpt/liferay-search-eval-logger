# Liferay Search Eval Logger

A free, open-source (Apache 2.0) plugin for Liferay DXP. It records what people search for on your site and which results they were shown, so you can measure search quality on your own content.

It never changes a search, never contacts anything outside your instance, and exports a vendor-neutral dataset that any evaluator can use.

- Approving it for production? Start with [SECURITY-REVIEW.md](SECURITY-REVIEW.md). It is written to be forwarded unedited.
- Design, decisions, empirical checks and measurements: [DESIGN.md](DESIGN.md)
- Download: [releases](https://github.com/TensorOpt/liferay-search-eval-logger/releases), one per DXP LTS line

## Why measure search

Users judge search by what it shows them. A relevant page that search never returned leaves no trace: no complaint, no click, no ticket.

In a classic study, searchers kept going until they believed they had found at least 75% of the relevant documents. Measured recall averaged about 20% ([Blair and Maron, 1985](https://doi.org/10.1145/3166.3197)). That was a Boolean system used for legal discovery. The lesson that carries over is that misses are invisible, to users and to the team running search.

The same applies to a change. Switching on semantic or hybrid search is a configuration change. Whether it helped on your content is a measurement.

### The measurement ladder

This ladder is TensorOpt's framing, not an industry standard.

| Group | Rung | What you have |
|---|---|---|
| Observe | 1 | Search is switched on. Nothing is measured |
| Observe | 2 | A search log: queries and the results shown. Top queries and zero-result queries become visible |
| Label | 3 | A sample of logged queries has relevance labels, so precision, recall and nDCG can be computed |
| Label | 4 | Agreement between labellers is measured, so you know how reliable the labels are |
| Label | 5 | An LLM judge, checked against the human labels, scores results at scale |
| Decide | 6 | Configurations are compared by their average scores |
| Decide | 7 | Configurations are compared with confidence intervals and paired significance tests |
| Connect | 8 | Search metrics are tied to outcomes such as time to find, support tickets or conversion |
| Connect | 9 | Configurations are A/B tested on live traffic |

Every rung above 1 starts from rung 2. Labels attach to a query and the results it returned, so you cannot label, compare or connect what was never recorded. **This plugin is rung 2.**

Rung 6 is a trap worth naming: two averages computed on a few hundred queries can differ by chance alone. Rung 7 is how you tell.

## What Liferay provides, and what is missing

- **Search Insights widget.** Shows the query Liferay sends to the search engine. Liferay documents it as [only useful for testing and development](https://learn.liferay.com/w/dxp/using-search/search-configuration-reference).
- **Liferay Analytics Cloud.** A separate Liferay cloud service. Its Search Terms view lists the [terms people search for and how many users searched for each](https://learn.liferay.com/en/w/dxp/personalization/analytics-cloud/touchpoints/sites-analytics/interests-and-search-terms).
- **Liferay Enterprise Search.** Blueprints, semantic search and Learning to Rank. These change how results are ranked. None of them records what a ranking returned.
- **Server logging.** DEBUG-level output, meant for diagnosis.

None of these stores each query together with the results it returned. Without that record you cannot:

- find the queries that return nothing, or return the wrong thing;
- build a judgment set, because labels need the results that were shown;
- compare configurations on the queries your users actually type.

## What the plugin does

- **Records user searches.** It wraps Liferay's public `Searcher` service. Each search that carries user keywords is recorded together with the results shown.
- **Ignores internal traffic.** Liferay runs many searches for its own purposes (Asset Publisher, Control Panel lists, workflow). Searches without user keywords are dropped, and typeahead suggestions are excluded by default.
- **Stays out of the way.** Recording is asynchronous and never alters a search. If recording fails, it records nothing; it never breaks a search. With collection on, median search round trip rose by 4 ms (64 ms against 60 ms, 180 searches each way).
- **Keeps the data in your database.** Two tables in the portal database. Records older than 90 days are deleted automatically.
- **Tells you its state.** It notifies you when collection starts, if it is stuck, and when there is enough data to export.
- **Exports on request.** An administrator exports a date range as a zip file. Nothing is ever sent anywhere.

## What data is collected

For each recorded search:

- the query text, locale, site scope and requested asset types;
- applied facet selections, where the search path exposes them;
- each result shown: rank, score, document ID, asset type, and the title and snippet when the response already contains them. The snippet is recorded as an object keyed by locale then field, each field's highlighted fragments an array, so which field and language a fragment came from is never lost to a join;
- page size, offset and total hit count, so a capped result list is never mistaken for a short one;
- whether the user was a guest or signed in, plus a salted hash of the user ID. The salt rotates weekly by default and is never exported.

Not collected: user IDs, names, email addresses, IP addresses or document bodies. The plugin never widens a query to capture more.

Full detail, including what the field whitelist does and does not prevent: [SECURITY-REVIEW.md, sections 3 and 4](SECURITY-REVIEW.md#3-what-is-stored).

## What you can do with the data

Without any labels (rung 2):

- **Zero-result queries.** Searches with `total_hits` of 0, grouped by cause: a vocabulary gap, a typo, missing content or a permission filter.
- **Head and tail.** Which few queries carry most of the traffic, and so deserve attention first.
- **Facet use.** Which filters people apply, and on which content types.
- **Reformulations, roughly.** Reworded searches from the same cohort hash within minutes suggest the first results failed. This is approximate, because there is no session ID, and it works only for signed-in users, because guests cannot be told apart.

With labels (rung 3 and up):

- **A judgment set.** Sample logged queries and label the results they returned. The export's `manifest.json` reports how many results carry a title or snippet, which tells you whether judging from the export alone is feasible.
- **Configuration comparisons.** Run the logged queries against your current configuration and a candidate (for example, semantic search switched on), label the results, and compare with paired tests.
- **Regression checks.** Repeat the comparison after upgrades or tuning changes.

The outcomes:

- synonym sets that close vocabulary gaps behind zero-result queries;
- result rankings for the head queries;
- an evidence-based decision on enabling semantic or hybrid search;
- a baseline that shows when a later change made search worse.

## Requirements

- **Liferay DXP 2025.Q3 to 2026.Q2, built for 2026.Q1 LTS.** These releases [run on Jakarta EE](https://learn.liferay.com/w/reference/jakarta-2025-faq). Compiled to Java 17 bytecode; the official DXP images run it on Java 21.

  | DXP | This build | Checked by |
  |---|---|---|
  | 2025.Q1 LTS, 2025.Q2 | No: Java EE (`javax`); use the [`dxp-2025.q1` branch](https://github.com/TensorOpt/liferay-search-eval-logger/tree/dxp-2025.q1) and its releases | not deployed: those releases are Java EE and cannot provide the `jakarta` packages this build imports |
  | 2025.Q3, 2025.Q4 | Yes | deploying it on 2025.Q3.10 and 2025.Q4.12 |
  | 2026.Q1 LTS | Yes | the full end-to-end suite on 2026.Q1.12 and 2026.Q1.13 |
  | 2026.Q2 | Yes | deploying it on 2026.Q2.12 |
  | 2026.Q3 and later | No: Liferay changed the base class Service Builder's persistence code extends, so storage fails to start; it needs a rebuild | deploying it on 2026.Q3.6 |

  "Deploying it" means on a clean portal, as JARs and as an `.lpkg`: all four bundles Active, every component of the plugin up, and no error from the plugin in the log. It does not exercise searching or exporting; the end-to-end suite does.
- **Database.** Tested end to end on PostgreSQL 15, MySQL 8.4 and MariaDB 11.4.
  - **MySQL with MySQL Connector/J:** add `useCursorFetch=true` to the JDBC URL. Without it, Connector/J loads an entire export into memory. The DXP image does not include Connector/J; copy it to `[Liferay Home]/tomcat/webapps/ROOT/WEB-INF/shielded-container-lib`.
  - **MariaDB, or MySQL with the MariaDB driver the DXP image ships:** no extra setting is needed.
- **Not yet verified:** Blueprints, the headless Search API, DXP Cloud, clusters, and databases other than the three above. See [SECURITY-REVIEW.md, section 9](SECURITY-REVIEW.md#9-what-is-not-verified).

## Install

1. From the [releases](https://github.com/TensorOpt/liferay-search-eval-logger/releases), pick the one for your DXP line: tags end in `-dxp-2026.q1` for DXP 2026.Q1 and `-dxp-2025.q1` for DXP 2025.Q1. Download the four `ai.tensoropt.sel.*.jar` files and `SHA256SUMS`.
2. Verify the download (on Linux, `sha256sum -c SHA256SUMS`):
   ```
   shasum -a 256 -c SHA256SUMS
   ```
3. Copy the four JARs to `[Liferay Home]/deploy`.
4. **Restart the portal.**

Installed from Liferay Marketplace instead, the plugin arrives as an `.lpkg` that Marketplace builds from the same four JARs; the restart is needed either way.

The restart is required. Liferay's search components bind the search service once, at startup. A plugin installed onto a running portal is registered but never called, so it records nothing and logs no error. The same applies after every redeploy of `search-eval-logger-impl`.

## Enable

Collection is off on install.

1. Go to Control Panel > Configuration > **Instance Settings** > Search > **Search Eval Logger**. The settings are per virtual instance, so use Instance Settings, not System Settings.
2. Switch on **Logging Enabled** and save.

Settings you may want to review:

| Setting | Default | What it does |
|---|---|---|
| Retention Window (Days) | 90 | Deletes records older than this, nightly at 03:00 UTC |
| Captured Fields | `title`, `snippet` | Result fields to record, if the response already has them |
| Exclude Suggestion Traffic | on | Leaves typeahead requests out |
| Require Web Request Context | off | Tightens the filter if internal searches show up |
| Excluded Asset Types | empty | Skips content types you do not want recorded |
| Readiness: Minimum Days / Events | 30 / 500 | When the "ready to export" notification fires |
| Show Evaluation Service Links | on | Shows or hides the two links to TensorOpt |

All settings: [DESIGN.md, section 5](DESIGN.md#5-configuration).

## Check that it is collecting

1. Open Control Panel > Configuration > **Search Eval Export**.
2. If a red **Restart required** banner shows, the restart has not happened yet. Nothing is being recorded.
3. Run a keyword search on a page with the Search Results widget, then reload the screen. The counters should go up, and a "Collecting since" date should appear.

You will also get a "Search logging is now collecting" notification.

## Notifications

| Notification | When |
|---|---|
| Search logging is now collecting | Immediately after the first search is recorded |
| Search logging is enabled but collecting nothing | Logging has been on for at least 24 hours, nothing has been recorded, and the restart is still outstanding |
| Your search log is ready to export | At least 30 days since collection started and at least 500 searches on record. Both thresholds are configurable |

- **Recipient.** Notifications go to the administrator who switched logging on. If that person cannot be determined, they go to the instance administrators.
- **Schedule.** The second and third are checked once a day, at 03:30 UTC. The readiness state also shows as a banner on the Search Eval Export screen.
- **Channels.** Each appears in the Liferay notifications list. It is also emailed through your portal's own mail server, if the instance has a mail server and a sender address configured (Instance Settings > Email), and the recipient has not turned email off. The switch is in the notifications list's Configuration, under **Search Eval Export**.
- **Links.** None contains an external link.

## Export

1. Exporting needs the **Export Search Evaluation Data** permission. Portal administrators have it. For anyone else, grant it to a role: Control Panel > Users > Roles, then Define Permissions > Control Panel > Configuration > Search Eval Export.
2. On the Search Eval Export screen, under **Run an Export**, enter a start and end date (`YYYY-MM-DD`, UTC). Leave a date empty for an open-ended range.
3. The export runs in the background. Reload the screen and download the zip from **Recent Exports**.

The archive holds:

- `events.jsonl`: one search per line, with its results nested;
- `manifest.json`: date range, counts, the settings in force, the filtering counters and per-field coverage;
- `README.md`: the schema and the caveats needed to read the data correctly.

## What to do with the export

1. **Read `manifest.json` first.** The coverage figures tell you how many results carry a title or snippet. The drop count tells you whether the log is complete or lossy.
2. **Read the archive's `README.md`.** It explains what to watch for: result lists capped by page size, results filtered by the searcher's permissions, and filters that Blueprints apply invisibly.
3. **Analyse it yourself, or give it to any evaluator.** The archive carries no vendor branding, links or tracking.
4. **Check data protection first.** If the query text may contain personal data, an external evaluator becomes a processor, which calls for a data processing agreement, not an NDA ([SECURITY-REVIEW.md, section 8](SECURITY-REVIEW.md#8-data-protection)).

## The two links to TensorOpt

TensorOpt, the plugin's author, offers search evaluation as a service. The plugin contains exactly two ordinary links to it:

- **A banner.** Shown once collection has started, linking to a free guide on what to look for in a search log. The link carries the collection start date and two tracking tags.
- **An export-screen link.** Shown after a successful export, for booking a call. It carries the two tracking tags only.

Neither link is fetched until someone clicks it. The plugin sends no install pings, usage counts or update checks. Switch off **Show Evaluation Service Links** to hide both. Details: [SECURITY-REVIEW.md, section 2](SECURITY-REVIEW.md#2-network-behaviour).

## Disable and uninstall

**Disable.**
- Switch off **Logging Enabled**. Nothing new is recorded.
- Existing records stay until the nightly purge deletes them at the end of the retention window.
- Switching logging back on starts a new collection cycle, with fresh notifications.

**Remove the recorded data.**
- Switch logging off and set **Retention Window (Days)** to 1.
- Within two nightly purges (03:00 UTC), the tables are empty.

**Uninstall.**
- Delete the four `ai.tensoropt.sel.*.jar` files from `[Liferay Home]/osgi/modules`.
- Search keeps working without a restart.
- Liferay does not drop a plugin's tables on uninstall, so the data stays in the database, and reinstalling picks it up again.

**Remove the tables as well.**
1. Uninstall as above.
2. Drop `SEL_SearchEvent` and `SEL_SearchHit`.
3. Delete the plugin's schema record: `delete from Release_ where servletContextName = 'ai.tensoropt.sel.service'`.

Do not skip step 3. With the record left in place, a later reinstall never recreates the tables, not even after a restart, and every recorded search fails with a "table does not exist" error in the log. With it removed, reinstalling and restarting creates the tables again, empty.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Red "Restart required" banner, or nothing recorded | The portal was not restarted after install or redeploy | Restart the portal |
| Searches observed, but few or none recorded | They carried no keywords (facet-only browsing, internal lists) or were typeahead suggestions | Expected. Run a keyword search to confirm recording works |
| Internal searches appear in the data | Some internal traffic carries keywords | Switch on Require Web Request Context, or exclude asset types |
| Most results have no snippet | Your search UI does not request highlighting | Expected. Check coverage in `manifest.json` before planning any labelling |
| No readiness notification after 30 days | Fewer than 500 searches on record, or the retention window is shorter than the readiness period | Lower the thresholds, or keep retention above the readiness days |
| Notification in Liferay but no email | No mail server or sender address for the instance, or email turned off for this notification. With the default sender, Liferay logs "Skipping email because the sender is not specified" | Configure both under Instance Settings > Email, or check the notifications list's Configuration under Search Eval Export |
| Export exhausts memory on MySQL | MySQL Connector/J loads the whole result set unless told otherwise | Add `useCursorFetch=true` to the JDBC URL, or use the MariaDB driver the DXP image ships |
| "You do not have permission to export search evaluation data." instead of the export form | You lack the export permission | Grant Export Search Evaluation Data to your role |

## Build, run and test from source

Needs JDK 17 or 21 (`JAVA_HOME`), Docker with Compose v2, `python3` and `make`, and network access to `repository-cdn.liferay.com` and Docker Hub.

```
make build                      # compile, unit tests, e2e selftest
make package                    # release jars and SHA256SUMS in build/dist/<version>-<line>/
make run DB=mysql FRESH=1       # a portal on http://localhost:8080 with the plugin installed
make stop                       # stop it; VOLUMES=1 also deletes its data
make test                       # unit tests, then the e2e suite on all four databases
make release VERSION=1.0.0      # what the GitLab release job runs; see CLAUDE.md
```

`DB` is `postgres` (the default), `mysql`, `mysql-mariadb-driver` or `mariadb`. Without `FRESH=1`, `make run` keeps the database from the last run.

Each branch builds for one DXP line, set by `liferay.workspace.product` in `gradle.properties`: `main` for DXP 2026.Q1, `dxp-2025.q1` for DXP 2025.Q1. The end-to-end suite is described in [e2e/README.md](e2e/README.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).
