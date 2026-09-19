# PRD-046 — Audit Trail with Old/New Values

| | |
|---|---|
| **Status** | ✅ Implemented 2026-09-19 — see §8 |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-19 |
| **Priority** | P2 — the history says *that* something changed, never *what*; three reports ask for the same missing data |
| **Target** | v2.5 |
| **Related** | PRD-006 (watchers/notifications, fed by the same audit call), PRD-011 (case versions), PRD-045 (bug triage: bulk changes and statuses), bug report `cd1412a5` (German activity sentences) |

**Source** — reports filed on the test instance (project SPI), 2026-09-17:

| Report | Asks for |
|---|---|
| `66a617d8` | Old → new values and changed fields in history and Activity; status as "A → B"; which object a comment belongs to; entries that link to the object |
| `d1bb6a83` | A change history on the bug itself (fields, status, assignment with user and time); created/updated by; an endpoint for it |
| `08d2158b` | Activity filters (date range, user, object type, action), sort order, optional export |

---

## 1. Summary

Every write already produces an `AuditEntry` (`AuditService.log`, `service/AuditService.java:35`), and
two views show them: the project Activity feed and `app-entity-history` on run, case, plan and suite
detail pages. What an entry holds is who, which action and which object — plus a free-text `details`
that most callers leave `null`:

- `TestRunService.java:436` logs a status change without the old status, though `oldStatus` is in scope.
- `BugReportService.update` (`:171`) logs a bare `UPDATED`; only `changeStatus` writes `"OLD -> NEW: reason"` (`:186`).
- Comments log `entityType = COMMENT`, `entityName = null` (`CommentService.java:57`), so "Anna created a comment" says nothing about where.
- The Activity endpoint filters by `entityId` only (`AuditController`), and the bug detail page shows no history at all.

This PRD adds structured field changes to audit entries, records the object a comment belongs to,
logs changes from every update path, shows history on bugs, makes entries link to their object, and
gives Activity real filters.

### Key decision: a JSON column on the entry, not a child table

Changes are written once and only ever read together with their entry; nobody queries "all changes to
field X". A `changes` TEXT column holding a JSON array keeps one row per event, one insert per write,
and no join on the Activity feed — at the cost of not being filterable by field, which no report asks
for. TEXT rather than `jsonb` keeps the migration vendor-neutral (H2 in tests).

## 2. Goals & Non-Goals

**Goals**
- Each audited update records the fields it changed with old and new values; status changes read
  "A → B".
- Comment entries name the object they belong to.
- History on the bug detail page, with created by / updated by in its header.
- Activity entries link to their object when it still exists.
- Activity filters: date range, user, object type, action; newest- or oldest-first; CSV export of the
  filtered list.
- Whole-sentence activity text in German and English.

**Non-Goals**
- Recording what was in a text field's full body. Long values are truncated (§3.1); PRD-011 versions
  keep full case texts.
- Undo / restore from history.
- Backfilling old entries — they keep showing what they have.
- Field-level history for custom fields beyond "name: old → new" (they use the same helper).
- Audit retention or pruning. Growth per entry is a few hundred bytes.

## 3. Proposed Design

### 3.1 Data model (next free V-number, V63 at time of writing — renumber if PRD-045 lands first)

```sql
ALTER TABLE audit_entries ADD COLUMN changes TEXT;             -- JSON array, null when none
ALTER TABLE audit_entries ADD COLUMN parent_entity_type VARCHAR(50);
ALTER TABLE audit_entries ADD COLUMN parent_entity_id UUID;
CREATE INDEX idx_audit_entries_project_user ON audit_entries (project_id, user_id, created_at DESC);
```

`changes` is `[{"field": "priority", "from": "MEDIUM", "to": "HIGH"}, …]`. Values are rendered strings:
enums by name, users by display name, dates ISO. A value longer than 200 characters is cut to 200 with
`"…"`; the entry still says the field changed.

`parent_entity_*` is set for comments (and later for anything that lives inside another object), so a
comment entry can say "on test case SPI-12" and link there.

### 3.2 Backend

