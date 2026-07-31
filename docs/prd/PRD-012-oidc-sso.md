# PRD-012 — OIDC / Keycloak Support

| | |
|---|---|
| **Status** | Proposed |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-09 |
| **Priority** | P3 — v2.0, enterprise driver-dependent |
| **Target** | v2.0 |
| **Related** | REQUIREMENTS.md §13.5 (was PRD-009 §2.3); interacts with PRD-001 |

---

## 1. Summary

Authentication is currently local (email/password → JWT issued by the app). Enterprises with an existing IdP (Keycloak, Azure AD, Okta, etc.) want SSO. This PRD adds Spring Security OIDC **alongside** local auth, selected by `app.auth.mode=local|oidc`, so air-gapped installs keep local accounts and enterprises can federate — without forking the codebase.

Crucially, **project authorization stays local**: OIDC establishes *identity*; project roles remain `ProjectMember` rows (PRD-001). System-admin can still be granted locally or mapped from a configurable claim/group.

## 2. Goals & Non-Goals

**Goals**
- `app.auth.mode=oidc` enabling Authorization Code + PKCE login against a configured issuer.
- Just-in-time user provisioning: on first OIDC login, create/update a `User` from token claims (email, display name, subject).
- Map a configurable claim/group to the `systemAdmin` flag (optional).
- `local` mode unchanged and remains the default.

**Non-Goals**
- SAML.
- Per-project role mapping from IdP groups (roles stay local in v1 of this feature; could extend later).
- Removing local auth.

## 3. Proposed Design

### 3.1 Config
- `app.auth.mode` (`local` default | `oidc`).
- Standard Spring `spring.security.oauth2.client.*` / `...resourceserver.*` registration for the issuer.
- `app.auth.oidc.admin-claim` / `admin-claim-value` (optional) to grant `systemAdmin`.
- `app.auth.oidc.unique-claim` (default `sub`) to key the local user.

### 3.2 Backend
- Add a second `SecurityFilterChain` activated under `oidc` mode (`@ConditionalOnProperty`), validating IdP-issued JWTs (resource-server) for `/api/**`; the existing local JWT chain is active under `local`. The API-key chain (`/api/external/**`) is unchanged in both modes.
- `OidcUserSyncService`: on authenticated request with no matching local user, JIT-create a `User` (email, displayName from claims, `systemAdmin` from admin-claim) keyed by `unique-claim`; subsequent logins update profile fields.
- The principal remains the local `User.id` (so `@RequireProjectRole`, `/api/me/*`, audit `userId` all keep working unchanged) — resolve/My-create the local user early in the chain and set the principal name to the local UUID.

### 3.3 Frontend
- Login screen branches on a public `GET /api/auth/config` returning `{ mode, oidcAuthorizeUrl? }`: local shows the password form; oidc shows a "Sign in with SSO" button that starts the auth-code flow and handles the redirect callback.
- Token storage/refresh handled per the OIDC flow; the rest of the app is auth-mode-agnostic.

## 4. Edge Cases
- Email collision: an OIDC user whose email matches an existing local user → link by `unique-claim`, not email, to avoid takeover; document the migration path.
- IdP down → login fails gracefully; existing sessions honor token expiry.
- Mixed mode (some local, some OIDC) is **not** supported simultaneously — one mode per deployment (keeps it simple).
- Air-gap: `local` remains default; no IdP calls unless configured.

## 5. Testing
- `local` mode unchanged (existing auth tests green).
- `oidc` mode (test profile with a mock issuer / signed test JWTs): JIT user creation, profile update on second login, admin-claim → systemAdmin mapping.
- `@RequireProjectRole` and `/api/me/*` work with an OIDC principal mapped to a local user.
- `GET /api/auth/config` returns the active mode.

## 6. Effort & Risk
- **Effort:** ~2 weeks (dual chains, JIT sync, frontend flow, tests).
- **Risk:** Medium — security-sensitive; the dual-chain + principal-mapping is the tricky part. Mitigated by keeping authorization fully local and gating everything behind `app.auth.mode`.

## 7. Acceptance Criteria
- [ ] `app.auth.mode=local` (default) behaves exactly as today.
- [ ] `app.auth.mode=oidc` authenticates via the IdP and JIT-provisions/updates local users.
- [ ] Project roles and system-admin still resolve from local data (optionally seeded from a claim).
- [ ] `/api/external/**` API-key auth works in both modes.
- [ ] Local + OIDC auth tests pass; identity maps to a stable local `User.id`.
