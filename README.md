# Testmanagement

A self-hosted test management tool for simple organisations. Manage test cases, execute test runs, track results, and integrate with CI/CD pipelines — all without sending data to external services.

**License:** MIT

## Features

- **Projects** — organise test artifacts under projects with short keys (e.g. `PROJ`), with per-project roles (Admin / Tester / Viewer)
- **Test Cases** — steps, expected results, priorities, labels, folders, full version history, and parameter sets that expand one case into several data-driven runs
- **Test Suites** — group test cases into suites for organised execution
- **Test Runs** — execute across environments, track pass/fail per step, with keyboard-driven execution
- **Test Plans** — group runs under a milestone and track release progress
- **Requirements** — link requirements to the cases that prove them and see a traceability matrix and coverage
- **Bug Reports** — built-in bug tracking, pre-filled from a failed result
- **Flaky Test Detection** — scores cases by how often consecutive runs disagree, not by failure count
- **Screenshots** — attach screenshots to individual step results
- **Allure Reports** — upload Allure HTML report ZIPs to test runs and view them in the browser
- **Import / Export** — CSV and JSON test case import with a dry run, CSV/JSON export, PDF run and suite reports
- **External API** — accept completed test runs from CI/CD pipelines via API key authentication (JUnit XML, Cucumber JSON, or native JSON)
- **Webhooks** — signed outbound callbacks on run and bug events
- **Issue Tracker** — link or file GitLab / Forgejo issues straight from a failed result
- **Notifications** — watch plans, runs and bug reports; in-app always, email optional
- **SSO** — optional multi-provider OpenID Connect, configured at runtime in the admin UI
- **Dark mode** — light, dark, or follow the operating system
- **i18n** — English and German language support with runtime switching

Full walkthrough: **[docs/USER_MANUAL.md](docs/USER_MANUAL.md)**.

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        Browser                               │
│                  Angular 21 + Material                       │
└──────────────────┬───────────────────────────────────────────┘
                   │ HTTP (published on 8012)
