# PRD-038 — Test Run Comparison

| | |
|---|---|
| **Status** | ✅ Implemented 2026-09-19 — see §8 |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-17 |
| **Priority** | P2 — "what changed since last time" is the first question after every run |
| **Target** | v2.4 |
| **Related** | PRD-001 (RBAC), PRD-005 (CI ingestion), PRD-015 (parameterized results), PRD-016 (flaky detection), PRD-024 (build servers / pipeline runs), PRD-025 / PRD-027 (MCP) |

---

## 1. Summary

A run's detail page answers "what is the state now". It cannot answer "what broke since the last run",
which is what a tester or a developer reading a red CI build actually wants. Today that means opening
two runs in two tabs and scanning by eye.

This PRD adds a side-by-side comparison of two runs in the same project, classifying every
`(test case, parameter set)` into **newly failing**, **fixed**, **still failing**, **added**, **removed**,
**other change** and **unchanged**. It is a pure function over results that already exist: no schema
change, one endpoint, one page, one MCP tool.

## 2. Goals & Non-Goals

**Goals**
- Compare a *base* run with a *head* run from the same project.
- When only a head run is given, pick a sensible base automatically (§3.3).
- Match results correctly for parameterized cases (PRD-015) and for CI-ingested runs (PRD-005).
- Headline counts plus grouped, collapsible lists that link to each result.
- A "Compare with…" action on the run detail page, and a read-only MCP tool.

**Non-Goals**
- Comparing more than two runs, or trends across N runs — flaky detection (PRD-016) already covers
  "keeps changing its mind".
- Step-level diffs, comment diffs, screenshot diffs.
- A "build" entity. Two builds are compared by comparing the runs they produced (`PipelineRun.testRun`,
  PRD-024); no new concept is introduced.
- Cross-project comparison (same suite in two projects).
- PDF export of a comparison. `PdfReportService` could render it later if asked.

## 3. Proposed Design

### 3.1 Data model

No migration. Everything needed is on `TestResult` (`status`, `testCase`, `parameterSetName`,
`executedVersion`) and `TestRun` (`key`, `name`, `environment`, `testPlan`, timestamps).

### 3.2 Matching and classification — `RunComparator` (new, pure)

**Match key:** `(testCaseId, parameterSetName)` where a null set name is its own value.

- **CI-ingested runs need nothing special.** `CiIngestionService.resolveOrCreate` resolves each
  reported test to an existing case by title (and creates it once), so the same JUnit test in two
  submissions already points at the same `TestCase` id. Matching by id is therefore exact; the case
  key is used only for display and sorting.
- **Parameterized cases** match per set name. A set renamed between runs appears as one *removed* and
  one *added* row — correct, since the result records the name it ran with and PRD-015 deliberately
  froze `parameterValuesJson` on the result.
- **Duplicates within one run** (a CI report listing the same test title twice, or a manually added
  duplicate result) collapse to the *worst* status by severity `FAILED > BLOCKED > SKIPPED > PENDING >
  PASSED`, and the row carries `duplicates: n` so it is visible rather than silently merged.

**Classification** of each matched key, with "failing" = `FAILED` or `BLOCKED`:

| Base | Head | Category |
|---|---|---|
| PASSED | failing | `NEWLY_FAILING` |
| failing | PASSED | `FIXED` |
| failing | failing | `STILL_FAILING` |
| PASSED | PASSED | `UNCHANGED` (counted, not listed by default) |
| — | any | `ADDED` |
| any | — | `REMOVED` |
| anything else (involves `SKIPPED`/`PENDING`, or FAILED↔BLOCKED) | | `OTHER_CHANGE`, or `UNCHANGED` when statuses are equal |

`BLOCKED → FAILED` is `OTHER_CHANGE`, not "still failing": the environment problem went away and a real
failure appeared, which a reader should see.

Each row also carries `versionChanged: boolean` when `executedVersion` differs and both are non-null
(PRD-011), shown as a small "case edited" marker — a newly failing test whose wording changed is a
different conversation from one that regressed.

```java
record ComparableResult(UUID resultId, UUID testCaseId, String testCaseKey, String title,
                        String parameterSetName, TestResultStatus status, Integer executedVersion) {}

static RunComparison compare(List<ComparableResult> base, List<ComparableResult> head)
```