- **`FieldChanges`** (small builder in `service/`): `FieldChanges.track("title", before, after)` —
  records a change only when the values differ (null and `""` count as equal). Callers snapshot the
  relevant fields before mutating, then pass the builder to `AuditService.log`.
- **`AuditService.log`** gains an overload taking `FieldChanges` and an optional parent
  (`AuditParent(type, id)`); the existing signature stays for callers with nothing to add. An update
  that changed nothing still logs, with `changes = null`, as today.
- **Callers updated:**
  - `BugReportService.update`, `changeStatus` and the PRD-045 bulk methods: title, priority, status,
    assignee, environment, resolution; the status reason stays in `details`.
  - `TestRunService.update`: name, status, environment, executor, plan. The status change becomes a
    change record, so the reopen and abort reasons stay in `details`.
  - `TestPlanService.update`: name, status, target date, assignee, gate fields.
    `TestSuiteService.update`: name; case membership changes (`:165`, `:179`) as "cases: +3 / −1".
  - `TestCaseService.update`: title, priority, status, labels, folder, estimate; steps as
    `"steps": "v4 → v5"` (the PRD-011 version numbers), since the full step text already lives in versions.
  - `CommentService`: parent type/id from the comment's own `entityType`/`entityId`, and the parent's
    display name as `entityName`.
