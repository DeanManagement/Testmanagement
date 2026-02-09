# Testmanagement - Requirements

## 1. Overview

**Testmanagement** is a self-hosted, air-gapped-capable test management tool for small organisations (~50 users, ~5 concurrent). It provides a web-based interface for managing test cases, test suites, test executions, and related artifacts.

---

## 2. Technology Stack

| Layer        | Technology                                   |
|--------------|----------------------------------------------|
| Backend      | Java 21, Spring Boot, Maven                  |
| Frontend     | Angular 19, Angular Material, NgRx, Chart.js |
| Database     | PostgreSQL                                   |
| Auth         | OIDC via Keycloak                            |
| Packaging    | Docker, docker-compose                       |
| API Style    | REST (JSON)                                  |

---

## 3. Domain Model & Features

### 3.1 Projects

- A **Project** is the top-level grouping for all test artifacts.
- Each project has a name, description, and key (short identifier).
- Users are assigned to projects with specific roles.

### 3.2 Test Cases

- A **Test Case** belongs to a project.
- Fields: title, description, preconditions, priority (Low/Medium/High/Critical), status (Draft/Active/Deprecated).
- A test case contains an ordered list of **Test Steps**, each with an action and an expected result.
- Test cases can be tagged with labels for filtering.

### 3.3 Test Suites

- A **Test Suite** is a named collection of test cases within a project.
- Suites can be nested (tree structure) or flat — **flat for the initial version**.
- A test case can belong to multiple suites.

### 3.4 Test Runs / Executions

- A **Test Run** executes a test suite (or an ad-hoc selection of test cases).
- Each run tracks: name, environment info, executor, start/end time, overall status.
- Each test case in a run produces a **Test Result**: Passed / Failed / Blocked / Skipped, with optional comments and attachments.

### 3.5 Defect Linking

- Failed test results can be linked to external defect/issue identifiers (free-text URL or ID).
- No deep integration with a specific bug tracker in v1 — just a reference field.

### 3.6 Users & Roles

- Authentication is handled externally via **OIDC (Keycloak)**. The application does not manage passwords.
- User profiles are synced/created on first login from the OIDC token.
- Roles (per project): **Admin**, **Tester**, **Viewer**.
  - **Admin**: full CRUD on all project artifacts, manage project members.
  - **Tester**: create/edit test cases, execute test runs.
  - **Viewer**: read-only access.
- A global **System Admin** role exists for managing projects and system settings.

### 3.7 Reporting & Dashboards

- **Project dashboard**: summary of test cases by status, recent test runs, pass/fail trends.
- **Test run report**: per-run breakdown of results.
- Charts and visualisations rendered in-app using **Chart.js** (e.g., pass/fail pie charts, trend lines, coverage bar charts).
- Reports are **exportable to PDF** directly from the application.

---

## 4. External Integrations

### 4.1 Jenkins (future-ready)

- The architecture should support triggering test runs from CI/CD pipelines and reporting results back.
- For v1: provide a REST API that Jenkins (or any CI tool) can call to create test runs and post results programmatically.
- No Jenkins-specific plugin in v1.

### 4.2 Air-Gapped Operation

- The application must run fully offline with no calls to external services at runtime.
- All dependencies (Docker images, npm packages, Maven artifacts) must be resolvable at build time only.
- No CDN-hosted fonts, scripts, or assets — everything is bundled.

---

## 5. Internationalization (i18n)

- The frontend uses Angular's i18n or `ngx-translate` for translations.
- Supported languages in v1: **English (default)**, **German**.
- The backend returns machine-readable error codes; human-readable messages are resolved on the frontend.

---

## 6. Non-Functional Requirements

| Requirement     | Target                                                    |
|-----------------|-----------------------------------------------------------|
| Users           | ~50 total, ~5 concurrent                                  |
| Deployment      | Self-hosted, Docker-based                                 |
| Network         | Must work air-gapped (no outbound internet at runtime)    |
| Authentication  | OIDC (Keycloak)                                           |
| Browser support | Latest Chrome, Firefox, Edge                              |
| Data backup     | PostgreSQL standard tooling (pg_dump); not in-app in v1   |

---

## 7. Docker / Deployment Architecture

```
docker-compose.yml
├── testmanagement-backend   (Spring Boot, Java 21, port 8080)
├── testmanagement-frontend  (Angular, served via Nginx, port 80)
└── testmanagement-db        (PostgreSQL 16)
```

- **Backend** container: fat JAR running on Eclipse Temurin 21 JRE.
- **Frontend** container: Angular production build served by Nginx; Nginx also reverse-proxies `/api/**` to the backend.
- **Database** container: PostgreSQL 16 with a named volume for persistence.
- Keycloak is **external** — not included in this compose file (user already has an instance).
- Environment variables configure DB connection, Keycloak realm/client, and other settings.

---

## 8. Project Structure

```
Testmanagement/
├── backend/                  # Spring Boot Maven project
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/deanmanagement/testmanagement/
│       │   │   ├── config/          # Security, OIDC, CORS, etc.
│       │   │   ├── controller/      # REST controllers
│       │   │   ├── dto/             # Request/Response DTOs
│       │   │   ├── entity/          # JPA entities
│       │   │   ├── repository/      # Spring Data JPA repositories
│       │   │   ├── service/         # Business logic
│       │   │   └── exception/       # Custom exceptions & error handling
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/    # Flyway migrations
│       └── test/
├── frontend/                 # Angular 19 project
│   ├── Dockerfile
│   ├── nginx.conf
│   └── src/
│       ├── app/
│       │   ├── core/                # Auth, guards, interceptors
│       │   ├── shared/              # Shared components, pipes, directives
│       │   ├── features/            # Feature modules (projects, test-cases, etc.)
│       │   └── store/               # NgRx store (actions, reducers, effects, selectors)
│       ├── assets/
│       │   └── i18n/                # en.json, de.json
│       └── environments/
├── docker-compose.yml
├── REQUIREMENTS.md
├── CLAUDE.md
├── README.md
└── LICENSE
```

---

## 9. API Overview (v1)

| Method | Endpoint                                         | Description                   |
|--------|--------------------------------------------------|-------------------------------|
| CRUD   | `/api/projects`                                  | Manage projects               |
| CRUD   | `/api/projects/{id}/test-cases`                  | Manage test cases             |
| CRUD   | `/api/projects/{id}/test-suites`                 | Manage test suites            |
| CRUD   | `/api/projects/{id}/test-runs`                   | Manage test runs              |
| PUT    | `/api/projects/{id}/test-runs/{runId}/results`   | Submit test results           |
| GET    | `/api/projects/{id}/dashboard`                   | Dashboard summary             |
| GET    | `/api/users/me`                                  | Current user profile          |
| CRUD   | `/api/projects/{id}/members`                     | Manage project membership     |

All endpoints require a valid OIDC bearer token. CORS is configured to allow the frontend origin.

---

## 10. Decisions Made

| Topic                | Decision                                                                 |
|----------------------|--------------------------------------------------------------------------|
| Attachment storage   | Local filesystem                                                         |
| Reports              | In-app via D3.js, exportable to PDF                                      |
| Import/Export        | Not in v1 — intentionally omitted to avoid encouraging off-tool authoring |

## 11. Open Questions / Decisions for Later

- Notification system (in-app, email)
- Test case versioning / history
- Jenkins plugin or webhook-based integration
