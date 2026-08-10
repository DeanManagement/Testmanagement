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

## 2. Create a key

Settings → API Keys → Create. Pick the project and a role:

- **Tester** — can create and update test cases, suites and plans.
- **Viewer** — read only. Write tools return an error naming the role required.

The key is shown once. It is scoped to that one project and there is no way for an agent to reach
another one: no tool takes a project id.

## 3. Point a client at it

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

`X-API-Key: tm_…` works too, for clients that prefer it.

## 4. Tools

**Read** — `get_project`, `search_test_cases`, `get_test_case`, `list_test_case_folders`,
`list_test_suites`, `get_test_suite`, `list_test_plans`, `get_test_plan`.

**Write** (Tester only) — `create_test_case`, `update_test_case`, `create_test_cases_bulk`,
`create_test_suite`, `create_test_plan`.

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

## Revoking

Revoking a key in the UI drops its project membership as well, so a request already in flight is
refused by authorization and not just by the key check. Past activity stays in the audit log.
