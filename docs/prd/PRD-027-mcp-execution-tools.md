# PRD-027 — MCP Execution & Defect Tools (Agent-Run Test Execution)

| | |
|---|---|
| **Status** | ✅ Implemented (2026-08-31) |
| **Author** | Engineering (Claude) |
| **Created** | 2026-08-31 |
| **Priority** | P2 — completes the agent loop opened by PRD-025 |
| **Target** | v2.3 |
| **Related** | PRD-025 (MCP server, §9 names this as the natural v2), PRD-005 (CI ingestion — the batch path this deliberately does not duplicate), PRD-015 (parameterized cases — the reason a result is not addressable by test case alone), PRD-010 (issue-tracker links — the *other* defect mechanism, §2 explains why it is not this one), PRD-003 (webhooks), PRD-021 (cross-project data safety) |

---

## 1. Summary

PRD-025 gave an agent a way to **write** tests and a way to **read** what happened when someone else
ran them. It cannot run them. `TestRunReadTools` is read-only by an explicit decision recorded in
its own javadoc — recording results was left to PRD-005's ingestion endpoints, on the grounds that
"a second way to write results would be a worse version of it."

That reasoning holds for the case it was written about and not for the one this PRD addresses.
PRD-005's endpoint reports a **finished** run: a CI job collects every result and POSTs them in one
shot. An agent executing tests interactively is in the opposite situation — it reads a case, runs
it, learns the outcome, and only then knows what the next case should be. It has one result at a
time and no idea how many more there will be. Asked to "work through the smoke suite and tell me
what breaks", an agent today can read the suite and can do nothing else: it cannot open a run, it
cannot record what it found, and when it finds a defect it cannot file one. The work exists only in
the chat transcript, which is exactly where test evidence must not live.

This PRD adds seven tools: three that let an agent open, populate and close a test run, and four
over the existing native `BugReport` entity so a failure becomes a tracked defect linked to the
result that produced it.

Building them turned up something that has to land first. PRD-025 §8 fixed an unscoped
`findAllById` in `TestSuiteService` and closed with the instruction to grep for the pattern before
trusting any service that takes caller-supplied child ids. That grep was never run. Running it
across every service turns up **nine more instances in five services**, all live through the REST
API today — and one of them is not a leak at all but a cross-tenant privilege escalation. §3.5 has
the list and the fixes.

The batch path is untouched. §2 states the boundary, and `TestRunReadTools`' javadoc is amended to
point at it rather than being quietly contradicted.

## 2. Goals & Non-Goals

**Goals**

- An agent holding a TESTER-scoped API key can execute a run end to end: open it seeded from a
  suite or a list of cases, record each result as it goes, and close it. The run is a first-class
  row afterwards — it appears in reports, in the traceability matrix, in flaky-test history, and in
  the pass-rate a human reads on Monday.
- A failure becomes a **filed defect** with reproduction steps, linked to the test result that
  produced it, not a paragraph in a chat log.
- Agent-recorded work is attributable and reversible: the run's executor is the key's service user,
  every call lands in `mcp_tool_invocations`, and the existing `TEST_FAILED` / `RUN_COMPLETED`
  webhooks fire exactly as they do for a human tester.
- Refusals are actionable. Every guard an agent can trip — bug reports disabled on the project, a
  case that is not in the run, an ambiguous parameterized result — returns a message naming the fix.

**Non-Goals**

- **Replacing PRD-005's batch ingestion.** `POST /api/external/projects/{ref}/test-runs` accepts a
  whole run of results in one request, authenticates with the same key, and is what CI uses. An
  agent that already holds every result should use it. No `record_test_results_bulk` tool ships
  here; §8 revisits that only if the singular tool turns out to be a bottleneck in practice.
- **Reopening a completed run.** `TestRunService.update` allows `COMPLETED → IN_PROGRESS` with a
  mandatory `reopenReason`. Reopening someone's signed-off run is a human judgment call; an agent
  that needs to re-test creates a new run, which is also the honest record of what happened.
- **Editing a bug report's body after filing.** `UpdateBugReportRequest` is a full replace by
  design — the SPA sends the whole object, and omitting `assigneeId` is how a human *clears* an
  assignee. A partial-update tool over it would need the read-then-merge that PRD-025 §8 removed
  from `update_test_case`, making the agent the author of fields it never touched. Status changes,
  which is the realistic agent need, get their own tool. §4 covers the workaround.
- **Assigning bugs or runs to people.** No `assigneeId` on any tool, per PRD-025 §3.4. The executor
  is the agent's own service user, which is attribution, not assignment.
- **The external issue-tracker path** (PRD-010, `IssueLinkService`). Filing into GitLab or Forgejo
  needs an admin-configured tracker and only two providers have adapters, so it cannot be the
  primary defect surface for a self-hosted instance. The native `bug_reports` table works with no
  external dependency. §8 keeps `link_issue` as follow-on work; the two are complementary, and a
  bug report carries `testResultId`, which is what `IssueLinkService` keys off.
- **Deletion.** Still no delete tools, per PRD-025.

## 3. Proposed Design

### 3.1 Placement and shape

Two new bean classes in `project/internal/mcp/`, alongside the existing five:

- `TestRunWriteTools` — `create_test_run`, `record_test_result`, `complete_test_run`
- `BugReportTools` — `create_bug_report`, `list_bug_reports`, `get_bug_report`,
  `change_bug_report_status`

