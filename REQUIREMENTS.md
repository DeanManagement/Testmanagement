# Testmanagement - Requirements

## 1. Overview

**Testmanagement** is a self-hosted, air-gapped-capable test management tool for small organisations (~50 users, ~5 concurrent). It provides a web-based interface for managing test cases, test suites, test executions, and related artifacts. Licensed under MIT, maintained by DeanManagement.

---

## 2. Technology Stack

| Layer        | Technology                                                     |
|--------------|----------------------------------------------------------------|
| Backend      | Java 25, Spring Boot 4.0.x, Spring Modulith 2.0.x, Maven       |
| Frontend     | Angular 21 (standalone, zoneless), Angular Material, NgRx 21, Chart.js |
| Database     | PostgreSQL 16, Flyway migrations (V1–V33)                      |
| Auth         | Local email/password + JWT (HS256)                             |
| Packaging    | Docker, docker-compose                                         |
| API Style    | REST (JSON)                                                    |
| i18n         | ngx-translate (EN, DE)                                         |

---

## 3. Domain Model & Features (v1 — Implemented)

### 3.1 Projects

- A **Project** is the top-level grouping for all test artifacts.
- Each project has a name, description, and key (short alphanumeric identifier, auto-generated).
- The key is stored as `project_key` in the database (`key` is reserved in H2).
- Projects track a `next_test_case_number` for auto-incrementing test case keys (e.g., `PROJ-1`, `PROJ-2`).
- Users are assigned to projects with specific roles via the project members relationship.

### 3.2 Test Cases

- A **Test Case** belongs to a project.
- Fields: `testCaseKey` (auto-generated), title, description, preconditions, priority (Low/Medium/High/Critical), status (Draft/Active/Deprecated).
- A test case contains an ordered list of **Test Steps**, each with an action, an expected result, an order index, optional test data, and an optional **reference image** (`StepImage`, stored as BYTEA, served with 365-day immutable cache headers).
- Test cases can be tagged with labels (string set) for filtering.
- Test cases can be organised into **Test Case Folders** (hierarchical tree, per project, see §3.12).

### 3.3 Test Suites

- A **Test Suite** is a named collection of test cases within a project.
- Flat structure (no nesting).
- A test case can belong to multiple suites (many-to-many).
- Suite reports include pass rate calculations based on latest test results.

### 3.4 Test Runs / Executions

- A **Test Run** executes a set of test cases (from a suite or ad-hoc selection).
- Each run tracks: name, auto-generated `key` (e.g., `PROJ-R-3`, see V24), environment info, executor (user), start/end time, overall status (Planned/In Progress/Completed/Aborted), and an optional `testPlanId` link.
- Runs can be cloned (copy structure with fresh pending results).
- Runs can be reopened after completion (with a mandatory reason; `completedBy` and `reopenReason` persisted, V17).
- Each test case in a run produces a **Test Result**: Passed / Failed / Blocked / Skipped / Pending, with optional comment and defect link.
- Each test step in a result produces a **Step Result**: status, actual result text, and optional screenshot.
- Completed runs can have an **Allure report** ZIP uploaded (see §3.13).

### 3.5 Screenshots

- Screenshots are uploaded as multipart form-data and stored as **BYTEA blobs in PostgreSQL** (not filesystem).
- Each screenshot belongs to a step result (one-to-one, cascade delete).
- Metadata: fileName, contentType, binary data.
- Max upload size: 10 MB per file.

### 3.6 Defect Linking

- Test results have a free-text `defectLink` field for linking to external issue trackers.
- No deep integration with a specific bug tracker — just a reference field.

### 3.7 Users & Roles

- Authentication is **local**: email/password with BCrypt hashing + JWT (HS256) tokens.
- A default admin account is seeded on first startup from environment variables.
- System admins can create, update, and delete user accounts.
- JWT tokens carry: userId (subject), email, systemAdmin flag. Expiration: 24 hours.
- Roles (per project): **Admin**, **Tester**, **Viewer**.
  - **Admin**: full CRUD on all project artifacts, manage project members.
  - **Tester**: create/edit test cases, execute test runs.
  - **Viewer**: read-only access.
- A global **System Admin** flag exists for managing users, API keys, and system settings.
- Users can be marked `force_password_change` (V31) to require a new password on next login.
- Role enforcement ([PRD-001](docs/prd/PRD-001-rbac-access-control.md), shipped): project roles are enforced server-side via the `@RequireProjectRole` annotation + `ProjectRoleAspect`, backed by `ProjectAccessService`. Every project-scoped endpoint confirms membership before any read and enforces the minimum role (`ADMIN > TESTER > VIEWER`) for writes; system admins bypass. Object-reference endpoints (screenshots, step images) enforce the same via a service-layer backstop. `ProjectService.create` now adds the creator as an `ADMIN` member, and a startup check logs any project lacking an `ADMIN` owner.

### 3.8 API Key Authentication

- System admins can create API keys for external/programmatic access.
- Key format: `tm_` prefix + 40-character hex string.
- Only the SHA-256 hash is stored; the raw key is shown once at creation.
- API keys authenticate via the `X-API-Key` header.
- Scoped to `/api/external/**` endpoints only.
- Keys can be revoked; `last_used_at` is tracked.

### 3.9 External API (CI/CD Integration)

- `POST /api/external/projects/{projectId}/test-runs` accepts a completed test run with results.
- Authenticated via API key (not JWT).
- Designed for Jenkins, GitHub Actions, or any CI tool to submit automated test results.

### 3.10 Reporting

