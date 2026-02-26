# Testmanagement

A self-hosted test management tool for simple organisations. Manage test cases, execute test runs, track results, and integrate with CI/CD pipelines — all without sending data to external services.

**License:** MIT

## Features

- **Projects** — organise test artifacts under projects with short keys (e.g. `PROJ`)
- **Test Cases** — define test cases with steps, expected results, priorities, labels
- **Test Suites** — group test cases into suites for organised execution
- **Test Runs** — execute test suites across environments, track pass/fail per step
- **Screenshots** — attach screenshots to individual step results
- **Allure Reports** — upload Allure HTML report ZIPs to test runs and view them in the browser
- **External API** — accept completed test runs from CI/CD pipelines via API key authentication
- **Settings** — manage API keys for external integrations from the UI
- **i18n** — English and German language support with runtime switching

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        Browser                               │
│                  Angular 19 + Material                       │
└──────────────────┬───────────────────────────────────────────┘
                   │ HTTP (port 80)
┌──────────────────▼───────────────────────────────────────────┐
│              Nginx (frontend container)                       │
│         Serves static files + reverse proxies /api/*         │
└──────────────────┬───────────────────────────────────────────┘
                   │ /api/* (port 8080)
┌──────────────────▼───────────────────────────────────────────┐
│            Spring Boot 3.5 (backend container)               │
│    ┌─────────────────────────────────────────────────┐       │
│    │  /api/**           OIDC (Keycloak JWT)          │       │
│    │  /api/external/**  API Key (X-API-Key header)   │       │
│    └─────────────────────────────────────────────────┘       │
└──────────────────┬───────────────────────────────────────────┘
                   │ JDBC
┌──────────────────▼───────────────────────────────────────────┐
│              PostgreSQL 16 (db container)                     │
└──────────────────────────────────────────────────────────────┘

External:  Keycloak (OIDC provider, not included in docker-compose)
```

## Quick Start

### Prerequisites

- Docker and Docker Compose
- A Keycloak instance with an OIDC realm configured (not needed for dev mode)

### Production (Docker Compose)

```bash
# Set environment variables
export DB_PASSWORD=your_secure_password
export KEYCLOAK_ISSUER_URI=https://keycloak.example.com/realms/testmanagement
export KEYCLOAK_JWK_SET_URI=https://keycloak.example.com/realms/testmanagement/protocol/openid-connect/certs

# Start all services
docker compose up --build
```

The application is available at `http://localhost`.

### Development (no Keycloak required)

```bash
# Terminal 1 — Backend (H2 in-memory DB, auth disabled)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Terminal 2 — Frontend (proxies to backend on :8080)
cd frontend
npm install
npx ng serve
```

The application is available at `http://localhost:4200`.

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
         │ 4. POST /api/external/projects/{projectId}/test-runs
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

### Step 2: Submit a Test Run

```bash
curl -X POST \
  http://localhost:8080/api/external/projects/{projectId}/test-runs \
  -H "Content-Type: application/json" \
  -H "X-API-Key: tm_a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2" \
  -d '{
    "name": "CI Build #142 - Integration Tests",
    "environment": "staging",
    "results": [
      {
        "testCaseId": "550e8400-e29b-41d4-a716-446655440001",
        "status": "PASSED",
        "comment": "All assertions passed"
      },
      {
        "testCaseId": "550e8400-e29b-41d4-a716-446655440002",
        "status": "FAILED",
        "comment": "Assertion failed on line 42",
        "defectLink": "https://issues.example.com/BUG-789",
        "stepResults": [
          {
            "testStepId": "660e8400-e29b-41d4-a716-446655440001",
            "status": "PASSED",
            "actualResult": "Login page displayed"
          },
          {
            "testStepId": "660e8400-e29b-41d4-a716-446655440002",
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
| `testCaseId` | UUID | Yes | ID of an existing test case in the project |
| `status` | enum | Yes | `PASSED`, `FAILED`, `BLOCKED`, `SKIPPED`, `PENDING` |
| `comment` | string | No | Free-text comment |
| `defectLink` | string | No | Link to an external issue/defect |
| `stepResults` | array | No | Per-step results (auto-created if omitted) |

Each **stepResult**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `testStepId` | UUID | Yes | ID of a test step belonging to the test case |
| `status` | enum | Yes | `PASSED`, `FAILED`, `BLOCKED`, `SKIPPED`, `PENDING` |
| `actualResult` | string | No | What actually happened |

If `stepResults` is omitted, step results are **auto-created** for every step of the test case, all set to the result's status.

### Response

The endpoint returns a `201 Created` with the full test run object (same format as `GET /api/projects/{id}/test-runs/{runId}`).

### Authentication Errors

| Status | Meaning |
|--------|---------|
| `401` | Missing, invalid, or revoked API key |
| `404` | Project, test case, or test step not found |
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

# Upload to a test run (use the test run ID from the creation response)
curl -X POST \
  http://localhost:8089/api/external/projects/{projectKey}/test-runs/{testRunId}/allure-report \
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
| POST | `/api/external/projects/{key}/test-runs` | Submit a completed test run |
| POST | `/api/external/projects/{key}/test-runs/{runId}/allure-report` | Upload an Allure report ZIP |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.5, Maven |
| Frontend | Angular 19, Angular Material, NgRx, Chart.js |
| Database | PostgreSQL 16, Flyway migrations |
| Auth | OIDC via Keycloak (external) |
| Packaging | Docker, docker-compose |
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
npx ng test       # Run tests
```

### Dev Mode

The `dev` profile uses an H2 in-memory database (PostgreSQL compatibility mode) and disables OIDC authentication. No external services are required.

## License

MIT - see [LICENSE](LICENSE) for details.
