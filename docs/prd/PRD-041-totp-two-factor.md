# PRD-041 — TOTP Two-Factor Authentication for Local Accounts

| | |
|---|---|
| **Status** | 📝 Draft |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-17 |
| **Priority** | P3 — driver-dependent (SSO already covers organisations with an IdP) |
| **Target** | v2.4 |
| **Related** | PRD-020 (login throttling, `token_version`), PRD-012 (SSO, break-glass admin login), PRD-019 (secrets), PRD-025 §3.2 (service accounts) |

---

## 1. Summary

Password sign-in is the only factor for local accounts. Organisations with an identity provider can
move to SSO (PRD-012) and get MFA from the IdP, but the typical install for this tool has no IdP.
Those installs run on one password per account, and one of those accounts is a system admin who can
read every project and change every role.

This PRD adds optional time-based one-time passwords (TOTP, RFC 6238) to **local password sign-in**:
a user scans a QR code with any authenticator app, gets ten single-use recovery codes, and from then
on must supply a 6-digit code after their password. An admin can reset a user's 2FA, and an instance
can require 2FA for everyone who signs in with a password.

It also closes a specific gap in PRD-012: when local login is switched off, system admins keep
password login as a break-glass (`AuthService.localLoginAllowed`). Today that break-glass is the
weakest door into the most privileged accounts. With this PRD it can require a second factor.

**Driver:** build this when a password-only install asks for it, or when an audit requires MFA. The
design is small enough to stay in one module (`user`) and adds no infrastructure.

## 2. Goals & Non-Goals

**Goals**
- Per-user opt-in TOTP for accounts that sign in with email + password.
- Enrolment with a QR code plus a manually typable key; confirmation by entering a valid code.
- Ten single-use recovery codes, stored hashed, shown exactly once, regenerable.
- Codes are checked inside the existing `AuthService.login`, and failures count against the existing
  `LoginThrottleService`.
- Enabling, disabling or resetting 2FA bumps `token_version`, so every other session ends.
- A system admin can reset another user's 2FA.
- Instance-wide "require 2FA for password sign-in" switch on `AuthSettings`, enforced server-side.
- The TOTP secret is encrypted at rest with the shared `AesGcmCipher` (`APP_ENCRYPTION_KEY`).

**Non-Goals**
- **SSO logins.** They never pass through `AuthService.login`, since `SsoAuthenticationSuccessHandler`
  calls `issueToken` directly. The IdP owns MFA for those users, and adding a second prompt on top
  would only annoy them.
- **API keys and MCP.** API-key requests authenticate through their own filter and act as service
  accounts, which can never log in (PRD-025 §3.2). Nothing changes for CI or agents.
- WebAuthn / passkeys, SMS or email codes. TOTP is the one factor that works air-gapped with no
  outbound calls and no extra infrastructure.
- "Remember this device for 30 days". It adds a second long-lived credential for little gain, since
  the JWT already lasts `app.jwt.expiration-ms` (24h by default).
- Persisting security events to the audit log. `AuditAction` is project-scoped and PRD-020 already
  lists login-event persistence as an open follow-up, so this PRD logs through SLF4J the way
  `LoginThrottleService` does.

## 3. Proposed Design

### 3.1 Data model (migration: next free V-number, V54+ at time of writing)

Add to `users` (entity `user/User.java`):

| Column | Type | Meaning |
|---|---|---|
| `totp_secret` | `TEXT NULL` | Base32 secret encrypted with `AesGcmCipher`. Set at setup, before confirmation |
| `totp_enabled_at` | `TIMESTAMP NULL` | Non-null means 2FA is active. A secret with a null value here is a pending enrolment |
| `totp_last_step` | `BIGINT NULL` | Last accepted RFC 6238 time step, for replay protection |

New table `user_recovery_codes` (entity in `user/internal/`; needs `created_by` / `updated_by`, see CLAUDE.md):

```sql
id UUID PK, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
code_hash VARCHAR(64) NOT NULL, used_at TIMESTAMP NULL,
created_at, updated_at, created_by, updated_by
INDEX (user_id)
```

