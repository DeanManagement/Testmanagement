# PRD-036 — Time Estimates, Actuals & Plan Burn-Down

| | |
|---|---|
| **Status** | ✅ Implemented 2026-09-19 — see §8 |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-17 |
| **Priority** | P3 — useful once test plans are used for release sign-off |
| **Target** | v2.4 |
| **Related** | PRD-005 (CI ingestion), PRD-015 (parameterized cases), PRD-025 / PRD-027 (MCP), REQUIREMENTS.md §3.18 (test plans), §11.2 (dashboard) |

---

## 1. Summary

A test plan has a `targetDate` and `TestPlanService.getSummary` reports pass/fail/pending counts, but nobody can answer the question a release manager actually asks: *"will we finish testing by Friday?"* Forty pending results could be four hours or four days.

The tool has no time data at all today. Checked explicitly:
- `TestCase` has no estimate; `TestResult` has no duration or execution timestamp (only `BaseEntity.updatedAt`, which moves on any edit, including a comment).
- `TestRun.startTime` / `endTime` exist but describe the whole run.
- CI reports carry durations that are **parsed and discarded**: JUnit `<testcase time="…">` is never read in `JUnitXmlParser`, and Cucumber's `result.duration` (nanoseconds) isn't mapped in `CucumberJsonParser.Result`. `CiResult` and `ExternalTestResultRequest` have no duration field.

This PRD adds an estimate per test case, a measured duration and execution timestamp per result, remaining-effort roll-ups on runs and plans, and a burn-down chart on the plan page — computed on demand from those fields, with no snapshot tables or scheduled jobs.

## 2. Goals & Non-Goals

**Goals**
- Optional `estimateMinutes` on test cases (form, import/export, MCP).
- `durationMs` and `executedAt` on test results, captured by a timer during manual execution, editable, and filled from CI reports.
- Run and plan roll-ups: estimated total, remaining (estimates of pending results), actual spent, and "pending without estimate" count.
- A plan burn-down: remaining estimated effort per day from plan creation to target date, with an ideal line.
- On the case detail page, "median actual of the last 5 executions" next to the estimate, so estimates can be corrected from evidence.

**Non-Goals**
- Timesheets, billing, per-user utilisation or capacity planning.
- Pause/resume tracking or idle detection in the timer — the tester corrects the number if they went to lunch.
- Automatic estimate adjustment. The median is shown; a human decides.
- Estimates on suites, steps or plans directly — they roll up from cases.
- Working-day calendars or holidays in the burn-down. Calendar days only.
- Burn-down per run or per project (plan is the unit with a target date).

## 3. Proposed Design

### 3.1 Data model (next free V-number, V54+ at time of writing)
```
ALTER TABLE test_cases   ADD COLUMN estimate_minutes INT;          -- CHECK (estimate_minutes BETWEEN 1 AND 1440)
ALTER TABLE test_results ADD COLUMN duration_ms BIGINT;            -- CHECK (duration_ms >= 0)
ALTER TABLE test_results ADD COLUMN executed_at TIMESTAMP;
CREATE INDEX idx_test_results_executed_at ON test_results (test_run_id, executed_at);
ALTER TABLE test_case_versions ADD COLUMN estimate_minutes INT;    -- snapshot, PRD-011
```
All nullable, vendor-neutral. **No backfill** of `executed_at` from `updated_at` for existing results: `updated_at` changes on comment and defect-link edits, so a backfill would fabricate execution times — the same reasoning PRD-011 used for leaving `executed_version` null on old results. Old plans' burn-downs start from the migration date, and the chart says so.

Milliseconds rather than minutes on the result because CI durations are sub-second; the UI rounds.

Estimates are read live from the case, not snapshotted onto the result: an estimate is a forecast, and a corrected forecast should correct the remaining effort.

### 3.2 Backend

**`executedAt` rule** — one place, `TestRunService`, applied in `updateResult`, `bulkUpdateResultStatus`, `addResult`, and the MCP recording path (`TestResultRecordingTools` → service): set when status moves from `PENDING` to anything else; cleared when a result is set back to `PENDING`; left unchanged on PASSED → FAILED corrections (it was already executed). Reopening a run (`reopenReason`) does not touch results.

**Duration**
- `UpdateTestResultRequest` and `CreateTestResultRequest` gain optional `Long durationMs`; null = unchanged.
- `ExternalTestResultRequest` gains optional `durationMs` for the native JSON CI API.
- `CiResult` gains `Long durationMs`. `JUnitXmlParser` reads `time` (seconds, decimal; missing or unparseable → null, never a failed upload). `CucumberJsonParser.Result` adds `duration` (nanoseconds; scenario duration = sum of step durations). `CiIngestionService` writes both `durationMs` and `executedAt = now`.

**Roll-ups** — one package-private helper `EffortCalculator` with a pure function over `(status, estimateMinutes, durationMs)` rows, used by both summaries so the numbers can't disagree:
- `estimatedMinutes` = Σ estimate over all results (a parameterized case contributes once per result, i.e. per set — PRD-015 already expands them).
- `remainingMinutes` = Σ estimate over `PENDING` results.
- `actualMinutes` = Σ `durationMs` / 60 000 over non-pending results.
- `pendingUnestimated` = count of `PENDING` results whose case has no estimate — shown next to "remaining" so a low number isn't mistaken for "almost done".

