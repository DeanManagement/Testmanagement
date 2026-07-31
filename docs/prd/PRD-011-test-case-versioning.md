# PRD-011 — Test Case Versioning / History

| | |
|---|---|
| **Status** | ✅ Implemented (2026-07-31) |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-09 |
| **Priority** | P3 — v2.0, compliance-driven |
| **Target** | v2.0 |
| **Related** | REQUIREMENTS.md §13.1 (was PRD-009 §2.2); REVIEW §4.14 |

---

## 1. Summary

Test cases are edited in place; the prior wording of steps/expected results is lost. For regulated/traceability contexts you need to know exactly which version of a test case a given run executed. This PRD snapshots a test case's prior state on every update, numbers versions, links each `TestResult` to the version it executed, and adds a History tab with diffs.

**Build only with a real driver.** Versioning adds storage and UX cost; without a regulatory/traceability need it works against the "small, simple, fast" bar. Listed here so the design is ready when that driver appears.

## 2. Goals & Non-Goals

**Goals**
- Immutable snapshot of a test case (fields + steps) captured before each edit.
- Monotonic `versionNumber` + `versionAt` per test case.
- `TestResult` records the executed `versionNumber`.
- History tab: list of versions with side-by-side diff of fields and steps.

**Non-Goals**
- Editing/restoring old versions in place (a "revert" could come later as a normal edit that copies an old version forward).
- Versioning of suites, runs, or plans.
- Branching/merging of versions.

## 3. Proposed Design

### 3.1 Data model (migration V39)
- `test_case_versions`: `id`, `test_case_id` (FK, indexed), `version_number`, `version_at`, `title`, `description`, `preconditions`, `priority`, `status`, `labels` (json or child table), `steps_snapshot` (JSON array of `{action, expected, testData, order}`), `created_by`. Unique `(test_case_id, version_number)`.
- `test_cases`: add `current_version` int (default 1).
- `test_results`: add `executed_version` int (nullable; set at result creation from the case's current version).

### 3.2 Snapshot flow
- In `TestCaseService.update`, **before** mutating, write a `test_case_versions` row capturing the *current* state, then increment `current_version`. (Snapshot-before-change keeps "version N" = the state results executed against.)
- Serialize steps to a JSON column to avoid a parallel child-table per version (simpler, read-mostly).
- `TestRunService.create`/`addResult`: stamp `executed_version = testCase.currentVersion` on each `TestResult`.

### 3.3 Backfill (migration)
- For every existing test case, insert a v1 `test_case_versions` row from current state and set `current_version=1`; set existing `test_results.executed_version=1`. One-off Flyway migration + a Java callback if JSON building is needed (or do it lazily/defensively in code).

### 3.4 Endpoints & UI
- `GET /api/projects/{projectId}/test-cases/{id}/versions` — list (VIEWER).
- `GET /api/projects/{projectId}/test-cases/{id}/versions/{n}` — one version (VIEWER).
- Frontend: a "History" tab on test-case detail; pick two versions → side-by-side field + step diff (reuse a simple text-diff for long fields). Result detail shows "executed v{n}".

## 4. Edge Cases
- High-churn editing: snapshot on every save could bloat; acceptable for this scale, but add an index and consider pruning policy later (out of scope).
- A result with `executed_version` pointing at a version whose case was later deleted: versions cascade-delete with the case; result keeps the number for display.
- Keep normal edit latency unchanged — snapshot write is a single insert in the same transaction.

## 5. Testing
- Update creates a version row with the pre-edit state; `current_version` increments.
- New results stamp the current version; editing the case afterward doesn't change historical results' `executed_version`.
- Backfill: existing cases get v1; existing results get v1.
- Diff endpoint returns correct field/step deltas.

## 6. Effort & Risk
- **Effort:** ~2 weeks (snapshotting, backfill, diff UI).
- **Risk:** Medium–high — backfill correctness and keeping editing fast. Mitigated by JSON snapshots (no per-version child tables) and snapshot-in-same-transaction.

## 7. Acceptance Criteria
- [ ] Editing a test case snapshots the prior version and increments `current_version`.
- [ ] Each result records the version it executed; historical results are immutable to later edits.
- [ ] History tab lists versions and shows side-by-side diffs.
- [ ] Backfill assigns v1 to all existing cases and results.
- [ ] Versioning, stamping, and backfill tests pass; edit latency unaffected.


## 8. As Built (2026-07-31)

**Snapshot-before-edit.** `TestCaseVersionService.snapshotBeforeEdit` is called from
`TestCaseService.update` before any field is touched, inside the same transaction. That ordering is
the whole feature: it makes "version N" name the wording that results stamped N executed. Snapshot
*after* the edit and every historical result silently points one version off.

**The live case is the current version.** Only superseded states get a row, so a case at
`current_version = 3` has snapshots 1 and 2 and version 3 is synthesised from the case itself.
Duplicating the live state into a row on every save would double every write and leave two copies to
keep in step.

**Backfill deviates from §3.3 deliberately.** The spec said set existing
`test_results.executed_version = 1`. It is left NULL instead. v1 would be reconstructed from each
case's *current* text, so stamping an old result v1 asserts it executed wording it may never have
seen — precisely the claim an audit would test and find false. Existing cases do start at
`current_version = 1` (that part is true by construction); results recorded before this feature
existed read as "version not recorded". A gap you can explain beats a confident wrong answer.

**Steps are a JSON column**, per §3.1: versions are written once and read rarely, so a per-version
child table would add joins and cascade rules for nothing.

**Diff is per field and per step**, not character-level. The audit question is "what did this test
say when that run executed it", and knowing which step changed answers it; a word-level diff of
prose adds noise without changing what a reviewer concludes. Steps compare by position, so an
inserted step shifts the ones after it — itself a change worth showing rather than hiding behind
smarter alignment.

**Tests: 14.** Version numbering from creation through repeated edits, snapshots capturing steps and
labels rather than just fields, old snapshots staying inert across later edits, cross-project
isolation, cascade on delete — plus four on the audit property itself: a result records its version,
later edits do not rewrite it, results after an edit carry the new number, and pre-versioning results
stay unrecorded.

### Deferred
- Restoring an old version (§2 non-goal) — would be a normal edit copying a version forward.
- Pruning policy for high-churn cases; storage is a single row per edit and fine at this scale.
