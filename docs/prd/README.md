# Product Requirements (PRDs)

PRDs for outstanding work on Testmanagement, derived from `REQUIREMENTS.md` (roadmap leftovers) and `REVIEW_AND_PROPOSALS.md` (the May 2026 audit). Each PRD is self-contained: problem, design, edge cases, testing, effort, and acceptance criteria.

## v1.3 — delivered

| PRD | Title | Priority | Status |
|---|---|---|---|
| [001](PRD-001-rbac-access-control.md) | RBAC & Project Authorization | **P0 — Security** | ✅ Implemented |
| [002](PRD-002-backend-filtering-pagination.md) | Backend Filtering & Pagination | P1 | ✅ Implemented |
| [003](PRD-003-webhooks.md) | Outbound Webhooks | P1 | ✅ Implemented |
| [004](PRD-004-import-export.md) | Test Case Import / Export (CSV + JSON) | P1 | ✅ Implemented |
| [005](PRD-005-ci-result-ingestion.md) | CI Result Ingestion (JUnit / Cucumber) | P2 | ✅ Implemented |
| [006](PRD-006-watcher-notifications.md) | Watcher Notifications (in-app + email) | P2 | ✅ Implemented |
| [007](PRD-007-server-side-search.md) | Server-Side Full-Text Search | P2 | ✅ Implemented |
| [008](PRD-008-usability-and-polish.md) | Usability & Polish Bundle | P2/P3 | ✅ Implemented |

## v1.4 — security & hardening (proposed, from the June 2026 review)

Fixes for the findings in [`docs/CODE_REVIEW_2026-06-09.md`](../CODE_REVIEW_2026-06-09.md). **The entire wave shipped on 2026-06-10** (205 backend tests green; frontend builds + tests clean). Open follow-ups noted inside the PRDs: audit-log persistence of login failures (020), ESLint subscription rule in CI (022), first live CI run on GitHub (023).

| PRD | Title | Priority | Size | Status |
|---|---|---|---|---|
| [017](PRD-017-media-cache-authorization-fix.md) | Authenticated Media Caching Fix | **P0 — auth bypass** | S | ✅ Implemented |
| [018](PRD-018-allure-sandboxing-token-hygiene.md) | Allure Report Sandboxing & Token Hygiene | **P0 — stored XSS** | M | ✅ Implemented |
| [019](PRD-019-deployment-secrets-hardening.md) | Deployment & Secrets Hardening | **P0 — default secrets** | M | ✅ Implemented |
| [020](PRD-020-auth-hardening.md) | Auth Hardening: Login Throttling & JWT Lifecycle | P1 | M | ✅ Implemented |
| [021](PRD-021-authorization-data-safety-gaps.md) | Authorization & Data-Safety Gap Closure | P0/P1 | S | ✅ Implemented |
| [022](PRD-022-frontend-stability-bundle.md) | Frontend Stability & Consistency Bundle | P1/P2 | M | ✅ Implemented |
| [023](PRD-023-ci-pipeline-repo-hygiene.md) | CI Pipeline & Repository Hygiene | P1 | S-M | ✅ Implemented |
| [024](PRD-024-build-server-integration.md) | Build Server Integration (Pipeline Triggering) | P2 | L | ✅ Implemented |

**Sequencing:** 017 + 019 §4.1 + 021 §4.1/§4.3 first (one day, closes everything exploitable) → 018 → 020 → 023 (so the rest lands with CI in place) → 022 → 019 remainder + 021 §4.2.

## v2.0 — proposed ([PRD-009](PRD-009-v2-future-backlog.md) is the index)

