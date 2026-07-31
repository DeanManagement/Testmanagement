# PRD-017 — Authenticated Media Caching Fix

| | |
|---|---|
| **Status** | Implemented (2026-06-10) |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-10 |
| **Priority** | **P0 — Security (authorization bypass)** |
| **Target** | v1.4 |
| **Related** | CODE_REVIEW_2026-06-09.md §C1; PRD-001 (RBAC) |

---

## 1. Summary

The nginx layer caches `/api/screenshots/{id}` and `/api/step-images/{id}` responses in a **shared proxy cache keyed only by URI** (`proxy_cache_key $uri`). After any authorized user fetches an image, **any subsequent request for the same URI — including unauthenticated ones — is served from the nginx cache without ever reaching the backend's `ProjectAccessService` check**. The PRD-001 RBAC enforcement on these endpoints is effectively bypassed for warm cache entries. The backend compounds this by sending `Cache-Control: public, immutable`, which authorizes *any* intermediary (corporate proxies, CDNs) to do the same.

## 2. Current State (verified)

- `frontend/nginx.conf`: image location block with `proxy_cache images`, `proxy_cache_key $uri`, 7-day validity; no auth component in the key, no `proxy_cache_bypass`.
- `ScreenshotController.download` / `StepImageController.download`: `CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()` + ETag/`If-None-Match` 304 handling.
- Access checks themselves are correct (service layer, `requireRoleForCurrentUser(projectId, VIEWER)`) — they are just never reached on cache hits.

## 3. Goals & Non-Goals

**Goals**
- Every image byte served passes the backend authorization check.
- Keep image delivery fast (the ETag/304 path already avoids re-sending bytes).
- Correct `Cache-Control` semantics for authenticated content everywhere.

**Non-Goals**
- A general CDN/caching strategy (not needed at ~5 concurrent users).
- Changing BYTEA storage or the image endpoints' API.

## 4. Proposed Design

### 4.1 nginx (primary fix)
Remove the proxy cache from the image location entirely:

- Delete `proxy_cache_path` directive and the `proxy_cache*` / `add_header X-Cache-Status` lines from the `^/api/(screenshots|step-images)/...` block (or delete the whole block — the generic `/api/` proxy then applies).
- Performance is preserved by the existing conditional-request flow: browser caches the image privately, revalidates with `If-None-Match`, backend answers 304 without a body.

*(Rejected alternative: keying the cache by `$uri$http_authorization`. It technically works but caches one copy per token, leaks tokens into cache files on disk, and provides no real benefit at this scale.)*

### 4.2 Backend headers
In `ScreenshotController` and `StepImageController`, change
`CacheControl.maxAge(365 days).cachePublic().immutable()` → `.cachePrivate().immutable()` (keep max-age + ETag). `private` forbids shared caches while keeping the browser cache hot.

## 5. Edge Cases

- Warm caches on already-deployed instances: the cache lives in the container at `/tmp/nginx_image_cache`; redeploying the frontend image clears it. Note in release notes.
- Browser back/forward: unaffected — private caching + immutable still applies.
- Same-id re-upload (screenshot replaced): ETag is `updatedAt`-based, so revalidation already handles it.

## 6. Testing

- Integration: fetch screenshot with valid token (200) → fetch same URI with **no** token → must be 401, not 200. Run against the docker-compose stack (this bug is invisible to backend-only tests — add a compose-level smoke test script or document a manual release check).
- Unit: response header assertion `Cache-Control: private, max-age=31536000, immutable` on both controllers.
- Regression: ETag/304 flow still returns 304 with `If-None-Match`.

## 7. Effort & Risk

- **Effort:** S — ~half a day including the compose-level test.
- **Risk:** Low. Removing a cache cannot break correctness; only theoretical perf impact, mitigated by 304s.

## 8. Acceptance Criteria

- [x] Unauthenticated request for a previously-fetched screenshot/step-image URI returns 401/403 through the full nginx → backend stack (smoke script; backend-level covered by test).
- [x] `proxy_cache` no longer applied to `/api/**` in nginx.conf.
- [x] Image responses carry `Cache-Control: private, …, immutable`.
- [x] ETag/304 revalidation still works (test green).

## 9. Implementation Notes (as shipped)

- `frontend/nginx.conf`: removed the `proxy_cache_path` directive and the dedicated `^/api/(screenshots|step-images)/...` location block entirely; the generic `/api/` proxy handles those URIs. A comment in the file warns against reintroducing `proxy_cache` on `/api/` (this PRD).
- `ScreenshotController` / `StepImageController`: `cachePublic()` → `cachePrivate()` (max-age 365 d + `immutable` + ETag retained), with explanatory comments.
- New test `MediaCacheHeadersApiTest` (5 tests): Cache-Control is `private`/not `public` on both endpoints, ETag 304 revalidation, anonymous rejected, non-member rejected. Full suite: 169 tests green.
- New `scripts/smoke-media-auth.sh`: compose-level release check — warms an image URI with a token, then asserts the unauthenticated request is rejected and no `X-Cache-Status` header appears. Run against a deployed stack (`./scripts/smoke-media-auth.sh http://localhost:8012 <screenshotId> <jwt>`); intended for the PRD-023 compose smoke job.
- Ops note: existing deployments hold warm cache entries in the old frontend image's `/tmp/nginx_image_cache`; rebuilding/redeploying the frontend container clears them.
