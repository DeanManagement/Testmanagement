# PRD-010 — Native Issue-Tracker Integration

| | |
|---|---|
| **Status** | Proposed |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-09 |
| **Priority** | P3 — v2.0, highest value in the v2 set |
| **Target** | v2.0 |
| **Related** | REQUIREMENTS.md §13 (was PRD-009 §2.1); REVIEW §3.10, §4.9 |

---

## 1. Summary

Today a failing test result links to a defect via a free-text `TestResult.defectLink` URL — the tester copies a link by hand and there is no status, no search, no creation. This PRD adds an optional per-project issue-tracker connection so testers can search existing issues, file a new one from a failure with a pre-filled body, and see a live OPEN/CLOSED pill — without leaving the tool. It is the single most-requested capability for a tool in this niche.

Ship **one provider first** (GitHub or GitLab — REST, token auth, simple) and keep the design provider-pluggable. `defectLink` stays for backward compatibility and for trackers we don't integrate.

## 2. Goals & Non-Goals

**Goals**
- Per-project `IssueTrackerConfig` (project ADMIN only): provider, base URL/repo, encrypted API token.
- Search issues (typeahead) and link one to a result.
- "Create issue" from a failed result with a templated body (test case key, run key, last actual result, links to screenshots where the provider supports it).
- A small OPEN/CLOSED status pill on linked issues, refreshed by a bounded poll.

**Non-Goals**
- Bi-directional sync / webhooks back from the tracker (poll only).
- Every provider at once — start with one, add adapters incrementally.
- Replacing `defectLink` (kept as a fallback).
- Storing issue comments/attachments locally.

## 3. Proposed Design

### 3.1 Data model (migration V39/V40)
- `issue_tracker_configs`: `id`, `project_id` (unique), `provider` (`GITHUB|GITLAB|JIRA|LINEAR`, start with GITHUB), `base_url`, `project_ref` (repo/owner or project id), `api_token_encrypted`, `active`, timestamps. Token encrypted at rest (see §3.4).
- `issue_links`: `id`, `test_result_id` (FK), `provider`, `external_id` (e.g. `owner/repo#123`), `url`, `title`, `state` (`OPEN|CLOSED|UNKNOWN`), `state_checked_at`. Index `(test_result_id)`.

### 3.2 Provider abstraction
`IssueTrackerProvider` interface: `searchIssues(config, query)`, `createIssue(config, IssueDraft)`, `getIssue(config, externalId)`. One implementation per provider (`GitHubIssueProvider` first) using `java.net.http.HttpClient` with connect/read timeouts (reuse the webhook client conventions). A registry selects the impl by `config.provider`. SSRF guard for self-hosted GitLab/Jira base URLs (reuse `WebhookUrlValidator` logic).

### 3.3 Endpoints (RBAC via PRD-001)
- `GET/PUT/DELETE /api/projects/{projectId}/issue-tracker` — config (ADMIN). Token write-only; never returned.
- `GET /api/projects/{projectId}/issues/search?q=` — typeahead (TESTER+/VIEWER read).
- `POST /api/projects/{projectId}/test-runs/{runId}/results/{resultId}/issues` — link existing (`{externalId}`) or create (`{create:true, title?, body?}`); TESTER.
- `DELETE .../results/{resultId}/issues/{linkId}` — unlink (TESTER).
- Status refresh: a `@Scheduled` poller (5-min, bounded batch) updates `state` only for links on results in non-completed plans/runs to limit API calls; on-demand refresh on result view.

### 3.4 Security
- API token encrypted with AES-GCM using a key from `app.issuetracker.encryption-key` (env). Decrypt only in the provider call. Never log or serialize the token.
- Outbound calls are opt-in per project — air-gap safe (no config → no calls).

### 3.5 Frontend
- Project settings: issue-tracker config form (provider, base URL/repo, token, test-connection button).
- Result detail (run execution + report): "Link issue" typeahead + "Create issue" button on FAILED results; linked issues render as chips with an OPEN/CLOSED pill and an external link.

## 4. Edge Cases
- Provider auth failure → clear error, config marked needs-attention; no retries hammering the API.
- Rate limiting → back off; show stale `state_checked_at`.
- Tracker down → linking/creation returns a 502-style error surfaced to the user; existing links still display cached state.
- Removing config keeps existing `issue_links` (read-only) and `defectLink` values.

## 5. Testing
- Provider adapter unit tests against a mock HTTP server (search/create/get; auth failure; rate limit).
- Token encryption round-trip; token never present in responses (serialization test).
- Authz: VIEWER cannot configure or create; TESTER can link/create; cross-project isolation.
- Templated body contains test-case key, run key, last actual result.

## 6. Effort & Risk
- **Effort:** ~1.5 weeks for one provider end-to-end (config, search, create, status, UI). Each additional provider ~2–3 days.
- **Risk:** Medium — outbound auth + token security + provider API variance. Mitigated by single-provider start, the existing webhook HTTP/SSRF patterns, and opt-in design.

## 7. Acceptance Criteria
- [ ] Project admin configures a provider with an encrypted token (never returned).
- [ ] Tester searches issues and links one to a result; can create a templated issue from a failure.
- [ ] Linked issues show an OPEN/CLOSED pill refreshed within the poll window.
- [ ] No outbound calls when no tracker is configured; non-HTTPS/private base URLs rejected.
- [ ] `defectLink` remains usable; provider/adapter/authz tests pass.
