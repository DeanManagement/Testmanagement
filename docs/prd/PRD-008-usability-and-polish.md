# PRD-008 — Usability & Polish Bundle

| | |
|---|---|
| **Status** | Implemented (2026-06-09) |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-09 |
| **Priority** | P2/P3 — Usability & tech debt |
| **Target** | v1.3 / opportunistic |
| **Related** | REVIEW §3.5, §3.7, §3.11, §3.12, §3.13 |

---

## 1. Summary

A bundle of small, independent improvements from the May 2026 review that don't each justify a standalone PRD but together meaningfully sharpen daily use and reduce tech debt. Each item below can ship on its own; they share this document for tracking. Effort sizes follow the review (`S` ≤ 2 days).

---

## 2. Items

### 2.1 (M) Bulk actions on test results within a run — REVIEW §3.7

**Problem:** Bulk select works on the test case list, but inside a run you can't multi-select results to set "all remaining to Skipped" or "these 10 to Blocked" — painful when a run is cut short by an environment outage.

**Proposal:** Add a checkbox column / multi-select in the run-detail results sidebar and a floating action bar ("Set status", with optional cascade to steps). Backend: a `POST /api/projects/{id}/test-runs/{trId}/results/bulk-status` endpoint (`{ resultIds, status }`), gated `TESTER` (PRD-001), max 100 IDs, all belonging to the run. Audit each change.

**Acceptance:** select N results, set status once, all update; viewer blocked; over-limit rejected.

---

### 2.2 (S) Persist list filters in the URL — REVIEW §3.4

**Problem:** List filters are component state; back-navigation drops them and they aren't shareable.

**Proposal:** Bind filters to `ActivatedRoute.queryParams` (replaceState) and persist last-used per project in `localStorage`. (Pairs with PRD-002; can ship independently for the client-side filtering that exists today.)

**Acceptance:** drilling into a detail and pressing Back preserves filters; a filtered URL is shareable.

---

### 2.3 (S) Test case list: density toggle + virtual scrolling — REVIEW §3.5

**Problem:** `displayedColumns` is hard-coded; ~12 rows fit on a 1080p screen; `MatTable` isn't virtualized, so large projects render every row in the DOM.

**Proposal:** Add a compact-density toggle and pair the table with `cdk-virtual-scroll-viewport`. Optional: a column-visibility menu. With PRD-002 pagination this is about in-page smoothness rather than payload size.

**Acceptance:** a 1,500-row project scrolls smoothly with bounded DOM nodes; density toggle persists.

---

### 2.4 (S) Translate remaining hardcoded chart labels — REVIEW §3.11

**Problem:** Some Chart.js datasets still use literal English status strings (e.g., `TestPlanDetailComponent`), despite ~95% i18n coverage. (v1.3 already translated the main run/queue chart labels; this is the cleanup tail.)

**Proposal:** Replace literals with `translate.instant('testResult.status.PASSED')` etc. when building datasets; rebuild on language change. Audit all `new Chart(...)` / dataset label sites.

**Acceptance:** switching EN/DE relabels every chart; no literal status strings remain in chart code.

---

### 2.5 (S) Responsive execution screen for tablets — REVIEW §3.12

**Problem:** The two-pane run-detail layout collapses awkwardly below ~1024px; testers on tablets during physical-device testing are a real QA case.

**Proposal:** Make run-detail responsive — stack the result list and detail panes under a breakpoint, keep keyboard/touch targets usable. Reuse the shell's existing `BreakpointObserver`.

**Acceptance:** run execution is usable on a ~768px tablet viewport; no overlap/clipping.

---

### 2.6 (S) Stream large blob downloads — REVIEW §3.13 (tech debt)

**Problem:** `ScreenshotController.download()` and `StepImageController` return `ResponseEntity<byte[]>`, loading the whole blob into JVM heap. At the 10 MB cap × 5 concurrent users that's ~50 MB resident.

**Proposal:** Switch to `StreamingResponseBody` reading from the JDBC `Blob` input stream. Keep the existing `Cache-Control: max-age=365d, immutable` headers (already correct). Apply when next touching this code — low urgency.

**Acceptance:** downloads stream without buffering the full blob; cache headers unchanged; access control (PRD-001 §4.4) preserved.

---

## 3. Status (as shipped 2026-06-09)

- **2.1 Bulk result status — done.** `POST /api/projects/{id}/test-runs/{runId}/results/bulk-status` (`{resultIds, status, cascadeSteps}`), `TESTER`-gated (PRD-001 aspect), max 100, all-in-run validation, audited. Run-detail execution sidebar gains a select mode (checkboxes + a "Set status" menu and an "also set steps" toggle). Backend tests: bulk update, over-limit 400, results-not-in-run 400.
- **2.2 URL-persisted filters — already delivered** by PRD-002 (test case/run/suite list filters live in query params).
- **2.3 Density toggle — done** (persisted in `localStorage`, compact row styling). Virtual scrolling was de-scoped: PRD-002 pagination (default 50/page, max 200) already bounds DOM node count, so a `cdk-virtual-scroll` rewrite of the MatTable adds risk without meaningful benefit now.
- **2.4 Chart label i18n — done.** All four chart components (run/suite reports, project dashboard, test-plan detail) already built labels via `translate.instant`; the remaining gap was relabeling on language switch, now fixed by re-rendering on `TranslateService.onLangChange`.
- **2.5 Responsive execution screen — done.** Run-detail two-pane layout stacks under 1024px (sidebar full-width, scrollable) via a SCSS breakpoint.
- **2.6 Stream blob downloads — done.** Screenshot/step-image downloads return `StreamingResponseBody`, preserving the `Cache-Control`/ETag/304 behavior and PRD-001 access control. (The bytes still come from the `BYTEA` column as a `byte[]`; a deeper JDBC-`Blob`-streaming change was left out to avoid the `bytea`↔LOB schema risk — the HTTP layer no longer builds a full `ResponseEntity<byte[]>` copy.)

## 4. Out of Scope
Rich-text editors, configurable workflows, and alternate screenshot stores are explicitly **not** pursued (REVIEW §6).
