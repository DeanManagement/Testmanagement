# Testmanagement — Flow & Usability Review + Feature Proposals

_Reviewer: Claude · Date: 2026-05-13_
_Scope: deep audit of backend (`backend/src/main/java/com/deanmanagement/testmanagement/**`) and Angular frontend (`frontend/src/app/**`)._
_Lens: keep the tool simple and fast. Prioritise daily-use speed and CI / external-system integration._

---

## 1. Executive Summary

The tool is in a strong place. v1 is complete, and large parts of the documented v1.1 and v1.2 are already implemented (PDF export, audit log, comments, test plans, bulk operations, folder hierarchy, watchers, Allure upload, bug reports). The data model is clean, the NgRx state layer is consistent, fetch strategies generally avoid N+1, and i18n coverage is roughly 95%.

The biggest opportunities are not new entities — they are **friction reduction on the hot path** (executing a test run) and **filling the integration gap** between this tool and the systems your testers and CI already use. Concretely:

- **Test run execution still requires a mouse.** No keyboard shortcuts to set Pass/Fail, navigate between cases, or jump to a step. For a tester running 50 cases this is the single biggest time sink.
- **No query-side filtering anywhere on the backend.** Every list endpoint returns the full collection; the frontend filters client-side after loading everything. This will not scale beyond a few hundred test cases per project.
- **REQUIREMENTS.md is out of date.** The roadmap reflects v1; the code is already past v1.2 on many fronts and has features the doc never mentions (Allure, bug reports, folders, watchers, step images, test run keys, force-password-change, dashboard analytics).
- **The webhook + CSV import items in the documented v1.2 backlog are still missing**, and they are the highest-leverage integration features.

This document proposes 14 enhancements, grouped into quick wins (≤ 2 days each), medium bets (~1 week), and bigger structural moves. Most are scoped to keep the simplicity bar intact.

---

## 2. Snapshot — What's Actually Implemented vs Documented

REQUIREMENTS.md describes v1 as done and lists v1.1/v1.2 as roadmap. Code reality:

| Capability | REQ.md status | Code reality | File evidence |
|---|---|---|---|
| Projects, members, roles | v1 done | ✓ done | `ProjectController`, `ProjectMemberController` |
| Test cases + steps + labels | v1 done | ✓ done + folder tree | `TestCaseController`, `TestCaseFolder` entity (V27) |
| Test suites, runs, results, step results | v1 done | ✓ done | `TestSuiteController`, `TestRunController` |
| Screenshots (BYTEA) | v1 done | ✓ done | `ScreenshotController` |
| API keys + external CI endpoint | v1 done | ✓ done | `ApiKeyController`, `ExternalTestRunController` |
| PDF export (v1.1) | planned | ✓ done | `PdfReportService` |
| Project dashboard (v1.1) | planned | ✓ done | `DashboardService` + `DashboardResponse` |
| Bulk operations (v1.1) | planned | ✓ done | `BulkStatusRequest`, `BulkDeleteRequest`, `BulkTestCasesRequest` |
| Audit / activity log (v1.1) | planned | ✓ done | `AuditEntry`, `AuditController` (V18, V33) |
| Comments (v1.2) | planned | ✓ done | `CommentController`, `CommentService` |
| Test plans / milestones (v1.2) | planned | ✓ done | `TestPlanController` + assignee (V30) |
| **Webhooks (v1.2)** | planned | **✗ missing** | no `WebhookController` |
| **CSV / JSON import-export (v1.2)** | planned | **✗ missing** | no import handler |
| OIDC support (v2) | future | ✗ missing | local JWT only |
| Notifications (v2) | future | partial | watchers exist, no delivery |
| Dark mode (v2) | future | ✗ missing | Material default theme only |
| Global search (v2) | future | ✗ missing | no `/api/search` |
| **Allure report upload** | undocumented | ✓ done | `AllureReportController` (V23) |
| **Bug reports** | undocumented | ✓ done | `BugReport` entity (V25), my-bug-reports route |
| **Entity watchers** | undocumented | ✓ done | `EntityWatcher` (V30) |
| **Step reference images** | undocumented | ✓ done | `StepImage` (V21) |
| **Test run keys** | undocumented | ✓ done | V24 migration |

