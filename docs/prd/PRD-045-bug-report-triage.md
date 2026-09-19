# PRD-045 — Bug Report Triage

| | |
|---|---|
| **Status** | 📝 Draft |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-19 |
| **Priority** | P2 — the bug module works for five bugs and breaks down at fifty; seven of the test team's reports are about exactly that |
| **Target** | v2.5 |
| **Related** | PRD-001 (RBAC), PRD-002 (filtering/pagination), PRD-035 (custom fields), PRD-037 (release gate counts open bugs), PRD-046 (bug change history), PRD-047 (defects in execution) |

**Source** — reports filed on the test instance (project SPI), 2026-09-17:

| Report | Asks for |
|---|---|
| `c632b339` | A readable, unique bug key (`SPI-BUG-12`) like case and run keys |
| `3a21254f` | Search and assignee/priority filters in list and Kanban |
| `84a1b810` | Multi-select and bulk assign / status / priority |
| `b9b8e361` | A NEW intake status, and a resolution when closing |
| `86da329a` | Kanban cards with key, assignee and age; all lanes visible without scrolling |
| `f3e1c5de` (bug list part) | Sortable columns in the bug list |
| `30e38de9` | A per-project template pre-filling the "Report Bug" form |

---

## 1. Summary

Bug reports have no identifier a human can say out loud, no search, no filter besides status, no
bulk action, and one status (`OPEN`) for both "just reported" and "confirmed". Triage — the weekly
job of reading new bugs, deduplicating, assigning and closing — is done one bug at a time through the
edit form.

This PRD makes triage a list operation: a key per bug, server-side search and filters, sortable and
paged results, multi-select with bulk actions, a `NEW → OPEN` intake step with a required resolution on
close, denser Kanban cards, and a per-project form template.

### Key decision: status changes go through one path

Today the Kanban drop (`bug-report-list.component.ts:79-101`) and the edit form change status with a
full `PUT` (`BugReportService.update`, `service/BugReportService.java:156`), which neither asks for a
reason nor records the transition — only `PATCH /{id}/status` does (`:177-189`, reason required by
`ChangeBugStatusRequest`). With a resolution now required on close, two paths would diverge further.
**`PUT` stops changing status**; status, resolution and duplicate link change only through the status
endpoint (single or bulk), and the Kanban drop opens the status dialog.

## 2. Goals & Non-Goals

**Goals**
- A per-project sequential key `<PROJECT>-BUG-<n>`, never reused, shown everywhere a bug is shown and
  accepted wherever a bug id is (REST lookup, MCP tools, search).
- `GET /bug-reports` with `q`, `status`, `priority`, `assignee` (incl. `none` and `me`), paging and
  sorting, reflected in the URL, used by both list and Kanban.
- Multi-select in list and Kanban; bulk assign, set priority, change status (with reason), delete.
- Status `NEW` as the default for new bugs; a resolution required when a bug is closed.
- Kanban cards with key, assignee and age; lanes that fit the screen.
- A project template for description, steps to reproduce and environment.

**Non-Goals**
- Saved filters/views. The URL is shareable; bookmark it.
- Configurable workflows or custom statuses (PRD-009 §4).
- A bug change-history view — PRD-046, which records these bulk changes too.
- Linking bugs to steps, plans or several results — PRD-047.
- Enforced placeholders or pick lists in the template; required fields and pick lists already exist as
  PRD-035 custom fields on bug reports.
- Configurable key prefix. `BUG` is fixed, like `Run` and `Session`.

## 3. Proposed Design

### 3.1 Data model (next free V-number, V63 at time of writing)

```sql
ALTER TABLE projects ADD COLUMN next_bug_number INT NOT NULL DEFAULT 1;
ALTER TABLE bug_reports ADD COLUMN bug_key VARCHAR(40);
-- Backfill in creation order, the V24 test-run pattern (portable correlated subqueries).
UPDATE bug_reports b SET bug_key = (SELECT p.project_key || '-BUG-' || CAST(
    (SELECT COUNT(*) FROM bug_reports b2 WHERE b2.project_id = b.project_id AND b2.created_at <= b.created_at)
    AS VARCHAR) FROM projects p WHERE p.id = b.project_id);
UPDATE projects p SET next_bug_number = (SELECT COUNT(*) + 1 FROM bug_reports b WHERE b.project_id = p.id);
ALTER TABLE bug_reports ALTER COLUMN bug_key SET NOT NULL;
CREATE UNIQUE INDEX idx_bug_reports_key ON bug_reports(bug_key);

ALTER TABLE bug_reports ADD COLUMN resolution VARCHAR(20);
ALTER TABLE bug_reports ADD COLUMN duplicate_of_id UUID REFERENCES bug_reports(id) ON DELETE SET NULL;
UPDATE bug_reports SET resolution = 'WONT_FIX', status = 'CLOSED' WHERE status = 'WONTFIX';
UPDATE bug_reports SET resolution = 'FIXED' WHERE status = 'CLOSED' AND resolution IS NULL;

ALTER TABLE projects ADD COLUMN bug_template_description TEXT;
ALTER TABLE projects ADD COLUMN bug_template_steps TEXT;
ALTER TABLE projects ADD COLUMN bug_template_environment TEXT;
```

