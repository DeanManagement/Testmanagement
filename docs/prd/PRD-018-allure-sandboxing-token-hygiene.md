# PRD-018 — Allure Report Sandboxing & Token Hygiene

| | |
|---|---|
| **Status** | Implemented (2026-06-10) — see §9 for design deviations |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-10 |
| **Priority** | **P0 — Security (stored XSS → token theft)** |
| **Target** | v1.4 |
| **Related** | CODE_REVIEW_2026-06-09.md §C2, §M11; AllureReportController; allure-report-viewer.component |

---

## 1. Summary

Any project TESTER can upload an Allure ZIP containing arbitrary HTML/JS. The report is served **same-origin** under `/api/projects/{id}/test-runs/{key}/allure-report/view/**` and rendered in an **unsandboxed iframe**. Because the JWT lives in `localStorage` on that same origin, a script inside an uploaded report can read the token of whoever views the report (e.g. a system admin) and exfiltrate it → privilege escalation from TESTER to any viewer's role. Additionally, the viewer passes the JWT as `?token=` in the iframe URL, leaking it into browser history and proxy/access logs.

## 2. Current State (verified)

- `AllureReportController.viewFile` serves zip entries with content-type mapping; a `?token=` query parameter is converted into a 5-minute, path-scoped, HttpOnly `allure_session` cookie (good mechanism, wrong delivery: the token still transits the URL).
- `JwtAuthenticationFilter.resolveAllureToken` accepts the cookie for `/view/**` paths.
- `allure-report-viewer.component.ts:52`: `bypassSecurityTrustResourceUrl(baseUrl + '?token=' + token)`, iframe without `sandbox`.
- Zip entries are decompressed on demand with no per-entry decompressed-size cap (10 MB multipart limit applies to the *compressed* upload only) — zip-bomb DoS vector.

## 3. Goals & Non-Goals

**Goals**
- Scripts inside uploaded reports must not be able to read the app's localStorage, cookies, or make credentialed same-origin requests as the viewer.
- The long-lived JWT never appears in a URL.
- Bounded decompression cost per request.

**Non-Goals**
- Sanitizing/rewriting Allure HTML (fragile; sandboxing is the robust boundary).
- Serving reports from a second domain (correct but disproportionate for a self-hosted single-host deployment; sandbox + CSP achieves the same isolation).

## 4. Proposed Design

### 4.1 Iframe sandbox (frontend)
`<iframe [src]="..." sandbox="allow-scripts allow-popups">` — **deliberately without `allow-same-origin`**. The report then runs in an opaque origin: it can execute its own JS (Allure needs this) but cannot touch `localStorage`, cookies, or the parent DOM.

### 4.2 CSP sandbox header (backend, defense in depth)
`AllureReportController.viewFile` adds `Content-Security-Policy: sandbox allow-scripts` on every `/view/**` response. This enforces the same isolation even if someone opens the report URL directly in a tab (bypassing the iframe).

### 4.3 One-time session handshake (replaces `?token=`)
1. New endpoint `POST /api/projects/{id}/test-runs/{key}/allure-report/session` — authenticated via the normal `Authorization` header, `@RequireProjectRole(VIEWER)`. Sets the existing path-scoped, HttpOnly, 5-min `allure_session` cookie (value: a freshly minted short-lived token or the JWT itself — reuse current cookie logic) and returns 204.
2. Viewer component calls this via HttpClient (auth interceptor attaches the header), then sets the iframe `src` to the plain view URL — **no query parameter**.
3. Remove the `?token=` handling from `viewFile` and `resolveAllureToken`'s query-param path.
4. Cookie must also set `SameSite=Strict`.

### 4.4 Decompression cap
In `AllureReportService.getFileFromReport`, stream-copy with a hard cap (e.g. 20 MB decompressed per entry); abort with 413 beyond it. Also reject zips with > ~5 000 entries at upload time.

## 5. Edge Cases

