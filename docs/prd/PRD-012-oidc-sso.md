# PRD-012 — SSO via OpenID Connect (multi-provider, admin-configurable)

| | |
|---|---|
| **Status** | ✅ Implemented (2026-07-31) |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-09 |
| **Revised** | 2026-07-31 — scope changed from one property-configured issuer to multiple providers managed at runtime |
| **Priority** | P3 — v2.0, enterprise driver-dependent |
| **Target** | v2.0 |
| **Related** | REQUIREMENTS.md §13.5; interacts with PRD-001 (roles), PRD-020 (token lifecycle), PRD-010 (secret encryption pattern) |

---

## 1. Summary

Authentication is local today: email/password, and the app mints its own JWT. This PRD adds SSO
against any OpenID Connect provider, **alongside** local auth rather than instead of it.

The original draft of this PRD assumed a single issuer configured in `application.yml` and
explicitly ruled out running local and SSO auth together. Both assumptions are dropped: providers
are rows in the database, added and edited by system admins in the UI without a restart, and any
number of them can be active at once while local login continues to work.

**Project authorization stays local.** OIDC establishes *identity*; project roles remain
`ProjectMember` rows (PRD-001). System admin can be granted locally or mapped from a claim.

## 2. Goals & Non-Goals

**Goals**
- Multiple OIDC providers, stored in the database, managed from a system-admin screen.
- Authorization Code + PKCE login against each, discovered from the issuer URL.
- Just-in-time provisioning: unknown users get an account with **no project access**.
- Safe account linking (see §4.1) between an SSO identity and an existing local user.
- Optional claim → `systemAdmin` mapping, per provider.
- Local password login can be disabled, with a break-glass for system admins.

**Non-Goals**
- SAML.
- Non-OIDC OAuth2 (GitHub and similar have no `id_token`; a separate userinfo path would be needed).
- Per-project role mapping from IdP groups — roles stay local.
- IdP-initiated logout / back-channel logout.
- Removing local auth entirely.

## 3. Proposed Design

### 3.1 Sessions stay local — the important decision

The original draft proposed a second `SecurityFilterChain` running as an OAuth2 **resource server**,
validating IdP-issued access tokens on every `/api/**` call. This rewrite does not do that. OIDC is
used **only to authenticate the login**; on success the app mints its own existing JWT for the
resolved local user.