**Action: refresh REQUIREMENTS.md.** It is the single artefact a new contributor will read first and it currently misrepresents the project. This is a 30-minute job and worth doing before the next contributor (human or LLM) touches the codebase.

---

## 3. Usability Findings

Severity scale: **High** = costs time on every session; **Medium** = costs time often or blocks specific users; **Low** = polish.

### 3.1 Test run execution is mouse-only (High)

`test-run-detail.component.ts` is the hottest path in the app. A tester executing a 30-case run does this loop dozens of times:

1. Click a test result in the sidebar
2. Click the status dropdown → wait for menu → click "Passed"
3. For each step: click step status dropdown → wait → click status; click into the "actual result" textarea; type
4. Click "Next" button at the bottom

There are no keyboard shortcuts. `navigateResult('prev' | 'next')` exists as a method (lines 251–258) but only Prev/Next buttons trigger it. Every status change is a multi-click chain through a `mat-select`. There is `executionSearchTerm` to filter results in the sidebar but no focus shortcut.

**Why this matters:** for manual testing this is the entire workflow. Cutting Pass/Fail from 3 clicks to 1 keypress, and Next from a button to `J`/`K`, is the single highest-impact change in the app.

### 3.2 Every active-result switch re-fetches comments and bug reports (High)

`setActiveResult` (line 229) dispatches `CommentActions.clearComments()` and a new `loadComments` action, plus `BugReportActions.loadBugReportsByTestResult` if bug reports are enabled. Switching between two cases means two round-trips, even if both were viewed seconds ago.

For a tester reviewing failures, the comment thread blinks in and out repeatedly. Cache by `resultId` for the lifetime of the run.

### 3.3 No backend query filtering on the main list endpoints (High)

`TestCaseController` accepts only `folderId` and `rootOnly`. No `?status=ACTIVE&priority=HIGH&label=smoke&q=login` filters. `TestRunController.findAll` returns every run for a project; only the `MyTestRunController.assigned-to-me` variant supports a `statuses` filter (and even there, no date or text filter). The frontend therefore loads the full list and filters in `*ngFor`. The list components are not virtualised either, so a project with 1 500 test cases serialises ~1 MB of JSON on every visit and renders all 1 500 rows in the DOM.

`AuditController` is the only paginated list. This is fine at 50 users / 5 concurrent today but will hurt the first time someone imports a real test catalogue from another tool.

### 3.4 Filters reset on every navigation (Medium)

The list filters (`searchTerm`, `statusFilter`, `priorityFilter`, selected folder) are component state, not query-string state. Drilling into a test case detail and clicking the browser back button drops the filters. There is no "saved filter" concept.

Fix: bind filters to query params (Angular Router supports this natively), and persist last-used filters per project in `localStorage`.

### 3.5 Test case list has no column customisation, density, or virtual scrolling (Medium)

`displayedColumns = ['select', 'key', 'title', 'priority', 'status', 'labels', 'actions']` is hard-coded. With Material default spacing, ~12 rows fit on a 1080p screen. No compact mode toggle. `MatTable` is not paired with `cdk-virtual-scroll-viewport`.

### 3.6 Project role enforcement is implicit (Medium — correctness)

Backend services do not check `ProjectRole` before allowing writes. The model defines `Admin`, `Tester`, `Viewer`, but a Viewer who reaches an endpoint can likely still PUT a test case — controllers only verify the user is _a_ project member, not what role they hold. Confirm this with a quick test:

```bash
curl -X PUT \
  -H "Authorization: Bearer $VIEWER_TOKEN" \
  -d '{...}' \
  /api/projects/{id}/test-cases/{tcId}
```

