# PRD-033 — Test Case Review & Approval

| | |
|---|---|
| **Status** | 📝 Draft |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-17 |
| **Priority** | P3 — driver-dependent (compliance or agent-authored cases at volume) |
| **Target** | v2.4 |
| **Related** | PRD-001 (RBAC), PRD-011 (versioning), PRD-014 (traceability), PRD-025 / PRD-027 (MCP authoring), PRD-004 (import), PRD-005 (CI ingestion) |

---

## 1. Summary

Test cases already carry a status — `TestCaseStatus { DRAFT, ACTIVE, DEPRECATED }` — but nothing stands between the two: any TESTER can flip a case to `ACTIVE` in `TestCaseService.update` or `bulkUpdateStatus`, including the author and including an MCP agent (`TestCaseBulkTools` literally tells the agent to move DRAFT to ACTIVE "once a human has reviewed what you wrote", with nothing checking that a human did). There is no record of who approved a case, or which version they approved.

This PRD adds one state, `IN_REVIEW`, and — **only in projects that opt in** — makes `ACTIVE` mean *approved*: reachable only through an explicit approve action by someone other than the author, recorded against the exact version approved, and lost again when the wording changes. Projects that don't opt in behave exactly as today.

## 2. Goals & Non-Goals

**Goals**
- A fixed lifecycle: `DRAFT → IN_REVIEW → ACTIVE (approved) → DEPRECATED`, with `IN_REVIEW → DRAFT` for "changes requested".
- Per-project switch `reviewRequired` (default off). When on, `ACTIVE` can only be reached via approve.
- Record `approvedBy`, `approvedAt`, `approvedVersion` (PRD-011 version number) on the case.
- A content edit to an approved case drops it back to `IN_REVIEW`; cosmetic edits (labels, priority, folder) do not.
- Agent-authored cases (PRD-025) can never approve themselves.
- A "Waiting for my review" entry in the existing My Queue (`MyQueueService`).

**Non-Goals**
- Configurable workflows, custom states, multi-stage or multi-approver chains (PRD-009 §4).
- Blocking execution of unapproved cases — see §3.4; runs warn, they don't refuse.
- Review comments as a separate thread — the existing `CommentEntityType.TEST_CASE` comments are the discussion channel.
- Approval of suites, plans or requirements.

## 3. Proposed Design

### 3.1 Data model (next free V-number, V54+ at time of writing)
- `TestCaseStatus` gains `IN_REVIEW` (stored as `VARCHAR(20)`, no data migration — existing rows keep their values; existing `ACTIVE` cases are treated as approved-by-legacy with null `approved_*`).
- `test_cases`: `approved_by UUID NULL`, `approved_at TIMESTAMP NULL`, `approved_version INT NULL`.
- `projects`: `review_required BOOLEAN NOT NULL DEFAULT FALSE`.
- `test_case_versions` already snapshots `status` (see `TestCaseVersionService.snapshotBeforeEdit`); add `approved_by` / `approved_version` to the snapshot so history shows which versions were approved.

Keeping the name `ACTIVE` rather than renaming to `APPROVED` avoids touching every consumer (frontend `TestCaseStatus` type, `project-dashboard` colours, CSV import values, MCP tool descriptions, saved URL filters from PRD-002). The UI label changes to "Approved" only when `reviewRequired` is on.

### 3.2 Backend rules (`TestCaseService`)
One guard method, called from every path that writes `status` — `update`, `bulkUpdateStatus`, `create`, `TestCaseImportExportService.importData`, `McpTestCaseWriter`, `CiIngestionService`:

| Transition | `reviewRequired = false` | `reviewRequired = true` |
|---|---|---|
| any → `ACTIVE` via plain status write | allowed (today's behaviour) | **rejected** (400, "use approve") |
| `DRAFT → IN_REVIEW` | TESTER | TESTER |
| `IN_REVIEW → ACTIVE` via `approve` | TESTER | ADMIN, or TESTER with explicit grant; **approver ≠ `createdBy` and ≠ last `updatedBy`** |
| `IN_REVIEW → DRAFT` via `request-changes` | TESTER | same as approve |
| `ACTIVE → DEPRECATED` | TESTER | ADMIN |

Approver role: project ADMIN by default. A project that wants testers to review each other sets `review_required` plus a second column `reviewer_min_role VARCHAR(20) DEFAULT 'ADMIN'` accepting `ADMIN | TESTER` — a single value, not a rule engine.

**Edit of an approved case** (`reviewRequired = true`, status `ACTIVE`): if the request changes title, description, preconditions or steps, set status to `IN_REVIEW` and clear nothing — `approved_version` still names the last approved wording, so the UI can diff "approved v4 → proposed v5" using the existing PRD-011 diff view. Labels, priority and folder moves keep `ACTIVE`. The snapshot is already taken by `snapshotBeforeEdit` before mutation, so no extra versioning code.

**CI ingestion** (`CiIngestionService` line ~129 creates cases as `ACTIVE`): under `reviewRequired` it creates them `IN_REVIEW` instead — an automated test is not reviewed wording.

**MCP** (`McpTestCaseWriter` already defaults to `DRAFT`): under `reviewRequired`, `update_test_case` / `bulk_update_test_case_status` refuse `ACTIVE` with a `McpToolException` telling the agent to set `IN_REVIEW`. No `approve` tool is exposed: service users (PRD-025 §3.2) are never the "someone else" a review needs.

### 3.3 Endpoints (RBAC via PRD-001 `@RequireProjectRole`)
- `POST /api/projects/{projectId}/test-cases/{id}/submit-review` — TESTER.
- `POST /api/projects/{projectId}/test-cases/{id}/approve` — `@RequireProjectRole(TESTER)` at the edge; the service checks `reviewer_min_role` and the not-the-author rule (the aspect can't know the author). Body: `{ "version": n }` — reject 409 if the case moved on since the reviewer loaded it.
- `POST /api/projects/{projectId}/test-cases/{id}/request-changes` — same guard; optional `comment` stored via `CommentService`.
- `PUT /api/projects/{projectId}` gains `reviewRequired`, `reviewerMinRole` — ADMIN.
- Audit: `AuditService.log` with `AuditAction.UPDATED` and details `"approved v4"` / `"changes requested"` — no new `AuditAction` value needed.

### 3.4 Runs and unapproved cases
Runs may include non-`ACTIVE` cases; `TestRunService.create` does not refuse. Rationale: exploratory re-runs, hotfix verification and first-time dry runs of a new case are legitimate, and refusing would push people to rubber-stamp approvals. Instead:
- The case picker in `test-run-form` defaults its status filter to `ACTIVE` when `reviewRequired` is on.
- `TestRunService.getReport` / the PDF report (`PdfReportService`) flag results where `executedVersion != approvedVersion` as "executed unapproved wording". This is the audit record compliance actually needs.

### 3.5 Frontend
- `test-case-detail`: status chip; "Submit for review", "Approve", "Request changes" buttons driven by `MyCapabilitiesController`-style capability flags returned on the case response (`canApprove`), not by client-side role maths.
- "Approved v4 by X on date" line, linking to the version diff in `test-case-versions`.
- `test-case-list`: `IN_REVIEW` added to `allStatuses` filter; `bulk-status-dialog` hides `ACTIVE` when review is required.
- My Queue (`features/dashboard/my-queue`): "Awaiting my review" section — cases `IN_REVIEW` in projects where I meet `reviewer_min_role` and am not the author.
- Project form: "Require review before cases become active" toggle + reviewer role select.
- i18n: `testCaseStatus.IN_REVIEW` in `en.json` / `de.json`.

## 4. Edge Cases
- **Turning review on** with existing `ACTIVE` cases: they stay `ACTIVE` with null `approved_*` and show "approved before review was enabled". No mass demotion.
- **Turning review off**: `IN_REVIEW` cases stay `IN_REVIEW`; plain status writes to `ACTIVE` work again.
- **Single-member project** with review on and `reviewer_min_role = ADMIN`: nobody but the author can approve → nobody can. Project form warns when enabling review on a project with fewer than two eligible reviewers; system admins bypass the not-the-author rule only with an explicit `force` flag that is audited.
- **Concurrent edit during review**: the `version` in the approve body prevents approving wording you didn't see.
- **Bulk import** (PRD-004) of `status=ACTIVE` rows under review → imported as `IN_REVIEW`, reported in the dry-run output rather than failing the row.
- **Restore an old version** (if/when PRD-011 restore exists) is a content edit → `IN_REVIEW`.
- **Deleting the approver's user account**: `approved_by` is a plain UUID like `created_by`; display falls back to "former user".

## 5. Testing
- Transition table (§3.2) as a parameterised service test, both project modes.
- Author cannot approve own case; last editor cannot approve; different ADMIN can.
- Content edit of approved case → `IN_REVIEW` with `approved_version` unchanged; label-only edit stays `ACTIVE`.
- Stale approve (`version` mismatch) → 409.
- Every status-writing path honours the guard: REST update, bulk status, import, CI ingestion, MCP update and bulk tools.
- Report flags `executedVersion != approvedVersion`.
- Frontend: buttons render from `canApprove`; list filter includes `IN_REVIEW`.

## 6. Effort & Risk
- **Effort:** ~4–6 days (backend guard + endpoints 2, frontend 2, queue + report flag 1).
- **Risk:** Medium-low. The real risk is missing one of the six status-writing paths and leaving a bypass — hence one guard method and a test per path. Behaviour is unchanged for projects that don't opt in.

## 7. Acceptance Criteria
- [ ] `IN_REVIEW` status exists; projects have `reviewRequired` (default off) and `reviewerMinRole`.
- [ ] With review required, `ACTIVE` is reachable only via approve, by an eligible member who is neither author nor last editor.
- [ ] `approvedBy`, `approvedAt`, `approvedVersion` recorded and shown with a link to the version diff.
- [ ] Content edits to an approved case return it to `IN_REVIEW`; cosmetic edits don't.
- [ ] CI ingestion, import and MCP writes cannot produce an approved case under review.
- [ ] Run reports flag results executed against unapproved wording.
- [ ] My Queue lists cases awaiting my review.
- [ ] Projects without review enabled behave exactly as before (existing tests green).