`TestRunReportResponse` and `TestPlanSummaryResponse` gain those four fields. Plan roll-up excludes `ABORTED` runs, matching how `FlakyTestService` treats aborted runs as not meaningful.

**Burn-down** — `TestPlanService.getBurnDown(projectId, planId)`, computed on demand with one narrow projection query (result `createdAt`, `executedAt`, case `estimateMinutes`, run status) over the plan's runs, like `FlakyResultRow` in PRD-016:
- For each calendar day *d* from `plan.createdAt` to `max(targetDate, today)`: remaining(*d*) = Σ estimate of results with `createdAt ≤ end of d` and (`executedAt` null or `executedAt > end of d`).
- Scope added later (new runs, `addResult`) appears as a step up — that's the honest picture.
- Ideal line: from remaining at the first day with any scope, straight to 0 on `targetDate`. Omitted if no target date.
- Days are in UTC in the response; the client labels them in the browser's zone. Capped at 366 points.

### 3.3 Endpoints (RBAC via PRD-001 `@RequireProjectRole`)
| Change | Role |
|---|---|
| `POST/PUT /api/projects/{projectId}/test-cases[/{id}]` accept `estimateMinutes` | TESTER (existing) |
| `PUT /api/projects/{projectId}/test-runs/{runId}/results/{resultId}` accepts `durationMs` | TESTER (existing) |
| `GET /api/projects/{projectId}/test-runs/{id}/report` and `GET .../test-plans/{id}/summary` return effort fields | VIEWER (existing) |
| **New** `GET /api/projects/{projectId}/test-plans/{id}/burn-down` → `{ days: [{date, remainingMinutes}], idealLine: [...], scopeStartsAt, historyAvailableFrom }` | VIEWER |
| `GET /api/projects/{projectId}/test-cases/{id}` returns `medianActualMs` (last 5 non-pending results with duration) | VIEWER (existing) |
| External API `POST /api/external/projects/{projectRef}/test-runs` (native JSON, `/junit`, `/cucumber`) carries durations | API key (existing) |

### 3.4 Import / export & MCP
- CSV/JSON (PRD-004): `estimateMinutes` column / property; blank → null; invalid → per-row dry-run error.
- MCP: `create_test_case` / `update_test_case` accept `estimateMinutes`; `get_test_case` returns it plus `medianActualMs`; `record_test_result(s)` accept `durationMs`; the run and plan read tools include the effort fields. Agents that execute tests (PRD-027) can then report real durations.

### 3.5 Frontend
- `test-case-form`: "Estimate (minutes)" number input. `test-case-detail`: estimate and "median actual: 12 min (last 5)".
- `test-run-detail` execution: when a result is opened for execution, a small timer starts (client-side, from the moment it is opened); the value is sent with the status update and is editable in the result panel before or after saving. The existing keyboard-driven flow is unchanged — no extra keystroke required. Results executed without the panel open (bulk status) get no duration rather than a fake one.
- Run header: "Remaining ~3 h 20 min · 4 unestimated · Spent 5 h 10 min".
- `test-plan-detail`: same effort line, plus a burn-down line chart using Chart.js with the existing `applyChartDefaults` theme helper (`core/utils/chart-theme`), so it follows dark mode. If `historyAvailableFrom` is after plan creation, a caption explains the gap.
- Formatting pipe for minutes → "3 h 20 min" in `shared/pipes/`, localised via ngx-translate.

## 4. Edge Cases
- **No estimates anywhere**: roll-ups show "—" plus the unestimated count; burn-down renders a message instead of a flat zero line.
- **Case estimate changed mid-plan**: historical burn-down points shift, because estimates are live. Accepted and documented in the chart tooltip — snapshotting estimates per day would need a job or an estimate history table, both out of the "simple" bar.
- **Case deleted**: burn-down follows whatever the existing delete does with that case's results (the `fk_test_results_test_case` FK in V10 has no `ON DELETE CASCADE`). This PRD doesn't change delete behaviour. It only needs a query that won't fail on a result with no case.
- **Timer left running** (tester walks away): the value is editable; a duration over 8 h prompts a confirmation in the UI but is accepted by the API.
- **Result set back to PENDING**: `executedAt` cleared, `durationMs` kept (it was real effort) — actual spent still counts it.
- **Clock skew between CI and server**: `executedAt` for CI results is server receive time, not the report's timestamp, so ordering is consistent with every other server-stamped time.
- **JUnit `time` with comma decimal or negative**: treated as missing.
- **Very long plans**: burn-down capped at 366 points; older days aggregated into the first point.

