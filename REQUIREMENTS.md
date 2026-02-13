# Testmanagement - Requirements

## 1. Overview

**Testmanagement** is a self-hosted, air-gapped-capable test management tool for small organisations (~50 users, ~5 concurrent). It provides a web-based interface for managing test cases, test suites, test executions, and related artifacts. Licensed under MIT, maintained by DeanManagement.

---

## 2. Technology Stack

| Layer        | Technology                                   |
|--------------|----------------------------------------------|
| Backend      | Java 21, Spring Boot 3.5.x, Maven           |
| Frontend     | Angular 19, Angular Material, NgRx 19, Chart.js |
| Database     | PostgreSQL 16, Flyway migrations             |
| Auth         | Local email/password + JWT (HS256)           |
| Packaging    | Docker, docker-compose                       |
| API Style    | REST (JSON)                                  |
| i18n         | ngx-translate (EN, DE)                       |

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
- A test case contains an ordered list of **Test Steps**, each with an action, an expected result, and an order index.
- Test cases can be tagged with labels (string set) for filtering.

### 3.3 Test Suites

- A **Test Suite** is a named collection of test cases within a project.
- Flat structure (no nesting).
- A test case can belong to multiple suites (many-to-many).
- Suite reports include pass rate calculations based on latest test results.

### 3.4 Test Runs / Executions

- A **Test Run** executes a set of test cases (from a suite or ad-hoc selection).
- Each run tracks: name, environment info, executor (user), start/end time, overall status (Planned/In Progress/Completed/Aborted).
- Runs can be cloned (copy structure with fresh pending results).
- Runs can be reopened after completion (with a mandatory reason).
- Each test case in a run produces a **Test Result**: Passed / Failed / Blocked / Skipped / Pending, with optional comment and defect link.
- Each test step in a result produces a **Step Result**: status, actual result text, and optional screenshot.

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
- No PDF export yet (see v1.1).

### 3.11 Dashboard

- The dashboard displays a list of projects the authenticated user has access to.
- Each project is shown as a card.
- Create project button available.
- No analytics/trend charts yet (see v1.1).

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

## 11. v1.1 — Polish the Core

Target: complete the v1 promise and improve daily usability.

### 11.1 PDF Report Export

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

### 11.2 Project Dashboard with Analytics

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

### 11.3 Bulk Operations

**Goal**: Enable users to perform actions on multiple test cases at once.

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

### 11.4 Activity / Audit Log

**Goal**: Track who changed what, when. Provide a per-project activity feed.

**Backend**:
- New entity: `AuditEntry` — `id`, `projectId`, `userId`, `action` (enum: CREATED, UPDATED, DELETED, STATUS_CHANGED, MEMBER_ADDED, MEMBER_REMOVED, RUN_COMPLETED, RUN_REOPENED), `entityType` (enum: TEST_CASE, TEST_SUITE, TEST_RUN, TEST_RESULT, PROJECT_MEMBER), `entityId`, `entityName`, `details` (JSON string for old/new values), `createdAt`.
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

## 12. v1.2 — Collaboration & Integration

Target: enable team collaboration and external system integration.

### 12.1 Comments

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

### 12.2 Test Plans / Milestones

**Goal**: Group test runs under a release or milestone to track overall progress.

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

### 12.3 Webhook Notifications

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

### 12.4 Import / Export

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

## 13. v2.0 — Advanced Features (Future)

These are identified as valuable but not yet designed in detail.

### 13.1 Test Case Versioning / History

- Track changes to test cases over time as immutable snapshots.
- When a test case is edited, the previous version is preserved.
- Test results reference the specific version of the test case that was executed.
- Version history viewer on the test case detail page showing diffs.
- Enables regulatory compliance and traceability.

### 13.2 Traceability Matrix

- Link test cases to external requirement identifiers (free-text IDs or URLs).
- New entity: `Requirement` — id, externalId, title, projectId.
- Many-to-many relationship between requirements and test cases.
- Matrix view: requirements on rows, test cases on columns, cells show latest result status.
- Coverage report: which requirements have test coverage, which don't.

### 13.3 Parameterized / Data-Driven Test Cases

- Define test cases with variable placeholders in steps (e.g., `{username}`, `{password}`).
- Create parameter sets (rows of values) for each test case.
- When added to a test run, the test case is expanded into N results (one per parameter set).
- Useful for boundary value testing and combinatorial testing.

### 13.4 Flaky Test Detection

- Automatically flag test cases that alternate between pass and fail across recent runs.
- Flaky score: percentage of status changes in last N executions.
- Dashboard widget showing top flaky tests per project.
- Optional label auto-applied to flaky test cases.

### 13.5 OIDC / Keycloak Support (Optional)

- Add Spring Security OIDC alongside existing local auth.
- Configuration toggle: `app.auth.mode=local|oidc`.
- In OIDC mode: user profiles synced from token claims on first login.
- In local mode: existing email/password + JWT (unchanged).
- Allows enterprises with existing Keycloak to integrate without changing their auth infrastructure.

### 13.6 Notifications

- In-app notification bell with unread count.
- Notification triggers: assigned as executor, test run completed, test failure on a watched test case.
- Notification preferences per user (which events to receive).
- Optional email delivery (SMTP configuration).

### 13.7 Dark Mode / Theming

- Angular Material theme switching (prebuilt light and dark themes).
- User preference stored in localStorage.
- Toggle in the navigation header.

### 13.8 Global Search

- Search across all projects the user has access to.
- Indexed entities: projects (by name/key), test cases (by key/title/labels), test suites (by name), test runs (by name).
- Backend: `GET /api/search?q={query}` returning grouped results by entity type.
- Frontend: search bar in the top navigation with typeahead dropdown.

---

## 14. Database Schema Summary

**11 tables (v1, migrations V1–V17):**

| Table                    | Key Columns                                      |
|--------------------------|--------------------------------------------------|
| `users`                  | id, email, display_name, password_hash, system_admin |
| `projects`               | id, name, project_key, description, next_test_case_number |
| `project_members`        | id, user_id (FK), project_id (FK), role          |
| `test_cases`             | id, test_case_key, title, description, preconditions, priority, status, project_id (FK) |
| `test_case_labels`       | test_case_id (FK), label                         |
| `test_steps`             | id, action, expected_result, order_index, test_case_id (FK) |
| `test_suites`            | id, name, description, project_id (FK)           |
| `test_suite_test_cases`  | test_suite_id (FK), test_case_id (FK)            |
| `test_runs`              | id, name, environment, start_time, end_time, status, project_id (FK), executor_id (FK), completed_by_id (FK), reopen_reason |
| `test_results`           | id, status, comment, defect_link, test_run_id (FK), test_case_id (FK) |
| `step_results`           | id, status, actual_result, test_result_id (FK), test_step_id (FK) |
| `screenshots`            | id, file_name, content_type, data (BYTEA), step_result_id (FK) |
| `api_keys`               | id, name, key_hash, key_prefix, revoked, last_used_at |

**New tables planned for v1.1–v1.2:**

| Table                    | Version | Purpose                                          |
|--------------------------|---------|--------------------------------------------------|
| `audit_entries`          | v1.1    | Activity / audit log                             |
| `comments`               | v1.2    | Discussion threads on test cases and results     |
| `test_plans`             | v1.2    | Milestone grouping for test runs                 |
| `webhooks`               | v1.2    | Outbound webhook configuration                   |
| `webhook_deliveries`     | v1.2    | Delivery attempt log for debugging               |