| PRD | Title | Size | Status |
|---|---|---|---|
| [010](PRD-010-issue-tracker-integration.md) | Native Issue-Tracker Integration | M | ✅ Implemented (GitLab + Forgejo) |
| [011](PRD-011-test-case-versioning.md) | Test Case Versioning / History | L | ✅ Implemented |
| [012](PRD-012-oidc-sso.md) | SSO via OpenID Connect (multi-provider) | M | ✅ Implemented |
| [013](PRD-013-dark-mode-theming.md) | Dark Mode / Theming | S | ✅ Implemented |
| [014](PRD-014-traceability-matrix.md) | Requirements & Traceability Matrix | M | ✅ Implemented |
| [015](PRD-015-parameterized-test-cases.md) | Parameterized / Data-Driven Test Cases | M | ✅ Implemented |
| [016](PRD-016-flaky-test-detection.md) | Flaky Test Detection | M (≈S) | ✅ Implemented |

## What shipped

The entire v1.3 backlog (PRD-001 through PRD-008) is implemented and tested. Highlights: server-side RBAC enforced via a `@RequireProjectRole` aspect (PRD-001, was a live IDOR); filtering/pagination with URL-bound filters (PRD-002); signed outbound webhooks with retry (PRD-003); CSV/JSON import-export with dry-run (PRD-004); JUnit/Cucumber CI ingestion (PRD-005); watcher notifications with an in-app bell (PRD-006); Postgres full-text search behind the command palette (PRD-007); and the usability/tech-debt bundle (PRD-008).

## Suggested sequencing (v2.0)

Driver-dependent — see [PRD-009](PRD-009-v2-future-backlog.md) §3 for the recommended order:
~~dark mode (013)~~ → issue-tracker (010) → OIDC (012) → flaky detection (016) → traceability (014) + versioning (011) → parameterized cases (015).

**The entire v2.0 backlog shipped on 2026-07-31.** Dark mode (013), issue-tracker integration
(010, GitLab + Forgejo), SSO (012), flaky detection (016), versioning (011), traceability (014) and
parameterized cases (015).

Note that 011, 014 and 015 were originally gated on a driver appearing — compliance for the first
two, real demand for the third — and were built on request rather than because that driver arrived.
Worth revisiting if any of them turns out to be carried complexity rather than used capability.

## v2.2 — proposed

| PRD | Title | Priority | Size | Status |
|---|---|---|---|---|
| [025](PRD-025-mcp-server.md) | MCP Server (agent-authored test cases & plans) | P2 | M | ✅ Implemented |

**PRD-025 shipped on 2026-08-09**, in two commits. First the API-key → service-user authorization
rework (§3.2), which closes a fail-open gap in `ProjectRoleAspect` — API-key callers bypassed
project role checks entirely — and is worth having on its own. Then the 13-tool authoring surface at
`/api/mcp` (Spring AI 2.0, stateless streamable-HTTP), off by default behind `app.mcp.enabled`.

Two cross-project holes were found by review during this work and are fixed: API keys failing open
past `@RequireProjectRole`, and `create_test_suite` (plus the equivalent REST endpoint) accepting
test case ids from any project.

**Breaking change in §3.2:** API keys without a project scope are now rejected. Set
`app.api-keys.allow-legacy-global=true` to keep them working while re-creating them scoped.

## v2.3 — proposed

| PRD | Title | Priority | Size | Status |
|---|---|---|---|---|
| [026](PRD-026-azure-devops-integration.md) | Azure DevOps Integration (Pipelines, Work Items, results, Entra ID) | P2 | L | 📝 Draft |
| [027](PRD-027-mcp-execution-tools.md) | MCP Execution & Defect Tools (agent-run test execution) | P2 | M | ✅ Implemented |
| [028](PRD-028-mcp-stdio-bridge.md) | MCP stdio Bridge (clients without HTTP transport) | P3 | S | ✅ Implemented |

Four surfaces on three seams that already exist, phased so each ships alone: Entra ID SSO is
documentation only (the generic OIDC provider already handles it), Pipelines fills the
`AZURE_DEVOPS` slot PRD-024 reserved, Work Items is a PRD-010 adapter, and test-result pull builds
on the Pipelines adapter. Cloud and on-premises Azure DevOps Server both in scope.

