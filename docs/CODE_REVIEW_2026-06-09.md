# Code Review — 2026-06-09

_Scope: full working tree (including uncommitted PRD-001…007 work). Backend, frontend, infra._
_All Critical/High findings below were verified directly in the code; nothing is reported from inference alone._

---

## 1. Executive summary

Since the May review the project has closed nearly every gap that report flagged: webhooks, import/export, CI ingestion, server-side search, watcher notifications, `@RequireProjectRole` RBAC, keyboard shortcuts, command palette, error interceptor. The architecture is in good shape — service-level access checks, SSRF-validated webhooks, XXE-safe XML parsing, membership-scoped search.

The remaining problems cluster in three places:

1. **Two exploitable security chains** — an nginx cache that bypasses authorization on images, and a stored-XSS-to-token-theft chain through Allure report uploads.
2. **Shipped default secrets** — docker-compose and application.yml contain working default credentials.
3. **Frontend subscription hygiene** — zero uses of `takeUntilDestroyed` in the whole app; leaks are systemic, not incidental.

---

## 2. Critical findings

### C1 — nginx image cache bypasses authorization
`frontend/nginx.conf` (image location block)

```nginx
proxy_cache images;
proxy_cache_key $uri;
```

`/api/screenshots/{id}` and `/api/step-images/{id}` responses are cached in a **shared** nginx cache keyed only by URI. After any authorized user fetches an image, **any request — including unauthenticated ones — gets the cached bytes without ever reaching the backend's access check**. The backend makes it worse by sending `Cache-Control: public, immutable` (`ScreenshotController.download`), which licenses any intermediary to do the same.

**Fix:**
- Remove `proxy_cache` from this location, or add `proxy_cache_key $uri$http_authorization;` plus `proxy_no_cache $arg_nocache` — simplest correct option is to drop the proxy cache and rely on the existing ETag/304 flow, which already avoids re-sending bytes.
- Change backend headers to `CacheControl.maxAge(...).cachePrivate()` and keep `immutable`.

### C2 — Allure upload → stored XSS → token theft
`AllureReportController.viewFile` (`/view/**`), `allure-report-viewer.component.ts:52`, `auth.service.ts` (localStorage)

Any project TESTER can upload a ZIP containing arbitrary HTML/JS. It is served **same-origin** under `/api/projects/.../allure-report/view/**`. The viewer iframes it without `sandbox`. Because the JWT lives in `localStorage` on the same origin, a script inside an uploaded report can read `localStorage.auth_token` of whoever views it (e.g. an admin) and exfiltrate it → privilege escalation. Additionally the JWT is passed as `?token=` in the iframe URL — it lands in browser history and any proxy/access logs (the 5-minute HttpOnly path-scoped cookie handshake is good, but the URL exposure remains).

**Fix (layered):**
1. `<iframe sandbox="allow-scripts" ...>` — *without* `allow-same-origin`, scripts run in an opaque origin and cannot touch localStorage or cookies.
2. Serve `/allure-report/view/**` with `Content-Security-Policy: sandbox allow-scripts` header as defense in depth.
3. Replace `?token=` with a one-time short-lived view-token endpoint (`POST /allure-report/session` with Authorization header → sets the cookie → iframe loads with no token in URL).

### C3 — Working default secrets shipped in the repo
`docker-compose.yml`, `application.yml`

```yaml
JWT_SECRET: ${JWT_SECRET:-defaultSecretKeyForDevelopmentOnly...}   # compose
password: ${ADMIN_PASSWORD:admin}                                  # application.yml
POSTGRES_PASSWORD: ${DB_PASSWORD:-testmanagement}
```