┌──────────────────▼───────────────────────────────────────────┐
│         Spring Boot 4.0 — single app container :8089         │
│    ┌─────────────────────────────────────────────────┐       │
│    │  /**               The Angular app, from the    │       │
│    │                    jar, with an index.html      │       │
│    │                    fallback for client routes   │       │
│    │  /api/**           Local JWT, or OIDC SSO       │       │
│    │  /api/external/**  API Key (X-API-Key header)   │       │
│    └─────────────────────────────────────────────────┘       │
└──────────────────┬───────────────────────────────────────────┘
                   │ JDBC
┌──────────────────▼───────────────────────────────────────────┐
│              PostgreSQL 16 (db container)                     │
└──────────────────────────────────────────────────────────────┘

The Angular build is baked into the jar and served by Spring, so there is one
image and one process. nginx is gone; its gzip, security headers and SPA
fallback now live in the backend.

Auth: local email/password + JWT (HS256). Multi-provider OpenID Connect SSO is
optional and configured at runtime by an admin (see docs/prd/PRD-012) — any OIDC
provider works, and no identity provider is needed to run the app.
```

## Quick Start

### Prerequisites

- Docker and Docker Compose

### Production (Docker Compose)

```bash
# 1. Configure secrets (compose refuses to start without them)
cp .env.example .env
#    - DB_PASSWORD: any strong value
#    - JWT_SECRET:  openssl rand -base64 48
#    - ADMIN_PASSWORD: optional — leave empty to get a generated admin
#      password printed once in the backend log at first start

# 2. Start all services
docker compose up --build
```

The application is available at `http://localhost:8012`. Log in as
`admin@localhost.ch` with your `ADMIN_PASSWORD` (or the generated one from
`docker compose logs testmanagement`); you'll be asked to change it.

### Development

```bash
# 1. Database — the dev profile expects a real PostgreSQL 16
docker compose up -d testmanagement-db

# 2. Backend + frontend together, with prefixed logs (Ctrl-C stops both)
./scripts/dev.sh
```

`./scripts/dev.sh backend` or `./scripts/dev.sh frontend` runs just one of them.

The application is available at `http://localhost:4200`, the API on `:8089`.
Log in as `admin@localhost.ch` / `admin` (dev-profile default; `ADMIN_PASSWORD` overrides).

## External Test Run API

External tools (CI/CD pipelines, test automation frameworks) can submit completed test runs via the REST API using API key authentication.

### Flow Overview

```
┌─────────────────┐         ┌──────────────────────────┐
│   CI Pipeline   │         │     Testmanagement UI    │
│  (Jenkins, etc) │         │                          │
└────────┬────────┘         └────────────┬─────────────┘
         │                               │
         │                               │ 1. Navigate to Settings > API Keys
         │                               │ 2. Click "Create API Key"
         │                               │ 3. Copy the generated key
         │                               │    (shown only once!)
         │                               │
         │  ┌────────────────────────────────────────────────────────┐
         │  │  Raw key: tm_a1b2c3d4e5f6...  (43 chars)             │
         │  │  Store this securely in your CI secrets!              │
         │  └────────────────────────────────────────────────────────┘
         │
         │ 4. POST /api/external/projects/{projectRef}/test-runs
         │    Header: X-API-Key: tm_a1b2c3d4e5f6...
         │    Body: { name, environment, results[] }
         │
         ▼
┌────────────────────┐
│  Testmanagement    │
│  Backend           │──── Validates API key (SHA-256 hash lookup)
│                    │──── Creates test run with COMPLETED status
│                    │──── Creates test results + step results
└────────────────────┘
         │
         ▼
   Test run appears in the project's
   test run list with all results
```

### Step 1: Create an API Key

Navigate to **Settings > API Keys** in the sidebar and click **Create API Key**.

```
┌───────────────────────────────────────────────────────────────┐
│  Settings > API Keys                        [Create API Key] │
├──────────┬──────────┬──────────┬───────────┬────────┬────────┤
│ Name     │ Key      │ Created  │ Last Used │ Status │        │
├──────────┼──────────┼──────────┼───────────┼────────┼────────┤
│ Jenkins  │ tm_a1b2..│ Feb 9    │ Never     │ Active │ Revoke │
│ GitHub   │ tm_f8e7..│ Feb 8    │ Feb 9     │ Active │ Revoke │
└──────────┴──────────┴──────────┴───────────┴────────┴────────┘
```

The raw key is displayed **only once** after creation. Store it securely (e.g. as a CI secret).

A key is scoped to one project and may only be used against that project's URLs; using it elsewhere
returns `403`.

### Referring to projects and test runs

`{projectRef}` accepts either the project **key** (`TES`) or its UUID. `{testRunRef}` likewise
accepts the run **key** (`TES-Run-1`, returned as `key` when the run is created) or its UUID.

### Step 2: Submit a Test Run

```bash
curl -X POST \
  http://localhost:8089/api/external/projects/{projectRef}/test-runs \
  -H "Content-Type: application/json" \
  -H "X-API-Key: tm_a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2" \
  -d '{
    "name": "CI Build #142 - Integration Tests",
    "environment": "staging",
    "results": [
      {
        "testCaseKey": "TES-1",
        "status": "PASSED",
        "comment": "All assertions passed"
      },
      {
        "testCaseKey": "TES-2",
        "status": "FAILED",
        "comment": "Assertion failed on line 42",
        "defectLink": "https://issues.example.com/BUG-789",
        "stepResults": [
          {
            "stepIndex": 1,
            "status": "PASSED",
            "actualResult": "Login page displayed"
          },
          {
            "stepIndex": 2,
            "status": "FAILED",
            "actualResult": "Got 500 error instead of dashboard"
          }
        ]
      }
    ]
  }'
```

### Request Format

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Name of the test run (e.g. build identifier) |
| `environment` | string | No | Target environment (e.g. `staging`, `prod`) |
| `results` | array | Yes | Non-empty list of test results |

Each **result**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `testCaseKey` | string | Yes | Key of an existing test case in the project (e.g. `TES-1`) |
| `status` | enum | Yes | `PASSED`, `FAILED`, `BLOCKED`, `SKIPPED`, `PENDING` |
| `comment` | string | No | Free-text comment |
| `defectLink` | string | No | Link to an external issue/defect |
| `stepResults` | array | No | Per-step results (auto-created if omitted) |

Each **stepResult**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `stepIndex` | integer | Yes | 1-based position of the step within the test case |
| `status` | enum | Yes | `PASSED`, `FAILED`, `BLOCKED`, `SKIPPED`, `PENDING` |
| `actualResult` | string | No | What actually happened |

If `stepResults` is omitted, step results are **auto-created** for every step of the test case, all set to the result's status.

### Response

The endpoint returns a `201 Created` with the full test run object (same format as `GET /api/projects/{id}/test-runs/{runId}`).

### Authentication Errors

| Status | Meaning |
|--------|---------|
| `401` | Missing, invalid, or revoked API key |
| `403` | The key is scoped to a different project than the one named in the URL |
| `404` | Project, test run, test case, or test step not found |
| `400` | Validation error (empty name, empty results, etc.) |

### Revoking a Key

Click the **Revoke** button next to any key in the Settings page. Revoked keys immediately stop working. The key remains visible in the list with a "Revoked" badge for audit purposes.

## Allure Report Integration

Attach [Allure](https://allurereport.org/) HTML reports to test runs. Reports can be uploaded from the UI or via the external API, and viewed directly in the browser.

### Uploading from CI/CD

After generating an Allure report, zip the `allure-report` directory and upload it:

```bash
# Generate the report (example with allure CLI)
allure generate allure-results -o allure-report

# Zip the report directory
zip -r allure-report.zip allure-report/

# Upload to a test run (use the "key" from the creation response, e.g. TES-Run-1)
curl -X POST \
  http://localhost:8089/api/external/projects/{projectRef}/test-runs/{testRunRef}/allure-report \
  -H "X-API-Key: tm_a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2" \
  -F "file=@allure-report.zip"
```

The ZIP must contain an `index.html` file. A common root directory (e.g. `allure-report/`) is automatically detected and stripped when serving files.

### Uploading from the UI

On the test run detail page, click **Upload Allure Report** and select a `.zip` file. Once uploaded, the button changes to **Allure Report** which opens the report in a new browser tab.

### Viewing

The backend serves individual files from the stored ZIP with correct MIME types, so all relative paths (CSS, JS, JSON, images, fonts) work as expected. The report opens in a new tab via a token query parameter for authentication.

## API Overview

### Standard Endpoints (OIDC-authenticated)

| Method | Endpoint | Description |
|--------|----------|-------------|
| CRUD | `/api/projects` | Manage projects |
| CRUD | `/api/projects/{id}/test-cases` | Manage test cases |
| CRUD | `/api/projects/{id}/test-suites` | Manage test suites |
| CRUD | `/api/projects/{id}/test-runs` | Manage test runs |
| PUT | `/api/projects/{id}/test-runs/{runId}/results/{resultId}` | Update a test result |
| PUT | `/api/.../results/{resultId}/steps/{stepId}` | Update a step result |
| POST/GET/DELETE | `/api/projects/{id}/test-runs/{runId}/allure-report` | Upload, view, or delete Allure reports |
| GET/POST/DELETE | `/api/api-keys` | Manage API keys |

### External Endpoints (API-key-authenticated)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/external/projects/{projectRef}/test-runs` | Submit a completed test run |
| POST | `/api/external/projects/{projectRef}/test-runs/junit` | Import a JUnit XML report |
| POST | `/api/external/projects/{projectRef}/test-runs/cucumber` | Import a Cucumber JSON report |
| POST | `/api/external/projects/{projectRef}/test-runs/{testRunRef}/allure-report` | Upload an Allure report ZIP |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 25, Spring Boot 4.0, Maven |
| Frontend | Angular 21 (standalone, zoneless), Angular Material, NgRx, Chart.js |
| Database | PostgreSQL 16, Flyway migrations |
| Auth | Local email/password + JWT; optional multi-provider SSO (any OIDC issuer, plus GitHub) |
| Packaging | Docker (one image: SPA baked into the jar), docker-compose |
| i18n | ngx-translate (English, German) |

## Development

### Backend Commands

```bash
cd backend
./mvnw clean package              # Build
./mvnw clean package -DskipTests  # Build without tests
./mvnw test                       # Run all tests
./mvnw test -Dtest=ApiKeyServiceTest  # Run a single test class
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev  # Run in dev mode
```

### Frontend Commands

```bash
cd frontend
npm install       # Install dependencies
npx ng serve      # Dev server (http://localhost:4200)
npx ng build      # Production build
npx ng test       # Run tests (Vitest + jsdom)
```

### Dev Mode

The `dev` profile points at the local PostgreSQL container (`docker compose up -d
testmanagement-db`), sets a throwaway JWT secret and seeds the admin password as
`admin`. No identity provider is required.

Tests run against H2 in PostgreSQL mode, so `./mvnw test` needs no container.
Vendor-specific SQL therefore lives in `db/specific/{vendor}/`, never under
`db/migration/` — Flyway scans that directory recursively.

## License

MIT - see [LICENSE](LICENSE) for details.