`TestRunReadTools` keeps `list_test_runs` and `get_test_run` and gains one output field (§3.2.2).
Everything reuses PRD-025's infrastructure unchanged: `McpCallerContext.requireWriter()` for the
role gate, `McpWriteThrottle` before each write, `McpValidator` for bean validation (which does not
fire on this path by itself), `McpToolException` for anything the agent can fix, and the
`McpToolAuditor` aspect, which picks up new `@McpTool` methods automatically. No new module, no new
config chain, no migration.

**Tool count, and a correction.** This PRD's first draft said the surface goes "13 → 20", taking
PRD-025 §3.4's thirteen at face value. Counting `@McpTool` annotations says otherwise: **the surface
is already 21**, and these seven take it to **28**. Thirteen was true the day PRD-025 shipped; the
folder tools and PRD-014's traceability tools were added afterwards without anyone revisiting the
"deliberate ceiling", which is how ceilings usually stop being ceilings.

That makes the context cost a real concern rather than a rhetorical one — 28 tool descriptions ride
along on every agent turn. Seven is still the minimum that closes the execution loop, and §2's
non-goals are the trims, so this PRD does not shrink the number. But the **per-key tool allow-list**
from PRD-025 §9 has stopped being a nice-to-have: it is now the mechanism that keeps an
authoring-only key from paying for execution tools it will never call, and it should be scheduled
rather than left in a backlog. §9 says so.

### 3.2 Execution tools

#### 3.2.1 `create_test_run`

| Argument | | |
|---|---|---|
| `name` | required | max 255 |
| `environment` | optional | free text, e.g. `staging` |
| `testCaseIds[]` | optional | UUIDs |
| `testSuiteId` | optional | seed from a suite instead |
| `testPlanId` | optional | associate the run with a plan |

Returns `{id, key, name, status, totalResults}` — the `key` (`PROJ-Run-7`) is what an agent quotes
back to a human.

Two things the underlying service does not do, which the tool must:

- **A suite does not populate a run.** `TestRunService.create` takes `testCaseIds` and nothing else;
  `testPlanId` only sets an association. Seeding from a suite therefore means resolving
  `TestSuiteService.findById(projectId, suiteId)` — already project-scoped — to its case ids and
  passing those. Worth doing in the tool rather than the service: "run this suite" is the request an
  agent actually makes, and making it two round trips invites the agent to invent its own id list.
- **`testCaseIds` are caller-supplied child ids, and they are currently resolved unscoped.**
  `TestRunService.create` calls `testCaseRepository.findAllById(request.testCaseIds())` — the exact
  line PRD-025 §8 fixed in `TestSuiteService.resolveTestCases` and, it turns out, did not sweep for.
  This is a live cross-project read through the REST API today; §3.5 is a prerequisite for this tool,
  not a tidy-up alongside it.

Seeding pre-creates one `PENDING` `TestResult` per case, plus one `PENDING` `StepResult` per step,
and expands parameterized cases (PRD-015) into one result per parameter set. That expansion is why
§3.2.2 exists.

The run's **executor is set to the caller's service user**. PRD-025 refused to let an agent set an
assignee on a plan; this is not that. Nobody is being given work — the agent is recording that it
did the work itself, and a run whose executor column is empty while an API key fills it with results
is a worse record than one that names the key.

#### 3.2.2 `record_test_result`

| Argument | | |
|---|---|---|
| `runId` | required | |
| `resultId` *or* `testCaseId` | one required | see below |
| `status` | required | `PASSED \| FAILED \| BLOCKED \| SKIPPED` |
| `comment` | optional | what the agent observed |
| `defectLink` | optional | free-text URL |

This is an **upsert against the seeded result**, not an append, and the distinction is the whole
tool. `TestRunService.addResult` creates a *new* result row for a case without checking whether one
already exists, so calling it on a seeded run leaves the original `PENDING` row in place beside the
new one: the run then reports 11 results for 10 cases, one of them permanently pending, and every
pass-rate downstream is wrong. `addResult` also skips step-result creation, so an appended result
has no steps while its seeded neighbours do. The tool therefore resolves the existing result and
calls `updateResult`, falling back to `addResult` only when the case genuinely is not in the run —
the ad-hoc exploratory case, which is real and stays supported.

**Resolution by test case is ambiguous under PRD-015.** A parameterized case expands into one result
per parameter set, so `testCaseId` may match three rows. The tool refuses that rather than guessing,
naming the candidate result ids and their parameter set names. For this to be answerable,
**`get_test_run` must return each result's `id` and `parameterSetName`**, which today it does not —
`McpDtos.TestResult` carries `testCaseId`, title, status, comment and `defectLink` only. Adding
them is a two-field change and is a prerequisite, not a nicety: without it there is no way for an
agent to comply with the refusal it just received.

**Status transition.** A run seeded and then filled with results while still `PLANNED` is a lie —
`startTime` stays null and the run never appears as in-flight. The first `record_test_result` on a
`PLANNED` run therefore transitions it to `IN_PROGRESS`, which sets `startTime` and publishes
`RUN_STARTED`. This is deliberately implicit rather than a fourth `start_test_run` tool: an agent
that must remember a ceremonial call will eventually not, and the failure is silent.

Recording `FAILED` publishes `TEST_FAILED` as it does for a human tester, so watcher notifications
(PRD-006) work with no change.

#### 3.2.3 `complete_test_run`