Every unmodified deployment shares a **publicly known JWT signing key** (anyone can forge any user's token, including admin) and an `admin@localhost.ch / admin` account. `JwtConfig` already fails fast on a blank secret — the compose default defeats that safeguard.

**Fix:** remove the compose fallback for `JWT_SECRET` and `DB_PASSWORD` (use `${JWT_SECRET:?set in .env}` syntax to hard-fail), remove the `admin` default password and instead generate a random one logged once at first start (or force `ADMIN_PASSWORD` to be set), ship a `.env.example`. `force_password_change` already exists (V31) — set it for the seeded admin.

---

## 3. High findings

| # | Finding | Evidence | Fix |
|---|---|---|---|
| H1 | **No login rate limiting.** `AuthService.login` does BCrypt check with no attempt counting, lockout, or delay. | `user/internal/services/AuthService.java` | Per-user+IP sliding-window counter (in-memory is fine at ~50 users); exponential backoff after 5 failures; audit-log failures. |
| H2 | **Watchers don't check entity access.** `WatcherService.watch()` persists `(userId, entityType, entityId)` without verifying the user can see the entity → any user can watch arbitrary UUIDs and receive notification payloads (titles, status changes) for projects they're not in. | `WatcherService.java:22-31`, contrast with `ScreenshotService` which does check | Resolve entity → project, then `projectAccessService.requireRoleForCurrentUser(projectId, VIEWER)` before saving. Same check in `isWatching`/`unwatch` is harmless but watch is the leak. |
| H3 | **API keys are global.** `ApiKey` entity has no project association — any key can ingest results into any project via the external API. | `entity/ApiKey.java` | Add `project_id` (nullable = legacy/global), enforce in `ApiKeyAuthenticationFilter`/external controllers; UI already has per-key management to surface it. |
| H4 | **Systemic frontend subscription leaks.** `grep takeUntilDestroyed → 0 hits` app-wide. Concrete: `test-case-list.component.ts:142,147,168` (folders$, queryParamMap, search debounce), `test-run-detail.component.ts:141`, `notification-bell`, `command-palette.component.ts:150,155`, dialog `afterClosed()` everywhere. Also the anti-pattern `subscribe(...).unsubscribe()` in `project-detail.component.ts:114-121` (sync unsubscribe works by accident on a BehaviorSubject-backed store, but is fragile). | multiple | Adopt one pattern: `takeUntilDestroyed(this.destroyRef)` for streams; `store.selectSignal(...)` for state reads (fits zoneless and removes most subscriptions outright); `take(1)` for dialogs/one-shot HTTP. |
| H5 | **401 handling can loop / lacks expiry awareness.** `error.interceptor.ts:22-24` logs out and navigates to `/login` without checking the current route; token expiry (24h) is never checked client-side, so the first action after expiry produces an error flash. | `core/interceptors/error.interceptor.ts` | Guard `if (!router.url.startsWith('/login'))`; decode `exp` on app init and schedule proactive logout/refresh. |
| H6 | **Docker runtime hardening + port mismatch.** No `USER` in either Dockerfile (root); no `restart:` policies; backend has no healthcheck; Postgres published to host `5432:5432`; compose maps backend `8089:8080` but Spring listens on **8089** in-container (`server.port: 8089`) so the host mapping points at nothing (traffic happens to work only via the frontend nginx proxy to `:8089`); stale `KEYCLOAK_*` env vars for a feature that no longer exists. | `docker-compose.yml`, `backend/Dockerfile` (EXPOSE 8080), `frontend/Dockerfile` | Add non-root users, `restart: unless-stopped`, actuator-based healthcheck, fix mapping to `8089:8089` + `EXPOSE 8089` (or set `SERVER_PORT=8080`), remove Postgres host port (or bind `127.0.0.1:5432:5432`), delete Keycloak vars. |
| H7 | **CSV export formula injection.** No `=`/`+`/`-`/`@` prefix escaping found in `TestCaseImportExportService` — a test case titled `=HYPERLINK(...)` executes when the export is opened in Excel. | `TestCaseImportExportService.java` | Prefix cells starting with `= + - @ \t \r` with `'` on export. |

---

## 4. Medium findings

- **M1 — No security headers in nginx.** Missing `X-Content-Type-Options: nosniff`, `X-Frame-Options`/`frame-ancestors`, `Referrer-Policy`, CSP for the app shell; no gzip for the Angular bundles. (`frontend/nginx.conf`)
- **M2 — JWT lifecycle.** 24 h tokens, no refresh, no server-side revocation — logout is purely client-side; a stolen token is valid for a day. Acceptable for an air-gapped LAN tool, but document it; consider shorter expiry + sliding refresh.
- **M3 — Token in localStorage.** Amplifies any XSS (see C2). With C2 fixed the residual risk is acceptable for this product class; the robust alternative is an HttpOnly cookie + CSRF token.
- **M4 — Hardcoded English strings** in `error.interceptor.ts` (lines 25/37/42) while everything else uses ngx-translate; native `confirm()` used in `test-case-list.component.ts` (251, 337) instead of a Material confirm dialog (untranslatable styling, inconsistent UX, not testable).
- **M5 — Unbounded cross-page bulk selection.** Selection set can grow across pages and is posted wholesale to bulk endpoints. Cap it or scope selection to the current page with a "select all matching filter" server-side path.
- **M6 — Dirty flag not restored on failed save** (`test-case-form.component.ts` ~182-192): submit clears `dirty` before the request; on error the unsaved-changes guard no longer protects the user. Reset `dirty = true` in the error handler.
- **M7 — `fail-on-missing-locations: false`** in Flyway config silently tolerates a misconfigured migration path. Set `true`; the `{vendor}` location exists for both vendors now.
- **M8 — Actuator exposure unspecified.** No `management.endpoints` config — verify only `health` is exposed in prod; pin it explicitly.
- **M9 — Repo hygiene.** Stray `backend/.attach_pid*`, `.fuse_hidden*` files; `frontend/dist/` and `.angular/` present in the tree; `Code_Review_Report.docx` / `REVIEW_AND_PROPOSALS.md` at root. Extend `.gitignore`, move review docs to `docs/`.
- **M10 — No CI pipeline.** No GitHub Actions/GitLab CI: tests only run when someone remembers. Given the test suite is now substantial (25 test classes incl. integration tests), this is cheap, high-value.
- **M11 — Allure zip decompression limits.** Upload is capped at 10 MB (multipart), but entries are decompressed on demand without a per-entry decompressed-size cap — a high-ratio zip can inflate to hundreds of MB per request. Cap decompressed entry size (e.g. 20 MB) in `AllureReportService`.

---

## 5. Low / polish

- Blob URL revoked synchronously after `a.click()` in `test-run-report.component.ts` (~64-70) — defer with `setTimeout`.
- Command palette caches store items and never clears on logout (`command-palette.component.ts:147`).
- `error.interceptor` matches the login route by string `req.url.includes('/api/auth/login')` — brittle; centralize route constants.
- No Angular `environment.ts` files; API base is implicit `/api` + dev proxy. Fine today, but document it.
- Docs drift: `CLAUDE.md` still says "Auth: OIDC via Keycloak (external)" and "Flyway migrations (V1-V10)" — reality is local JWT and V1-V37. REQUIREMENTS.md is current; CLAUDE.md is not.

---

## 6. What's good (keep doing this)

Service-level access checks on binary endpoints with explanatory comments (`ScreenshotService`, PRD references); fail-fast JWT secret validation; SSRF guard with explicit opt-in for private targets; HMAC-signed webhooks with bounded retry/backoff; XXE-hardened parsers; `default_batch_fetch_size` to kill N+1; `open-in-view: false`; ETag/304 on images; membership-scoped full-text search with LIKE fallback for non-Postgres; modulith package structure with `internal` enforcement and a `ModularityTests`; the PRD-driven docs folder.

---

## 7. Proposed fix order

1. **Day 1 (security, ~half day):** C1 nginx cache, C3 secrets/compose, H2 watcher check, H7 CSV escaping.
2. **Day 2:** C2 Allure sandbox chain (iframe `sandbox` + CSP header is 1 h; the token-handshake endpoint another 2-3 h), H1 login throttle.
3. **Week 1:** H4 subscription sweep (mechanical: `selectSignal` + `takeUntilDestroyed`), H5 interceptor, H6 Docker hardening + port fix, M10 CI pipeline.
4. **Then:** H3 project-scoped API keys (needs a migration + small UI change), remaining mediums.

---

## 8. Proposed extensions

The PRD backlog (009-016) already names the right candidates. Suggested priority given current state:

1. **CI pipeline + release versioning** (not in any PRD, biggest gap): GitHub Actions — backend `mvnw verify`, frontend `ng test` + build, Docker image build, Trivy scan, Dependabot. Tag-driven releases with pinned image digests.
2. **PRD-012 OIDC/SSO** — the codebase clearly migrated from Keycloak to local JWT; reintroducing OIDC as an *optional* second auth path (spring-security oauth2-client, map by email) is the most-requested enterprise feature for self-hosted tools and the config remnants show it was planned.
3. **PRD-016 Flaky test detection** — cheap now that CI ingestion exists: a rolling window over `TestResult` status flips per test-case key, surfaced as a badge + dashboard widget. High tester value.
4. **PRD-011 Test case versioning** — results currently point at mutable test cases; an executed run should reference the step text as it was. Snapshot steps into the `TestResult` at run creation (lighter than full versioning, solves 90% of the audit need).
5. **PRD-014 Traceability matrix** — requirements field already exists on test cases? If not: a simple `requirement_ref` label convention + a matrix report view (test case × requirement × latest result) reuses existing report infra.
6. **Backup/restore story** — documented `pg_dump` sidecar or cron container + restore runbook in README. For an air-gap-capable tool this is table stakes.
7. **PRD-013 Dark mode** — Material theming is mostly wired; low effort, high perceived polish.

---

_Verification notes: every Critical/High was confirmed by reading the cited file in the working tree on 2026-06-09. Findings from sub-reviews that did **not** survive verification were dropped — e.g. "Screenshot endpoints missing RBAC" (access is enforced in the service layer) and "JWT secret accepts weak keys" (length is validated at startup)._
