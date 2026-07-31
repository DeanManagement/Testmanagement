# PRD-009 — v2.0 Future Backlog (Index)

| | |
|---|---|
| **Status** | Split into individual PRDs (2026-06-09) |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-09 |
| **Priority** | P3 — Future / driver-dependent |
| **Target** | v2.0 |
| **Related** | REQUIREMENTS.md §13; REVIEW §3.10, §4.9, §4.14 |

---

## 1. Summary

The larger, lower-urgency v2.0 roadmap items. Originally captured as a single outline; now each has its own full PRD so it can be picked up independently. All remain deliberately scoped to the "small, simple, fast tool" bar, and several are **driver-dependent** (build only when a concrete need appears).

This document is now an **index**. See the per-feature PRDs below.

## 2. The v2.0 PRDs

| PRD | Feature | Size | Notes |
|---|---|---|---|
| [PRD-010](PRD-010-issue-tracker-integration.md) | Native issue-tracker integration | M | Highest value; start single-provider (GitHub/GitLab). Keeps `defectLink` as fallback. |
| [PRD-011](PRD-011-test-case-versioning.md) | Test case versioning / history | L | Compliance-driven; snapshot-on-edit + diff UI + backfill. |
| [PRD-012](PRD-012-oidc-sso.md) | OIDC / Keycloak SSO | M | Alongside local auth via `app.auth.mode`; roles stay local (PRD-001). |
| [PRD-013](PRD-013-dark-mode-theming.md) | Dark mode / theming | S | Frontend-only; cheap, high satisfaction. |
| [PRD-014](PRD-014-traceability-matrix.md) | Requirements & traceability matrix | M | Requirement entity + matrix + coverage report. |
| [PRD-015](PRD-015-parameterized-test-cases.md) | Parameterized / data-driven cases | M | Parameter sets expand a case into N results per run. |
| [PRD-016](PRD-016-flaky-test-detection.md) | Flaky test detection | M (≈S) | Analytics over existing result history; dashboard widget. |

## 3. Suggested order (when picked up)
1. **Dark mode (PRD-013)** — cheap, high satisfaction, no dependencies.
2. **Issue-tracker integration (PRD-010)** — highest value; start single-provider.
3. **OIDC (PRD-012)** — when an enterprise/Keycloak driver appears.
4. **Flaky detection (PRD-016)** — cheap analytics win (stronger once PRD-005 CI ingestion feeds regular results).
5. **Traceability (PRD-014) + Versioning (PRD-011)** — together, only with a compliance driver.
6. **Parameterized cases (PRD-015)** — only on real demand.

## 4. Explicitly NOT planned (REVIEW §6)
Jira-style configurable workflows; in-app rich-text (TipTap/ProseMirror) editors; a second screenshot store (S3/MinIO). These add disproportionate complexity for the 50-user / 5-concurrent target.
