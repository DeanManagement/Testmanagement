# PRD-047 — Defects in Execution and Planning

| | |
|---|---|
| **Status** | ✅ Implemented 2026-09-19 — see §8 |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-19 |
| **Priority** | P2 — testers find defects while executing; today linking them is API-only and the plan says nothing about them |
| **Target** | v2.5 |
| **Related** | PRD-034 (bug reports from sessions), PRD-037 (release gate counts open blocker bugs), PRD-045 (bug triage: key, statuses), PRD-010/029 (external issue links) |

**Source** — change requests filed as bugs on the test instance (2026-09-17):

| Report | Asks for |
|---|---|
| `4cdea940` | Report or link a defect directly from a FAILED result |
| `61b53bde` | Bug ↔ run/step linking in the UI both ways, defects on the test plan, clickable "Failed" counts |
| `02e7399d` | Defect overview on the dashboard: counts, priority/status split, trend |

---

## 1. Summary

A bug report can already point at the result it was found in: `bug_reports.test_result_id` and
`test_run_id` (`BugReport.java:67-73`), set from the "Report Bug" button in the execution view. That
button only appears when the *result's overall status* is FAILED or BLOCKED
(`test-run-detail.component.html:285`), so a tester who marks only a step as Failed never sees it —
the likely cause of report `4cdea940`. Everything else is missing: reporting from a step, linking an
*existing* bug, seeing a result's case from the bug, defects on the plan, and any defect numbers on
the dashboard.

This PRD makes defects visible where testing happens: in the execution view (per result and per
step), on the plan, and on the project dashboard.

### Key decision: keep the origin link, add a join table for further occurrences

"Link an existing bug" is mostly the regression case: bug SPI-BUG-12 was found in run 17 and the same
case fails again in run 19. With only the single `test_result_id` FK, linking it to run 19 would move
the bug off run 17 and lose where it was found. A pure many-to-many replacement would change the
meaning of every existing API and MCP field.

So:
- `bug_reports.test_result_id` stays the **origin** ("found in"), unchanged in API and MCP.
- A new `bug_report_links` table records **further occurrences** (result, optionally step).
- "Defects of a result/plan" = origin **or** linked.

## 2. Goals & Non-Goals

**Goals**
- "Report bug" on a result when it or **any of its steps** is FAILED/BLOCKED, and on each failed or
  blocked step, prefilled with the step, its actual result and the case.
- "Link existing bug" on a result or step: search by key/title, one click.
- An editable defect link (`defectLink`) field on the result in the execution view.
- Bug detail: links to its case, run, result and step (origin and occurrences); unlink an occurrence.
- Test plan: a Defects section listing bugs found in or linked to the plan's runs, with status and
  priority; Failed counters that open the failing results.
- Project dashboard: open defects by priority, status split, created-vs-resolved trend (12 weeks).

**Non-Goals**
- Changing the bug status model or adding keys: PRD-045. This PRD uses whatever key PRD-045 delivers
  and falls back to the title.
- Syncing defects with external trackers: external issue links (PRD-010/029) stay separate.
- Release/plan filters on the dashboard defect widget. The plan page shows plan-scoped defects; the
  dashboard is project-wide.
- A go/no-go light: that is PRD-037's release gate, which already counts open blocker bugs.

## 3. Proposed Design

### 3.1 Data model (next free V-number)

```sql
ALTER TABLE bug_reports ADD COLUMN step_result_id UUID REFERENCES step_results(id) ON DELETE SET NULL;
ALTER TABLE bug_reports ADD COLUMN resolved_at TIMESTAMP;

CREATE TABLE bug_report_links (
    id UUID PRIMARY KEY,
    bug_report_id UUID NOT NULL REFERENCES bug_reports(id) ON DELETE CASCADE,
    test_result_id UUID NOT NULL REFERENCES test_results(id) ON DELETE CASCADE,
    step_result_id UUID REFERENCES step_results(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
    created_by UUID, updated_by UUID,
    CONSTRAINT uq_bug_report_links UNIQUE (bug_report_id, test_result_id)
);
CREATE INDEX idx_bug_report_links_result ON bug_report_links(test_result_id);

-- Best-effort backfill: bugs already resolved get their last update time.
UPDATE bug_reports SET resolved_at = updated_at WHERE status IN ('RESOLVED', 'CLOSED', 'WONTFIX');
```

