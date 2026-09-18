# PRD-034 — Exploratory Testing Sessions

| | |
|---|---|
| **Status** | ✅ Implemented 2026-09-18 — see §8; UI not yet click-tested in a browser |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-17 |
| **Priority** | P3 — value grows with teams that test manually more than they script |
| **Target** | v2.4 |
| **Related** | PRD-001 (RBAC), PRD-017 (authenticated media), PRD-006 (watchers), PRD-003 (webhooks), REQUIREMENTS.md §3.14 (bug reports), §3.18 (test plans) |

---

## 1. Summary

Everything the tool records today is scripted: a `TestRun` is a list of `TestResult`s, each tied to a `TestCase` with steps. Exploratory testing — "spend 60 minutes attacking checkout with odd currencies" — has nowhere to live, so its findings end up in chat, a personal notes file, or a bug report with no context about what was being explored when it was found.

This PRD adds an **exploratory session**: a time-boxed charter, a timestamped log of plain-text notes with optional screenshots, a debrief summary, and bug reports filed straight from a note. Sessions belong to a project and optionally a test plan, so plan progress can show that exploration happened.

## 2. Goals & Non-Goals

**Goals**
- Create a session with a charter, time box, optional test plan, optional environment and a tester.
- Start / complete / abort, with the elapsed time visible against the time box.
- Append timestamped notes while testing, each tagged `NOTE | BUG | QUESTION | IDEA`, each with at most one screenshot.
- File a bug report from a note, pre-filled like "file bug from failed result" is today.
- A debrief summary on completion; sessions listed on the plan detail page.

**Non-Goals**
- Rich-text notes or an in-app editor (PRD-009 §4) — notes are plain text, as are `TestResult.comment` and `BugReport.description`.
- Screen recording, session video, or browser extensions.
- Pause/resume accounting. Elapsed time is `now - startedAt`; interruptions are the tester's problem, not a state machine's.
- Converting notes into test cases automatically (a later "create case from note" button is cheap if asked for).
- SBTM-style metrics (on-charter %, setup/test/bug split). Driver-dependent; add only on request.
- MCP tools for sessions in the first cut (PRD-025 surface stays as is).

## 3. Proposed Design

### 3.1 Why not a `TestRun` with a type flag
Reusing `TestRun` would give key, executor, plan link, environment and status for free, but every consumer of runs assumes results: `TestPlanService` summaries (`TestPlanSummaryResponse.totalRuns/completedRuns/passRate`), `DashboardService`, `FlakyTestService`, `PdfReportService`, `TestRunSpecifications`, webhooks on run completion, and the MCP run tools. A result-less run would skew pass rates and need a type filter in all of them. A separate entity is less code overall. It does reuse `TestRunStatus` (`PLANNED, IN_PROGRESS, COMPLETED, ABORTED`) — the lifecycle is identical.

### 3.2 Data model (next free V-number, V54+ at time of writing)
```
exploratory_sessions
  id UUID PK, project_id UUID NOT NULL FK, test_plan_id UUID NULL FK (ON DELETE SET NULL),
  session_key VARCHAR(30) NOT NULL UNIQUE,         -- PROJ-Session-12
  charter TEXT NOT NULL, environment VARCHAR(255),
  timebox_minutes INT NOT NULL,                    -- CHECK (timebox_minutes BETWEEN 5 AND 480)
  status VARCHAR(20) NOT NULL,                     -- TestRunStatus values
  tester_id UUID NULL FK users, started_at TIMESTAMP, ended_at TIMESTAMP,
  summary TEXT,
  created_at, updated_at, created_by, updated_by

exploratory_session_notes
  id UUID PK, session_id UUID NOT NULL FK ON DELETE CASCADE,
  note_type VARCHAR(20) NOT NULL,                  -- NOTE | BUG | QUESTION | IDEA
  body TEXT NOT NULL, occurred_at TIMESTAMP NOT NULL,
  created_at, updated_at, created_by, updated_by

exploratory_session_note_images
  id UUID PK, note_id UUID NOT NULL UNIQUE FK ON DELETE CASCADE,
  file_name VARCHAR(255) NOT NULL, content_type VARCHAR(100) NOT NULL, data BYTEA NOT NULL,
  created_at, updated_at, created_by, updated_by

projects.next_session_number INT NOT NULL DEFAULT 1
bug_reports.exploratory_session_id UUID NULL FK ON DELETE SET NULL
```
All `BYTEA`/`TEXT`/`UUID` types already work on both H2 (PostgreSQL mode) and Postgres — see `screenshots` in V12. V12 predates `created_by`/`updated_by` (added in V22); the new tables include them from the start, as CLAUDE.md requires — so nothing goes under `db/specific/`.

