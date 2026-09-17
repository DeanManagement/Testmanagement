# PRD-040 — BDD / Gherkin Support

| | |
|---|---|
| **Status** | 📝 Draft |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-17 |
| **Priority** | P3 — driver-dependent: build when a team writing Cucumber scenarios adopts the tool |
| **Target** | v2.4 |
| **Related** | PRD-004 (import/export), PRD-005 (CI ingestion — Cucumber JSON), PRD-011 (versioning), PRD-015 (parameterized cases), PRD-001 (RBAC) |

---

## 1. Summary

The tool already *ingests* Cucumber results (`CucumberJsonParser`, PRD-005), but it knows nothing about
the scenarios that produced them. `CiIngestionService.resolveOrCreate` matches each scenario to a test
case by the title `"<Feature> - <Scenario>"`, so a BDD team ends up with a parallel set of
`ci-imported` cases that drift from the `.feature` files in their repository, break when a scenario is
renamed, and cannot be planned or run manually.

This PRD closes the loop with three pieces:

1. **Import `.feature` files** into ordinary test cases.
2. **Export test cases as `.feature` files**, stamping each scenario with a `@tm:<case key>` tag.
3. **Match Cucumber JSON results by that tag** before falling back to today's title matching.

Plus a small "edit as Gherkin" textarea on the test case form.

### Key decision: Gherkin is a converter, not a second case format

A `format = STEPS | GHERKIN` column with a raw Gherkin text field was considered and rejected. Every
consumer of a test case — manual execution with per-step results, `StepResult`, version snapshots
(PRD-011), parameter substitution (PRD-015), PDF reports, CSV/JSON export, search, MCP authoring tools —
would need a second code path, and the two representations would disagree the moment one is edited.

Gherkin maps cleanly onto the model that already exists:

| Gherkin | Test management |
|---|---|
| `Feature:` | `TestCaseFolder` (name) |
| `Rule:` | child `TestCaseFolder` |
| `Scenario:` / `Scenario Outline:` | `TestCase` (title = scenario name) |
| Scenario description | `TestCase.description` |
| Each step (`Given/When/Then/And/But/*`) | one `TestStep`, `action` = keyword + text, verbatim |
| Step doc string / data table | `TestStep.testData` (rendered text) |
| `Background:` steps | `TestCase.preconditions` (one line per step) |
| Tags (feature + rule + scenario, inherited) | `TestCase.labels` (without `@`) |
| `@tm:PROJ-17` tag | identity: the case key, not a label |
| `Examples:` rows | `TestCaseParameterSet`s; `<name>` placeholders → `{name}` |

Since steps stay steps, manual execution, history, reports and MCP work unchanged on BDD cases.

## 2. Goals & Non-Goals

**Goals**
- Import one or more `.feature` files into a chosen folder, with the existing dry-run flow.
- Re-import updates cases that carry a `@tm:` key (feature files as the source of truth), producing
  normal version history.
- Export a folder (or the whole project) as `.feature` file(s) that round-trip.
- Cucumber JSON results land on the right case even after a scenario is renamed, and Scenario Outline
  example rows land on the right parameter set.
- Author a single scenario as Gherkin text in the case form.

**Non-Goals**
- Executing scenarios, step definitions, glue code, or any runner integration.
- Syncing feature files from a git repository automatically. A build-server workflow (PRD-024) can call
  the import endpoint; a built-in git poller waits for a driver.
- An IDE-grade editor: no syntax highlighting, autocompletion, or step-definition lookup (PRD-009 §4, no
  rich editors). A monospace `<textarea>` only.
- Preserving Gherkin comments, feature descriptions and formatting on round trip (§4).
- Changing how JUnit XML results are matched.
- Deleting cases whose scenarios disappeared from a re-imported file. Reported in the dry run; removal
  stays a human action.

## 3. Proposed Design

### 3.1 Data model

**No migration.** Every Gherkin element maps to an existing column (§1 table). The case key tag is not
stored as a label; the key already exists on `TestCase.key`.

### 3.2 Parsing — dependency

Add `io.cucumber:gherkin` (MIT, the official parser used by every Cucumber implementation). A hand-written
parser would have to handle localized keywords (German teams write `Funktionalität` / `Angenommen`),
doc strings, data tables, `Rule`, and `Examples` — exactly the edge cases a homegrown parser gets wrong.
This is the one new dependency; it has no transitive runtime dependencies beyond Cucumber's `messages`.

### 3.3 Import — `GherkinImporter` (new, in `project/internal/service/`)

Wired into the existing `TestCaseImportExportService.importData`, which already dispatches on file name:
a `.feature` extension (or a `.zip` of `.feature` files) routes to Gherkin.

- Walk the parser's `GherkinDocument` AST, not the expanded "pickles": the source structure (outline +
  examples) is what maps to a case with parameter sets.
- Resolve/create folders under the target folder (`folderId` request param, default root): `Feature`
  name, then `Rule` name. An existing folder with the same name under the same parent is reused.