- **Test run reports**: per-run breakdown of results by status (Passed/Failed/Blocked/Skipped/Pending) with doughnut chart (Chart.js).
- **Test suite reports**: pass rate calculations, test coverage metrics, doughnut chart.
- **Completion info endpoint**: returns counts of total/passed/failed/blocked/skipped/pending per run.
- **PDF export** (implemented in v1.1): `GET /api/projects/{id}/test-runs/{trId}/report/pdf` and `…/test-suites/{tsId}/report/pdf` return tabular PDF reports via `PdfReportService` (openhtmltopdf).

### 3.11 Dashboard

- Home dashboard lists the projects the authenticated user can access.
- Each project is shown as a card with quick stats.
- Project dashboard endpoint `GET /api/projects/{id}/dashboard` returns `testCasesByStatus`, `testCasesByPriority`, `recentTestRuns`, `passRateTrend`, and totals (implemented in v1.1, see `DashboardService`).

### 3.12 Test Case Folders

- Hierarchical, per-project folder tree for organising test cases (`test_case_folders` table, V27).
- Endpoints under `/api/projects/{id}/test-case-folders` support CRUD, reorder, and bulk move of test cases between folders.
- Test cases can sit at the project root (`rootOnly=true`) or inside any folder.

### 3.13 Allure Report Integration

- `AllureReport` entity (V23) stores a ZIP-uploaded Allure report associated with a test run.
- Endpoints under `/api/projects/{id}/test-runs/{runId}/allure-report` accept ZIP upload (both JWT and API-key auth paths exist).
- The report is served at `/allure-report/view/**`, with file-level access control gated by `projectMemberRepository.existsByUserIdAndProjectId`.

### 3.14 Bug Reports

- Built-in lightweight bug tracking (`bug_reports` table, V25). Each project has a per-project `bugReportsEnabled` toggle.
- A `BugReport` has: id, key (e.g., `PROJ-B-12`), title, description, status (`OPEN` / `IN_PROGRESS` / `RESOLVED` / `DUPLICATE` / `WONT_FIX`), reporter, optional assignee, optional links to a `TestResult` and `TestRun`.
- Status changes are auditable and require a reason.
- Endpoints: `/api/projects/{id}/bug-reports` for CRUD; `/api/my-bug-reports/assigned-to-me` for the assignee view.

### 3.15 Entity Watchers / Notifications (foundation)

- `entity_watchers` table (V30) stores `(userId, entityType, entityId)` subscriptions. Supported entity types (`WatchableEntityType`): `TEST_PLAN`, `TEST_RUN`, `BUG_REPORT`. (Test cases are not currently watchable.)
- Endpoints under `/api/watchers` allow watching/unwatching and listing "my watched items" across entity types.
- _Delivery_ shipped via [PRD-006](docs/prd/PRD-006-watcher-notifications.md): `AuditService.log` fans out to watchers (actor excluded, per-action opt-out, dedup) into a `notifications` table, surfaced by an in-app bell; optional email is off by default.

### 3.16 Activity / Audit Log

- `audit_entries` table (V18, indexed by `(project_id, created_at DESC)`; author index added V33).
- Services explicitly call `auditService.log(...)` after mutations — not driven by JPA listeners — so the action set is precise and stable.
- Endpoint `GET /api/projects/{id}/activity?page=0&size=20&entityId={optional}` returns a paginated `Page<AuditEntryResponse>` newest-first.

### 3.17 Comments

- Threaded comments on test cases and test results (`comments` table, V19, V33).
- Endpoints under `/api/projects/{id}/test-cases/{tcId}/comments` and `…/test-runs/{runId}/results/{resultId}/comments`.
- Authors can edit/delete their own comments; project admins and system admins can delete any.

### 3.18 Test Plans / Milestones

- `test_plans` table (V20, with assignee added in V30).
- Group test runs under a release/milestone; optional `targetDate`; status `OPEN`/`IN_PROGRESS`/`COMPLETED`/`CANCELLED`.
- Endpoint `GET /api/projects/{id}/test-plans/{tpId}/summary` returns aggregated stats across the runs in the plan.

### 3.19 Bulk Operations

- `POST /api/projects/{id}/test-cases/bulk-status` and `bulk-delete`; `POST /api/projects/{id}/test-suites/{tsId}/bulk-add` and `bulk-remove`.
- Limit: 100 IDs per request. All IDs must belong to the named project.

---

## 4. External Integrations

### 4.1 CI/CD API (Implemented)

- REST API at `/api/external/**` for programmatic test run submission.
- Authenticated via API keys (not user JWT).
- Any CI tool (Jenkins, GitHub Actions, GitLab CI) can POST completed test runs with results.
- No tool-specific plugins.

### 4.2 Air-Gapped Operation

- The application runs fully offline with no calls to external services at runtime.
- All dependencies (Docker images, npm packages, Maven artifacts) are resolved at build time only.
- No CDN-hosted fonts, scripts, or assets — everything is bundled.

---

## 5. Internationalization (i18n)

- The frontend uses `ngx-translate` with HTTP loader for runtime language switching.
- `@ngx-translate/http-loader` v17 uses a zero-arg constructor with DI (no factory needed), defaulting to `/assets/i18n/*.json`.
- Supported languages: **English (default)**, **German**.
- Translation keys are organized by feature: `nav.*`, `auth.*`, `project.*`, `testCase.*`, `testRun.*`, `testSuite.*`, `settings.*`, `dashboard.*`.
- The backend returns machine-readable error codes; human-readable messages are resolved on the frontend.

---

## 6. Non-Functional Requirements

| Requirement     | Target                                                        |
|-----------------|---------------------------------------------------------------|
| Users           | ~50 total, ~5 concurrent                                      |
| Deployment      | Self-hosted, Docker-based                                     |
| Network         | Must work air-gapped (no outbound internet at runtime)        |
| Authentication  | Local email/password + JWT                                    |
| Browser support | Latest Chrome, Firefox, Edge                                  |
| Data backup     | PostgreSQL standard tooling (pg_dump); not in-app in v1       |
| Dev profile     | H2 in-memory with `MODE=PostgreSQL`, security permit-all      |

