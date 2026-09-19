# PRD-030 — Shared Steps

| | |
|---|---|
| **Status** | ✅ Implemented 2026-09-19 — see §8 |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-17 |
| **Priority** | P2 — the most repetitive daily authoring work |
| **Target** | v2.4 |
| **Related** | PRD-011 (versioning), PRD-015 (parameter sets), PRD-004 (import/export), PRD-025/027 (MCP tools), PRD-001 (RBAC), V28 (nullable `step_results.test_step_id`) |

---

## 1. Summary

The same "Log in as an admin", "Reset the basket" or "Open the settings page" steps get copied into
dozens of test cases. When the login page changes, someone has to find and edit every copy, and
usually misses some. TestRail, Xray and Zephyr all solve this with *shared steps*: a named, reusable
block of steps kept once per project and referenced from any number of cases.

This PRD adds project-scoped shared step blocks and a step type in a test case that references one.
References are **expanded when a run is created**, so execution, step results, screenshots and
reporting keep working on ordinary `TestStep` rows as they do today.

## 2. Goals & Non-Goals

**Goals**
- Create, edit and delete named shared step blocks per project, each with ordered steps (action,
  expected result, test data, optional image), reusing the existing step shape.
- Insert a reference to a block anywhere in a test case's step list, mixed with local steps.
- An edit to a block is reflected in every case that uses it, and in runs created afterwards.
- Execution shows expanded steps grouped under the block's title, with per-step results.
- Version history (PRD-011) records what each affected case said before a block edit.
- Parameter placeholders (PRD-015) inside shared steps resolve against the *calling* case's sets.
- "Where used" list per block, and "convert to local steps" on a case.

**Non-Goals**
- Nesting (a shared block referencing another shared block). One level only, enforced on save.
- Cross-project or global shared step libraries.
- Parameters passed *into* a block per reference (e.g. `Login(user=bob)`). The calling case's
  parameter sets already cover data variation.
- Changing steps inside a run that is already in progress. Runs keep what they were created with (§4).
- Full-text search indexing of shared step text (PRD-007). Only block titles are searchable at first.

## 3. Proposed Design

### 3.1 Data model (next free V-number, V54+ at time of writing)

```sql
CREATE TABLE shared_steps (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
    created_by UUID, updated_by UUID,
    CONSTRAINT uq_shared_steps_project_title UNIQUE (project_id, title)
);

-- A step belongs to either a test case or a shared block.
ALTER TABLE test_steps ALTER COLUMN test_case_id DROP NOT NULL;
ALTER TABLE test_steps ADD COLUMN shared_step_id UUID REFERENCES shared_steps(id) ON DELETE CASCADE;
-- A case step may instead be a reference to a block.
ALTER TABLE test_steps ADD COLUMN uses_shared_step_id UUID REFERENCES shared_steps(id);
ALTER TABLE test_steps ADD CONSTRAINT ck_test_steps_owner
    CHECK ((test_case_id IS NULL) <> (shared_step_id IS NULL));
ALTER TABLE test_steps ADD CONSTRAINT ck_test_steps_no_nested_ref
    CHECK (uses_shared_step_id IS NULL OR shared_step_id IS NULL);
CREATE INDEX idx_test_steps_shared_step ON test_steps(shared_step_id);
CREATE INDEX idx_test_steps_uses_shared_step ON test_steps(uses_shared_step_id);

-- Display order of a result's steps, now that expanded steps come from several owners.
ALTER TABLE step_results ADD COLUMN position INT;
UPDATE step_results sr SET position = ts.order_index FROM test_steps ts WHERE ts.id = sr.test_step_id;
```

The backfill `UPDATE ... FROM` is Postgres syntax. The H2 variant goes in `db/specific/h2/` per the
vendor-migration convention.

**Why reuse `test_steps` for block steps:** `StepResult.testStep`, `StepImage`, `TestRunMapper`
(`action`/`expectedResult`/`stepImageId` from `testStep`) and `StepImageService` authorisation all
work on `TestStep`. A separate `shared_step_items` table would mean duplicating each of those paths.

