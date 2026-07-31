# PRD-007 — Server-Side Full-Text Search

| | |
|---|---|
| **Status** | Implemented (2026-06-09) |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-09 |
| **Priority** | P2 — Usability |
| **Target** | v1.3+ / v2.0 |
| **Related** | REQUIREMENTS.md §13.8; REVIEW §3.9, §4.13 |

---

## 1. Summary

The Cmd-K / Ctrl-K command palette (shipped in v1.3) fuzzy-searches only data already loaded in the NgRx store. This PRD adds a real server-side search endpoint backed by Postgres full-text indexes so users can find anything across the projects they can access — not just what's currently in memory. It wires in behind the existing palette as a fallback.

## 2. Goals & Non-Goals

**Goals**
- `GET /api/search?q=` returning grouped results across test cases, test runs, bug reports, and projects.
- Postgres `tsvector` + GIN indexes for fast, language-tolerant matching.
- Results scoped to the caller's project membership (PRD-001).

**Non-Goals**
- Searching inside step text / comments (could extend later).
- A standalone search results page (palette is the surface; a page can come later).
- External search engine (Elasticsearch) — overkill at this scale.

## 3. Proposed Design

### 3.1 Indexes (migration V34)
Add a generated `tsvector` column per searchable table and a GIN index, e.g. for `test_cases`:

```sql
ALTER TABLE test_cases ADD COLUMN search_vector tsvector
  GENERATED ALWAYS AS (
    to_tsvector('simple', coalesce(test_case_key,'') || ' ' || coalesce(title,'') || ' ' || coalesce(description,''))
  ) STORED;
CREATE INDEX idx_test_cases_search ON test_cases USING GIN (search_vector);
```

Repeat for `test_runs` (key, name, environment), `bug_reports` (key, title, description), `projects` (project_key, name, description). Use the `simple` configuration (not `english`) so German and mixed-language content works.

> H2 dev note: H2 lacks `tsvector`. Guard the migration to Postgres (Flyway `postgresql/` path or vendor check) and fall back to `LIKE` queries under the dev profile so dev startup isn't broken.

### 3.2 Endpoint
- `GET /api/search?q=&types=testCase,testRun,project,bugReport&projectId=&limit=20`
- Builds a `plainto_tsquery('simple', :q)`; returns top results per type with `{ type, id, key, title, projectId, snippet }`, grouped.
- Membership filter applied so users only see results in their projects (system admins see all).

### 3.3 Frontend
- Cmd-K palette calls the server endpoint when the client-side fuzzy match yields `< 5` results (or after a short debounce on every keystroke), merging server results below local ones.

## 4. Edge Cases
- Short/empty `q` (`< 2` chars) → return nothing (avoid scanning).
- Ranking: order by `ts_rank` desc, then `updatedAt` desc.
- Deleted/again-permissioned entities filtered post-query by membership.
- Dev profile uses `LIKE` fallback transparently.

## 5. Testing
- Migration applies on Postgres; GIN index used (verify with `EXPLAIN`).
- Endpoint returns expected grouped hits for seeded data; respects `types` and `projectId`.
- Membership scoping: non-member's items excluded.
- Dev `LIKE` fallback returns sane results.

## 6. Effort & Risk
- **Effort:** ~1 week (REVIEW `M`).
- **Risk:** Medium — the Postgres/H2 split is the main complexity. Generated columns keep the index maintenance-free.

## 7. Acceptance Criteria
- [x] `tsvector` columns + GIN indexes exist on the four tables (Postgres) via the vendor migration.
- [x] `GET /api/search` returns membership-scoped, grouped, ranked results.
- [x] Cmd-K palette falls back to server search and merges results (deduped, debounced).
- [x] Dev/test (H2) works via the `LIKE` fallback.
- [x] Endpoint + scoping tests pass (161 backend tests green; frontend builds clean).

## 8. Implementation Notes (as shipped)

- **Migration:** `db/specific/postgresql/V38__add_search_vectors.sql` adds generated `tsvector` columns + GIN indexes on `test_cases`, `test_runs`, `bug_reports`, `projects` (bug reports have no key, so their vector is title+description). It lives in a **sibling** vendor path (not under `db/migration`, which Flyway scans recursively) and is selected via `spring.flyway.locations=classpath:db/migration,classpath:db/specific/{vendor}` — so H2 (`db/specific/h2`, absent) skips it.
- **Service:** `SearchService` (EntityManager) runs Postgres full-text (`plainto_tsquery('simple', …)` ranked by `ts_rank`) when `app.search.full-text=true`, otherwise a portable membership-scoped `LIKE` query (the H2 dev/test path). Results are grouped by type, scoped to the caller's project membership (system admins see all), with `types`/`projectId` filters, a min-2-char guard, and a 50-cap.
- **Endpoint:** `GET /api/search?q=&types=&projectId=&limit=`.
- **Frontend:** the Cmd-K palette keeps its instant local fuzzy match and, when fewer than 5 local hits, debounces a `GET /api/search` call and appends de-duplicated server hits below the local ones.
