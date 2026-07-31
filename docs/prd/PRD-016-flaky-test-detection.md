# PRD-016 — Flaky Test Detection

| | |
|---|---|
| **Status** | ✅ Implemented (2026-07-31) |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-09 |
| **Priority** | P3 — v2.0, cheap analytics win |
| **Target** | v2.0 |
| **Related** | REQUIREMENTS.md §13.4 (was PRD-009 §2.7) |

---

## 1. Summary

Surface test cases that flip between pass and fail across recent runs ("flaky"), so teams can quarantine or fix them. This is pure analytics over the result history already stored — no new capture, minimal schema. A natural fit once enough run history exists (especially with CI ingestion from PRD-005 feeding regular results).

## 2. Goals & Non-Goals

**Goals**
- A flakiness score per test case = proportion of status *changes* across its last N executions (chronological), restricted to PASSED/FAILED transitions.
- A dashboard widget: top flaky tests in a project.
- Optional auto-label (`flaky`) above a configurable threshold.

**Non-Goals**
- Root-cause analysis or retry orchestration.
- Cross-project flakiness ranking.
- Real-time scoring on every result write (batch/cached is fine).

## 3. Proposed Design

### 3.1 Computation
- For a test case, take the last N results (default 20) ordered by run time; consider only terminal PASSED/FAILED outcomes (ignore PENDING/BLOCKED/SKIPPED for the transition count, but keep them for context).
- `flakyScore = transitions / max(1, comparable_pairs)` where a transition is consecutive PASSED↔FAILED. Score in [0,1]; also expose `runsConsidered`, `failRate`.
- A case is "flaky" when `flakyScore >= app.flaky.threshold` (default 0.3) and `runsConsidered >= app.flaky.min-runs` (default 5).

### 3.2 Where it runs
- Compute on demand for the dashboard query (bounded: top-K cases by recent activity), and/or a nightly `@Scheduled` job that caches `flaky_score` + `flaky_at` on the test case (optional column) to keep the widget cheap. Start on-demand; add the cached column only if query cost warrants it (migration V39, nullable).

### 3.3 Endpoints & UI
- `GET /api/projects/{projectId}/analytics/flaky?limit=10` — top flaky cases with score, fail rate, runs considered (VIEWER, membership-scoped).
- Project dashboard: a "Flaky tests" widget (list with score bar, link to the case). Reuse the existing dashboard chart/widget patterns.
- Optional: when scoring runs and a case crosses the threshold, add the `flaky` label (and remove when it drops) — gated by `app.flaky.auto-label` (default false) to avoid surprising edits/audit noise.

## 4. Edge Cases
- Too few runs → not flaky (respect `min-runs`); show "insufficient data" rather than a misleading 0/1.
- A case never failing or always failing → score 0 (stable), not flaky.
- Ignore runs that were ABORTED so a cut-short run doesn't look like a transition.
- Auto-label off by default; when on, changes are audited like any label change.

## 5. Testing
- Score math: alternating P/F sequence → high score; monotonic → 0; respects N window and min-runs.
- Endpoint returns top-K membership-scoped; non-members excluded.
- Auto-label adds/removes `flaky` only across the threshold and only when enabled.

## 6. Effort & Risk
- **Effort:** ~3–5 days (scoring + endpoint + widget); +1 day if caching column/job added.
- **Risk:** Low — read-only analytics over existing data; main care is a defensible score definition and not flooding audit via auto-label.

## 7. Acceptance Criteria
- [x] Flakiness score computed from recent PASSED/FAILED history with configurable window/threshold/min-runs.
- [x] `GET .../analytics/flaky` returns membership-scoped top flaky cases.
- [x] Dashboard widget lists top flaky tests linking to the case.
- [x] Optional auto-label is off by default and audited when enabled.
- [x] Score, endpoint, and scoping tests pass.

## 8. As Built (2026-07-31)

**No schema change.** Scoring reads existing results through a narrow projection
(`FlakyResultRow`: case id, key, title, status, run time) rather than loading `TestResult` graphs.
Computed on demand, as §3.2 suggested starting: at this tool's scale the query is a few thousand
narrow rows fetched once per dashboard visit, and a cached column plus a nightly job would be two
more things to keep correct for no measurable gain.

**The score answers "does this keep changing its mind", not "does this fail."** Transitions over
comparable pairs, so a test that fails every time scores 0 — it is broken, not flaky — and a single
regression in a long history scores near 0 rather than looking intermittent. Tests assert both
distinctions explicitly because getting them wrong would fill the widget with ordinary failures.

**Ordering** is by `COALESCE(run.endTime, run.startTime, run.createdAt)`, not row creation time:
CI ingestion (PRD-005) backfills results long after the run they describe, so writing order is not
run order.

**Excluded:** BLOCKED, SKIPPED and PENDING (they describe the environment or the schedule, not the
test), and ABORTED runs (a run someone cut short must not read as a transition). IN_PROGRESS runs
still count — a recorded result is a real observation.

**`runsConsidered` is exposed** so the UI can tell "stable" from "not enough history to say". Below
`min-runs` a case is never reported flaky regardless of score: one flip out of two results is 1.0 on
almost no evidence.

**Auto-label** is off by default and idempotent — a second sync over unchanged data writes nothing,
so enabling it does not flood the audit log. It preserves other labels and is admin-only, since it
edits test cases.

**Tests: 27 backend.** Score maths (alternating, monotonic, always-failing, single regression,
single result), window ageing out old behaviour, min-runs suppression, status and aborted-run
exclusion, cross-project isolation, endpoint scoping and the VIEWER/ADMIN split, plus six auto-label
cases with the flag on.

### Deferred
- Cached `flaky_score` column and nightly job — revisit if the query shows up in profiling.
- Cross-project ranking and root-cause analysis remain out of scope per §2.
