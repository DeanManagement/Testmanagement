# PRD-049 — Metrics Consistency and Dashboard

| | |
|---|---|
| **Status** | 📝 Draft |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-19 |
| **Priority** | P2 — the same project state shows as 100 %, 0 % and 10.5 %, so nobody trusts any of them |
| **Target** | v2.5 |
| **Related** | PRD-037 (release gate), PRD-032 (environment roll-ups), PRD-014 (coverage), PRD-025 (MCP), PRD-031 (chat payloads) |

**Source** — change requests filed as bugs on the test instance (project SPI, 2026-09-17):

| Report | Asks for |
|---|---|
| `fe290f1a` | One pass-rate definition, labelled everywhere; a run with nothing executed must not plot as 0 % |
| `bccafd65` | Priority chart in rank order; a test-case icon that isn't the bug icon; requirement coverage on the dashboard |
| `6a46d857` | Run list shows the result split, not only a total; optional plan/executor/start/end columns; a legend for the dashboard's "6 / 0 / 6" |

---

## 1. Summary

"Pass rate" is computed in eight places with two different denominators and no visible definition:

| Where | Today | Denominator |
|---|---|---|
| Dashboard tile (`DashboardService.currentPassRate`, `:107`) | passed / cases with an executed result | executed ✔ |
| Dashboard trend (`DashboardService.java:77`) | passed / all results; 0.0 when nothing ran | **incl. PENDING** |
| Plan summary (`TestPlanService.java:127`) | passed / all results of all runs | **incl. PENDING** |
| Plan "pass rate per run" chart (`test-plan-detail.component.ts:309`) | computed in the browser, passed / total | **incl. PENDING** |
| Plan per-environment table (`runs-by-environment.ts:28`) | computed in the browser, passed / total | **incl. PENDING** |
| Run report and PDF (`TestRunService.java:180`, `PdfReportService.java:84`) | passed / total | **incl. PENDING** |
| Suite report (`TestSuiteService.java:148`) | passed / cases in the suite | **incl. untested** |
| Run webhook payload (`RunEventPublisher.java:55`) | passed / total | **incl. PENDING** |

The reported 10.5 % for a plan (11 passed of 105 results, 94 still pending) is progress, not quality.

**Key decision: one definition, one helper, and a second number for progress.**

- **Pass rate** = PASSED ÷ executed, where *executed* means every status except PENDING. SKIPPED
  counts as executed, since someone decided to skip it. With nothing executed the pass rate is
  **null**, shown as "–" and left as a gap in charts, never 0.
- **Progress** = executed ÷ total, shown next to the pass rate wherever both apply.

The dashboard tile already uses this definition (per case), so it doesn't change. Everything else
moves to it, and the browser stops computing its own.

**The release gate (PRD-037) keeps its stricter rule and says so.** `ReleaseReadinessService.passRate`
(`:156`) divides by `Counts.considered`, the latest result per case *including PENDING*
(`countLatest`, `:107`). That is deliberate for a gate: an unexecuted test is no evidence, so a plan
with 1 of 100 tests run must not read 100 % and go GO. Moving the gate to the display definition
would silently loosen every threshold users have set. Instead the criterion is renamed in the UI and
docs to **"Passed of in-scope tests"** with a tooltip, so it no longer claims to be the same number as
the pass-rate tiles.

## 2. Goals & Non-Goals

**Goals**
- One pass-rate definition in one backend helper, used by dashboard trend, plan summary, run report,
  PDF, suite report, webhook payload and MCP.
- Null instead of 0 when nothing was executed, with charts showing a gap.
- A progress figure (executed ÷ total) on the plan, run report and suite report.
- Every pass-rate tile has a tooltip with the definition; the trend line is not smoothed.
- Dashboard: priorities in rank order with fixed colours, a distinct test-case icon, and a
  requirement-coverage tile.
- Run list: result breakdown, optional Plan / Executor / Started / Ended columns, translated headers;
  a legend on the dashboard's recent-runs results column.

