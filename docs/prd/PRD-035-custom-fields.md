# PRD-035 — Custom Fields

| | |
|---|---|
| **Status** | 📝 Draft |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-17 |
| **Priority** | P3 — driver-dependent (the first "we need a Component / Sprint / Customer field" request) |
| **Target** | v2.4 |
| **Related** | PRD-001 (RBAC), PRD-002 (filtering), PRD-004 (import/export), PRD-007 (search), PRD-011 (versioning), PRD-025 (MCP) |

---

## 1. Summary

Every organisation eventually needs one or two attributes the tool doesn't have: *Component*, *Automation status*, *Customer*, *Sprint*, *Risk*. Today people encode them in labels (`component:checkout`), which gives no validation, no fixed option list, no numbers or dates, and nothing on test runs or bug reports at all (only `TestCase` has `labels`).

This PRD lets a project admin define a small number of typed fields per entity type — test case, test run, bug report — and have them appear on forms, detail pages, list filters, and import/export.

## 2. Goals & Non-Goals

**Goals**
- Per-project field definitions for `TEST_CASE`, `TEST_RUN`, `BUG_REPORT`.
- Types: `TEXT` (single line, ≤ 500 chars), `NUMBER` (decimal), `DATE`, `SELECT`, `MULTI_SELECT`.
- Optional `required` flag; ordering; archiving (hide without losing data).
- Filter list endpoints by custom field values (PRD-002 `Specification` style).
- CSV/JSON import and export of test case custom fields (PRD-004).
- Values included in test case version snapshots (PRD-011).
- MCP `get_test_case` / `create_test_case` / `update_test_case` read and write them.

**Non-Goals**
- Conditional fields, field dependencies, computed/formula fields, per-status visibility — that is a configurable workflow engine (PRD-009 §4).
- Rich text, long text areas, user-picker, URL or file fields. Description fields already hold long text; `assignee`/`executor` already cover users.
- Global (cross-project) field definitions. Copying definitions between projects is a later convenience, not v1.
- Custom fields in the global full-text search (PRD-007) — see §3.6.
- Custom fields on suites, plans, steps or individual results.
- Using custom fields in dashboards/charts or the PDF reports in v1.

## 3. Proposed Design

### 3.1 Storage choice

| Option | Filterable | H2 tests | Orphans | Verdict |
|---|---|---|---|---|
| `JSONB` column on each entity | Postgres only (`->>`, GIN) | H2 has no `JSONB` operators, so every filter needs a vendor split and the test DB stops exercising the real query | none | ✗ |
| `TEXT` JSON column (as `test_case_parameter_sets.values_json`, V46) | only by `LIKE` on serialised JSON — wrong for numbers, dates and multi-select | fine | none | ✗ — V46 chose JSON *because* sets are never queried; these are |
| Polymorphic `(entity_type, entity_id)` value table (as `comments`, V19) | yes | fine | **yes** — `TestCaseService.delete` doesn't clean up `comments` today, and nothing would clean these up either | ✗ |
| **Value table with one nullable FK per entity + CHECK** | yes, via `EXISTS` subquery | fine, plain SQL | none — `ON DELETE CASCADE` | ✓ |

The chosen shape is vendor-neutral (`db/migration/`, nothing under `db/specific/`), cascades with its owner, and filters with the same correlated-`EXISTS` pattern `TestCaseSpecifications` already uses for labels.

### 3.2 Data model (next free V-number, V54+ at time of writing)
```
custom_field_definitions
  id UUID PK, project_id UUID NOT NULL FK ON DELETE CASCADE,
  entity_type VARCHAR(20) NOT NULL,        -- TEST_CASE | TEST_RUN | BUG_REPORT
  name VARCHAR(100) NOT NULL,
  field_type VARCHAR(20) NOT NULL,         -- TEXT | NUMBER | DATE | SELECT | MULTI_SELECT
  options_json TEXT,                       -- ["Checkout","Search"] for SELECT/MULTI_SELECT, else null
  required BOOLEAN NOT NULL DEFAULT FALSE,
  archived BOOLEAN NOT NULL DEFAULT FALSE,
  order_index INT NOT NULL DEFAULT 0,
  created_at, updated_at, created_by, updated_by,
  UNIQUE (project_id, entity_type, name)

custom_field_values
  id UUID PK, field_id UUID NOT NULL FK ON DELETE CASCADE,
  test_case_id UUID NULL FK ON DELETE CASCADE,
  test_run_id UUID NULL FK ON DELETE CASCADE,
  bug_report_id UUID NULL FK ON DELETE CASCADE,
  value_text VARCHAR(500), value_number DECIMAL(19,4), value_date DATE,
  created_at, updated_at, created_by, updated_by,
  CHECK ((test_case_id IS NOT NULL) + (test_run_id IS NOT NULL) + (bug_report_id IS NOT NULL) = 1)
```
Write the CHECK with `CASE WHEN … THEN 1 ELSE 0 END` sums — boolean-to-int addition differs between H2 and Postgres. Indexes: `(field_id, value_text)`, `(field_id, value_number)`, `(field_id, value_date)`, and one per owner FK (V40 added FK indexes for the same reason).

