# Changelog

## 1.0.0

The first release, on three release lines: `main` for DXP 2026.Q3, `dxp-2026.q1` for DXP 2025.Q3 to 2026.Q2 (built for 2026.Q1 LTS) and `dxp-2025.q1` for DXP 2025.Q1 LTS and 2025.Q2 (Java EE). All three contain the tickets below; TO-85 concerns the two Jakarta branches and TO-138 `main` only.

### Features

- **TO-82** The plugin: a `Searcher` wrapper that records user searches with the results shown, an admission filter that drops Liferay's internal traffic, asynchronous persistence that never slows or breaks a search, a nightly retention purge, and the Search Eval Export screen with a streamed JSONL export.
- **TO-86** The two links to TensorOpt: a signup banner once collection has started and a booking link after a successful export, both switchable off.
- **TO-115** Snippets are stored and exported as JSON keyed by locale, then field.
- **TO-122** The export manifest names its format, `format_version`, as the plugin's major.minor.
- **TO-133** Ranks are numbered from 1, as stored and as exported.
- **TO-135** The export screen shows the share of searches that returned no results, overall and for guests and signed-in users, and the 20 most frequent zero-result queries, cached for an hour.
- **TO-139** The blueprint driving a search is recorded and exported by external reference code too, Liferay's preferred way to name one, alongside its ID.
- **TO-140** Installing or redeploying the plugin no longer needs a portal restart for the Search Results widget, shared search requests or search bar suggestions: an automatic rebind moves them onto the wrapper on its own. A secondary consumer such as Objects REST still needs a restart to join, and the export screen now says so separately from the "Restart required" banner.

### Fixes

- **TO-87** An export with a blank date bound failed.
- **TO-88** Long values were written to the export as JSON strings.
- **TO-90** The export file had the wrong name.
- **TO-91** The download endpoint served any background task's file, not only exports.
- **TO-92** The admission counters did not add up.
- **TO-94** Long text was cut back to its last comma rather than at the column width.
- **TO-110** The notifications never reached the notifications list, and every restart postponed the daily purge and cycle check by a day.
- **TO-112** Notification flow fixes.
- **TO-113** Signup banner and funnel link fixes, the released link addresses, and margins and sections on the export screen.
- **TO-141** Searches from the Blueprint editor's Preview were logged as user searches, with no Blueprint recorded.
- **TO-144** A guest row's `cohortHash` is stored as `null`, never a hash of a non-existent user.
- **TO-144** The zero-result panel's distinct-query map is capped at 10,000 entries; the top-20 table is marked approximate once the cap is reached instead of letting the map grow without bound.
- **TO-144** The collection-started and stall notification texts no longer tie collection starting to a restart, which TO-140 made only one of several ways a cycle can open.
- **TO-144** An export range whose end date landed on the start day was rejected instead of exporting that one day.
- **TO-144** Downloading a stale or already-removed export answered HTTP 500 instead of a clear error.
- **TO-144** `samplingRate`'s configuration field is now bounded to the range its own help text already stated.

### Platform

- **TO-85** Runs on DXP 2026.Q1 LTS (Jakarta EE); DXP 2025.Q1 LTS is served from the `dxp-2025.q1` branch.
- **TO-138** `main` builds for DXP 2026.Q3; DXP 2026.Q1 LTS is served from the `dxp-2026.q1` branch.

### Refactoring

- **TO-83** Package renamed to `ai.tensoropt.sel`.
- **TO-95** Configuration defaults are built from `@Meta.AD` instead of being copied.
- **TO-96** The export screen's logic moved from the JSP into the portlet.
- **TO-97** The service layer's JDBC plumbing is shared.
- **TO-98** A search's entry class names are resolved in one place.
- **TO-99** Dead code removed.
- **TO-100** `ConfigurationProvider` is injected instead of wrapped.
- **TO-101** Diagnostic logging removed from the capture path.
- **TO-102** The marker property and the executor class name are named once.
- **TO-103** Code tightened where several lines said what fits in one.

### Documentation

- **TO-89** The refactored export is documented.
- **TO-104** Pre-release checks.
- **TO-105** MySQL Connector/J needs `useCursorFetch=true`.
- **TO-106** Release-readiness results recorded.
- **TO-114** README updates, including the databases tested.
- **TO-131** The README says who maintains the plugin.

### Tests and build

- **TO-84** The end-to-end test suite.
- **TO-93** The end-to-end bundle-start wait no longer misses bundles that start together.
- **TO-108** End-to-end case for a full event queue.
- **TO-109** End-to-end case for the export download check.
- **TO-111** End-to-end case for uninstalling and reinstalling the plugin.
- **TO-116** `make` targets, the GitLab pipeline and release tooling, an end-to-end stack without bind mounts, and an `.lpkg` install case.
- **TO-144** A narrowly named tolerance for the uninstall cascade's Aries Whiteboard/vulcan error (distinct from the existing AopServiceManager rule it does not cover), and a positive recovery check that the OSGi JAX-RS whiteboard settles back to its pre-uninstall baseline after uninstalling and after reinstalling.
- **TO-144** An end-to-end case for DESIGN.md 3's latency claim (`search-overhead`), and `scripts/loadtest.py`/`make loadtest`, which measures collection's actual search overhead against a running portal with a bootstrap confidence interval.
