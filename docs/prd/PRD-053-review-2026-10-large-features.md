# PRD-053: Larger Features from the October 2026 Review

**Source:** `docs/reviews/TM-Tool_Bugs_und_Verbesserungsvorschlaege.docx` (project SN, 28.10.2026, 40 items).
**Status:** Proposed — needs a go/no-go per section before implementation.

The review's six bugs (TM01, TM02, TM04, TM25, TM38, TM39) and TM40 shipped on 2026-10-28. The
MCP CRUD gaps (TM01, TM08, TM13–TM16, TM26, TM27, TM29–TM35), the smaller UI features (TM06, TM08,
TM09, TM11, TM17, TM20–TM22) and Markdown rendering (TM03) are implemented separately. This PRD
covers what is left: items that add a concept or change the schema in a way worth deciding first.

Every section is independent. Sizes: S ≤ 1 day, M ≤ 3 days, L ≤ 2 weeks.

---

## 1. Documents on test steps (TM05, part of TM13) — M

**Problem.** A step takes one image. Documents (PDF, Office) attach only to the whole case, and in
a run they appear under "Preconditions and description", detached from the step that needs them.
A run step takes one screenshot, images only.

**Design.**
- Reuse `Attachment` (it already carries size, sha256, media-type checks and the 10 MB cap) and
  add a nullable `test_step_id` FK, so an attachment belongs to a case, a bug report or a step.
  Keep `StepImage` as is: it is the inline reference picture; attachments are the documents list.
- Step attachments follow the step: copied when a case is copied (TM09), kept when steps are
  reordered (TM06), deleted with the step. Replacing a case's steps through `update_test_case`
  already loses step images; attachments must follow the same rule *and say so* in the tool
  description, or better, be carried over by position like images are.
- Run view shows a step's attachments beside the step. Results: allow several evidence files per
  step result (images and documents), replacing the single `Screenshot`. Migration copies the
  existing screenshot into the new list.
- MCP: `upload_step_attachment`, `list/download/delete` share the TM13 test-case attachment tools
  with an optional `stepId`.

**Edge cases.** Version history (PRD-011) snapshots steps as JSON — record attachment names in the
snapshot, not the bytes. Shared steps: attachments on a shared step's own steps show in every case
that uses it.

**Acceptance.** A PDF attached to step 3 shows at step 3 in the editor, the detail view and the run;
an agent can attach one via MCP; existing screenshots survive the migration.

---

## 2. Linked test cases and pre-condition cases (TM07) — M

**Problem.** Cases cannot reference each other. Xray models a reusable pre-condition as its own
object linked to many tests.

**Design.**
- New table `test_case_links(id, source_case_id, target_case_id, link_type, created_by, ...)` with
  `link_type` ∈ `PRECONDITION`, `RELATES_TO`, unique on (source, target, type), no self-links.
- Detail view: a "Linked test cases" section; `PRECONDITION` links render above the preconditions
  text ("Run first: SN-12 …") — the free-text preconditions field stays, as the review requires.
- Run view: a case's precondition cases are listed with their latest result *in the same run* when
  present, so the tester sees whether the setup passed.
- MCP: `link_test_cases` / `unlink_test_cases`; `get_test_case` returns links both ways.

**Out of scope.** Automatically adding precondition cases to a run, or ordering by them — offer it
later if asked; the link is the prerequisite.

**Status (2026-10-28):** implemented (V90). A precondition link both ways round between the same
two cases is refused; a RELATES_TO link counts as one whichever way it was made.

---

## 3. Saved and shared filters (TM10) — S/M

**Problem.** The test-case list filters (PRD-002, URL-bound) cannot be saved or shared.

**Design.** The filters already live in the URL query string, which is the whole state. Store it:
`saved_filters(id, project_id, owner_id, name, view, query_string, shared)`. A menu next to the
filter bar lists "My filters" and "Shared in project"; saving takes the current query string.
Shared filters can be edited by their owner and project ADMINs.

**Explicitly not:** a JQL-style AND/OR query language. The review mentions it, but the structured
filters plus saved combinations cover the stated need; revisit only with a concrete query that
cannot be expressed.

---

## 4. Test specification export, PDF and Word (TM12) — M

**Problem.** Cases export only as CSV; there is no printable specification.

**Design.** The backend already renders PDFs with openhtmltopdf (`PdfReportService`,
`RunReportHtml`). Add `TestSpecHtml` (cases in folder order: key, title, metadata, description,
preconditions, steps table with test data and expected results, step images inline) and render it
through the same pipeline. Selection: the current filter, selected cases, or a folder subtree.
Word: render the same HTML as `.docx` only if a user asks for Word specifically — PDF covers
printing and archiving; a Word export needs a new dependency (docx4j / Apache POI). Recommend PDF
first.

Markdown (TM03) must render in the export too — convert server-side with the same rules as the
UI (commonmark-java, escaping raw HTML), or export as plain text with line breaks preserved.

---

## 5. Requirement structure, import and sync (TM18, TM19) — L

**Problem.** A requirement is id, title and description: no hierarchy, status, priority or
custom fields, and there is no import or tracker sync. SN has 179 requirements entered by hand.

**Design, in three steps that each ship on their own:**
1. **Fields (S/M).** Add `status` (DRAFT/APPROVED/OBSOLETE), `priority` (reuse `Priority`) and
   custom fields — `CustomFieldEntityType` gains `REQUIREMENT`; `CustomFieldValueWriter` is
   already entity-generic. List filters (TM17) extend to the new fields.