`{runId, status?}` where status is `COMPLETED` (default) or `ABORTED`. Both set `endTime`;
`COMPLETED` also sets `completedBy`, clears `reopenReason` and publishes `RUN_COMPLETED` /
`RUN_FAILED`. `ABORTED` is offered so an agent that hits a blocker mid-run can close the record
honestly instead of leaving it open forever.

The tool returns the run's final counts, which gives the agent its own summary to report back
without a second `get_test_run` call.

Completing a run that still holds `PENDING` results is **allowed, with the count in the response**.
Refusing would strand an agent that was legitimately blocked, and `TestRunStatus.COMPLETED` already
coexists with pending results everywhere else in the tool.

### 3.3 Bug report tools

`BugReportService` already does the right things — forces `status = OPEN` on create, audits, and
publishes `BUG_REPORT_CREATED` — so these are thin.

| Tool | Arguments | Notes |
|---|---|---|
| `create_bug_report` | `title`, `priority`, `description?`, `stepsToReproduce?`, `expectedBehavior?`, `actualBehavior?`, `environment?`, `testResultId?`, `testRunId?`, `allowDuplicateTitle?` | No `assigneeId`. Duplicate guard, §3.4 |
| `list_bug_reports` | `status[]?`, `priority[]?`, `page?`, `size?` | Summaries only — title, status, priority, linked run/case |
| `get_bug_report` | `id` | Full body incl. repro/expected/actual |
| `change_bug_report_status` | `id`, `status`, `reason` | `reason` is mandatory — `ChangeBugStatusRequest` already requires it, and it lands in the audit log as `OPEN -> RESOLVED: <reason>` |

Tool descriptions instruct the agent to pass `testResultId` from `get_test_run` whenever the bug
came out of a run, because that link is what makes the bug visible from the failure and what a
future `link_issue` (§8) would key off.

**`Project.bugReportsEnabled` defaults to `false`** and every `BugReportService` method except
`findByAssignee` throws `ForbiddenException("Bug reports are not enabled for this project")`. Left
alone, an agent asked to file a bug on a fresh project gets an opaque refusal and will retry it.
All four tools therefore catch that and re-throw an `McpToolException` naming the fix: a project
admin enables bug reports in project settings (`PUT /api/projects/{id}/settings/bug-reports`), and
the agent cannot do it itself because the toggle is ADMIN and API keys top out at TESTER. The
message says both halves, so the agent reports the blocker instead of looping.

**Paging note.** `BugReportService.findByProject` is unpaged and returns the whole project. The tool
filters and slices in memory against PRD-002's conventions. That is a full load per call, and it is
the right trade for now: `bug_reports` is a small table on a self-hosted instance, and adding a
filter/specification layer for one tool would be the premature abstraction CLAUDE.md warns about.
The response's `totalElements` is honest either way, and the day a project has thousands of bugs the
fix is a paged repository query behind an unchanged tool signature.

### 3.4 Duplicate guard on bugs

Same failure mode as PRD-025 §3.5, and worse in practice: an agent that runs the same suite nightly
will file the same bug every night. Before inserting, `create_bug_report` normalises the candidate
title (lowercase, collapse whitespace, strip punctuation — the existing
`TestCaseDuplicateDetector` normalisation, extracted to a shared helper) and compares it against the
project's **open** bugs, meaning `OPEN` and `IN_PROGRESS`. A match refuses the create and returns
the candidates as `{id, title, status}` with the instruction to either call
`change_bug_report_status` on the existing one or retry with `allowDuplicateTitle: true`.

`RESOLVED`, `CLOSED` and `WONTFIX` are deliberately excluded: a regression of a bug that was closed
last month is a genuinely new report, and refusing it would hide the most interesting signal the
suite produces.

No migration and no index — the comparison runs over the list `findByProject` already loads, per
§3.3. The tier-2 `pg_trgm` path from PRD-025 §3.5 is not extended to bugs; bug titles are free prose
where test case titles are formulaic, so fuzzy matching there would mostly produce false positives.

### 3.5 Unscoped child-id lookups (prerequisite fix, affects REST too)

PRD-025 §8 fixed `TestSuiteService.resolveTestCases`, which used `findAllById` and let a suite be
built from another project's cases, and closed with the lesson: *any service method taking
caller-supplied child ids needs a project-scoped lookup — grep for `findAllById` before trusting
one.* That grep was never run across the rest of the codebase. Running it now turns up **five more
instances in the two services this PRD builds on**, all reachable through the REST API today and all
of them directly under the new tools:

`TestRunService.create`:

```java
List<TestCase> testCases = testCaseRepository.findAllById(request.testCaseIds());   // unscoped
TestPlan testPlan = testPlanRepository.findById(request.testPlanId())...            // unscoped
User executor = userService.findEntityById(request.executorId())...                 // any user
```

`BugReportService.create` (and `update`, identically):

```java
bugReport.setTestResult(testResultRepository.findById(request.testResultId()).orElse(null));
bugReport.setTestRun(testRunRepository.findById(request.testRunId()).orElse(null));
bugReport.setAssignee(userService.findEntityById(request.assigneeId()).orElse(null));
```

Three distinct defects fall out:

1. **Cross-project read.** A run in project A can be seeded with project B's test cases, and
   `get_test_run` / `GET /api/projects/{a}/test-runs/{id}` then returns `testCaseTitle` for each of
   them. Likewise a bug in A can carry B's `testResultId` and `testRunId`, and `BugReportResponse`
   echoes back `testCaseTitle` and `testRunName`. A caller with access to A alone learns the names
   of B's cases and runs — the PRD-021 discipline, violated in four places.