The reason is that everything downstream is already built on that token: `@RequireProjectRole` reads
the local user id from the principal, audit rows record it, and PRD-020's `token_version` bump gives
server-side logout and password-change invalidation. Accepting IdP tokens directly would mean
re-plumbing all of it, losing server-side revocation (we cannot invalidate someone else's token),
and — because a resource server is configured per issuer — would make "many providers at once"
awkward. Minting our own token after an OIDC login keeps one session model regardless of how the
user proved who they are, and makes mixed mode fall out for free.

Trade-off: the app's JWT lifetime is its own, so revoking a user at the IdP does not immediately
kill an active session — it stops the *next* login. With a 12h token that is an acceptable window;
an admin can force it to zero by deactivating the user, which bumps `token_version`.

### 3.2 Data model (migration V43)
- `sso_providers`: `id`, `slug` (unique, used in the callback URL), `display_name`, `issuer_uri`,
  `client_id`, `client_secret_encrypted`, `scopes`, `email_claim` (default `email`),
  `name_claim` (default `name`), `admin_claim`, `admin_claim_value`, `trust_email_for_linking`,
  `auto_provision`, `active`, `last_error`, `last_error_at`, timestamps.
- `sso_identities`: `id`, `user_id` FK, `provider_id` FK, `subject`, `created_at`, `last_login_at`.
  Unique `(provider_id, subject)` and `(provider_id, user_id)`.
- `auth_settings`: single row — `local_login_enabled`.

The identity table is what makes linking safe: the durable key is `(provider, sub)`, never the email.

### 3.3 Login flow
1. `GET /api/auth/config` (public) → `{ localLoginEnabled, providers: [{slug, displayName}] }`.
2. The login screen renders a button per provider pointing at `/oauth2/authorization/{slug}`.
3. Spring Security runs Authorization Code + PKCE, using a **database-backed
   `ClientRegistrationRepository`**. Discovery (`ClientRegistrations.fromIssuerLocation`) is a
   network call, so registrations are cached and the cache is invalidated when a provider row is
   saved or deleted.
4. On success a custom handler resolves the local user (§4.1), mints the app JWT, and redirects to
   the frontend callback with the token in the URL **fragment** — a fragment is not sent to the
   server, so it stays out of access logs and `Referer` headers.
5. The frontend stores the token, clears the fragment, and proceeds as after a local login.

### 3.4 Security
- Client secrets encrypted at rest with the shared AES-GCM cipher (same construction as PRD-010).
- The issuer URL is admin-supplied, so it is SSRF-validated exactly like webhook and tracker URLs:
  https required, private/loopback/link-local rejected.
- The post-login redirect target is derived from server configuration, never from a request
  parameter — otherwise the callback becomes an open redirect that leaks the freshly minted token.
- Client secret is never returned by any endpoint; the DTO exposes `secretSet: boolean`.

## 4. Edge Cases

### 4.1 Account linking — the security-critical path
On successful OIDC authentication:

1. **Known identity** — `(provider, sub)` matches an `sso_identities` row → log in as that user.
   Profile fields are refreshed from claims.
2. **Unknown identity, email matches an existing user** →
   - Link **only if** the provider is flagged `trust_email_for_linking` **and** the token asserts
     `email_verified: true`. Otherwise **refuse the login** with a message telling the user to ask an
     admin to link the account.
   - This is the account-takeover surface: an IdP where users can set an arbitrary email would
     otherwise let anyone claim an existing admin account. The flag is off by default and per
     provider, so trusting a corporate Keycloak does not mean trusting a public IdP.
3. **Unknown identity, no matching email** → if `auto_provision`, create a user with **no project
   memberships and no admin flag**; they can log in but see nothing until an admin adds them.
   Otherwise refuse.

An existing local user keeps their password; linking does not remove it.

### 4.2 Others
- **Local login disabled** — the password form is hidden and `POST /api/auth/login` rejects
  non-admins. System admins can still log in locally, so a broken IdP is recoverable.
- **IdP down / discovery fails** — saving a provider records the error and the provider is shown as
  needing attention; login attempts fail with a clear message rather than a stack trace.
- **Provider deactivated or deleted** — existing `sso_identities` rows are kept, so re-adding the
  provider re-links the same people. Users who can only log in via that provider lose access, which
  is the intended effect.
- **Air-gap** — no providers configured means no outbound calls, as today.

## 5. Testing
- Linking: known identity; unverified email refused; verified email with the flag off refused;
  verified email with the flag on linked; no match auto-provisioned with no access; auto-provision
  off refused.
- Admin-claim mapping grants and revokes `systemAdmin`.
- Break-glass: with local login disabled, an admin can still authenticate locally and a
  non-admin cannot.
- Client secret never present in any response; issuer SSRF rejection.
- The existing local auth suite stays green.

## 6. Effort & Risk
- **Effort:** ~2 weeks (data model, dynamic registrations, linking rules, admin UI, tests).
- **Risk:** High — this is the authentication path. Mitigated by keeping the session model
  unchanged (§3.1), defaulting every permissive switch to off, and testing the takeover cases
  explicitly rather than only the happy path.

## 7. Acceptance Criteria
- [x] A system admin can add, edit, test, deactivate and delete OIDC providers in the UI, no restart.
- [x] Login screen offers a button per active provider; local login still works.
- [x] Linking follows §4.1 exactly, including refusing the unverified-email case.
- [x] Unknown users are provisioned with no project access and no admin flag.
- [x] Local login can be disabled while system admins retain access.
- [x] Client secrets are encrypted at rest and never returned.
- [x] Project roles and `@RequireProjectRole` behave identically for SSO and local users.

## 8. As Built (2026-07-31)

**Backend.** `sso_providers`, `sso_identities` and a single-row `auth_settings` (V43).
`DynamicClientRegistrationRepository` serves Spring's registrations from the database, caching
discovery per slug and invalidating on every provider mutation. `SsoSecurityConfig` adds an
`@Order(1)` chain matching only `/oauth2/authorization/**` and `/login/oauth2/code/**`, so no
existing route changes how it authenticates; sessions are allowed there because the
authorization-code flow needs somewhere for `state` and the PKCE verifier, and end at the callback.
`SsoLoginService` implements §4.1; `SsoAuthenticationSuccessHandler` mints the app JWT via
`AuthService.issueToken`.

**Break-glass.** `AuthService` consults a `LocalLoginPolicy` seam rather than importing the SSO
module — authentication is the lower layer and must work whether or not SSO is configured. The check
runs *after* password verification, so it cannot be used as an oracle for which addresses are admins.
Disabling local login is refused unless an active provider exists.

**Admin claim.** Applied only when the provider actually maps one, so a provider with no admin claim
never demotes someone granted the flag locally. Array claims (`groups`) count membership as a match.

**Frontend.** Settings → Single sign-on lists providers with their redirect URI (shown so it cannot
be mistyped when registering at the IdP), a test-connection action, and the recorded last error. The
email-trust switch is visually separated with a warning because it is a security boundary rather
than a preference. The login screen renders a button per active provider and hides the password form
when local login is off. `/login/callback` is public and outside the shell.

**Shared extractions.** A third feature needing both prompted `AesGcmCipher` (one key,
`app.security.encryption-key`, with the issue-tracker key kept as a fallback so existing deployments
keep working) and `OutboundUrlValidator`, now used by webhooks, issue trackers and issuers alike.

**Tests: 35 backend, 6 frontend.** Linking covers every refusal — untrusted provider, unverified
email, absent `email_verified`, a second subject for an already-linked account — plus adoption when
both conditions hold, provisioning with no access, email lower-casing, admin grant/revoke from
scalar and array claims, and a provider without an admin claim leaving a local admin alone.
Break-glass covers admin-in, non-admin-out, and that the disabled message only appears after correct
credentials. The callback spec asserts the fragment is wiped and that a rejected token is not left
in storage.

### Deferred
- SAML and non-OIDC OAuth2 (GitHub) — both need a different token/userinfo path.
- Admin-driven manual linking of an existing user to an SSO identity: today the route is to enable
  email trust on a provider you control. Worth adding if the refusal proves noisy in practice.
- IdP-initiated and back-channel logout.