Add to `auth_settings` (`user/internal/sso/AuthSettings.java`): `totp_required BOOLEAN NOT NULL DEFAULT FALSE`.

**Recovery codes are hashed with SHA-256, not BCrypt.** They are 50+ bits of server-generated
randomness, not user-chosen, so a slow hash adds nothing against brute force. BCrypt would add up to
ten ~100 ms comparisons to every recovery login. The hash is compared in constant time
(`MessageDigest.isEqual`).

### 3.2 TOTP implementation, with no new backend dependency

RFC 6238 with the defaults every authenticator app assumes: HMAC-SHA1, 30-second step, 6 digits.
It fits in one package-private class, `TotpCodes`, of about 40 lines built on `javax.crypto.Mac`, plus
a base32 encoder of about 20 lines (the JDK has none). It takes the injected `Clock` from
`shared/config/ClockConfig`, so tests control time. This is less code than a library dependency and
is fully covered by the RFC 6238 Appendix B test vectors.

- Verification accepts the current step ±1 (90 seconds in total) to absorb phone clock drift.
- A code is rejected if its step is `<= totp_last_step`, so a code seen over a shoulder or in a proxy
  log cannot be replayed within its window. On success `totp_last_step` is updated in the same
  transaction as the login.
- Secrets are 20 random bytes from `SecureRandom`.

### 3.3 Login flow: one request, no half-authenticated token

`LoginRequest` gains an optional `@Size(max = 32) String secondFactor` field, which takes either a
6-digit code or a recovery code. `AuthService.login` changes after the existing password, service
account and `localLoginAllowed` checks:

1. User has 2FA enabled and `secondFactor` is blank: respond **401** with `ErrorResponse.error =
   "SECOND_FACTOR_REQUIRED"`. This is **not** a throttle failure, because the password was right.
2. User has 2FA enabled and `secondFactor` is present: verify it as a TOTP code, or as an unused
   recovery code if it matches the recovery format. If it's wrong, `loginThrottle.recordFailure`
   and 401 `INVALID_SECOND_FACTOR`. If it's right, `recordSuccess` and issue the JWT as today.
3. User has no 2FA and `AuthSettings.totpRequired` is true: issue a JWT carrying the claim
   `enrolmentPending: true` (§3.5).

The frontend keeps email and password in the form and re-posts all three fields. The alternative
was a short-lived "MFA pending" token, which is a second token type that `JwtAuthenticationFilter`
must never accept as a session. Getting that wrong is an authentication bypass, while re-posting the
password costs one extra BCrypt check per login.

Step 1 does reveal that the password was correct. Every two-step flow does, and the per-email
throttle (5 failures / 15 min) still bounds password guessing before step 1 is reached. Code
guessing is bounded by the same throttle: 5 attempts against 10⁶ codes.

### 3.4 Enrolment, disable, regenerate

All endpoints sit under `/api/auth/2fa` in a new `TwoFactorController` (`user/internal/controller/`).
Every one requires an authenticated local user, and every mutating call **re-verifies the current
password**, so a stolen session token alone cannot turn 2FA off or swap the device.

| Method | Path | Body | Effect |
|---|---|---|---|
| `GET` | `/api/auth/2fa` | – | `{ enabled, enabledAt, recoveryCodesRemaining, required }` |
| `POST` | `/api/auth/2fa/setup` | `{ password }` | Generates the secret and stores it encrypted, pending. Returns `{ secret, otpauthUri }`. Calling it again replaces a pending secret. Returns 409 if 2FA is already enabled |
| `POST` | `/api/auth/2fa/enable` | `{ code }` | Verifies against the pending secret, sets `totp_enabled_at`, creates 10 recovery codes, bumps `token_version`. Returns `{ token, recoveryCodes }` (codes shown once) |
| `POST` | `/api/auth/2fa/recovery-codes` | `{ password, code }` | Replaces all recovery codes. Returns the new set once |
| `DELETE` | `/api/auth/2fa` | `{ password, code }` | Clears secret, codes and `enabled_at`, bumps `token_version`. Returns `{ token }`. Refused with 409 while `totpRequired` is on |

