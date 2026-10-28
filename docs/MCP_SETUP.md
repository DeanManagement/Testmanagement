# MCP Server Setup

Lets an AI agent read and author test cases, suites and plans in one project. Implements
[PRD-025](prd/PRD-025-mcp-server.md).

## 1. Turn it on

Off by default — it is a write surface for non-human callers, so enabling it should be deliberate.

```bash
MCP_ENABLED=true
```

With it off, `/api/mcp` does not exist (404) and no MCP beans load.

Optional limits, with their defaults:

| Variable | Default | What it bounds |
|---|---|---|
| `MCP_MAX_WRITES_PER_MINUTE` | 120 | Writes per API key per minute |
| `MCP_MAX_BULK_SIZE` | 50 | Items in one `create_test_cases_bulk` or `record_test_results` call |
| `MCP_MAX_STEPS_PER_CASE` | 100 | Steps in one test case |
| `MCP_AUDIT_RETENTION_DAYS` | 90 | How long tool-call records are kept |

All of these are plumbed through `docker-compose.yml`, so setting them in your `.env` is enough.
Raise `MCP_MAX_WRITES_PER_MINUTE` before a bulk import — 120 is deliberately low enough that an
agent stuck in a loop is stopped within a minute, and executing a run costs one write per test case.

## 2. Create a key

Settings → API Keys → Create. Pick the project and a role:

- **Tester** — can create and update test cases, suites and plans.
- **Viewer** — read only. Write tools return an error naming the role required.

The key is shown once. It is scoped to that one project and there is no way for an agent to reach
another one: no tool takes a project id.

## 3. Point a client at it

The key dialog shows this block with your host and key already filled in — copy it from there.
Note that **an agent cannot find the endpoint on its own**: MCP has no discovery protocol, so
telling it only the hostname is not enough. It needs the full URL and the header.

```json
{
  "mcpServers": {
    "testmanagement": {
      "type": "http",
      "url": "https://your-instance.example.com/api/mcp",
      "headers": { "Authorization": "Bearer tm_your_key_here" }
    }
  }
}
```

`X-API-Key: tm_…` works too, for clients that prefer it. **Include the scheme** — a bare
`your-instance/api/mcp` without `https://` is not a URL most clients can use.

### If your client only speaks stdio

A good deal of local-model tooling registers **stdio** servers only: you give it a command to spawn
and it talks JSON-RPC over that process's stdin and stdout. Point such a client at an HTTPS URL and
it registers nothing — no error, no tools, silently — and the model, seeing a URL and no tools, will
usually start shelling out to `curl`. Either bridge below fixes it.

**Option 1 — the bundled bridge** (`tools/testmanagement-mcp-stdio.py`). One file, Python 3.9+,
standard library only. Nothing to install, so it works on an air-gapped machine with no npm and no
route out except to the instance:

```json
{
  "mcpServers": {
    "testmanagement": {
      "command": "python3",
      "args": ["/path/to/testmanagement-mcp-stdio.py"],
      "env": {
        "TESTMANAGEMENT_URL": "https://your-instance.example.com/api/mcp",
        "TESTMANAGEMENT_API_KEY": "tm_your_key_here"
      }
    }
  }
}
```

The key goes in `env`, not `args` — arguments are visible in `ps` to every user on the machine, and
the bridge refuses to take a key from the command line. Optional: `TESTMANAGEMENT_TIMEOUT_SECONDS`
(default 120) and `TESTMANAGEMENT_USER_AGENT` (see the Cloudflare note in troubleshooting).

**Option 2 — `mcp-remote`**, if you already have Node and would rather not manage a file:

```json
{
  "mcpServers": {
    "testmanagement": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "https://your-instance.example.com/api/mcp",
               "--header", "Authorization: Bearer tm_your_key_here"]
    }
  }
}
```

Note what `npx -y` does: it fetches and runs the latest third-party package from npm on every
launch, and hands that process your API key. That is fine on a workstation with internet access and
wrong for an air-gapped or locked-down install, which is why the bundled bridge exists.

Neither adds behaviour. Both are pipes — every tool, schema and error you see is the server's.

### Driving it by hand (humans and CI only)

**This is not how an agent should call the tools.** If the server is registered with your client,
its tools are in the model's tool list and it calls them by name — sending JSON-RPC by hand throws
away the tool schemas, the argument validation and whatever tool permissions the client enforces.
See the troubleshooting entry below if a model is reaching for `curl` instead.

