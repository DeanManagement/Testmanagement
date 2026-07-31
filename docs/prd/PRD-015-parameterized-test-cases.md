# PRD-015 — Parameterized / Data-Driven Test Cases

| | |
|---|---|
| **Status** | Proposed |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-09 |
| **Priority** | P3 — v2.0, build on real demand |
| **Target** | v2.0 |
| **Related** | REQUIREMENTS.md §13.3 (was PRD-009 §2.6) |

---

## 1. Summary

Let a single test case carry variable placeholders (e.g. `{username}`) and one or more parameter sets; when the case is added to a run, it expands into N executable results — one per set — with placeholders substituted in step text. Useful for boundary/combinatorial testing without duplicating cases.

This adds modelling complexity, so weigh it against the "small, simple, fast" bar — build only on real demand.

## 2. Goals & Non-Goals

**Goals**
- Optional parameter sets on a test case (named key/value rows).
- `{placeholder}` substitution in step `action`/`expectedResult`/`testData`.
- On run creation, a parameterized case expands to one result per set; each result shows the substituted steps and which set it used.

**Non-Goals**
- Computed/generated data (random, sequences) — static sets only.
- Cross-case shared datasets / global parameter libraries.
- Combinatorial auto-generation (pairwise) — sets are authored explicitly.

## 3. Proposed Design

### 3.1 Data model (migration V39)
- `test_case_parameter_sets`: `id`, `test_case_id` (FK), `name`, `values_json` (`{ "username":"alice", "amount":"100" }`), `order_index`. A case with zero sets behaves exactly as today (no expansion).
- `test_results`: add `parameter_set_name` (nullable) and `parameter_values_json` (nullable) to record which set a result executed, so substitution is reproducible and auditable.

### 3.2 Expansion flow
- In `TestRunService.create` (and add-case-to-run paths): for each selected case, if it has parameter sets, create one `TestResult` per set; otherwise one result as today. Store the set name + values on the result.
- Substitution is applied at **read/display time** (and when stamping step results) using the stored values, so the underlying step text stays templated and editable. A shared `ParameterSubstitutor` replaces `{key}` tokens; unknown tokens are left literal and flagged in the UI.

### 3.3 Endpoints & UI
- Parameter set CRUD under the test case: `GET/POST/PUT/DELETE /api/projects/{projectId}/test-cases/{id}/parameter-sets` (read VIEWER, write TESTER).
- Test-case editor: a "Parameters" section (table of sets, each a key/value grid) + a live preview of substituted steps.
- Run execution: results from a parameterized case are labelled with the set name; step text shows substituted values; the test-case link still points to the template.

## 4. Edge Cases
- Placeholder with no matching key → render the literal `{key}` and show a subtle "unresolved parameter" hint.
- Editing the template after a run executed: historical results keep their stored `parameter_values_json` (display is reproducible); pairs well with PRD-011 versioning if both are present.
- Removing all sets reverts to single-result behavior for future runs.
- Reasonable cap on sets per case (e.g., 50) to avoid run blow-up.

## 5. Testing
- A case with N sets expands to N results on run creation; a case with no sets yields exactly one.
- Substitution replaces known tokens, preserves unknown ones, across action/expected/testData.
- Stored `parameter_values_json` makes a result's substituted view reproducible after template edits.
- Parameter-set CRUD authz + the per-case cap.

## 6. Effort & Risk
- **Effort:** ~2 weeks (model, expansion, substitution, editor UI).
- **Risk:** Medium — the run-expansion and result-count change touch a core flow; keep zero-set behavior byte-for-byte identical to today.

## 7. Acceptance Criteria
- [ ] A parameterized case expands to one result per set when added to a run; non-parameterized cases are unchanged.
- [ ] Step text shows substituted values; unknown placeholders are preserved and flagged.
- [ ] Each result records its set name + values; the substituted view is reproducible.
- [ ] Parameter-set CRUD is role-gated; expansion/substitution tests pass.
