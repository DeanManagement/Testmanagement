# PRD-004 — Test Case Import / Export (CSV + JSON)

| | |
|---|---|
| **Status** | Implemented (2026-06-09) |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-09 |
| **Priority** | P1 — Onboarding / migration |
| **Target** | v1.3 |
| **Related** | REQUIREMENTS.md §12.4 (documented, not built); REVIEW §4.5 |

---

## 1. Summary

Allow teams to migrate test cases from spreadsheets and to back up / transfer data. Specified in v1.2 but never built. Ship **JSON first** (near-free over the existing `TestCaseResponse` serialisation), then CSV (Excel-quoting nuances). Add a **dry-run import** so onboarding is "preview, fix, commit" instead of "try, break, undo."

## 2. Goals & Non-Goals

**Goals**
- Export a project's test cases as JSON or CSV (round-trip compatible).
- Import test cases from CSV (and JSON) with per-row validation and an error report.
- Dry-run import that reports the would-be result without persisting.

**Non-Goals**
- Importing runs/results/suites (test cases only in this iteration).
- Cross-project merge/dedup logic beyond key handling.

## 3. Proposed Design

### 3.1 Endpoints (gated by PRD-001 — export `VIEWER`, import `TESTER`)
- `GET /api/projects/{projectId}/test-cases/export?format=json` → JSON array of full test case objects.
- `GET /api/projects/{projectId}/test-cases/export?format=csv&excel=true` → CSV download; `excel=true` adds a UTF-8 BOM for Excel.
- `POST /api/projects/{projectId}/test-cases/import` (multipart) → imports; `?dryRun=true` validates only.

### 3.2 CSV format
- Required column: `title`.
- Optional: `description`, `preconditions`, `priority` (`LOW|MEDIUM|HIGH|CRITICAL`), `status` (`DRAFT|ACTIVE|DEPRECATED`), `labels` (semicolon-separated), `steps` (pipe-delimited `action|expected` pairs, separated by `;;`).
- Header row required; UTF-8. Use Apache Commons CSV (handles quoting; German-locale semicolons).
- Import response: `{ imported, skipped, errors: [{ row, message }] }` (same shape for dry-run, with nothing persisted).
- Limit: 500 test cases per import.

### 3.3 JSON format
- Export: array matching `TestCaseResponse` (including steps).
- Import: same array; ignores server-managed fields (`id`, `testCaseKey`, timestamps) — new keys are generated via the existing `incrementAndGetTestCaseNumber`.

### 3.4 Frontend
- "Import" button on the test case list → dialog with file upload, format help, and a **dry-run preview** (shows imported/skipped/errors before a confirm step).
- "Export" split-button (CSV / JSON).
- Result dialog summarising success/errors with row numbers.

## 4. Edge Cases
- Malformed rows reported with row number; valid rows still import (partial success) unless dry-run.
- Over-limit file → 400 before processing.
- Invalid enum / step syntax → row-level error, not a whole-file failure.
- Encoding: accept UTF-8 with or without BOM on import.
- Labels and steps round-trip identically (export → import yields equivalent test cases).

## 5. Testing
- Round-trip: export a project, re-import into an empty project, assert equality of titles/steps/labels.
- Malformed CSV fixtures (missing header, bad enum, unbalanced step pairs) → precise error rows.
- Dry-run persists nothing (DB row count unchanged).
- Authz: viewer can export, cannot import.

## 6. Effort & Risk
- **Effort:** JSON ~0.5 day; CSV + dry-run + frontend ~1 week total (REVIEW `M`).
- **Risk:** Low–medium. CSV quoting/locale is the main fiddly part; Commons CSV mitigates.

## 7. Acceptance Criteria
- [x] JSON and CSV export produce downloadable, round-trip-compatible files.
- [x] CSV/JSON import validates per row and returns `{imported, skipped, dryRun, errors}`.
- [x] `?dryRun=true` returns the same report without writing (verified: DB count unchanged).
- [x] 500-row limit enforced (400); Excel BOM option works.
- [x] Round-trip + malformed-input + authz tests pass (139 backend tests green; frontend builds clean).

## 8. Implementation Notes (as shipped)

- **Endpoints** (separate `TestCaseImportExportController`, shares the `/test-cases` base path): `GET /export?format=json|csv&excel=true` (`@RequireProjectRole` VIEWER) and `POST /import` multipart with `?dryRun` (`@RequireProjectRole(TESTER)`).
- **Service** `TestCaseImportExportService`: JSON export via the existing `TestCaseResponse` serialization; CSV via Apache Commons CSV (`commons-csv`), columns `title,description,preconditions,priority,status,labels,steps` (labels `;`-joined, steps `action|expected` pairs joined by `;;`). Import detects format by filename/content, strips UTF-8 BOM, defaults `priority=MEDIUM`/`status=DRAFT`, validates per row (partial success: valid rows import, invalid rows reported with 1-based row numbers), enforces the 500-row cap (400), and reuses `TestCaseService.create` for key generation + audit. Dry-run validates without persisting.
- **Counter portability (prereq):** the per-project test-case/run number was generated with a Postgres-only `UPDATE ... RETURNING`, which broke on H2 (dev/test) and blocked import round-trip testing. Replaced with `ProjectSequenceService` (pessimistic row lock + read-increment via `findByIdForUpdate`), portable across Postgres and H2 and still safe under concurrent creates. This also resolves the prior limitation that test cases couldn't be created in H2-backed tests.
- **Frontend:** test-case list gains an Import button (dialog with file picker, dry-run preview showing imported/skipped + per-row errors, then confirm) and an Export menu (JSON / CSV / CSV-for-Excel) that downloads via a blob.