- For each scenario build a `CreateTestCaseRequest` (steps, labels, description, preconditions from the
  nearest `Background`), plus parameter sets from `Examples`:
  - one set per example row; name = `"<Examples name or 'Example'> #<row>"`; values = header → cell;
  - `<placeholder>` in step text and `testData` rewritten to `{placeholder}` so `ParameterSubstitutor`
    (PRD-015) substitutes them at execution.
- **Identity:** a scenario tagged `@tm:<KEY>` where `<KEY>` exists in this project
  (`TestCaseRepository.findByKeyAndProjectId`) is an **update** of that case via
  `TestCaseService.update` (creating a PRD-011 version when content changed; no-op when identical).
  Parameter sets are replaced through `ParameterSetService`. Untagged scenarios are **creates**.
- A `@tm:` key that does not exist in this project is an error row, not a silent create — it is either
  a typo or a file from another project.
- `status` defaults to `ACTIVE` for imported scenarios (they are already executable specs);
  `priority` defaults to `MEDIUM`, overridable by `@priority:high` style tags (consumed, not labelled).
- Limits: the existing `MAX_IMPORT_ROWS = 500` applies to scenarios per request.

Response reuses `ImportResultResponse`, extended with `updated` and `unchanged` counts and a `warnings`
list (e.g. "Feature description dropped", "Scenario X in folder Y has no counterpart in the file").

### 3.4 Export — `GherkinExporter`

`GET /api/projects/{projectId}/test-cases/export?format=feature&folderId={id?}` on
`TestCaseImportExportController`.

- One `.feature` per folder that directly contains cases: `Feature: <folder name>`; child folders of a
  feature folder become `Rule:` blocks (one level; deeper nesting is flattened with a warning in a
  header comment). Cases with no folder go to `Unfiled.feature`.
- A single file downloads as `text/plain`; more than one is a ZIP (`java.util.zip`, no dependency).
- Each scenario gets `@tm:<KEY>` first, then its labels as tags (labels containing whitespace are
  slugified and noted in a comment).
- Parameterized cases export as `Scenario Outline` with a single `Examples:` table; `{x}` → `<x>`. Sets
  with differing key sets are unioned; missing cells are empty.
