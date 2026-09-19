# PRD-037 — Release Readiness & Quality Gate

| | |
|---|---|
| **Status** | ✅ Implemented 2026-09-19 — see §8 |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-17 |
| **Priority** | P2 — turns data the tool already has into the question people actually ask |
| **Target** | v2.4 |
| **Related** | PRD-001 (RBAC), PRD-005 (CI ingestion / external API), PRD-014 (traceability & coverage), PRD-016 (flaky detection), PRD-015 (parameterized results), PRD-025 (MCP) |

---

## 1. Summary

A test plan is how teams track a release (REQUIREMENTS.md §12.2), and every number needed to decide
"can we ship?" already exists: result statuses on the plan's runs, open bug reports, requirement
coverage (`RequirementService.coverage`) and flakiness (`FlakyTestService.analyse`). Today they live
on four different screens and nobody can hold a line against them.

This PRD adds optional **readiness criteria** to a test plan, a computed **GO / NO_GO verdict** with a
per-criterion breakdown, a card on the plan detail page, and a read-only API-key endpoint a CI job can
call to fail a deploy. It is aggregation over existing data plus four nullable columns.

## 2. Goals & Non-Goals

**Goals**
- Per-plan, optional thresholds: minimum pass rate, maximum open blocker bugs, minimum requirement
  coverage, maximum flaky tests.
- A verdict computed on read — never stored — with each criterion's actual value, threshold and
  outcome, so a NO_GO says *why*.
- A plan detail "Readiness" card, and threshold fields on the plan form.
- `GET` on the external API that a pipeline can turn into a non-zero exit with plain `curl --fail`.
- An MCP read tool so an agent can answer "is release X ready?".

**Non-Goals**
- Verdict history, trend charts, or "readiness changed" webhooks/notifications. Add only if someone
  asks; the audit log already records plan edits.
- Configurable formulas, weighted scores or custom criteria (PRD-009 §4: no configurable workflows).
- Blocking actions inside the tool (e.g. refusing to close a plan on NO_GO). The gate informs; the
  pipeline enforces.
- A release/version entity. The test plan *is* the release.
- Instance-wide default thresholds. Copying four numbers onto a new plan is not a problem worth config.

## 3. Proposed Design

### 3.1 Data model (next free V-number, V54+ at time of writing)

Four nullable columns on `test_plans`; `null` means "criterion not used".

```sql
ALTER TABLE test_plans ADD COLUMN gate_min_pass_rate      NUMERIC(5,2);  -- 0..100
ALTER TABLE test_plans ADD COLUMN gate_max_blocker_bugs   INTEGER;       -- >= 0
ALTER TABLE test_plans ADD COLUMN gate_min_coverage       NUMERIC(5,2);  -- 0..100
ALTER TABLE test_plans ADD COLUMN gate_max_flaky          INTEGER;       -- >= 0
```

Vendor-neutral, so it goes in `db/migration/`. Mapped on `TestPlan` as `BigDecimal`/`Integer`, exposed on
`CreateTestPlanRequest`, `UpdateTestPlanRequest` and `TestPlanResponse` as a nested `gate` record, with
`@DecimalMin/@DecimalMax/@Min` validation at the boundary. No separate table: there is exactly one gate
per plan and it has no lifecycle of its own.

### 3.2 Computation — `ReleaseReadinessService` (new, `project/internal/service/`)

Pure evaluation is split from loading so it can be unit-tested without a database:

- `ReadinessInputs load(UUID projectId, UUID planId)` — reads data (the only I/O).
- `static ReadinessResponse evaluate(TestPlan gate, ReadinessInputs inputs)` — pure.

**Pass rate — latest result wins, not all results.** `TestPlanService.getSummary` sums every result of
every run, so a case that failed on Monday and passed on the Wednesday retest counts as one pass and one
fail. That is fine for a progress bar and wrong for a gate. Readiness takes, per
`(testCaseId, parameterSetName)` across the plan's runs, the result from the most recent run by
`COALESCE(run.endTime, run.startTime, run.createdAt)` — the ordering PRD-016 already settled on for the
same reason (CI backfills). `ABORTED` runs are excluded. `PENDING` counts as not passed: an unexecuted
test is not evidence. `passRate = passed / considered × 100`.

The existing `getSummary` pass rate is left unchanged; the card labels its number "effective pass rate"
and the tooltip explains the difference.

**Open blocker bugs.** `BugReport` with `status IN (OPEN, IN_PROGRESS)` and `priority = CRITICAL` in the
project. Project-wide rather than plan-scoped: bugs have no plan link, and a critical bug filed by hand
is exactly as blocking as one filed from a failed result in the plan's run. The priority is fixed at
`CRITICAL` — if teams want `HIGH` to block too, that is a one-line change, not a setting.

**Requirement coverage.** `RequirementService.coverage(projectId).percent` as-is. It is project-wide and
already means "a linked test has passed" (PRD-014), which is the right semantic for a gate. When the
project has zero requirements the criterion is `NOT_APPLICABLE`, not a 0% failure.

