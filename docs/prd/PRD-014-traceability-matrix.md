# PRD-014 — Requirements & Traceability Matrix

| | |
|---|---|
| **Status** | ✅ Implemented (2026-07-31) |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-09 |
| **Priority** | P3 — v2.0, compliance-driven |
| **Target** | v2.0 |
| **Related** | REQUIREMENTS.md §13.2 (was PRD-009 §2.5); pairs with PRD-010/PRD-011 |

---

## 1. Summary

Regulated teams must show that every requirement is covered by tests and what the latest result is. This PRD adds a lightweight `Requirement` entity, a many-to-many link to test cases, a matrix view (requirements × test cases with the latest result status in each cell), and a coverage report highlighting uncovered requirements.

Pairs naturally with issue-tracker integration (PRD-010) and versioning (PRD-011) for regulated contexts; build with a real compliance driver.

## 2. Goals & Non-Goals

**Goals**
- `Requirement` per project (`externalId`, `title`, `description`).
- Many-to-many between requirements and test cases.
- Matrix view: requirements × linked test cases, each cell showing the latest result status (from the most recent run).
- Coverage report: requirements with zero linked cases, and requirements whose latest results are failing/untested.

**Non-Goals**
- Importing requirements from external ALM tools (manual + CSV import later).
- Requirement versioning/workflow.
- Linking requirements to runs/plans directly (coverage is via test cases).

## 3. Proposed Design

### 3.1 Data model (migration V39)
- `requirements`: `id`, `project_id` (FK, indexed), `external_id`, `title`, `description`, timestamps, audit columns. Unique `(project_id, external_id)`.
- `requirement_test_cases`: `requirement_id`, `test_case_id`, unique pair (join table).

### 3.2 Endpoints (RBAC via PRD-001)
- CRUD `GET/POST/PUT/DELETE /api/projects/{projectId}/requirements` (read VIEWER, write TESTER).
- `POST /api/projects/{projectId}/requirements/{id}/test-cases` / `DELETE .../{tcId}` — link/unlink (TESTER).
- `GET /api/projects/{projectId}/traceability` — matrix payload: requirements, their linked cases, and each case's latest result status (computed from latest `TestResult` per case across runs). Paginated by requirement (reuse `PageableUtils`).
- `GET /api/projects/{projectId}/traceability/coverage` — summary: total requirements, # uncovered (no cases), # with failing/untested latest result, coverage %.

### 3.3 Latest-status computation
- Reuse the existing "latest result per test case" logic (already used by suite reports) to avoid a new query path; cell status ∈ `PASSED|FAILED|BLOCKED|SKIPPED|UNTESTED`.

### 3.4 Frontend
- Requirements list (CRUD) under the project.
- Matrix view: sticky-header table (requirements as rows, linked cases as columns or a compact per-requirement coverage row for large projects), cells color-coded by status; link cells to the test case.
- Coverage report: KPI cards (coverage %, uncovered count) + a list of uncovered/failing requirements.

## 4. Edge Cases
- Large matrices: cap columns / switch to a per-requirement summary row beyond a threshold; paginate requirements.
- A test case linked to multiple requirements counts toward each.
- Deleting a test case removes its matrix cells (join rows cascade); requirement remains (may become uncovered).

## 5. Testing
- Requirement CRUD + link/unlink; uniqueness on `(project_id, external_id)`.
- Matrix returns correct latest-status per cell for seeded runs/results.
- Coverage report counts uncovered and failing requirements correctly.
- Authz: VIEWER read-only; membership scoping.

## 6. Effort & Risk
- **Effort:** ~1.5–2 weeks (model, matrix/coverage queries, two views).
- **Risk:** Medium — matrix rendering/perf for large projects. Mitigated by pagination + a summary fallback.

## 7. Acceptance Criteria
- [ ] Requirements are CRUD-able and link to test cases (membership-scoped, role-gated).
- [ ] Matrix shows latest result status per requirement/case.
- [ ] Coverage report lists uncovered + failing requirements with a coverage %.
- [ ] Model, query, and authz tests pass.


## 8. As Built (2026-07-31)

**Coverage means a linked test has *passed*, not that a test is linked.** This is the one judgement
that shapes everything else. Counting "has a test attached" would let a project report 100% coverage
having never executed anything — the exact false assurance a traceability report exists to prevent.
So `coveragePercent` counts only requirements whose linked tests passed most recently.

**Row status is the worst cell.** A requirement is only as proven as its weakest test, so one
failing case among five passing ones reads FAILED, not covered. Severity order is
FAILED → BLOCKED → UNTESTED → SKIPPED → PASSED.

**UNTESTED is a first-class state**, distinct from UNCOVERED. "A test is linked but has never run"
is the quiet failure mode in traceability: it looks covered on the page and proves nothing. Both
render in warning or danger colours rather than neutral.

**Latest status reuses the completed-run query** the suite report already uses (§3.3) rather than
introducing a second definition of "latest", which would eventually disagree with itself.

**Requirements are deliberately thin** — external id, title, description. This is not a
requirements-management tool; it exists so coverage can be demonstrated, and anything richer belongs
in the system of record. External ids are unique per project, since two projects may legitimately
both have REQ-1.

**Tests: 14.** Per-project uniqueness, idempotent linking, cross-project link rejection, and the
coverage semantics case by case — uncovered, linked-but-never-run, passing, latest-result-wins, one
failure sinking a row, an untested case among passing ones, per-cell status, the percentage counting
only proven requirements, and an empty project not dividing by zero.

### Deferred
- CSV/ALM import of requirements (§2 non-goal).
- Column-capping for very large matrices: the current view is per-requirement rows with their linked
  cases, which does not blow up the way a full requirements × cases grid would.