2. **Cross-project write.** A run in A can be attached to a test plan in B, where it then counts
   toward B's `getSummary` pass rate. That is worse than a read: it silently corrupts a number
   someone else reports on.
3. **Silent loss.** `findAllById` drops unknown ids without complaint, so a run seeded with ten ids
   of which three are typos is created with seven results and a `200`. `BugReportService`'s
   `.orElse(null)` does the same for a single link. Nothing tells the caller.

Fix, following the `resolveTestCases` precedent already in the tree:

- test cases → `testCaseRepository.findByIdInAndProjectId` (**already exists**, added by the PRD-025
  fix, and simply not used here), with unknown ids failing the whole call and naming themselves
- test plan, test result, test run → project-scoped finders; `TestResultRepository` has none today
  and needs one that joins through `testRun.project`
- executor and assignee → resolved through a project-membership check, not `findEntityById`; a user
  who is not a member of the project should not be settable as either

Misses throw `ResourceNotFoundException` rather than nulling or dropping. No behavioural change for
the SPA, which only ever sends ids it read from the same project — asserted in §5 against the SPA's
actual payloads.

### 3.5.1 As built — the sweep found nine, not five

Running the grep properly across every service turned up more than the two services this PRD builds
on. The full list, all fixed:

| Service | Method | Unscoped id | What it allowed |
|---|---|---|---|
| `TestRunService` | `create` | `testCaseIds` | seed a run with another project's cases; titles read back |
| `TestRunService` | `create` | `testPlanId` | run counts toward another project's plan pass rate |
| `TestRunService` | `create` | `executorId` | any user in the instance as executor |
| `TestRunService` | `update` | `testPlanId` | same plan corruption, via `PUT` |
| `TestRunService` | `addResult` | `testCaseId` | foreign case's title into this run's report |
| `TestRunService` | `setExecutor` | `executorId` | as above |
| `BugReportService` | `create` + `update` | `testResultId`, `testRunId`, `assigneeId` | foreign `testCaseTitle` / `testRunName` echoed back |
| `TestPlanService` | `create` + `update` | `assigneeId` | plan appears in a non-member's "My queue" |
| `CiIngestionService` | `ingest` | `testPlanId` | CI key files its run against another project's plan |
| **`ProjectMemberService`** | **`updateRole`, `removeMember`** | **`memberId`** | **see below** |

The last one is the serious one and it is not a leak. Both methods accepted a `projectId` and never
read it — the parameter was declared and dropped. `ProjectMemberController` authorizes with
`requireProjectAdmin(projectId)`, so being an admin of **any** project passed the gate, after which
the service acted on whatever membership row the id named. An admin of project A could promote an
account to ADMIN on project B, or delete a member's access to B outright, and `updateRole`'s
response returned that user's email and display name on the way past. That is cross-tenant
privilege escalation, and it is unrelated to MCP except that the same sweep found it.

`TestCaseService.bulkUpdateStatus` and `bulkDelete` also used `findAllById`, but safely — they
filter by project and throw if the counts differ. They were still converted to the scoped query, so
that the guarantee lives in the lookup rather than in a size check thirty lines away. That is the
arrangement that has now failed three times.

### 3.5.2 As built — the static rule, corrected

§3.5 originally proposed an ArchUnit rule failing "any service method that resolves a
caller-supplied id through a bare `findById`". **That rule cannot be written honestly**, and the
attempt is worth recording so nobody proposes it again. Whether a `findById` is safe depends on what
happens to its *result* — `findById(id).filter(x -> x.getProject()...)` is correct and is the idiom
used throughout these services, while the identical call without the filter is the bug. That is a
dataflow property; ArchUnit sees static structure. Broad enough to catch the real cases means
flagging dozens of correct ones and acquiring a suppression list; narrow enough to stay quiet means
catching nothing.

A companion rule on `userService.findEntityById` was written and deleted for the same reason: nine
hits, **zero** of them bugs — three were the `requireProjectMember` helpers that exist to make the
call safe, two resolved the authenticated actor to stamp `completedBy`, the rest were display-name
lookups. A rule that is wrong every time it fires teaches people to add allowlist entries without
reading.

What ships instead is `UnscopedLookupGuardTest`, which enforces one thing with **no allowlist**:
`findAllById` does not appear in any project service. It has no project-aware form, a scoped
alternative already exists, and it is the specific call behind both prior incidents. The
assignee/executor cases are covered behaviourally in `ProjectScopedChildIdApiTest` instead, which is
where a new child-id parameter should acquire its assertion. A narrow rule that is always right beats
a broad one that is usually wrong.

### 3.6 Guardrails

Every recorded result is a write against `McpWriteThrottle`. That matters more than it did for
authoring: a 50-case run is 52 writes, against a `max-writes-per-minute` default of **60** sized for
a human-paced authoring loop. An agent executing at machine speed will hit it mid-run and — worse
than failing — will have a half-recorded run.

- **Default raised to 120.** It is a runaway-loop guard, not a security boundary (the API key's
  project role is that), and 120 still bounds an overnight accident to a level a human notices.
- The refusal message gains the remaining seconds in the window, so an agent can wait rather than
  abandon the run: "write budget exhausted, 23s until the window resets."

Payload caps are unchanged. `max-bulk-size` does not apply — nothing here is bulk.

