# PRD-051 — Bug Report Attachments and Image Viewer

| | |
|---|---|
| **Status** | ✅ Implemented 2026-09-19 — see §8 |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-19 |
| **Priority** | P2 — a UI defect without a screenshot has to be reproduced from prose |
| **Target** | v2.5 |
| **Related** | PRD-044 (test case attachments, draft — shares its storage), PRD-017 (private media caching), PRD-018 (stored-XSS lessons), PRD-034 (session note images), PRD-025/027 (MCP), PRD-001 (RBAC) |

**Source** (change requests filed as bugs on the test instance, 2026-09-17):

| Report | Asks for |
|---|---|
| `057aa497` | Screenshots and attachments on a bug report: in the Report Bug form and on the detail page, drag and drop, paste, thumbnails, copy the step screenshots when reporting from a failed result, REST and MCP |
| `1627cd62` | Enlarge step screenshots (lightbox) instead of a thumbnail only, with download |

---

## 1. Summary

A bug report is text only. `BugReport` has title, description, steps, expected/actual, environment,
priority and assignee (`entity/BugReport.java:31-82`), and `BugReportController` has no media
endpoint. The only images in the product are step reference images (`StepImage`), execution
screenshots (`Screenshot`, one per step result) and exploratory session note images, and each is
shown as a small thumbnail with no way to see it larger.

This PRD adds file attachments to bug reports and one shared image viewer for every image in the app.

### Key decision: one attachments table, shared with PRD-044

PRD-044 (draft) designs `test_case_attachments` and rules bug reports out "on demand". The demand
is here, and 044 is not built yet, so both owners share **one `attachments` table with one nullable
foreign key per owner** and a check that exactly one is set. This is the pattern V59 already uses
for `custom_field_values` (`ck_custom_field_values_one_owner`). One table means one allowlist, one
magic-byte check, one quota query and one download path.

**Build order:** PRD-044 and this PRD are built together, or 051 immediately after 044, on the same
table. The only change PRD-044 needs is its non-goal line *"Attachments on bug reports,
requirements or plans…"*, which becomes *"…on requirements or plans"*, plus its table name in §3.1.

## 2. Goals & Non-Goals

**Goals**
- Attach files to a bug report from the Report Bug form and the detail page: file picker, drag and
  drop, and paste from the clipboard.
- Reporting from a failed result copies that result's step screenshots onto the new bug.
- Image attachments show as thumbnails; everything else as a file row with download.
- One image viewer: click any thumbnail (step image, execution screenshot, session note image,
  attachment) to see it full size, with close and download.
- An MCP agent can attach evidence to a bug it reported.

**Non-Goals**
- Attachments on requirements, plans or runs.
- Annotating or cropping images in the browser.
- Zoom/pan beyond "fit to the viewport, scroll at 100 %": a native `<img>` in a scroll box.
- Screenshots in the run report screen and PDF. That is report content (the report-evidence PRD), and
  the viewer will work there once thumbnails exist.
- Upload through the external REST API (`/api/external/**`). The requester is an MCP agent; a CI
  pipeline has not asked.

## 3. Proposed Design

### 3.1 Data model (migration: next free V-number)

PRD-044 §3.1's table, renamed, with a second owner:

```sql
CREATE TABLE attachments (
    id             UUID PRIMARY KEY,
    test_case_id   UUID REFERENCES test_cases(id) ON DELETE CASCADE,
    bug_report_id  UUID REFERENCES bug_reports(id) ON DELETE CASCADE,
    file_name      VARCHAR(255) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    sha256         VARCHAR(64)  NOT NULL,
    data           BYTEA        NOT NULL,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
    created_by UUID, updated_by UUID,
    CONSTRAINT ck_attachments_one_owner CHECK (
        (test_case_id IS NOT NULL AND bug_report_id IS NULL) OR
        (test_case_id IS NULL AND bug_report_id IS NOT NULL))
);
CREATE INDEX idx_attachments_test_case ON attachments(test_case_id);
CREATE INDEX idx_attachments_bug_report ON attachments(bug_report_id);
```

`created_by` / `updated_by` are `UUID`, like the rest of the V5x tables. PRD-044 wrote `VARCHAR`,
which should be corrected there too. Lists use PRD-044's constructor projection, so no bytes are
loaded to list files.

### 3.2 Types, limits and headers

Unchanged from PRD-044 §3.2–3.3: the magic-byte allowlist (raster images inline; PDF, ZIP/OOXML,
text-like as downloads; SVG and HTML rejected), 10 MB per file, `max-per-owner` 20 (PRD-044's
`max-per-case` renamed), and one project-wide byte quota counted over both owners.