**Reference rows:** a case step with `uses_shared_step_id` set still has `action NOT NULL`. It stores
the block title at save time, which is also a readable fallback if a client ignores the new field.
Its `expected_result`, `test_data` and image are ignored.

`TestStep` gains `sharedStep` (owner, `@ManyToOne`) and `usesSharedStep` (`@ManyToOne`).
`testCase` becomes nullable. `StepResult` gains `position`.

### 3.2 Backend

- **`SharedStepService`** (new) — CRUD, project-scoped lookups (`findByIdAndProjectId`; foreign ids
  404, per the PRD-027 §3.5 sweep), `whereUsed(projectId, id)`.
  - **Update matches steps by id** (update in place, insert new, delete removed) instead of the
    clear-and-rebuild `TestCaseService.update` uses. Rebuilding would SET NULL the `test_step_id` of
    every step result referencing the block across *all* runs, blanking their action text. For a
    case that affects one case's history; for a block it would affect dozens.
  - Delete is refused with 409 and the usage count while any case references the block.
- **`TestCaseService`** — `TestStepRequest` gains optional `sharedStepId`. When it is set, `action`
  etc. are ignored and the id must resolve within the same project. `TestCaseResponse` steps gain
  `sharedStepId`, `sharedStepTitle` and `expandedSteps` (read-only), so clients can render a block
  without a second call.
- **Run creation** — `TestRunService`'s seeding loop (`for (TestStep step : tc.getSteps())`) expands
  reference rows into one `StepResult` per block step, in block order. Results point at the block's
  `TestStep` rows, and `position` is set to the running index across the expanded list.
  `TestRunMapper.orderIndex` maps from `position`, falling back to `testStep.orderIndex` for rows
  created before the migration.
- **Versioning (PRD-011)** — `TestCaseVersionService.serialiseSteps` snapshots the **expanded**
  steps, each with an optional `sharedStepTitle`, so a version shows what a tester would have
  executed. When a block is edited, `SharedStepService` writes a pre-edit version for **each case that
  references it**, reusing the existing snapshot call. Per-case history then stays complete without a
  second history model. At this tool's scale (tens of referencing cases) that is tens of small inserts.
- **Parameter sets (PRD-015)** — no backend change. Substitution is textual and happens on the
  expanded steps. The unresolved-placeholder check in `test-case-parameters.component.ts` must run
  over expanded steps so a `{username}` inside a shared block is flagged on the calling case.
- **Convert to local steps** — `POST .../test-cases/{id}/steps/{stepId}/inline` replaces the reference
  row with copies of the block's steps (images copied), writing a version first.
- **Import/export (PRD-004)** — JSON export writes reference steps as `{"sharedStep": "<title>"}`.
  JSON import resolves by title within the project, and the dry run reports unknown titles as row
  errors. CSV stays lossy by design: it exports expanded steps, since `action;;expected` pairs have no
  room for a reference. That is documented.
- **Duplicate detection** — `TestCaseDuplicateDetector` compares expanded steps.

### 3.3 Endpoints (RBAC via PRD-001)

| Method & path | Role |
|---|---|
| `GET /api/projects/{projectId}/shared-steps` (paged, `q` on title) | `@RequireProjectRole` (VIEWER) |
| `GET /api/projects/{projectId}/shared-steps/{id}` (includes steps and `usedByCount`) | VIEWER |
| `GET /api/projects/{projectId}/shared-steps/{id}/usages` | VIEWER |
| `POST /api/projects/{projectId}/shared-steps` | TESTER |
| `PUT /api/projects/{projectId}/shared-steps/{id}` | TESTER |
| `DELETE /api/projects/{projectId}/shared-steps/{id}` | TESTER (409 while in use) |
| `POST /api/projects/{projectId}/test-cases/{id}/steps/{stepId}/inline` | TESTER |
| Step image upload/download for block steps | same as case step images (`StepImageController`), project resolved via the owning block |

Editing a block is TESTER, matching `TestCaseController.update`. Per-case edit grants
(`TestCasePermission`) do **not** extend to blocks, because a block edit changes many cases at once.

Audit: `AuditEntityType.SHARED_STEP` with CREATED/UPDATED/DELETED via `AuditService.log`.

### 3.4 Frontend