For a human checking a connection, or a CI smoke test, the transport requires **both** Accept types.
This is the single most common way to get stuck: sending only `application/json`, or leaving a
client's default `*/*`, is rejected.

```bash
curl -X POST https://your-instance/api/mcp \
  -H 'Authorization: Bearer tm_your_key_here' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

Get it wrong and the response says so, naming the header and what it needs to contain.

If you are unsure a client is reaching the right place, `GET https://your-instance/api/mcp` in a
browser returns a small descriptor naming the endpoint, transport and accepted headers. And an API
key used on any other `/api/` path answers with a hint pointing back here, rather than a bare 403.

## 4. Tools

**Read** — `get_project`, `search_test_cases`, `get_test_case`, `list_test_case_folders`,
`list_custom_fields`,
`list_test_suites`, `get_test_suite`, `list_test_plans`, `get_test_plan`, `list_test_runs`,
`get_test_run`, `compare_test_runs`, `list_requirements`, `get_requirement` (a synced requirement names
its `source`, `sourceUrl` and `lastSyncedAt`; `update_requirement` refuses its synced fields), `get_traceability_matrix`,
`list_bug_reports`, `get_bug_report`, `list_comments`, `list_parameter_sets`, `list_test_case_versions`,
`get_test_case_version`, `list_shared_steps`, `get_shared_step`, `download_test_case_attachment`,
`list_environments`, `get_project_dashboard`, `list_flaky_tests`, `get_release_readiness`,
`get_test_suite_report`,
`list_pipeline_workflows`, `list_pipeline_runs`, `get_pipeline_run`, `list_issue_links`,
`list_exploratory_sessions`, `get_exploratory_session`.

**Write** (Tester only) — `create_test_case`, `update_test_case`, `create_test_cases_bulk`,
`create_test_suite`, `create_test_plan`, `create_test_case_folder`,
`move_test_cases_to_folder`, `create_requirement`, `link_test_cases_to_requirement`,
`create_test_run`, `record_test_result`, `record_test_results`, `record_step_result`,
`complete_test_run`, `update_test_run`, `clone_test_run`, `add_comment`, `create_bug_report`,
`change_bug_report_status`, `assign_bug_reports`, `link_bug_report`, `add_bug_report_attachment`, `update_test_suite`, `add_test_cases_to_suite`,
`remove_test_cases_from_suite`, `update_test_plan`, `add_cases_to_plan`, `remove_cases_from_plan`, `update_requirement`,
`unlink_test_case_from_requirement`, `rename_test_case_folder`, `move_test_case_folder`, `change_test_case_status_bulk`,
`create_parameter_set`, `update_parameter_set`, `trigger_pipeline`, `refresh_pipeline_run`,
`link_issue`, `create_linked_issue`, `unlink_issue`, `reopen_test_run`, `update_bug_report`,
`unlink_bug_report`, `update_comment`, `create_shared_step`, `update_shared_step`,
`add_test_case_attachment`, `copy_test_case`, `insert_test_step`, `move_test_step`,
`remove_test_step`, `add_test_cases_to_run`, `remove_test_cases_from_run`, `reorder_test_run_cases`,
`link_test_cases`, `unlink_test_cases`, `assign_test_run_cases`, `create_exploratory_session`,
`update_exploratory_session`, `start_exploratory_session`, `complete_exploratory_session`,
`abort_exploratory_session`, `add_session_note`.

**Project settings** (ADMIN keys only, like the settings pages) — `create_environment`,
`update_environment` (rename, describe, archive / restore), `create_custom_field`,
`update_custom_field` (rename, replace options, required, archive / restore), `sync_requirements`
(pull requirements from Azure DevOps with the project's sync settings; `dryRun` only counts what
would change). A key may hold
VIEWER, TESTER or ADMIN; only system admins create keys.