**Screenshot storage.** The existing `Screenshot` entity is 1:1 with `StepResult` and `step_result_id` is `NOT NULL`, so a session image cannot live in that table without loosening a constraint every existing query relies on. The note image table is a sibling with the identical shape, and reuses the *mechanism*: bytes in Postgres (no S3, PRD-009 §4), `ImageMediaTypes.requireAllowed` on upload, the 10 MB `spring.servlet.multipart` limit, and the PRD-017 response headers (`Cache-Control: private`, ETag/304, `nosniff`, `Content-Security-Policy: sandbox`) from `ScreenshotController.download`. Extract that header-building block into a small shared helper used by both controllers rather than copying it — it is security-relevant and must not drift.

### 3.3 Backend
- `ExploratorySession`, `ExploratorySessionNote`, `ExploratorySessionNoteImage` entities extending `BaseEntity`, Lombok, MapStruct mapper, records `CreateExploratorySessionRequest`, `UpdateExploratorySessionRequest`, `ExploratorySessionResponse`, `CreateSessionNoteRequest`, `SessionNoteResponse`.
- `ExploratorySessionService`: key from `ProjectSequenceService` (new `nextSessionNumber`), plan resolved with `testPlanRepository.findByIdAndProjectId` (the PRD-027 §3.5 scoping rule — never `findById` on a child id), tester must be a project member (`TestRunService.requireProjectMember` pattern).
- Transitions: `PLANNED → IN_PROGRESS` sets `startedAt`; `IN_PROGRESS → COMPLETED | ABORTED` sets `endedAt` and accepts `summary`. Notes may only be added while `IN_PROGRESS` or within `COMPLETED` for 24 h (late debrief additions); `occurred_at` defaults to now and may be back-dated but not before `startedAt`.
- `BugReportService.create`: `CreateBugReportRequest` gains `exploratorySessionId`, resolved within the project. `environment` defaults from the session.
- Audit: new `AuditEntityType.EXPLORATORY_SESSION`; created / status change / deleted. Notes are not individually audited (they would drown the activity feed).
- `TestPlanService.getSummary`: add `sessions` (count, completed, total minutes) as a separate block — **not** folded into run counts or `passRate`.

### 3.4 Endpoints (RBAC via PRD-001 `@RequireProjectRole`)
| Method & path | Role |
|---|---|
| `GET /api/projects/{projectId}/exploratory-sessions?status=&testPlanId=&testerId=` (paged, PRD-002 style) | VIEWER |
| `GET /api/projects/{projectId}/exploratory-sessions/{id}` (includes notes) | VIEWER |
| `POST /api/projects/{projectId}/exploratory-sessions` | TESTER |
| `PUT /api/projects/{projectId}/exploratory-sessions/{id}` | TESTER |
| `POST .../{id}/start`, `.../{id}/complete`, `.../{id}/abort` | TESTER |
| `DELETE /api/projects/{projectId}/exploratory-sessions/{id}` | TESTER |
| `POST .../{id}/notes`, `PUT .../{id}/notes/{noteId}`, `DELETE .../{id}/notes/{noteId}` | TESTER (edit/delete own notes; ADMIN any) |
| `POST .../{id}/notes/{noteId}/image` (multipart), `GET` / `DELETE` same path | TESTER / VIEWER / TESTER |

Image routes are project-scoped paths, so `@RequireProjectRole` covers them — unlike `/api/screenshots/{id}`, which has to look the project up in `ScreenshotService`.

### 3.5 Frontend
- New lazy route `projects/:id/exploratory-sessions` in `projects.routes.ts`, feature folder `features/exploratory-sessions/` with `session-list`, `session-form`, `session-detail`.
- NgRx slice `store/exploratory-session` (`createActionGroup` + `createEntityAdapter`), matching `store/test-run`.
- `session-detail` while in progress: charter at the top, elapsed/time-box progress bar (turns amber past 100 %, never stops), a single-line note input with a type toggle and paste-to-attach screenshot, and the note log newest-first. Keyboard: `Enter` saves, `Alt+1..4` switches type — follows the keyboard-driven execution pattern in `test-run-detail` and its `keyboard-shortcuts-dialog`.
- `BUG` notes show "File bug", opening `bug-report-form` pre-filled (title from the first line, description from the note, session and environment linked).
- `test-plan-detail`: "Exploratory sessions" card listing sessions with status and duration.
- My Test Runs page (`features/my-test-runs`) lists my planned / in-progress sessions below runs.
- i18n keys in `en.json` / `de.json`.