Two bugs created in the same millisecond would get the same backfilled number; the unique index then
fails the migration loudly rather than silently. At this tool's scale that is theoretical — V24 made the
same trade for runs.

**Statuses.** `BugReportStatus` becomes `NEW, OPEN, IN_PROGRESS, RESOLVED, CLOSED`. `WONTFIX` goes:
it was a resolution disguised as a status, and it is exactly `CLOSED` + `WONT_FIX`. Existing rows are
migrated as above; closed bugs without a recorded reason become `FIXED`, the only honest default.
Existing `OPEN` bugs stay `OPEN` (they were already triaged by the fact of sitting there); new bugs start
as `NEW` (`BugReportService.java:105` changes).

**Resolution** (`BugResolution`): `FIXED, WONT_FIX, DUPLICATE, CANNOT_REPRODUCE, NOT_A_BUG, DEFERRED`.
Required when moving to `CLOSED`, allowed on `RESOLVED`, cleared on reopen (any move back to
`NEW/OPEN/IN_PROGRESS`). `DUPLICATE` requires `duplicateOfId` (same project, not itself). `DEFERRED`
has no target-release field: the tool has no release entity (PRD-037 §2) — put it in the reason.

"Open" for counting purposes (`ReleaseReadinessService.OPEN_STATUSES`, `MyQueueService.java:95`,
`BugReportTools.java:52`) becomes `NEW, OPEN, IN_PROGRESS` — one shared constant on `BugReportStatus`
instead of three copies.

**Key** via `ProjectSequenceService.nextBugNumber` (the existing locked-counter pattern next to
`nextTestRunNumber` / `nextSessionNumber`), set in `BugReportService.create` like
`ExploratorySessionService.java:109`.

### 3.2 Endpoints (RBAC via PRD-001)

| Method & path | Role | Change |
|---|---|---|
| `GET /api/projects/{p}/bug-reports?q&status&priority&assignee&page&size&sort` | VIEWER | Paged (`Page<BugReportResponse>`); filters are repeatable; `assignee=none`, `assignee=me` |
| `GET /api/projects/{p}/bug-reports/{idOrKey}` | VIEWER | Accepts the key |
| `PUT /api/projects/{p}/bug-reports/{id}` | TESTER | Ignores `status` (see §1) |
| `PATCH /api/projects/{p}/bug-reports/{id}/status` | TESTER | Body gains `resolution`, `duplicateOfId` |
| `PATCH /api/projects/{p}/bug-reports/bulk` | TESTER | `{ids[≤100], assigneeId?, clearAssignee?, priority?, status?, resolution?, reason?}` |
| `POST /api/projects/{p}/bug-reports/bulk-delete` | TESTER | `{ids[≤100]}`, same rule as single delete |
| `GET /api/projects/{p}` | VIEWER | Project response carries the three template fields |
| `PUT /api/projects/{p}/bug-template` | ADMIN | Sets the template |

The existing `testResultId` / `environmentId` query parameters stay and become filters like the rest.
The paged response is a breaking change for the list endpoint; the SPA is the only consumer besides MCP,
which gets its own paging (§3.5).

**Search** is `LIKE` over title, description, steps, expected/actual behaviour, environment and key,
case-insensitive — via a JPA `Specification`, never `(:q IS NULL OR …)` JPQL, which PostgreSQL rejects
when the parameter is null. No full-text index: PRD-007's `tsvector` search covers cases; bug volume
per project is in the hundreds.

**Bulk** validates every id against the project first and every assignee against membership
(`requireProjectMember`), then applies all changes in one transaction — all or nothing, with a 400
naming the offending ids. A status change in bulk goes through the same method as a single change
(reason required, resolution rules, one audit entry per bug with `OLD -> NEW: reason`).

### 3.3 Backend

- `BugReportFilter` record + `BugReportSpecifications` (the `TestCaseSpecifications` pattern).
- `BugReportService.search`, `bulkUpdate`, `bulkDelete`; `changeStatus` gains the resolution rules;
  `update` drops the status line.
- `BugReportResponse` gains `key`, `resolution`, `duplicateOfId`, `duplicateOfKey`.
- Notifications and activity show the key (entity name becomes `"<KEY> <title>"`).

### 3.4 Frontend