**Flaky tests.** Cases from `FlakyTestService.analyse(projectId)` that are flagged flaky *and* have at
least one result in the plan's runs. A flaky test the release never touches is not this release's
problem.

**Verdict.**
- `NO_CRITERIA` — all four thresholds null.
- `NO_GO` — any configured criterion fails.
- `GO` — every configured criterion passes (`NOT_APPLICABLE` counts as passing, and is shown).

```json
{
  "planId": "…", "planName": "Release 3.2", "verdict": "NO_GO",
  "evaluatedAt": "2026-09-17T10:00:00Z",
  "criteria": [
    { "name": "PASS_RATE",       "actual": 96.4, "threshold": 98.0, "outcome": "FAIL" },
    { "name": "BLOCKER_BUGS",    "actual": 0,    "threshold": 0,    "outcome": "PASS" },
    { "name": "COVERAGE",        "actual": null, "threshold": 80.0, "outcome": "NOT_APPLICABLE" },
    { "name": "FLAKY_TESTS",     "actual": 2,    "threshold": 5,    "outcome": "PASS" }
  ],
  "counts": { "considered": 250, "passed": 241, "failed": 6, "blocked": 1, "skipped": 0, "pending": 2 }
}
```

Unconfigured criteria are omitted from `criteria`.

### 3.3 Endpoints (RBAC via PRD-001)

| Method & path | Role | Notes |
|---|---|---|
| `GET /api/projects/{projectId}/test-plans/{id}/readiness` | `@RequireProjectRole` (VIEWER) | Plan looked up with `findByIdAndProjectId`; foreign id → 404 (PRD-027 §3.5). |
| `GET /api/external/projects/{projectRef}/test-plans/{planId}/readiness` | VIEWER via `projectAccessService.requireRoleForCurrentUser` | Added to `ExternalTestRunController`'s sibling as a new `ExternalReadinessController`; `@RequireProjectRole` cannot resolve `projectRef` (see the comment in `ExternalTestRunController.requireTester`). |

The external endpoint returns **200** with the body above by default. With `?enforce=true` a `NO_GO`
returns **412 Precondition Failed** with the same body, so a pipeline step is just:

```bash
curl --fail-with-body -H "X-API-Key: $TM_KEY" \
  "$TM_URL/api/external/projects/PROJ/test-plans/$PLAN_ID/readiness?enforce=true"
```

`NO_CRITERIA` under `enforce=true` is also 412: a pipeline that asks for a gate and gets none has been
misconfigured, and passing silently would be the fail-open behaviour PRD-025 §3.2 removed elsewhere.

Plans have no key, so `planId` is a UUID only. Add a plan key only if CI users ask for one.

Plan edits keep their existing `TESTER` requirement; thresholds ride on `PUT /test-plans/{id}` and are
audited through the existing `AuditService.log(... TEST_PLAN ...)` call.

### 3.4 Frontend

- `features/test-plans/test-plan-form`: an expandable "Release gate" section with four optional numeric
  inputs (native `type="number"` with min/max). Empty = off.
- `features/test-plans/test-plan-detail`: a "Readiness" card above the run list — verdict chip (green
  GO / red NO_GO / grey "No criteria"), one row per criterion with actual vs threshold, and links: failed
  results → the run, blocker bugs → bug list filtered to open critical, flaky → the dashboard widget.
- Refetch on plan detail load; no polling.
- i18n keys in `en.json` and `de.json`.

### 3.5 MCP impact

One read-only tool in `ReportingTools`: `get_release_readiness(planId)`, same response, `readOnlyHint =
true`, in the reporting tool group. No write tool — thresholds are set by humans.

### 3.6 Docs

USER_MANUAL.md: a "Release gate" subsection under Test Plans and the `curl` recipe under the External
API section.

## 4. Edge Cases

- **Plan with no runs** → pass-rate criterion `FAIL` with `considered = 0` (nothing proven), not a
  division error and not GO.
- **Parameterized case** (PRD-015) → each parameter set is its own considered result; a case with three
  sets and one failing contributes one failure, not a failed case.
- **Same case in two environments within the plan** → latest run wins regardless of environment.
  Called out in the card tooltip; environment-aware gating waits for a real configurations model.
- **Run still `IN_PROGRESS`** → its recorded results count (a recorded result is a real observation, as
  in PRD-016); its `PENDING` results count as not passed.
- **Project with no requirements** → coverage `NOT_APPLICABLE`.
- **Flaky analysis with too little history** → not flaky (respects `app.flaky.min-runs`).
- **Thresholds out of range** (pass rate 120, negative bug count) → 400 via bean validation.
- **API key scoped to another project** → 404/403 exactly as the existing external endpoints behave.
- **Deleted plan between CI trigger and gate call** → 404; `curl --fail` fails the job, which is correct.

## 5. Testing