**PRD-027 shipped on 2026-08-31**, in two commits. It closes the loop PRD-025 opened: an agent could
author tests and read results but not run them, so it adds three test-run tools and four native
bug-report tools (21 → 28).

The first commit is §3.5 and is worth having on its own. The `findAllById` sweep PRD-025 §8 asked
for was never run; doing it found **nine unscoped child-id lookups across five services**, all live
through the REST API. Eight are cross-project leaks or writes — a run seeded from another project's
cases, a run counting toward another project's plan pass rate, a bug linked to another project's
result. The ninth is worse and unrelated to MCP: `ProjectMemberService.updateRole` and
`removeMember` accepted a `projectId` and never read it, so an admin of **any** project could change
roles or remove members in **any other** — cross-tenant privilege escalation. All fixed, with the
regression tests demonstrated red against the unfixed code first.

**Behaviour change:** unknown or foreign child ids now 404 instead of being silently dropped or
nulled, and `UpdateTestRunRequest.name` treats null as "unchanged" (as `UpdateTestCaseRequest`
already did since PRD-025). `MCP_MAX_WRITES_PER_MINUTE` defaults to 120, up from 60.

**PRD-028 shipped on 2026-08-31**, taking up the clause PRD-025 §9 left open — "a stdio wrapper if
clients without HTTP transport turn out to matter in practice". `tools/testmanagement-mcp-stdio.py`
is one stdlib-only Python file with 22 tests; `mcp-remote` stays documented as the zero-install
alternative for anyone who does not mind `npx -y` fetching third-party code into the credential path.

It was **built with its own §2.1 unanswered**, which the PRD says plainly. The bridge earns its place
on the air-gap case alone, but nobody has confirmed that the local model which prompted it actually
lacks HTTP transport — so it may not be the fix for that symptom. §2.1 is a five-minute check.

Worth reading §8 for one thing: the live run found a bug no unit test could. urllib identifies as
`Python-urllib/3.x`, which Cloudflare blocks outright, so the bridge worked against a stub and
failed against any instance behind a CDN — and its own error message blamed the API key, which was
the one thing that was fine.

## v2.4 — proposed (feature research, 2026-09-17)

Gaps against comparable test-management tools, each grounded in the current code. All drafts.

| PRD | Title | Priority | Size | Status |
|---|---|---|---|---|
| [029](PRD-029-jira-github-issue-trackers.md) | Jira & GitHub Issues as Issue Trackers | P1 | M | ✅ Implemented (manual smoke test open) |
| [030](PRD-030-shared-steps.md) | Shared / Reusable Steps | P2 | M-L | ✅ Implemented |
| [031](PRD-031-chat-notification-presets.md) | Chat Notification Presets (Slack / Teams / Mattermost) | P2 | S-M | ✅ Implemented (manual screenshots open) |
| [032](PRD-032-environments-configurations.md) | Project Environments | P2 | M | ✅ Implemented (browser check open) |
| [033](PRD-033-test-case-review-status.md) | Test Case Review / Approval Status | P3 | S-M | ✅ Implemented (browser check open) |
| [034](PRD-034-exploratory-sessions.md) | Exploratory Testing Sessions | P3 | M | ✅ Implemented (browser check open) |
| [035](PRD-035-custom-fields.md) | Custom Fields | P3 | M-L | ✅ Implemented (bug list filter left out) |
| [036](PRD-036-time-estimates-tracking.md) | Time Estimates & Tracking | P3 | M | ✅ Implemented |
| [037](PRD-037-release-readiness-quality-gate.md) | Release Readiness / Quality Gate | P2 | S-M | ✅ Implemented |
| [038](PRD-038-run-comparison.md) | Run Comparison | P2 | S-M | ✅ Implemented |
| [039](PRD-039-backup-restore.md) | Backup / Restore | P2 (A) · P3 (B) | S (A) · L (B) | ✅ Phase A implemented · Phase B deferred |
| [040](PRD-040-bdd-gherkin.md) | BDD / Gherkin Authoring | P3 | M | ✅ Implemented |
| [041](PRD-041-totp-two-factor.md) | TOTP Two-Factor Authentication | P3 | M | ⏸ Backlog |
| [042](PRD-042-ai-case-generation.md) | AI-Assisted Case Generation | P3 | S (A) | ⏸ Backlog |
| [043](PRD-043-scheduled-reports.md) | Scheduled Report Emails | P3 | M | ⏸ Backlog |
| [044](PRD-044-test-case-attachments.md) | Test Case Attachments | P2 | M | ✅ Implemented |

