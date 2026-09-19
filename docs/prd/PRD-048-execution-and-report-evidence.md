# PRD-048 — Execution and Report Evidence

| | |
|---|---|
| **Status** | ✅ Implemented 2026-09-19 — see §8 |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-19 |
| **Priority** | P2 — the run report is what auditors and stakeholders receive; today it cannot answer "who ran what, when, against which wording" |
| **Target** | v2.5 |
| **Related** | PRD-008 (bulk result cascade), PRD-011 (versions), PRD-033 (unapproved wording flag), PRD-036 (`executedAt`), PRD-047 (defects on results) |

**Source** — change requests filed as bugs on the test instance (2026-09-17):

| Report | Asks for |
|---|---|
| `63fa1043` | Test case keys (SPI-n) in the execution view, run result list, run report, PDF, suite detail |
| `f427f84c` | Run report and PDF as test evidence: key, executor and time, step results, screenshots, defects, version, plan |
| `7f06071e` | Execution view: show the case's preconditions and description |
| `abb85389` | Execution view: overall "Passed" optionally cascades to the steps |

---

## 1. Summary

The execution view and the run report identify a case only by its title
(`test-run-detail.component.html:179,191,393`, `test-run-report.component.html:93`, `PdfReportService`
results table), although every case has a key. `TestResultResponse` carries `testCaseTitle` but no
key. The report lists case, status, comment and (PDF only) defect link; it has no executor, no step
table, no screenshots, and no plan. A tester executing a case sees its steps but not its
preconditions or description, and setting the overall status to Passed leaves every step PENDING
unless the bulk action is used (`BulkResultStatusRequest.cascadeSteps`, `TestRunService.java:542`).

This PRD adds the missing evidence to the result, the execution view and the report, in one pass
over `TestResultResponse`, the run detail and `PdfReportService`.

### Key decision: record `executedBy`, don't infer it from auditing

`TestResult` has `executedAt` (PRD-036, set in `setStatus`) and the inherited `updatedBy`. `updatedBy`
changes on any later edit — a comment fixed by a lead, a duration corrected — so it names the last
editor, not the tester. A report that is evidence must not change its "executed by" when someone
edits a typo. So `executed_by` becomes a column, set together with `executedAt` when the status first
leaves PENDING and cleared with it when set back to PENDING.

## 2. Goals & Non-Goals

**Goals**
- The case key next to the title in: execution view list and header, run result panels, run report
  (screen and PDF), suite detail; the execution view search matches keys.
- Per result in report and PDF: key, status, executed by, executed at, executed version, comment,
  defect link (screen report too).
- Report header: the run's plan (name, link).
- Report options "include steps" (step table with status and actual result) and "include
  screenshots" (thumbnails in the PDF, links on screen).
- Execution view: a collapsible "Preconditions & description" block above the steps.
- Setting a single result to PASSED or SKIPPED offers to set its PENDING steps to the same status.

**Non-Goals**
- Changing the pass-rate definition in the report: a separate metrics PRD (report `fe290f1a`).
- Showing the preconditions/description *as they were* at execution time (see §3.4).
- Cascading FAILED or BLOCKED: a failure belongs to a specific step, which the tester must pick.
- Report templates, custom branding, or Word export.

## 3. Proposed Design

### 3.1 Data model (next free V-number)

```sql
ALTER TABLE test_results ADD COLUMN executed_by UUID;
-- Best effort for existing rows: the last editor is the best information there is.
UPDATE test_results SET executed_by = updated_by WHERE executed_at IS NOT NULL;
```

No FK to `users` (other audit UUIDs have none either; a deleted user must not block anything).

### 3.2 Backend

- **`TestResult.setStatus(status, actor)`**: an overload next to the existing method that also sets or
  clears `executedBy` in the same branch that handles `executedAt`. Every path that records a status
  with a known user passes it: `TestRunService` single and bulk updates, MCP recording tools, external
  API (service user), CI ingestion (service user, or null for anonymous CI keys).
- **`TestResultResponse`** gains `testCaseKey`, `testCaseDescription`, `testCasePreconditions`,
  `executedBy` (UUID) and `executedByName` (resolved in the service, like reporters on bug reports).
  A compatibility constructor keeps existing callers compiling (house convention).
- **`UpdateTestResultRequest`** gains `boolean cascadeSteps` (default false). When true and the status
  is PASSED or SKIPPED, only steps still **PENDING** take the status; recorded step outcomes are never
  overwritten. With FAILED/BLOCKED the flag is rejected (400). The bulk endpoint keeps its current
  "all steps" semantics; its UI already asks.
- **`TestRunReportResponse`** gains `testPlanId`, `testPlanName`. The report endpoint gains
  `?steps=true&screenshots=true`; without `steps` the step results are omitted from the payload, which
  keeps today's size for the common case.
- **`PdfReportService`**: results table gets Key, Executed by, Executed at, Version columns; with
  `steps`, a nested step table per result (number, action, status, actual result); with
  `screenshots`, the step's screenshot embedded as a scaled `data:` image (bytes are in the DB).
  Screenshots are capped at 200 per PDF; beyond that the PDF lists "n more screenshots, see the app".

### 3.3 Endpoints (RBAC via PRD-001)

| Method & path | Role | Change |
|---|---|---|
| `GET /test-runs/{id}/report?steps=&screenshots=` | VIEWER | options, plan, new result fields |
| `GET /test-runs/{id}/report/pdf?steps=&screenshots=` | VIEWER | same |
| `PUT /test-runs/{id}/results/{resultId}` | TESTER | `cascadeSteps` |

### 3.4 Frontend