---

## 7. Docker / Deployment Architecture

```
docker-compose.yml
├── testmanagement-backend   (Spring Boot, Java 21, port 8080)
├── testmanagement-frontend  (Angular, served via Nginx, port 80)
└── testmanagement-db        (PostgreSQL 16)
```

- **Backend** container: fat JAR running on Eclipse Temurin 21 JRE. Port 8089 for local dev, 8080 in Docker.
- **Frontend** container: Angular production build served by Nginx; Nginx reverse-proxies `/api/**` to the backend.
- **Database** container: PostgreSQL 16 with a named volume (`pgdata_tm`) for persistence.
- Environment variables configure: DB connection (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), JWT secret (`JWT_SECRET`), CORS origins (`CORS_ALLOWED_ORIGINS`), admin credentials (`ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_DISPLAY_NAME`).

---

## 8. Project Structure

```
Testmanagement/
├── backend/                  # Spring Boot Maven project
│   ├── pom.xml
│   ├── Dockerfile
│   ├── mvnw / mvnw.cmd
│   └── src/
│       ├── main/java/com/deanmanagement/testmanagement/
│       │   ├── shared/               # BaseEntity, exceptions, global config
│       │   │   ├── BaseEntity.java   # UUID id, createdAt, updatedAt
│       │   │   ├── config/           # CORS, JPA auditing, OpenAPI
│       │   │   └── exception/        # GlobalExceptionHandler, custom exceptions
│       │   ├── user/                 # User module
│       │   │   └── internal/
│       │   │       ├── config/       # JWT, security filters, admin seeder
│       │   │       ├── controller/   # AuthController, UserController
│       │   │       ├── dto/          # Login/User request/response records
│       │   │       ├── mapper/       # UserMapper (MapStruct)
│       │   │       ├── repository/   # UserRepository
│       │   │       └── services/     # AuthService, UserService
│       │   └── project/              # Project module
│       │       └── internal/
│       │           ├── config/       # API key security filter
│       │           ├── controller/   # All project-scoped controllers
│       │           ├── dto/          # All project-scoped DTOs
│       │           ├── entity/       # All domain entities + enums
│       │           ├── mapper/       # MapStruct mappers
│       │           ├── repository/   # Spring Data JPA repositories
│       │           └── service/      # Business logic services
│       └── main/resources/
│           ├── application.yml
│           ├── application-dev.yml
│           └── db/migration/         # Flyway SQL migrations (V1–V17)
├── frontend/                 # Angular 19 project
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── proxy.conf.json
│   └── src/app/
│       ├── core/                     # Guards, interceptors, auth service
│       ├── shared/                   # Shared models, components, pipes
│       ├── features/                 # Feature components
│       │   ├── landing/              # Public landing page
│       │   ├── login/                # Login form
│       │   ├── dashboard/            # Project list dashboard
│       │   ├── projects/             # Project CRUD, member management
│       │   ├── test-cases/           # Test case CRUD with step builder
│       │   ├── test-suites/          # Test suite CRUD + reports
│       │   ├── test-runs/            # Test execution, results, cloning
│       │   └── settings/             # User admin, API key management
│       └── store/                    # NgRx store
│           ├── auth/                 # Auth state (login, logout, current user)
│           ├── project/              # Project entity state
│           ├── test-case/            # Test case entity state
│           ├── test-suite/           # Test suite entity state
│           ├── test-run/             # Test run entity state
│           ├── user/                 # User admin entity state
│           └── api-key/              # API key entity state
├── docker-compose.yml
├── REQUIREMENTS.md
├── CLAUDE.md
├── README.md
└── LICENSE
```

---

## 9. API Overview (v1 — Implemented)

### Authentication & Users

| Method | Endpoint                    | Description                          |
|--------|-----------------------------|--------------------------------------|
| POST   | `/api/auth/login`           | Login with email/password, returns JWT |
| GET    | `/api/auth/me`              | Get current authenticated user       |
| GET    | `/api/users`                | List all users (system admin only)   |
| POST   | `/api/users`                | Create user (system admin only)      |
| PUT    | `/api/users/{id}`           | Update user (system admin only)      |
| DELETE | `/api/users/{id}`           | Delete user (system admin only)      |

### Projects

| Method | Endpoint                            | Description                |
|--------|-------------------------------------|----------------------------|
| GET    | `/api/projects`                     | List user's projects       |
| GET    | `/api/projects/{id}`                | Get project details        |
| GET    | `/api/projects/search?key={key}`    | Search by project key      |
| POST   | `/api/projects`                     | Create project             |
| PUT    | `/api/projects/{id}`                | Update project             |
| DELETE | `/api/projects/{id}`                | Delete project             |

### Project Members

| Method | Endpoint                                          | Description          |
|--------|---------------------------------------------------|----------------------|
| GET    | `/api/projects/{id}/members`                      | List members         |
| POST   | `/api/projects/{id}/members`                      | Add member with role |
| PUT    | `/api/projects/{id}/members/{memberId}`           | Update member role   |
| DELETE | `/api/projects/{id}/members/{memberId}`           | Remove member        |

### Test Cases

| Method | Endpoint                                          | Description          |
|--------|---------------------------------------------------|----------------------|
| GET    | `/api/projects/{id}/test-cases`                   | List test cases      |
| GET    | `/api/projects/{id}/test-cases/{tcId}`            | Get test case        |
| POST   | `/api/projects/{id}/test-cases`                   | Create test case     |
| PUT    | `/api/projects/{id}/test-cases/{tcId}`            | Update test case     |
| DELETE | `/api/projects/{id}/test-cases/{tcId}`            | Delete test case     |

