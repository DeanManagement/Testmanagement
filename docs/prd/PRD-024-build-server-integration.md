# PRD-024 — Build Server Integration (On-Demand Pipeline Triggering)

| | |
|---|---|
| **Status** | ✅ Implemented (2026-08-08, all five providers) |
| **Author** | Engineering (Claude) |
| **Created** | 2026-08-08 |
| **Priority** | P2 — closes the loop between manual and automated testing |
| **Target** | v2.1 |
| **Related** | PRD-005 (CI ingestion), PRD-010 (provider SPI pattern), PRD-003 (webhook HTTP conventions) |

---

## 1. Summary

PRD-005 lets CI pipelines push results *into* the tool, but a pipeline can only be started from the
build server itself. This PRD adds the other direction: an **instance admin** registers build
servers (GitLab CI, GitHub Actions, Forgejo/Gitea Actions, Woodpecker, Jenkins) **globally**,
defines workflows on them (picking from a server-discovered list where the provider API supports
it), and assigns those workflows to specific projects. Testers in an assigned project trigger a
workflow on demand from the UI and watch its status live. The tool
tracks the triggered pipeline's status by polling, and the workflow reports results back through the
existing PRD-005 ingestion endpoints (JUnit/Cucumber/generic JSON and Allure upload), correlated to
the trigger so the resulting test run and pipeline run reference each other.

The dead `CiIntegrationController`/`CiTriggerService` stub (hardcoded URLs, no credential store, no
SSRF guard) is deleted and replaced by this feature.

## 2. Goals & Non-Goals

**Goals**
- **Global** build-server connections (instance admin, alongside user/API-key management):
  provider, base URL, encrypted token. Multiple servers side by side (e.g. Jenkins + GitLab).
- Workflows defined per connection by the instance admin: name, repo ref, workflow reference,
  default branch/ref, default parameters. Where the provider API allows, the admin **selects from a
  discovered list** (GitHub/Forgejo workflow files, Jenkins jobs, Woodpecker repos); manual entry
  is always possible as fallback.
- Per-project assignment: the admin exposes chosen workflows to chosen projects. A project only
  ever sees its assigned workflows — never the server list, URLs, or credentials.
- One-click trigger (TESTER+) with optional ref and parameter overrides; each trigger creates a
  `PipelineRun` record with **live status visible to the tester** (running → success/failure
  updates without a manual reload) and a link to the external run.
- Correlation: results pushed back via `/api/external/**` carrying the pipeline-run id attach the
  created test run (and Allure report) to the trigger.
- Air-gap safe: no config → no outbound calls, exactly like PRD-010.

**Non-Goals**
- Inbound webhooks from build servers (PRD-003 non-goal stands; status comes from polling, results
  from the authenticated push channel).
- Sending CI secrets from the tool to the build server. The project API key used for reporting back
  is configured as a secret *on the CI side* by the admin, once.
- Scheduled/automatic triggering (cron), plan-level automation bindings, artifact pulling — later
  iterations.
- Log streaming or console output mirroring; the external run link covers this.

## 3. Proposed Design

### 3.1 Data model (migration V49)
- `build_server_configs` (**global**, no project FK): `id`, `name` (unique), `provider`
  (`GITLAB_CI|GITHUB_ACTIONS|FORGEJO_ACTIONS|WOODPECKER|JENKINS|AZURE_DEVOPS` — last one declared
  unimplemented, PRD-010 style), `base_url`, `api_token_encrypted`, `active`, `last_error`,
  `last_error_at`, BaseEntity columns.
- `build_workflows` (global, defined by instance admin): `id`, `build_server_config_id` (FK),
  `name`, `repo_ref` (provider-specific: GitLab project path/id, `owner/repo`, Woodpecker repo id,
  Jenkins job path), `workflow_ref` (workflow file for Actions; blank where `repo_ref` suffices),
  `default_ref` (branch), `default_parameters` (JSONB text), `active`, BaseEntity columns.
  Unique `(build_server_config_id, name)`.
