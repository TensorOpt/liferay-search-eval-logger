---
name: pre-release-audit
description: Runs the pre-release audit of this repository with the agent pipeline auditor (Fable) -> developer (Sonnet) -> reviewer (Opus) -> auditor close-out, for the engineering principles, the documentation consistency, or both. Use before every major release.
argument-hint: "[principles|docs|all] [ticket, e.g. TO-150]"
disable-model-invocation: true
---

Run the pre-release audit for scope `$0` (default `all`) under ticket `$1`.
You coordinate; you do not audit, fix or review yourself. Every finding
goes through the agents below, and anything that needs the owner's decision
is reported to the owner before it is fixed.

The pipeline, per scope (`principles` first, then `docs` when `all`):

## 0. Preconditions

- On `main`, clean working tree, `./gradlew build` and `python3 e2e/selftest.py`
  green (`JAVA_HOME` pointing at a JDK 21). Stop and report if not.
- Findings live in `.claude/audit/<ticket>-<scope>.md` (gitignored, survives
  `./gradlew clean`). Create the directory.
- One developer per worktree at a time. Never run two developers on the same
  checkout; the second one reverts the first one's uncommitted work.

## 1. Assess

Launch the `auditor` agent, mode `assess`, with the scope and the findings
file path. When it reports, read the findings file.

Collect every finding marked `decision: owner`, present them to the owner
as a numbered list with the options and the auditor's recommendation, and
wait for the answers. Record each answer under its finding in the file.
Findings the owner declines are marked `declined: <reason>` and are not
fixed.

## 2. Fix and review, up to 5 rounds

Round N:

1. Launch the `developer` agent with the findings file and the round
   number. It fixes every finding that is open, one commit per finding or
   per tight group of findings, commit subject `<type>(<ticket>): ...`,
   finding ids in the body. It runs the build and the unit tests, and the
   e2e suite when a finding touched `e2e/` or the capture or export path.
   It reports the exact tallies.
2. Launch the `reviewer` agent on the round's commits with the findings
   file. It verifies each finding fixed in this round, reviews the fixes
   against `CLAUDE.md` and `DESIGN.md`, and returns a verdict.
3. New findings from the review are appended to the findings file with the
   round number (`R2-1`, `R2-2`, ...). Verdict `APPROVE` ends the loop.

After round 5, stop and report what is still open; do not start a sixth.

## 3. Close out

Launch the `auditor` agent, mode `close-out`, on the same findings file.
Relay its "Coverage" section to the owner verbatim. Anything it would not
release with is an open point for the owner, not something to fix quietly.

## 4. Backport and release notes

When both scopes are closed out on `main`:

- Cherry-pick the fix commits to every `dxp-*` branch in its own worktree,
  in order, resolving conflicts in the older branch's own form. Never
  cherry-pick generated Service Builder code: change `service.xml` there and
  re-run `buildService` on that branch. Run the unit tests and the e2e
  suite on each branch and report the exact tallies.
- Update `RELEASE_NOTES.md` with what was checked and fixed, per task, and
  with the verification table (tips, test counts, databases). Declare the
  version READY only when every finding is fixed or declined by the owner.

## Rules for the whole run

- Report back to the owner at the end of every step with what was found,
  fixed, declined and still open.
- Commit only through the developer, on `main`, with the attribution
  trailers this session requires. Never push.
- Never state a verification that was not run. A tally is quoted from the
  tool output, never estimated.
- No em or en dashes in anything written to the repository.