## 4. Edge Cases
- **Tab closed mid-session**: nothing is lost — every note is saved on `Enter`; elapsed time is derived from `startedAt`, so reopening shows the right clock.
- **Session never completed**: stays `IN_PROGRESS`; the list shows "running for 3 days". No auto-abort job — fewer moving parts, and the tester can abort manually.
- **Plan deleted**: `test_plan_id` set null, session survives.
- **Tester removed from project**: session keeps `tester_id` for history; tester can no longer add notes (membership check).
- **Image on a note in another project**: the project-scoped path plus `findByIdAndSessionId` lookup returns 404, same as PRD-027's child-id rule.
- **Non-image upload / SVG**: rejected by `ImageMediaTypes`; legacy-type rows are served as `application/octet-stream` attachment exactly like `ScreenshotController`.
- **Bug report deleted**: note unaffected; session's bug list shrinks.

## 5. Testing
- Service: key sequencing, state transitions (including invalid ones), note timestamp bounds, plan/tester scoping to project.
- Controller slice tests per endpoint for VIEWER/TESTER/non-member; note edit restricted to author or ADMIN.
- Image: allowlist enforced, response headers identical to `ScreenshotController` (assert via the shared helper's test), cross-project image id → 404.
- Bug report from session: session id scoped, environment defaulted.
- Plan summary: sessions reported separately; `passRate` unchanged by sessions.
- Frontend: elapsed-time display from `startedAt`, note submit on Enter, "File bug" pre-fill.

## 6. Effort & Risk
- **Effort:** ~6–8 days (backend 3, frontend 3–4, plan summary + bug linking 1).
- **Risk:** Low-medium. New, isolated entity; the one sharp edge is image serving, mitigated by sharing the PRD-017 header code with `ScreenshotController` instead of duplicating it.

## 7. Acceptance Criteria
- [x] Sessions with charter, time box, optional plan/environment/tester can be created, started, completed with a summary, or aborted.
- [x] Timestamped, typed plain-text notes can be added during a session, each with an optional screenshot.
- [x] Session images are served with the same authorization and headers as step screenshots.
- [x] A bug report can be filed from a note and links back to the session.
- [x] Plan detail lists sessions; plan pass rate is unaffected by them.
- [x] All endpoints enforce project roles; cross-project ids return 404.
- [x] Backend and frontend tests pass.

## 8. As Built (2026-09-18)

Built as specified, with these differences:

- **The header code moved into `ImageResponses` first, in its own commit.** `StepImageController`
  had a third identical copy of the `ScreenshotController` block, so all three controllers now
  share one helper. The existing `MediaCacheHeadersApiTest`/`MediaContentTypeApiTest` pass
  unchanged, and `ExploratorySessionApiTest` asserts the same headers on a session image.
- **The environment comes from the PRD-032 catalogue, not free text.** This PRD predates it.
  Sessions store only `environment_id`. A merge carries sessions along (`repointSessions`), and an
  environment used by a session can't be deleted. Names are resolved like runs and bugs.
- **Deleting a session unlinks its bug reports in the service,** not only through
  `ON DELETE SET NULL`. A bug report already loaded in the same transaction would otherwise still
  point at the deleted session and fail the flush.
- **Notes don't map their image.** Images sit in their own table, and the log asks only which notes
  have one (`findNoteIdsWithImage`), so reading a log never loads image bytes. Uploading to a note
  that already has an image replaces it (at most one per note).
- **No NgRx slice.** Session state is page-local behind `ExploratorySessionApiService`, like the
  webhook and environment pages. Nothing outside the session pages reads it.
- **"My test runs" uses a new `GET /api/exploratory-sessions/assigned-to-me`** (planned and
  running sessions where I'm the tester), because that page spans projects.
- **Alt+1..4 reads the physical key code**, since on macOS Alt changes `event.key`.
- **Who may add notes:** any project tester, not only the assigned tester (pair exploration). Edits
  are limited to the author or an admin, as specified.

Tests: `ExploratorySessionServiceTest` (19: keys, transitions, note time bounds and the 24 h late
window, author/admin rule, image allowlist, cross-project 404s, bug linking and environment
default, plan summary kept apart from runs, environment merge/delete),
`ExploratorySessionApiTest` (7: role guards, the time box bound, multipart upload and PRD-017
headers, cross-project image 404, my sessions), and `session-time.spec.ts`.
**928 backend tests, 28 frontend spec files (141 tests).**

**Still open:** clicking through the session flow in a browser.