**Audit.** New tools are picked up by `McpToolAuditor` automatically, and argument *shapes* are
recorded rather than values (PRD-025 §8), which matters here too: `stepsToReproduce` and `comment`
routinely hold environment details and occasionally credentials.

One accepted gap: `TestRunService.addResult` and `updateResult` take no `userId` and write no
`AuditService` row, so agent-recorded results appear in `mcp_tool_invocations` but not the project
audit log. Run creation and completion do audit. Adding result-level project auditing is a change to
the human path as much as the agent one and is out of scope; the MCP audit trail is complete on its
own.

### 3.7 Frontend

Nothing new. Runs created by an agent appear in the existing run list with `API key: <name>` as the
executor via the service-user display name (PRD-025 §3.7); bug reports appear in the existing bug
list with the same reporter rendering. The MCP activity table remains the outstanding UI work from
PRD-025 §8 and is not in this PRD's scope.

`docs/MCP_SETUP.md` needs its tool table extended from 13 to 20 and a short "executing a run"
walkthrough, since the ordering (`create` → `record` × n → `complete`) is the one thing a reader
cannot infer from the tool list.

## 4. Edge Cases

- **Bug reports disabled on the project.** Actionable refusal naming the setting and the fact that
  only a project admin can flip it (§3.3). Not a silent no-op.
- **`testCaseId` matches several results** (parameterized case, PRD-015). Refused with the candidate
  result ids and parameter set names; `resultId` is the escape. This is why `get_test_run` gains
  `id` and `parameterSetName`.
- **`record_test_result` for a case not in the run.** Falls back to `addResult`, creating an ad-hoc
  result. The response flags `added: true` so the agent can see it did not do what it probably
  meant, and the result has no step results — noted in the tool description.
- **Recording into a `COMPLETED` run.** Refused, naming the run key and suggesting a new run.
  Silently reopening someone's signed-off run is the worst available option.
- **Completing an already-completed run.** Idempotent no-op returning the current counts, not an
  error — an agent retrying after a dropped response should not be punished.
- **`testSuiteId` and `testCaseIds` both supplied.** Union, deduplicated. Refusing would be
  pedantic; the agent's intent is unambiguous.
- **Empty run** (neither ids nor suite, or an empty suite). Allowed — an ad-hoc run filled by
  `record_test_result` is a legitimate exploratory pattern.
- **Suite or plan id from another project.** 404, not 403 — cross-project existence is not
  disclosed (PRD-021).
- **Some `testCaseIds` unknown.** The whole call fails, naming the offending ids, rather than
  creating a partial run. Today it silently creates one — an agent that mistypes three of ten ids
  gets a seven-case run and a success, and reports the suite as green.
- **`testResultId` on a bug report pointing at another project's result.** 404 after §3.5, where it
  currently succeeds and leaks a title.
- **Duplicate-guard false positive** on a genuinely recurring-but-new bug →
  `allowDuplicateTitle: true`, and the refusal message says so.
- **Write budget exhausted mid-run.** The run is left `IN_PROGRESS` with partial results, which is
  accurate. The refusal names the reset time so the agent can resume rather than restart.
- **A human edits the run while the agent is recording.** Last write wins, as everywhere else.
- **VIEWER key.** Refused on all seven tools with the role-naming message from
  `McpCallerContext.requireWriter()`.
- **`app.mcp.enabled=false`.** No endpoint at all — 404, unchanged.

## 5. Testing

- **Tools:** `create_test_run` seeded from ids, from a suite, from both, and empty; executor set to
  the service user; run key format. `record_test_result` updates the seeded `PENDING` row rather
  than appending — asserted by result **count**, which is the assertion that catches a regression to
  `addResult`. Ad-hoc fallback path. `PLANNED → IN_PROGRESS` on first result with `startTime` set.
  `complete_test_run` for both statuses, idempotent re-completion, pending count surfaced.
- **Parameterized ambiguity:** a case with three parameter sets refuses resolution by `testCaseId`
  and succeeds by `resultId`; `get_test_run` returns both new fields.
- **Bug tools:** create with and without links; `bugReportsEnabled=false` produces the actionable
  refusal on all four tools, not a `ForbiddenException` leaking through; status change writes the
  `OLD -> NEW: reason` audit row; list paging and filtering.
- **Duplicate guard:** exact-after-normalisation hit against an `OPEN` bug refuses; the same title
  against a `CLOSED` bug proceeds; `allowDuplicateTitle` overrides.
- **Cross-project (§3.5), asserted through the REST API as well as the tools**, since every one of
  these holes exists in both: a run cannot be seeded with another project's `testCaseIds` or
  attached to another project's `testPlanId`; a bug report cannot take another project's
  `testResultId` or `testRunId`; neither executor nor assignee can be set to a non-member. A
  mistyped id 404s naming itself instead of being silently dropped or nulled — including the
  partial case, ten ids of which three are unknown, which currently creates a seven-result run.
  A regression test asserts a run attached to a foreign plan no longer moves that plan's pass rate.
- **The static check** (§3.5.2): `UnscopedLookupGuardTest`, a source scan with no new dependency,
  asserting `findAllById` appears in no project service. No allowlist.
- **Proven red before green.** The whole point of a regression test for a live bug is that it fails
  against the unfixed code, so this was demonstrated rather than assumed: with the three service
  files reverted, 9 of the 11 original assertions fail, and the 2 that stay green are exactly the
  "own-project ids still work" cases, which must pass both ways. *(Done — see §8.)*