### Test Suites

| Method | Endpoint                                          | Description          |
|--------|---------------------------------------------------|----------------------|
| GET    | `/api/projects/{id}/test-suites`                  | List test suites     |
| GET    | `/api/projects/{id}/test-suites/{tsId}`           | Get test suite       |
| GET    | `/api/projects/{id}/test-suites/{tsId}/report`    | Get suite report     |
| POST   | `/api/projects/{id}/test-suites`                  | Create test suite    |
| PUT    | `/api/projects/{id}/test-suites/{tsId}`           | Update test suite    |
| DELETE | `/api/projects/{id}/test-suites/{tsId}`           | Delete test suite    |

### Test Runs

| Method | Endpoint                                                              | Description                 |
|--------|-----------------------------------------------------------------------|-----------------------------|
| GET    | `/api/projects/{id}/test-runs`                                        | List test runs              |
| GET    | `/api/projects/{id}/test-runs/{trId}`                                 | Get test run                |
| GET    | `/api/projects/{id}/test-runs/{trId}/report`                          | Get run report              |
| GET    | `/api/projects/{id}/test-runs/{trId}/completion-info`                 | Get completion counts       |
| POST   | `/api/projects/{id}/test-runs`                                        | Create test run             |
| PUT    | `/api/projects/{id}/test-runs/{trId}`                                 | Update test run             |
| POST   | `/api/projects/{id}/test-runs/{trId}/clone`                           | Clone test run              |
| DELETE | `/api/projects/{id}/test-runs/{trId}`                                 | Delete test run             |
| POST   | `/api/projects/{id}/test-runs/{trId}/results`                         | Add test result             |
| PUT    | `/api/projects/{id}/test-runs/{trId}/results/{rId}`                   | Update test result          |
| PUT    | `/api/projects/{id}/test-runs/{trId}/results/{rId}/steps/{sId}`       | Update step result          |

### Screenshots

| Method | Endpoint                    | Description          |
|--------|-----------------------------|----------------------|
| POST   | `/api/screenshots`          | Upload screenshot    |
| GET    | `/api/screenshots/{id}`     | Download screenshot  |
| DELETE | `/api/screenshots/{id}`     | Delete screenshot    |

### API Keys

| Method | Endpoint                    | Description          |
|--------|-----------------------------|----------------------|
| GET    | `/api/api-keys`             | List API keys        |
| POST   | `/api/api-keys`             | Create API key       |
| DELETE | `/api/api-keys/{id}`        | Revoke API key       |

### External API (CI/CD)

| Method | Endpoint                                              | Description                        |
|--------|-------------------------------------------------------|------------------------------------|
| POST   | `/api/external/projects/{id}/test-runs`               | Submit completed test run (API key auth) |

All endpoints (except `/api/external/**` and `/api/auth/login`) require a valid JWT bearer token. External endpoints require an `X-API-Key` header. CORS is configured to allow the frontend origin.

---

## 10. Decisions Made

| Topic                | Decision                                                                 | Rationale |
|----------------------|--------------------------------------------------------------------------|-----------|
| Auth                 | Local email/password + JWT instead of OIDC/Keycloak                      | Simpler deployment, no external dependency; OIDC can be added later as optional |
| DB IDs               | UUID                                                                     | Air-gap/distributed friendly |
| DTO mapping          | MapStruct with `@Mapper(componentModel = "spring")`                      | Type-safe, compile-time, no reflection |
| Boilerplate          | Lombok (`@Getter`, `@Setter`, `@NoArgsConstructor`)                      | Reduces entity verbosity |
| DTOs                 | Java records (`CreateXxxRequest`, `UpdateXxxRequest`, `XxxResponse`)     | Immutable, concise |
| State management     | NgRx with EntityAdapter + functional API                                 | Scalable, normalized state |
| Charts               | Chart.js (doughnut charts)                                               | Simpler than D3.js, sufficient for reporting |
| i18n                 | ngx-translate with HTTP loader                                           | Runtime language switching |
| Screenshot storage   | BYTEA in PostgreSQL                                                      | No filesystem dependency, simplifies backup/restore |
| Dev DB               | H2 in PostgreSQL mode                                                    | No external DB needed for dev |
| Column `key`         | Mapped to `project_key`                                                  | `key` is reserved in H2 |
| Test case keys       | Auto-generated `PROJECT-N` format                                        | Human-readable, sequential per project |
| Frontend components  | Angular 19 standalone (no NgModules)                                     | Modern Angular pattern |
| Import/Export        | Not in v1                                                                | Avoid encouraging off-tool authoring |
| PDF export           | Deferred to v1.1                                                         | Core reporting works; PDF is a polish feature |

---

## 11. v1.1 — Polish the Core (Done)

Target: complete the v1 promise and improve daily usability. **All four items below shipped.**

### 11.1 PDF Report Export — ✓ Done

**Goal**: Allow users to download test run and test suite reports as PDF files.

**Backend**:
- Add a dependency on OpenPDF (LGPL, fork of iText 2) or alternatively use the `openhtmltopdf` library for HTML-to-PDF rendering.
- New endpoints:
  - `GET /api/projects/{id}/test-runs/{trId}/report/pdf` — returns `application/pdf`.
  - `GET /api/projects/{id}/test-suites/{tsId}/report/pdf` — returns `application/pdf`.
- The PDF should contain:
  - Report header: project name, report title, generation timestamp.
  - Summary table: total test cases, counts by status, pass rate percentage.
  - Results table: each test case with its status, executor, comment, defect link.
  - For test run reports: environment info, start/end time, duration.
  - For test suite reports: suite description, coverage metrics.
- No charts in PDF (keep it simple — tabular data only).