If this returns 200, you have a quiet authorization bug. Recommendation: introduce a `@RequireProjectRole(MIN_ROLE)` annotation with an `AOP` aspect that loads the caller's `ProjectMember` and rejects with 403 if their role is below the minimum.

### 3.7 No bulk select on test results within a run (Medium)

Bulk select works on the test case list, but inside a run you cannot multi-select results to mark "all remaining as Skipped" or "these 10 as Blocked." For runs interrupted by an environment outage this is painful.

### 3.8 "Mark all steps as Pass" missing (Medium)

The execution UI requires marking each step independently in addition to the overall result. For test cases where every step truly passed, there is no one-click way to set all step statuses to `PASSED`. Many smaller competing tools have a "mark result Pass and cascade to steps" toggle.

### 3.9 No global search (Medium)

There is no top-bar search. A tester who remembers "the login redirect test" must navigate to the right project, then to test cases, then type into the list search. A `Ctrl-K` / `Cmd-K` palette that searches projects, test cases by key/title, runs, and bug reports would remove a lot of clicks.

### 3.10 Defect linking is a free-text URL field (Medium — integration)

`TestResult.defectLink` is a single string. No issue-tracker integration, no preview of bug status, no "open new Jira/GitHub issue" button. The internal `BugReport` entity now exists, which is good, but external tracker integration is still untouched and that is where most teams live.

### 3.11 Chart labels are hardcoded English (Low)

Confirmed by frontend audit: `TestPlanDetailComponent` and similar pages have Chart.js labels like `"Passed"`/`"Failed"` literal strings, despite full i18n elsewhere. Translate them via `translate.instant(...)` when building chart datasets.

### 3.12 No mobile/tablet posture for the execution screen (Low)

The two-pane execution layout collapses awkwardly under 1024px. The Shell uses `BreakpointObserver` to flip the sidenav, but the run-detail page itself is desktop-only in practice. Useful for testers on tablets during physical-device testing — common in QA.

### 3.13 No streaming for large blob downloads (Low)

Both `ScreenshotController.download()` and `StepImageController` return `ResponseEntity<byte[]>`, so the whole blob is loaded into the JVM heap before being written. At the 10 MB cap, with 5 concurrent users, that is ~50 MB resident. Fine today, but switch to `StreamingResponseBody` reading from a `Blob` input stream when you next touch this code. (Both already set `Cache-Control: max-age=365d, immutable`, which is good — that part is fine.)

---

## 4. Feature Proposals

Each proposal lists: what, why, sketch of implementation, rough effort (`S` = ≤ 2 days, `M` = ~1 week, `L` = ~2–3 weeks). All keep the "small team, simple, fast" bar.

### 4.1 (S) Keyboard-driven test execution

The biggest single-day improvement. Add a `@HostListener('document:keydown')` on `TestRunDetailComponent`:

| Key | Action |
|---|---|
| `J` / `↓` | Next result |
| `K` / `↑` | Previous result |
| `P` | Mark active result Passed (and cascade to all steps if none set) |
| `F` | Mark active result Failed |
| `B` | Mark active result Blocked |
| `S` | Mark active result Skipped |
| `Shift+P` | Mark all _step_ statuses Passed |
| `C` | Focus the comment textarea |
| `N` | Focus the "actual result" textarea of the next step with empty value |
| `?` | Toggle a keyboard-shortcut cheatsheet overlay |

Guard rails: ignore keys when an `<input>` / `<textarea>` is focused (check `event.target` against `INPUT`/`TEXTAREA`/`contenteditable`). Always show the cheatsheet hint in the run-detail header so the shortcuts are discoverable.

Expected impact: cuts result-marking from 3+ clicks to 1 keypress. For a 50-case run, that is roughly 5–10 minutes saved.

### 4.2 (S) Cache comments and bug reports per result in the run

Stop firing `CommentActions.clearComments` on every `setActiveResult`. Instead, store comments keyed by `resultId` in the NgRx `comment` slice (already an entity adapter — change the `entityId` field to a composite key or maintain a per-result loaded set). Only fetch if not already loaded for the current run session. Same pattern for `BugReportActions.loadBugReportsByTestResult`.