**Project configuration** (ADMIN keys that also hold the Configuration group, see below) —
`list_webhooks`, `create_webhook`, `update_webhook`, `delete_webhook`, `test_webhook`,
`get_issue_tracker_config`, `set_issue_tracker_config`, `test_issue_tracker_connection`,
`delete_issue_tracker_config`, `list_available_pipeline_workflows`, `set_pipeline_workflows`.
Secrets are write-only: webhook signing secrets and tracker API tokens are accepted but never
returned (`secretSet` / `tokenSet` say whether one is stored), Slack / Teams URLs come back masked,
and any `user:password@` or query string in a returned URL is cut off. Outbound URLs go through the
same private-address checks as the settings pages. Build servers and their workflows remain an
instance administrator's job; `set_pipeline_workflows` only picks which of the workflows open to the
project testers may trigger, as the project settings page does. These changes are not written to the
project audit log (the UI's are not either); the MCP activity log records each call.

**Delete** (Tester only, like the UI) — `delete_test_case`, `delete_test_case_folder`,
`delete_test_suite`, `delete_test_plan`, `delete_test_run`, `delete_requirement`,
`delete_parameter_set`, `delete_shared_step`, `delete_test_case_attachment`, `delete_bug_report`,
`delete_bug_report_attachment`, `delete_comment`, `delete_exploratory_session` (and, with an ADMIN
key in the Configuration group, `delete_webhook`, `delete_issue_tracker_config`). Each is marked `destructiveHint`, so a client can
ask before calling it, and each applies the UI's rules: a test case with results is refused (naming
the runs; deprecate it instead), a shared step still in use is refused, a deleted folder's cases move
to the project root, and comments can only be changed or deleted by the key that wrote them.

Every `update_*` tool is partial: only the arguments you pass change, and an empty string `""`
clears a text field (and, on `update_test_plan`, the target date). Assigning work stays a human decision too, with one
exception: `assign_bug_reports` assigns bugs to a member (by email) or unassigns them, and its
description tells the agent to use it only when a human asked.

**Custom fields** (`customFields` on `get_test_case`, `create_test_case`, `update_test_case` and as
a filter on `search_test_cases`) are keyed by field **name**, not id. Call `list_custom_fields`
first for the names, types and options; an unknown name is refused with the valid ones listed.
On update only the names you pass change, and a `null` value clears that field. A field marked
required never blocks an agent.

**One step at a time** — `insert_test_step`, `move_test_step` and `remove_test_step` change a
single step and leave the others, and the images a human attached to them, untouched; prefer them
to resending the whole list with `update_test_case`. Their positions are 1-based in the case's own
step list, where a shared step counts as one step. `copy_test_case` makes a new `DRAFT` case from an
existing one, steps, images and attachments included.

**Shared steps** — `get_test_case` returns the steps as a tester executes them: a shared step's
steps appear in place, each with `sharedStepId` and `sharedStepTitle`. To use a shared step in
`create_test_case` / `update_test_case`, pass a step with just its `sharedStepId` (from
`list_shared_steps`) instead of an `action`. Consecutive steps with the same `sharedStepId` count as
one use of it, so steps read from `get_test_case` can be sent back unchanged. Shared steps
themselves can be maintained with `create_shared_step` / `update_shared_step` /
`delete_shared_step`; an edit changes every case that uses it, so those cases get a new version and
go back to review exactly as when a human edits it. Send back the step ids from `get_shared_step`
for the steps you keep.

**Environments** — the `environment` of `create_test_run`, `update_test_run`, `clone_test_run`,
`create_bug_report` and `update_bug_report` must be a name from `list_environments` (case does not
matter). An unknown name is refused with the known ones listed, rather than registered as a new
environment the way CI uploads and the UI still do; adding environments stays with project admins.

**Time** — `create_test_case` / `update_test_case` take `estimateMinutes` (1-1440; `0` clears it on
update), and `get_test_case` returns it with `medianActualMs`. An agent that executes tests should
pass `durationMs` to `record_test_result(s)`. `get_test_run` and `get_test_plan` include an
`effort` object: estimated, remaining and actual minutes, and pending results without an estimate.

**Exploratory sessions** — sessions are named by key (`PROJ-Session-3`) or UUID and follow the UI's
life cycle: created `PLANNED`, then `start_exploratory_session`, then complete or abort.
`add_session_note` works while the session runs and up to 24 hours after it completed, and can carry
one screenshot as base64 (at most 2 MB, PNG / JPEG / GIF / WebP). Assigning a tester stays with
humans.

**Release readiness** — `get_release_readiness(planId)` answers "is release X ready?": GO, NO_GO or
NO_CRITERIA, with each configured criterion's actual value, threshold and outcome. Thresholds are
set by people on the plan form; there is no tool to change them.

**Comparing runs** — `compare_test_runs(head, base?)` says what changed between two runs, named by key
or UUID: newly failing, fixed, still failing, added, removed and other changes, with unchanged ones
only counted. Omit `base` to compare with the previous run of the same name, else of the same test
plan and environment.

### Executing a run

The one sequence you cannot infer from the tool list. An agent that runs tests itself does:

