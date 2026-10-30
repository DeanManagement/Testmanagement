# PRD-054: Configurable Bug Workflow and Handover to the Issue Tracker

**Source:** `docs/reviews/TM-Tool_Bugs_und_Verbesserungsvorschlaege_Nachpruefung.docx` (project SN Syrona Next, 29.10.2026).
**Status:** Proposed — needs a go/no-go per section before implementation.

The follow-up review's other items (TM41, TM42, TM44, TM45, TM46, TM48, TM49) are implemented
separately. This PRD covers the two that add a concept and change the schema: TM47 and TM50.

The sections are independent, but TM50's status mapping gets better with TM47 (§2.3). Sizes:
S ≤ 1 day, M ≤ 3 days, L ≤ 2 weeks. Migration numbers below are relative: V97 and V98 went to
TM49 and TM46, so this starts at V99.

---

## 1. Configurable bug workflow (TM47) — L

**Problem.** Bug statuses (`BugReportStatus`: NEW, OPEN, IN_PROGRESS, RESOLVED, CLOSED) and
resolutions (`BugResolution`, six values) are fixed, and any status can follow any other. Result
statuses became configurable in TM24 (`0a69e3a`); bug statuses did not. Teams coming from Jira
expect their own statuses and transitions, e.g. "Ready for retest" between "In progress" and
"Closed".

**Design — the TM24 pattern, applied to bugs.** `BugReportStatus` stays the *category* every
metric reads; projects name statuses on top of it.

- **Schema (one migration).**
  - `project_bug_statuses(id, project_id, name, name_normalized, color, category, position,
    archived, created_*/updated_*)`, unique on `(project_id, name_normalized)`, `CHECK category IN
    ('NEW','OPEN','IN_PROGRESS','RESOLVED','CLOSED')` — a copy of `project_result_statuses` (V81).
  - `bug_reports.custom_status_id` UUID, FK `ON DELETE SET NULL`, indexed. `bug_reports.status`
    keeps holding the category, unchanged.
  - `bug_status_transitions(id, project_id, from_status_id, to_status_id, created_*/updated_*)`,
    unique on `(from_status_id, to_status_id)`, both FKs `ON DELETE CASCADE`.
  - Seed one status per category for every existing project ("New", "Open", "In progress",
    "Resolved", "Closed"), and point every existing bug at the seeded status of its category, so
    no bug has a null custom status and the UI has a single code path. New projects are seeded by
    `BugStatusService.seedDefaults`, called from `ProjectService` next to the result-status seed.
- **Entities.** `ProjectBugStatus` mirrors `ProjectResultStatus` (category `updatable = false`).
  `BugReport.setCustomStatus(status)` sets `status` to its category, like
  `TestResult.setCustomStatus`; setting a category directly picks that category's first active
  status (lowest `position`), so callers that only know the category keep working.