**Frontend**:
- Add a "Download PDF" button on `TestRunReportComponent` and `TestSuiteReportComponent`.
- Trigger a file download via the browser's blob/download mechanism.
- Show a loading spinner while the PDF is being generated.

### 11.2 Project Dashboard with Analytics — ✓ Done

**Goal**: Replace the simple project card list with an analytics-rich project dashboard.

**Backend**:
- New endpoint: `GET /api/projects/{id}/dashboard` returning:
  - `testCasesByStatus`: `{ DRAFT: n, ACTIVE: n, DEPRECATED: n }`
  - `testCasesByPriority`: `{ LOW: n, MEDIUM: n, HIGH: n, CRITICAL: n }`
  - `recentTestRuns`: last 5 test runs with name, status, pass rate, date.
  - `passRateTrend`: array of `{ runName, date, passRate }` for the last 10 runs.
  - `totalTestCases`, `totalTestSuites`, `totalTestRuns` counts.

**Frontend**:
- Redesign `DashboardComponent` with two levels:
  1. **Home dashboard** (existing): project cards with quick stats (total test cases, latest run status).
  2. **Project dashboard** (new): accessed from a project card or the project detail page.
- Project dashboard widgets:
  - Test cases by status (doughnut chart).
  - Test cases by priority (horizontal bar chart).
  - Pass rate trend over last 10 runs (line chart).
  - Recent test runs table (last 5, with status badges).
  - Summary cards: total test cases, total suites, total runs, overall pass rate.
- All charts rendered with Chart.js (already a dependency).

### 11.3 Bulk Operations — ✓ Done

**Backend**:
- New endpoints:
  - `POST /api/projects/{id}/test-cases/bulk-status` — body: `{ testCaseIds: UUID[], status: TestCaseStatus }`.
  - `POST /api/projects/{id}/test-cases/bulk-delete` — body: `{ testCaseIds: UUID[] }`.
  - `POST /api/projects/{id}/test-suites/{tsId}/bulk-add` — body: `{ testCaseIds: UUID[] }`.
  - `POST /api/projects/{id}/test-suites/{tsId}/bulk-remove` — body: `{ testCaseIds: UUID[] }`.
- Validation: all IDs must belong to the specified project. Return 400 with details for any invalid IDs.
- Limit: max 100 items per bulk operation.

**Frontend**:
- Add checkbox column to `TestCaseListComponent`.
- Floating action bar appears when 1+ items are selected, showing: "Change Status", "Add to Suite", "Delete" buttons.
- Confirmation dialog for destructive operations (delete).
- Select all / deselect all toggle.

### 11.4 Activity / Audit Log — ✓ Done

**Backend**:
- New entity: `AuditEntry` — `id`, `projectId`, `userId`, `action` (enum `AuditAction`: CREATED, UPDATED, DELETED, STATUS_CHANGED, COMPLETED, REOPENED, CLONED, MOVED), `entityType` (`AuditEntityType`), `entityId`, `entityName`, `details` (JSON string for old/new values), `createdAt`.
- New table: `audit_entries` with index on `(project_id, created_at DESC)`.
- New endpoint: `GET /api/projects/{id}/activity?page=0&size=20` — paginated, sorted newest first.
- Audit entries are written by services (not via JPA listeners — keep it explicit).
- Retention: no automatic cleanup in v1.1; can be addressed later.

**Frontend**:
- New `ActivityFeedComponent` displayed as a tab on the project detail page.
- Each entry shows: timestamp, user display name, action description (e.g., "Alice updated test case PROJ-42"), link to the affected entity.
- Infinite scroll or "Load more" pagination.
- i18n: action descriptions are translated using parameterized translation keys.

---

## 12. v1.2 — Collaboration & Integration (Mostly Done)

Target: enable team collaboration and external system integration.
**Shipped:** Comments (§12.1), Test Plans (§12.2). **Still pending:** Webhooks (§12.3) and CSV/JSON import-export (§12.4) — these have been retargeted into v1.3 below.

### 12.1 Comments — ✓ Done

**Goal**: Lightweight discussion threads on test cases and test results.

**Backend**:
- New entity: `Comment` — `id`, `content` (text, max 2000 chars), `authorId` (FK to users), `entityType` (enum: TEST_CASE, TEST_RESULT), `entityId` (UUID), `createdAt`, `updatedAt`.
- New table: `comments` with index on `(entity_type, entity_id, created_at)`.
- New endpoints:
  - `GET /api/projects/{id}/test-cases/{tcId}/comments` — list comments for a test case.
  - `POST /api/projects/{id}/test-cases/{tcId}/comments` — add comment.
  - `PUT /api/projects/{id}/comments/{commentId}` — edit own comment.
  - `DELETE /api/projects/{id}/comments/{commentId}` — delete own comment (admins can delete any).
  - Same pattern for test result comments under `/api/projects/{id}/test-runs/{trId}/results/{rId}/comments`.
- Comments are included in the audit log.

**Frontend**:
- `CommentListComponent` (shared, reusable) — displays chronological comment thread.
- `CommentFormComponent` — text area with submit button.
- Displayed on test case detail page and test result detail/modal.
- Comments show: author name, timestamp, content. Edit/delete actions on own comments.
- NgRx: new `comment` store slice with entity adapter.

### 12.2 Test Plans / Milestones — ✓ Done

**Backend**:
- New entity: `TestPlan` — `id`, `name`, `description`, `projectId` (FK), `status` (OPEN, IN_PROGRESS, COMPLETED, CANCELLED), `targetDate` (optional), `createdAt`, `updatedAt`.
- Relationship: a test run can optionally belong to a test plan (`test_plan_id` FK on `test_runs`, nullable).
- New endpoints:
  - CRUD: `/api/projects/{id}/test-plans` — standard create, read, update, delete.
  - `GET /api/projects/{id}/test-plans/{tpId}/summary` — returns aggregated stats across all runs in the plan: total results, pass rate, per-run breakdown, completion percentage.