1. **`create_test_run`** — seed it with `testCaseIds`, or with `testSuiteId` to take a whole suite.
   Each seeded case gets a `PENDING` result. The run is created `PLANNED`.
2. **`record_test_result`** once per case, as each finishes. This *fills in* the pending result
   rather than adding another, and the first one moves the run to `IN_PROGRESS` — there is no
   separate start call. Identify the case with `testCaseId`; for a parameterized case, which has one
   result per parameter set, use the `resultId` from `get_test_run` instead.

   **If you already have several outcomes, use `record_test_results` instead** — one call, a list of
   entries, capped at `MCP_MAX_BULK_SIZE`. Every entry is validated before anything is written, so a
   bad id fails the whole call naming its position and leaves the run untouched; fix that entry and
   resend. Recording is idempotent, so resending is safe.

   **For a case with steps, `record_step_result` records one step at a time** — `stepNumber` is the
   1-based position from `get_test_case`, and `actualResult` is what you saw there. The case's own
   status is then derived from its steps (worst one wins, `PENDING` until all are recorded), so use
   either this or `record_test_result` for a given case, not both: a step recorded after a
   whole-case outcome recomputes that outcome from the steps.

   Both take an optional `customStatus`: the name of one of the project's own result statuses
   (e.g. `Not applicable`), which must belong to the category passed as `status`. An unknown name
   is refused with the valid ones listed. Metrics only ever count the category; `get_test_run`
   shows the name as `customStatus`.
3. **`complete_test_run`** — `COMPLETED`, or `ABORTED` with a `reason` saying what blocked you.
   Results still pending are reported back, not refused.
4. **`create_bug_report`** for a real defect, passing the `resultId` from step 2 as `testResultId`
   so the bug is reachable from the failure that produced it.

