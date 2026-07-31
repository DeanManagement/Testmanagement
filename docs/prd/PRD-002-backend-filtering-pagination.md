# PRD-002 — Backend Filtering & Pagination

| | |
|---|---|
| **Status** | Implemented (2026-06-09) |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-09 |
| **Priority** | P1 — Scalability / correctness |
| **Target** | v1.3 |
| **Related** | REQUIREMENTS.md §12.6; REVIEW §3.3, §3.4, §4.3 |

---

## 1. Summary

Every list endpoint returns the **entire** collection for a project; the Angular client loads everything and filters in `*ngFor`. This works at today's scale but degrades sharply the first time someone imports a real test catalogue (the import feature in PRD-004 makes that imminent). This PRD adds server-side filtering, sorting, and pagination to the main list endpoints, and persists the active filters in the URL so they survive navigation.

## 2. Problem & Current State

- `TestCaseController.findAll` accepts only `folderId` and `rootOnly` — no `q`, `status`, `priority`, or `label` filters (verified).
- `TestRunController.findAll(projectId)` returns every run; only `MyTestRunController.assigned-to-me` supports a `statuses` filter (verified).
- `AuditController` is the **only** paginated endpoint.
- List components are not virtualized; a project with ~1,500 test cases serialises ~1 MB JSON per visit and renders all rows in the DOM.
- Filters (`searchTerm`, `statusFilter`, `priorityFilter`, selected folder) are component state, not URL state — they reset on back-navigation and can't be shared as deep links (REVIEW §3.4).

## 3. Goals & Non-Goals

**Goals**
- Server-side filtering + sorting + pagination on test cases, test runs, and test suites.
- Backward-compatible: existing callers without params get sensible defaults.
- Filters bound to URL query params on the frontend → persistent, shareable, survive back-navigation.
- Cap response sizes (default page size, max page size).

**Non-Goals**
- Full-text relevance ranking (that's PRD-007; this is exact/`LIKE` filtering).
- Saved/named filter presets (possible later; out of scope).
- Frontend virtual scrolling is covered in PRD-008; this PRD makes it unnecessary at the data layer.

## 4. Proposed Design

### 4.1 Backend

Use Spring Data JPA `Specification<T>` to compose predicates dynamically and return `Page<T>`.

Test cases — `GET /api/projects/{projectId}/test-cases`:

| Param | Type | Behavior |
|---|---|---|
| `q` | string | case-insensitive contains on `title` (and `testCaseKey`) |
| `status` | enum (repeatable) | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `priority` | enum (repeatable) | `LOW`/`MEDIUM`/`HIGH`/`CRITICAL` |
| `label` | string (repeatable) | matches any of the given labels |
| `folderId` | UUID | existing |
| `rootOnly` | boolean | existing |
| `updatedAfter` | ISO instant | optional |
| `page`,`size`,`sort` | Pageable | default `page=0,size=50,sort=updatedAt,desc`; `size` capped at 200 |

Response becomes `Page<TestCaseResponse>` (`content`, `totalElements`, `totalPages`, `number`, `size`).

Test runs — same pattern: `q` (name/key), `status` (repeatable), `testPlanId`, `executorId`, `startedAfter`, plus `Pageable`.

Test suites — `q` (name) + `Pageable` (smaller collections; lower priority but consistent).

Repositories extend `JpaSpecificationExecutor<T>`. Predicates built in a small `TestCaseSpecifications` helper. Reuse `LOWER(col) LIKE LOWER(concat('%',:q,'%'))` for portability across Postgres and the H2 dev DB.

### 4.2 Frontend

- Bind the filter form to `ActivatedRoute.queryParams`; on change, `router.navigate([], { queryParams, replaceState: true })` and dispatch the load action with the params.
- NgRx list slices store `totalElements` + current page; the list effect calls the paginated endpoint and the reducer replaces (not appends) the page.
- Persist last-used filters per project in `localStorage` so returning to a project restores the view.
- Paginator (`mat-paginator`) wired to `totalElements`.

## 5. Edge Cases
- Empty/whitespace `q` → ignored (no predicate).
- Unknown enum values → `400` with a clear message.
- `size > 200` → clamped to 200 (don't error).
- Default sort must be deterministic (`updatedAt desc, id asc`) to avoid page drift.
- RBAC (PRD-001) still applies — list is scoped to the caller's project membership.

## 6. Testing
- Repository/`Specification` slice tests: each filter in isolation and combined; pagination boundaries; sort direction.
- Controller tests: default params, capped size, repeatable params, invalid enum → 400.
- Frontend: query-param round-trip restores filters; paginator drives reloads.

## 7. Effort & Risk
- **Effort:** ~1 day test cases + ~1 day runs/suites backend; ~1 day frontend. (REVIEW: `S`–`M`.)
- **Risk:** Low. Additive params; defaults preserve current behavior. Main care: keeping `Specification` predicates H2/Postgres-portable.

## 8. Acceptance Criteria
- [x] Test case, run, and suite list endpoints accept the documented filters + `Pageable` and return `Page<…>` (Spring Data `PagedModel` shape: `{ content, page: { size, number, totalElements, totalPages } }`).
- [x] Omitting all params yields sensible defaults (first page, `size=50`, sort `updatedAt desc, id asc`).
- [x] `size` is capped at 200 (clamped, not an error); invalid enum values return `400` with the standard `ErrorResponse`.
- [x] Frontend filters (q/status/priority/folder/sort/page) live in the URL, survive back-navigation, and are shareable.
- [x] Specification/controller backend tests pass; frontend builds clean.

## 9. Implementation Notes (as shipped)

- **Backend:** repositories extend `JpaSpecificationExecutor`; predicates built in `TestCase/TestRun/TestSuiteSpecifications` (label filter uses a correlated `EXISTS` subquery to keep pagination counts correct). Services return `Page<…Response>`; `PageableUtils.normalize` clamps size and adds the deterministic sort + `id` tiebreaker. Default page size 50 via `@PageableDefault`. `hibernate.default_batch_fetch_size=100` mitigates N+1 when mapping a page with lazy collections.
- **Response shape:** Spring Boot 4 serializes pages via `PagedModel` (nested `page` object), so the frontend `Page<T>` model reads `result.page.totalElements` etc.
- **Frontend:** list components are URL-driven (filters/sort/page in query params, `mat-paginator` + `matSort`); NgRx slices store page metadata and `setAll` the current page. A singular `loadTestCase` action was added so detail/edit screens fetch one entity by id instead of scanning a paginated list. Selection dialogs/forms request `size=200`.