- Test runs API: add optional `testPlanId` to `CreateTestRunRequest` and `UpdateTestRunRequest`.

**Frontend**:
- New `test-plans` feature module under `features/`.
- `TestPlanListComponent` — table of test plans with status, target date, progress bar.
- `TestPlanDetailComponent` — shows all test runs in the plan, aggregated statistics, overall progress.
- `TestPlanFormComponent` — create/edit with name, description, target date.
- Project detail page gains a "Test Plans" tab.
- Test run creation dialog gains an optional "Test Plan" dropdown.

### 12.3 Webhook Notifications — ✗ Not yet implemented (carried to v1.3)

**Goal**: Fire HTTP callbacks on key events so external systems (Slack, Teams, email relays) can react.

**Backend**:
- New entity: `Webhook` — `id`, `projectId` (FK), `url` (HTTPS URL), `secret` (for HMAC signature), `events` (set of enum: RUN_COMPLETED, RUN_FAILED, TEST_FAILED, PLAN_COMPLETED), `active` (boolean), `createdAt`.
- New table: `webhooks` with index on `(project_id)`.
- New endpoints:
  - CRUD: `/api/projects/{id}/webhooks` — manage webhooks (admin only).
  - `POST /api/projects/{id}/webhooks/{whId}/test` — send a test payload.
- Webhook delivery:
  - POST to the configured URL with JSON payload: `{ event, projectId, projectName, timestamp, data }`.
  - `data` varies by event type (e.g., for RUN_COMPLETED: run name, pass rate, result counts).
  - HMAC-SHA256 signature in `X-TM-Signature` header using the webhook secret.
  - Fire-and-forget with async execution (`@Async`). No retry in v1.2.
  - Log delivery attempts in `webhook_deliveries` table (url, status code, timestamp) for debugging.
- Air-gapped note: webhooks are opt-in. In air-gapped deployments, simply don't configure any — no outbound calls are made unless webhooks are explicitly set up.

**Frontend**:
- Webhook management in project settings (visible to project admins).
- `WebhookListComponent` — table of configured webhooks with active toggle.
- `WebhookFormComponent` — URL, event checkboxes, secret field.
- "Test" button sends a test payload and shows the response status.

### 12.4 Import / Export — ✗ Not yet implemented (carried to v1.3)

**Goal**: Allow migration from spreadsheets and backup/transfer of test case data.

**Backend**:
- New endpoints:
  - `POST /api/projects/{id}/test-cases/import` — accepts CSV file (multipart upload).
  - `GET /api/projects/{id}/test-cases/export?format=csv` — returns CSV download.
  - `GET /api/projects/{id}/test-cases/export?format=json` — returns JSON download.
- CSV format (import):
  - Required columns: `title`.
  - Optional columns: `description`, `preconditions`, `priority` (LOW/MEDIUM/HIGH/CRITICAL), `status` (DRAFT/ACTIVE/DEPRECATED), `labels` (semicolon-separated), `steps` (pipe-delimited `action|expected` pairs, separated by `;;`).
  - First row is header. Encoding: UTF-8.
  - Import response: `{ imported: n, skipped: n, errors: [{ row: n, message: string }] }`.
- CSV export includes all fields including steps (same format as import for round-trip compatibility).
- JSON export: array of full test case objects (same as API response format).
- Limit: max 500 test cases per import.

**Frontend**:
- "Import" button on `TestCaseListComponent` opens a dialog with file upload and format instructions.
- "Export" dropdown (CSV / JSON) on `TestCaseListComponent`.
- Import result dialog showing success/error summary.

---

## 12.5 Implemented Beyond the Original v1.2 Plan

Several features were added on top of the documented v1.2 scope. They are described above in §3.12–§3.19. For completeness:

- **Test case folders** (§3.12) — hierarchical organisation.
- **Allure report upload** (§3.13) — ZIP per test run, both JWT and API-key paths.
- **Bug reports** (§3.14) — built-in lightweight tracker with status workflow.
- **Entity watchers** (§3.15) — subscription rows ready to drive notifications later.
- **Step reference images** — separate from execution screenshots; immutable, cached.
- **Test run keys** (V24) — human-readable keys like `PROJ-R-3` for CI integration.
- **Force password change** flag (V31) — admin-controlled prompt on next login.

---

## 12.6 v1.3 — Speed & Integrations (Next)

Focus per the May 2026 review (`REVIEW_AND_PROPOSALS.md`): make daily use faster and close the integration gap.

**Shipped in the May 2026 sprint:**

- **Keyboard shortcuts for test run execution** — `j`/`k` navigate results, `p`/`f`/`b`/`s` set result status, `Shift+P` cascades Pass to all steps, `c` focuses the comment field, `?` shows a cheatsheet. Inputs/textareas/open dialogs disable the listener.
- **Resume to first PENDING result** — when re-opening an `IN_PROGRESS` run, the active selection lands on the first unfinished case instead of the first case overall.
- **Per-result comment & bug-report cache** — `CommentResponse` now exposes `entityType`/`entityId`; the comment store keeps every result's comments and the run-detail view filters by `entityId`. Switching between results within a run no longer re-fetches.
- **Cmd-K / Ctrl-K command palette** — fuzzy search over loaded projects, test cases, test runs, and bug reports. Triggered from anywhere inside the authenticated shell.
- **"My queue" dashboard widget** — single endpoint `GET /api/me/queue` aggregates due test plans, in-progress runs, stale OPEN bug reports, and long-stale DRAFT test cases I authored.
- **i18n** — Chart.js status labels and "My queue" copy now translated EN/DE.

**Still pending — now specified as PRDs (see [`docs/prd/`](docs/prd/README.md)):**

