# PRD-050 — Test Case Context

| | |
|---|---|
| **Status** | ✅ Implemented 2026-09-19 — see §8 |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-19 |
| **Priority** | P2 — "where is this case used and when did it last run?" today means opening every suite and run |
| **Target** | v2.5 |
| **Related** | PRD-048 (execution and report evidence: `executed_by`), PRD-032 (latest result per environment), PRD-011 (versions), PRD-001 (RBAC) |

**Source** — change request filed as a bug on the test instance (project SPI, 2026-09-17):

| Report | Asks for |
|---|---|
| `091917d9` | On the case page: created by, created and updated dates, folder, the suites containing the case, and an execution history (run key, date, status, executor, executed version) |

---

## 1. Summary

The case page shows content, comments, parameters, versions, audit history
(`test-case-detail.component.html:145`) and the latest result per environment (`:97`, PRD-032). It
doesn't show who made the case or when, where it sits, which suites include it, or its executions. The
API already returns `createdAt`, `updatedAt`, `createdBy`, `updatedBy` and `folderId`
(`TestCaseResponse.java:23-27`), but only as ids. `TestCase.testSuites` exists as the inverse side of
the suite relation (`TestCase.java:99-100`) and is never exposed.

**Key decision: two read endpoints, not a heavier `TestCaseResponse`.** `TestCaseResponse` is also the
list payload. Adding names, a folder path and suites to it would cost a lookup per row on every list
page. The case page makes two extra calls instead: one small **context** call (names, folder path,
suites) and one paged **executions** call.

## 2. Goals & Non-Goals

**Goals**
- Case page header line: folder path, "Created by *name* on *date*", "Updated by *name* on *date*".
- A "Used in suites" list linking to each suite.
- An "Execution history" table, newest first, paged: run key and name (linked to the result), run
  status, environment, parameter set, result status, executed at, executor, executed version.

**Non-Goals**
- Filtering or exporting the history. The run comparison (PRD-038) and reports cover analysis.
- Showing suites or history in the case list.
- MCP changes. `get_test_case` stays as it is; an agent that needs history can use the run tools.
  Add a tool when an agent workflow asks for it.
- Replacing the per-environment "latest result" section; it stays as the one-line summary above the
  full history.

## 3. Proposed Design

### 3.1 Data model

**No migration in this PRD.** The executor comes from `test_results.executed_by`, which PRD-048
introduces (set when a result leaves PENDING, alongside `executedAt`,
`TestResult.java:43, 89-98`). If this PRD ships first, the executor falls back to the result's
`updatedBy`, the last user who changed it, and the column header reads "Last changed by" until
PRD-048 lands. The endpoint doesn't change shape either way.

### 3.2 Endpoints (RBAC via PRD-001)

| Method & path | Role | Returns |
|---|---|---|
| `GET /api/projects/{projectId}/test-cases/{id}/context` | VIEWER | `{ createdByName, updatedByName, folderPath: [{id, name}], suites: [{id, name}] }` |
| `GET /api/projects/{projectId}/test-cases/{id}/executions?page=&size=` | VIEWER | `Page<TestCaseExecutionResponse>` |

Both endpoints look the case up with `findByIdAndProjectId`, so a foreign id is a 404 (PRD-027 §3.5).

`TestCaseExecutionResponse(resultId, runId, runKey, runName, runStatus, environment,
parameterSetName, status, executedAt, executorName, executedVersion, durationMs)`.

### 3.3 Backend

- **Names:** `UserService.findDisplayNamesByIds` (`UserService.java:49`), the batch lookup the audit
  feed already uses (`AuditService.java:72`). One call per request, covering creator, updater and
  every executor on the page.
- **Folder path:** walk `TestCaseFolder.parent` from the case's folder to the root. Folder depth is
  small, and the walk hits folders already in the persistence context or the global batch fetch.
- **Suites:** `tc.getTestSuites()`, sorted by name. No new query.
- **Executions:** a new paged query in `TestResultRepository`:

  ```java
  @Query(value = "SELECT r FROM TestResult r JOIN FETCH r.testRun run " +
                 "WHERE r.testCase.id = :caseId AND run.project.id = :projectId",
         countQuery = "SELECT COUNT(r) FROM TestResult r " +
                      "WHERE r.testCase.id = :caseId AND r.testRun.project.id = :projectId")
  Page<TestResult> findHistory(UUID projectId, UUID caseId, Pageable pageable);
  ```

  Sorted by `run.createdAt DESC`, then `r.parameterSetName`. Default page size 20, maximum 100.
  Pending results are included, so a case planned in an open run shows as PENDING; the history is
  "where it ran or is scheduled to run", which is what the reporter asked.