While a run is `PLANNED` or `IN_PROGRESS`, **`add_test_cases_to_run`** and
**`remove_test_cases_from_run`** change which cases it holds, and **`reorder_test_run_cases`** sets
the order they are executed in (`get_test_run` lists results in that order, each with its
`position`). A case with a recorded result is not removed; only a human can discard a result.
**`assign_test_run_cases`** gives results to a member by email (each result in `get_test_run`
shows its `assignee`, which is the run's executor unless one was set) — only when a human asked.

To re-test after a fix, **`clone_test_run`** opens a fresh `PLANNED` run with the same cases.
**`reopen_test_run`** reopens a completed or aborted run instead, with a required reason that lands
in the audit log. **`update_test_run`** renames a run, changes its
environment or files it under a plan — it cannot change the status.

Anywhere a run is named — `get_test_run`, `record_test_result`, `record_test_results`,
`record_step_result`, `complete_test_run`, `update_test_run`, `clone_test_run` — **either the UUID or the key (`PROJ-Run-7`) works**, so you can pass whichever
the previous call handed you rather than making a round trip to translate one into the other.

**If you already have every result** — a CI job, a test framework's output — do not use these tools.
`POST /api/external/projects/{key}/test-runs` takes a whole run in one request with the same API
key, and `…/junit` and `…/cucumber` take report files directly. The MCP tools are for the case where
results arrive one at a time.

### Restricting a key to some tools

An agent carries the description of every tool it is shown on every turn, so a key meant for one
kind of work should only see that kind. When creating a key, **MCP tools** selects the groups it may
use; leaving it empty allows everything except Configuration, which is also what every key issued
before this existed holds.

| Group | Tools |
|---|---|
| Authoring | test cases and their attachments, folders, suites, plans, requirements, parameter sets, shared steps, version history |
| Execution | test runs, results, step results, comments, environments, native bug reports, exploratory sessions |
| Reporting | dashboard, flaky tests, suite report, traceability matrix |
| Pipelines | build-server workflows and pipeline runs |
| Issue tracker | linking and filing issues in the external tracker |
| Configuration | webhooks, the issue-tracker connection, which pipeline workflows testers are offered — ADMIN keys only |

**Configuration is never implied.** An agent that can point a webhook at any URL can send every
event the project emits there, so a key gets these tools only when Configuration is selected
explicitly — an unrestricted key, even an ADMIN one, neither sees nor can call them.

`get_project` is always available. A restricted key does not see the other groups' tools in
`tools/list`, and calling one anyway is refused with a message naming the group it would need — the
refusal appears in the MCP activity log like any other. Groups are fixed when the key is created,
like its role; issue a new key to change them.

### Tools that reach outside Testmanagement

Three tools act on other systems, and are marked `openWorldHint` so a client can ask before calling
them:

- **`trigger_pipeline`** starts real CI on a build server. Only workflows an instance administrator
  has assigned to the key's project can be triggered. The pipeline reports back as a test run: poll
  **`refresh_pipeline_run`** until `testRunKey` appears, then read it with `get_test_run`.
- **`create_linked_issue`** files a new issue in the project's external tracker (PRD-010) and links
  it to a test result; **`link_issue`** attaches one that already exists. They are separate tools so
  that filing cannot happen by getting an optional argument wrong. Both refuse, pointing at
  `create_bug_report`, until a project administrator has configured a tracker.

**Bug reports are off by default.** `create_bug_report` refuses until a project administrator
enables them in the project's settings; there is no MCP tool for that switch.

### What the tools return

**Do not guess the response shape — it is published.** Every tool carries an `outputSchema` in
`tools/list`, and every call returns `structuredContent` matching it alongside the text block. A
client that reads the schema never has to infer whether a list came back as an array or a page.

Two conventions worth knowing, because they are what callers most often get wrong:

- **List tools return a page object, not an array.** `search_test_cases` returns
  `{testCases: [...], page, size, totalElements, hasMore}` — the items are under a *named* field
  (`testCases`, `testSuites`, `testRuns`, `requirements`), never at the top level. Check
  `hasMore` before concluding something does not exist. The exceptions are
  `list_test_case_folders` and `list_test_plans`, which return plain arrays because neither is
  paged.
- **Empty fields are omitted, never null.** A project with no description has no `description`
  key at all. So `folderId` absent means the case is at the project root, and `targetDate` absent
  means the plan has no date.

To see any shape exactly, ask the server rather than guessing:

```bash
curl -s -X POST https://your-instance/api/mcp \
  -H 'Authorization: Bearer tm_your_key_here' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' \
  | jq '.result.tools[] | select(.name=="search_test_cases") | .outputSchema'
```

Delete tools return `{type, id, name}` naming what was removed.

## Things worth knowing before you let an agent loose

- **New cases default to `DRAFT`.** A human is expected to review before they count as real.
- **Duplicate titles are refused.** A create whose title matches an existing case — ignoring case,
  punctuation and extra spaces — comes back with the existing case's key and a suggestion to update
  it instead. Pass `allowDuplicateTitle: true` to override when two cases really do share a title.
- **`search_test_cases` with several `labels` returns the cases carrying all of them**, as its
  description says. Before this version it returned cases with any of them.
- **Bulk creates are per-item.** Read the per-item `CREATED` / `SKIPPED` / `ERROR` outcomes; a
  partial result is normal, not a failure. `dryRun: true` shows what would happen.
- **Replacing a case's steps keeps images and files by position**: a step that no longer exists
  loses them. Omit `steps` unless you mean to rewrite them.
- **Files on a step**: `add_test_case_attachment` takes an optional `stepNumber` (from 1, as
  `get_test_case` numbers the steps); `get_test_case` lists a step's files with their `stepNumber`,
  and `download_test_case_attachment` / `delete_test_case_attachment` take them like the case's own.
- **Executing a run costs one write per test case.** A 50-case run is 52 writes against a budget of
  120 a minute. If you run larger suites through an agent, raise `MCP_MAX_WRITES_PER_MINUTE` — a run
  refused half-way leaves a half-recorded run.
- **A known bug that fails again is linked, not re-filed**: `link_bug_report` records the result,
  and optionally the step (`stepNumber`, from 1 as `get_test_run` numbers them), where it showed up;
  the bug keeps the result it was found in. `create_bug_report` takes a `stepNumber` too, and
  `get_bug_report` lists the bug's `occurrences`.
- **Screenshots go on the bug.** `add_bug_report_attachment` takes the file as `contentBase64`, at
  most 2 MB decoded, with its `fileName` and `contentType`; the bytes must match the type, and SVG
  and HTML are refused. A bug filed with a `testResultId` already carries that result's step
  evidence files, so do not attach them again. `get_bug_report` lists `attachments` (id, name, type,
  size); there is no download tool.
- **Pass rates are passed of executed results** in `get_project_dashboard`, `get_test_plan` and
  `get_test_suite_report`: pending results are not counted, and a
  pass rate is absent (null) when nothing was executed, which is not 0 %. `get_test_plan` also
  returns `executed` and `progress`.
- **Results name their case and executor.** `get_test_run` returns each result's `testCaseKey`,
  who executed it and when. `record_test_result` records the key as the executor, and with
  `cascadeSteps: true` a PASSED or SKIPPED also sets the steps still pending; recorded steps keep
  their outcome, and a failure never cascades.
- **Bugs are named by key** (`PROJ-BUG-12`) in every bug tool; UUIDs still work.
  `list_bug_reports` takes a `query`, `status`, `priority` and `assignee` (an email, `none` or `me`).
  New bugs start as `NEW` with the project's bug template in empty fields. Closing with
  `change_bug_report_status` needs a `resolution`, and `DUPLICATE` needs `duplicateOf`.
- **A bug title matching an already-open bug is refused**, with that bug's key, so a nightly agent
  does not file the same defect every night. The same title is allowed again once the earlier bug is
  closed, because that is a regression and worth knowing about. `allowDuplicateTitle: true`
  overrides.
- **Everything is logged.** `GET /api/mcp-activity` (instance admin) shows every call: which key,
  which tool, the outcome, and what it created. Argument *shapes* are recorded, not values — a
  step's `testData` often holds a test-account password, and that does not belong in an audit table.
- Agent-authored rows show `API key: <name>` as their author.

## Troubleshooting

### The model shells out to `curl` instead of calling the tools

Almost always this means **the tools are not in its tool list**, so `curl` is the only route it has
and it is behaving correctly. Check, in this order:

1. **Is the server switched on?** `MCP_ENABLED=true`. It is off by default, and with it off
   `/api/mcp` returns 404 — so the model gets nothing from the client *and* nothing from a manual
   call. `GET /api/mcp` in a browser returning a JSON descriptor is the quickest confirmation it is
   running.
2. **Does the client speak streamable-HTTP MCP?** This server is HTTP-only, and a lot of local-model
   tooling supports **stdio servers only** — pointing such a client at an HTTP URL registers
   nothing, with no error. See [If your client only speaks stdio](#if-your-client-only-speaks-stdio)
   above for the two bridges.
3. **Ask the model to list its tools.** If `get_project` and `search_test_cases` are not there, it
   is a wiring problem, not a prompting one — no amount of instruction will fix it.
4. **Only if the tools *are* listed** is this a model-behaviour problem. Smaller local models have a
   much stronger prior for JSON-RPC-over-`curl` than for MCP tool calls, and will imitate any curl
   example in their context — including the ones in this file. Telling them plainly, in the system
   prompt, that the Testmanagement tools are available directly and must not be called over HTTP is
   usually enough.

The endpoint cannot tell the two apart, and deliberately does not try: a hand-driven call is
indistinguishable from CI, which is a supported caller.

### The bridge gets 403, but `curl` with the same key works

Something in front of the instance is blocking the client signature, not rejecting the key — the
application answers **401** for a bad key, so a 403 points at a CDN or WAF. Cloudflare's managed
rules block `Python-urllib/3.x` outright and answer with error 1010, "banned based on your browser's
signature".

The bundled bridge already sends `testmanagement-mcp-stdio/1.0` for exactly this reason. If your
proxy is stricter still, override it:

```json
"env": { "TESTMANAGEMENT_USER_AGENT": "curl/8.5.0" }
```

The bridge's own 403 message quotes the upstream response body, which is usually where the real
cause is named.

### Everything returns 401

The key is wrong, revoked, or missing its `tm_` prefix. An API key used on any other `/api/` path
answers with a hint pointing back here rather than a bare 403.

### Every call fails with an empty 400

The `Accept` header. It must contain both `application/json` and `text/event-stream`.

## Rotating a key

Settings → API Keys → the **regenerate** button on a key issues a new secret and shows it once,
with the client config already filled in.

The key itself is unchanged — same project, same role, same service account — so `created_by` on
everything it has written and its entry in the MCP activity log stay attached. Only the secret
moves.

**The old secret stops working immediately.** Any CI pipeline or agent still holding it fails until
you update it, so rotate at a moment when you can. The list shows when each key was last
regenerated, and clears its last-used timestamp — once that reappears, the new secret has been
picked up.

Rotate when a key has leaked (a chat transcript, a commit, a screen share), when someone with
access leaves, or on whatever schedule your policy sets. A revoked key cannot be rotated: create a
new one instead.

## Revoking

Revoking a key in the UI drops its project membership as well, so a request already in flight is
refused by authorization and not just by the key check. Past activity stays in the audit log.
