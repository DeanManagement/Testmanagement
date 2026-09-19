# PRD-042 — AI-Assisted Test Case Generation from Requirements

| | |
|---|---|
| **Status** | ⏸ Backlog — postponed 2026-09-19; kept as a draft, not planned for now |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-17 |
| **Priority** | P3 — Option A is cheap and ready. Option B is driver-dependent |
| **Target** | v2.4 |
| **Related** | PRD-014 (requirements & traceability), PRD-025 (MCP server), PRD-027 (MCP execution tools), PRD-028 (stdio bridge), PRD-010 / PRD-024 (outbound-call discipline, `OutboundUrlValidator`) |

---

## 1. Summary

A common request for test management tools is: "give it a requirement, get draft test cases back."
This tool already has everything that flow needs, just not as a button:

- Requirements carry an `externalId`, `title` and `description` (`entity/Requirement.java`, PRD-014).
- The MCP server exposes `list_requirements`, `create_test_case` / `create_test_cases_bulk` (default
  status **DRAFT** "so a human reviews", per `TestCaseTools`), `link_test_cases_to_requirement` and
  `get_traceability_matrix`. It also has duplicate detection (`TestCaseDuplicateDetector`), a write
  throttle (`McpWriteThrottle`) and an audit trail (`McpToolAuditor`).

So an external agent (Claude Code, Claude Desktop, a local model through the stdio bridge) can
already do the whole job today: read a requirement, draft cases, create them as DRAFT, and link
them. What's missing is discoverability and a good, reusable instruction.

This PRD weighs two options and **recommends Option A**:

- **Option A: MCP prompt + recipe (recommended).** Ship a server-side MCP prompt and a documented
  recipe, plus a "Generate with an agent" helper on the requirement page that copies a ready-made
  instruction. There's no LLM call inside the product and no new secret.
- **Option B: built-in generation.** An admin configures an LLM endpoint (OpenAI-compatible,
  Anthropic, or local Ollama). A button on the requirement proposes cases, and the user picks which
  to create as drafts.

## 2. Goals & Non-Goals

**Goals**
- A tester can go from a requirement to linked **DRAFT** test cases in a couple of minutes.
- Generated cases are never ACTIVE without a human changing their status.
- It works in an air-gapped install. Nothing reaches the internet unless an operator deliberately
  configures it.
- Provenance is visible: generated cases carry a label, so they can be found and reviewed.

**Non-Goals**
- Generating steps for existing cases, test data, or automation code.
- Automatic generation on requirement create or import. Every generation is explicitly requested by
  a person.
- Agentic execution. PRD-027 already covers agent-run tests.
- Fine-tuning, embeddings, RAG over the project, or storing prompts and completions beyond the
  existing MCP invocation audit.
- Any path that creates ACTIVE cases or skips review.

## 3. Proposed Design

### 3.1 Why Option A is recommended