- **Authorization:** VIEWER key refused on every new tool; TESTER key confined to its own project
  for every id argument; a key scoped to project A cannot record into project B's run.
- **Guardrails:** budget exhaustion returns a tool error naming the reset time and stops writing;
  audit rows written for success, refusal and error, with `comment` and `stepsToReproduce` reduced
  to lengths rather than stored.
- **Webhooks:** `RUN_STARTED` on implicit transition, `TEST_FAILED` on a failed result,
  `RUN_COMPLETED` / `RUN_FAILED` on completion, `BUG_REPORT_CREATED` on file — the same events a
  human tester produces.
- **Protocol:** `tools/list` advertises the execution and bug tools and still advertises nothing
  destructive (no delete, no reopen); every nullable field of every new nested
  output record is `@Nullable`, asserted against the generated `required` arrays as
  `McpEndpointApiTest` already does — this is the bug class that only a live client found last time.
- **Regression:** the existing 13 tools and `/api/external/**` ingestion are unchanged.

## 6. Effort & Risk

- **Effort:** ~5 days. §3.5 with its REST-level tests and the ArchUnit rule ~1.5 days, and it goes
  **first**, as its own commit — it is independently valuable, it is a live data-safety bug, and
  PRD-025 proved the pattern of shipping the authorization fix ahead of the feature that motivated
  it. Execution tools + the `get_test_run` field addition ~1.5 days. Bug tools + duplicate guard
  ~1 day. Tests and `MCP_SETUP.md` ~1 day.
- **Risk:** Low-medium.
  - *§3.5 is a behaviour change on live REST endpoints*, from "silently drop" to 404. Something may
    depend on the sloppiness — an importer passing a stale id list, most plausibly. Mitigation: it
    ships as its own commit with the SPA's real payloads asserted, so a regression is attributable
    and revertable without touching the tools.
  - *The upsert semantics in `record_test_result` are the whole correctness story.* Getting it wrong
    silently corrupts pass rates rather than failing loudly. Mitigation: the count-based assertion
    in §5, and a comment in the tool explaining why `addResult` is not the default path — the same
    device that keeps `create_test_cases_bulk` from being made `@Transactional` again.
  - *Tool-surface growth.* 28 tools is more than double the ceiling PRD-025 set, and most of that
    overrun predates this PRD (see §3.1). Mitigation: the non-goals in §2 are the trims, and the
    per-key allow-list is promoted from backlog to scheduled work.
  - *Product risk is agent judgment, not security* — an agent marking things `PASSED` it did not
    really verify. Mitigation is the same as PRD-025's: the audit trail, the service-user
    attribution on every result, and the recommendation to start with a key on a scratch project.

## 7. Acceptance Criteria

- [x] An agent with a TESTER key creates a run from a suite, records a pass and a failure, files a
      bug linked to the failed result, and completes the run — with no human step in between.
- [x] The resulting run holds exactly one result per seeded case; no duplicate or orphaned `PENDING`
      rows.
- [x] `get_test_run` returns `id` and `parameterSetName` per result, and a parameterized case is
      addressable by `resultId`.
- [x] Filing on a project with bug reports disabled returns a refusal naming the setting and the
      role needed to change it.
- [x] `create_bug_report` refuses a title matching an open bug and proceeds with
      `allowDuplicateTitle: true`.
- [x] No caller-supplied child id crosses a project boundary, through the MCP tools **or** the REST
      API: not a run's `testCaseIds` or `testPlanId`, not a bug's `testResultId` or `testRunId`, not
      an executor or assignee who is not a member. Unknown ids 404 naming themselves instead of
      being dropped or nulled.
- [x] An admin of one project cannot change roles or remove members in another.
- [x] The §3.5 regression tests fail against the pre-fix tree and pass after — demonstrated, not
      asserted.
- [x] `findAllById` appears in no project service, enforced with no allowlist.
- [x] A VIEWER key is refused on all seven tools; a key scoped to project A cannot touch project B.
- [ ] Agent-recorded failures fire `TEST_FAILED` and reach watchers exactly as human-recorded ones
      do. *(Holds by construction — the tools go through `TestRunService.updateResult`, which
      publishes it — but no test asserts it end to end. Outstanding.)*
- [x] Every invocation is recorded in `mcp_tool_invocations` with free text reduced to lengths.
- [x] `tools/list` advertises the seven new tools with schemas whose `required` arrays match the
      records, and still advertises no delete or reopen tool.
- [x] Backend suite green (625/626 — the failure is the stale-artifact `MigrationVersionsTest`,
      §8); `/api/external/**` ingestion and the existing 21 tools unchanged. **Frontend not
      re-run** — nothing in it was touched, but the claim is unverified.

## 8. As Built — §3.5 (2026-08-31)

The prerequisite shipped first, as §6 planned. **602 backend tests green** (the 603rd,
`MigrationVersionsTest`, fails on a stale `V38__add_search_vectors.sql` left in `target/classes`
from before commit `fab8262` — the source tree has no duplicate; `./mvnw clean` clears it).

- **Nine instances across five services**, not the five in two that the draft predicted — §3.5.1 has
  the table. `ProjectMemberService` was the outlier in kind as well as severity: a cross-tenant
  privilege escalation reachable by any project admin, found by the same sweep and unrelated to MCP.
- **Two were found by restoring the fix, not by writing it.** After proving the tests red the
  restored files were grepped again, and `TestRunService.update` and `addResult` — same class, four
  hundred lines from the call site already fixed — still had theirs. The sweep that found the last
  two (`CiIngestionService`, `ProjectMemberService`) was run only after that.