- **List:** a search box and assignee/priority/status multi-selects bound to query params
  (`c83ca71`'s pattern for cases and runs), `MatSort` on key, title, priority, status, assignee, created
  (server-side sort), a key column, a paginator. Empty result with active filters says so and offers
  "clear filters".
- **Selection:** checkboxes in the list and on Kanban cards, "select all on this page", an action bar
  with Assign (members, Unassigned, Me), Priority, Status (opens the status dialog with reason and
  resolution) and Delete (confirm with count).
- **Kanban:** lanes `NEW · OPEN · IN_PROGRESS · RESOLVED · CLOSED` as `flex: 1 1 0; min-width: 180px`
  instead of the fixed `width: 220px; flex-shrink: 0` (`bug-report-list.component.scss:62-72`), so five
  lanes fit a 1280 px screen; horizontal scroll only below that. Cards show key, assignee initials and
  age ("3 d"). A drop opens the status dialog; cancelling puts the card back.
- **Status dialog:** resolution select when the target is `CLOSED` (optional on `RESOLVED`), a
  duplicate picker (search by key/title) for `DUPLICATE`.
- **Form:** on create, empty description/steps/environment are pre-filled from the project template.
  Editing a bug never re-applies it.
- **Project settings:** a "Bug report template" section for admins.

### 3.5 MCP impact

- `list_bug_reports` gains `query`, `priority`, `assignee` and paging; results carry `key`.
- `get_bug_report` and `change_bug_report_status` accept the key; the latter takes `resolution` and
  `duplicateOf`.
- New `assign_bug_reports(ids or keys, assignee | "none")` — assignment is the one bulk change agents
  were asked for. No bulk delete over MCP (no delete tools, `MCP_SETUP.md` §4).
- `create_bug_report` applies the project template to empty fields, like the form.

### 3.6 Docs

USER_MANUAL bug report section: keys, search syntax, bulk actions, the NEW/OPEN distinction and
resolutions, the template. MCP_SETUP: the new tool and parameters.

## 4. Edge Cases

- **Key after a project key rename:** keys are stored, not derived, as for cases and runs; old bugs
  keep the old prefix. Lookup by key is exact.
- **Deleted bug:** its number is not reused (the counter only increases).
- **Duplicate chains** (A duplicate of B, B later duplicate of C): allowed; the detail page links to the
  direct target only. Self-reference is refused. Deleting the target clears the link (`ON DELETE SET
  NULL`) and leaves the resolution as `DUPLICATE`.
- **Reopening a closed bug:** clears resolution and duplicate link; reason required as for any change.
- **Bulk assign with a non-member id:** 400 listing it, nothing changed.
- **Bulk over 100 ids:** 400; the UI selects at most one page (≤ 100).
- **Kanban with filters:** lanes show filtered bugs only; the lane count says "12 of 30" when filtered.
- **Release gate (PRD-037):** `NEW` counts as open, so a flood of untriaged bugs holds a gate as before.
- **Template on MCP/API create** only fills empty fields; an agent's text is never overwritten.

## 5. Testing

- Migration on H2 and PostgreSQL: keys backfilled per project in creation order, counter set,
  `WONTFIX` rows become `CLOSED/WONT_FIX`, closed rows get `FIXED`, unique index present.
- `BugReportService`: key assigned and sequential per project; search matches each field and the key;
  each filter and `assignee=none/me`; sort; paging.
- Status rules: close without resolution → 400; `DUPLICATE` without or with foreign/self target → 400;
  reopen clears resolution; `PUT` with a different status leaves status unchanged.
- Bulk: all-or-nothing on a foreign id and on a non-member; status change writes one audit entry per bug;
  more than 100 → 400; VIEWER → 403.
- MCP: lookup by key, `assign_bug_reports` with keys and `"none"`, list filters.
- Frontend: filters round-trip through the URL; selection survives paging within a page only; drop opens
  the dialog and cancel restores the card; template pre-fill on create but not on edit.

## 6. Effort & Risk

- **Effort:** ~6–8 days. Migration and key 1, search/filter/paging 1.5, status/resolution 1.5, bulk 1,
  frontend list/Kanban/dialog/template 2–3.
- **Risk:** Medium-low. The status enum change touches every consumer of `BugReportStatus` (release
  gate, my queue, MCP, i18n, Kanban); the shared "open statuses" constant keeps that to one decision.
  The paged list endpoint changes a response shape — the SPA and MCP are updated in the same change.

## 7. Acceptance Criteria

- [ ] Every bug has a key `<PROJECT>-BUG-<n>`, existing bugs backfilled in creation order; keys are never reused and are accepted by REST and MCP lookups.
- [ ] The bug list searches title, texts, environment and key, filters by status, priority and assignee (incl. unassigned and me), sorts and pages server-side, and keeps all of it in the URL.
- [ ] Bugs can be selected in list and Kanban and bulk-assigned, re-prioritised, moved (with reason) and deleted, all-or-nothing.
- [ ] New bugs start as NEW; closing requires a resolution; DUPLICATE requires a same-project target; WONTFIX rows are migrated to CLOSED/WONT_FIX.
- [ ] Status changes only through the status endpoint; the Kanban drop asks for a reason.
- [ ] Kanban cards show key, assignee and age, and five lanes fit a 1280 px screen.
- [ ] A project admin can set a template that pre-fills new bug reports from the UI and MCP.
- [ ] MCP `assign_bug_reports` exists; list/get/status tools know keys, filters and resolutions.
- [ ] Backend, migration and frontend tests pass; en/de translations and USER_MANUAL updated.
