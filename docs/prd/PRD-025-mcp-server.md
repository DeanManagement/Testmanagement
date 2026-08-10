# PRD-025 — MCP Server (Agent-Authored Test Cases & Plans)

| | |
|---|---|
| **Status** | ✅ Implemented (2026-08-09) |
| **Author** | Engineering (Claude) |
| **Created** | 2026-08-09 |
| **Priority** | P2 — new integration surface |
| **Target** | v2.2 |
| **Related** | PRD-001 (RBAC), PRD-002 (filtering/pagination), PRD-004 (import/export), PRD-005 (API keys, external ingestion), PRD-007 (search), PRD-021 §4.2 (API-key project scoping) |

---

## 1. Summary

Testmanagement has two machine-facing surfaces today: `/api/external/**` (API-key, **write-only** —
four POSTs: create run, JUnit, Cucumber, Allure upload) and `/api/**` (JWT, the surface the SPA
uses). Neither is usable by an LLM
agent: the first cannot read and cannot author test cases, the second needs a human's short-lived
token.

This PRD adds an **in-process MCP server** to the backend, exposed at `/api/mcp` over
stateless streamable-HTTP, authenticated with the existing project-scoped API keys. It publishes a
focused **authoring** tool surface — search and read projects, test cases, folders, suites and
plans; create and update test cases (with steps); create suites and plans — so an agent connected to
a repo can turn requirements or a diff into properly structured test cases in the right project,
without a human copying anything by hand.

It also closes a real authorization gap on the way. The API-key principal is currently the string
`"api-key:<name>"`, which is not a UUID, so `ProjectAccessService.currentUserId()` returns `null`
and `ProjectRoleAspect` **fails open** for API-key callers (`ProjectRoleAspect:30-35`, with an
explicit "fail open here to preserve dev/test parity" comment). The same parse also gates
`SecurityAuditorAware`, so API-key writes cannot populate `created_by` today. Reusing the domain
services from an MCP tool is therefore **blocked** on an API key resolving to a real UUID principal
with a real project role — §3.2 is a hard prerequisite, not a side benefit.

## 2. Goals & Non-Goals

**Goals**

- MCP server embedded in the Spring Boot app — one artifact, one deployment, no sidecar. Reachable
  by any MCP client (Claude Code/Desktop, IDE agents, custom clients) over HTTP.
- Authentication with an existing project-scoped API key. The key's project **is** the agent's
  blast radius: an agent cannot see or touch any other project.
- Every API key resolves to a **service user** with a genuine `ProjectMember` role, so
  `@RequireProjectRole` enforces instead of failing open, and `created_by`/`updated_by` on
  agent-written rows point at an identifiable actor.
- Read tools rich enough that an agent can avoid duplicating work: search test cases, list folders,
  read a suite or plan before adding to it.
- Write tools for the authoring loop only: create/update test case, create suite, create plan,
  plus a bounded bulk create.
- Off unless switched on. No config → no MCP endpoint, matching PRD-010/PRD-024 air-gap discipline.
- Auditable: every tool invocation is recorded and visible to an instance admin.

**Non-Goals**

- Execution. Test runs, results, step results and report generation stay out of v1 — an agent
  recording pass/fail is PRD-005's job, and the tool surface stays small enough to be cheap in an
  agent's context window.
- Destructive operations. No delete tools, no member management, no project creation, no API-key
  management. Deletion stays a human action in the UI.
- An MCP **client** in the backend (the tool calling out to models). This PRD is server-side only;
  no model provider, no API keys to any LLM vendor, no outbound AI calls.
- OAuth 2.1 / dynamic client registration per the MCP authorization spec. API keys are the
  authentication story for v1; OAuth is noted as future work in §8.
- A standalone stdio MCP server. Clients that only speak stdio can front the HTTP endpoint with
  `mcp-remote` or equivalent; shipping a second artifact is not worth it yet.

## 3. Proposed Design

### 3.1 Dependency, transport and placement

Spring AI 2.0 (GA 2026-06-12) requires Spring Boot 4.1 + Spring Framework 7 and Java 21+ — the
backend is already on Boot 4.1.0 / Java 25, so no version gymnastics. Add:

```xml
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

with the `spring-ai-bom` imported alongside the existing `spring-modulith` BOM. The servlet
(`webmvc`) variant matches the existing stack; no WebFlux is introduced.

Configuration:

```yaml
app:
  mcp:
    enabled: false          # master switch, off by default
    max-writes-per-minute: 60
    max-bulk-size: 50
    max-steps-per-case: 100
spring:
  ai:
    mcp:
      server:
        enabled: ${app.mcp.enabled}
        protocol: STATELESS       # stateless streamable-HTTP
        name: testmanagement
        version: ${project.version}
        streamable-http:
          mcp-endpoint: /api/mcp
```

**STATELESS** rather than session-based streamable-HTTP: authentication is per-request (an API key
header on every call), there is no server-side conversation state worth keeping, and stateless
means no session affinity if the app is ever run with more than one replica. SSE transport is
deprecated in Spring AI 2.0 and is not offered.

**Placement.** Spring Modulith is in play and the domain services the tools need (`TestCaseService`,
`TestSuiteService`, `TestPlanService`, `TestCaseFolderService`, `SearchService`,
`ProjectAccessService`, the DTOs) all live under `project.internal.*`, which a sibling top-level
module may not touch. A new top-level `mcp` module would therefore force a large widening of the
`project` module's public surface for no benefit. (`PageableUtils` sits in `shared` and
`User`/`UserService` in `user`, both already legitimately reachable.) The tool beans instead live at
`project/internal/mcp/` (`TestCaseTools`, `TestPlanningTools`, `ProjectDiscoveryTools`,
`McpToolAuditor`), with the transport/security wiring in `project/internal/config/`. If a second
consumer ever needs the same operations, that is the moment to extract a public facade — not before.

Tools are declared with the Spring AI 2.0 annotation API — `@McpTool` / `@McpToolParam` on bean
methods, JSON schema derived from parameter types — so there is no hand-written schema to drift
from the records.

### 3.2 Authentication: API keys become real principals (migration V50) — ✅ shipped 2026-08-09

> **As built.** Implemented as its own commit ahead of any MCP code, as §6 proposed. 513 backend
> tests green, 51 frontend, frontend builds clean. Deviations from the proposal below are marked
> **[as built]**.
>
> **[as built] The role is enforced on `/api/external/**` too, not just prepared for MCP.** The
> proposal treated §3.2 as plumbing, which would have shipped a Viewer/Tester selector that changed
> nothing — the CI endpoints carry no `@RequireProjectRole`, so a "read only" key could still ingest
> runs and upload Allure reports. `ExternalTestRunController` now calls a private `requireTester()`
> on all four POSTs. The annotation cannot be used there: `ProjectRoleAspect` resolves the project
> by calling `UUID.fromString` on the path variable, and `{projectRef}` may be a project key, so the
> check resolves the project itself and delegates to `requireRoleForCurrentUser`. Existing keys are
> backfilled as `TESTER` and are unaffected; a `VIEWER` key now gets 403.


Today `ApiKeyAuthenticationFilter` sets a principal named `"api-key:" + name` with `ROLE_API_KEY`.
That is fine for the three write-only ingestion endpoints, which carry the project in the path and
check it in the filter, but it cannot support tools that go through the domain services.

Changes:

- `users`: add `service_account BOOLEAN NOT NULL DEFAULT false`. Service accounts cannot log in —
  rejected explicitly in **`AuthService.login`** (`AuthController` is a pure delegation) and in
  `SsoLoginService`, which is the real hole: it links an existing account **by email** and never
  consults `password_hash`, so the null hash that incidentally blocks password login does not block
  SSO. Service accounts are excluded from user administration lists, assignee pickers, watcher
  selection and notifications, and are shown as `API key: <name>` where they appear as `created_by`.
- `api_keys`: add `service_user_id UUID` (FK → `users`, unique) and `role VARCHAR(20) NOT NULL
  DEFAULT 'TESTER'` (`VIEWER` | `TESTER` only; `ADMIN` is not offered — an agent has no business
  managing members or deleting a project).
- Creating an API key creates its service user (`email = apikey-<keyPrefix>@service.invalid`,
  `display_name = name`, `password_hash = null`, `service_account = true`, `system_admin = false`)
  and a `ProjectMember` row binding it to the key's project with the chosen role, in one
  transaction. `ProjectMember` has no active flag and no soft-delete, so **revoking a key deletes
  the membership row** (the key's `revoked` flag is the record of what happened). Adding an `active`
  column to `ProjectMember` for this is rejected — it would put a nullable state flag on the table
  every authorization check reads. **[as built]** The service *user* survives revocation: rows it
  authored point at it via `created_by`, and deleting it would make that history unattributable.
  There is no delete-a-key endpoint to reconcile — `DELETE /api/api-keys/{id}` revokes.
- **[as built]** Service accounts are also filtered out of `ProjectMemberService.findByProject`, and
  `addMember`/`updateRole`/`removeMember` refuse them (404). They hold real memberships, but member
  management and assignee pickers read that list, and a key's role belongs on the API-key page.
- `ApiKeyAuthenticationFilter` sets the principal name to the **service user's UUID** with
  authorities `ROLE_API_KEY` + `ROLE_USER`. `currentUserId()` then resolves, `@RequireProjectRole`
  enforces properly, and `SecurityAuditorAware` populates `created_by`/`updated_by`.
- **[as built]** The fail-open in `ProjectRoleAspect` is closed *surgically*, via a new
  `ProjectAccessService.resolvedCallerOrNull()`. No principal at all (anonymous or permit-all, which
  is how most MockMvc slices run) still skips the check — that is the dev/test parity the original
  comment was protecting. A principal that **exists but does not resolve to a user id** now throws
  `ForbiddenException` instead of falling into the same branch. That distinction is the whole fix:
  `"api-key:<name>"` was landing in the second case and being treated as the first.
- The filter's existing path-based project-scope check for `/api/external/**` stays as
  defence-in-depth; it is now belt *and* braces rather than the only strap.
- Legacy project-less keys (`project_id IS NULL`, deprecated since PRD-021 §4.2) cannot be given a
  membership and are **rejected** rather than warned about, gated by
  `app.api-keys.allow-legacy-global` (default `false`). This is a deliberate breaking change for
  anyone still on one; the release note calls it out.

**Backfill.** `created_by` on `users`/`project_members` and vendor-neutral UUID generation make a
pure-SQL backfill awkward across Postgres and H2, so V50 adds the columns only and a small
idempotent `ApiKeyServiceUserBackfill` (`ApplicationRunner`) creates the missing service users and
memberships at startup, defaulting existing keys to `TESTER`. It logs a one-line summary and is a
no-op on subsequent boots. **[as built]** It replaces `LegacyApiKeyWarner`, absorbing that warning
and re-wording it for the new reject-by-default behaviour; a key that fails to backfill is logged
and skipped rather than aborting startup — a self-hosted instance should not refuse to boot over one
bad credential.

**Frontend [as built].** The API-key create dialog gained a Viewer/Tester select (default Tester)
and the key list a Role column, with `en`/`de` strings. Nothing else changed: `role` is optional on
the request and defaults to `TESTER`, so an older client keeps working.

### 3.3 Security chain

`ApiKeySecurityConfig` (`@Order(1)`) widens its `securityMatcher` to
`{"/api/external/**", "/api/mcp/**"}` and `/api/mcp/**` requires `hasRole("API_KEY")`. The widening
is itself **conditional on `app.mcp.enabled`** (`@ConditionalOnProperty` selecting one of two
matcher arrays): an `@Order(1)` chain claims a request before the dispatcher sees it, so an
unconditional matcher would answer `401` for a feature that is switched off instead of `404`.
Disabled means disabled — no Spring AI beans, no chain entry, no endpoint.

`JwtAuthenticationFilter.shouldNotFilter` also gains `/api/mcp`. Strictly this is redundant — that
filter only runs on the `UserSecurityConfig` chain, which never sees a request claimed by chain 1 —
but it mirrors the existing `/api/external/` entry and costs nothing.

The filter accepts the key from either header, so standard MCP clients work without custom header
support:

- `X-API-Key: tm_…` (existing convention), or
- `Authorization: Bearer tm_…` — distinguished from a JWT by the `tm_` prefix, so there is no
  ambiguity with the JWT chain.

A missing or invalid key returns `401` with `WWW-Authenticate: Bearer` so MCP clients surface a
usable error rather than a bare protocol failure.

### 3.4 Tool surface (v1)

All tools operate inside the key's project; **no tool takes a project id**. Paging follows
PRD-002's conventions (`PageableUtils`, default size 50, max 200) with `page`/`size` arguments and a
`totalElements` field in the response, so an agent can tell when it is looking at a slice.

**Read** (`readOnlyHint`)

| Tool | Arguments | Returns |
|---|---|---|
| `get_project` | – | key, name, description, counts of cases/suites/plans |
| `search_test_cases` | `q?`, `status[]?`, `priority[]?`, `label[]?`, `folderId?`, `updatedAfter?`, `page?`, `size?` | paged summaries (id, key, title, status, priority, labels, folderId) |
| `get_test_case` | `idOrKey` | full case incl. ordered steps |
| `list_test_case_folders` | – | the existing nested `TestCaseFolderResponse` tree (id, name, parentId, sortOrder, testCaseCount, children) |
| `list_test_suites` | `q?`, `page?`, `size?` | paged summaries |
| `get_test_suite` | `id` | suite + member case ids/titles |
| `list_test_plans` | `status?` | plans (id, name, status, targetDate, testRunCount) |
| `get_test_plan` | `id` | plan + run summary counts |

**Write** (`destructiveHint: false`, `idempotentHint: false`)

| Tool | Arguments | Notes |
|---|---|---|
| `create_test_case` | `title`, `description?`, `preconditions?`, `priority`, `status?` (default `DRAFT`), `labels[]?`, `steps[]?`, `folderId?`, `allowDuplicateTitle?` | Duplicate guard, §3.5 |
| `update_test_case` | `idOrKey` + same optional fields, plus `folderId?` | Fully partial: absent fields are left alone. `TestCaseService.update` is currently a hybrid — `priority`/`status`/`labels`/`steps` are already null-guarded, but `title`/`description`/`preconditions` are set unconditionally, so omitting them **nulls** the stored value. The tool must read-then-merge rather than pass the request through. `folderId` has no equivalent on `UpdateTestCaseRequest` at all and routes to `TestCaseFolderService`'s move operation. Version history from PRD-011 records the change as usual |
| `create_test_cases_bulk` | `cases[]` (≤ `max-bulk-size`), `dryRun?` | Per-item outcome list `{index, status: CREATED\|SKIPPED\|ERROR, id?, key?, message?}`; partial success is normal, not an error |
| `create_test_suite` | `name`, `description?`, `testCaseIds[]?` | |
| `create_test_plan` | `name`, `description?`, `targetDate?` | No `assigneeId` — an agent should not assign work to a person |

Tool descriptions are written for the model, not for a human reader: each write tool's description
states the enum values verbatim (`Priority: LOW|MEDIUM|HIGH|CRITICAL`,
`TestCaseStatus: DRAFT|ACTIVE|DEPRECATED`) and instructs the agent to call `search_test_cases`
first. Thirteen tools is a deliberate ceiling — every tool costs context on every agent turn.

### 3.5 Duplicate guard

The failure mode that matters is an agent quietly re-creating cases that already exist. Before
inserting, `create_test_case` and `create_test_cases_bulk` check the project's existing case titles;
if a candidate match is found the create is **refused** with a structured result listing the
candidates (`key`, `title`, `score`) and the instruction to either call `update_test_case` or retry
with `allowDuplicateTitle: true`. Refusal rather than silent creation: an agent handed the candidate
list reliably picks the update path, and a human reviewing the transcript can see the decision.

This is **less reuse of PRD-007 than it looks**, and the PRD budgets for that. `SearchService`
searches four entity types across title/key/description, is capped at 50 hits, and its `SearchHit`
carries no score — `ts_rank` is used for `ORDER BY` only and never surfaced. `pg_trgm` is not
installed anywhere in the repo, so there is no similarity function to lean on either. The guard is
therefore a small purpose-built `TestCaseDuplicateDetector`:

- **Tier 1 (both vendors, always on):** normalised title equality — lowercase, collapse whitespace,
  strip punctuation — via a new indexed lookup on `test_cases`. Cheap, exact, catches the common
  case of an agent re-running the same prompt.
- **Tier 2 (Postgres only, opt-in):** `similarity()` above `app.mcp.duplicate-threshold` (default
  `0.85`), gated behind `app.mcp.fuzzy-duplicates` (default `false`) because it needs
  `CREATE EXTENSION pg_trgm`, which a managed Postgres may not grant. The migration attempts the
  extension and degrades to tier 1 with a startup log if it cannot.

On H2 (tests) only tier 1 exists — noted so nobody reads a green test suite as proof the fuzzy path
works.

### 3.6 Guardrails and audit (migration V51)

- **Write budget.** `app.mcp.max-writes-per-minute` per API key (default 60), enforced in-process
  with the same sliding-window approach as PRD-020's `LoginThrottleService`. Not the same bean:
  that one lives in `user.internal.services` (off-limits to the `project` module), has an
  email/IP-shaped API and hard-coded constants. A sibling `McpWriteThrottle` copies the pattern —
  ~30 lines — rather than forcing a premature extraction. Exceeding the budget returns a tool error
  telling the agent to slow down, not a transport failure.
- **Payload caps.** `max-steps-per-case` (100), `max-bulk-size` (50), plus the existing
  `@Size` constraints on titles and descriptions. Bean-validation failures map to readable tool
  errors listing the offending fields rather than a stack trace.
- **Audit.** `mcp_tool_invocations`: `id`, `api_key_id` (FK, `ON DELETE SET NULL`), `project_id`,
  `service_user_id`, `tool_name`, `arguments_json` (truncated to 4 KB), `outcome`
  (`SUCCESS|REFUSED|ERROR`), `error_message`, `created_entity_type`, `created_entity_id`,
  `duration_ms`, BaseEntity columns. Index `(project_id, created_at DESC)`. Retention is a
  scheduled purge after `app.mcp.audit-retention-days` (default 90). The `api_key_id` FK is
  `ON DELETE SET NULL` — deliberately unlike V39's `CASCADE` on `api_keys`, because deleting a key
  must not erase the record of what it did.
- Tool errors are returned as MCP tool results with `isError`, never as HTTP 5xx — an agent can act
  on the former and can only give up on the latter.

### 3.7 Frontend

Small, additive, all inside the existing admin API-key settings page:

- API-key create dialog gains a **role** select (Viewer / Tester, default Tester) and shows the
  MCP endpoint URL after creation.
- A copy-to-clipboard MCP client config block:
  ```json
  {"mcpServers": {"testmanagement": {
    "type": "http",
    "url": "https://<host>/api/mcp",
    "headers": {"Authorization": "Bearer tm_…"}
  }}}
  ```
- An **MCP activity** table (instance admin) over `GET /api/mcp-activity?apiKeyId=&page=`: time,
  key, tool, outcome, created entity link. Paged, PRD-002 conventions.
- Test cases created by an agent show `API key: <name>` as their author in the existing
  created-by/updated-by display — no new badge, the service-user display name carries it.

## 4. Edge Cases

- **Key revoked mid-session.** Stateless transport means the next tool call simply 401s; no session
  to invalidate. The service-user membership is deactivated at revocation, so even a race that
  slips past the filter hits `ForbiddenException` in `ProjectAccessService`.
- **Key role is VIEWER.** Write tools are still advertised (the tool list is static) but return a
  structured "insufficient role" error naming the required role. Alternative — filtering the
  advertised list per key — is rejected for v1: it makes the tool list vary by caller, which
  confuses client-side caching for little gain.
- **`folderId` from another project.** 404, not 403 — cross-project existence is not disclosed
  (PRD-021 discipline).
- **`create_test_cases_bulk` partially fails.** Each item commits independently; the result lists
  per-item outcomes. No all-or-nothing transaction, because an agent recovering from item 37 of 50
  is far better served by knowing which 36 landed.
- **Agent sends steps out of order / with gaps.** Order is taken from array position; any supplied
  index is ignored.
- **Concurrent update of the same case by agent and human.** Last write wins, as everywhere else in
  the tool; PRD-011 version history makes the overwrite recoverable.
- **`app.mcp.enabled=true` but no API keys exist.** Endpoint responds 401 to everything; the
  settings page shows a hint pointing at key creation.
- **Legacy project-less key present at startup** with `allow-legacy-global=false` → startup logs a
  clear error naming the key prefixes and the keys are rejected at request time. The app still
  starts; a self-hosted instance should not fail to boot over a deprecated credential.
- **Duplicate-guard false positive** on genuinely similar cases (parameterized variants, PRD-015) →
  `allowDuplicateTitle: true` is the documented escape, and the refusal message says so.
- **Postgres without `pg_trgm` grant** (managed/hosted DB) → the extension migration fails soft, the
  guard runs tier-1 only, and startup logs it once. It does not block boot.
- **An SSO user's email collides with a service-account email.** `@service.invalid` is a reserved
  TLD (RFC 2606) so no real IdP can assert it, and the service-account check in `SsoLoginService`
  refuses the link regardless.

## 5. Testing

- **Transport/protocol:** initialize → `tools/list` → `tools/call` round trip over streamable-HTTP
  against the running context; schema generated for every tool matches its record; unknown tool and
  malformed arguments produce `isError` results, not 500s.
- **Auth:** `X-API-Key` and `Authorization: Bearer tm_…` both accepted; JWT rejected on `/api/mcp`;
  API key rejected on `/api/**`; revoked key → 401; legacy project-less key → 401 with the flag off
  and accepted with it on.
- **Authorization (the point of §3.2):** a TESTER key can create in its own project and gets 404 for
  every id belonging to another project; a VIEWER key is refused on every write tool; a regression
  test asserts `ProjectRoleAspect` no longer fails open for an API-key principal (this is the test
  that would have caught the current gap); service users cannot log in via `/api/auth/login` and do
  not appear in user lists, assignee pickers or watcher notifications.
- **Tools:** each read tool's paging/filter parity with its REST equivalent; `update_test_case`
  partial-update semantics (absent field ≠ null field); bulk partial success; step ordering;
  duplicate guard hit, miss and override; enum coercion from lowercase input.
- **Guardrails:** write budget exhaustion returns a tool error and stops writing; oversized bulk and
  step counts rejected; audit row written for success, refusal and error paths with arguments
  truncated and no key material recorded.
- **Backfill:** runs once over pre-existing keys, idempotent on second boot, correct on an empty
  database.
- **Air-gap:** with `app.mcp.enabled=false` the endpoint returns **404, not 401** (this is the test
  that catches an unconditional `securityMatcher`), no Spring AI beans are registered, and the rest
  of the test suite is unaffected.
- **Duplicate guard vendor split:** tier-1 normalised-title equality is asserted on H2; the tier-2
  `pg_trgm` path is asserted only in a Postgres-tagged test and is explicitly skipped, not silently
  passed, when the extension is unavailable.
- **Frontend:** key dialog role select, config snippet rendering, activity table paging/filtering.

## 6. Effort & Risk

- **Effort:** ~1.5 weeks. Service-user auth rework + migrations + backfill ~3 days (the substantive
  half, and independently valuable). Spring AI wiring + 13 tools over existing services ~3 days.
  Guardrails, audit table and frontend ~2 days.
- **Risk:** Medium-low.
  - *Spring AI 2.0 maturity* — GA since June 2026 but young, and the annotation API stabilised only
    in 2.0-M6 (May 2026). Mitigation: tools are thin adapters over existing services, so a transport
    swap costs a config change, not a rewrite.
  - *The auth rework touches the login path and every audit column.* Mitigation: it is a separable
    first commit with its own tests, shippable and verifiable before any MCP code lands.
  - *Agent write quality* — the real product risk is not security but an agent filling a project
    with mediocre test cases. Mitigation: `DRAFT` default status, the duplicate guard, the audit
    trail, and VIEWER-by-default guidance for anyone experimenting.

## 7. Acceptance Criteria

- [x] With `app.mcp.enabled=true`, a client configured with a project API key lists the tools and
      creates a test case with steps in the right project. *(Asserted over real HTTP; not yet run
      against a live Claude client — see §8.)*
- [x] With `app.mcp.enabled=false` (default) `/api/mcp` returns 404 (not 401) and no Spring AI beans
      load.
- [x] Every API key resolves to a service user with a real `ProjectMember` role;
      `@RequireProjectRole` enforces for API-key callers and a regression test proves it no longer
      fails open (`ProjectAccessServiceTest#resolvedCallerOrNull_unresolvablePrincipal_throwsInsteadOfFailingOpen`).
- [x] A key scoped to project A cannot read or write anything in project B
      (`ApiKeyServiceUserApiTest`).
- [x] A VIEWER key is refused on the existing CI ingestion endpoints; a TESTER key is not.
      *(Extends to the MCP write tools when they land.)*
- [x] Service users cannot log in — by password **or by SSO email linking** — and do not appear in
      user lists, member lists, assignee pickers or notifications.
- [x] Legacy project-less keys are rejected by default, with `app.api-keys.allow-legacy-global` as
      the migration escape hatch.
- [x] `create_test_case` refuses a near-duplicate title with candidate keys, and proceeds with
      `allowDuplicateTitle: true`.
- [x] Write budget, bulk size and step caps are enforced and surfaced as tool errors.
- [x] Every invocation is recorded in `mcp_tool_invocations`, exposed by `GET /api/mcp-activity`.
      *(No frontend table yet — §8.)*
- [x] Backend and frontend test suites green; existing `/api/external/**` ingestion unchanged.

## 8. As Built (2026-08-09)

Shipped in two commits, §3.2 first as §6 proposed. **544 backend tests green, 51 frontend, frontend
builds clean.** Spring AI 2.0.0 resolved against Boot 4.1.0 / Java 25 with no version gymnastics,
and the annotation API (`@McpTool` / `@McpTool.McpAnnotations` / `@McpToolParam`) matched the
proposal. Thirteen tools, exactly the §3.4 list.

Deviations and things worth knowing:

- **Cross-project write, caught in review.** `create_test_suite` takes caller-supplied test case
  ids and `TestSuiteService.resolveTestCases` used `findAllById`, which knows nothing about
  projects. An agent could attach another project's cases to its own suite and read their titles
  back through `get_test_suite`. Now resolved project-scoped, and unknown ids fail the whole call
  instead of being silently dropped. **The REST API had the same hole**, so this fixes both.
- **Bulk creates commit per item, for real.** `create_test_cases_bulk` promises independent items.
  With a shared transaction it could not deliver: the first failure marks the transaction
  rollback-only, the loop continues, the tool reports 36 created, and then the commit throws and
  takes all 36 with it. Each item now goes through `McpTestCaseWriter` (`REQUIRES_NEW`), and the
  tool method is deliberately **not** `@Transactional` — a comment says so, because adding one
  would silently restore the bug.
- **`TestCaseService.update` now null-guards `title`/`description`/`preconditions`** like it already
  did the other fields, and `UpdateTestCaseRequest.title` lost its `@NotBlank` (blank is refused in
  the service instead; null now means "unchanged"). §3.4 originally specced a read-then-merge in
  the tool, which worked but made the agent the author of fields it never touched — it would
  silently revert a human's concurrent edit. Fixing the service removed the merge entirely.
- **Bean validation had to be added explicitly** (`McpValidator`). The `@Size`/`@NotBlank`
  constraints on the request records only fire because the *controllers* annotate the body
  `@Valid`; the tools build those records by hand, so a 5 000-character title would have reached
  the database as a JDBC error. §3.6 claimed this worked; it did not until it was wired.
- **The audit log stores argument shapes, not values** — `{title=<47 chars>, steps=<12 items>,
  priority=HIGH}`. §3.6 said "arguments, truncated", which would have copied every step's
  `testData` — the one field in this domain most likely to hold a test-account password — into an
  admin-global table with a longer retention than the test cases themselves. Enums, numbers,
  booleans and UUIDs are kept verbatim; free text is reduced to a length.
- **`mcp_tool_invocations` (V51) has no foreign keys.** It must outlive what it references, and it
  is written `REQUIRES_NEW` so a FK could not be satisfied from another uncommitted transaction.
  Growth is bounded by the retention purge, not by cascades.
- **Two Spring ordering traps**, both found by test rather than by reading:
  `@Order(HIGHEST_PRECEDENCE)` on the audit aspect breaks AspectJ argument binding (that slot
  belongs to `ExposeInvocationInterceptor`) — `HIGHEST_PRECEDENCE + 10` is the workable spot, still
  outside `@Transactional`. And `src/test/resources/application.yml` **shadows** rather than merges
  with the main one, so the `spring.ai.mcp.*` block had to be repeated there; without it every
  protocol assertion fails with a puzzling 404.
- `McpCallerHolder` (a `ThreadLocal`) carries the resolved caller from the tool to the auditor,
  cleared on both entry and exit. Nothing authorizes off it — `McpCallerContext` always re-resolves
  from the security context — so the worst a stale value could do is misattribute an audit row.
- `spring.ai.mcp.server.enabled` chains off `app.mcp.enabled` so there is one switch, and the
  security chain's `securityMatcher` widening is conditional on the same flag. Disabled means 404,
  asserted by `McpDisabledApiTest`.

### Verified live (2026-08-09)

Exercised against a deployed instance with a project-scoped TESTER key: handshake, `tools/list`
(all 13), `get_project`, `search_test_cases`, `list_test_case_folders`, `create_test_case` with
steps into a folder, `update_test_case`, `create_test_cases_bulk` (dry run then real, with a
duplicate correctly skipped against an existing case), `create_test_suite`, `create_test_plan`.
Auth boundaries confirmed on the deployment: no key → 401 with a `WWW-Authenticate` challenge, a
JWT-shaped bearer → 401, an unknown key → 401, and an API key against `/api/mcp-activity` → 403.

**One bug only a live client could find.** Spring AI generates schemas with victools, which marks
every property of a *nested* type required unless annotated `@Nullable` —
`@McpToolParam(required = false)` only reaches top-level method parameters. So `Step.expectedResult`
and `Step.testData` were mandatory, and every one of `BulkCase`'s eight fields was too. The client
was rejected at schema validation, above the Java methods the tool tests call, so nothing in the
suite could see it. Fixed, and `McpEndpointApiTest` now asserts the generated `required` arrays
directly.

**Still to do:** the frontend has no MCP activity table; the data and the admin endpoint
(`GET /api/mcp-activity`) exist, so it is a UI-only piece of work.

## 9. Future Work

- **Execution tools** — record results, start/complete runs. Natural v2 once authoring is proven.
- **OAuth 2.1 authorization** per the MCP spec, so an agent acts as the *human* who authorized it
  rather than as a shared project credential. This is the right long-term answer for multi-user
  instances and pairs with PRD-012's OIDC work; API keys remain the simple path for CI-adjacent use.
- **MCP resources and prompts** — expose a project's test-case corpus as resources, and ship
  prompts like "derive test cases from this requirement" so clients get a house style for free.
- **Per-key tool allow-list**, if the write budget turns out to be too blunt.
- **stdio wrapper** if clients without HTTP transport turn out to matter in practice.

---

**Sources for the Spring AI / MCP claims in §3.1:**
[MCP Server Boot Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) ·
[Stateless Streamable-HTTP MCP Servers](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-stateless-server-boot-starter-docs.html) ·
[Streamable-HTTP MCP Servers](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html) ·
[Model Context Protocol (MCP) overview](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)