- **Shared steps page** — `features/shared-steps/` (lazy route under the project). List with title,
  step count and "used by N". The editor reuses the step `FormArray` editor from
  `test-case-form.component`, extracted into a shared component so there aren't two step editors.
  "Where used" links to the cases.
- **Test case form** — an "Insert shared step" button opens a searchable picker. A reference row
  renders collapsed as a chip with the block title and expands read-only, with an "Edit shared step"
  link that warns "used by N cases". There's also a row action "Convert to local steps".
- **Test case detail / versions** — expanded steps with a subtle group header for block steps.
- **Run execution** (`test-run-detail`) — step results sorted by `orderIndex` (now `position`), with a
  group header whenever consecutive steps share a `sharedStepTitle`. Keyboard-driven execution is
  unchanged because the steps are still a flat list.
- i18n keys in `en.json` / `de.json`.

### 3.5 MCP impact

- `get_test_case` returns expanded steps, each with an optional `sharedStepTitle`, so agents executing
  runs see exactly what testers see.
- `McpDtos.Step` gains an optional `sharedStepId`. `create_test_case` / `update_test_case` accept it in
  place of `action`, with `McpValidator` enforcing "either action or sharedStepId".
- New read tool `list_shared_steps` (project, optional title query) in the authoring tool group.
  Creating or editing blocks through MCP is deliberately **not** added: a block edit fans out across
  many cases, which is the kind of change a human should make.

## 4. Edge Cases

- **Block edited while a run is in progress.** In-place updates change the `TestStep` text that
  existing step results point at, so an open run would show the new wording. Added and removed steps
  don't change open runs, since their step results were fixed at creation. This matches today's
  behaviour for case edits and is documented. Snapshotting step text onto `step_results` would fix
  both and is a separate change.
- **Block step removed** → its step results keep their row with `test_step_id` NULL (V28). Their
  `position` keeps them in order.
- **Reference to a block from another project** → 404 on save, never silently dropped.
- **Nested reference attempted** (a block step with `sharedStepId`) → 400, backed by
  `ck_test_steps_no_nested_ref`.
- **Delete block in use** → 409 with count and a link to "where used". The user converts or removes
  the references first.
- **Rename to a title that already exists** → 409 via `uq_shared_steps_project_title`.
- **Empty block** → allowed to save, but a reference to an empty block expands to zero steps. The case
  form warns about it.
- **Test case deleted** → its reference rows cascade as today. Blocks are unaffected.
- **Case with many block references and parameter sets** → N sets × expanded steps. The expansion is
  O(steps) per result, which is fine at this tool's scale.
- **Version history for a block edit touching 100+ cases** → one transaction with one snapshot per
  case. Acceptable. If it ever shows in profiling, write versions in batches.

## 5. Testing

- `SharedStepService`: CRUD, project scoping (foreign id → 404), in-place update keeps step ids and
  step-result links, delete-in-use → 409, unique title, audit entries.
- `TestCaseService`: save with mixed local/reference steps, reference to a foreign block → 404,
  nested reference → 400, inline conversion copies steps and images and writes a version.
- `TestRunService`: run creation expands references in order with correct `position`, parameterized
  case × shared block expands per set, pre-migration results still ordered via fallback.
- `TestCaseVersionService`: snapshot contains expanded steps; a block edit writes one version per
  referencing case with the pre-edit text.
- Import/export: JSON round trip preserves references, unknown title → dry-run error, CSV exports
  expanded steps.
- MCP: `get_test_case` expanded output, `create_test_case` with `sharedStepId`, validator rejects both
  or neither, `list_shared_steps` scoped to the caller's project.
- Migration: H2 and Postgres both apply, and the check constraints reject a step with two owners.
- Frontend: picker inserts a reference, collapsed/expanded rendering, run detail group headers,
  unresolved-placeholder warning includes shared step text.

## 6. Effort & Risk

- **Effort:** ~8–10 days. Schema and service ~3, run expansion and versioning ~2, import/export and
  MCP ~1.5, frontend (page, extracted step editor, picker, grouping) ~3.
- **Risk:** Medium. It touches the most central path in the product: case → run → step result. The
  main risks are ordering regressions in existing runs (covered by the `position` fallback and tests)
  and fan-out surprises from editing a block (mitigated by the "used by N" warning and per-case
  versions). No behaviour change for projects that never create a block.

