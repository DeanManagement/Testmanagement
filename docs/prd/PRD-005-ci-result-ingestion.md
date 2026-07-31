# PRD-005 — CI Result Ingestion (JUnit XML + Cucumber JSON)

| | |
|---|---|
| **Status** | Implemented (2026-06-09) |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-09 |
| **Priority** | P2 — Integration |
| **Target** | v1.3 (JUnit) / v1.3+ (Cucumber) |
| **Related** | REQUIREMENTS.md §12.6; REVIEW §4.6 |

---

## 1. Summary

The tool already accepts a generic external run submission and Allure ZIPs. Add the two report formats that essentially every JVM/JS CI pipeline emits by default — **JUnit XML** (Surefire/Jest/PHPUnit/pytest) and **Cucumber JSON** — so teams can pipe existing CI output in with no custom payload. This makes the tool viable as a CI aggregator. Ship JUnit first; it covers ~80% of real CI output.

## 2. Goals & Non-Goals

**Goals**
- Ingest JUnit XML and Cucumber JSON via the existing API-key-authenticated external API.
- Auto-create missing test cases from the report structure.
- Attach failure messages / stack traces as result comments.

**Non-Goals**
- Live CI plugins (the point is to avoid them).
- Parsing every vendor dialect quirk — target the common JUnit schema and standard Cucumber JSON.

## 3. Proposed Design

### 3.1 Endpoints (API-key auth, existing `/api/external/**` chain)
- `POST /api/external/projects/{projectKey}/test-runs/junit` — `Content-Type: application/xml`.
- `POST /api/external/projects/{projectKey}/test-runs/cucumber` — `Content-Type: application/json`.
- Optional query params: `runName`, `environment`, `testPlanId` (mirrors the generic submission).

### 3.2 Mapping
**JUnit XML:** `<testsuite>` → test suite (by name); `<testcase classname.name>` → test case (match existing by a derived key/title, else auto-create as `ACTIVE`); `<failure>/<error>` → result `FAILED` (or `BLOCKED` for errors), message + stack trace stored as a comment on the result; `<skipped>` → `SKIPPED`; otherwise `PASSED`. `time` attribute captured where the model allows.

**Cucumber JSON:** feature → suite; scenario → test case; steps → test steps; step status → step result; any failed step → result `FAILED`.

A new test run (`status=COMPLETED`) is created and linked to the optional plan; existing run-key generation (`PROJ-R-N`) is reused.

### 3.3 Implementation
- Parse JUnit with a simple JAXB binding (or `junit-platform-reporting` schema if strictness is wanted); Cucumber JSON with Jackson to typed records.
- Reuse `ExternalTestRunService` patterns; add per-format adapters that normalise into the existing internal "create completed run with results" path.
- Auto-creation is idempotent within a submission (don't create the same case twice).

## 4. Edge Cases
- Unknown/empty file → 400 with parse error detail.
- Mixed pass/fail/skip counts reflected in the run report.
- Large reports: stream-parse; enforce a max payload size consistent with other uploads.
- Auto-created cases flagged (e.g., a `ci-imported` label) so they're distinguishable from hand-authored cases.

## 5. Testing
- Fixture reports from Surefire, Jest, and pytest (JUnit) and a standard Cucumber JSON file → expected runs/results.
- Auto-creation vs match-existing behavior.
- Malformed XML/JSON → 400.
- API-key auth required; JWT path not exposed for these.

## 6. Effort & Risk
- **Effort:** ~3 days each (REVIEW). JUnit first.
- **Risk:** Medium — schema variance across tools. Mitigate by targeting the common subset and failing clearly on the rest.

## 7. Acceptance Criteria
- [x] JUnit XML POST creates a completed run with correctly mapped statuses (`failure`→FAILED, `error`→BLOCKED, `skipped`→SKIPPED, else PASSED) and stack-trace comments.
- [x] Cucumber JSON POST maps feature/scenario/step structure (scenario fails if any step fails; steps become step results).
- [x] Missing test cases are auto-created idempotently (de-duped within a submission and matched by title across submissions) and labelled `ci-imported`.
- [x] Malformed/empty input returns 400; endpoints require API-key auth (401 without key).
- [x] Fixture-based tests pass for three JUnit producers (Surefire, Jest, pytest) + Cucumber (147 backend tests green).

## 8. Implementation Notes (as shipped)

- **Endpoints** (existing API-key `/api/external/**` chain): `POST .../test-runs/junit` (`application/xml`) and `POST .../test-runs/cucumber` (`application/json`), both with optional `runName`, `environment`, `testPlanId` query params and a 10 MB body cap.
- **Parsers** (`project/internal/ci`): `JUnitXmlParser` uses a DOM parser with DOCTYPE/external entities disabled (XXE-safe), handling both `<testsuite>` root and `<testsuites>` wrappers; `CucumberJsonParser` uses Jackson typed records. Both normalize into `CiResult`.
- **Ingestion** (`CiIngestionService`): creates a `COMPLETED` run (reusing the portable per-project run-number generator), auto-creates missing cases (`ACTIVE`, `MEDIUM`, `ci-imported` label; Cucumber scenarios also get their steps), maps statuses, and stores failure messages + stack traces in the result `comment` (already a `TEXT` column). Auto-creation is cached by title per submission and matches existing cases by title across submissions.
- **Deviation:** JUnit `<testsuite>` grouping is not materialized as a `TestSuite` (results are grouped by the run). Suite auto-creation can be added later if needed.