`MULTI_SELECT` stores one row per selected option; `SELECT` stores exactly one. Options live in `options_json` — a definition's options are read and written whole and never joined, the same reasoning V46 used. Option *values* are stored as the label text; renaming an option rewrites matching `value_text` rows in the same transaction.

Limits: 20 non-archived definitions per project per entity type, 50 options per select. These keep forms usable and filter queries bounded; they are constants, not config.

### 3.3 Backend
- Entities `CustomFieldDefinition`, `CustomFieldValue` (`BaseEntity`, Lombok). Values are not mapped as a collection on `TestCase`/`TestRun`/`BugReport` — loading them via a dedicated repository query (`findByTestCaseIdIn`) avoids adding another eagerly-joined collection to entities that list endpoints page over.
- `CustomFieldService`
  - `definitions(projectId, entityType)`; `create/update/archive/delete` definitions. Delete refused while values exist unless `?force=true` (audited); archive is the normal path.
  - `validateAndWrite(projectId, entityType, ownerId, Map<String, Object> values, WriteMode mode)` — one entry point called from `TestCaseService.create/update`, `TestRunService.create/update/cloneRun`, `BugReportService.create/update`, `TestCaseImportExportService.importData`, and the MCP writers. Validates type, option membership, length; `mode = INTERACTIVE` enforces `required`, `mode = MACHINE` (CI ingestion `CiIngestionService`, external run API, import) does not, so a CI upload never fails because an admin added a required field yesterday.
  - Keyed by **field name** in request/response payloads (`"customFields": {"Component": "Checkout"}`), because import files and MCP agents know names, not UUIDs. Unknown names → 400 listing valid names.
- DTOs: `CreateTestCaseRequest`, `UpdateTestCaseRequest`, `TestCaseResponse` (and run/bug equivalents) gain `Map<String, Object> customFields`. On update, `null` means unchanged (the PRD-025 §3.4 rule); a key with `null` value clears that field.
- Versioning: `TestCaseVersionService.snapshotBeforeEdit` serialises the case's custom field values into a new `test_case_versions.custom_fields_json TEXT` column; the version diff shows them.
- `cloneRun` copies run custom field values.
- Audit: definition changes logged against `AuditEntityType.PROJECT`; value changes ride on the owning entity's existing `UPDATED` entry.

### 3.4 Endpoints (RBAC via PRD-001 `@RequireProjectRole`)
| Method & path | Role |
|---|---|
| `GET /api/projects/{projectId}/custom-fields?entityType=` | VIEWER |
| `POST /api/projects/{projectId}/custom-fields` | ADMIN |
| `PUT /api/projects/{projectId}/custom-fields/{id}` | ADMIN |
| `POST /api/projects/{projectId}/custom-fields/{id}/archive` / `unarchive` | ADMIN |
| `DELETE /api/projects/{projectId}/custom-fields/{id}?force=` | ADMIN |

Values travel on the existing entity endpoints, so they inherit those endpoints' roles (TESTER to write).

### 3.5 Filtering (PRD-002)
- Query parameter form: `cf.<fieldName>=value` (repeatable → OR within a field), `cf.<fieldName>.min=` / `.max=` for `NUMBER` and `DATE`, and `cf.<fieldName>=` substring match for `TEXT`. Different fields AND together.
- `TestCaseListFilter`, `TestRunListFilter` gain `List<CustomFieldCriterion> customFields`; the controller resolves names to definition ids once. Bug reports: `BugReportController` list is currently unpaged and unfiltered (`findByProject`), so it gets the filter only if PRD-002-style filtering is added to it — in scope as a small prerequisite.
- `TestCaseSpecifications` / `TestRunSpecifications`: one correlated `EXISTS (SELECT 1 FROM custom_field_values v WHERE v.test_case_id = root.id AND v.field_id = :f AND …)` per criterion — same shape as the label predicate, so pagination counts stay correct.
- Frontend filters are URL-bound like existing ones, so a filtered list stays shareable.