2. **Hierarchy (M).** `parent_id` on requirements (Epic → Story → Requirement); the list becomes
   a tree like the test-case folders. Coverage rolls up: a parent is covered when every leaf is.
3. **Import & sync (M/L).** CSV import (externalId, title, description, parent externalId,
   status, priority) through the PRD-004 import pipeline with dry-run. Tracker sync: pull issues
   from the configured issue tracker by a saved query (JQL for Jira, WIQL for Azure DevOps, label
   for GitLab/GitHub/Forgejo) and upsert by `externalId`, storing the issue URL. One direction
   only — the tracker owns requirement text; local edits to synced fields are refused.

**Decision (2026-10-28):** Azure DevOps is the main tracker; sync is built for Azure DevOps only,
with the settings and the `source` column kept provider-neutral for later.

**Status (2026-10-28):** steps 1–2 and the CSV import of step 3 are implemented (V91, V92).
Tracker sync from Azure DevOps is implemented too (V93, V94): per-project settings (WIQL query,
ID prefix, state lists for APPROVED/OBSOLETE; priority 1–4 maps to CRITICAL…LOW) on the project's
Azure DevOps tracker connection; "Sync now" with a dry-run preview, MCP `sync_requirements`
(ADMIN) and an opt-in scheduled sync (`app.requirementsync.cron`, off by default). Work items are
read 200 per `workitemsbatch` call and upserted by externalId; the HTML description is stored as
plain text; the parent comes from the hierarchy link when that parent is synced too. A work item
leaving the query is marked OBSOLETE, not deleted. Synced fields are refused in REST, MCP and CSV
import; custom fields and test case links stay editable. As built for steps 1–2: deleting a requirement moves
its children up to its own parent; an OBSOLETE requirement does not count towards coverage (and
does not hold its parent back); the coverage summary counts leaves only, a parent's status being
derived from them. The CSV import upserts by `externalId`; a present `description` or
`parentExternalId` column is authoritative (blank clears), blank `status`/`priority`/`cf:` cells
keep the current value.

---

## 6. Per-case assignment in runs (TM23) — M

**Problem.** A run has one executor; cases cannot be split between testers.

**Design.** `assignee_id` on `test_results` (nullable, falls back to the run's executor). Run view:
assignee column, bulk-assign selected cases, filter "assigned to me". My Queue (`MyQueueService`)
lists results assigned to the user across runs. MCP `assign_test_run_cases`. Notifications reuse
the watcher mechanism (PRD-006).

---

## 7. Configurable result statuses (TM24) — L, high risk

**Problem.** Statuses are fixed (Pending, Passed, Failed, Blocked, Skipped). Teams want e.g.
"In progress" or "Not applicable".

**Why it is risky.** `TestResultStatus` is referenced 88 times in the backend: pass rate, release
gates, flaky detection, burn-down, dashboards, CI ingestion, webhooks and reports all rely on its
meaning.

**Design.** Keep the enum as the *category* every metric uses, and let projects define statuses on
top: `project_result_statuses(id, project_id, name, color, category, position, archived)` where
`category` ∈ the existing enum values. Results store both `status` (category, unchanged column)
and `custom_status_id` (nullable). The UI shows the custom name; every metric keeps reading the
category, so nothing downstream changes. "In progress" maps to PENDING, "Not applicable" to
SKIPPED. Seed each project with one custom status per category so the UI has a single code path.

**Acceptance.** A project defines "Not applicable" (SKIPPED); results and steps can take it; pass
rate and gates are unchanged versus recording SKIPPED.

---

## 8. Test plan scope (TM28) — M

**Problem.** A plan's scope is only the runs attached to it; cases not yet in any run are invisible.

**Design.** `test_plan_cases(test_plan_id, test_case_id)`. Plan detail gets a "Scope" tab: each
planned case with its latest result across the plan's runs, "not executed yet" highlighted, and a
"Create run from unexecuted cases" action. Release-readiness coverage (`ReleaseReadinessService`)
can then measure against the plan's scope instead of all active cases — make that a per-plan
choice so existing gates do not move. MCP: `add_cases_to_plan`, `remove_cases_from_plan`,
`get_test_plan` returns the scope summary.

---

## 9. MCP for exploratory sessions and project configuration (TM36, TM37) — M

**TM36.** Tools over `ExploratorySessionService`: `list/get/create/update/complete` sessions and
`add_session_note` (text notes; images via the TM13 base64 upload pattern). Group: EXECUTION.

**TM37.** Pipeline workflows, webhooks and the issue-tracker connection are configuration that
holds credentials and outbound URLs. Recommend: read-only MCP tools (`list_webhooks`,
`get_issue_tracker_config` with secrets redacted) and **no** create/update/delete via MCP. An
agent that can point a webhook at an arbitrary URL can exfiltrate every event the project emits;
the SSRF guards limit hosts, not intent. If write access is still wanted, put it in a new
`CONFIGURATION` tool group that is never part of the default (NULL = all groups) grant, which
needs a change to how NULL is interpreted (PRD-027 §9).

---

## Suggested order

1 (step documents) and 8 (plan scope) have the most direct effect on day-to-day testing in SN;
3 (saved filters) is cheapest. 7 (configurable statuses) last — it is the only one that can
destabilise metrics.