- **`AuditEntryResponse`** gains `changes` (parsed list), `parentEntityType`, `parentEntityId`, and
  `entityExists` (false after delete, so the UI doesn't link to a 404; one `IN` query per page per type).

### 3.3 Endpoints (RBAC via PRD-001)

| Method & path | Role | Change |
|---|---|---|
| `GET /api/projects/{p}/activity?entityId&entityType&userId&action&from&to&sort&page&size` | VIEWER | New filters, all optional and repeatable where it makes sense; `sort=createdAt,asc|desc` |
| `GET /api/projects/{p}/activity/export?…same filters…` | VIEWER | CSV of the filtered entries, max 10 000 rows |

Filtering is a JPA `Specification` (`AuditEntrySpecifications`) built from the parameters that are
present — never `(:x IS NULL OR …)` JPQL, which PostgreSQL rejects for a null parameter. `from`/`to` are
dates in the user's time zone sent as ISO instants by the SPA. The CSV reuses the formula-injection guard
of the case CSV export (`TestCaseImportExportService.csvSafe`, `:124`), moved to a shared helper.

The bug history is `GET /activity?entityId={bugId}` — no separate history endpoint; it is the same data.

### 3.4 Frontend

- **Sentences.** Today a line is assembled from fragments (`activity-feed.component.html:28-30`,
  `entity-history.component.html:15`: user + `activity.actions.X` + type + name), which cannot be
  grammatical in German ("hat gelöscht Testlauf …", report `cd1412a5`). Both views switch to one
  translation key per action with placeholders — `activity.sentence.DELETED`:
  `"{{user}} hat {{type}} „{{name}}“ gelöscht"` / `"{{user}} deleted {{type}} \"{{name}}\""`. This fixes
  that report's point 4 and is the only place the sentence is built.
- **Changes** render under the sentence as `Field: old → new` rows (field names translated), status
  values through the existing status translations.
- **Links:** a small `entityRoute(type, id, parent)` mapping (case, run, plan, suite, bug, requirement,
  session, shared step; comments → their parent) makes the name a link when `entityExists`.
- **Bug detail:** `app-entity-history` added, as on the other four detail pages; header shows
  "Created by X on … · Updated by Y on …". `BugReportResponse` already has `createdBy` and
  `reporterName`; it gains `updatedBy` and `updatedByName` the same way.
- **Activity page:** a filter bar — date range, user (project members), object type, action, sort
  toggle, "Export CSV" — bound to the URL.

### 3.5 MCP impact

None. No MCP tool reads the audit log today; the invocation log (`McpActivityController`) is separate.

### 3.6 Docs

USER_MANUAL Activity section: what is recorded, filters, export; a note that entries before this
version have no field details.

## 4. Edge Cases

- **Entity deleted:** its entries stay, the name stays, the link goes (`entityExists = false`).
- **Assignee removed from the project:** the change record already holds the display name as text.
- **Several fields in one save:** one entry, several change rows.
- **Bulk change (PRD-045):** one entry per bug, each with its own old values.
- **Values that are secrets** (issue-tracker tokens, webhook secrets): never tracked — settings
  endpoints keep logging without values.
- **Huge text fields:** truncated at 200 characters; the field still appears as changed.
- **Old entries without changes:** render exactly as today.
- **Export of a large project:** capped at 10 000 rows with a note in the last line; narrow the filter.

## 5. Testing

- `FieldChanges`: equal values and null/"" produce nothing; truncation; enum and user rendering.
- Each updated caller: one test that a representative edit records the right field, old and new value.
- Comments: parent type/id and name recorded.
- `AuditController`: each filter alone and combined, sort both ways, a null-parameter combination on
  PostgreSQL (the pitfall this design avoids), paging, VIEWER allowed, non-member 403.
- Export: filters applied, formula-looking values neutralised, cap respected.
- Frontend: sentence keys for every action in en/de; change rows; link only when the entity exists;
  filter bar round-trips through the URL.

## 6. Effort & Risk

- **Effort:** ~5–6 days. Migration, helper and service overload 1, callers 1.5, endpoint and export 1,
  frontend (sentences, change rows, links, filter bar, bug history) 2.
- **Risk:** Low. Additive columns; old entries unchanged. The main cost is touching every update path —
  each gets a focused test, and a missed one simply keeps logging as today.

## 7. Acceptance Criteria

- [ ] Updates to bugs, runs, plans, suites and cases record changed fields with old and new values; status changes show "A → B".
- [ ] Comment entries name and link the object they belong to.
- [ ] The bug detail page shows its history and who created and last updated it.
- [ ] Activity entries link to their object while it exists.
- [ ] Activity can be filtered by date range, user, object type and action, sorted either way, and exported as CSV.
- [ ] Activity and history sentences are grammatical in German and English (whole-sentence templates).
- [ ] Backend, migration and frontend tests pass; en/de translations and USER_MANUAL updated.

## 8. As Built (2026-09-19)

Built as specified, with these differences:

- **Whole-sentence activity text** had already shipped in `a71a07b` (report `cd1412a5`); this PRD's
  views use it unchanged.
- **Migration V68**, not V63: PRD-045 took V66 and V67.
- **`link` instead of `entityExists`.** Each entry carries `link: {type, id}`, or null. The server
  resolves the target (the parent for a comment or attachment, otherwise the object) and checks it
  still exists, with one query per object type per page. The UI only maps the type to a route.
- **Comments on a result** have the run as their parent, since a result has no page of its own, and
  are named "run · case". **Attachments** (PRD-044) get their test case as parent too.
- **An object's history includes what lives inside it**: `?entityId=` matches the entry's object or
  its parent, so a case's history shows the comments on it.
- **A bug's status reason** is now the whole `details`; the transition is in `changes`. Entries
  written before keep their old "OLD -> NEW: reason" text.
- **What each update records:** bugs: every text field, priority, status, resolution, duplicate,
  assignee and environment. Runs: name, status, environment and plan (an update does not change the
  executor). Plans: name, description, status, target date, assignee and the four gate values.
  Suites: name, description, and membership as the case count. Cases: title, description,
  preconditions, priority, status, labels, estimate, and `version vN → vN+1` for everything the
  version history holds. Custom fields are not tracked.
- **Activity filters:** `entityType`, `userId` and `action` can be repeated; `from`/`to` are ISO
  instants, and the SPA sends local midnights so "to" covers its whole day; `sort=asc|desc`.
- **Two bugs found and fixed on the way:** "Load more" on the Activity page and the History cards
  never went away, because the SPA read a top-level `last` the paged response doesn't have. And
  `SecurityAuditorAware` only read the principal when it was a plain String, so `createdBy` /
  `updatedBy` stayed empty for any other authentication type. It now reads the authentication's
  name, like the controllers do.
- **Bug saves are flushed** before the response is built, so `updatedByName` is already the person
  who just saved.

Not tested: the 10,000-row export cap, and PostgreSQL.
