# PRD-026 — Azure DevOps Integration

| | |
|---|---|
| **Status** | ✅ Implemented 2026-09-19 against stubs — not yet verified on a live Azure DevOps, see §8 |
| **Author** | Engineering (Claude) |
| **Created** | 2026-08-14 |
| **Priority** | P2 — the one ecosystem the tool cannot talk to at all |
| **Target** | v2.3 |
| **Related** | PRD-024 (build-server SPI, `AZURE_DEVOPS` enum value already reserved), PRD-010 (issue-tracker SPI), PRD-005 (CI ingestion), PRD-012 (multi-provider SSO) |

---

## 1. Summary

Azure DevOps is a single product that lands on four seams the tool already has. Treating it as one
feature would produce a 4000-line adapter; treating it as four features that happen to share a base
URL and a token produces four small ones, three of which are ordinary adapters against SPIs that
already exist.

| Surface | Seam | Work |
|---|---|---|
| **A. Pipelines** | `BuildServerProvider` (PRD-024) | New adapter. The `AZURE_DEVOPS` enum value was declared unimplemented in PRD-024 §3.1 precisely for this. |
| **B. Work Items** | `IssueTrackerProvider` (PRD-010) | New adapter + one enum value. |
| **C. Test results** | PRD-005 ingestion | Push path already works (docs only). Pull path is new: read results Azure DevOps already collected from a triggered pipeline run. |
| **D. Entra ID SSO** | PRD-012 OIDC | **Zero code.** Entra ID publishes a discovery document; the generic OIDC provider handles it today. Documentation and a verified-against-a-real-tenant checklist. |

Both cloud (`dev.azure.com`) and Azure DevOps Server (on-premises) are supported. On-prem is not a
footnote here — a self-hosted test tool is disproportionately likely to sit next to a self-hosted
Azure DevOps Server, and the two deployments differ in URL shape and in which API versions exist.

## 2. Goals & Non-Goals

**Goals**

- Trigger an Azure Pipeline from a project and watch its status live, through the existing global
  registry → workflow → project-assignment model. No new authorization concept.
- Pipeline **discovery**: the admin picks from a server-supplied list rather than typing an id.
  Azure DevOps has a clean list endpoint, so this is one of the providers where `discover()` works.
- Search, create and status-poll Azure Boards **work items** from a test result, with an OPEN/CLOSED
  pill that is correct under **custom processes**, not just out-of-the-box Agile.
- Pull test results from a completed pipeline run without requiring the customer to edit their YAML,
  for the common case where the pipeline already runs `PublishTestResults@2`.
- Document the Entra ID SSO path and verify it against a real tenant.
- Air-gap safe and opt-in, exactly as PRD-010/024: no configuration → no outbound calls.

**Non-Goals**

- **Writing into Azure Test Plans.** Ingestion is one-way, Azure DevOps → Testmanagement. Pushing our
  manual results back into Test Plans means mapping our cases onto their test points and suites, and
  owning a second source of truth for execution history. If it is wanted, it is its own PRD.
- Classic **Release** pipelines and the classic Build definitions API. Only YAML pipelines via the
  `pipelines` API. Anyone still on classic releases can use the push ingestion path.
- Azure Repos, Artifacts, Wiki, or work-item comments/attachments.
- **Service hooks / inbound webhooks.** PRD-003's non-goal stands; status comes from polling.
- Entra ID as an authentication mode for the *API calls* the adapters make. v1 is PAT-only — see
  §3.6 for why, and for the deadline that makes this worth revisiting.
- Azure DevOps Server with Windows-integrated auth (NTLM/Kerberos) for SSO. That is not OIDC and is
  not in scope; PAT-based API access to the same server is.

## 3. Proposed Design

### 3.1 The connection model, shared by A and B

Azure DevOps addresses everything as `{host}/{organization|collection}/{project}`. The two
deployments differ only in the middle:

| | Base URL to store | Project ref to store |
|---|---|---|
| Services (cloud) | `https://dev.azure.com/contoso` | `Payments` |
| Server (on-prem) | `https://tfs.corp.local:8080/tfs/DefaultCollection` | `Payments` |

**Decision: the organization/collection is part of `base_url`; the Azure DevOps project goes in the
existing project-reference column.** For the issue tracker that is `issue_tracker_configs.project_ref`;
for build servers it is `build_workflows.repo_ref`, with `workflow_ref` holding the numeric pipeline
id. Both existing schemas therefore need **no change** for the adapters themselves.

This is the single most common misconfiguration in every Azure DevOps client — entering
`https://dev.azure.com` and putting the org in the project field produces a 404 that reads like a
permissions problem. §3.7 makes the form say the right thing per provider.

**API version.** Azure DevOps requires `api-version` on every request and has no discovery endpoint
to negotiate it. `7.1` is current for Services and Azure DevOps Server 2022.1; Server 2020 tops out
at `6.0` and Server 2019 at `5.0`. Adding a nullable `api_version` column to both config tables
(migration V53, null = the adapter's default of `7.1`) is a two-line change that turns "this product
does not work on our server" into a settings field. `testConnection` validates it, so a wrong value
fails at configuration time rather than at first trigger.

### 3.2 A — Pipelines adapter (`AzureDevOpsProvider implements BuildServerProvider`)

Extends `HttpBuildProviderSupport`; registered by nothing, discovered by `BuildServerProviderRegistry`
via `type()`, per PRD-024.

- **`authenticate`** — `Authorization: Basic base64(":" + pat)`. Azure DevOps expects an empty
  username. Also sets `Accept: application/json` for the reason in the next paragraph.

- **The 203 trap — this is the one that will waste a day if it is not designed in.** With an expired,
  revoked or malformed PAT, Azure DevOps does not answer 401. It answers **`203 Non-Authoritative
  Information` with an HTML sign-in page**. A client that keys off `2xx` then fails inside the JSON
  parser with a message that has nothing to do with authentication. `HttpBuildProviderSupport`'s
  status→exception mapping must therefore be extended for this adapter: a 203, *or any response whose
  `Content-Type` is not JSON*, maps to the same `UpstreamServiceException` as a 401 — "rejected the
  configured access token". Getting this wrong does not merely produce a bad message; it breaks the
  failure taxonomy that `PipelineStatusPoller` and the settings banner depend on.

- **`trigger`** — `POST {base}/{project}/_apis/pipelines/{pipelineId}/runs?api-version=7.1`

  ```json
  {
    "resources": { "repositories": { "self": { "refName": "refs/heads/main" } } },
    "templateParameters": { "...": "workflow defaults + tester overrides" },
    "variables": { "TM_PIPELINE_RUN_ID": { "value": "<uuid>" } }
  }
  ```

  `templateParameters` are rejected unless declared in the YAML `parameters:` block, and queue-time
  `variables` are rejected unless marked settable at queue time (and are rejected outright if the
  organization has "Limit variables that can be set at queue time" enabled). This is the same failure
  GitHub and Forgejo Actions have, so it gets the same answer: **on 400, retry once with the
  parameters and variables stripped.**

  The retry costs less here than it does for Actions. The Actions adapters need `TM_PIPELINE_RUN_ID`
  to correlate a run they were never given an id for; Azure DevOps returns the run id in the trigger
  response, so a stripped retry loses only the *report-back* correlation — and surface C exists
  precisely so results can be pulled without the pipeline knowing anything about us.

  Response `200` → `{ id, state, result, _links.web.href }`. `externalRunId = id`,
  `externalUrl = _links.web.href` (fall back to `{base}/{project}/_build/results?buildId={id}` if the
  link is absent).

- **`fetchStatus`** — `GET {base}/{project}/_apis/pipelines/{pipelineId}/runs/{runId}?api-version=7.1`

  | `state` | `result` | `PipelineRunStatus` |
  |---|---|---|
  | `unknown` | — | `PENDING` |
  | `inProgress` | — | `RUNNING` |
  | `canceling` | — | `RUNNING` |
  | `completed` | `succeeded` | `SUCCESS` |
  | `completed` | `failed` | `FAILED` |
  | `completed` | `canceled` | `CANCELLED` |
  | `completed` | `unknown`/absent | `FAILED` |

  Correlation logic is not needed — `externalRunId` is always known after a successful trigger — so
  `StatusQuery.pipelineRunId` and `triggeredAt` go unused. ⚠️ The Pipelines `RunResult` enum has no
  `partiallySucceeded` value even though the Build API does; whether a partially-succeeded run
  reports `failed` or `succeeded` here must be **confirmed against a live organization** before this
  table is called final.

- **`discover`** — `GET {base}/{project}/_apis/pipelines?api-version=7.1` →
  `DiscoveredWorkflow(name = folder + "/" + name, repoRef = project, workflowRef = String(id), defaultRef = null)`.
  Paginated by `continuationToken`; one page (top 1000) is enough for a picker.

- **`testConnection`** — `GET {base}/_apis/projects/{project}?api-version=7.1`. Side-effect free and
  validates the organization, the project and the token in one call, which is exactly the set of
  things the admin can get wrong.

### 3.3 B — Work Items adapter (`AzureDevOpsIssueProvider implements IssueTrackerProvider`)

Extends `HttpIssueProviderSupport`. Add `AZURE_DEVOPS` to `IssueTrackerProviderType`; the
`provider VARCHAR(20)` column already accommodates the 13-character value, so **no migration**.

- **`search` is two calls**, unlike every provider we have. WIQL returns id references only:

  1. `POST {base}/{project}/_apis/wit/wiql?$top=20&api-version=7.1`
     ```sql
     SELECT [System.Id] FROM WorkItems
     WHERE [System.TeamProject] = @project
       AND [System.WorkItemType] IN ('Bug', 'Issue')
       AND [System.Title] CONTAINS '<query>'
     ORDER BY [System.ChangedDate] DESC
     ```
  2. `GET {base}/_apis/wit/workitems?ids=1,2,3&fields=System.Id,System.Title,System.State,System.WorkItemType&api-version=7.1`

  An empty result from step 1 must short-circuit — a batch GET with an empty `ids` is a 400.

  **WIQL has no parameter binding.** The user's query text is concatenated into a query language.
  Single quotes must be doubled and the input length-capped before it reaches the string, and that
  escaping gets its own unit test with `'; --` and `' OR '1'='1` style inputs. This is the only place
  in the codebase where user input becomes part of a query sent to a third party, and it is the one
  thing in this PRD that is a security bug rather than a support ticket if it is done casually.

- **`create`** — `POST {base}/{project}/_apis/wit/workitems/$Bug?api-version=7.1` with
  `Content-Type: application/json-patch+json` and a JSON-Patch array:

  ```json
  [ { "op": "add", "path": "/fields/System.Title", "value": "..." },
    { "op": "add", "path": "/fields/Microsoft.VSTS.TCM.ReproSteps", "value": "..." } ]
  ```

  Three traps, all silent:

  1. **The `$` in `$Bug` must not be percent-encoded.** Our `encodePath()` convention (PRD-010) would
     encode it and produce a 404. The type segment is built by hand from a validated identifier.
  2. **A Bug's description field is not `System.Description`.** In the Agile and CMMI processes it is
     `Microsoft.VSTS.TCM.ReproSteps`; in Scrum, Bugs use `Microsoft.VSTS.TCM.ReproSteps` as well while
     other types use `System.Description`. Writing to the wrong one *succeeds* and stores text that
     never appears on the work-item form. Mitigation: work-item type and body field are per-config
     with defaults `Bug` / `Microsoft.VSTS.TCM.ReproSteps`, and a 400 naming the field falls back to
     `System.Description` once.
  3. **The body is HTML, not Markdown.** Our templated issue body (PRD-010 §2, §3.5) is Markdown. Rendering it
     raw gives one unbroken paragraph with literal asterisks. The adapter HTML-escapes the template
     output and converts newlines to `<br>` — deliberately not a Markdown renderer; the template is a
     handful of labelled lines and a link.

- **`get`** — `GET {base}/_apis/wit/workitems/{id}?fields=...&api-version=7.1`. Work item ids are
  organization-unique, so `externalId` is the bare integer as a string (contrast GitLab's
  `group/project#123`). Web URL: `{base}/{project}/_workitems/edit/{id}`.

- **State → OPEN/CLOSED, without hardcoding state names.** Agile has New/Active/Resolved/Closed,
  Scrum has New/Approved/Committed/Done/Removed, CMMI has Proposed/Active/Resolved/Closed, and a
  custom process has whatever the customer typed. Hardcoding a name list is wrong for a meaningful
  share of real organizations. Instead:
  `GET {base}/{project}/_apis/wit/workitemtypes/{type}/states?api-version=7.1` returns each state
  with a `category` from `Proposed | InProgress | Resolved | Completed | Removed`.
  `Completed`/`Removed` → CLOSED, the rest → OPEN, an unrecognised category → UNKNOWN. The map is
  cached per (config, work-item type) for the lifetime of one `IssueStateRefresher` batch, so the
  poller makes one extra call per type per pass, not one per link.

- **`testConnection`** — same projects endpoint as §3.2, plus the 203/non-JSON guard, which
  `HttpIssueProviderSupport` needs for the same reason `HttpBuildProviderSupport` does.

### 3.4 C — Test result ingestion

**Push (works today, documentation only).** An Azure Pipelines job can already report into PRD-005:

```yaml
- script: |
    curl -sS -X POST \
      "$(TM_BASE_URL)/api/external/projects/$(TM_PROJECT_KEY)/test-runs/junit?pipelineRunId=$(TM_PIPELINE_RUN_ID)" \
      -H "X-API-Key: $(TM_API_KEY)" -H "Content-Type: application/xml" \
      --data-binary @test-results.xml
  condition: succeededOrFailed()
  displayName: Report results to Testmanagement
```

`TM_API_KEY` is a secret variable set once on the Azure DevOps side, per PRD-024's rule that we never
send credentials to the build server. This snippet belongs in the docs, not in code.

**Pull (new).** The push path requires editing YAML the customer may not own. But a pipeline that
runs `PublishTestResults@2` has *already* given Azure DevOps a structured result set, and we hold a
token that can read it. When a triggered run reaches a terminal state and its workflow has
`pullTestResults` enabled and nothing has yet linked a test run:

1. `GET {base}/{project}/_apis/test/runs?buildUri=vstfs:///Build/Build/{runId}&api-version=7.1`
2. per test run: `GET {base}/{project}/_apis/test/Runs/{testRunId}/results?$top=200&$skip=…&api-version=7.1`
3. map each result to the existing `CiResult` record —
   `suiteName = automatedTestStorage`, `title = testCaseTitle ?: automatedTestName`,
   `message = errorMessage + stackTrace`, `steps = []` — and hand the list to `CiIngestionService`.

Everything downstream is unchanged: auto-created cases still get the `ci-imported` label, dedup by
title still applies, `PipelineRunLinker` still links first-wins so a pushed result always beats a
pulled one.

Outcome mapping: `Passed` → PASSED, `Failed`/`Timeout`/`Aborted`/`Error` → FAILED, `Blocked` →
BLOCKED, `NotExecuted`/`None`/`NotApplicable` → SKIPPED, `Inconclusive`/`Warning` → SKIPPED with the
outcome name in the comment.

Placement: `PipelineRunRefresher` detects the terminal transition and enqueues the pull; the pull
itself runs outside the poller's transaction, per PRD-024's "HTTP outside DB tx" rule. Result count
is capped (5000 per pipeline run) and the cap is reported in the run's `error_message` rather than
silently truncating.

⚠️ Two assumptions to **verify against a live organization** before implementation: that a Pipelines
run id is the same integer as the Build id, and that `buildUri` has the form
`vstfs:///Build/Build/{buildId}`. If either is false the entry point becomes
`GET /_apis/build/builds/{id}` to read the `uri` field, which costs one call and no design change.

Schema: migration **V53** adds `build_workflows.pull_test_results BOOLEAN NOT NULL DEFAULT FALSE`
alongside the `api_version` columns from §3.1. `created_by`/`updated_by` already exist on these
tables.

### 3.5 D — Entra ID SSO

No backend change. Entra ID is an OIDC provider with a discovery document, which is exactly what
PRD-012's `OIDC` protocol targets. What this surface delivers is a documented, tested configuration:

- **Issuer** `https://login.microsoftonline.com/{tenantId}/v2.0` — the tenant GUID, not `common`,
  unless multi-tenant sign-in is genuinely wanted.
- **Email claim.** `email` is *not* guaranteed in an Entra ID token. Work or school accounts often
  carry only `preferred_username`. Either add `email` as an optional claim on the app registration or
  set `emailClaim` to `preferred_username`. This interacts with PRD-012's `trustEmailForLinking`:
  linking an existing local account on an unverified `preferred_username` is a weaker claim than the
  flag's name suggests, and the docs must say so.
- **Admin claim.** App roles (`roles` claim) are the clean option. If `groups` is used instead, the
  values are **object GUIDs, not display names** — and above roughly 200 group memberships Entra
  replaces the `groups` claim with a `_claim_names` pointer to the Graph API, at which point admin
  mapping silently stops working for exactly the users most likely to be admins. Document it;
  recommend app roles.
- Redirect URI must be registered on the app registration; `scopes` stay `openid,profile,email`.

Azure DevOps Server on-premises federating to AD FS or using Windows auth is out of scope (§2).

### 3.6 Security

- PAT encrypted at rest by the existing machinery — `IssueTrackerTokenCipher` on the tracker side,
  the shared `AesGcmCipher` injected into `BuildServerConfigService` on the build-server side. No new
  cipher, no new key, no new environment variable. Fails closed with no key, per PRD-010.
- SSRF: reuse `BuildServerUrlValidator` / `IssueTrackerUrlValidator`. **On-prem base URLs are private
  addresses by definition**, so `app.buildserver.allow-private-targets` / the issue-tracker
  equivalent must be enabled for Azure DevOps Server — that is a deliberate operator decision and the
  docs should frame it that way rather than as a checkbox to tick. Tests follow the established
  convention: test profile sets `allow-private-targets: true`, plus a DNS-free
  `AzureDevOpsUrlValidatorTest`.
- **Redirects are never followed** — already the client policy in both `Http*ProviderSupport` classes,
  and it matters more here than anywhere else: an unauthenticated Azure DevOps request redirects
  toward `login.microsoftonline.com`, and a followed redirect would put the customer's PAT in a
  request to Microsoft.
- PAT scopes to document, least-privilege, one per surface so separate configs can hold separate
  tokens: **Build (Read & execute)** for pipelines, **Work Items (Read & write)** for work items,
  **Test Management (Read)** for the pull path.
- **Global PATs are being retired.** Creation and regeneration of organization-spanning PATs is
  blocked from 2026-03-15, with full decommissioning announced for 2026-12-01; organization-scoped
  PATs remain supported. The config form's help text should say "organization-scoped PAT", and this
  dated fact should be re-checked at implementation time. If the timeline moves, Entra ID
  client-credentials auth for the adapters (§2 non-goal) becomes a v2 requirement rather than a
  nice-to-have.

### 3.7 Frontend

- Build-server side needs **nothing**: `buildServers.providers.AZURE_DEVOPS`,
  `buildServers.baseUrlHint.AZURE_DEVOPS` and `buildServers.tokenHint.AZURE_DEVOPS` already exist in
  `en.json`/`de.json` (the enum value was reserved in PRD-024 and the labels shipped with it). The
  base-URL hint currently reads "Organization URL." and should be sharpened to the §3.1 shape,
  because that sentence is exactly the ambiguity that causes the misconfiguration.
- Issue-tracker side needs `issueTracker.providers.AZURE_DEVOPS` added, and its `baseUrlHint` is a
  flat string today — it becomes provider-keyed, matching the build-server pattern. The
  registry-driven dropdowns (PRD-010 §3.5) pick the provider up with no component change.
- Two new optional admin fields: API version (placeholder `7.1`) and, on the workflow form, a
  "Pull test results after the run finishes" checkbox.
- The hint text both forms should carry: `https://dev.azure.com/{organization}` or
  `https://{server}/{collection}` — include the organization, not just the host.

## 4. Edge Cases

| Case | Behaviour |
|---|---|
| Expired/revoked PAT | 203 + HTML → "rejected the configured access token"; config flagged, banner shown, batch stops (PRD-010 rule) |
| Org in project field instead of base URL | `testConnection` 404 → message names both fields and shows the expected URL shape |
| Pipeline id points at a deleted pipeline | 404 on trigger → run recorded as ERROR with the message; workflow left assigned for the admin to fix |
| Undeclared template parameters / locked queue-time variables | 400 → one retry stripped of both; run proceeds without `TM_*`, report-back falls to the pull path |
| Run reaches terminal state before the first poll | Trigger already returned the id; first poll reports the final status directly |
| Custom process with renamed states | State *category* drives OPEN/CLOSED; unrecognised category → UNKNOWN pill, never a wrong one |
| `Bug` type disabled in the process | Create returns 400 → error names the configured type and points at the per-config type field |
| Pull enabled but pipeline publishes no results | Zero test runs for the build → no test run created, no error; run stays SUCCESS |
| Both push and pull deliver results | `pipeline_runs.test_run_id` first-wins; the pull is skipped when the field is already set |
| Server 2019/2020 rejecting `api-version=7.1` | 400 at `testConnection` → message names the `api_version` field and the 6.0/5.0 fallbacks |
| Azure DevOps rate limiting (429 + `Retry-After`) | Existing taxonomy; batch stops rather than retrying per item |

## 5. Phasing

Each phase is independently shippable and independently valuable.

1. **D — Entra ID SSO docs.** Zero code, zero risk, immediate value. Ship first.
2. **A — Pipelines adapter.** Self-contained; the enum value already exists.
3. **B — Work Items adapter.** Independent of A; shares only the 203/non-JSON guard, which A lands.
4. **C — Test result pull.** Depends on A. The push documentation ships with A.

## 6. Testing

- Stubbed-API adapter tests in the style of the existing five build providers and two issue
  providers, covering: the 203-HTML auth path, the 400-retry-stripped trigger path, state/result
  mapping, WIQL two-call search, the `$Bug` unencoded path segment, and JSON-Patch body shape.
- `AzureDevOpsUrlValidatorTest` — DNS-free, per the CI gotcha in PRD-024.
- A dedicated WIQL escaping test with quote-injection inputs.
- A state-category mapping test built from a *custom* process fixture, not an Agile one — an
  Agile-only fixture would pass with a hardcoded name list and prove nothing.
- **Live verification is part of done, not a follow-up.** PRD-024 shipped Woodpecker and Forgejo
  Actions against stubs only and carries that caveat to this day. This PRD names three claims that
  stubs cannot settle: the 203 behaviour, `partiallySucceeded` handling, and the
  `buildUri`/run-id relationship. Verification means one live Azure DevOps Services organization
  **and** one Azure DevOps Server 2022 instance, and the checklist is in the PR.

## 7. Open Questions

1. **Is Azure DevOps Server 2019/2020 in scope, or only 2022.1+?** Supporting 2020 means `6.0` and
   the `api_version` column earns its keep; 2022.1-only means `7.1` everywhere and the column is
   speculative. This is the one open question that changes §3.1.
2. **Should the work-item type be per-config or per-project?** Per-config is proposed. Teams that
   file bugs into different types per project would need per-project, which the PRD-010 schema
   already allows since its config *is* per-project — so this may be free.
3. **Pull-only workflows.** C is currently gated on a *triggered* run. Polling for results from
   pipeline runs we did not trigger is a different feature (it needs its own scheduling and a "which
   runs are ours" answer) and is deliberately excluded — worth confirming that matches intent.
4. **Does the pull path need to attach Azure DevOps result attachments** (screenshots, logs) to our
   results? PRD-010 §2 excludes storing attachments locally; the same reasoning probably applies, but
   test screenshots are more load-bearing than issue attachments.

## 8. As Built (2026-09-19)

All four surfaces are built. Differences from the design:

- **Migration V72, not V53** (V53 was taken long ago). It adds `api_version` to both config tables,
  `work_item_type` to `issue_tracker_configs`, `build_workflows.pull_test_results`, and one column
  the design did not have: `pipeline_runs.results_pulled_at`, so a run is pulled once, and a
  pipeline that published nothing, or a server that refused, is not asked again on every poll.
- **Open questions, as decided:** Server 2019/2020 are in scope through the API version setting
  (Q1). The work item type is per config, which is per project (Q2). Only runs the tool triggered
  are pulled (Q3). Result attachments are not pulled (Q4).
- **Build-server `testConnection` lists one project** (`_apis/projects?$top=1`) instead of reading
  one: a build-server connection is global and has no project. The project is checked per workflow
  when it is used.
- **The sign-in page guard is in `HttpBuildProviderSupport` for every provider**, not only Azure: a
  203, or an HTML body where JSON was expected, reads as a rejected token. Before, it read as a
  malformed response. The issue-tracker side already had the HTML half (PRD-029); the Azure adapter
  adds the 203.
- **Parameters:** workflow and tester parameters go as `templateParameters`, the injected `TM_*`
  values as queue-time `variables`. A 400 retries once without both; a second 400 fails the trigger.
- **`partiallySucceeded` maps to FAILED**, defensively; the pipelines API may never send it.
- **Unsupported api-version** errors name the setting and the 6.0/5.0 values, through a small
  `failureHint` hook on the build-server side and `rejectFailure` on the tracker side.
- **Work item body field:** no per-config field. Repro Steps is used, and a 400 naming it retries
  once with Description, which covers the types that lack it. A 400 or 404 on create names the
  configured work item type.
- **Search** matches Bugs, Issues and the configured type by title, and a number also matches the
  work item id. WIQL escaping is in `Wiql`, tested with quote-injection inputs.
- **State categories are cached for ten minutes** per config, project and type, instead of per
  `IssueStateRefresher` batch: the same effect (one lookup per type per pass) without threading a
  cache through the refresher.
- **Pulling** runs after each poll pass in `PipelineResultPuller`, with HTTP outside transactions.
  It takes SUCCESS and FAILED runs finished in the last day, so switching pulling on does not
  import a workflow's history. The test run is named after the workflow, like a pushed run without
  a name. At most 5,000 results, and the cap is noted on the pipeline run.
- **Entra ID** is a section of the user manual ("Setting up Microsoft Entra ID"); no code.
- **Frontend:** API version on Azure build servers and trackers, work item type on Azure trackers,
  and the pull toggle on Azure workflows, each shown only for Azure DevOps. The in-app manual listed
  Linear as a supported tracker, which has never had an adapter; it now lists Azure DevOps instead.

### Not verified

Everything is tested against stub servers only. §6 makes live verification part of done, and it is
**still open**: no Azure DevOps organization or Server was available. These need checking on one
Azure DevOps Services organization and one Azure DevOps Server 2022:

1. A revoked PAT is answered with 203 and HTML.
2. What a partially succeeded run reports in the pipelines API.
3. A pipeline run id is its build id, and `buildUri=vstfs:///Build/Build/{id}` finds its test runs.
4. The 400 for undeclared template parameters, and for a work item type without Repro Steps, looks
   as assumed (the retry keys on the field name in the body).
5. WIQL search, the batch work item read and state categories on a real, customised process.
6. The Entra ID manual section, against a real tenant.
