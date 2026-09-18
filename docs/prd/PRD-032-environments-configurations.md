# PRD-032 — Project Environments & Multi-Environment Runs

| | |
|---|---|
| **Status** | ✅ Implemented 2026-09-18 — see §8; UI not yet click-tested in a browser, Automation-panel environment picker open |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-17 |
| **Priority** | P2 — makes "did it pass on X?" answerable |
| **Target** | v2.4 |
| **Related** | PRD-002 (filtering), PRD-005 (CI ingestion), PRD-016 (flaky detection), PRD-024 (build servers), PRD-025/027 (MCP), PRD-001 (RBAC) |

---

## 1. Summary

`TestRun.environment` and `BugReport.environment` are free-text `VARCHAR(255)` columns (V9, V25). A
tester types `staging`, CI sends `?environment=Staging`, an agent passes `stage`, and the tool
can't tell that all three are the same place. So the questions a release manager actually asks,
"has checkout passed on Firefox + staging?" or "which environments did this plan cover?", can't be
answered. Filtering runs by environment isn't possible either (`TestRunController.findAll` has no
such parameter).

This PRD adds a **per-project list of environments**, links runs and bug reports to it, backfills
the existing strings, and lets a tester **create one run per environment in a single action**. The
string column stays as the display name, so the ~40 read sites (DTOs, `PdfReportService`,
`SearchService`, `DashboardService`, `IssueLinkService.buildBody`, MCP DTOs) don't change.

## 2. Goals & Non-Goals

**Goals**
- A project-level environment catalogue: name, optional description, sort order, archived flag.
- Runs and bug reports reference a catalogue entry. Every existing write path (UI, REST, CI
  `?environment=`, MCP string params, clone) keeps accepting a name, matched case- and
  whitespace-insensitively, and **auto-registers unknown names**, so no integration breaks.
- A picker instead of a free-text field in the run, clone and bug forms.
- Create runs across N environments at once (same cases, plan and assignee; one run each).
- Filter the runs list by environment. A test case's "latest result per environment" is visible on
  its detail page.
- Admins can rename, archive and **merge** environments, which cleans up backfilled duplicates.
- Build-server triggers (PRD-024) can target an environment and the reported-back run inherits it.