- `project_build_workflows` (assignment): `id`, `project_id` (FK), `build_workflow_id` (FK),
  BaseEntity columns. Unique `(project_id, build_workflow_id)`.
- `pipeline_runs`: `id`, `build_workflow_id` (FK, `ON DELETE SET NULL`), `project_id` (FK — the
  project it was triggered from), `workflow_name` (denormalised), `status`
  (`TRIGGERED|PENDING|RUNNING|SUCCESS|FAILED|CANCELLED|TIMED_OUT|ERROR`), `external_run_id`,
  `external_url`, `triggered_ref`, `parameters` (JSONB text), `test_run_id` (nullable FK, set on
  report-back), `error_message`, `last_polled_at`, `finished_at`, BaseEntity columns. Index
  `(project_id, created_at)`, partial interest index on non-terminal status for the poller.

Deleting a server config cascades its workflows and assignments, but `pipeline_runs` survive:
they carry `external_url` and the denormalised `workflow_name`, so run history stays readable
after a server is removed or switched (PRD-010's denormalisation lesson). Unassigning a workflow
from a project likewise leaves its past runs intact.

### 3.2 Provider abstraction
Package `project/internal/buildserver/`, mirroring `issuetracker/`:

- `BuildServerProvider` SPI: `type()`, `trigger(DecryptedConfig, TriggerSpec) → TriggerResult`,
  `fetchStatus(DecryptedConfig, PipelineRun) → StatusResult`, `testConnection(DecryptedConfig)`,
  and `discoverWorkflows(DecryptedConfig, repoRef) → List<DiscoveredWorkflow>` for the admin's
  pick-list (GitHub/Forgejo: workflow files of a repo; Jenkins: job tree; Woodpecker: repos;
  GitLab: branches of a project — it has no workflow-file concept). Discovery is a UX assist, not
  a gate: manual entry always works, and a provider may return `DISCOVERY_UNSUPPORTED`.
  `DecryptedConfig` assembled per call, never persisted. Failures throw
  `UpstreamServiceException`.
- `BuildServerProviderRegistry` — `EnumMap` from injected `List<BuildServerProvider>`; feeds
  `GET /build-servers/providers`. Adding a provider = one bean.
- `HttpBuildProviderSupport` — cloned conventions from `HttpIssueProviderSupport`: lazy
  `HttpClient`, `Redirect.NEVER`, response cap, 401/403/404/429 taxonomy, JSON helpers.
- URL validation via `OutboundUrlValidator` with `app.buildserver.*` properties
  (`require-https=true`, `allow-private-targets=false`, timeouts, poll interval/batch,
  `run-timeout-minutes=120`).
- Token encryption via the shared `secretCipher` bean (`APP_ENCRYPTION_KEY`), write-only,
  `hasToken` boolean in responses.

**Per-provider trigger/status mechanics**

| Provider | Trigger | Status | Notes |
|---|---|---|---|
| GitLab CI | `POST /api/v4/projects/{id}/pipeline` (ref + variables), `PRIVATE-TOKEN` | `GET .../pipelines/{id}` | Returns pipeline id directly |
| Woodpecker | `POST /api/repos/{repoId}/pipelines` (branch + variables), Bearer | `GET .../pipelines/{number}` | Returns number directly |
| Jenkins | `POST {job}/buildWithParameters`, Basic user:apiToken | queue item → build number → build status | Two-step id resolution via `Location` queue URL; token stored as `user:token`, split in the adapter (no extra schema column) |
| GitHub Actions | `POST /repos/{o}/{r}/actions/workflows/{file}/dispatches` (ref + inputs) | list runs filtered by workflow+ref+created≥dispatch, newest match | Dispatch returns 204 **without a run id**. The adapter passes input `tm_run_id`; exact match when the workflow sets `run-name` containing it, else best-effort newest-run match flagged `external_id_confidence` |
| Forgejo Actions | same dispatch API as GitHub | same run-listing correlation | Also covers Gitea/Codeberg |

The dispatch-without-id problem is the one genuinely awkward spot; the docs page for the feature
shows the two-line `run-name:` snippet that makes correlation exact.

### 3.3 Trigger flow & report-back correlation
1. `POST /api/projects/{projectId}/workflows/{workflowId}/trigger` (TESTER; workflow must be
   assigned to the project, else 404) with optional `{ref, parameters}` → creates
   `PipelineRun(TRIGGERED)`, calls the adapter, stores `external_run_id`/`external_url`
   (or `ERROR` + message).
2. The adapter always injects non-secret variables: `TM_PIPELINE_RUN_ID`, `TM_PROJECT_KEY`,
   `TM_BASE_URL`.
3. The workflow runs tests, then POSTs results to the existing PRD-005 endpoints using its
   CI-side-configured project API key, adding `?pipelineRunId={TM_PIPELINE_RUN_ID}`. The external
   endpoints (`test-runs`, `/junit`, `/cucumber`) accept the new optional param, validate the
   pipeline run belongs to the key's project, link `pipeline_runs.test_run_id`, and name the run
   after the pipeline when no `runName` is given. Allure upload works unchanged against the created
   run key.
4. `PipelineStatusPoller` (`@Scheduled`, `IssueStatePoller` mould: instant no-op without active
   configs, bounded batch, oldest-polled first, per-config stop on auth failure, error recorded on
   config) advances non-terminal runs; runs older than `run-timeout-minutes` become `TIMED_OUT`.
   Because only *active* (non-terminal) runs are polled, the interval can be short —
   `poll-interval-ms` default 15s — without meaningful load; an idle instance does zero calls.
   A pipeline may finish `SUCCESS` with no reported results (misconfigured workflow) — the UI shows
   this state distinctly ("finished, no results received").

**Live status for the tester.** The trigger response returns the created `PipelineRun`; the
frontend then polls `GET /pipeline-runs` (cheap, local DB read) every ~5s while any listed run is
non-terminal and stops when all are settled. Combined with the 15s upstream poll, a tester sees
TRIGGERED → RUNNING → SUCCESS/FAILED progress on screen within seconds of the build server,
with no manual reload. `POST /pipeline-runs/{id}/refresh` forces an immediate upstream fetch for
the impatient case.

### 3.4 Endpoints (RBAC via PRD-001)

**Global** (instance admin, same guard as user/API-key management), under `/api/build-servers`:
- `GET /providers` — implemented provider list.
- `GET/POST /`, `GET/PUT/DELETE /{id}`, `POST /{id}/test`. Token write-only.
- `POST /{id}/discover` (`{repoRef?}`) — provider workflow/job/repo discovery for the pick-list.
- `GET/POST /{id}/workflows`, `PUT/DELETE /workflows/{workflowId}`.
- `GET/PUT /workflows/{workflowId}/projects` — assign the workflow to projects (multi-select).

**Project-scoped**, under `/api/projects/{projectId}`:
- `GET /workflows` (any member) — the workflows assigned to this project (name, default ref,
  default parameter names; no server URL, no credentials, no repo internals beyond what the admin
  named).
- `POST /workflows/{workflowId}/trigger` (TESTER).
- `GET /pipeline-runs?page=…` + `GET /pipeline-runs/{id}` (any member);
  `POST /pipeline-runs/{id}/refresh` (TESTER, forces an upstream status fetch).

**External** (API-key chain): optional `pipelineRunId` request param on the three ingestion POSTs.

### 3.5 Frontend
- **Global settings** `/settings/build-servers` (instance admin, alongside users/API keys,
  modelled on `issue-tracker-settings` + `sso-settings`): server list with provider badge +
  last-error banner; add/edit form (provider select from `/providers`, base URL, token,
  test-connection). Per server an expandable workflow list; "Add workflow" opens a dialog with a
  **discover** step (repo ref input → pick from the returned list) falling back to manual fields,
  plus default ref/parameters. Each workflow row has a project multi-select chip control for
  assignment.
- **Automation panel** on the project's test-runs page (visible only when workflows are assigned):
  assigned workflows with a Run button → dialog pre-filled with default ref/parameters; recent
  pipeline runs as a table with **live status chip** (spinner while non-terminal, auto-refreshing
  every ~5s until settled — the tester who triggered watches it go RUNNING → SUCCESS/FAILED
  without reloading), external link, and — once reported — a link to the created test run and its
  Allure report.
- New models + `build-server-api.service.ts`; routes in `settings.routes.ts` (global) and the
  automation panel wired into the test-runs feature.

### 3.6 Docs
Setup snippet per provider in the settings UI (hint text) covering: where to create the token, the
minimum scope, and a copy-pasteable reporting step (curl with `$TM_PIPELINE_RUN_ID` and the API-key
secret) plus the GitHub/Forgejo `run-name` correlation snippet.

## 4. Edge Cases
- Trigger succeeds upstream but response parsing fails → run stored as `PENDING` with null external
  id; poller-side correlation may still attach it (Actions path), else `ERROR` on timeout.
- Server deleted or workflow unassigned while runs are in flight → runs keep history (denormalised
  name/url), poller skips orphaned runs, status frozen with a note.
- Same workflow assigned to two projects → runs are strictly per-project; a project never sees
  another project's runs even for the shared workflow.
- Duplicate report-back for the same `pipelineRunId` → second ingestion creates its run but the link
  is first-wins; 409-free, logged.
- `pipelineRunId` from another project on the external endpoint → 404 (existence not disclosed
  cross-project, PRD-021 discipline).
- Rate limiting (429) → poller backs off for that config; trigger surfaces a retryable 502-style
  error.
- Jenkins queue item expires (build never started) → `ERROR` after timeout with queue URL in message.

## 5. Testing
- Adapter tests against stub HTTP servers per provider: trigger happy path, auth failure, 404, 429,
  malformed JSON, redirect-refusal; GitHub/Forgejo dispatch + run-listing correlation incl. the
  no-match and multiple-candidates cases; Jenkins queue→build resolution.
- Trigger/poll service tests: status transitions, timeout, stop-on-auth-failure batching, air-gap
  no-op.
- Correlation tests: external ingestion with valid/foreign/absent `pipelineRunId`; Allure attach;
  run naming.
- Authz: only instance admin configures servers/workflows/assignments; VIEWER cannot trigger;
  TESTER triggers only workflows assigned to their project (unassigned → 404); project-scoped
  endpoints never leak server URL or credentials; run listing is per-project even for shared
  workflows. Token never serialised; encrypted at rest.
- Discovery tests per provider (workflow files / job tree / repos / branches), incl.
  `DISCOVERY_UNSUPPORTED` fallback to manual entry.
- Frontend specs: settings CRUD + test-connection, trigger dialog defaults/overrides, run list
  status rendering incl. "finished, no results received".

## 6. Effort & Risk
- **Effort:** ~2.5 weeks. SPI + data model + GitLab + Woodpecker (clean APIs) ~1 week;
  GitHub/Forgejo correlation + Jenkins two-step + discovery ~0.5 week; global settings UI with
  discovery/assignment + automation panel ~1 week.
- **Risk:** Medium. Provider API variance is the known quantity (PRD-010 pattern absorbs it); the
  GitHub/Forgejo dispatch-without-id correlation is the novel risk, mitigated by the `run-name`
  convention and an explicit confidence flag rather than silent misattribution.

## 7. Acceptance Criteria
- [x] Instance admin registers build servers globally (multiple allowed), token encrypted and
      never returned; test-connection works; non-HTTPS/private URLs rejected by default.
- [x] Admin defines workflows — via discovery pick-list where supported, manually otherwise — and
      assigns them to projects; a project sees only its assigned workflows and no server detail.
- [x] Tester triggers an assigned workflow with parameter overrides; the run's status updates live
      on screen (TRIGGERED → RUNNING → terminal) without a reload, with an external link.
- [x] A workflow reporting JUnit results + Allure zip with `pipelineRunId` produces a test run
      linked from the pipeline run (and vice versa via run detail).
- [x] GitHub/Forgejo runs correlate exactly when the documented `run-name` snippet is used.
- [x] No outbound calls with no active config; poller stops per config on auth failure and records
      the error in settings.
- [x] Old `CiIntegrationController`/`CiTriggerService` removed. All backend + frontend tests green.

## 8. As Built (2026-08-08)

All five providers shipped at once rather than incrementally — the `HttpBuildProviderSupport` base
(cloned conventions from PRD-010's `HttpIssueProviderSupport`, plus raw-response access for
Jenkins' `Location` header and the Actions 204s) absorbed the variance as designed. Notable
deviations from the proposal: none structural. Details worth knowing:

- **Migration is V49**; tables as §3.1. `pipeline_runs.test_run_id` is `ON DELETE SET NULL`, so
  deleting a test run does not take its pipeline history with it.
- **Trigger is deliberately non-transactional** (`PipelineRunService.trigger`): the run row is
  committed before the provider call (the webhook "no HTTP inside a DB transaction" convention),
  so a failed upstream call leaves a visible ERROR run instead of rolling it back.
- **GitHub/Forgejo 422 handling:** a dispatch rejected for undeclared inputs is retried once with
  no inputs. The trigger then works but correlation falls back from run-name to
  newest-plausible-candidate matching (workflow + branch + created-after-trigger, 60 s skew).
- **Jenkins:** secret stored as `user:apiToken`, split in the adapter; external id starts as
  `queue:<n>` and is upgraded to the build number when the queue item names its executable.
  `UNSTABLE` maps to FAILED (tests failed is exactly what it means here).
- **Poller:** 15 s fixed delay with two cheap local guards (any active server? any non-terminal
  run?), batch of 20, oldest-polled first, per-server stop on upstream failure, 120 min timeout →
  TIMED_OUT. The frontend panel polls the local list endpoint every 5 s only while a run is live.
- **Tests:** 451 backend green (adapter stubs for GitLab/Jenkins/GitHub incl. 422-retry, queue
  upgrade and all three correlation outcomes; API tests for admin authz, token hygiene, SSRF
  rejection, assignment-as-authorization, per-project run isolation, and both correlation
  directions on the external API). 51 frontend green.

### Workflow author's report-back recipe

The trigger injects `TM_PIPELINE_RUN_ID`, `TM_PROJECT_KEY` and (when `PUBLIC_BASE_URL` is set)
`TM_BASE_URL` as pipeline variables/inputs. The project API key is configured as a CI-side secret.

```yaml
# GitHub/Forgejo Actions — exact correlation + report-back
run-name: TM ${{ inputs.TM_PIPELINE_RUN_ID }}
on:
  workflow_dispatch:
    inputs:
      TM_PIPELINE_RUN_ID: { required: false }
      TM_PROJECT_KEY: { required: false }
      TM_BASE_URL: { required: false }
```

```bash
# After the test step (any provider; variables are env vars on GitLab/Woodpecker/Jenkins):
curl -f -X POST \
  -H "X-API-Key: $TM_API_KEY" \
  -H "Content-Type: application/xml" \
  --data-binary @target/surefire-reports/merged.xml \
  "$TM_BASE_URL/api/external/projects/$TM_PROJECT_KEY/test-runs/junit?pipelineRunId=$TM_PIPELINE_RUN_ID"

# Optional: attach the Allure report to the created run (key is in the response above as .key):
curl -f -X POST -H "X-API-Key: $TM_API_KEY" -F "file=@allure-report.zip" \
  "$TM_BASE_URL/api/external/projects/$TM_PROJECT_KEY/test-runs/$RUN_KEY/allure-report"
```

### Still to do
- Real-world verification against live Forgejo/Woodpecker instances — the adapters were built
  against stubbed APIs; Forgejo's `/actions/tasks` response shape in particular varies by version
  and the adapter parses it defensively (`workflow_runs` or `entries`).
- Plan-level automation bindings and scheduled triggering stay future work (§2 non-goals).