- The `executedVersion` value is shown as is. When it is lower than the case's current version, the
  frontend marks it "older wording" and links it to the version view (PRD-011).
- New `TestCaseContextService` in `project/internal/service/`, called by `TestCaseController`.

### 3.4 Frontend

- **Header meta line** under the title in `.tc-hero`: folder breadcrumb (each segment linking to the
  list filtered by that folder, `?folderId=`), then created and updated with names and
  `localizedDate: 'medium'`.
- **"Used in suites"** as chips linking to `/projects/:id/test-suites/:suiteId`, or "Not in any suite".
- **"Execution history"** section after the per-environment results: a `mat-table` with a
  `mat-paginator`, the run key linking to the run page with `?result=<resultId>` (the deep link
  PRD-038 added), a status badge, and the version with the older-wording marker. Empty state: "Not
  executed yet."
- Both calls are made in `ngOnInit`, independent of the case store. A failure hides the section with
  a message and leaves the rest of the page intact.
- i18n keys in `en.json` and `de.json`.

### 3.5 MCP impact

None (Non-Goals).

### 3.6 Docs

USER_MANUAL, test case section: a short paragraph on the header meta line, suites and execution
history, and the executor fallback note until PRD-048 ships.

## 4. Edge Cases

- **Creator deleted or a service account** (API key, CI): the name map returns what `UserService`
  has. A missing name falls back to the existing `activity.system` label, as the audit history does
  (`entity-history.component.html:14`). Service accounts show their display name, e.g.
  "API key: Spielwiese".
- **Case with no folder:** no breadcrumb, "No folder".
- **Case in a deleted suite:** gone from the join table with the suite. Nothing to handle.
- **Parameterized case:** one history row per set per run, labelled with the set name.
- **CI-ingested results:** executor is the API key's service user (or `updatedBy` before PRD-048).
- **Results whose run was deleted:** deleted with the run, so the history can't show them. That is
  the same as today.
- **Very long history:** paged; the count query uses the same indexed `test_case_id`.

## 5. Testing

- `TestCaseContextServiceTest` against H2: context returns names, the folder path in root-to-leaf
  order and suites sorted by name. A case of another project returns 404.
- Executions: newest run first; pending included; parameter sets as separate rows; paging and the
  maximum page size; executor from `executed_by` when set, otherwise from `updatedBy`.
- Controller: VIEWER can read both endpoints, a non-member can't.
- Frontend specs: the meta line renders names and the breadcrumb; the history table renders a row and
  the older-wording marker; a failing context call leaves the rest of the page rendered.

## 6. Effort & Risk

- **Effort:** ~2–3 days. Backend endpoints and query 1, frontend sections 1–1.5, docs and tests 0.5.
- **Risk:** Low. Read-only, additive, no migration. The only coupling is the executor field, which
  has a defined fallback.

## 7. Acceptance Criteria

- [ ] The case page shows the folder path, created and updated with user names and dates.
- [ ] The case page lists the suites that contain the case, linked.
- [ ] The case page shows a paged execution history with run key, status, environment, parameter
      set, executed at, executor and executed version, newest first, linking to the result.
- [ ] Both endpoints are project-scoped (foreign id → 404) and readable by VIEWER.
- [ ] The executor uses `executed_by` (PRD-048), falling back to `updatedBy` with an honest label.
- [ ] en/de translations and the USER_MANUAL paragraph are present; backend and frontend tests pass.

## 8. As Built (2026-09-19)

Built as specified, with these differences:

- **No executor fallback.** PRD-048 landed first, so the executor is `executed_by` and the column
  reads "Executed by". V70 already backfilled `executed_by` from `updated_by` for results executed
  before it, so older results have a name too; a pending result, or one uploaded anonymously, has
  none.
- **The two endpoints live in `TestCaseContextService`** and `TestCaseController`, as proposed. The
  history is one query with a fetch join on the run and a separate count query, sorted by run
  creation, then parameter set; page size defaults to 20 and is capped at 100.
- **The case page** gets two self-contained components: `app-case-context` in the title card and
  `app-execution-history` after the per-environment results. Each loads on its own, and a failure
  shows a one-line note instead of the section.
- **The context reloads when the case changes**, so moving the case to another folder in the editor
  shows the new path on return.
- **"Older wording"** is shown next to the version and explained in a tooltip; the case's own
  version history section on the same page is where the comparison lives.

Not tested in a browser.
