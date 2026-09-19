# PRD-052 — Lists, Filters and Pickers

| | |
|---|---|
| **Status** | ✅ Implemented 2026-09-19 — see §8 |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-19 |
| **Priority** | P2 — daily friction in the three most used screens, each fix small |
| **Target** | v2.5 |
| **Related** | PRD-002 (filtering/pagination), PRD-008 (usability bundle), PRD-032 (environment roll-up on plans), PRD-045 (bug list sorting), PRD-051 (image viewer) |

**Source** (change requests filed as bugs on the test instance, 2026-09-17):

| Report | Asks for |
|---|---|
| `e446aed3` | Label filter on the test case list (multi-select, AND), click a label chip to filter |
| `2a9cdc40` | "No test cases yet" shown when a filter has no matches; distinguish the two, also in Start Test Run |
| `5613fff3` | Suite editor: search and folder/label filters over the case checklist, "n selected", select all visible |
| `6b095f97` | Case detail step layout: test data under action/expected, no empty cell beside it |
| `f3e1c5de` | Sortable plan list and plan run table; the run table gets a key column and a defined order (the bug-list part is in PRD-045) |

---

## 1. Summary

Five small, independent frictions in lists and pickers. Like PRD-008, this is a bundle: each item
ships and can be reviewed on its own.

### Key decision: the label filter means "all of these labels"

The backend already filters by label: `label` on `GET test-cases` (`TestCaseController.java:66`), in
`TestCaseSpecifications.java:56-63` and on MCP `search_test_cases`. It matches cases carrying **any**
of the given labels (`labels.in(...)` inside one `EXISTS`). The MCP parameter, however, is documented
as *"Only cases carrying all of these labels"* (`TestCaseTools.java:83`), and the requester asks for
AND. A multi-select that narrows as you add labels is the useful behaviour: "req-aca-027 and
negativ" answers a question, while "either" is rarely wanted. The specification changes to AND, one
`EXISTS` per label, which fixes the MCP contract at the same time. The UI has never sent `label`,
so the only other caller is MCP, whose documentation already promised AND.

## 2. Goals & Non-Goals

**Goals**
- Filter the test case list by labels (AND), bound to the URL like status and priority; clicking a
  label chip adds it to the filter.
- Every empty list says which situation it is in: nothing in the project, nothing in this folder, or
  nothing matching the filters (with "Reset filters").
- One case picker, used by both Start Test Run and the suite editor, with search, folder and label
  filters, a selection counter, "select all visible", and the current selection shown above the list.
- Step layout on the case page without empty cells.
- Sortable plan list and plan run table; the run table gets a key column and a stable default order.

**Non-Goals**
- Bug list sorting, search and filters (PRD-045).
- Saved filters or views.
- Grouping the plan run table by environment: the environment roll-up table on the plan page
  (`test-plan-detail.component.html:152-181`, PRD-032) already answers "how is staging doing".
- Label management (rename or merge a label across cases).

## 3. Proposed Design

No migration.

### 3.1 Label filter (`e446aed3`)

- **Backend:** in `TestCaseSpecifications`, one correlated `EXISTS` per requested label (the existing
  subquery, with `cb.equal(labels, value)` instead of `labels.in(...)`), which gives AND. The MCP
  description is already correct. REST gets an OpenAPI description: "all of".
- **New endpoint:** `GET /api/projects/{projectId}/test-cases/labels` (VIEWER) returns the project's
  distinct labels, sorted, through one `SELECT DISTINCT` over the `@ElementCollection`. It feeds the
  filter's options.
- **Frontend** (`test-case-list`): a "Labels" multi-select next to Status and Priority (`:178-195`),
  read from and written to the `label` query parameter, like the others. `TestCaseApiService` already
  sends `label` (`:21`). The label chips in the table (`matColumnDef="labels"`, `:272`) become buttons
  that add their label to the filter.

### 3.2 Empty states (`2a9cdc40`)

Today every empty list shows `testCase.list.empty` ("No test cases yet…") with a Create button:
`test-case-list.component.html:208-216`, the run form (`test-run-form.component.html:90`) and the
suite form (`test-suite-form.component.html:23`). The list decides from what it already knows:

| Situation | Message | Action |
|---|---|---|
| A search, status, priority or label filter is set | "No test cases match the filters." | Reset filters (clears them in the URL) |
| Only a folder is selected | "This folder is empty." | Create test case (in this folder) |
| Nothing set | "No test cases yet…" (today's text) | Create test case |

The pickers (§3.3) use the first and last rows.

### 3.3 One case picker (`5613fff3`)

The run form and the suite form each render their own checklist from the global test case store,
loaded with `size: 200` (`test-run-form.component.ts:100`, `test-suite-form.component.ts:70`). That
store is the one the case list page uses, and 200 is the server's `max-page-size`
(`application.yml:52`), so a larger project silently loses cases from both pickers.

A shared `shared/components/test-case-picker`:

- **Inputs:** `projectId`, `selectedIds`. **Output:** the selection changed.
- **Loading:** calls `TestCaseApiService` directly (not the store, so it no longer overwrites the
  list page's state), with `q`, `folderId` (sub-folders included) and `label` sent to the server.
  When `totalElements` exceeds what was loaded, it says "Showing 200 of 340 — narrow the filters".
- **Controls:** search (debounced), folder select (the run form's `flattenFolders` moves here), label
  multi-select (§3.1 endpoint), "n selected", "Select all visible", "Clear".
- **Layout:** selected cases that the current filter hides stay listed in a "Selected" group above the
  results, so filtering never silently drops a selection.
- Both forms replace their checklist with it. What they send is unchanged: the selected ids. An empty
  selection still creates a run with no results (`TestRunService.java:247`), which is today's
  behaviour.

### 3.4 Step layout on the case page (`6b095f97`)

`step-spec-card.component.html` renders test data and the image as two grid cells whenever either
exists, so a step with test data but no image shows an empty right-hand cell. The fix:

- test data spans the full width under action/expected (`grid-column: 1 / -1`) when there is no image;
- with an image, the two cells stay side by side;
- neither present: no row, as today.

CSS and template only. The same card is used in run execution, which gets the same fix. PRD-051's
image viewer then makes the thumbnail clickable.

### 3.5 Plan list and plan run table (`f3e1c5de`)

- **Plan list** (`test-plan-list`): loaded unpaged (`TestPlanApiService.getAll`) and filtered
  client-side (`filteredTestPlans`), so `MatSort` on the table's data source sorts name, status,
  target date and run count. It follows the sort-header pattern of `test-run-list`.
- **Plan run table:** `TestPlanService.getSummary` iterates `plan.getTestRuns()`, an `@OneToMany`
  without `@OrderBy` (`entity/TestPlan.java:51-52`), which is why the order looks random.
  - `@OrderBy("createdAt ASC")` on `testRuns` gives creation order, which is key order since run
    numbers are sequential.
  - `TestPlanRunSummary` gains `key`.
  - The table gets a Key column and client-side `MatSort` on every column, default key ascending.

### 3.6 MCP impact

`search_test_cases`'s label filter now does what its description says (AND). No new tools.

### 3.7 Docs

USER_MANUAL: the label filter and AND semantics in "Folders, labels and search"; the picker's filters
in "Test runs" and "Test suites".

## 4. Edge Cases

- **Label with different case** ("Smoke" vs "smoke"): labels are compared exactly, as stored; the
  distinct-labels list shows both. Normalising labels is out of scope.
- **Label filter plus a folder:** both apply (AND), as with status today.
- **Old bookmarked URL with several labels:** now narrows instead of widening. Acceptable: the UI never
  produced such a URL.
- **Picker selection of a case later deleted:** the suite form already sends only existing ids; the
  "Selected" group shows only cases it can load.
- **Plan with runs created in the same millisecond:** `createdAt` ties fall back to database order;
  the Key sort in the UI is exact.

## 5. Testing

- Specification: two labels return only cases with both; one label unchanged; MCP `search_test_cases`
  with two labels returns the intersection.
- Labels endpoint: distinct, sorted, project-scoped (another project's labels never appear).
- Test case list: the label filter round-trips through the URL; chip click adds the label; each of
  the three empty states.
- Picker: filters are sent to the API; a hidden selection stays in "Selected"; select all visible;
  the "showing n of m" hint.
- Plan summary: runs in creation order with keys; the plan list and run table sort.
- Step card: test data full width without an image; side by side with one.

## 6. Effort & Risk

- **Effort:** ~3–4 days. Labels 1, empty states 0.5, picker 1.5, step layout 0.25, plan tables 0.5.
- **Risk:** low. The AND change is the only behaviour change to an existing API; its sole existing
  caller documented AND already.

## 7. Acceptance Criteria

- [x] The test case list filters by labels (all of them), via a multi-select and by clicking a chip; the URL keeps it.
- [x] `GET test-cases/labels` returns the project's distinct labels.
- [x] Empty lists distinguish "no match", "empty folder" and "empty project".
- [x] Start Test Run and the suite editor share one picker with search, folder and label filters, counter and select-all-visible.
- [x] The pickers no longer overwrite the case list's store state, and say when not every match is shown.
- [x] Step test data spans the card when there is no image.
- [x] The plan list and the plan run table sort by every column; the run table has a key column and a creation-order default.
- [x] Tests pass; en/de translations present.

## 8. As Built (2026-09-19)

Built as specified, with these differences:

- **`TestPlanRunSummary.key` already existed** (PRD-049), so the plan run table only needed the
  column. `@OrderBy("createdAt ASC")` on `TestPlan.testRuns` gives the creation order; the table's
  default sort is the key, compared naturally, so `Run-10` follows `Run-2`.
- **Client-side sorting is one helper**, `shared/utils/sort-rows.ts`, used by the plan list and the
  plan run table, instead of a `MatTableDataSource` in each. Empty values sort last in both
  directions. The plan list keeps the server's order until a header is clicked.
- **Label chips are plain buttons styled as chips**, not `mat-chip-option`, which is a listbox option
  and cannot be a button. The tooltip says what a click does.
- **"No match" counts custom field filters too.** Reset clears search, status, priority, labels and
  every `cf.*` parameter, and keeps the folder, sort and page size.
- **The picker's selection is `[(selected)]` of `{ id, key, title }`**, not bare ids, so a suite's
  existing cases show by name under "Selected" before any search has loaded them. Results are
  sorted by title. The picker reads the folder tree from the store, which it loads itself; it no
  longer touches the test case store, and neither form loads it.
- **"Select all visible" reads "Select all shown"** in the UI.
- **Not verified in a browser.** No migration.