**Non-Goals**
- **Multi-dimensional configurations** (browser × OS × locale with cartesian generation, as in
  TestRail's configurations). Environments are flat names like `Chrome · Staging`. Add dimensions
  only if a real matrix need appears.
- Environment metadata such as URLs, credentials or deployment versions. This is a label, not a
  deployment registry.
- A "strict mode" that rejects unknown names. The picker already prevents most typos in the UI, and
  rejecting names would break CI uploads. Revisit if duplicates keep appearing after merges.
- Per-environment flaky scoring in PRD-016 (follow-up; see §4).
- Environments shared across projects.

## 3. Proposed Design

### 3.1 Data model (next free V-number, V54+ at time of writing)

```sql
CREATE TABLE project_environments (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    name_normalized VARCHAR(255) NOT NULL,   -- lower(trim(name)); vendor-neutral uniqueness
    description TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
    created_by UUID, updated_by UUID,
    CONSTRAINT uq_project_environments_name UNIQUE (project_id, name_normalized)
);

ALTER TABLE test_runs   ADD COLUMN environment_id UUID REFERENCES project_environments(id) ON DELETE SET NULL;
ALTER TABLE bug_reports ADD COLUMN environment_id UUID REFERENCES project_environments(id) ON DELETE SET NULL;
CREATE INDEX idx_test_runs_environment   ON test_runs(environment_id);
CREATE INDEX idx_bug_reports_environment ON bug_reports(environment_id);
```

- **`environment_id` is the source of truth. `environment` (string) stays as a denormalised copy of
  the name**, kept in sync on write and on rename (one `UPDATE ... WHERE environment_id = ?`). The
  alternative, dropping the string and joining everywhere, touches every DTO, the PDF, search, the
  dashboard, MCP and the frontend models in exchange for removing one column. Denormalising is
  explicit here and kept in sync in exactly one service.
- **Backfill** (vendor-specific in `db/specific/postgresql` and `db/specific/h2`, since UUID
  generation differs: `gen_random_uuid()` vs `RANDOM_UUID()`):
  1. Blank or whitespace-only `environment` → `NULL`.
  2. Insert one environment per `(project_id, lower(trim(environment)))` across `test_runs ∪ bug_reports`,
     with the display `name` taken as `MIN(trim(environment))` in that group.
  3. Set `environment_id` and rewrite `environment` to the canonical name. Only case and whitespace
     change, so no information is lost.
- Entities: `ProjectEnvironment` (extends `BaseEntity`), plus `@ManyToOne environment` on `TestRun`
  and `BugReport` next to the existing string.

### 3.2 Backend

- **`ProjectEnvironmentService`** (new):
  - `resolve(projectId, name)` → trims and normalises. Blank returns null, a match returns the entry,
    and an unknown name is **auto-created** (appended to `sort_order`, audited). Unarchives an archived
    match: something just used it.
  - `resolve(projectId, environmentId)` → project-scoped lookup; a foreign id is a 404 (PRD-027 §3.5).
  - CRUD, `rename` (propagates the denormalised name), `archive`, `delete` (only when unreferenced,
    else 409 suggesting archive), and `merge(sourceId, targetId)` (repoints runs and bug reports,
    rewrites their names, deletes the source, all in one transaction).
- **Write paths** call the resolver. None of their signatures lose the string:
  - `TestRunService` create/update/clone (`CreateTestRunRequest`, `UpdateTestRunRequest`,
    `CloneTestRunRequest`): add optional `environmentId`. If both `environmentId` and `environment`
    are given, the id wins; if only a name is given, it is resolved.
  - `BugReportService` create/update: same.
  - `CiIngestionService.ingest` and `ExternalTestRunService` (`?environment=` / JSON `environment`): name resolution.
  - MCP `create_test_run`, `update_test_run` (`""` still clears), `clone_test_run`, bug report tools:
    name resolution. No tool signature changes.
- **Multi-environment create**: `CreateTestRunRequest` gains optional `environmentIds: List<UUID>`
  (max 20, mutually exclusive with `environment`/`environmentId`). `TestRunService.createAcrossEnvironments`
  creates one run per environment in a single transaction, named `"<name> · <environment>"`, each with
  its own run key and the same case selection (including parameter-set expansion, PRD-015), plan and
  executor. The response is the list of created runs.
- **Filtering (PRD-002)**: `TestRunSpecifications` gains `environmentId`. `TestRunController.findAll`
  and the bug report list accept `environmentId`.
- **Latest result per environment**: `TestResultRepository` query returning, for one test case, the
  most recent result per `environment_id`, ordered by `COALESCE(run.endTime, run.startTime,
  run.createdAt)` like `FlakyTestService` (CI backfills results late). Runs with no environment are
  grouped as "unspecified".
- **Build servers (PRD-024)**: `TriggerPipelineRequest` gains optional `environmentId`.
  `PipelineRunService` adds `TM_ENVIRONMENT=<name>` to the merged variables, next to `TM_PROJECT_KEY`,
  and it is stored in `PipelineRun.parameters` as today. When the report-back call links a run through
  `PipelineRunLinker.attach` and the upload named no environment, the run inherits `TM_ENVIRONMENT`.
  No `pipeline_runs` schema change.
- Audit: `AuditEntityType.ENVIRONMENT` for create (including auto-create), rename, archive, merge, delete.

### 3.3 Endpoints (RBAC via PRD-001)

| Method & path | Role |
|---|---|
| `GET /api/projects/{projectId}/environments?includeArchived=` | `@RequireProjectRole` (VIEWER) |
| `POST /api/projects/{projectId}/environments` | ADMIN |
| `PUT /api/projects/{projectId}/environments/{id}` (rename, description, sort order, archived) | ADMIN |
| `DELETE /api/projects/{projectId}/environments/{id}` (409 when referenced) | ADMIN |
| `POST /api/projects/{projectId}/environments/{id}/merge` `{targetId}` | ADMIN |
| `POST /api/projects/{projectId}/test-runs/across-environments` | TESTER (same as run create) |
| `GET /api/projects/{projectId}/test-runs?environmentId=` | VIEWER |
| `GET /api/projects/{projectId}/test-cases/{id}/results/by-environment` | VIEWER |

Implicit auto-create through a run, bug or CI write needs only the role that write already requires.
Curating the list is ADMIN.

### 3.4 Frontend

- **Project settings → Environments** (new tab, `features/settings` pattern): list with drag to reorder,
  rename inline, archive toggle, run/bug counts, and "Merge into…" dialog.
- **`test-run-form`**: the free-text `environment` input becomes a `mat-autocomplete` over active
  environments, still allowing a new name, shown as "Add 'foo'". A "Run on multiple environments"
  toggle switches to a multi-select that calls `across-environments` and navigates to the run list
  filtered to the new runs.
- **`clone-test-run-dialog`, `bug-report-form`**: the same autocomplete.
- **`test-run-list`, `my-test-runs`**: environment filter chip, bound to the URL like the existing filters.
- **`test-case-detail`**: "Latest result by environment" table (environment · status · run key · date).
- **`test-plan-detail`**: the runs table's environment column gets a group-by-environment toggle
  showing pass rate per environment, computed client-side from the loaded run summaries.
- The NgRx test-run state gains `environmentId` in filters. The environments list is fetched per project
  and cached in a small service (no store slice needed). i18n in `en.json` / `de.json`.

### 3.5 MCP impact

- Existing tools keep their string `environment` parameters, now resolved against the catalogue.
  Their responses already return the (now canonical) name.
- New read tool `list_environments` (project) so agents pick an existing name instead of inventing one.
  Tool descriptions for `create_test_run` / `update_test_run` point to it.
- Multi-environment creation is not exposed through MCP initially. An agent can call
  `create_test_run` once per environment.

## 4. Edge Cases

- **Case/whitespace variants** (`Staging`, ` staging `) → one environment. **Spelling variants**
  (`stage`, `stg`) → separate entries until an admin merges them. That is intended, since guessing
  would be wrong more often than right.
- **Rename to a name that already exists** → 409 pointing at merge.
- **Archived environment** → hidden from pickers, still shown on historical runs and filters. A CI or
  MCP write naming it unarchives it.
- **Delete while referenced** → 409. Merge or archive instead. `ON DELETE SET NULL` only protects
  against a project-cascade path.
- **Run with no environment** → allowed as today. It is grouped as "unspecified" in per-environment views.
- **Clone** → keeps the source run's environment unless the dialog changes it.
- **Multi-environment create with a plan** → all N runs attach to the plan, and plan pass-rate
  calculations include each run as they would for separately created runs.
- **`environmentIds` containing a foreign or duplicate id** → 404 / de-duplicated respectively.
- **Flaky detection (PRD-016)** alternates across environments as if they were one history, so a test
  that always fails on Firefox and passes on Chrome can look flaky. It is out of scope here, but
  `environment_id` makes partitioning the score by environment a small follow-up.
- **Backfill on a large `test_runs` table** → a few set-based statements. At this tool's scale
  (thousands of runs) that is seconds, so no batching is needed.
- **Existing API consumers reading `environment`** → still get a string. It may differ in case or
  whitespace from what they sent, since it is canonicalised. Documented in release notes.

## 5. Testing

- `ProjectEnvironmentService`: normalised matching, auto-create, unarchive on use, rename propagates
  the denormalised name to runs and bugs, merge repoints and deletes atomically, delete-in-use → 409,
  foreign id → 404, audit entries.
- Write paths: UI create with id, REST with name, CI `?environment=Staging` resolves to an existing
  `staging`, MCP `update_test_run` with `""` clears both columns, clone keeps the environment.
- Multi-environment create: N runs with distinct keys, names and environments, the same cases
  (including parameter sets) and plan linkage; limit and foreign-id validation.
- `TestRunSpecifications` `environmentId` filter; latest-result-per-environment ordering uses run time,
  not insert time (backfilled CI result).
- Build servers: `TM_ENVIRONMENT` present in trigger variables; linked run inherits it only when the
  upload named none.
- Migration: backfill on H2 and Postgres over fixture rows with case and whitespace variants, blanks and
  nulls. One environment per normalised name, all references set.
- MCP: `list_environments` project-scoped; tool responses show canonical names.
- Frontend: autocomplete add-new, multi-environment toggle, URL-bound filter, per-environment table on
  case detail, merge dialog.

## 6. Effort & Risk

- **Effort:** ~7–9 days. Schema, backfill and service ~2.5, write-path wiring (REST, CI, MCP, clone,
  bug, build server) ~2, multi-environment create and queries ~1, frontend (settings tab, pickers,
  filters, case table) ~2.5.
- **Risk:** Medium-low. The backfill is the one irreversible step: it canonicalises existing strings,
  though only case and whitespace. The denormalised name must stay in sync, which holds as long as all
  writes go through `ProjectEnvironmentService` (enforced by tests on rename and merge). Integrations
  are protected by keeping name-based input everywhere.

## 7. Acceptance Criteria

- [x] Projects have an environment catalogue that admins can create, rename, reorder, archive, merge and delete (when unused).
- [x] Existing environment strings are backfilled into the catalogue, one entry per case- and whitespace-insensitive name.
- [x] Runs and bug reports reference an environment; every existing write path still accepts a name and auto-registers unknown ones.
- [x] Testers can create one run per selected environment in a single action.
- [x] Runs can be filtered by environment; a test case shows its latest result per environment.
- [x] Build-server triggers pass `TM_ENVIRONMENT`, and reported-back runs inherit it (trigger via API only, see §8).
- [x] MCP `list_environments` exists and existing tools resolve names against the catalogue.
- [x] Backend, migration (H2 + Postgres) and frontend tests pass; en/de translations present.

## 8. As Built (2026-09-18)

Built as specified, with these differences:

- **One vendor-neutral backfill migration (V56), not two vendor files.** `MigrationVersionsTest`
  forbids the same V-number in `db/specific/postgresql` and `db/specific/h2`, and
  `gen_random_uuid()` exists in both PostgreSQL 13+ and H2 2.x (PostgreSQL mode), so the split
  wasn't needed. `EnvironmentBackfillMigrationTest` migrates a fresh database to V55, inserts
  case/whitespace variants, blanks and nulls, then applies V56. It runs on H2 in the suite and
  passed against a throwaway PostgreSQL 16 via `-Dmigration-test.url=…`.
- **`MIN(TRIM(name))` usually keeps the uppercase spelling** (`STAGING` over `Staging`), as
  specified. Admins can rename. The manual says so.
- **The UI pickers still send names, not ids.** The autocomplete offers catalogue names plus
  "Add 'foo'", and the server resolves them, so the run/bug request models didn't change.
  `environmentId` is accepted by REST (it wins over a name) and used by the multi-environment
  create and the build-server trigger. Responses carry the canonical name, not the id.
- **Clone:** omitting the environment keeps the source's (§4). `""` clears it. The dialog is
  prefilled with the source's name and now always sends the field, so clearing it really clears it.
- **Delete-while-in-use is a new `ConflictException` (409 `CONFLICT`).** A rename or create that
  clashes is the existing `DuplicateKeyException` (409 `DUPLICATE`).
- **Latest result per environment** ignores PENDING results and ABORTED runs, so a freshly
  planned run doesn't hide the last real outcome. It loads the case's executed results and keeps
  the first per environment in Java (a `ponytail:` note names the window-function upgrade).
- **My test runs filters by environment *name*,** client-side, because that page spans projects and
  ids are per project. The project run list filters by id, and its filter includes archived
  environments.
- **Across-environments** refuses `environment`/`environmentId` alongside `environmentIds`, and
  the plain create refuses `environmentIds` rather than ignoring them. Afterwards the form opens
  the run list filtered by the shared name.
- **The Automation panel doesn't offer an environment yet.** `TriggerPipelineRequest.environmentId`
  works through the API. §3.4 didn't list the panel, and it is the obvious next step.
- **Descriptions are API-only.** The settings page covers name, order, archive, merge and delete.
- **Concurrent first use of a new name** races on the unique key, and one write gets a 409. A retry
  resolves to the winner. This is noted in code and acceptable at CI-upload rates.

Tests: `ProjectEnvironmentServiceTest` (13), `EnvironmentWritePathsTest` (8), `EnvironmentRunsTest`
(10), `EnvironmentBackfillMigrationTest` (1), plus new cases in `CiIngestionApiTest`,
`ProjectScopedChildIdApiTest` (foreign id → 404, and JSON binding of `environmentId`) and
`BuildServerApiTest` (`TM_ENVIRONMENT` sent, inherited, and overridden by an explicit name).
Frontend: `environment-options`, `environment-order`, `environment-filter` and
`runs-by-environment` specs. **865 backend tests, 26 frontend spec files (124 tests).** REST was
smoke-tested against a throwaway PostgreSQL: ` staging ` resolved to an existing `Staging`.

**Still open:** clicking through the new UI in a browser, and an environment picker on the
Automation panel.