## 5. Testing
- `EffortCalculator`: remaining/actual/estimated/unestimated for mixed statuses, parameterized expansion, aborted-run exclusion.
- `executedAt` transitions: PENDING→PASSED sets, PASSED→FAILED keeps, →PENDING clears; same via bulk status, `addResult`, MCP recording.
- Parsers: JUnit `time="1.234"` → 1234 ms; missing/garbage → null without failing; Cucumber nanosecond sum.
- Burn-down: fixed clock (injected `Clock`), results created and executed on known days, scope added mid-plan produces a step, no target date → no ideal line, pre-migration results excluded with `historyAvailableFrom` set.
- Median actual ignores results without duration and uses the last 5 by `executedAt`.
- Import/export round trip of `estimateMinutes`; version snapshot includes it.
- Frontend: timer value submitted with status update and editable; duration pipe formatting in `en` and `de`.

## 6. Effort & Risk
- **Effort:** ~5–7 days (schema + `executedAt` rule + parsers 2, roll-ups + burn-down 2, frontend timer/charts 2, MCP/import 1).
- **Risk:** Low. Additive nullable columns and read-side calculations. The one correctness hazard is setting `executedAt` consistently across the four result-writing paths — covered by per-path tests. Burn-down accuracy is bounded by estimate quality, which the UI makes visible via the unestimated count.

## 7. Acceptance Criteria
- [x] Test cases have an optional estimate, editable in the UI, import/export, and MCP, and captured in version snapshots.
- [x] Results record `executedAt` on leaving PENDING and an optional `durationMs` from the execution timer, manual edit, external API, JUnit `time` or Cucumber `duration`.
- [x] Run report and plan summary show estimated, remaining, actual and unestimated-pending figures from one shared calculation.
- [x] Plan detail shows a burn-down with an ideal line to the target date, computed on demand.
- [x] Case detail shows the median actual duration of recent executions.
- [x] No backfill fabricates execution times for pre-existing results.
- [x] Backend and frontend tests pass.

## 8. As Built (2026-09-19)

Built as specified, with these differences:

- **`executedAt` is decided in `TestResult.setStatus`, not in four service methods.** There are
  seven paths that set a result's status, not four: `updateStepResult` moves the parent off PENDING
  too, and `ExternalTestRunService` and `CiIngestionService` create results directly. One rule in
  the entity covers all of them and any added later. Leaving PENDING stamps it, returning clears
  it, a correction keeps it. So a CI result's `executedAt` is the moment the server stored it, which
  is what §4 asked for anyway.
- **No separate `EffortCalculator`.** The calculation is `EffortSummary.of(results)` on the record
  the responses carry, because the MapStruct run mapper (in `dto`) needs it and couldn't reach a
  package-private helper in `service`. The burn-down is a separate pure `BurnDownCalculator`.
- **Actual includes results set back to PENDING**, following §4 ("it was real effort") where §3.2
  said "non-pending".
- **The run detail response carries `effort` too,** not only the report: `get_test_run` reads the
  detail, and the run header needs no second request.
- **Each result response carries its case's live `estimateMinutes`,** so the run header recalculates
  remaining effort as a tester records results instead of reloading the run after each keystroke.
  The frontend's `effortOf` mirrors `EffortSummary`; reports and plans use the server's figures.
- **An estimate edit is not a PRD-033 content edit,** so it doesn't send an approved case back to
  review. On update `null` leaves the estimate alone and `0` clears it.
- **Burn-down response** adds `hasEstimates` (so the UI shows a message instead of a flat zero) and
  omits `idealLine` without a target date. A plan older than 366 days shows its most recent year
  rather than aggregating older days into the first point.
- **Timer:** only a result's first execution is timed, so correcting a status later never overwrites
  the recorded duration. The running time shows as a hint under the duration field. A duration over
  8 h asks first, and "no" saves the status without it. Setting only step statuses moves the result
  off PENDING on the server without a duration; the timer value goes out with the result's own
  status, including the Shift+P "all steps passed" shortcut.
- **JUnit `time`:** besides missing, negative and comma decimals, values that overflow a long
  (`1e400`) are also treated as unknown.

Tests: `TestResultExecutedAtTest` (5), `TimeTrackingWritePathsTest` (14: every write path, duration
rules, estimate and its snapshot), `CiDurationParsingTest` (10), `EffortSummaryTest` (6),
`BurnDownCalculatorTest` (10, fixed days), `EffortRollUpTest` (11: roll-ups, the burn-down query,
median, import/export), `TimeTrackingApiTest` (6: bounds and burn-down access),
`McpTimeTrackingToolsApiTest` (3); frontend `duration`, `execution-timer` and `burn-down-dates`
specs. **1066 backend tests, 35 frontend spec files (191 tests).** V60 applied on PostgreSQL 16 with
`ddl-auto=validate` and its two CHECK constraints present.

Checked in a browser against PostgreSQL: the burn-down with backdated history and an ideal line
(this found day labels shifted one day early east of UTC, fixed before commit), recording a result
with `p` (timed duration stored, remaining updated live), a correction with `f` keeping the
duration, a manual edit saved, and the case detail's estimate and median. **Not clicked through:**
the test case form's estimate field, the 8 h confirmation, the run report line and the German
strings.