Quick check: keep an in-memory `Set<resultId>` of "loaded in this session" on the component; skip the dispatch if already present.

### 4.3 (S) Query-string filters for test cases and runs

Two changes:

1. **Backend** — add `@RequestParam` filters to the existing list endpoints. Use Spring Data JPA `Specification<TestCase>` to compose predicates dynamically. Supported params: `?q=` (title contains), `?status=`, `?priority=`, `?label=` (repeatable), `?folderId=`, `?createdAfter=`, `?updatedAfter=`. Add server-side pagination via `Pageable` (`?page=0&size=50&sort=updatedAt,desc`). Wrap responses in `Page<TestCaseResponse>` and update the frontend to consume `content`/`totalElements`.

2. **Frontend** — bind the filter form to `ActivatedRoute.queryParams`. On change, update query params (replaceState, not pushState, to avoid history pollution) and dispatch the load action with the params. Persistence comes for free; deep links to filtered views become shareable.

Effort: backend ~1 day for test cases + 1 day for runs/suites; frontend ~1 day.

### 4.4 (M) Webhooks (the missing v1.2)

The REQUIREMENTS.md spec is good — implement it as documented (`Webhook` entity, HMAC-SHA256 signature, `@Async` delivery, `webhook_deliveries` table). Two refinements:

- Add `RUN_STARTED` and `BUG_REPORT_CREATED` to the event list — the documented list is a touch CI-centric.
- Implement minimal retry (e.g., 3 tries with 1m/5m/30m backoff) rather than the documented "no retry." A single dropped notification often loses the value of the entire integration. Persist retry state on `webhook_deliveries`.

This is the lowest-cost integration improvement because it unlocks Slack, Teams, Discord, email-relays, and custom dashboards without writing tool-specific plugins.

### 4.5 (M) CSV + JSON import / export (the other missing v1.2)

Also follow the existing spec but ship JSON first — it's a 4-hour task because the existing `TestCaseResponse` already serialises cleanly and the export endpoint is a one-liner over the repository. CSV is more delicate (Excel quoting, semicolon separators in German locales). Use Apache Commons CSV; expose Excel-friendly UTF-8 BOM as a `?excel=true` query param.

Bonus: support **dry-run import** (`POST .../import?dryRun=true`) that returns the would-be result without persisting. This converts onboarding from "try it, see what breaks, undo, retry" to "preview, fix, commit," and is cheap to add.

### 4.6 (M) JUnit XML + Cucumber JSON ingestion

You already accept generic external-run submissions and Allure ZIPs. Add the two formats every CI in the JVM/Node world produces by default:

- `POST /api/external/projects/{projectKey}/test-runs/junit` — parse Surefire/Jest/PHPUnit JUnit XML, auto-create test cases by `classname.methodname` if missing, attach failure stack traces as comments on results.
- `POST /api/external/projects/{projectKey}/test-runs/cucumber` — parse Cucumber JSON output, map feature → suite, scenario → test case, steps → steps.

This makes the tool _viable as a CI aggregator_ without anyone writing a custom JSON payload. Use `org.junit.platform:junit-platform-reporting` for the XML schema if you want to be pedantic about it, but a simple JAXB binding is enough.

Effort: ~3 days each. Ship JUnit first — it covers 80% of real CI output.

### 4.7 (S) Global `Cmd-K` / `Ctrl-K` command palette

A single floating dialog (Angular Material `MatDialog`) bound to `keydown.meta.k` and `keydown.control.k`. It indexes — in memory, frontend-only first — every project, test case (key + title), test run (key + name), and bug report from the NgRx store. As the user types, fuzzy-match (use `fuse.js` or a tiny hand-rolled scorer; no need for an external service). Press Enter to navigate.

Server-side full-text search via a `GET /api/search?q=` endpoint backed by Postgres `tsvector` columns (with a generated `to_tsvector('english', title || ' ' || description)` index) can come later. The client-only version solves 90% of the daily search need and ships in a day.