- **Steps not written as Gherkin** (action does not start with a keyword in the case's language):
  exported with the `*` keyword, and a non-empty `expectedResult` is emitted as `# expected: …` on the
  next line. This keeps export total rather than refusing classic cases, and is called out in the
  manual as lossy.
- `preconditions` export as a `Background` only when every case in the folder has identical
  preconditions; otherwise as a `# preconditions:` comment above the scenario.

### 3.5 Result matching — `CucumberJsonParser` and `CiIngestionService`

- `CucumberJsonParser.Element` gains `tags` (`[{ "name": "@tm:PROJ-17" }]` in standard Cucumber JSON) and
  `id`. `CiResult` gains a nullable `testCaseKey`; `JUnitXmlParser` passes null.
- `CiIngestionService.resolveOrCreate`:
  1. `testCaseKey` present and found in the project → that case.
  2. `testCaseKey` present but unknown → today's title path, with
     `"Unknown test case key tm:<KEY>"` prepended to the result comment so the mismatch is visible
     rather than silently creating a lookalike.
  3. No key → today's title behaviour, unchanged.
- **Scenario Outline rows:** Cucumber JSON emits one element per example row sharing the scenario.
  For a keyed case that has parameter sets, results for that key within one submission are assigned
  to sets in `orderIndex` order and get `parameterSetName` / `parameterValuesJson` set. If the row count
  differs from the set count, rows beyond the sets are recorded without a set name and the run's
  comment notes the mismatch.
- Step results continue to map by index (`min(ciSteps, caseSteps)`), which now lines up because
  imported steps are exactly the scenario's steps. `Background` steps appear in Cucumber JSON before
  scenario steps (as `type: "background"` elements, already skipped by the parser) so indices stay aligned.

### 3.6 Endpoints (RBAC via PRD-001)

| Method & path | Role | Change |
|---|---|---|
| `POST /api/projects/{projectId}/test-cases/import?dryRun=&folderId=` | `TESTER` | Accepts `.feature` / `.zip` |
| `GET /api/projects/{projectId}/test-cases/export?format=feature&folderId=` | VIEWER | New format |
| `POST /api/projects/{projectId}/test-cases/gherkin/preview` (body: text) | `TESTER` | Parses one scenario, returns `CreateTestCaseRequest`-shaped JSON + warnings; writes nothing |
| `POST /api/external/projects/{projectRef}/test-runs/cucumber` | `TESTER` (existing) | Key-based matching only; no signature change |

`folderId` is resolved with a project-scoped lookup; a foreign folder id is a 404 (PRD-027 §3.5).
The import endpoint is also reachable with a project-scoped API key through the service-user path
(PRD-025 §3.2), so a pipeline can push feature files on merge.

### 3.7 Frontend

- `import-test-cases-dialog`: accept `.feature` and `.zip`, add a target folder picker, show
  created / updated / unchanged / errors / warnings in the dry-run preview.
- Test case list export split-button: add "Gherkin (.feature)" (respects the selected folder).
- `test-case-form`: an "Edit as Gherkin" toggle. Steps → Gherkin is a client-side join (keyword lines,
  `*` for non-keyword steps). Gherkin → steps calls `gherkin/preview` and replaces the form's steps,
  description and parameter placeholders, showing warnings inline. Monospace textarea only.
- i18n keys in `en.json` and `de.json`.

### 3.8 MCP impact

None required: BDD cases are ordinary cases, so `create_test_case`, `get_test_case` and the execution tools
(PRD-025/027) already work on them. An `import_feature_file` tool is deliberately left out until an agent
workflow needs it.

### 3.9 Docs

USER_MANUAL: a "BDD / Gherkin" section — the mapping table, the `@tm:` tag convention, the recommended
flow (import once → export → commit the tagged files → re-import on change → report Cucumber JSON), and
what does not round-trip.

## 4. Edge Cases

- **Not preserved on round trip:** Gherkin comments, feature-level description, blank-line layout, tag
  order, nested rules beyond one level, and `# expected:` comments on re-import. Import reports each
  drop as a warning.
- **Localized keywords** (`# language: de`) → parser handles them; exporter writes English keywords unless
  every step of every case in the file starts with the same language's keywords, in which case it writes
  that `# language:` header. Detection is a keyword lookup from the parser's dialect data.
- **Same scenario name twice in a feature** → two cases; title collisions are allowed today.
- **Scenario renamed in the file** → keyed: title updated on the same case, results keep history.
  Unkeyed: a new case, exactly like today. The dry run lists existing cases in the target folder that
  no scenario matched, and leaves the call to the user; no similarity guessing.
- **`@tm:` key from another project** → error row on import; comment prefix on CI ingestion.
- **Scenario Outline with no Examples** → case without parameter sets; `<x>` left literal (consistent with
  `ParameterSubstitutor` leaving unknown placeholders literal).
- **Example headers that are not identifier-like** (`<first name>`) → set values keep the header; the
  placeholder is rewritten to `{first_name}` and the value key normalised to match; warning emitted.
- **Data table in a step** → rendered as a pipe table into `testData`; exported back as a table if it
  still parses as one, otherwise as a doc string.
- **Case with steps edited in the UI after import** → next export reflects the UI edit; next re-import
  of an older file overwrites it (with a version snapshot preserving the UI edit).
- **More than 500 scenarios in one upload** → 400 with the existing limit message; split the upload.
- **Cucumber JSON without tags** (older formatter options) → today's title path, unchanged.

## 5. Testing

- `GherkinImporter` unit tests over fixture `.feature` files: feature→folder, rule→child folder, tags
  inherited into labels, `@tm:` consumed, background→preconditions, doc string and data table → testData,
  outline → parameter sets with `<x>`→`{x}`, German dialect, unknown key → error row.
- Re-import: keyed unchanged scenario → `unchanged`, no new version; keyed changed → `updated` and a
  PRD-011 version; missing scenario → warning, case untouched.
- `GherkinExporter`: round trip import→export→import yields identical cases (titles, steps, labels,
  parameter sets); classic case exports with `*` and `# expected:`; multiple folders → ZIP.
- `CucumberJsonParser`: tags extracted; `testCaseKey` set only for `@tm:` tags.
- `CiIngestionService`: keyed result hits renamed case; unknown key falls back with comment prefix;
  outline rows map to sets by order; row/set count mismatch recorded without set name and noted.
- Controller/RBAC: VIEWER can export but not import; foreign `folderId` 404; preview writes nothing.
- Frontend: Gherkin toggle round-trips steps; dialog shows updated/unchanged counts.

## 6. Effort & Risk

- **Effort:** ~2 weeks — importer 3 days, exporter 2, CI matching 1.5, form toggle + dialog 2, docs and
  round-trip fixtures 1.5.
- **Risk:** Medium. The mapping is straightforward; the risk is in round-trip expectations. BDD users treat
  feature files as code, and any reformatting on export will be noticed in diffs. Mitigation: the
  documented flow makes the repository the source of truth (import), with export used once to stamp keys.
- **Dependency risk:** low — `io.cucumber:gherkin` is stable, MIT, and has no heavy transitive graph.
- **Driver-dependence:** worth building when a team that already writes Cucumber adopts the tool. Until
  then, PRD-005 ingestion alone serves CI-only BDD users adequately.

## 7. Acceptance Criteria

- [ ] `.feature` and `.zip` uploads import through the existing import endpoint with dry run and folder target.
- [ ] Features, rules, scenarios, backgrounds, tags, doc strings, data tables and examples map as in §1.
- [ ] Scenarios tagged `@tm:<KEY>` update their case (versioned) instead of creating a new one; unknown keys are errors.
- [ ] `format=feature` export produces tagged `.feature` files (ZIP for several folders) that re-import without changes.
- [ ] Cucumber JSON results match cases by `@tm:` tag first, fall back to title matching, and map outline rows to parameter sets.
- [ ] The test case form can edit a single scenario as Gherkin text via the preview endpoint.
- [ ] No schema migration; one new dependency (`io.cucumber:gherkin`).
- [ ] en/de translations and USER_MANUAL section present.
- [ ] Importer, exporter, parser, ingestion, controller and frontend tests pass.
