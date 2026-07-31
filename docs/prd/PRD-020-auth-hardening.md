# PRD-020 — Auth Hardening: Login Throttling & JWT Lifecycle

| | |
|---|---|
| **Status** | Implemented (2026-06-10) — see §9 |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-10 |
| **Priority** | P1 — Security |
| **Target** | v1.4 |
| **Related** | CODE_REVIEW_2026-06-09.md §H1, §M2, §M3; AuthService; JwtConfig |

---

## 1. Summary

`POST /api/auth/login` accepts unlimited attempts — passwords can be brute-forced at network speed. Tokens live 24 h with no server-side revocation (logout is purely client-side: a stolen token stays valid for a day), and the client never checks expiry, so the first action after expiry surfaces as a confusing error.

## 2. Current State (verified)

- `AuthService.login`: BCrypt verify, mint HS256 JWT (`expiration-ms: 86400000`), no attempt tracking, no lockout, no delay, failures not audit-logged.
- `JwtAuthenticationFilter`: validates signature + expiry; no revocation list.
- Frontend `auth.service.ts`: token in localStorage, no `exp` awareness; `error.interceptor.ts` reacts to 401 after the fact.

## 3. Goals & Non-Goals

**Goals**
- Brute-force attempts become impractical and visible (audit log).
- Logout actually invalidates the session server-side.
- Client knows when its token expires and acts before requests fail.

**Non-Goals**
- Full refresh-token rotation infrastructure (disproportionate at ~50 users; revisit with PRD-012 OIDC, which delegates session lifecycle to the IdP).
- Moving the token out of localStorage into HttpOnly cookies + CSRF (bigger change; PRD-018 removes the acute XSS amplifier — keep this as a noted v2 option).
- MFA (PRD-012 / IdP territory).

## 4. Proposed Design

### 4.1 Login throttling
- In-memory sliding-window counter keyed by `(emailLowercase)` and `(remoteIp)` — a small `ConcurrentHashMap`-based component, no new dependency, fine at this scale (single instance).
- Policy: ≥5 failures in 15 min → reject with 429 and exponential backoff (1 s, 2 s, 4 s… cap 60 s) on subsequent attempts; counter reset on success.
- Identical response body/timing for "unknown email" vs "wrong password" (verify BCrypt against a dummy hash for unknown users to keep timing flat).
- Failures and lockouts written to the audit log (`AuditAction.LOGIN_FAILED` — new enum value, user-less project-less entry or a dedicated auth log table; prefer reusing `audit_entries` with nullable project).

### 4.2 Token lifecycle
- Add a `token_version` (int, default 0) column on `users` (migration). Embed it as a JWT claim; `JwtAuthenticationFilter` rejects tokens whose claim ≠ current column value.
- `POST /api/auth/logout` (new): increments the caller's `token_version` → all existing tokens die. Also incremented on password change (including forced change) and on user deactivation by an admin.
- Cost: one indexed read per request; acceptable here. (Cache user-version in a 30 s in-memory cache if it ever shows up in profiles.)
- Reduce default `expiration-ms` to 12 h (working day + margin); keep configurable.

### 4.3 Client expiry awareness
- `auth.service.ts`: decode `exp` on init/login; schedule auto-logout (with snackbar) ~1 min before expiry; treat an already-expired stored token as logged-out on boot.
- `error.interceptor.ts`: on 401, skip logout/navigation if already on `/login` (fixes the redirect-loop finding §H5 — implemented here since both touch the same flow).

## 5. Edge Cases

- Shared office IP: per-IP threshold must be looser than per-email (e.g. 20/15 min) to avoid one tester locking out the office.
- In-memory counters reset on backend restart — acceptable; an attacker forcing restarts has bigger access already.
- `token_version` bump while a user has multiple tabs: all tabs get 401 → interceptor logs out cleanly (verify no loop).
- Clock skew: keep the existing JWT clock-skew tolerance; client-side schedule uses server `exp` minus a safety margin.

## 6. Testing

- Throttle unit tests: lockout after N failures, backoff growth, reset on success, per-IP vs per-email independence, flat timing for unknown email (assert dummy-hash path taken).
- Filter test: stale `token_version` → 401.
- Logout integration: login → logout → old token rejected.
- Password change invalidates tokens.
- Frontend: expired stored token at boot → redirected to login without error flash; 401 on login page does not navigate.

## 7. Effort & Risk

- **Effort:** M — throttle ~3 h, token_version + logout ~4 h, client expiry ~2 h, tests ~3 h.
- **Risk:** Low. All additive; the only behavior change users see is logout becoming real and a 12 h default session.

## 8. Acceptance Criteria

- [x] 6th failed login within 15 min returns 429 (+ `Retry-After`); success resets; even the correct password is rejected while locked out. Failures/lockouts are SLF4J-warn logged — *deviation:* `audit_entries.project_id` is `NOT NULL`, so reusing that table would need a schema change; deferred (note below).
- [x] Response body identical for unknown email vs wrong password (timestamp aside, test-asserted); BCrypt runs against a dummy hash on unknown emails for flat timing.
- [x] Server-side logout invalidates existing tokens (`users.token_version`, V38; claim checked on every request); password change rotates the version and returns a fresh token so the active session survives.
- [x] Client clears already-expired tokens at boot, auto-logs-out ~1 min before expiry with a snackbar, and the 401 interceptor no longer navigates when already on /login (loop fixed); 429 surfaces a translated "too many attempts" message (en+de).

## 9. Implementation Notes (as shipped)

- `LoginThrottleService` (Clock-injected): sliding 15-min window, 5 failures/email (case-insensitive) and 20/IP; lockout until the oldest failure leaves the window (simpler than per-attempt exponential backoff, same effect at this scale); `Retry-After` header via new `TooManyRequestsException` → 429 in `GlobalExceptionHandler`. In-memory by design (single instance; restart resets counters — accepted).
- IP comes from `request.getRemoteAddr()`; correct behind the bundled nginx because `forward-headers-strategy: native` is set.
- `users.token_version` (V38): embedded as a JWT claim; `JwtAuthenticationFilter` loads the user per request and rejects stale/missing-user tokens (pre-feature tokens count as version 0, so sessions survive the upgrade). `POST /api/auth/logout` bumps it; `change-password` bumps it and returns `{token}` (response changed from 204 to 200).
- Default expiry now 12 h (`JWT_EXPIRATION_MS` overridable).
- Frontend: `AuthService` decodes `exp`, schedules pre-expiry auto-logout, stores the rotated token after password change; `clearLocalSession()` (no doomed server call) used by the interceptor; bonus from PRD-022 scope: all interceptor messages now translated (en+de).
- Tests: `LoginThrottleServiceTest` (6), `AuthFlowApiTest` (5, real Bearer-token round trips through the filter). Full suite: 201 green.
- **Follow-up (small):** persist auth failures to the audit log once `audit_entries.project_id` is made nullable (or a dedicated `auth_events` table is added).