**Non-Goals**
- Configurable pass-rate formulas (PRD-009 §4).
- Changing the release gate's arithmetic (§1).
- Weighting by priority or parameter set; each result counts once, as today.
- A column-picker that persists server-side. Column choice is kept in `localStorage`, like the
  existing density toggle.

## 3. Proposed Design

### 3.1 Data model

**No migration.** All inputs already exist.

### 3.2 Backend — `PassRate` (new, `project/internal/service/`)

```java
public record PassRate(int passed, int executed, int total) {
    public static PassRate of(Collection<TestResultStatus> statuses) { … }
    /** Percent with two decimals; null when nothing was executed. */
    public Double percent() { … }
    /** Executed ÷ total in percent; null when total is 0. */
    public Double progress() { … }
}
```

A pure record with a unit test. Callers:

- `DashboardService`: trend entries use `PassRate.of(...)`; `PassRateTrendEntry.passRate` becomes
  `Double` (nullable). `overallPassRate` also becomes `Double`, null when no case has an executed
  result. Today it is `0.0`, and the tile hides 0 behind `@if (dashboard.overallPassRate > 0)`
  (`project-dashboard.component.html:41`), so a project where everything failed shows "–" instead of 0 %.
- `TestPlanService.getSummary`: `passRate` = passed ÷ executed over all counted runs, plus new
  `executed` and `progress`. `TestPlanRunSummary` gains `key`, `executed` and `passRate`, so the two
  browser calculations are deleted.
- `TestRunService` report and `PdfReportService`: the same, plus `progress` on the report DTO and a
  "Progress" cell in the PDF header row.
- `TestSuiteService` report: denominator = cases with a latest result; untested cases count toward
  progress only.
- `RunEventPublisher`: `passRate` uses the helper and the payload gains `executed`. This changes a
  webhook field's value for runs finished with pending results (typically aborted runs). The field
  name and type stay the same, and the change is listed in the release notes.

Records gain fields at the end with the usual secondary constructor for existing call sites.

### 3.3 Endpoints

No new endpoints. Changed response fields: `DashboardResponse.overallPassRate` and
`passRateTrend[].passRate` become nullable; `TestPlanSummaryResponse` adds `executed` and `progress`;
`TestPlanRunSummary` adds `key`, `executed` and `passRate`; `TestRunReportResponse` and
`TestSuiteReportResponse` add `progress`. RBAC is unchanged.

### 3.4 Frontend

**Pass rate**
- Every pass-rate display renders `null` as "–" and carries a `matTooltip` with the definition
  (`metrics.passRateHint`: "Passed ÷ executed results. Pending results are not counted.").
  Progress gets `metrics.progressHint`.
- Dashboard trend: `tension: 0` (today `0.3`, `project-dashboard.component.ts:246`),
  `spanGaps: false`, null points as gaps.
- Plan detail: the pass-rate tile shows the pass rate and, underneath, "Progress 11 / 105 (10.5 %)".
  The per-run chart and the environment table read the backend values.
- The release-readiness card relabels its criterion "Passed of in-scope tests" with its own tooltip.
- The "Latest Run Results" tile is retitled "Last completed run: *name*", which it already is in
  substance (`DashboardService.java:67-69`).

**Dashboard**
- Priority chart: labels and data built from the fixed order `CRITICAL, HIGH, MEDIUM, LOW`, skipping
  zero counts. Today it follows `Object.keys(data)` (`project-dashboard.component.ts:178`); the
  colours are already fixed (`:168`).
- Test-case icon: `checklist` instead of `bug_report` on the dashboard tile
  (`project-dashboard.component.html:20`) and the project page card
  (`project-detail.component.html:33`). The issue-tracker button (`:184`) keeps `bug_report`, which is
  then unambiguous.
- Coverage tile: shown when the project has requirements, from the existing
  `GET /traceability/coverage` (`RequirementController.java:105`, `CoverageSummaryResponse`):
  coverage %, with passing / failing / untested / uncovered underneath, linking to the requirements
  page. It is a second call from the dashboard component, not a new field on `DashboardResponse`, so
  the dashboard stays one query set and coverage keeps one owner.
- Recent runs: the results header reads "Passed / Failed / Total" (a new i18n key) instead of relying
  on the tooltip (`:114`).