- `step_result_id` on the bug is the origin step (optional).
- `resolved_at` is a column, not derived from the audit log. The audit stores status changes as free
  text in `details` (`BugReportService.java:185-188`), which is not queryable, and a trend over
  months must be one indexed query. It is set when the status enters a resolved state
  (RESOLVED/CLOSED/WONTFIX, or PRD-045's equivalents) from an open one, and cleared on reopen. The
  backfill is approximate and documented as such.
- `testRun` for a link is derived from the result; no second FK.

### 3.2 Backend

- **`BugReportService`**
  - `create`: accepts `stepResultId`; it must belong to the given result (400 otherwise).
  - `link(projectId, bugId, testResultId, stepResultId)` / `unlink(projectId, bugId, linkId)`: all ids
    resolved with project-scoped lookups (foreign → 404, PRD-027 §3.5). Linking the origin result
    again is a no-op. Audited as UPDATED with details `"linked to <run key> / <case key>"`.
  - Status change sets/clears `resolvedAt` (§3.1).
  - `findByTestResult` returns origin **and** linked bugs, each with `linkKind: ORIGIN | LINKED`.
- **`BugReportResponse`** gains `testCaseId`, `testCaseKey`, `stepResultId`, `stepNumber` and
  `links[]` (linkId, runId, runKey, resultId, caseKey, stepNumber). The case link on the bug detail
  needs `testCaseId`, which it lacks today (`bug-report-detail.component.html:103-109` shows the title
  as plain text).
- **Plan defects**: `GET /api/projects/{p}/test-plans/{id}/bug-reports` — bugs whose origin result or a
  link result belongs to a run of the plan, newest first. One JPQL query with `EXISTS` on both paths.
- **Dashboard**: `GET /api/projects/{p}/dashboard/defects` →
  `{ open, byStatus{…}, openByPriority{…}, trend[{weekStart, created, resolved}] }` for 12 weeks,
  counted from `created_at` and `resolved_at`. Separate endpoint so the main dashboard stays cheap
  for projects without bug reports.

### 3.3 Endpoints (RBAC via PRD-001)

| Method & path | Role |
|---|---|
| `POST /bug-reports` (existing) with `stepResultId` | TESTER |
| `POST /bug-reports/{id}/links` `{testResultId, stepResultId?}` | TESTER |
| `DELETE /bug-reports/{id}/links/{linkId}` | TESTER |
| `GET /test-plans/{id}/bug-reports` | VIEWER |
| `GET /dashboard/defects` | VIEWER |
| `PUT .../results/{resultId}` (existing) — `defectLink` already accepted (`UpdateTestResultRequest.java`) | TESTER |

All are 404 when bug reports are disabled for the project (existing rule for the bug endpoints).

### 3.4 Frontend

- **Execution view (`test-run-detail`)**
  - "Report bug" shows when the result is FAILED/BLOCKED **or** any step is. Prefill (query params,
    as today in `reportBug()` at `test-run-detail.component.ts:716`): add `stepResultId`, step number
    and action into "Steps to reproduce", the step's `actualResult` into "Actual behaviour".
  - A small bug icon on each failed/blocked step row: "Report bug" / "Link existing bug".
  - "Link existing bug": a dialog searching the project's bugs (title/key), listing open ones first.
  - Linked bugs list (`test-run-detail.component.html:294`) shows origin and linked bugs, with key,
    status chip and step number.
  - A `defectLink` input under the comment (today it is only rendered as a link at `:409`).
- **Bug detail**: the case title becomes a link to the case; origin step shown ("Step 3"); an
  "Occurrences" list of links with run key → result deep link (`?result=`, PRD-038) and an unlink
  action.
- **Test plan detail**: a "Defects" section (key, title, priority, status, found in run); the Failed
  number in the per-run table links to `/test-runs/{id}?status=FAILED`.
- **Run detail** reads `?status=` and pre-selects that result filter (it already filters by text in
  `filteredResults`, `test-run-detail.component.ts:498`).
- **Project dashboard**: a Defects tile (open count, click → bug list filtered to open) and a small
  chart pair: open by priority (bar), created vs resolved per week (lines). Only when bug reports are
  enabled.
- i18n keys in `en.json` / `de.json`.

### 3.5 MCP impact

- `get_bug_report` returns `links` and the origin step.
- New write tool `link_bug_report` (bug id/key, result id, optional step number), TESTER, same
  validation as REST. No unlink tool: removing evidence stays a UI action.
- `create_bug_report` accepts an optional step number with the result.

### 3.6 Docs

USER_MANUAL: "Reporting defects while testing" (result vs step, link existing, defect link field),
plan Defects section, dashboard widget, and what "resolved" counts as.

## 4. Edge Cases

- **Step result deleted** (step removed from a case, V28 SET NULL chain) → link keeps the result,
  loses the step number.
- **Result deleted with its run** → links cascade; a bug whose origin run is deleted keeps existing
  with `test_result_id` null (today's behaviour).
- **Linking the same bug twice to one result** → unique constraint; the service returns the existing
  link (idempotent), not 409.
- **Bug of another project** in the link dialog → never offered; a forged id is 404.
- **Bug reports disabled** → no buttons, sections or widget; endpoints 404.
- **Reopened bug** → `resolved_at` cleared; it leaves the "resolved" line of the trend for its old week.
  The trend shows resolutions that still stand, which is the useful number.
- **Result status later changed to PASSED** → links stay: the bug was seen there.

## 5. Testing

- `BugReportService`: link/unlink scoping (foreign bug/result/step → 404), step must belong to the
  result, idempotent re-link, origin + linked returned by `findByTestResult`, `resolvedAt` set/cleared
  on status transitions.
- Plan defects query: origin in plan, link in plan, neither, both (no duplicate).
- Dashboard defects: weekly buckets, empty weeks present, reopened bug not counted as resolved.
- Migration on H2 and PostgreSQL (`ddl-auto=validate`), backfill of `resolved_at`.
- MCP `link_bug_report` happy path and scoping.
- Frontend: report-bug visibility with a failed step on a PENDING result; step prefill; link dialog;
  `?status=FAILED` pre-filter.

## 6. Effort & Risk

- **Effort:** ~5–6 days. Schema and service 1.5, plan and dashboard endpoints 1, execution view
  (buttons, dialog, prefill, defect link) 1.5, bug detail and plan section 1, dashboard widget 0.5,
  docs 0.5.
- **Risk:** Low–medium. Additive schema; the origin FK keeps every existing path working. The only
  behaviour change is the report-bug button appearing for failed steps.

## 7. Acceptance Criteria

- [ ] "Report bug" appears when a result or any of its steps is FAILED/BLOCKED, and per failed step, with step and actual result prefilled.
- [ ] An existing bug can be linked to a result or step from the execution view; the bug keeps its origin.
- [ ] The execution view shows and edits the result's defect link.
- [ ] The bug detail links to its case, run, result and step, and lists and unlinks further occurrences.
- [ ] The test plan shows its defects; Failed counts open the run filtered to failed results.
- [ ] The dashboard shows open defects by priority and a created-vs-resolved trend backed by `resolved_at`.
- [ ] MCP `link_bug_report` exists; `get_bug_report` shows links.
- [ ] Migration applies on H2 and PostgreSQL; backend and frontend tests pass; en/de translations and USER_MANUAL updated.

## 8. As Built (2026-09-19)

Built as specified, with these differences:

- **Migration V69.** The `resolved_at` backfill covers RESOLVED and CLOSED: PRD-045's V66 had
  already turned WONTFIX into CLOSED.
- **No `linkKind` field.** A bug in a result's list was found there when its `testResultId` is that
  result, and seen there again otherwise; the SPA compares the two. `findByTestResult` is the
  list endpoint's `testResultId` filter, which now matches the found-in result or a link.
- **Bug reports switched off** is still a 403 from these endpoints, the existing rule, not a 404.
  The plan's Defects section and the dashboard widget render nothing then.
- **The dashboard trend** has 12 weeks starting on Monday (UTC), the current one included, and
  counts `resolved_at` from the first move out of the open statuses. RESOLVED to CLOSED keeps the
  first time, and reopening clears it. The open-defects tile opens the bug list filtered to the
  open statuses.
- **Unlinking asks no confirmation**: it removes a record of an occurrence, and linking again
  restores it.
- **MCP:** `create_bug_report` and `link_bug_report` take a `stepNumber` (from 1), not a step
  result id, since that is how `get_test_run` numbers steps. `get_bug_report` returns `stepNumber`
  and `occurrences`.
- **A bug found on the way and fixed:** updating a result with only a status (what the SPA does
  when a tester clicks one) set its comment and defect link to null, wiping what CI or an agent had
  written. Null now leaves them alone and an empty string clears them. The MCP result tools had
  worked around it by reading the result and merging; that code is gone.

Not tested: PostgreSQL, and the dashboard charts themselves (jsdom has no canvas).