### 4.8 (S) Explicit project role enforcement

Introduce `@RequireProjectRole(ProjectRole.TESTER)` annotation, an aspect that reads `projectId` from the path (`#projectId` SpEL) and the current user from `SecurityContext`, loads the `ProjectMember`, and throws `ForbiddenException` if their role rank is below the minimum. Apply to every write endpoint in `TestCaseController`, `TestSuiteController`, `TestRunController`, `CommentController`, `BugReportController`, `TestPlanController`, `TestCaseFolderController`.

Add an integration test per controller asserting:
- `VIEWER` → 403 on writes
- `TESTER` → 200 on test-case/run writes, 403 on project-member changes
- `ADMIN` → 200 on everything

Effort: ~2 days including tests. Closes a quiet correctness gap.

### 4.9 (M) Native issue tracker integration (replace free-text `defectLink`)

Keep `defectLink` for backward compat. Add a new `IssueTrackerConfig` per project:

```
type: NONE | GITHUB | GITLAB | JIRA | LINEAR
baseUrl: https://github.com/myorg/myrepo
auth: optional API token (encrypted at rest, project-admin only)
```

In the result UI:

- If `type=NONE`, render the existing free-text URL field.
- If configured, render a typeahead that searches issues by key/title in the tracker.
- On a failed test result, add a "Create issue" button that POSTs to the tracker's API with a templated body (test case key, run key, last actual result, screenshots as attachments where the tracker supports it).
- Show a tiny status pill next to each linked issue (`OPEN` / `CLOSED`) populated by a lightweight poll job every 5 minutes (only for issues linked to results in non-completed test plans, to bound the API calls).

This is the single most-requested feature for any tool in this niche. Doing it well (status pills, create button) is the difference between "I link a URL" and "I never leave the tool."

### 4.10 (M) Notifications service for watchers

Watchers (`EntityWatcher`) and audit entries already exist. Add:

1. A `NotificationDispatcher` that subscribes to audit-entry creation and fans out to:
   - **In-app notification bell** — new `Notification` table (`id`, `userId`, `entityType`, `entityId`, `summary`, `readAt`, `createdAt`). Frontend: top-nav badge with unread count, dropdown showing last 20.
   - **Email** (optional, off by default; requires SMTP config — keep air-gap default off).
2. Per-user preferences: per-event-type opt-out.

Trigger events from existing audit log inserts (no double-writes): when an `AuditEntry` is created on an entity, fan out to its watchers.

This finally cashes in the watcher infrastructure that is already shipped.

### 4.11 (S) Dashboard "what should I do right now" widget

The dashboard already shows project cards. Add a single home-page widget above them: **My queue**, listing in priority order:
1. Test plans I am assigned to with `targetDate < +7 days`
2. Test runs I started but didn't complete (`status=IN_PROGRESS && executor=me`)
3. Bug reports I created in `OPEN` state with no comment in 7 days
4. Test cases I authored that are still `DRAFT` after 14 days

Backend: one consolidated endpoint `GET /api/me/queue` returns ~20 items max. Frontend: a `MyQueueComponent` rendered as the first card on the dashboard, with each item a one-line link.

This makes the app a workspace instead of a database. Costs about a day end-to-end and changes how often power users open it.

### 4.12 (S) Test execution "session resume"

A tester opens a run, marks 12 of 30 cases, closes the laptop. When she comes back tomorrow, the run is `IN_PROGRESS` and the sidebar shows everything but does not auto-jump to case 13. Auto-jump to the first `PENDING` result on load (currently auto-selects index 0 regardless). Two lines in `ngOnInit`:

```ts
const firstPending = run.results.find(r => r.status === 'PENDING');
this.activeResultId = (firstPending ?? run.results[0]).id;
```

Pair with a small visual indicator in the sidebar ("12 / 30 done") and you have visibly resumed work.

### 4.13 (M) Server-side full-text search