- **Keys**: a `key` prefix (monospace, muted) before the title in the four run templates and the
  suite detail (`test-suite-detail.component.html:42`, which has `tc.key` but shows only the title).
  `filteredResults` (`test-run-detail.component.ts:498`) also matches the key.
- **Preconditions & description**: a collapsed-by-default panel above the step list, open by default
  when the case has preconditions. Shown from the **live** case, like the steps, which also come from
  the live case (step results point at live steps). When `executedVersion` differs from the case's
  current version, the panel says "The case changed since this result was recorded" with a link to
  the version comparison. Snapshot text would disagree with the live steps shown right below it.
- **Cascade**: choosing PASSED or SKIPPED on a result with PENDING steps shows a one-line inline
  prompt "Also mark the 4 pending steps as Passed?" with Yes / No; the choice is not remembered.
- **Report screen**: new columns (key, executed by, at, version, defect link), a plan link in the
  header, two toggles "Steps" and "Screenshots" that reload the report and are passed to the PDF
  download.
- i18n keys in `en.json` / `de.json`.

### 3.5 MCP impact

- `get_test_run` results gain `testCaseKey`, `executedBy`, `executedAt`.
- `record_test_result` gains optional `cascadeSteps` with the same PENDING-only rule.

### 3.6 Docs

USER_MANUAL: run report options and columns, what "executed by/at" mean (first move out of PENDING;
older results backfilled from the last editor), the cascade prompt.

## 4. Edge Cases

- **Result set PASSED, later FAILED** → `executedAt`/`executedBy` keep the first execution (same rule
  as `executedAt` today); the audit log has the change.
- **Result reset to PENDING** → both cleared; the next status records a new executor.
- **CI/external results** → executor is the API key's service user, shown as "API: <key name>".
- **Deleted user** → "Unknown user" in report and PDF.
- **Step without screenshot** → no image cell; the PDF never embeds step *reference* images, only
  execution screenshots.
- **Very large runs with screenshots** → cap (§3.2); the screen report shows thumbnails lazily.
- **Parameterized results** → the key is the case key; the set name is already shown (PRD-015).
- **Cascade with no pending steps** → no prompt.

## 5. Testing

- `TestResult`: `executedBy` set on first leaving PENDING, kept on later changes, cleared on PENDING.
- Every status path passes the actor: single, bulk, MCP, external, CI (service user).
- Single-result cascade: PENDING steps only; FAILED/BLOCKED with the flag → 400.
- Report DTO: plan fields; steps omitted without `?steps`; PDF contains key/executor columns, a step
  table with `steps`, an embedded image with `screenshots`, and the cap message beyond 200.
- Migration and backfill on H2 and PostgreSQL (`ddl-auto=validate`).
- Frontend: key shown and searchable; preconditions panel and changed-version note; cascade prompt
  appears only for PASSED/SKIPPED with pending steps.

## 6. Effort & Risk

- **Effort:** ~4–5 days. Keys 0.5, `executedBy` across paths 1, report/PDF options 1.5, execution view
  panel and cascade 1, docs 0.5.
- **Risk:** Low. Additive fields; the only behaviour change is an opt-in prompt. The backfilled
  executor is approximate and documented.

## 7. Acceptance Criteria

- [ ] Case keys appear in the execution view, run result list, run report, PDF and suite detail; the execution search matches keys.
- [ ] Each result records who executed it and when; report and PDF show executor, time and executed version.
- [ ] The report shows its plan and offers "with steps" and "with screenshots", on screen and in the PDF.
- [ ] The execution view shows the case's preconditions and description, noting when the case changed since execution.
- [ ] A single result set to PASSED/SKIPPED can cascade to its PENDING steps only.
- [ ] MCP `get_test_run` shows key and executor; `record_test_result` accepts `cascadeSteps`.
- [ ] Migration applies on H2 and PostgreSQL; tests pass; en/de translations and USER_MANUAL updated.

## 8. As Built (2026-09-19)

Built as specified, with these differences:

- **Migration V70.** `executed_by` has no FK; the backfill copies `updated_by` where `executed_at`
  is set.
- **`setStatus(status, executor)` replaces the one-argument method** instead of sitting next to
  it, so no path can record a status without saying who executed it. The paths are single and bulk
  updates, adding a result, a step change that recomputes the result, the external API and CI
  uploads (the key's service user), and the MCP tools (the key).
- **Executor names** are looked up once per run in `TestRunMapper`, which now has `UserService`
  (setter-injected; MapStruct generates the subclass). A deleted user reads "Unknown user".
- **The report JSON always carries the steps**, as it already did; there is no `?steps=` on it. The
  Steps and Screenshots toggles on the report page switch the display, and are passed to the PDF as
  `?steps=&screenshots=`. Screenshots imply steps.
- **The PDF** is built by a new `RunReportHtml`, split out of `PdfReportService`. It embeds PNG,
  JPEG and GIF screenshots (the renderer draws no WebP) up to 200, then counts the rest.
- **`TestResultResponse` also carries `testCaseVersion`**, the case's current version, for the "the
  case changed since this was recorded" note, which links to the case page and its version history.
- **The suite detail's key** needed a `key` on the suite's case summary; the PRD assumed it was
  there.
- **The cascade prompt** appears after the status is saved: Yes sends the same status again with
  `cascadeSteps`.

Found on the way: the bug-report form spec from PRD-045 stubbed `EnvironmentApiService` without
`getActive`, which made the frontend test run exit 1 through Vitest's "unhandled errors" although
every test passed. Fixed.

Not tested: the V70 backfill on H2 or PostgreSQL, and the PDF's look (the tests check its HTML and
that the PDF renders).
