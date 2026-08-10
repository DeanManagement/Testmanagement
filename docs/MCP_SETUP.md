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
| `MCP_MAX_WRITES_PER_MINUTE` | 60 | Writes per API key per minute |
| `MCP_MAX_BULK_SIZE` | 50 | Items in one `create_test_cases_bulk` call |
| `MCP_MAX_STEPS_PER_CASE` | 100 | Steps in one test case |
| `MCP_AUDIT_RETENTION_DAYS` | 90 | How long tool-call records are kept |

All of these are plumbed through `docker-compose.yml`, so setting them in your `.env` is enough.
Raise `MCP_MAX_WRITES_PER_MINUTE` before a bulk import — 60 is deliberately low enough that an
agent stuck in a loop is stopped within a minute.

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

### Calling it without an MCP client

If you are driving it by hand, the transport requires **both** Accept types. This is the single
most common way to get stuck: sending only `application/json`, or leaving a client's default
`*/*`, is rejected.

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
`list_test_suites`, `get_test_suite`, `list_test_plans`, `get_test_plan`.

**Write** (Tester only) — `create_test_case`, `update_test_case`, `create_test_cases_bulk`,
`create_test_suite`, `create_test_plan`, `create_test_case_folder`,
`move_test_cases_to_folder`, `create_requirement`, `link_test_cases_to_requirement`.

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

There are no delete tools and no test-run or result tools. Recording results stays with the CI
ingestion API (PRD-005), and deleting anything stays a human action in the UI.

## Things worth knowing before you let an agent loose

- **New cases default to `DRAFT`.** A human is expected to review before they count as real.
- **Duplicate titles are refused.** A create whose title matches an existing case — ignoring case,
  punctuation and extra spaces — comes back with the existing case's key and a suggestion to update
  it instead. Pass `allowDuplicateTitle: true` to override when two cases really do share a title.
- **Bulk creates are per-item.** Read the per-item `CREATED` / `SKIPPED` / `ERROR` outcomes; a
  partial result is normal, not a failure. `dryRun: true` shows what would happen.
- **Replacing a case's steps discards screenshots** attached to steps that no longer exist. Omit
  `steps` unless you mean to rewrite them.
- **Everything is logged.** `GET /api/mcp-activity` (instance admin) shows every call: which key,
  which tool, the outcome, and what it created. Argument *shapes* are recorded, not values — a
  step's `testData` often holds a test-account password, and that does not belong in an audit table.
- Agent-authored rows show `API key: <name>` as their author.

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