- `ReleaseReadinessService.evaluate` unit tests (pure): each criterion pass/fail at the boundary
  (equal to threshold passes); `NO_CRITERIA`; `NOT_APPLICABLE` coverage does not produce NO_GO; one
  failing criterion among four passing → NO_GO.
- Latest-result selection: fail-then-pass across two runs counts as pass; aborted run ignored; ordering
  by end time not creation time (backfilled CI run); parameter sets counted separately.
- Integration: blocker count ignores `RESOLVED/CLOSED/WONTFIX` and non-critical bugs and other projects'
  bugs; flaky count only includes cases executed in the plan.
- Controller: VIEWER can read, non-member 404, foreign plan id 404; update persists and audits thresholds.
- External: `enforce=false` NO_GO → 200; `enforce=true` NO_GO → 412 with body; `NO_CRITERIA` + enforce → 412;
  key for another project rejected.
- MCP tool returns the same verdict as REST for a fixture plan.
- Frontend: form round-trips empty ↔ null; card renders each verdict state.

## 6. Effort & Risk

- **Effort:** ~4–5 days (migration + DTOs 0.5, service + tests 2, endpoints + external 1, UI 1, MCP + docs 0.5).
- **Risk:** Low. Read-only aggregation. The real risk is definitional — a gate that disagrees with the
  plan's existing pass-rate number will confuse people, hence the explicit "effective pass rate" label.
- **Performance:** one plan's results plus a project-wide flaky scan per call. Fine at the 50-user
  target; if CI polls it hard, cache the flaky set for a minute before reaching for anything else.

## 7. Acceptance Criteria

- [x] Test plans accept four optional gate thresholds; empty means off; changes are audited.
- [x] `GET .../test-plans/{id}/readiness` returns verdict and per-criterion actual/threshold/outcome.
- [x] Pass rate uses the latest result per case and parameter set across the plan's non-aborted runs.
- [x] Blocker bugs = open/in-progress critical bugs in the project; coverage reuses PRD-014; flaky count is limited to cases executed in the plan.
- [x] External endpoint works with a project-scoped API key; `enforce=true` returns 412 on NO_GO and on NO_CRITERIA.
- [x] Plan detail shows a Readiness card; plan form edits thresholds; en/de translations present.
- [x] `get_release_readiness` MCP tool is available and read-only.
- [x] USER_MANUAL documents the gate and the CI recipe.
- [x] Unit, integration, controller and frontend tests pass.

## 8. As Built (2026-09-19)

Built as specified, with these differences:

- **An update without `gate` leaves the thresholds as they are.** `UpdateTestPlanRequest` is otherwise
  whole-object, but MCP's `update_test_plan` merges only the fields it is given; if a missing gate
  meant "clear", an agent renaming a plan would silently remove its release gate. The web app always
  sends the whole gate object, and a null threshold inside it switches that criterion off.
- **Only configured criteria are loaded.** The project-wide flaky scan runs only for plans that gate
  on flakiness, which is the performance concern §6 raised.
- **Pass rate is compared exactly** (`passed × 100 ≥ threshold × considered`) and rounded only for
  display, so 97.995 % shows as 98.00 % and still fails a 98 % gate.
- **Within one run, a case recorded twice** (an ad-hoc result added next to a seeded one) is decided
  by the result's own last update, after ordering runs as specified.
- **Card links:** the pass-rate row has no link (the runs are on the same page); blocker bugs → bug
  list, coverage → requirements, flaky → dashboard. The bug list has no URL-bound filters, so the
  blocker link opens it unfiltered rather than pre-filtered to open critical bugs.
- **The plan page refetches readiness when the stored plan changes.** The plan form saves and
  navigates without waiting for the response, so a readiness fetched on arrival could show the
  verdict from before the edit.
- **Plan response is `gate` with all four fields always present** (null when off), rather than
  omitting an unconfigured gate.

Tests: `ReleaseReadinessEvaluationTest` (16, pure: latest-result selection, boundaries, exact
comparison, verdicts), `ReleaseReadinessApiTest` (17: retest, aborted runs, pending, which bugs block,
coverage with and without requirements, role and cross-project access, gate edits and validation,
the external endpoint with and without `enforce`), `McpReleaseReadinessToolsApiTest` (2: same
verdict as REST, foreign plan); frontend `release-gate-form` and `release-readiness-card` specs.
**1101 backend tests, 37 frontend spec files (199 tests).** V61 applied on PostgreSQL 16 with
`ddl-auto=validate` and its four CHECK constraints present.

Checked in a browser against PostgreSQL: NO GO with two failing criteria and coverage not
applicable, the gate section opening with the saved values, an edit (threshold lowered, one field
emptied) turning the verdict to GO, and a plan without a gate. The CI recipe was run against the
same server with a real VIEWER key: GO → 200, NO_CRITERIA with `enforce=true` → 412 and
`curl --fail-with-body` exiting non-zero. **Not clicked through:** the German strings and dark mode.
