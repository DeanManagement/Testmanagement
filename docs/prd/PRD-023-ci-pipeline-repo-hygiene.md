# PRD-023 — CI Pipeline & Repository Hygiene

| | |
|---|---|
| **Status** | Implemented (2026-06-10) — first pipeline run pending push to GitHub |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-10 |
| **Priority** | P1 — Process (prevents regressions of everything above) |
| **Target** | v1.4 |
| **Related** | CODE_REVIEW_2026-06-09.md §M9, §M10; all other v1.4 PRDs (their tests need a place to run) |

---

## 1. Summary

The project has a substantial test suite (25 backend test classes incl. integration tests, frontend specs) but **no CI** — tests run only when someone remembers. There is no release versioning, no dependency/vulnerability scanning, and the working tree carries debris (`.attach_pid*`, `.fuse_hidden*`, `frontend/dist/`, `.angular/`, review docs at root) that `.gitignore` doesn't cover.

## 2. Current State (verified)

- No `.github/workflows/`, no GitLab CI, no Jenkinsfile.
- No tags/releases; images built ad hoc via `docker compose build`.
- `.gitignore` misses: `backend/.attach_pid*`, `.fuse_hidden*`, `frontend/dist/`, `frontend/.angular/`, `.claude/`.
- `Code_Review_Report.docx` and `REVIEW_AND_PROPOSALS.md` sit at repo root; newer review lives in `docs/`.

## 3. Goals & Non-Goals

**Goals**
- Every push/PR runs backend + frontend tests and builds both Docker images.
- Tagged releases produce versioned images.
- Dependency and image vulnerability scanning with low noise.
- Clean tree; ignores cover generated/debris files.

**Non-Goals**
- CD/auto-deploy (self-hosted users pull images themselves).
- Code-coverage gates (start by measuring, not gating).
- Kubernetes/helm packaging.

## 4. Proposed Design

### 4.1 GitHub Actions — `ci.yml` (push + PR)
- **backend:** temurin 25, `./mvnw verify` (cache `~/.m2`). Runs the full suite incl. Modulith + integration tests (H2/dev profile — already self-contained).
- **frontend:** node 22, `npm ci`, `ng lint` (once PRD-022's rule lands), `ng test --watch=false --browsers=ChromeHeadless`, `ng build`.
- **docker:** build both images (no push on PR) to catch Dockerfile breakage — including PRD-019's non-root changes.
- **compose smoke:** `docker compose up` with a test `.env`, wait for healthchecks, curl login + the PRD-017 unauthenticated-image check. This is the only place that nginx-level bugs are catchable.

### 4.2 `release.yml` (on tag `v*`)
- Re-run tests, build images tagged `vX.Y.Z` + `latest`, push to GHCR (`ghcr.io/deanmanagement/testmanagement-{backend,frontend}`), generate release notes from commits.
- Versioning: simple semver, manual tagging. Stamp the version into `/actuator/info` and the frontend footer (build arg).

### 4.3 Scanning
- **Dependabot:** maven, npm, docker, github-actions ecosystems, weekly, grouped minor/patch.
- **Trivy** image scan step in CI: fail on CRITICAL fixable vulns only (keeps noise tolerable).

### 4.4 Hygiene
- Extend `.gitignore` (§2 list); `git rm --cached` anything already tracked.
- Move root review artifacts into `docs/reviews/`; delete the stray `.attach_pid*` / `.fuse_hidden*` files.
- `CONTRIBUTING.md` stub: how to run tests, the PR checklist (tests green, lint clean, migration numbering).

## 5. Edge Cases

- Fork PRs lack registry credentials → push steps must be `if: github.event_name != 'pull_request'`.
- Compose smoke test timing: gate on healthchecks (PRD-019), not sleeps.
- Maven downloads on first run are slow → cache keyed on `pom.xml` hash.

## 6. Testing

The pipeline *is* the test. Verification: a PR with a failing unit test goes red; a tag produces pullable GHCR images; Dependabot opens its first PRs; Trivy step passes on current images (or produces an actionable list feeding PRD-019's base-image pinning).

## 7. Effort & Risk

- **Effort:** S-M — ~1 day for ci.yml + hygiene; ~half day for release.yml + scanning.
- **Risk:** Low. Additive; worst case is flaky CI to iterate on. Depends mildly on PRD-019 (healthchecks for the smoke test) — can ship with sleeps first.

## 8. Acceptance Criteria

- [x] `ci.yml`: backend `mvnw verify` (temurin 25, maven cache), frontend `ng test --watch=false` + build (command verified locally against the vitest builder), Docker builds, compose smoke test (secrets-guard check, health wait, end-to-end login, PRD-017 media-auth script). *Verification of the live run pending the next push to GitHub.*
- [x] `release.yml`: tag `vX.Y.Z` → re-test → GHCR images (`testmanagement-{backend,frontend}`, version + latest) → GitHub release with generated notes; `APP_VERSION` build-arg surfaces at `/actuator/info`. *UI footer version display deferred (no footer exists; fold into PRD-013 theming).*
- [x] Dependabot (maven/npm/docker/actions, weekly, grouped minor+patch) + Trivy in CI (CRITICAL, fixable-only, blocking).
- [x] `.gitignore` extended (`.attach_pid*`, `.fuse_hidden*`, `.claude/`); review docs moved to `docs/reviews/`; `CONTRIBUTING.md` added. *Note: the stray `.attach_pid*`/`.fuse_hidden*` files are OS-locked and need a one-off manual delete; they are ignored either way.*
