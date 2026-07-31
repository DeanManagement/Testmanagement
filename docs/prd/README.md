# Product Requirements (PRDs)

PRDs for outstanding work on Testmanagement, derived from `REQUIREMENTS.md` (roadmap leftovers) and `REVIEW_AND_PROPOSALS.md` (the May 2026 audit). Each PRD is self-contained: problem, design, edge cases, testing, effort, and acceptance criteria.

## v1.3 — delivered

| PRD | Title | Priority | Status |
|---|---|---|---|
| [001](PRD-001-rbac-access-control.md) | RBAC & Project Authorization | **P0 — Security** | ✅ Implemented |
| [002](PRD-002-backend-filtering-pagination.md) | Backend Filtering & Pagination | P1 | ✅ Implemented |
| [003](PRD-003-webhooks.md) | Outbound Webhooks | P1 | ✅ Implemented |
| [004](PRD-004-import-export.md) | Test Case Import / Export (CSV + JSON) | P1 | ✅ Implemented |
| [005](PRD-005-ci-result-ingestion.md) | CI Result Ingestion (JUnit / Cucumber) | P2 | ✅ Implemented |
| [006](PRD-006-watcher-notifications.md) | Watcher Notifications (in-app + email) | P2 | ✅ Implemented |
| [007](PRD-007-server-side-search.md) | Server-Side Full-Text Search | P2 | ✅ Implemented |
| [008](PRD-008-usability-and-polish.md) | Usability & Polish Bundle | P2/P3 | ✅ Implemented |

## v1.4 — security & hardening (proposed, from the June 2026 review)

Fixes for the findings in [`docs/CODE_REVIEW_2026-06-09.md`](../CODE_REVIEW_2026-06-09.md). **The entire wave shipped on 2026-06-10** (205 backend tests green; frontend builds + tests clean). Open follow-ups noted inside the PRDs: audit-log persistence of login failures (020), ESLint subscription rule in CI (022), first live CI run on GitHub (023).

| PRD | Title | Priority | Size | Status |
|---|---|---|---|---|
| [017](PRD-017-media-cache-authorization-fix.md) | Authenticated Media Caching Fix | **P0 — auth bypass** | S | ✅ Implemented |
| [018](PRD-018-allure-sandboxing-token-hygiene.md) | Allure Report Sandboxing & Token Hygiene | **P0 — stored XSS** | M | ✅ Implemented |
| [019](PRD-019-deployment-secrets-hardening.md) | Deployment & Secrets Hardening | **P0 — default secrets** | M | ✅ Implemented |
| [020](PRD-020-auth-hardening.md) | Auth Hardening: Login Throttling & JWT Lifecycle | P1 | M | ✅ Implemented |
| [021](PRD-021-authorization-data-safety-gaps.md) | Authorization & Data-Safety Gap Closure | P0/P1 | S | ✅ Implemented |
| [022](PRD-022-frontend-stability-bundle.md) | Frontend Stability & Consistency Bundle | P1/P2 | M | ✅ Implemented |
| [023](PRD-023-ci-pipeline-repo-hygiene.md) | CI Pipeline & Repository Hygiene | P1 | S-M | ✅ Implemented |

**Sequencing:** 017 + 019 §4.1 + 021 §4.1/§4.3 first (one day, closes everything exploitable) → 018 → 020 → 023 (so the rest lands with CI in place) → 022 → 019 remainder + 021 §4.2.

## v2.0 — proposed ([PRD-009](PRD-009-v2-future-backlog.md) is the index)

| PRD | Title | Size | Status |
|---|---|---|---|
| [010](PRD-010-issue-tracker-integration.md) | Native Issue-Tracker Integration | M | ✅ Implemented (GitLab + Forgejo) |
| [011](PRD-011-test-case-versioning.md) | Test Case Versioning / History | L | ✅ Implemented |
| [012](PRD-012-oidc-sso.md) | SSO via OpenID Connect (multi-provider) | M | ✅ Implemented |
| [013](PRD-013-dark-mode-theming.md) | Dark Mode / Theming | S | ✅ Implemented |
| [014](PRD-014-traceability-matrix.md) | Requirements & Traceability Matrix | M | ✅ Implemented |
| [015](PRD-015-parameterized-test-cases.md) | Parameterized / Data-Driven Test Cases | M | Proposed |
| [016](PRD-016-flaky-test-detection.md) | Flaky Test Detection | M (≈S) | ✅ Implemented |

## What shipped

The entire v1.3 backlog (PRD-001 through PRD-008) is implemented and tested. Highlights: server-side RBAC enforced via a `@RequireProjectRole` aspect (PRD-001, was a live IDOR); filtering/pagination with URL-bound filters (PRD-002); signed outbound webhooks with retry (PRD-003); CSV/JSON import-export with dry-run (PRD-004); JUnit/Cucumber CI ingestion (PRD-005); watcher notifications with an in-app bell (PRD-006); Postgres full-text search behind the command palette (PRD-007); and the usability/tech-debt bundle (PRD-008).

## Suggested sequencing (v2.0)

Driver-dependent — see [PRD-009](PRD-009-v2-future-backlog.md) §3 for the recommended order:
~~dark mode (013)~~ → issue-tracker (010) → OIDC (012) → flaky detection (016) → traceability (014) + versioning (011) → parameterized cases (015).

Dark mode (013), issue-tracker integration (010, GitLab + Forgejo), SSO (012) and flaky detection
(016) all shipped on 2026-07-31. **Traceability ([PRD-014](PRD-014-traceability-matrix.md)) is
next**, then versioning (011), then parameterized cases (015).

## Status legend
v1.3, v1.4, PRD-010, PRD-012, PRD-013 and PRD-016 are **Implemented**. The remaining v2.0 PRDs are **Proposed**; update the status field as work is picked up and reflect shipped features in `REQUIREMENTS.md`.