Add a `GET /api/search?q=&types=testCase,testRun,project,bugReport&projectId=` endpoint. In Postgres, add `tsvector` columns on `test_cases`, `test_runs`, `bug_reports`, `projects` populated by a `GENERATED ALWAYS AS (to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(description,'')))` and indexed with GIN. `simple` (not `english`) keeps it usable for German + mixed-language content. The endpoint returns the top 20 results grouped by type.

Wire this behind the `Cmd-K` palette from 4.7 as a "Search server" fallback when the client-side fuzzy match returns < 5 results.

### 4.14 (L) Versioning of test cases (the v2 ask, done minimally)

Track changes immutably. When a test case is updated, INSERT a copy in `test_case_versions` (snapshot of fields + steps) before applying the update. Add `versionAt` and `versionNumber` columns. `TestResult` references the version executed (FK to `test_case_versions.id`). UI: a "History" tab on the test case detail showing past versions with a side-by-side diff (use `diff2html` or roll your own line-based diff for steps).

This is the only proposal that justifies the L sizing: it touches the data model, requires a backfill migration of existing test cases as version 1, and needs careful UX so it doesn't slow down normal editing. Skip it unless you have a regulatory / compliance driver.

---

## 5. Recommended Order of Work

If I had to slot these into a quarter, I would do them like this:

**Sprint 1 (quick wins, 2 weeks):**
- 4.1 Keyboard shortcuts for execution
- 4.2 Cache comments / bug reports per result
- 4.7 `Cmd-K` palette (client-side first)
- 4.11 "My queue" dashboard widget
- 4.12 Auto-jump to first pending result
- 3.11 Translate chart labels
- _Plus: refresh REQUIREMENTS.md_

**Sprint 2 (~3 weeks):**
- 4.3 Backend filters + pagination (test cases + runs)
- 4.8 Project role enforcement aspect + tests
- 4.5 CSV/JSON import-export
- 4.6 JUnit XML ingestion

**Sprint 3 (~3 weeks):**
- 4.4 Webhooks with retry
- 4.10 Notifications service (in-app first, email later)
- 4.6 Cucumber JSON ingestion
- 4.13 Server-side full-text search

**Sprint 4 / later:**
- 4.9 Native issue tracker integration (start with one — GitHub or GitLab — and templatise)
- 4.14 Test case versioning (only if you have a real compliance driver)
- 3.13 Streaming blob downloads (only if profile data shows heap pressure)

---

## 6. Things to NOT Build

A simple/fast tool dies by accretion. Three temptations I would actively resist:

- **Jira-style workflow customisation.** Three roles and five result statuses cover the 50-user, 5-concurrent target. Configurable state machines would require a permissions and validation layer that is out of proportion to the value.
- **In-app rich-text editor (TipTap, ProseMirror) for descriptions.** Markdown with a preview tab is enough and avoids a heavy dependency, XSS surface, and copy-paste-from-Word horror stories. Especially relevant given the air-gap requirement.
- **A second persistence engine for screenshots (S3, MinIO).** BYTEA in PostgreSQL is the right call for this size of deployment and survives `pg_dump` cleanly. Resist this until you have a project sized > 10 GB of screenshots.

---

## 7. Verification Notes for the Reader

References used in this review (verified by direct read):

- Backend entities: `backend/src/main/java/com/deanmanagement/testmanagement/project/internal/entity/*.java`
- Backend controllers: `.../project/internal/controller/*.java`
- Migrations: `backend/src/main/resources/db/migration/V1__…V33__add_comments_author_index.sql`
- Frontend routes: `frontend/src/app/app.routes.ts` and per-feature `*.routes.ts`
- Frontend hot path: `frontend/src/app/features/test-runs/test-run-detail/test-run-detail.component.ts`
- Frontend test-case list: `frontend/src/app/features/test-cases/test-case-list/test-case-list.component.ts`
- Documented roadmap: `REQUIREMENTS.md` §§ 11–13

Where this review says "missing," it means the controller, service, or component does not exist in the source tree as of commit reachable on `main`. Where it says "implemented," the file path is the proof.