The June 2026 review broke the remaining work into individual PRDs with designs, edge cases, and acceptance criteria. Priority order:

| # | Item | Priority | PRD |
|---|------|----------|-----|
| 1 | ✅ **Project role enforcement & access control** — RBAC aspect + membership checks (shipped). | **P0** | [PRD-001](docs/prd/PRD-001-rbac-access-control.md) |
| 2 | ✅ **Backend filters + pagination** — `?q=`, `?status=`, `?priority=`, `?label=`, `Pageable` on test case/run/suite list endpoints; URL-bound filters + paginator on the frontend (shipped). | P1 | [PRD-002](docs/prd/PRD-002-backend-filtering-pagination.md) |
| 3 | ✅ **§12.3 Webhooks** — outbound HTTP callbacks (HMAC signed, async, bounded retry, SSRF-guarded) with admin UI + delivery log (shipped). | P1 | [PRD-003](docs/prd/PRD-003-webhooks.md) |
| 4 | ✅ **§12.4 Import / Export** — CSV + JSON round-trip for test cases, with dry-run import + per-row error report (shipped). | P1 | [PRD-004](docs/prd/PRD-004-import-export.md) |
| 5 | ✅ **JUnit XML / Cucumber JSON ingestion** — extra CI submission formats with auto-created `ci-imported` cases (shipped). | P2 | [PRD-005](docs/prd/PRD-005-ci-result-ingestion.md) |

**Usability & tech-debt backlog:** ✅ shipped — bulk result actions in a run, URL-persisted list filters, density toggle, chart-label i18n (relabel on language switch), responsive execution screen, and streaming blob downloads are all delivered (list virtual-scroll de-scoped, made moot by pagination). See [PRD-008](docs/prd/PRD-008-usability-and-polish.md).

---

## 13. v2.0 — Advanced Features (Future)

These are valuable but lower-urgency. Each now has its own full PRD — see [PRD-009](docs/prd/PRD-009-v2-future-backlog.md) (the v2.0 index) for the set and the suggested order. Notifications (PRD-006) and global search (PRD-007) already shipped in v1.3.

| Area | PRD | Size |
|---|---|---|
| ~~Issue-tracker integration (§13.9)~~ — ✅ shipped | [PRD-010](docs/prd/PRD-010-issue-tracker-integration.md) | M |
| Test case versioning (§13.1) | [PRD-011](docs/prd/PRD-011-test-case-versioning.md) | L |
| ~~OIDC / Keycloak (§13.5)~~ — ✅ shipped | [PRD-012](docs/prd/PRD-012-oidc-sso.md) | M |
| ~~Dark mode / theming (§13.7)~~ — ✅ shipped | [PRD-013](docs/prd/PRD-013-dark-mode-theming.md) | S |
| Traceability matrix (§13.2) | [PRD-014](docs/prd/PRD-014-traceability-matrix.md) | M |
| Parameterized cases (§13.3) | [PRD-015](docs/prd/PRD-015-parameterized-test-cases.md) | M |
| Flaky detection (§13.4) | [PRD-016](docs/prd/PRD-016-flaky-test-detection.md) | M |

### 13.1 Test Case Versioning / History — [PRD-011](docs/prd/PRD-011-test-case-versioning.md)

- Track changes to test cases over time as immutable snapshots.
- When a test case is edited, the previous version is preserved.
- Test results reference the specific version of the test case that was executed.
- Version history viewer on the test case detail page showing diffs.
- Enables regulatory compliance and traceability.

### 13.2 Traceability Matrix — [PRD-014](docs/prd/PRD-014-traceability-matrix.md)

- Link test cases to external requirement identifiers (free-text IDs or URLs).
- New entity: `Requirement` — id, externalId, title, projectId.
- Many-to-many relationship between requirements and test cases.
- Matrix view: requirements on rows, test cases on columns, cells show latest result status.
- Coverage report: which requirements have test coverage, which don't.

### 13.3 Parameterized / Data-Driven Test Cases — [PRD-015](docs/prd/PRD-015-parameterized-test-cases.md)

- Define test cases with variable placeholders in steps (e.g., `{username}`, `{password}`).
- Create parameter sets (rows of values) for each test case.
- When added to a test run, the test case is expanded into N results (one per parameter set).
- Useful for boundary value testing and combinatorial testing.

### 13.4 Flaky Test Detection — [PRD-016](docs/prd/PRD-016-flaky-test-detection.md)

- Automatically flag test cases that alternate between pass and fail across recent runs.
- Flaky score: percentage of status changes in last N executions.
- Dashboard widget showing top flaky tests per project.
- Optional label auto-applied to flaky test cases.

### 13.5 SSO via OpenID Connect — ✅ shipped ([PRD-012](docs/prd/PRD-012-oidc-sso.md))

Shipped 2026-07-31, wider than originally specified: any number of OIDC providers, stored in the
database and managed by system admins in the UI without a restart, running alongside local password
login rather than instead of it. Client secrets encrypted (`APP_ENCRYPTION_KEY`). Account linking to
an existing local user requires both a per-provider trust flag and a verified email from the IdP;
unknown users are provisioned with no project access. Local password login can be disabled, with
system admins retaining it as a break-glass. Project roles stay local.

Original scope:

- Add Spring Security OIDC alongside existing local auth.
- Configuration toggle: `app.auth.mode=local|oidc`.
- In OIDC mode: user profiles synced from token claims on first login.
- In local mode: existing email/password + JWT (unchanged).
- Allows enterprises with existing Keycloak to integrate without changing their auth infrastructure.

### 13.6 Notifications — ✅ shipped ([PRD-006](docs/prd/PRD-006-watcher-notifications.md))