- **The ArchUnit rule §3.5 promised was written, tried, and deleted** in favour of something
  narrower that is always right. §3.5.2 records why, because the proposal looked reasonable on paper
  and it is the kind of thing that gets proposed again.
- **`ProjectScopedChildIdApiTest`** (15 tests) drives everything through the REST endpoints rather
  than the services, because a mocked-repository unit test passes just as happily against the broken
  code. The CI-ingestion case lives in `CiIngestionApiTest`, which already had the API-key harness.

## 8.1 As Built — the tools (2026-08-31)

**625 backend tests green** (the 626th is the stale-artifact `MigrationVersionsTest` above). Seven
tools as specced, in `TestRunWriteTools` and `BugReportTools`. The frontend was not touched and its
suite was not re-run.

The count-based assertion §5 asked for was proven to work the same way §3.5's were: with
`record_test_result` forced down the naive append path, `recordingFillsInTheSeededResultRather...`
reports **three results for one test case** — the orphaned PENDING plus two appends. A status
assertion passes against that broken version, because the last appended row does have the right
status. Only the count catches it.

**A review pass found eight defects in the first draft of these two files.** Worth recording,
because several are the same shape as bugs this codebase has already fixed once:

1. **Re-recording wiped the comment.** `UpdateTestResultRequest` has no absent-versus-null
   distinction and `updateResult` assigns all three fields, so a retry carrying only a status
   cleared the failure evidence — on a tool that advertises `idempotentHint` and tells the agent to
   retry. Exactly the bug PRD-025 §8 fixed in `update_test_case`, reintroduced through a different
   door. Absent now means unchanged.
2. **`resultId` and `testCaseId` disagreeing was not checked**, so a stale `resultId` recorded an
   outcome against a different case and returned success naming it.
3. **`complete_test_run` swallowed the terminal-state conflict.** Asking to abort a completed run
   returned a cheerful `COMPLETED` and no error. Idempotent now means "already what you asked for";
   the opposite terminal state is refused. Completing a `PLANNED` run also went through
   `IN_PROGRESS`, since `startTime` is only stamped on that edge and the run was otherwise ending
   before it began.
4. **The implicit start wrote a `null` actor** to the audit row.
5. **Status changes echoed `name` and `environment` back**, reverting a human's concurrent rename.
   Fixed in `TestRunService.update` rather than the tool — null now means unchanged there too, and
   `UpdateTestRunRequest.name` loses its `@NotBlank` exactly as `UpdateTestCaseRequest` did under
   PRD-025 §8. **This fixes the REST path as well**, where the same lost update existed.
6. **`listBugReports` paging overflowed to a negative offset** on a large page number and threw out
   of `subList`. `page` comes from a model, which is the input most likely to be nonsense.
7. **The bug-reports-disabled translation caught `ForbiddenException` by type**, so any future
   authorization failure inside `BugReportService` would have been reported to the agent as a
   settings problem and audited `REFUSED` instead of `ERROR`. It matches the condition now.
8. **`McpToolAuditor` did not recognise what the new tools create**, so every `create_test_run` and
   `create_bug_report` wrote an audit row with a null entity link — the aspect covers new tools
   automatically, but its result-to-entity mapping does not, and the row looks fine without it.

Two smaller ones are accepted rather than fixed, and are noted here so they are not rediscovered as
surprises: an appended ad-hoc result has no step results (inherited from the REST `addResult`; the
tool response flags `added` so the agent can tell), and `updateResult` sets only the parent status,
so a human later editing one step recomputes the parent from its still-PENDING siblings and can
overwrite what the agent recorded. The second is pre-existing behaviour of the human path and wants
its own change.

Also corrected: the webhook-ordering claim in the first draft. Both events publish `AFTER_COMMIT`
onto an async pool, so starting the run before recording orders the *publishing*, not the delivery.
The transaction reasoning behind that ordering does hold, and the `@Transactional` annotations are
load-bearing because of it.

## 8.2 As Built — refusal wording (2026-09-01)

Found by test-driving the tools against a live instance rather than by any test. Every refusal
reached the client as:

```
Error invoking method: recordTestResult
Pass either testCaseId or resultId.
```

The second line is ours and the `isError` flag was correct, so §3.6's contract held on paper. The
first line is Spring AI's and it undoes the intent: "Error invoking method" reads as *this tool is
broken*, which is the one conclusion an agent must not draw from a deliberate refusal that is
telling it exactly what to do instead. A capable model looks past it; a smaller local model — the
kind already inclined to work around a tool rather than fix its own call — may reasonably stop using
it. The line carries no information either, since the agent knows what it called.

`AbstractSyncMcpToolMethodCallback.createSyncErrorResult` builds the text as
`e.getMessage() + lineSeparator() + cause.getMessage()`, and `createErrorMessage` is `protected` —
so overriding it looks like the answer, until you find `SyncMcpToolMethodCallback` is `final`.

What is reachable is the specification. The stateless server takes its tools as
`List<SyncToolSpecification>` **beans**, and a specification is a record of a tool and a call
handler, so `McpRefusalMessageCleaner` wraps the handler in a `BeanPostProcessor` and trims the
framing off failed results. The annotation-driven schema generation PRD-025 §3.1 valued is
untouched.

Two things worth carrying forward:

- **The framing names the Java method (`createTestCase`), not the MCP tool (`create_test_case`).**
  The first version checked the text against the tool name from the specification — a
  guard against stripping something unrelated — and therefore matched *nothing*. Every unit test
  passed, because they were written with the same wrong assumption as the code: they supplied a name
  that agreed with itself. Only the end-to-end assertion over a real `tools/call` caught it, which
  is why that test exists and why its javadoc says so. The guard is now that the framing must name a
  bare identifier, which does not depend on knowing which name it is.
- **A `@Bean` method sharing its `@Configuration` class's name** collides with the component-scanned
  definition and the context refuses to start. Also caught end-to-end, immediately.

Trimming does not try to tell a refusal from a genuine fault: by the time the result exists the
exception is gone. Trimming is right either way — for a refusal it leaves the instruction, for a
fault it leaves the underlying message, which beats the name of a method the caller already knows.
`isError` still says it failed and `mcp_tool_invocations` still records REFUSED against ERROR.

**637 backend tests green.**

## 8.3 As Built — friction found by a real agent (2026-09-01)

A local model working a project through the tools filed a report. Four items, triaged rather than
taken at face value — two of the diagnoses did not survive checking.

**Accepted and fixed.**

- **`get_test_run` demanded a UUID while every run response leads with `key`.** The agent had the
  key, was told by `create_test_run`'s own description to quote it, and then paid a translation
  round trip. `get_test_case` has taken `idOrKey` since PRD-025, so this was an inconsistency as
  well as a friction. `get_test_run`, `record_test_result`, `record_test_results` and
  `complete_test_run` now take either. The key lookup is project-scoped, because run keys are
  unique instance-wide and an unscoped one resolves a stranger's run — the §3.5 shape again, in a
  lookup added after §3.5 was written.
- **The 29-call fan-out.** §2 declined a bulk record tool on the grounds that PRD-005's ingestion
  endpoint covers batches. That reasoning does not survive the case reported: re-recording 29
  results *inside an ongoing MCP session* would mean leaving MCP, translating ids to case keys, and
  posting to a different API. §8 named "if the singular tool turns out to be a bottleneck" as the
  trigger; 29 calls, and 29 writes against a 120/minute budget, is it. `record_test_results` takes a
  list, validates every entry before writing any, and charges the budget per result so a batch is
  not a way around it. It is **one transaction, not per-item** — the opposite of
  `create_test_cases_bulk`, and for a reason: recording is idempotent, so resending a corrected
  batch is safe, which makes "nothing happened, entry 12 is wrong" better than a half-recorded run
  to reconcile.
- **"Invalid or revoked API key"** covered both cases and helped with neither. The reader's next
  move differs — ask an administrator, versus check the copy you are holding — and the report shows
  several round trips spent re-probing the auth header on the strength of it, when the header was
  never the problem. Now distinguished. While there: the rejection body was concatenated into JSON
  with no charset and no escaping, so a message with a non-ASCII character was mangled and one with
  a quote would have produced an unparseable body.

**Not accepted.**

- **"Transient 401, likely a key-store cache lagging the redeploy."** There is no cache:
  `validateKey` hashes and hits the database on every request. A 401 there means the hash matched
  nothing, which is a wrong or stale key. The improved message above is the useful response to this;
  a cache that does not exist cannot be the cause.
- **`Unknown tool: invalid_tool_name`** is a Spring AI defect — `message` carries a literal
  placeholder while the real name sits in `data`. Left alone deliberately: nothing is lost, only
  mislabelled, and intercepting it means rewriting JSON-RPC errors in a servlet filter, outside the
  tool-specification seam everything else here uses. Documented, and worth reporting upstream.
- **Calling a tool that does not exist** was the agent working from memory instead of `tools/list`.
  Not a server problem.
- **Both `Accept` types being mandatory** only bites a caller hand-driving the transport, which is
  the behaviour §8.2 and the descriptor rework exist to discourage. A registered client handles it.

**The offer of a skill encoding these as gotchas was declined.** Three of the four were defects to
remove rather than lore to memorise, and a skill would have made the workarounds permanent and rotted
the moment they were fixed.

**645 backend tests green.**

## 9. Future Work

- **`record_test_results_bulk`**, if the singular tool turns out to be the bottleneck rather than
  the throttle. PRD-005's endpoint covers the batch case today and this PRD deliberately does not
  duplicate it.
- **`link_issue`** over `IssueLinkService` (PRD-010), so a bug can also be filed into GitLab or
  Forgejo. Complementary rather than alternative: a bug report already carries `testResultId`, which
  is exactly what `IssueLinkService.link` keys off. Gated on a tracker being configured, which is
  why it is not in v1.
- **Result-level project audit rows** in `AuditService` — currently only `mcp_tool_invocations`
  records who set a result. A change to the human path as much as the agent one.
- **Per-key tool allow-list** (PRD-025 §9) — promoted from nice-to-have to scheduled. The surface is
  28 tools (§3.1), more than double PRD-025's stated ceiling, and every description rides along on
  every agent turn. An authoring-only key should not pay for execution tools it will never call.
- **Step-level results from an agent.** `record_test_result` sets the parent status only, so a
  human editing one step afterwards recomputes the parent from still-PENDING siblings and can
  overwrite it. Fixing it well means changing the human path too.
- **Flaky-test signal from agent runs** (PRD-016) — agent-driven re-runs are a cheap source of the
  repeated executions flaky detection needs, once anyone trusts them enough to feed it.
