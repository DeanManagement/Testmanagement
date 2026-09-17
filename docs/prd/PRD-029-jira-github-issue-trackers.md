# PRD-029 — Jira and GitHub Issues as Issue Trackers

| | |
|---|---|
| **Status** | ✅ Implemented 2026-09-17 — see §8; manual smoke test against real Jira/GitHub still open |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-17 |
| **Priority** | P1 — the most common reason a team can't adopt the tool |
| **Target** | v2.4 |
| **Related** | PRD-010 (issue-tracker SPI), PRD-026 (Azure DevOps Work Items on the same seam), PRD-001 (RBAC), PRD-028 §8 (User-Agent lesson) |

---

## 1. Summary

PRD-010 built the tracker integration around a pluggable `IssueTrackerProvider` SPI but shipped
only GitLab and Forgejo. `IssueTrackerProviderType` already declares `GITHUB` and `JIRA`, and
`IssueTrackerProviderRegistry.supported()` already hides providers without an adapter. Small
organisations mostly track defects in **Jira** (Cloud or Data Center) or **GitHub Issues**, so today
most of them get a dropdown with nothing useful in it and fall back to pasting a `defectLink`.

This PRD adds two adapters, `JiraIssueProvider` and `GitHubIssueProvider`, both extending
`HttpIssueProviderSupport`. The SPI stays the same. The service, controller, poller and link model
also stay as they are, apart from one nullable column that Jira Cloud's authentication needs.

## 2. Goals & Non-Goals

**Goals**
- Jira Cloud (`*.atlassian.net`) and Jira Data Center / Server 9.x+: search, create, get, test
  connection, and state polling.
- GitHub.com and GitHub Enterprise Server 3.x+: the same four operations.
- Filing from a failed result produces a readable issue body on both, including Jira, which does not
  render Markdown.
- The existing config form, typeahead, chip and state refresh work unchanged for the new providers.

**Non-Goals**
- Two-way sync (comments, assignee, labels, custom fields). As with PRD-010, the tracker stays the
  source of truth and we only store `externalId / url / title / state`.
- Jira custom required fields on create. If a project's create screen requires extra fields, filing
  fails with a clear message and the tester links an issue made in Jira instead (§4).
- Jira OAuth 2.0 (3LO) apps and GitHub Apps. Personal/API tokens are enough for a self-hosted tool
  with one config per project, and an app registration flow is disproportionate.
- Linear (still declared in the enum and still out of scope), and Azure DevOps (PRD-026).
- Mapping Jira priorities or GitHub labels from the test case priority.

## 3. Proposed Design

### 3.1 Data model (next free V-number, V54+ at time of writing)

Jira Cloud authenticates with HTTP Basic `email:apiToken`. Every other provider takes a single
bearer-style token. Rather than packing the email into the encrypted token string, which would be
implicit and would leak a Jira-only parsing rule into the form, add one explicit column:

```sql
ALTER TABLE issue_tracker_configs ADD COLUMN auth_username VARCHAR(255);
```

- Nullable, not secret, returned to project admins in the config response. It is required only when
  `provider = JIRA` and the base URL host ends in `.atlassian.net`, validated in
  `IssueTrackerConfigService`.
- `IssueTrackerConfig` gets the field. `DecryptedConfig` gains an `authUsername()` accessor next to
  `baseUrl()` / `projectRef()`.
- `issue_links.external_id` (VARCHAR 300) already fits both `PROJ-123` and `owner/repo#123`. No change.

### 3.2 `JiraIssueProvider`