### 3.3 Service & automatic base — `RunComparisonService`

- Loads both runs with `TestRunRepository.findByIdAndProjectId` (a foreign id is a 404, per PRD-027
  §3.5) and their results through a narrow JPQL projection into `ComparableResult` — no step results,
  no screenshots.
- **Base omitted** → the most recent run in the project, excluding `ABORTED` and the head itself, whose
  `COALESCE(endTime, startTime, createdAt)` precedes the head's, chosen by the first rule that matches:
  1. same `name` (CI runs from one workflow share a name — the workflow name or `runName` parameter);
  2. same `testPlan` and same `environment`;
  3. otherwise none → 404 with a message asking the user to pick a base.

  No "compare with a clever heuristic across all runs" beyond this; the UI always lets the user change
  the base.

### 3.4 Endpoints (RBAC via PRD-001)

| Method & path | Role |
|---|---|
| `GET /api/projects/{projectId}/test-runs/compare?head={runId}&base={runId?}&includeUnchanged=false` | `@RequireProjectRole` (VIEWER) |

Added to `TestRunController`. Response:

```json
{
  "base": { "id": "…", "key": "PROJ-Run-41", "name": "nightly", "environment": "staging", "endTime": "…" },
  "head": { "id": "…", "key": "PROJ-Run-42", "name": "nightly", "environment": "staging", "endTime": "…" },
  "baseAutoSelected": true,
  "counts": { "newlyFailing": 3, "fixed": 5, "stillFailing": 2, "added": 1, "removed": 0, "otherChange": 1, "unchanged": 240 },
  "rows": [
    { "category": "NEWLY_FAILING", "testCaseId": "…", "testCaseKey": "PROJ-17", "title": "Checkout with voucher",
      "parameterSetName": null, "baseStatus": "PASSED", "headStatus": "FAILED",
      "baseResultId": "…", "headResultId": "…", "versionChanged": false, "duplicates": 0 }
  ]
}
```

Rows are ordered by category (the table order above) then case key. `UNCHANGED` rows are only returned
with `includeUnchanged=true`. No pagination: a run of a few thousand results is a few hundred KB of JSON
at worst, and the list is collapsed by category.

No external (API-key) endpoint in this PRD: CI already has the run it just created, and gating belongs
to PRD-037. The regular endpoint works with an API key through the service-user path (PRD-025 §3.2) if
a pipeline wants it.

### 3.5 Frontend

- Route `test-runs/compare` added to `test-runs.routes.ts` *before* `:runId`, with `head` and `base` as
  query params so a comparison is linkable.
- `test-run-detail`: a "Compare with…" button → navigates with only `head`; the page shows the
  auto-selected base with a picker (the existing run list API, filtered to the project) to change it.
- `features/test-runs/test-run-compare/`: header with both runs side by side (key, name, environment,
  end time), count chips, then `mat-expansion-panel` per category — `NEWLY_FAILING` open by default.
  Each row links to the result in each run. Status chips reuse the run detail component's styles.
- Empty state: "No differences" when every category except `UNCHANGED` is empty.
- Test run list: a "Compare with previous" row menu item (same navigation, `head` only). The list has
  no row selection today, and adding checkboxes just to pick two runs is not worth it.
- i18n keys in `en.json` and `de.json`.

### 3.6 MCP impact

`compare_test_runs(head, base?)` in `TestRunReadTools`, accepting UUID or run key via the existing
`McpRunReferences`, `readOnlyHint = true`. Returns the same shape without `UNCHANGED` rows. This is the
tool an agent needs to say "the build broke these three tests".

## 4. Edge Cases

- **Base and head are the same run** → 400.
- **Runs from different projects** → 404 on whichever does not belong to `{projectId}`.
- **Head is older than base** → allowed; the UI shows a "base is newer than head" hint and offers a swap.
  Categories are computed as given.
- **Run with zero results** → every result of the other run is `ADDED` or `REMOVED`.
- **Case deleted** between runs → its results are gone by cascade in both, so it simply does not appear.
- **Case parameterized after the base run** → base has `(case, null)`, head has `(case, "Set A")`,
  `(case, "Set B")`: one removed, two added. Documented; not special-cased.