| | Option A: MCP prompt + recipe | Option B: built-in LLM endpoint |
|---|---|---|
| New code | One prompt, one small UI helper, docs | Provider config, secret storage, HTTP client or Spring AI model starters, proposal UI, error handling per provider |
| Secrets held by the app | None new (the user's own MCP key) | An LLM API key per instance (`AesGcmCipher`) |
| Air-gap | Unaffected; the agent runs wherever the user runs it | An outbound call from the server, needing `OutboundUrlValidator` and allow-private settings for Ollama |
| Model choice | Whatever the user already uses | Whatever the admin configured |
| Data egress | Decided by each user's agent setup | Requirement text leaves the server to the configured endpoint, a policy question for every install |
| Review step | DRAFT status and linked cases in the normal UI | Proposal dialog, then DRAFT |
| Works for users without an agent | No | Yes |
| Size | S | L |

The only thing Option B buys is "works for users who have no agent". For a self-hosted tool whose
audience already runs CI and increasingly runs coding agents, that doesn't justify permanently
carrying a second outbound integration, an LLM secret and provider-specific failure modes. **Build
A now and revisit B only when an install asks for it and has no agent** (§3.4 keeps B specified
enough to pick up).

### 3.2 Option A design

**MCP prompt.** Register a prompt `generate_test_cases_for_requirement` alongside the existing tools
in `project/internal/mcp/`. Use Spring AI's MCP prompt annotation if the Spring AI 2.0.x version in
use supports it; otherwise use a `McpServerFeatures.SyncPromptSpecification` bean. Arguments:
`projectKey`, `requirementExternalId`, optional `maxCases` (default 8). The prompt text tells the
agent to:

1. Call `list_requirements` with the external id and read title and description.
2. Call `get_traceability_matrix` and `search_test_cases` to see what already covers the requirement,
   and not duplicate it.
3. Draft positive, negative and boundary cases with concrete steps and expected results.
4. Create them with `create_test_cases_bulk`, `status: DRAFT`, label `ai-generated`.
5. Link them with `link_test_cases_to_requirement`.
6. Report the created keys and anything in the requirement it found ambiguous.

The prompt is static text with argument substitution. It calls no model and lives in the repo, so it
can be reviewed like code. It respects the key's tool groups (`McpToolListFilter`): a key without the
authoring group sees the prompt but its tool calls are refused, and the prompt says which group is
needed.

**UI helper.** On `features/requirements/requirements.component.*`, a per-requirement menu item
"Generate test cases with an agent…" opens a dialog with:
- the ready-to-paste instruction ("Use the Testmanagement MCP server to run the prompt
  generate_test_cases_for_requirement for project PROJ, requirement REQ-014"),
- a Copy button,
- a link to `docs/MCP_SETUP.md#generating-test-cases`,
- a static note that MCP must be enabled by an administrator (`app.mcp.enabled`, off by default)
  and that the key needs the authoring tool group. The frontend has no signal for "MCP is on"
  today (`McpDiscoveryRoute` doesn't report it), and a constant note is cheaper than adding one.

**Review surface, which already exists:** filter the test case list by `label = ai-generated` and
`status = DRAFT`, then use the bulk status dialog (`features/test-cases/bulk-status-dialog`) to
promote reviewed cases to ACTIVE. Promoting them does **not** remove the label; provenance stays.

**Docs.** A new "Generating test cases" section in `docs/MCP_SETUP.md`: the prompt, a worked example,
the review workflow, and a warning that requirement text is sent to whatever model the agent uses.

**No backend endpoint and no schema change.**

### 3.3 Option A: endpoints, RBAC, MCP impact

- No REST endpoints are added.
- The prompt is available to any valid MCP key. Tool calls inside it are authorized exactly as today
  (service-user `ProjectMember` role via `@RequireProjectRole` paths, PRD-025 §3.2). Creating cases
  needs TESTER.
- MCP tool count is unchanged. Prompt count goes from 0 to 1.

### 3.4 Option B, outline for when a driver appears

Kept short on purpose. Expand into its own PRD if picked up.

- **Config:** a system-admin settings page, stored in a single-row table, with provider
  (`OPENAI_COMPATIBLE` | `ANTHROPIC` | `OLLAMA`), base URL, model, and API key encrypted with
  `AesGcmCipher`. Off unless configured. The base URL is checked with
  `OutboundUrlValidator.validate(url, label, requireHttps, allowPrivate)`, with `allowPrivate` true
  only for OLLAMA. Spring AI model starters are already managed by the `spring-ai-bom` in `pom.xml`,
  so no new BOM is needed.
- **Endpoint:** `POST /api/projects/{projectId}/requirements/{id}/case-proposals`,
  `@RequireProjectRole(ProjectRole.TESTER)`, rate-limited per user. It returns proposals and
  **persists nothing**.
- **UI:** a proposal dialog with checkboxes and inline edits. "Create selected" calls the existing
  create and link endpoints with status DRAFT and label `ai-generated`. Near-duplicates are flagged
  with `TestCaseDuplicateDetector`.
- **Egress notice:** the admin page and the dialog both say the requirement text is sent to the
  configured endpoint.
- **Effort:** ~2 weeks. Risk: medium (provider variance, timeouts, JSON-shape failures, data-egress
  policy).

## 4. Edge Cases

- **Requirement with no description:** the prompt instructs the agent to generate from the title
  only and to say so in its report, not to invent detail.
- **Requirement already covered:** the prompt checks traceability first and proposes only gaps. If
  nothing is missing, it creates nothing and reports that.
- **Duplicate titles:** `create_test_cases_bulk` already runs duplicate detection, and the prompt
  tells the agent not to force-create.
- **MCP disabled:** the agent's client fails to connect. The dialog's static note and the setup doc
  cover it, and there's nothing to detect server-side.
- **Key scoped to another project:** tool calls are refused by the existing scope check, and the
  prompt's error guidance points to key scope.
- **Write throttle hit** on large generations: `maxCases` defaults to 8 and bulk create is one write.
- **Agent ignores `status: DRAFT`:** the tool already defaults to DRAFT. The residual risk is an agent
  explicitly passing ACTIVE. That is accepted and visible in the MCP activity log
  (`McpActivityController`). A server-side "prompt-created cases are always DRAFT" rule isn't
  possible because the server can't tell a prompt-driven call from any other call.

## 5. Testing

- The prompt is registered and listed by the MCP server. Argument substitution produces the expected
  text, as a snapshot test of the rendered prompt.
- The prompt names only tools that exist. A test fails if a referenced tool name disappears from the
  registry, so the recipe can't rot silently.
- Frontend (Vitest): the requirement menu opens the dialog, Copy puts the instruction with the right
  project key and requirement id on the clipboard, and the MCP prerequisite note is shown.
- Manual end-to-end check recorded in the PR: Claude Code via MCP against a dev instance, one
  requirement, cases created as DRAFT with the `ai-generated` label and linked. This follows the
  PRD-028 §8 lesson that live runs catch what stubs don't.

## 6. Effort & Risk

- **Option A effort:** ~2 days (prompt + test ~0.5, UI dialog ~0.5, docs and worked example ~1).
- **Option A risk:** Low. It adds no new authority, no new secret, no outbound calls and no schema
  change. The main risk is output quality, which is contained by DRAFT status and human review.
- **Option B:** see §3.4. Don't start it without a named install that needs it and cannot run an
  agent.

## 7. Acceptance Criteria

- [ ] MCP prompt `generate_test_cases_for_requirement` is registered and listed, with `projectKey`,
      `requirementExternalId` and optional `maxCases` arguments.
- [ ] The prompt directs agents to check existing coverage, create cases as DRAFT with label
      `ai-generated`, and link them to the requirement.
- [ ] A test fails if the prompt references a tool name that does not exist.
- [ ] The requirement page offers "Generate test cases with an agent…" with a copyable instruction
      and a note on the MCP prerequisite.
- [ ] `docs/MCP_SETUP.md` documents the recipe, the review workflow and the data-egress caveat.
- [ ] No REST endpoint, schema change or outbound call is added by Option A.
- [ ] Option B is not built. This PRD records the decision and the outline for when a driver
      appears.