### 3.6 Search (PRD-007)
Out of scope. The Postgres path relies on `GENERATED ALWAYS AS … STORED` `tsvector` columns on the entity tables (`db/specific/postgresql/V47__add_search_vectors.sql`); a generated column cannot read a child table, so including custom values would mean trigger-maintained vectors — a second vendor-specific mechanism for a marginal gain. Field filters (§3.5) cover the actual need ("all cases where Component = Checkout").

### 3.7 Import / export (PRD-004)
- CSV: extra columns named `cf:<Field name>`; `MULTI_SELECT` values joined with `;` (the existing `LABEL_SEPARATOR`). Export appends one column per non-archived test case field after `CSV_HEADERS`; `csvSafe` applies to values as to every other cell.
- JSON: `"customFields": { "Component": "Checkout", "Browsers": ["Chrome","Firefox"] }`.
- Dry run reports unknown field names and invalid option values per row, without failing the whole file.

### 3.8 MCP (PRD-025)
`get_test_case` returns `customFields`; `create_test_case` / `update_test_case` accept them (validated through `CustomFieldService` in `MACHINE` mode); `search_test_cases` accepts a `customFields` equality map. A new read tool `list_custom_fields(projectKey)` lets an agent discover names and options before writing. Tool descriptions list the rule "names, not ids".

### 3.9 Frontend
- Project settings: new route `projects/:id/custom-fields` (sibling of `:id/webhooks`, `:id/issue-tracker` in `projects.routes.ts`) with a table per entity type, add/edit dialog, drag reordering, archive toggle. ADMIN only.
- A single `CustomFieldsFormComponent` in `shared/components/` that takes definitions + values and renders Material inputs (`mat-input`, `type="number"`, `mat-datepicker`, `mat-select` with `multiple`). Used by `test-case-form`, `test-run-form`, `bug-report-form`.
- Detail pages render a read-only definition list; archived fields shown only when they hold a value.
- List pages: a "More filters" menu listing the entity's fields.
- i18n: labels for the settings UI only — field names are user data and not translated.

## 4. Edge Cases
- **Type change** of a field with values: refused (400). Create a new field and archive the old one.
- **Option removed** while in use: refused; rename it or archive the field.
- **Required field added** later: existing entities are not invalid retroactively; the edit form asks for it on next save; machine writes are never blocked.
- **Archived field**: hidden from forms and filters, values kept, still exported (so an export/import round trip doesn't lose data), and still in version snapshots.
- **Moving a test case between projects**: not a supported operation today; if added, values for fields that don't exist in the target are dropped with a warning.
- **Duplicate names differing by case** (`component` vs `Component`): the unique constraint is case-sensitive, so the service checks case-insensitively.
- **Number precision**: `DECIMAL(19,4)`; more decimals rejected rather than silently rounded.
- **Cross-project field id** smuggled into a filter or write: names are resolved against the project's definitions only, so a foreign field is simply "unknown".

## 5. Testing
- Migration applies on H2 and Postgres; CHECK rejects zero or two owners; deleting a case/run/bug cascades its values.
- Validation matrix per type (valid, wrong type, too long, unknown option, required INTERACTIVE vs MACHINE).
- Filter specs: equality, multi-value OR, number/date range, combined with existing status/label filters, correct page counts with multi-select rows.
- Import dry run reports unknown fields/options; CSV and JSON round trip preserves values including multi-select.
- Version snapshot contains custom field values; diff shows a changed field.
- Clone run copies values.
- MCP: create with unknown field name → descriptive refusal; `list_custom_fields` scoped to the key's project.
- RBAC: definitions writable by ADMIN only; values by TESTER via entity endpoints; non-member 403/404.
- Frontend: dynamic form renders each type and emits the name-keyed map.

## 6. Effort & Risk
- **Effort:** ~8–10 days (definitions + values + validation 3, filters 2, import/export + versioning + MCP 2, frontend 3). Largest item in this batch.
- **Risk:** Medium. Touches every write path of three entities and three list queries; the single `validateAndWrite` entry point and per-path tests contain that. Performance risk is low at the 50-user target given the per-field indexes and the 20-field cap.

## 7. Acceptance Criteria
- [ ] Project admins can define, reorder, archive and (force-)delete typed custom fields for test cases, test runs and bug reports.
- [ ] Values are validated by type and options, stored vendor-neutrally, and cascade-deleted with their owner.
- [ ] Forms and detail pages show custom fields; `required` is enforced for interactive writes only.
- [ ] Test case and test run lists filter by custom fields via URL-bound parameters with correct pagination.
- [ ] CSV/JSON import (with dry run) and export include test case custom fields.
- [ ] Test case version snapshots include custom field values.
- [ ] MCP tools read and write custom fields by name; `list_custom_fields` exists.
- [ ] Tests pass on H2; migration verified against Postgres.