- **Different environments** → allowed and shown in the header; comparing staging with prod is a
  legitimate question.
- **`ABORTED` head** → allowed when explicit; never chosen as an automatic base.
- **Duplicate results in a run** → worst status wins, `duplicates` shows the count.

## 5. Testing

- `RunComparator` unit tests (pure), one per table row: newly failing, fixed, still failing, added,
  removed, unchanged, BLOCKED→FAILED as other change, SKIPPED involvement, parameter sets matched
  independently, renamed set as removed+added, duplicate collapse to worst with count, version change
  flag only when both versions non-null.
- `RunComparisonService` integration: automatic base picks same-name run over same-plan-and-environment
  run; skips aborted runs and newer runs; no candidate → 404; CI-ingested runs (two `CiIngestionService`
  submissions with the same titles) compare by case, not as all-added.
- Controller: VIEWER allowed, non-member 404, foreign run id 404, same id twice 400, `includeUnchanged`
  toggles row inclusion.
- MCP tool accepts run keys and matches REST output.
- Frontend: query params drive the request; categories render with counts; base picker re-requests.

## 6. Effort & Risk

- **Effort:** ~4 days (comparator + tests 1.5, service/endpoint 1, page + menu actions 1.5).
- **Risk:** Low. Read-only, no schema. The only judgement calls are the category table and the
  automatic-base rules, both of which are small, tested and easy to change.

## 7. Acceptance Criteria

- [x] `GET .../test-runs/compare` classifies results into newly failing, fixed, still failing, added, removed, other change and unchanged.
- [x] Matching is by test case id and parameter set name; CI-ingested runs of the same tests match.
- [x] Omitting `base` selects the previous non-aborted run with the same name, else the same plan and environment, else 404.
- [x] Foreign or cross-project run ids return 404; identical base and head return 400.
- [x] Run detail has "Compare with…"; run list rows have "Compare with previous"; the comparison URL is shareable.
- [x] `compare_test_runs` MCP tool is read-only and accepts run keys.
- [x] en/de translations present; USER_MANUAL documents the feature.
- [x] Comparator, service, controller, MCP and frontend tests pass.

## 8. As Built (2026-09-19)

Built as specified, with these differences:

- **The same-plan-and-environment fallback needs the head to have a plan.** Otherwise two unrelated
  ad-hoc runs would be paired because both have *no* plan. A run without an environment matches
  runs without one; the query compares `COALESCE(environment, '')` with `''` rather than binding a
  null, which PostgreSQL cannot type.
- **`duplicates`** counts the results beyond one per run for a key, both runs together.
- **Rows sort by category, then case key in number order** (PROJ-9 before PROJ-10), then parameter set.
- **Each run carries `happenedAt`** (end, else start, else creation time) so the page can tell when
  the base is newer than the head and offer a swap.
- **Linking to a result:** the run page had no way to open one result, so it now takes
  `?result=<id>`: selected during execution, otherwise its panel opened and scrolled to.
- **Run list:** a compare icon beside report, clone and delete, as the list has icon buttons rather
  than a row menu. "Show unchanged" is a switch on the compare page, bound to `unchanged=true`.

Tests: `RunComparatorTest` (26: every row of the category table, parameter sets, renamed sets, a
case becoming parameterized, duplicate collapse, version flag, ordering), `RunComparisonApiTest`
(12: automatic base by name, then plan and environment, skipping aborted and later runs, runs
without a plan, two CI uploads matching by case, same run twice, access), and
`McpRunComparisonToolsApiTest` (3: keys, automatic base, foreign key); frontend `comparison-view`
and `test-run-compare.component` specs. **1142 backend tests, 39 frontend spec files (209 tests).**

Checked against PostgreSQL over HTTP (both automatic-base queries, including a run without an
environment) and in a browser: every category on a seeded pair, a status badge opening its result on
the run page, the base-newer hint and swap, the run list's compare icon, and a run with no earlier
run showing the reason with the picker still usable. **Not clicked through:** the German strings,
dark mode, and the "Show unchanged" switch.