The `otpauth://totp/Testmanagement:{email}?secret=…&issuer=Testmanagement` URI uses the issuer
label from `app.auth.totp-issuer` (default `Testmanagement`), so several instances are
distinguishable in one authenticator app. Returning a fresh token after a `token_version` bump
follows `AuthService.changePassword`, so the current tab stays signed in.

Users without a password hash (SSO-only accounts) get 409 `NO_LOCAL_PASSWORD` from `setup`, because
the second factor would protect nothing.

**Encryption key missing:** `AesGcmCipher.encrypt` already throws with an operator-facing message
naming `APP_ENCRYPTION_KEY`. `GET /api/auth/2fa` returns `available: false` so the UI can explain
why instead of showing a failing button.

### 3.5 Instance-wide enforcement, server-side

`forcePasswordChange` is currently enforced only by the frontend (`core/services/auth.service.ts`
navigates, and no backend check exists). That's acceptable for a nudge, but not for an MFA
requirement.

- A token carrying `enrolmentPending: true` passes `JwtAuthenticationFilter` only for
  `GET /api/auth/me`, `POST /api/auth/logout` and `/api/auth/2fa/**`. Every other request gets 403
  `SECOND_FACTOR_ENROLMENT_REQUIRED`. The check sits in the filter next to the existing
  `tokenVersion` claim check, so no endpoint can miss it.
- `enable` returns a normal token, which ends the restriction.
- The switch lives in the SSO settings page next to `localLoginEnabled`
  (`PUT /api/admin/sso/settings` on `SsoAdminController`, system admin only). Turning it on doesn't end existing sessions;
  the requirement applies at next sign-in. The UI says so.

The same filter-level mechanism would make `forcePasswordChange` real. That fix is out of scope
here and noted in §6.

### 3.6 Admin reset

`POST /api/users/{id}/2fa/reset` on `UserController`, system admin only, with the same guard style as
the existing user update/delete. It clears the secret, recovery codes and `enabled_at`, bumps the
user's `token_version`, and logs `INFO "2FA reset for user={} by admin={}"`.

- An admin cannot reset their **own** 2FA through this endpoint (403), because that would let a
  stolen admin session remove the admin's own second factor. Admins use their recovery codes, or
  another admin.
- If the last admin loses both device and codes, recovery is a documented SQL statement in
  `docs/USER_MANUAL.md`, the same break-glass level as losing the admin password today.

### 3.7 Frontend

- **Login** (`features/login/login.component.*`): on `SECOND_FACTOR_REQUIRED`, reveal a code field
  (`autocomplete="one-time-code"`, `inputmode="numeric"`) and a "Use a recovery code" link that
  switches the input to free text, then re-submit. Email and password fields become read-only with a
  "Back" action.
- **New page** `features/two-factor/`, route `two-factor` next to `notification-settings` in
  `app.routes.ts`, linked from the user menu. It has three states: off (enable), setup (QR + manual
  key + confirm code), and on (regenerate codes, disable). Recovery codes appear in a dialog with Copy
  and Download (.txt) buttons and an "I have saved these" confirmation before it closes.
- **QR code:** rendered client-side with one small dependency-free library (e.g.
  `qrcode-generator`, MIT). No image request carries the secret, and the manual key is always shown
  for accessibility and for devices without a camera.
- **Enrolment pending:** an auth guard redirects every route to `two-factor` while the token carries
  `enrolmentPending`, mirroring the `change-password` redirect.
- **Admin:** "Reset two-factor" action in `features/settings/edit-user-dialog`, shown only when the
  user has 2FA. The `sso-settings` page gets the "Require 2FA for password sign-in" toggle.
- i18n keys in `en.json` / `de.json`.

## 4. Edge Cases

- **Clock skew beyond ±30s** on the server: every code fails. The error text suggests checking the
  server's NTP, and codes one step either side are accepted.
- **Replay** of a code within its 30 s window: rejected by `totp_last_step`. Two legitimate logins
  from two tabs in the same 30 s need two different codes, which is accepted friction.
- **Recovery code reuse:** `used_at` is set in the login transaction. A second use fails like a wrong
  code and counts toward the throttle.
- **Last recovery code used:** the login succeeds and `GET /api/auth/2fa` reports 0 remaining. The
  UI shows a banner prompting regeneration. It doesn't block.