Downloads go through `controller/ImageResponses`, which is already the single copy of the
stored-media headers: `Cache-Control: private` immutable, ETag/304, `nosniff`, `CSP: sandbox`, fixed
length. It already serves allowlisted images `inline` and everything else as an
`application/octet-stream` attachment under the stored file name, which is exactly the rule
attachments need, so it is reused unchanged. PRD-044's planned "extract the header code" refactor is
already done (`8ab341c`). Both PRDs use one audit type, `ATTACHMENT`, not PRD-044's
`TEST_CASE_ATTACHMENT`.

### 3.3 Endpoints (RBAC via PRD-001)

| Method | Path | Role |
|---|---|---|
| `GET` | `/api/projects/{projectId}/bug-reports/{bugId}/attachments` | VIEWER |
| `POST` (multipart `file`) | same | TESTER |
| `GET` | `/api/projects/{projectId}/bug-reports/{bugId}/attachments/{id}` | VIEWER |
| `DELETE` | same | TESTER |

- These mirror PRD-044's test case endpoints. Every lookup is `findByIdAndBugReportId`, with the bug
  resolved through `projectId`, so an id from another project is a 404 (PRD-027 §3.5).
- Audit: `AuditEntityType.ATTACHMENT`, `CREATED`/`DELETED`, with the entity name being the file name
  and the sha256 in the details. It is logged against the bug, so it shows in the bug's history once
  that exists.
- A `BugReportResponse` gains `attachmentCount`, from one grouped count query for lists and not an
  N+1.

### 3.4 Copying screenshots when reporting from a result

`BugReportService.create` already accepts `testResultId` (form query parameter, `bug-report-form`
`:97-99`). When it is set, the service copies every `Screenshot` of that result's step results into
new bug attachments, bytes included, in the same transaction. The file name is
`step-<n>-<original name>`.

- **Copy, don't reference:** a screenshot is replaced when someone uploads a new one for the step.
  Evidence on a bug must not change after the bug is filed.
- Stops at the per-owner limit, with a warning in the response: "3 screenshots not copied (limit
  20)".
- The same happens for bugs created over MCP with a `testResultId`.

### 3.5 Frontend

- **Image viewer** (`shared/components/image-viewer-dialog`): a `MatDialog` showing the image through
  the existing `AuthImagePipe`, scaled to fit, with a "100 %" toggle, the file name, a download button
  (fetches the blob through `HttpClient` and saves it with the stored name), and close on Esc or
  backdrop. Every current thumbnail becomes a `<button>` with an "Enlarge image" label that opens
  it:
  - `step-spec-card` (case detail, `:19`)
  - `step-list-editor` previews (these are `data:` URLs before upload; the viewer handles both)
  - execution screenshots in `test-run-detail` (`:261`, `:444`)
  - session note images (`session-detail.component.html:123`)
  - attachment thumbnails
- **Attachment panel** (`shared/components/attachment-panel`): drop zone plus file input plus paste
  (a `paste` listener that takes `clipboardData.files`), a thumbnail grid for images, rows for other
  files, delete with confirm. It is used on the bug detail page (uploads immediately) and in the
  Report Bug form, where files queue and upload after the bug is saved (the pattern of
  `syncStepImages`). PRD-044 uses the same panel on the test case page.
- **Report from a result:** the form shows "Step screenshots will be attached (n)" when it has a
  `testResultId`, so the copy in §3.4 is not a surprise.
- i18n keys in `en.json` / `de.json`.

### 3.6 MCP impact

- `get_bug_report` includes attachment metadata (id, file name, type, size).
- **New tool `add_bug_report_attachment(bugReportId, fileName, contentType, contentBase64)`**, TESTER
  only, write-throttled. This departs from PRD-044's "no MCP upload". The requester of `057aa497`
  is an MCP agent that reproduces bugs with a browser and has screenshots in hand, and it cannot send
  multipart. The tool:
  - checks the encoded length before decoding
  - is capped at 2 MB decoded (screenshots are well under that; larger evidence goes through the UI)
  - runs the same magic-byte checks and quota as the REST upload
- No download tool: bytes back into a model's context are not useful.

### 3.7 Docs

USER_MANUAL gets "Attachments on bug reports" and "Enlarging images". MCP_SETUP gets the new tool.

## 4. Edge Cases

- **Paste of text, not an image:** ignored, since the panel only takes `clipboardData.files`.
- **Bug deleted:** attachments cascade. **Result or screenshot deleted later:** the copies stay.
- **A result whose steps have no screenshots:** nothing copied, no note shown.
- **The form is left with queued files:** the unsaved-changes guard counts queued files as unsaved.
- **An upload fails after the bug was created** (quota or type): the bug is kept, the panel shows the
  error on that file, and the rest upload.
