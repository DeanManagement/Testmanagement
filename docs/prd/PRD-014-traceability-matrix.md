# PRD-014 — Requirements & Traceability Matrix

| | |
|---|---|
| **Status** | Proposed |
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