- **Encryption key rotated or lost:** decrypting the secret throws, so every 2FA user is locked out.
  Login returns a 500-class error naming the key, not "invalid code". Recovery codes still work
  because they are hashed, not encrypted, which is one more reason to keep them separate from the
  secret. Documented next to the existing warning for issue-tracker tokens.
- **Pending enrolment abandoned:** the secret remains with null `enabled_at` and is ignored at login.
  The next `setup` overwrites it.
- **`totpRequired` on and the break-glass admin has no 2FA:** they get an `enrolmentPending` session
  and must enrol before doing anything else. That is intended, and it is why the toggle warns
  admins to enrol first.
- **Service accounts:** already refused before any 2FA logic (`AuthService.login`), so there is no
  change and no enrolment path, since they have no password.
- **User deleted:** recovery codes cascade.
- **Password change** does not disable 2FA, and 2FA disable requires the current password and a code.

## 5. Testing

- `TotpCodesTest`: RFC 6238 Appendix B SHA-1 vectors, ±1 step acceptance, ±2 rejection, base32
  round-trip.
- `AuthServiceTest` (login):
  - Password right with no code returns `SECOND_FACTOR_REQUIRED` and records no throttle failure.
  - A wrong code records a failure, and 5 wrong codes produce 429.
  - A valid code issues a token, and the same code replayed is rejected.
  - A recovery code works once.
  - SSO `issueToken` path is unaffected.
  - Break-glass admin with 2FA is challenged.
- `TwoFactorControllerTest`:
  - Setup requires the password, and enable requires a valid code against the pending secret.
  - Enable bumps `token_version`, so the old token is rejected by the filter.
  - Recovery codes are returned once and stored hashed.
  - Disable is refused while required, and setup is refused without an encryption key or without a
    password hash.
- `JwtAuthenticationFilter`: an `enrolmentPending` token reaches `/api/auth/2fa/**` and `/me` but
  gets 403 on `/api/projects`.
- Admin reset: system admin only, self-reset forbidden, other sessions invalidated.
- Frontend (Vitest): the login component reveals the code field on `SECOND_FACTOR_REQUIRED` and
  re-posts all three fields. The two-factor page moves off → setup → on, and the recovery dialog
  can't close before confirmation.

## 6. Effort & Risk

- **Effort:** ~5–7 days. Backend ~3 (TOTP + login + endpoints + filter), frontend ~2, tests and
  docs ~1–2.
- **Risk:** Medium. It sits on the authentication path, so a bug locks users out or lets them in.
  Mitigations: RFC vectors, the one-request design with no new token type for the login step, and
  the enrolment restriction enforced in one place (the filter).
- **Operational risk:** 2FA makes `APP_ENCRYPTION_KEY` load-bearing for sign-in, not just for
  integrations. `docs/USER_MANUAL.md` must say this plainly.
- **Follow-up noted, not in scope:** enforce `forcePasswordChange` server-side with the same filter
  mechanism, since it is currently frontend-only.

## 7. Acceptance Criteria

- [ ] Local users can enrol TOTP via QR code or manual key, confirmed by a valid code.
- [ ] Ten recovery codes are shown once, stored as SHA-256 hashes, single-use and regenerable.
- [ ] Password login for a 2FA user requires a valid code or an unused recovery code, and replayed
      codes are rejected.
- [ ] Wrong codes count toward the PRD-020 login throttle; the "code required" response does not.
- [ ] Enable, disable and reset bump `token_version`, and the acting session receives a fresh token.
- [ ] The TOTP secret is encrypted with `AesGcmCipher`, and enrolment is refused with a clear message
      when no key is configured.
- [ ] System admins can reset another user's 2FA but not their own.
- [ ] `totpRequired` restricts non-enrolled password users to enrolment endpoints, enforced in
      `JwtAuthenticationFilter`.
- [ ] SSO logins, API keys and MCP are unaffected.
- [ ] Login, enrolment and admin UI translated in `en.json` and `de.json`. `USER_MANUAL.md` covers
      enrolment, recovery and the encryption-key dependency.
- [ ] Backend and frontend tests above pass.