- Fan-out off audit events to watchers of TEST_PLAN/TEST_RUN/BUG_REPORT (actor excluded, dedup), stored in a `notifications` table.
- In-app notification bell with unread count, dropdown, and mark-(all-)read.
- Per-user, per-action notification preferences (in-app/email).
- Optional email delivery (SMTP), off by default for air-gap.

### 13.7 Dark Mode / Theming — ✅ shipped ([PRD-013](docs/prd/PRD-013-dark-mode-theming.md))

- Light/dark/system toggle in the toolbar; `system` follows the OS and keeps tracking it.
- Preference stored in `localStorage`, applied before first paint; private browsing falls back to
  `system` instead of failing.
- Dark palette defined as overrides of the existing `--tm-*` tokens plus semantic tint tokens, so
  components re-theme without per-component dark rules.
- Charts re-render on theme change with theme-derived axis, legend and tooltip colors; PDF/print
  output stays light.

### 13.8 Global Search — ✅ shipped ([PRD-007](docs/prd/PRD-007-server-side-search.md))

- Membership-scoped search across test cases, test runs, bug reports, and projects.
- `GET /api/search?q=&types=&projectId=&limit=` returns grouped, ranked results; Postgres `tsvector` + GIN (`ts_rank`) with a portable `LIKE` fallback for non-Postgres/H2.
- The Cmd-K / Ctrl-K command palette now tops up its instant local fuzzy match with debounced server results when local hits are sparse.

### 13.9 Native Issue-Tracker Integration — ✅ shipped ([PRD-010](docs/prd/PRD-010-issue-tracker-integration.md))

Backend landed 2026-07-31 for GitLab and Forgejo (which also covers Gitea and Codeberg):
per-project config with an AES-GCM encrypted token (never
returned by the API), issue search, link an existing issue or file a templated one from a failed
result, cached OPEN/CLOSED state with a bounded poller that stays silent unless a tracker is
configured. Requires `ISSUE_TRACKER_ENCRYPTION_KEY`. Admin config screen per project, and a linked-
issues section on the execution screen with typeahead linking, one-click issue creation from a
failure, and an OPEN/CLOSED pill.

Original scope:

- Optional per-project connection to GitHub/GitLab/Jira/Linear (start single-provider); encrypted API token, project-admin only.
- Search issues, link to results, and create a templated issue from a failure; live OPEN/CLOSED status pill via a bounded poll.
- Keeps the free-text `TestResult.defectLink` as a fallback; opt-in and air-gap safe.

---

## 14. Database Schema Summary

**Current schema: migrations V1–V38** (V34–V35 webhooks + deliveries/events, V36–V37 notifications + preferences, V38 Postgres search vectors under `db/specific/postgresql`). Added since the v1 core: `webhooks`, `webhook_events`, `webhook_deliveries`, `notifications`, `notification_preferences`. v2.0 PRDs (PRD-010..016) introduce further tables when picked up.

### v1 core tables (V1–V17)

| Table                    | Key Columns                                      |
|--------------------------|--------------------------------------------------|
| `users`                  | id, email, display_name, password_hash, system_admin |
| `projects`               | id, name, project_key, description, next_test_case_number, bug_reports_enabled |
| `project_members`        | id, user_id (FK), project_id (FK), role          |
| `test_cases`             | id, test_case_key, title, description, preconditions, priority, status, project_id (FK), folder_id (FK, nullable) |
| `test_case_labels`       | test_case_id (FK), label                         |
| `test_steps`             | id, action, expected_result, test_data, order_index, test_case_id (FK), image_id (FK, nullable) |
| `test_suites`            | id, name, description, project_id (FK)           |
| `test_suite_test_cases`  | test_suite_id (FK), test_case_id (FK)            |
| `test_runs`              | id, key, name, environment, start_time, end_time, status, project_id (FK), executor_id (FK), completed_by_id (FK), reopen_reason, test_plan_id (FK, nullable) |
| `test_results`           | id, status, comment, defect_link, test_run_id (FK), test_case_id (FK) |
| `step_results`           | id, status, actual_result, test_result_id (FK), test_step_id (FK, nullable since V28) |
| `screenshots`            | id, file_name, content_type, data (BYTEA), step_result_id (FK) |
| `api_keys`               | id, name, key_hash, key_prefix, revoked, last_used_at |

### v1.1 / v1.2 / additional features (V18–V33)

| Table                    | Version  | Purpose                                          |
|--------------------------|----------|--------------------------------------------------|
| `audit_entries`          | V18, V33 | Activity / audit log; indexed by `(project_id, created_at DESC)` and author |
| `comments`               | V19      | Discussion threads on test cases and test results |
| `test_plans`             | V20, V30 | Milestone grouping for test runs; assignee added in V30 |
| `step_images`            | V21      | Reference images attached to test step definitions (separate from execution screenshots) |
| `audit_entries` (extras) | V22      | `created_by` / `updated_by` author tracking      |
| `allure_reports`         | V23      | Allure ZIP archives per test run                  |
| `test_runs.key`          | V24      | Human-readable test-run keys (`PROJ-R-N`)         |
| `bug_reports`            | V25      | Built-in lightweight bug tracker                  |
| `test_case_folders`      | V27      | Hierarchical organisation of test cases           |
| `entity_watchers`        | V30      | Per-user watch subscriptions for follow-on notifications |
| `users.force_password_change` | V31 | Admin-driven forced password rotation on next login |
| _various unique indexes_ | V29, V32 | Performance / consistency indexes                 |

### Planned, not yet created

| Table                    | Target  | Purpose                                          |
|--------------------------|---------|--------------------------------------------------|
| `webhooks`               | v1.3    | Outbound webhook configuration                   |
| `webhook_deliveries`     | v1.3    | Delivery attempt log for debugging               |
| `notifications`          | v2.0    | In-app notification queue per user               |