**Run list**
- The `results` column (`test-run-list.component.html:102-106`, header hard-coded "Results") becomes
  a translated header and a stacked mini-bar: passed, failed, blocked, skipped, pending, using the
  same status colours as the dashboard, with the counts in a tooltip and `passed / total` as text
  beside it. `TestRunSummaryResponse` already carries all counts (`:28-33`).
- Optional columns `plan`, `executor`, `started`, `ended` (fields already on the DTO) behind a
  column toggle menu; the choice is stored in `localStorage` under `tm-run-columns`, read in a
  `try/catch` like `tc-density`.

### 3.5 MCP impact

- `get_project_dashboard`: `overallPassRate` and trend points become nullable, and the description
  states the definition (`ReportingTools.java:41-52`).
- `get_test_plan`: gains `executed` and `progress` (`TestPlanningTools.java:149`).
- No new tools. Coverage is already available through `get_traceability_matrix`.

### 3.6 Docs

The USER_MANUAL reports section gets a short "How the numbers are calculated" box (pass rate,
progress, the release gate's in-scope rule, suite report). MCP_SETUP gets the dashboard field note.

## 4. Edge Cases

- **Run with only PENDING results** (the reported SPI-Run-19): trend point is a gap, the tooltip
  says "nothing executed".
- **All executed results SKIPPED:** pass rate 0 %, which is honest: nothing passed.
- **Aborted runs:** plan totals already exclude them from effort (`countedResults`,
  `TestPlanService.java:147`). The pass rate counts their executed results; their pending results
  don't count because pending never does.
- **Parameterized cases:** each result counts once, as today.
- **Old clients reading `passRate` as a number:** null is new. The Angular models change in the same
  release; external API consumers get the field note in the release notes.
- **Coverage endpoint forbidden or failing:** the tile is hidden. The dashboard does not fail with it.

## 5. Testing

- `PassRateTest`: empty → null; all pending → null; passed/failed/skipped mix; progress with
  total 0.
- `DashboardService`: a completed run with only pending results yields a null trend point;
  `overallPassRate` null for a project with no executed results and `0.0` when all failed.
- `TestPlanService` summary: 11 passed, 94 pending, 0 failed gives pass rate 100 % and progress
  10.5 %; per-run summaries carry `key`, `executed`, `passRate`.
- Run report, PDF and suite report use the helper, with snapshot assertions on the PDF header cells.
- Webhook payload: `passRate` and `executed` for a run with pending results.
- Frontend specs: priority chart order; trend dataset has `null` and `tension: 0`; run-list mini-bar
  renders the counts; column toggles survive a reload and a throwing `localStorage`.
- Release gate unchanged: the existing `ReleaseReadinessServiceTest` passes untouched.

## 6. Effort & Risk

- **Effort:** ~3–4 days. Helper and backend callers 1.5, frontend dashboard and run list 1.5,
  docs 0.5.
- **Risk:** Low to medium. Users will see numbers change: plan and run pass rates go *up* where
  pending results were counted as failures. The release notes and tooltips explain the change. The
  webhook value change is the only external contract touched.

## 7. Acceptance Criteria

- [ ] One `PassRate` helper; no pass-rate arithmetic left in `DashboardService`, `TestPlanService`,
      `TestRunService`, `PdfReportService`, `TestSuiteService`, `RunEventPublisher` or the plan detail
      component.
- [ ] Pass rate is passed ÷ executed everywhere except the release gate, which is relabelled
      "Passed of in-scope tests".
- [ ] Nothing executed gives null / "–" / a chart gap, never 0 %.
- [ ] Plan, run report and suite report show progress next to the pass rate.
- [ ] Trend line unsmoothed; every pass-rate tile has a definition tooltip.
- [ ] Priority chart in rank order; test-case icon is `checklist`; coverage tile on the dashboard.
- [ ] Run list shows the result split and optional plan/executor/start/end columns; the dashboard
      results column has a legend; headers translated (en/de).
- [ ] MCP dashboard and plan outputs updated, with descriptions; USER_MANUAL explains the numbers.