| Concern | Decision |
|---|---|
| Flavour detection | Host ends in `.atlassian.net` → Cloud; anything else → Data Center. No extra setting: it's derived and can't drift. |
| Auth | Cloud: `Authorization: Basic base64(authUsername:token)`. DC: `Authorization: Bearer <PAT>` (DC 8.14+). |
| `projectRef` | Jira project key, optionally with an issue type: `PROJ` or `PROJ:Task`. The default issue type is `Bug` (`app.issuetracker.jira.default-issue-type`). |
| Search | Cloud: `GET /rest/api/3/search/jql` (the legacy `/search` was removed on Cloud in 2025). DC: `GET /rest/api/2/search`. JQL: `project = "PROJ" AND (text ~ "<q>" OR key = "<q>")` when `q` looks like a key, otherwise just the `text ~` clause; `fields=summary,status`, `maxResults=20`. JQL string values are escaped (`\` and `"`). |
| Create | `POST /rest/api/2/issue` on both flavours. v2 still accepts a wiki-markup `description` on Cloud, which avoids building Atlassian Document Format. The response only carries `id/key/self`, so the returned `Issue` is assembled from the key plus the draft title with state `OPEN`. That avoids a second round trip. |
| Get | `GET /rest/api/2/issue/{key}?fields=summary,status`. |
| State | `fields.status.statusCategory.key`: `done` → `CLOSED`; `new`, `indeterminate` → `OPEN`; else `UNKNOWN`. Status *categories* are fixed by Jira. Status *names* are per-workflow and must not be matched. |
| URL | `{base}/browse/{key}`. |
| Test connection | `GET /rest/api/2/project/{key}`, then confirm the configured issue type is in the project's `issueTypes`, so "Bug doesn't exist here" shows up at config time and not at the first filing. |
| Body | `IssueDraft.body` is Markdown built by `IssueLinkService.buildBody`. A small `MarkdownToJiraWiki` helper converts only what that method emits: `**bold**` → `*bold*`, `[text](url)` → `[text\|url]`, fenced code → `{code}`. Anything else passes through as plain text. It is package-private and not a general converter. |

**Non-JSON 200s.** Data Center behind SSO or Seraph can answer an unauthenticated call with an HTML
login page and status 200. PRD-026 §3 adds a content-type / non-JSON guard to
`HttpIssueProviderSupport.send` for the same reason. Whichever PRD lands first adds it, and it maps to
"rejected the configured access token".

### 3.3 `GitHubIssueProvider`

| Concern | Decision |
|---|---|
| API base | Admins will paste `https://github.com`, so `https://github.com` maps to `https://api.github.com`. Any other host is GHES and maps to `{base}/api/v3`. The stored `base_url` stays as typed. |
| Auth / headers | `Authorization: Bearer <token>` (fine-grained PAT with *Issues: read & write* on the repo, or a classic PAT with `repo`), `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2022-11-28`, and an explicit `User-Agent: Testmanagement`. GitHub rejects requests without a UA, and PRD-028 §8 showed that default client UAs get blocked by CDNs. |
| `projectRef` | `owner/repo`, split and encoded exactly like `ForgejoIssueProvider.repoApi`. |
| Search | Input `#123` or `123` → direct `get`. Otherwise `GET /search/issues?q=repo:owner/repo is:issue <q>&per_page=20`. `is:issue` excludes pull requests server-side. |
| Create | `POST /repos/{owner}/{repo}/issues` with `title` and `body`. GitHub renders the Markdown body natively. |
| Get | `GET /repos/{owner}/{repo}/issues/{n}`. **This endpoint also returns pull requests.** A node with a non-null `pull_request` is rejected with "#n is a pull request, not an issue", the same guard Forgejo already has. |
| State | `open` → `OPEN`; `closed` (any `state_reason`, including `not_planned`) → `CLOSED`. |
| External id | `owner/repo#123`, matching Forgejo's shape. |
| Test connection | `GET /repos/{owner}/{repo}`, then fail if `has_issues` is false, which is common on forks and would otherwise surface as a 410 at filing time. |

**Rate limits.** The search API allows 30 requests/minute per token, far below the core API's 5,000/hour.
The typeahead is already debounced client-side. GitHub also signals *secondary* rate limits with
**403** plus `retry-after` or `x-ratelimit-remaining: 0`, which `HttpIssueProviderSupport.send` would
currently report as "rejected the configured access token". The adapter overrides status mapping to
check those headers first and raise the existing rate-limit message. The poller's batch
(`poll-batch-size: 50` every 5 minutes) stays well within the core limit.

### 3.4 Endpoints (RBAC via PRD-001)

No new endpoints. Existing ones pick the providers up through the registry:

- `GET /api/projects/{projectId}/issue-tracker/providers` — `@RequireProjectRole(ADMIN)`, now lists
  `GITHUB` and `JIRA`.
- `PUT /issue-tracker`, `POST /issue-tracker/test` — `ADMIN`. The create/update request DTOs gain an
  optional `authUsername`.
- `GET /issues/search` and the link/create endpoints in `IssueLinkController` — unchanged roles.

### 3.5 Frontend

- `issue-tracker.model.ts`: add project-reference hints for `GITHUB` (`owner/repository`) and `JIRA`
  (`project key, optionally KEY:IssueType`), plus `authUsername?` on the config models.
- `issue-tracker-settings.component`: show an "Account email" field only when the provider is
  `JIRA` and the base URL host ends in `.atlassian.net`. Pre-fill the base URL with
  `https://github.com` when `GITHUB` is selected.
- Token help text per provider, linking to the vendor's token page. i18n keys in `en.json` / `de.json`.
- The issue chip, typeahead and "File issue" dialog are provider-agnostic and need no change.

### 3.6 MCP impact

None. `IssueLinkTools` delegates to `IssueLinkService`, so agents can link and file Jira/GitHub issues
as soon as a project is configured.

### 3.7 Docs

`docs/USER_MANUAL.md` issue-tracker section: token scopes per provider, the `KEY:IssueType` form, and
the Jira required-fields limitation.

## 4. Edge Cases

- **Jira create screen requires custom fields** → Jira returns 400 with `errors{field: msg}`. Surface
  the field names ("Jira requires: Components, Fix Version") instead of a bare HTTP 400. The tester can
  still link an issue made in Jira.
- **Jira issue moved to another project** → the key changes. `GET` by the old key follows Jira's
  redirect server-side and returns the new key. `IssueStateRefresher` should persist the returned
  `externalId`/`url` rather than assume they are stable. That is a one-line check, and it also covers
  GitHub issue transfers (which return 301; redirects are not followed, so a transferred GitHub issue
  goes `UNKNOWN`, which is acceptable).
- **Issue deleted** → 404 maps to "not found". The link keeps its last known state and the poller moves
  on, as it does for GitLab.
- **GitHub repo with issues disabled** → caught at test connection (`has_issues`); 410 at runtime maps
  to a clear message.
- **Number typed that is a PR** → rejected, never linked as a defect.
- **Jira DC HTML login page with 200** → non-JSON guard (§3.2), not a parser error.
- **Self-hosted Jira/GHES on a private network** → blocked by `IssueTrackerUrlValidator` unless
  `app.issuetracker.allow-private-targets` is set, same as GitLab.
- **Switching provider on an existing config** → existing `issue_links` keep their own `provider`
  column and stay readable. The poller only refreshes links whose provider matches the active config.
- **JQL injection via search text** → quoted and escaped. The token's own permissions bound what can
  be read anyway.

## 5. Testing

- Adapter unit tests against a local stub HTTP server (the pattern the GitLab/Forgejo tests use):
  - Jira: Cloud vs DC auth header chosen by host, `/search/jql` vs `/search` path, key-shaped query,
    JQL escaping, `statusCategory` mapping, 201 create → `Issue` without a second call, required-field
    400 → readable message, HTML 200 → auth error, issue-type check in test connection.
  - GitHub: `github.com` → `api.github.com` and GHES → `/api/v3`, `#123` shortcut, PR rejection on
    `get`, `has_issues=false`, 403 with `x-ratelimit-remaining: 0` → rate-limit message (not "token
    rejected"), `User-Agent` present.
- `MarkdownToJiraWiki`: each construct `buildBody` emits, plus pass-through of plain text.
- `IssueTrackerConfigService`: `authUsername` required for Jira Cloud only.
- Registry: `supported()` now contains `GITHUB` and `JIRA`.
- Frontend: settings form shows/hides the email field; hints per provider.
- Manual smoke test once against a real Jira Cloud site and github.com, recorded in the PR. Stubs
  cannot catch vendor drift.

## 6. Effort & Risk

- **Effort:** ~5–7 days. GitHub is ~2 days (a near copy of Forgejo). Jira is ~3–4 days (two flavours,
  wiki conversion, required-field errors). Frontend and docs are ~1 day.
- **Risk:** Medium-low. The SPI and failure taxonomy exist and were built for this. The real risks
  are vendor API drift (Jira Cloud's search migration is recent) and 403 ambiguity on GitHub. Both are
  covered by explicit tests and a manual smoke test.

## 7. Acceptance Criteria

- [x] `JiraIssueProvider` supports search, create, get and test connection on Jira Cloud and Data Center.
- [x] `GitHubIssueProvider` supports the same on GitHub.com and GHES, never linking a pull request.
- [x] Jira Cloud config stores the account email in `auth_username`; the token stays encrypted.
- [x] Issues filed to Jira render bold, links and code from the templated body.
- [x] Jira required-field failures and GitHub secondary rate limits produce specific messages.
- [x] Linked-issue state refreshes via the existing poller for both providers.
- [x] Settings form lists both providers with correct hints; en/de translations present.
- [ ] Adapter, config-validation and frontend tests pass (**done**); manual smoke test recorded (**open** — needs a real Jira Cloud site and a GitHub token).

## 8. As Built (2026-09-17)

Built as specified, with these differences:

- **Key-shaped search is a lookup, not JQL.** §3.2 specified `text ~ "q" OR key = "q"`. JQL that
  names an issue key which does not exist is a **400** in Jira, so that query would have failed for
  every near-miss. A key-shaped query is fetched directly (`GET /issue/{key}`, 404 tolerated) and
  the text search is the fallback. The GitHub adapter does the same for `#123`, which also keeps
  numbers off the 30-requests-a-minute search API.
- **`app.issuetracker.jira.default-issue-type` was not added.** The default is `Bug` and
  `KEY:IssueType` already overrides it per project; a second, global knob had no user.
- **Cloud detection is injected for tests.** A stub on 127.0.0.1 can never look like
  `*.atlassian.net`, so `JiraIssueProvider` takes the detector through a package-private constructor.
  The suffix is matched on the *parsed host*, so `atlassian.net.evil.example` is never sent Basic
  credentials; the frontend's `needsAccountEmail` applies the same rule.
- **`HttpIssueProviderSupport` gained two seams** rather than per-adapter copies of `send`: an
  overridable `rejectFailure()` (GitHub's 403-is-a-rate-limit, Jira's 400-names-the-fields) and
  `getJsonIfFound()`, where "no such issue" has to stay distinguishable from "token rejected". The
  HTML-with-200 guard from §3.2 lives there too, so PRD-026 inherits it.
- **The moved-issue follow-up (§4) respects the unique key.** `(test_result_id, external_id)` is
  unique, so the refresher does not rename a link into an id the same result already links.
- **The account email is dropped on save for every tracker but Jira Cloud**, so switching a
  project's tracker does not leave a stale address in the row.
- **Project-reference hints stayed where they were** — English strings in `issue-tracker.model.ts`,
  the existing pattern — while the new per-tracker token help is translated (en/de).

Tests: `GitHubIssueProviderTest` (15), `JiraIssueProviderTest` (19), `MarkdownToJiraWikiTest` (7),
four account-email cases in `IssueTrackerApiTest`, two moved-issue cases in
`IssueStateRefresherTest`, and `issue-tracker-form.spec.ts`. `unsupportedProviderIsRejected…` now
uses `LINEAR`, the one provider still declared without an adapter. **797 backend tests, 21 frontend
spec files.**

**Still open:** the manual smoke test. Stubs cannot catch vendor drift, and Jira Cloud's search
migration is recent.