- Allure reports that legitimately use `fetch` for their own JSON: still works — sandboxed fetches to the same path-scoped `/view/**` URLs carry the cookie? **No** — opaque origin requests are uncredentialed. Mitigation: Allure's static HTML report (`allure-generate`d single-page) loads data via relative `<script>`/XHR; verify with a real report during implementation. If credentialed subresource loads fail under sandbox, fall back to `allow-same-origin` **plus** moving the app JWT out of reach (PRD-020 token storage) — decide at implementation with a real report as the test artifact.
- Cookie expiry mid-viewing (5 min): navigation within the report re-fetches entries → 401. Viewer should re-call the session endpoint on iframe load error, or bump cookie lifetime to 30 min.
- Old links containing `?token=` after upgrade: parameter is ignored; viewer flow re-establishes the session transparently.

## 6. Testing

- Upload a crafted report containing `<script>parent.localStorage.getItem('auth_token')</script>` + exfil beacon; assert the script cannot read the token (manual/e2e).
- Controller test: `/view/**` responses carry the CSP sandbox header; `?token=` no longer sets a cookie.
- Session endpoint: role enforced (VIEWER of that project), cookie attributes (HttpOnly, path, SameSite, max-age).
- Zip-bomb fixture: high-ratio entry rejected with 413; upload with excessive entry count rejected.
- Happy path: real Allure report renders and navigates correctly under sandbox.

## 7. Effort & Risk

- **Effort:** M — sandbox + CSP ~1 h; session endpoint + viewer change ~3 h; decompression caps ~2 h; real-report verification ~2 h.
- **Risk:** Medium — the only open question is Allure-report compatibility under `sandbox` (see §5 first bullet); the PRD defines the fallback.

## 8. Acceptance Criteria

- [x] A malicious uploaded report cannot read the viewer's localStorage/cookies — iframe `sandbox="allow-scripts allow-popups"` without `allow-same-origin` (opaque origin) + CSP sandbox header on every /view response.
- [x] No JWT ever appears in a URL — the `?token=` flow and the `allure_session` cookie are removed entirely; viewing uses short-lived single-report path tokens minted via an authenticated `POST /session`.
- [x] `/view/**` responses carry `Content-Security-Policy: sandbox allow-scripts allow-popups` and `X-Content-Type-Options: nosniff` (test-asserted).
- [x] Decompressed entry size (20 MB) and entry count (5 000) are capped; zip-bomb and entry-flood fixtures rejected (`AllureReportZipLimitsTest`).
- [x] Report renders under sandbox: document/script/img loads are unaffected; the report's internal JSON fetches work because the view path has an uncredentialed CORS wildcard (opaque origin sends `Origin: null`) — asserted in `AllureReportViewApiTest`. Verify once with a real Allure zip on a deployed stack as a release check.

## 9. Implementation Notes (as shipped)

- **Design deviation from §4.3 (cookie handshake):** a sandboxed iframe has an *opaque origin* that never sends cookies, so the cookie approach cannot work together with the sandbox. Instead, `POST …/allure-report/session` (`@RequireProjectRole(VIEWER)`) mints a 32-byte random, 30-minute, single-report token kept in memory (`AllureViewSessionService`, Clock-injected for tests), and the view route became `GET …/view/{token}/**`. Relative asset/data fetches inside the report automatically stay under the token prefix. A leaked view token grants read-only access to one report for ≤30 min — strictly better than the leaked 24 h JWT it replaces.
- Security config permits `GET …/allure-report/view/**` at the filter level; the controller enforces the view token (403 otherwise). The `allure_session` cookie branch was removed from `JwtAuthenticationFilter` (header-only now, and invalid header tokens now always get 401).
- CORS: a dedicated uncredentialed `*` mapping for the view path (more specific pattern registered first) so `Origin: null` fetches pass; the credentialed app-wide config is unchanged.
- Frontend: viewer calls the session endpoint via the auth interceptor, then sets the iframe `src` to the token URL; iframe carries `sandbox="allow-scripts allow-popups"`; the `AuthService`/`?token=` usage is gone; subscription uses `takeUntilDestroyed` (first PRD-022-style cleanup).
- **Bugfix found by tests:** the zip path-traversal check rejected *all* zips with `index.html` at the root (`Path("x").startsWith(Path(""))` is `false`), so root-level reports always 500'd. Fixed with an explicit empty-base branch + absolute-path rejection, covered by `view_rejectsPathTraversal` and the root-level fixture in the API test.
- Tests: `AllureReportViewApiTest` (7), `AllureViewSessionServiceTest` (3), `AllureReportZipLimitsTest` (3). Full suite: 190 green; frontend builds clean.