## 7. Acceptance Criteria

- [x] Project members with TESTER can create, edit and delete shared step blocks; VIEWER can list and read them.
- [x] Test cases can mix local steps and references to blocks from the same project; nesting is rejected.
- [x] New runs expand references into ordinary step results in the correct order.
- [x] Editing a block updates steps in place and writes a pre-edit version for every referencing case.
- [x] Parameter placeholders inside blocks resolve per the calling case's parameter sets.
- [x] Deleting a block in use is refused; "convert to local steps" works.
- [x] JSON import/export round-trips references; CSV export is expanded and documented as lossy.
- [x] MCP `get_test_case` shows expanded steps; `create/update_test_case` accept `sharedStepId`; `list_shared_steps` exists.
- [x] Backend, migration and frontend tests pass; en/de translations present.

## 8. As Built (2026-09-19)

Built in five commits (schema and library; references in cases and runs; import/export, Gherkin
and MCP; frontend; docs), as specified except:

- **The `position` backfill is one correlated subquery** that runs on PostgreSQL and H2 alike, so
  no vendor-specific migration was needed (§3.1 planned a Postgres `UPDATE … FROM` plus an H2
  variant).
- **More paths expand references than §3.2 lists.** Every path that matches results to steps goes
  through one `StepExpansion`: run creation, but also CI ingestion (JUnit/Cucumber step results by
  index), the external runs API (`stepIndex`) and the review check for a content edit. Without
  them a CI upload for a case using a shared step would put step results on the wrong steps.
- **A block edit is a content edit for review (PRD-033)**, not only a version: an approved case
  whose shared step changed goes back to review, as it would if its own steps had changed. §3.2 did
  not say. Only a change to the steps counts; a rename writes no versions and just updates the
  reference rows' fallback title.
- **JSON references by `sharedStepTitle`**, the field the export already carries on a reference,
  rather than a separate `{"sharedStep": "<title>"}` shape.
- **Gherkin (PRD-040)**, which §3.2 predates: export writes the expanded steps; a keyed case is
  compared against its expanded steps, so re-importing the export is unchanged; a changed scenario
  replaces the reference with local steps and warns, dry run included. "Edit as Gherkin" is disabled
  on a case with references.
- **MCP:** consecutive steps with the same `sharedStepId` become one reference, so the expanded
  steps `get_test_case` returns can be sent back to `update_test_case` without silently inlining
  the block. `get_test_case_version` tags steps with their shared step too. The schema no longer
  marks a step's `action` required; the tool checks "action or sharedStepId" itself.
- **"Convert to local steps" is on the test case page**, not a row action in the form: it calls
  the server endpoint, which copies images and writes a version, for a case that is saved.
- **Duplicate detection** compares titles only, so there was no step comparison to expand.
- **Nesting** cannot be requested at all: a block's step request has no `sharedStepId`. The check
  constraint backs that.

Tests: `SharedStepApiTest` (14: roles, scoping, in-place update keeping step ids, removal, unknown
step id, duplicate title, search, delete in use / unused with audit, both check constraints,
reference save and inline over HTTP), `SharedStepUsageTest` (15: mixed steps and expansion,
foreign block 404, run expansion with positions, per-set expansion, pre-V62 ordering fallback, CI
and external step matching, expanded snapshots, one version per case on a block edit, rename writes
none, review, inline in place with images copied), `SharedStepImportExportTest` (5, committed:
JSON round trip into another project, unknown title in the dry run, CSV expanded, Gherkin unchanged
and changed), `McpSharedStepToolsApiTest` (5), and frontend specs for the step editor, the form's
picker and save, and the expansion helpers. Migration checked on PostgreSQL 16 with
`ddl-auto=validate`, plus the backfill against sample rows.

Browser check against a throwaway PostgreSQL: create a shared step, use it in a case through the
picker, run it (grouped under its title), edit the shared step (a version on the case, the diff
shows the change, the case follows), parameter pre-fill from inside the shared step, convert to
local steps, and reopen a saved case in the form. It found only layout issues, fixed before commit.