- **Transitions.** No rows for a project = any transition allowed (today's behaviour). Once a
  project has rows, a change from A to B needs the row A → B; staying in the same status is always
  allowed. The check lives in `BugReportService.applyStatus`, which the single change, the bulk
  change (`BugReportBulkService`, all-or-nothing) and MCP already share. The resolution rules stay
  on the category: CLOSED needs a resolution, RESOLVED takes one optionally, open categories clear
  it — so "Ready for retest" (category RESOLVED) needs no new rule.
- **What keeps working through the category, unchanged:** the release gate's "max blocker bugs"
  (`ReleaseReadinessService`, `countByProjectIdAndPriorityAndStatusIn(..., OPEN_STATUSES)`),
  My queue's open and stale bugs (`MyQueueService`), the defect dashboard counts, open-by-priority
  and resolution trend (`DefectDashboardService`, `resolvedAt` via `trackResolution`), the
  duplicate check (`BugReportTools`, `isOpen()`), the link-bug dialog's open filter
  (`OPEN_BUG_STATUSES`), sorting by status (`BugReportSort`), and the only bug webhook,
  `BUG_REPORT_CREATED` (always the NEW category).
- **What must change.**
  - Backend DTOs: `BugReportResponse` gains `customStatus {id, name, color}`;
    `ChangeBugStatusRequest` and `BulkUpdateBugReportsRequest` take `statusId` (category derived;
    the plain `status` stays accepted and resolves to the category's first status);
    `BugReportFilter` gains `statusIds` next to the category list. `WatchedItemsService` and the
    audit snapshot (`FieldChanges`) show the custom name instead of `status.name()`. `MyQueueResponse`
    and `McpDtos.BugSummary`/bug detail carry the name.
  - Endpoints: `ProjectBugStatusController` (list for members; add, rename, recolour, reorder,
    archive and edit transitions for project ADMIN); the list response includes each status's
    allowed targets, so the UI needs no extra call.
  - Frontend: `bug-report.model.ts` (`ALL_BUG_STATUSES` and `takesResolution` become category
    helpers); the kanban (`bug-report-kanban`) gets one lane per active status, ordered by category
    then position, and refuses drops onto lanes not allowed from the card's status
    (`cdkDropListEnterPredicate`); the detail status select and the list's bulk-status menu offer
    only allowed targets; `change-bug-status-dialog` keys the resolution field on the target's
    category; list filters take statuses (the URL keeps `status=` for categories and adds
    `statusId=`). The defect dashboard stays per category.
  - Admin UI: `bug-status-settings` component at `projects/:id/bug-statuses`, linked from the
    project detail next to result statuses, reusing the `result-status-settings` layout plus a
    transition grid (rows "from", columns "to", checkboxes; "Allow any transition" clears it).
  - MCP: `change_bug_status` and `bulk_update_bug_reports` take an optional `customStatus` name
    (unknown or not-allowed names are refused with the valid ones listed, as `record_test_result`
    does for TM24); `list_bug_reports` filters by it; `get_bug_report` shows it and the allowed next
    statuses. A read-only `list_bug_statuses`. Editing statuses over MCP only through the TM37
    `CONFIGURATION` group, if at all.

**Edge cases.**
- Archiving: the last active status of a category cannot be archived (TM24 rule). Bugs keep an
  archived status and show it marked as archived; it is not offered as a target. Transitions from
  it stay valid so the bug can move on; transitions *to* it are ignored.
- A category is fixed once created; to "move" a status, archive it and add a new one.
- A transition table that strands bugs (a status with no way out) is allowed but flagged in the
  settings page as a warning, not refused.
- New bugs start in the NEW category's first active status. Bugs filed by CI, sessions and MCP go
  through the same path.
- Status changes coming from the tracker (§2) bypass the transition table — the tracker is the
  authority there.
- Resolutions stay fixed. The review asks for statuses and transitions only; configurable
  resolutions would touch `DefectDashboardService` and the DUPLICATE rule for little gain.

**Acceptance.** A project adds "Ready for retest" (RESOLVED) and allows only In progress → Ready for
retest → Closed / Open. The kanban shows the lane, refuses a drag from New to Closed, the REST API
and MCP refuse it with the allowed targets listed, and the release gate, My queue and defect
dashboard give the same numbers as with the category alone. A project with no transitions behaves
exactly as today.

---

## 2. Hand a bug report to the issue tracker (TM50) — M (Azure DevOps); +S per other provider

**Problem.** Internal bugs and tracker issues are separate worlds. From a failed result a tester
files either a bug report (`BugReportService`) or a tracker issue (`IssueLinkService.link`, MCP
`create_linked_issue`) — never both, and an existing bug cannot be moved to the tracker. Developers
work in Azure DevOps, so the bug's status in the tool goes stale. In Xray a defect *is* the Jira
issue.

**What exists.** `IssueTrackerProvider` (`search`, `create`, `get`, `testConnection`) with
`AzureDevOpsIssueProvider`, which already files a work item of the configured type (default
`Bug`) with the body in Repro Steps, and resolves each work item state to its *state category*
(`Proposed`, `InProgress`, `Resolved`, `Completed`, `Removed`) via a cached
`workitemtypes/{type}/states` lookup — then collapses it to `IssueState` OPEN/CLOSED/UNKNOWN.
`IssueLink` belongs to one `test_result_id` (NOT NULL, unique with `external_id`, V42).
`IssueStatePoller` → `IssueStateRefresher.refreshProject` refreshes stale links on planned or
in-progress runs, batch-capped, oldest first. The requirement sync (`bdf16d4`, `eebb436`) is the
precedent for reading ADO in bulk (`queryWorkItems`, `workitemsbatch`) and for refusing local
edits of tracker-owned fields (`RequirementService.refuseSyncedFieldChanges`).

### 2.1 Handover

- **Link storage: reuse `IssueLink`.** Migration: `issue_links.test_result_id` becomes nullable,
  add `bug_report_id` UUID FK → `bug_reports` `ON DELETE CASCADE`, `CHECK` exactly one of the two
  is set, `UNIQUE (bug_report_id)` (one tracker issue per bug; NULLs don't collide in PostgreSQL or
  H2). Add `tracker_state VARCHAR(100)` (the raw name, e.g. "Active") and `state_category
  VARCHAR(20)`. This keeps the provider/URL denormalisation, `followIfMoved` and the chip UI, and
  avoids a second refresh loop. Columns on `bug_reports` would be simpler to read but duplicate all
  of that.
- **Content.** `IssueDraft` gains an optional `priority`. A `BugIssueDraft` builder (next to
  `IssueLinkService.buildBody`) composes the body from description, steps to reproduce, expected
  and actual behaviour as Markdown sections, plus the bug key with a link back, environment, and
  test case / run keys when the bug came from a result. The ADO provider escapes it and keeps line
  breaks, as today — no new Markdown dependency. Priority: CRITICAL → 1, HIGH → 2, MEDIUM → 3,
  LOW → 4 (the inverse of the requirement sync), into `Microsoft.VSTS.Common.Priority`.
  Attachments: not in the first step; later via ADO's two-call upload (`_apis/wit/attachments`,
  then an `AttachedFile` relation), opt-in per handover, under the existing 10 MB cap.
- **Two ways in.** "Hand over to Azure DevOps" creates the work item; "Link existing work item"
  takes an id (search reuses `IssueLinkService.search`) for when a developer already filed it.
  Both: `POST /api/projects/{projectId}/bug-reports/{bugId}/tracker-issue` (TESTER, like issue
  links), body `{ existingExternalId? }`. `DELETE` detaches.
- **Bug detail** shows the work item chip (id, title, tracker state, last checked, error if any)
  and a "Refresh" action; the list and kanban show a small tracker badge.
- **MCP:** `hand_over_bug_report(bugKey, existingIssueId?)`, `detach_bug_report_issue(bugKey)`;
  `get_bug_report` and `list_bug_reports` include the link and tracker state.

### 2.2 Status follows the tracker

- `IssueTrackerProvider` gets `get` returning the state category alongside `IssueState` (extend
  `Issue` with `trackerState` and `stateCategory`; other providers leave the category null).
- `IssueLinkRepository.findStaleForBugs(projectId, staleBefore, page)`: every bug link of the
  project, oldest-checked first, under the same batch cap. Not limited to open bugs — a work item
  reopened in ADO must reopen the bug. `refreshProject` runs it after the result links.
- On a changed category the refresher calls a system-actor variant of
  `BugReportService.applyStatus` (bypassing TM47 transitions), audited as "Status changed by
  Azure DevOps (Active → Resolved)":

  | ADO state category | Bug category | Resolution |
  |---|---|---|
  | Proposed | OPEN | cleared |
  | InProgress | IN_PROGRESS | cleared |
  | Resolved | RESOLVED | unchanged |
  | Completed | CLOSED | `FIXED`, or from `Microsoft.VSTS.Common.ResolvedReason`: Cannot Reproduce → CANNOT_REPRODUCE, As Designed → NOT_A_BUG (Duplicate stays FIXED: DUPLICATE needs a local bug to point at) |
  | Removed | CLOSED | `WONT_FIX` |

  For Jira/GitLab/GitHub/Forgejo only OPEN/CLOSED is known: CLOSED → CLOSED/FIXED, OPEN after
  CLOSED → OPEN, otherwise the bug's status is left alone.
- **With TM47:** a mapping table `bug_tracker_state_mappings(project_id, tracker_state,
  bug_status_id)` edited on the bug-status settings page lets "Ready for test" in ADO land on
  "Ready for retest" here. Unmapped states fall back to the category's first status.
- **Local edits after handover — recommendation:** status and resolution are owned by the tracker
  and refused locally (REST, bulk, kanban drag, MCP) with "status follows AB#123", the same shape
  as `refuseSyncedFieldChanges`. Title, texts, priority, labels and assignee stay editable but are
  *not* pushed; the detail view says so. Two-way sync is out of scope: it needs conflict rules and
  webhooks from ADO that an air-gapped or firewalled install cannot receive. Detaching returns
  full local control and keeps the last status.

### 2.3 Edge cases

- **No tracker, or inactive:** the actions are hidden; the endpoint and MCP refuse with "No issue
  tracker is configured for this project". Bug reports disabled for the project: refused by
  `requireBugReportsEnabled` as today.
- **Token lacks permission / wrong work item type:** the provider throws
  `UpstreamServiceException` (existing messages for 401/403 and for the type); nothing is stored,
  the UI shows the message, `configService.recordError` notes it.
- **Work item deleted remotely:** today the ADO `get` uses `getJson`, whose 404 throws "project
  not found" — and the refresher then *stops the whole project's batch*. That is an existing defect
  for result links too. Fix first: ADO `get` uses `getJsonIfFound`; a missing item sets the link
  state to UNKNOWN with "work item no longer exists", leaves the bug status alone, unlocks status
  editing and offers Detach.
- **Work item moved to another ADO project:** `followIfMoved` adopts the new id; its duplicate
  check must look at `bug_report_id` for bug links.
- **Tracker switched to another provider:** the link keeps its provider and is skipped by the
  refresher (existing rule); the bug shows it as no longer synced and status unlocks.
- **Double handover / race:** refused if the bug already has a link (409 naming the work item);
  the unique index catches a concurrent second request. The remote call happens before the local
  save, so a lost race can leave an orphan work item — logged at WARN with its id. Acceptable;
  the button is disabled while pending.
- **Duplicates:** a bug closed as DUPLICATE of a handed-over bug cannot be handed over itself; the
  UI offers "Link existing work item" with the original's id instead. Two bugs may not share one
  work item (the status would fight) — refused with the other bug's key.
- **Re-handover after detach:** creates a new work item unless an existing id is given; the old
  item is untouched remotely.
- **Bug deleted locally:** the link cascades; the work item stays in ADO.
- **Poll load:** bug links add to the per-project batch; the cap and the 10-minute state-category
  cache keep the call count bounded.

### 2.4 Out of scope

Pushing local edits or comments (TM44) to the tracker; ADO service hooks; handing over many bugs
at once (add a bulk action only if asked); turning the failed-result "create issue" action into
"file bug and hand over" — a follow-up once this ships.

**Acceptance.** On a project with an Azure DevOps connection, a tester hands bug SN-BUG-12 over;
a `Bug` work item appears with title, the four text sections, priority 2 for HIGH and a link back.
The bug shows the work item, its status select is locked, and when the work item moves to Resolved
and then Closed in ADO, the bug follows to RESOLVED and CLOSED/FIXED within one poll interval, with
audit entries. Deleting the work item in ADO marks the link broken without stopping the poll for
other links. The same works through `hand_over_bug_report`.

---

## Open questions

1. TM47: should a project ADMIN be able to override the transition table (e.g. for cleanup), or is
   it binding for everyone?
2. TM50: lock only status/resolution after handover (recommended), or lock the texts too like
   synced requirements?
3. TM50: which ADO process does SN use (Agile, Scrum, CMMI)? It decides whether the default state
   mapping is enough or the TM47 mapping table is needed from day one.
4. TM50: ship for Azure DevOps only (like the requirement sync), or keep the coarse OPEN/CLOSED
   following for the other providers in the same change?

## Suggested order

Fix the deleted-work-item poll defect (§2.3) on its own first — it affects existing result links.
Then TM50 for Azure DevOps without TM47 (category mapping only), since it removes the duplicate
bookkeeping SN does today; then TM47, adding the state mapping table as its last step.
