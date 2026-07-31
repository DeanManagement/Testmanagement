# PRD-010 — Native Issue-Tracker Integration

| | |
|---|---|
| **Status** | 🚧 Backend implemented (2026-07-31, GitLab + Forgejo) — frontend pending |
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
- [x] Project admin configures a provider with an encrypted token (never returned).
- [x] Tester searches issues and links one to a result; can create a templated issue from a failure. *(API only — UI pending.)*
- [x] Linked issues show an OPEN/CLOSED pill refreshed within the poll window. *(State cached and polled; pill pending.)*
- [x] No outbound calls when no tracker is configured; non-HTTPS/private base URLs rejected.
- [x] `defectLink` remains usable; provider/adapter/authz tests pass.

## 8. As Built — backend (2026-07-31)

**Providers:** GitLab first per §1, then Forgejo (which also covers Gitea, the project it forked
from, and Codeberg, which runs it). `IssueTrackerProvider` exposes search/create/get/testConnection;
`IssueTrackerProviderRegistry` resolves the adapter from the config's provider enum, and
`GET /issue-tracker/providers` reports which ones have an adapter so the UI cannot offer a dead
option. Adding GitHub, Jira or Linear means adding one bean — no service or controller changes. The
enum declares all five so stored rows survive a later adapter without a migration.

`HttpIssueProviderSupport` holds the shared HTTP mechanics: client construction, the
status-to-exception mapping, and JSON helpers. The failure taxonomy lives there deliberately —
the service layer and the poller's backoff both depend on "token rejected", "project missing" and
"rate limited" being distinguishable whichever tracker produced them. Adding Forgejo took one
subclass supplying its auth header and URL shapes.

**Forgejo specifics.** Two things differ from GitLab and are worth knowing before adding a third
adapter. Repos are addressed as two path segments (`/repos/{owner}/{repo}`) rather than one encoded
path, so `owner/repo` is split and each half encoded separately — a nested GitLab-style
`group/sub/project` is rejected up front rather than 404-ing later. And its issues endpoint returns
pull requests alongside issues unless `type=issues` is passed; the adapter passes it *and* skips
anything carrying a `pull_request` payload, because a merge request must never be linkable as a
defect. Field names differ too: `number`/`html_url`/`body` against GitLab's `iid`/`web_url`/
`description`, and state is `open` rather than `opened`.

Path segments are encoded with `encodePath`, not `URLEncoder.encode` directly: the latter is form
encoding, where a space becomes `+`, but in a path `+` is a literal plus.

**Data model:** V42 adds `issue_tracker_configs` (unique per project) and `issue_links` (unique per
result + external id). Provider and URL are denormalised onto the link so previously filed defects
stay visible and clickable after a config is deleted or switched.

**Token security:** AES-GCM with a fresh random IV per encryption, key from
`app.issuetracker.encryption-key`. With no key configured the cipher refuses to encrypt rather than
falling back to plaintext — a tracker token grants write access to the customer's tracker, so it
fails closed. The token has no field in any response DTO, not even masked; `tokenSet` is a boolean.
Omitting the token on update keeps the stored one, so changing the project ref does not require
re-pasting the secret.

**SSRF:** `IssueTrackerUrlValidator` mirrors the webhook validator — https required, loopback,
private, link-local and any-local rejected — with independent switches. Redirects are never
followed, since a redirect would re-send the `PRIVATE-TOKEN` header to a host the tracker chooses.

**Polling:** `IssueStatePoller` returns immediately unless some project has an active config, so an
air-gapped install makes no outbound calls at all. Only links on `PLANNED`/`IN_PROGRESS` runs are
considered; each pass is capped at `poll-batch-size`, oldest-checked first. On an upstream error the
project's batch stops after the first failure rather than repeating a rejected call per link, and
the error is recorded on the config for the settings UI. The refresh logic lives in a separate
`IssueStateRefresher` bean because `@Transactional` is proxy-applied and would not take effect on a
self-invoked method.

**Errors:** new `UpstreamServiceException` → 502, so "the tracker is down" is distinguishable from
a bad request (400) or an authorization failure (403). On-demand refresh swallows provider failures
and keeps the cached state — a stale pill beats an error page on the result view.

**Endpoints:** config GET/PUT/DELETE and `POST /issue-tracker/test` (ADMIN); `GET /issues/search`
(any member); list and `POST .../issues/refresh` (any member); link/create and unlink (TESTER).

**Tests (71 new, 294 total green):** both adapters against stub APIs covering auth failure, 404,
rate limiting, malformed JSON, unreachable host and path encoding, plus Forgejo's pull-request
exclusion and owner/repo validation; cipher round trip, IV uniqueness, tamper detection, wrong key,
fail-closed and key-length validation; URL validator including the cloud metadata address; config
authz, token-never-returned and encrypted-at-rest; link/create/unlink/refresh end to end with
templated-body assertions, cross-project isolation and VIEWER/TESTER split; poller batching and
stop-on-auth-failure.

**Note:** `CreateIssueLinkRequest.create` is boxed rather than primitive — Jackson rejects a record
whose primitive component is absent from the body, which made "link an existing issue" 400. The same
latent bug was fixed in `GrantTestCasePermissionRequest.canEdit`.

### Still to do
- Frontend: project-settings config form with test-connection button; link/create UI and OPEN/CLOSED
  pill on result detail (§3.5).
- Screenshot links in the templated body (§2) — deferred until the UI exists to exercise them.