- **Quota reached:** 409 naming the limit, same as PRD-044.
- **A legacy stored type outside the allowlist:** served as an octet-stream download, as today.
- **Viewer on a very large image:** fit mode scales it down; 100 % mode scrolls within the dialog.

## 5. Testing

- Attachment service: allowlist and magic bytes per type, per-owner limit, project quota across both
  owners, cross-project id → 404, audit entries, delete cascade with the bug.
- Copy on create: screenshots copied with bytes; a replaced screenshot leaves the bug's copy
  unchanged; limit warning.
- Controller: headers match `ImageResponses` (inline image, attachment PDF, `nosniff`, CSP, private
  cache, 304 on ETag); VIEWER reads, cannot upload.
- MCP: base64 accepted and stored; over 2 MB refused before decoding; bad magic bytes refused;
  VIEWER key refused; `get_bug_report` lists metadata.
- Frontend: panel queues in the form and uploads after save; paste adds a file; the viewer opens
  from each thumbnail and downloads with the stored name.

## 6. Effort & Risk

- **Effort:** ~4 days on top of PRD-044, or ~6 days if 044's table and service are built here first.
  Backend 2 (owner column, copy on create, MCP tool), frontend 3 (viewer, panel, wiring), docs and
  tests 1.
- **Risk:** low to medium. Storage growth is bounded by the quota. The security surface is PRD-044's,
  already reasoned; the new part is base64 over MCP, which is size-checked before decoding.

## 7. Acceptance Criteria

- [x] One `attachments` table serves test cases and bug reports, with a one-owner check.
- [x] Bug reports accept attachments by picker, drag and drop and paste, in the form and on the detail page.
- [x] Reporting from a failed result copies its step screenshots onto the bug.
- [x] Every image thumbnail opens a shared full-size viewer with download.
- [x] Downloads carry the `ImageResponses` headers; non-images download, never render inline.
- [x] `add_bug_report_attachment` works for TESTER keys within the 2 MB cap; `get_bug_report` lists attachments.
- [x] PRD-044's non-goal line and table name are updated to match.
- [x] Backend, MCP and frontend tests pass; en/de translations and manual sections present.

## 8. As Built (2026-09-19)

PRD-044 was already built, on a table named `attachments` from the start, so this PRD added the
second owner to it. Built as specified, with these differences:

- **Migration V71** makes `test_case_id` nullable, adds `bug_report_id` (cascading delete) with its
  index, and adds `ck_attachments_one_owner`. `AttachmentSummary` carries both owner ids, one of them
  null. The project quota query counts both owners.
- **`max-per-owner`** replaces `max-per-case` (`APP_ATTACHMENTS_MAX_PER_OWNER`); the PRD-044
  variable is still read as its default, so existing deployments keep their setting.
- **Bug attachment endpoints take the bug's id or key**, like every other bug endpoint, and refuse
  (403) while bug reports are off for the project. `BugReportAttachmentService` resolves the bug
  through `BugReportService`; the storage stays in `AttachmentService`.
- **No `attachmentCount` on `BugReportResponse`.** Nothing shows it yet: the bug list has no
  paperclip column, and the detail page loads the files themselves. Add it with the column.
- **No warning in the create response when screenshots are left out.** A new bug starts empty, so
  the per-bug limit only bites for a result with more than 20 step screenshots, or when the project
  quota runs out; either way the bug is saved, the copies stop, and the server logs how many were
  left out. A screenshot of a legacy type outside the allowlist is skipped, and the rest still copy.
- **The viewer opens from an `appEnlarge` directive** on each thumbnail `<img>` (role `button`,
  focusable, Enter/Space, "Enlarge image" label), instead of wrapping every thumbnail in a
  `<button>`. Besides the five places listed, the run report screen's screenshots open it too; the
  PDF is unchanged.
- **Paste listens on the whole page.** A pasted image attaches wherever the focus is; pasted text is
  left alone, and the panel ignores pastes in the run execution view and for viewers. The panel also
  takes several files at once (picker, drop or paste) and uploads them one after another; a refused
  file keeps its message while the rest upload.
- **Queued files upload in the create effect**, after the bug is saved and before the app navigates
  to it, so the detail page lists them. A refused file does not undo the bug: the snackbar names
  it. Edit mode uploads straight to the bug.
- **The form's note** ("The 3 step screenshots of this result will be attached") takes the count from
  the run page, which passes it along with the result.
- **MCP:** `McpDtos.Attachment` gained an `id`, so `get_test_case` returns it too. Line breaks in
  `contentBase64` are tolerated; any other character outside the alphabet is refused rather than
  skipped. The length is checked before decoding and the exact 2 MB after, since base64 groups
  round up. The MCP audit links a successful call to the attachment it created.
- **Not verified on PostgreSQL or in a browser.** V71 has run on H2 only.
