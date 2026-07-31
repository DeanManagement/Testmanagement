# PRD-019 — Deployment & Secrets Hardening

| | |
|---|---|
| **Status** | Implemented (2026-06-10) |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-10 |
| **Priority** | **P0 — Security (known default secrets)** / P1 (container hardening) |
| **Target** | v1.4 |
| **Related** | CODE_REVIEW_2026-06-09.md §C3, §H6, §M1, §M7, §M8 |

---

## 1. Summary

Every unmodified `docker compose up` deployment today runs with a **publicly known JWT signing key** (committed default in docker-compose.yml — anyone can forge an admin token), a default DB password, and an `admin@localhost.ch / admin` account. On top of that, both containers run as root, the backend has no healthcheck or restart policy, Postgres is published to the host network, the backend host-port mapping is broken (`8089:8080` vs. Spring listening on 8089), and nginx serves the app without security headers or gzip.

## 2. Current State (verified)

- `docker-compose.yml`: `JWT_SECRET: ${JWT_SECRET:-defaultSecretKey...}`, `POSTGRES_PASSWORD: ${DB_PASSWORD:-testmanagement}`, `ports: "5432:5432"` on the DB, `"8089:8080"` on the backend, stale `KEYCLOAK_*` env vars, no `restart:` anywhere, no backend/frontend healthchecks.
- `application.yml`: `ADMIN_PASSWORD` defaults to `admin`; `flyway.fail-on-missing-locations: false`; no `management.endpoints` restriction.
- `backend/Dockerfile`: no `USER`, `EXPOSE 8080` (wrong — app listens on 8089).
- `frontend/Dockerfile`: nginx as root; `frontend/nginx.conf`: no security headers, no gzip.
- `JwtConfig` fails fast on blank/short secrets — currently defeated by the compose default.

## 3. Goals & Non-Goals

**Goals**
- No working deployment with default secrets — fail loudly instead.
- Containers follow baseline hardening (non-root, healthchecks, restart policy, pinned bases).
- Correct port wiring; no stale config.
- Sensible nginx security headers + gzip.

**Non-Goals**
- TLS termination (deployment-specific; document a reverse-proxy recipe in README instead).
- Kubernetes manifests, secret managers (overkill for the target audience).

## 4. Proposed Design

### 4.1 Secrets (P0)
- compose: `JWT_SECRET: ${JWT_SECRET:?Set JWT_SECRET in .env (min 64 chars)}` and `DB_PASSWORD: ${DB_PASSWORD:?Set DB_PASSWORD in .env}` — the `:?` form aborts `docker compose up` with a clear message.
- Remove the `admin` default: `app.admin.password: ${ADMIN_PASSWORD:}`. `AdminSeeder` behavior when blank: generate a random password, print it **once** to the startup log, and set `force_password_change=true` (column exists, V31). If `ADMIN_PASSWORD` is set, still seed with `force_password_change=true`.
- Ship `.env.example` (documented placeholders, generation hint: `openssl rand -base64 48`) and reference it in README quick-start.
- Remove `KEYCLOAK_*` env vars from compose.

### 4.2 Containers (P1)
- Both Dockerfiles: create and switch to a non-root user (`USER app` / nginx-unprivileged base or `nginx` user with adjusted pid/cache paths).
- Fix ports: backend `EXPOSE 8089`, compose `"8089:8089"` — or set `SERVER_PORT=8080` and keep mappings; pick one, today's mix is broken.
- compose: `restart: unless-stopped` on all services; backend healthcheck on `/actuator/health` (curl/wget in the JRE image or a tiny healthcheck script); frontend healthcheck on `/`.
- DB: drop the host port publish, or bind loopback-only `127.0.0.1:5432:5432` for admin access.
- Pin base images by digest or at least minor version (`postgres:16.6-alpine`, `eclipse-temurin:25-jre@sha256:…`).

### 4.3 nginx (P1)
Add to the server block: `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `X-Frame-Options: DENY` (app shell; the Allure CSP from PRD-018 governs `/view/**`), a baseline CSP for the shell (`default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'` — Angular Material needs inline styles), and `gzip on` for text/JS/CSS/JSON.

### 4.4 Spring config (P1)
- `flyway.fail-on-missing-locations: true` — **caveat:** `false` is currently load-bearing: H2 (dev/test) resolves `classpath:db/specific/{vendor}` to a nonexistent `db/specific/h2`. To enable `true`, add an empty `db/specific/h2/` placeholder (e.g. a `.keep`d no-op) first, or scope `true` to the prod profile only.
- Explicit `management.endpoints.web.exposure.include: health` (+ `info` if wanted); health probes unauthenticated, everything else locked.

## 5. Edge Cases

- Existing deployments that relied on the default JWT secret: tokens are invalidated on upgrade → all users re-login. Release note required.
- Existing deployments with the seeded `admin/admin`: seeder must not overwrite an existing admin's password; the force-change flag only applies to newly seeded accounts (document: change it manually).
- Dev profile (H2, no Docker) is unaffected — dev defaults may stay convenient there, but `application-dev.yml` must not be active in the shipped image.

## 6. Testing

- `docker compose up` without `.env` → fails with the JWT_SECRET message (CI smoke test).
- `docker compose config` golden test for the resolved file.
- Container runs as non-root (`docker exec … whoami`), healthchecks reach healthy, app reachable on documented ports.
- curl assertions for nginx headers + gzip (`Content-Encoding`).
- Actuator: `/actuator/health` 200; `/actuator/env` 401/404.

## 7. Effort & Risk

- **Effort:** M — ~1 day total (secrets 2 h, Dockerfiles/compose 3 h, nginx 1 h, config + tests 2 h).
- **Risk:** Low-medium. Forced re-login on upgrade; non-root nginx needs the unprivileged port/cache path adjustments (well-trodden).

## 8. Acceptance Criteria

- [x] Compose refuses to start without explicit `JWT_SECRET` and `DB_PASSWORD` (`:?` syntax; Keycloak leftovers removed; `.env.example` committed; README quick-start rewritten for local-JWT auth).
- [x] No default admin password; if `ADMIN_PASSWORD` is unset a random one is generated and logged once; seeded admin must change password at first login (`AdminSeederTest`). Dev profile keeps an explicit local-only `admin` default.
- [x] Both containers run as non-root (backend: dedicated `app` user; frontend: `nginxinc/nginx-unprivileged` on port 8080), with Docker healthchecks (`curl /actuator/health` / `wget /`) and `restart: unless-stopped`; backend mapping fixed to `8089:8089` + `EXPOSE 8089`, frontend `8012:8080`.
- [x] Postgres bound to `127.0.0.1:5432` only.
- [x] nginx: nosniff/X-Frame-Options/Referrer-Policy/CSP **scoped to static locations only** (a server-level X-Frame-Options would break the Allure iframe through `/api/` — PRD-018), gzip enabled. Flyway `fail-on-missing-locations: true` with a `db/specific/h2/` placeholder; actuator restricted to `health,info`; `/actuator/info` reports `APP_VERSION` (set as a Docker build arg by the release workflow, PRD-023).
- [x] `.env.example` committed; README quick-start updated.

**Note:** base images pinned to major tags (e.g. `postgres:16-alpine`); digest pinning was skipped in favor of Dependabot docker updates (PRD-023), which achieve the same control with less friction.