**Suggested order:** 029 → 031 → 039 Phase A → 038 → 037 → 044 → 030 → 032. Everything P3 waits for
its driver. 041, 042 and 043 were postponed to the backlog on 2026-09-19: kept as drafts, not planned
for now.

**Found while writing these (existing bugs, not new features):** CI-ingested runs fire no run webhook
events (031); `PLAN_COMPLETED` is declared but never published (031); `forcePasswordChange` is
enforced only in the frontend (041); comments on a deleted test case are never cleaned up (035).

## v2.5 — proposed (test-team findings, 2026-09-19)

From the 49 reports the test team filed on the test instance (v2.0.0) on 2026-09-16/17. 28 of them
are change requests filed as bugs; each PRD lists the reports it resolves by id. The other 21 are
bugs: 5 are already fixed on `main` (language persistence `95dad30`, add-step icon `0d73ea6`, deep-link
run refresh `f5cc79f`, run-form case picker `d166e85`, import 500 `16790c6`); the other 16 were fixed
on 2026-09-19 (`01d7da0` to `a71a07b`) and need a deploy to reach the test instance.

| PRD | Title | Priority | Size | Status |
|---|---|---|---|---|
| [045](PRD-045-bug-report-triage.md) | Bug Report Triage (key, search, bulk, New/resolution, template) | P2 | M-L | ✅ Implemented |
| [046](PRD-046-audit-trail-with-values.md) | Audit Trail with Old/New Values | P2 | M | ✅ Implemented |
| [047](PRD-047-defects-in-execution-and-planning.md) | Defects in Execution and Planning | P2 | M-L | ✅ Implemented |
| [048](PRD-048-execution-and-report-evidence.md) | Execution and Report Evidence | P2 | M | 📝 Draft |
| [049](PRD-049-metrics-consistency-and-dashboard.md) | Metrics Consistency and Dashboard | P2 | S-M | 📝 Draft |
| [050](PRD-050-test-case-context.md) | Test Case Context | P2 | S-M | 📝 Draft |
| [051](PRD-051-bug-report-attachments-and-image-viewer.md) | Bug Report Attachments and Image Viewer (with PRD-044) | P2 | M | 📝 Draft |
| [052](PRD-052-lists-filters-and-pickers.md) | Lists, Filters and Pickers | P2 | S-M | 📝 Draft |

**Suggested order:** 049 (small, and every later screen shows its numbers) → 045 → 046 → 047 → 048 →
050 → 052; 051 together with or right after 044. The issue-tracker request (Jira, GitHub, Azure
DevOps) needs no new PRD: Jira and GitHub are PRD-029 on `main`, Azure DevOps is PRD-026.

**Found while writing these (existing bugs):** dropping a bug card on the Kanban board changes the
status through a full update, skipping the required reason (045); the test case label filter
matches *any* label while MCP `search_test_cases` documents *all* (052); the dashboard hides a real
0 % pass rate (049); `list_custom_fields` returns an array like the two reported MCP tools (bug
report `7a792e91`); `step_images` and `screenshots` allow duplicate rows per owner (bug report `bd5b0f76`).

## Status legend
Every PRD up to 028 except 026 is **Implemented**; 029–040 and 044–047 are implemented except where noted; 048–052 are drafts; 041–043 are drafts
postponed to the backlog (⏸). New work should get a new PRD rather than
extending a shipped one.
