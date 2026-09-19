# PRD-044 — Test Case Attachments

| | |
|---|---|
| **Status** | ✅ Implemented 2026-09-19 — see §8 |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-17 |
| **Priority** | P2 — common gap for manual testing |
| **Target** | v2.4 |
| **Related** | PRD-017 (authenticated media caching), PRD-018 (stored-XSS lessons), PRD-011 (versioning), PRD-004 (import/export), PRD-001 (RBAC), PRD-027 §3.5 (child-id scoping), PRD-009 §4 (no S3) |

---

## 1. Summary

Testers regularly need a file to *perform* a test: a sample invoice to upload, a CSV to import, a
spec PDF, a certificate, an expected-output image. Today the only places a file can live are:

- `step_images`: **one image per step** (`StepImage`, `@OneToOne` to `TestStep`), images only, a
  reference picture of the expected state.
- `screenshots`: one image per **step result** (`Screenshot`), which is evidence from execution, not
  input to it.

So test input files end up on a shared drive, with a path pasted into `preconditions`, and they drift
out of sync with the case. This PRD adds **multiple, typed file attachments on a test case**, stored
the way the existing media already is (`BYTEA` in Postgres), served with the hardened headers
already on `StepImageController` / `ScreenshotController`, and visible to the tester while executing
a run.

## 2. Goals & Non-Goals

**Goals**
- Attach up to N files (default 20) to a test case. Upload, list, download, delete.
- A strict allowlist of content types, checked against the file's **magic bytes**, not just the
  client's declared type.
- Downloads can't execute script in the app origin (PRD-018): `nosniff`, `CSP: sandbox`,
  `Content-Disposition: attachment` for everything except allowlisted raster images.
- `Cache-Control: private` so no shared cache serves one user's authorized bytes to another
  (PRD-017).
- Attachments are visible (read-only) in the test run execution view for the result's case.
- Attachment changes are auditable. Export carries metadata, not bytes.

**Non-Goals**
- **Step-level attachments beyond today's step image.** `StepImage` already covers the step's
  reference picture. A second per-step file mechanism would double the UI for rare use. Put the file
  on the case and reference it in the step text.
- External storage (S3/MinIO, filesystem volume). PRD-009 §4 rules it out. `BYTEA` with limits is
  enough at this tool's scale (§6 covers growth).
- In-browser preview of PDFs or office documents, and virus scanning. Operators who need scanning
  put it at the reverse proxy.
- Attachments on requirements or plans. Same mechanism, separate decision, only on demand. (Bug
  reports got them in PRD-051, on the same table.)
- Versioned attachment bytes (§3.5).
- Uploading attachments over MCP or the external API. Base64 blobs through tool calls are a poor fit
  and nobody has asked.

## 3. Proposed Design

### 3.1 Data model (migration: next free V-number, V54+ at time of writing)

```sql
CREATE TABLE test_case_attachments (
    id            UUID PRIMARY KEY,
    test_case_id  UUID NOT NULL REFERENCES test_cases(id) ON DELETE CASCADE,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    sha256        VARCHAR(64)  NOT NULL,
    data          BYTEA        NOT NULL,
    created_at    TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
    created_by    VARCHAR(255), updated_by VARCHAR(255)
);
CREATE INDEX idx_test_case_attachments_case ON test_case_attachments(test_case_id);
```

The column shape mirrors `step_images` / `screenshots` (V12, V21), plus `size_bytes`, `sha256` and
the `created_by` / `updated_by` columns `BaseEntity` requires. On H2, `BYTEA` maps as it already does
for the existing media tables.

**Keeping list queries cheap.** The existing media entities load `data` with the row, which is fine
for a single image fetched by id. Listing a case's 20 attachments that way would pull every byte
into memory. The list goes through a JPQL constructor projection
(`AttachmentSummary(id, fileName, contentType, sizeBytes, sha256, createdAt, createdBy)`), and only
the download endpoint loads the entity. No bytecode enhancement or lazy-`@Basic` tricks are needed.

### 3.2 Type allowlist and content checks

A new `AttachmentMediaTypes` next to `ImageMediaTypes`, following the same shape (static allowlist,
`normalize`, `requireAllowed`), plus a magic-byte check:

| Type | Declared media type | Signature check | Served as |
|---|---|---|---|
| PNG / JPEG / GIF / WebP | via `ImageMediaTypes` | PNG `89 50 4E 47`, JPEG `FF D8 FF`, GIF `GIF8`, WebP `RIFF....WEBP` | `inline` |
| PDF | `application/pdf` | `%PDF-` | `attachment` |
| ZIP, DOCX, XLSX, PPTX | `application/zip`, the three OOXML types | `PK\x03\x04` | `attachment` |
| Plain text, CSV, JSON, XML, logs | `text/plain`, `text/csv`, `application/json`, `application/xml` | no NUL bytes in the first 8 KB | `attachment`, served as `text/plain; charset=utf-8` |

- **Rejected outright:** `image/svg+xml`, `text/html`, `application/xhtml+xml`, JavaScript, and
  anything not in the table, including a declared type whose magic bytes don't match (a PNG header
  on a file declared as PDF gets 400).
- **Filename:** stripped to its base name, control characters removed, max 255 characters, sent
  with `ContentDisposition...filename(name, UTF_8)` as the existing controllers do. The extension
  isn't trusted for anything.
- XML is served as `text/plain`, so a browser never parses it as a document even if headers are
  stripped by a proxy.

The magic-byte check is about 30 lines. Apache Tika was considered and rejected: a large dependency
tree to classify under ten types.

### 3.3 Limits

- **Per file:** the existing `spring.servlet.multipart.max-file-size: 10MB` / `max-request-size:
  10MB`. There's no separate setting; one limit for all uploads is simpler to explain.
- **Per case:** `app.attachments.max-per-case` (default 20). Upload beyond it gets 409.
- **Per project total:** `app.attachments.max-project-bytes` (default 500 MB, `0` = unlimited),
  checked with `SUM(size_bytes)`. It exists so one enthusiastic import can't quietly double the size
  of every `pg_dump`.
- Uploads are one file per request, matching `StepImageController.upload`.

### 3.4 Endpoints (RBAC via PRD-001)

Nested under the project, so `@RequireProjectRole` applies directly. The existing flat media URLs
(`/api/step-images/{id}`) rely on service-level `requireRoleForCurrentUser`, and new endpoints
shouldn't copy that.

| Method | Path | Role |
|---|---|---|
| `GET` | `/api/projects/{projectId}/test-cases/{testCaseId}/attachments` | `@RequireProjectRole` (VIEWER) |
| `POST` (multipart `file`) | `/api/projects/{projectId}/test-cases/{testCaseId}/attachments` | `@RequireProjectRole(ProjectRole.TESTER)` |
| `GET` | `/api/projects/{projectId}/test-cases/{testCaseId}/attachments/{id}` | VIEWER |
| `DELETE` | `/api/projects/{projectId}/test-cases/{testCaseId}/attachments/{id}` | TESTER |

- **Scoping (PRD-027 §3.5):** every lookup is `findByIdAndTestCaseId` with the case resolved through
  `projectId`, and a mismatch at either level gets 404. A test proves an attachment id from project
  B can't be fetched through a project A URL.
- **Download** copies `StepImageController.download` headers exactly: an ETag from `updatedAt`
  (attachments are immutable, so the sha256 works too and is stronger), `If-None-Match` → 304,
  `CacheControl.maxAge(365d).cachePrivate().immutable()`, `X-Content-Type-Options: nosniff`,
  `Content-Security-Policy: sandbox`, exact `Content-Length`, and the disposition from §3.2. The
  shared header-building code gets extracted into one package-private helper used by all three
  controllers. That's the one refactor this PRD allows, and it lands as its own commit before the
  feature, with existing tests green.
- **Frontend fetch:** images render through the existing `AuthImagePipe`
  (`shared/pipes/auth-image.pipe.ts`), which fetches with the JWT into a blob URL. Other files
  download through `HttpClient` as a blob in the same way. There are no token-in-query URLs.

### 3.5 Versioning interplay (PRD-011)

`TestCaseVersionService` snapshots text fields and steps (`StepSnapshot`). Attachments stay **out
of the snapshot and don't bump `currentVersion`**:

- A snapshot that references bytes which may since have been deleted would show history the system
  can't reproduce. Keeping bytes forever to avoid that turns every replace into permanent storage
  growth.
- Instead, add, remove and replace each write an audit entry (`AuditAction.CREATED` / `DELETED` on
  a new `AuditEntityType.TEST_CASE_ATTACHMENT`, with `entityName` = file name and the sha256 in the
  detail). The activity log answers "which file was attached when".
- The version history UI (`features/test-cases/test-case-versions`) shows a one-line note that
  attachment changes appear in the activity log, so nobody assumes they're versioned.

If a compliance user needs byte-level history, the upgrade path is to make attachments append-only
(soft delete) and store attachment ids in the snapshot. That's deliberately not done now.

### 3.6 Import / export (PRD-004)

- **JSON export** (`TestCaseImportExportService`): each case gains
  `attachments: [{ fileName, contentType, sizeBytes, sha256 }]`, as metadata only, so an export
  stays a readable, diffable text file.
- **CSV export:** unchanged. There's no sensible column for a list of files.
- **Import:** `attachments` is ignored if present, and the dry-run report lists "N attachments not
  imported" per case so nobody believes they came across.
- **PDF suite and run reports:** list attachment file names under each case, with no bytes.

### 3.7 Frontend

- **Test case detail** (`features/test-cases/test-case-detail`): an "Attachments" card with a native
  `<input type="file">` plus drop zone, a list (icon by type, name, size, uploader, date), download,
  and delete for TESTER+ with a confirm. The upload shows progress through `HttpClient`
  `reportProgress`, and validation errors from the server (type, size, quota) appear inline.
- **Test run execution** (`features/test-runs/test-run-detail`): read-only attachment list for the
  current result's case, collapsed by default and shown only when the case has attachments. This is
  the main reason the feature exists.
- **Test case list:** a paperclip indicator with a count. The count comes from a single grouped
  count query added to the list response, not an N+1.
- Image attachments render as thumbnails through `AuthImagePipe`, the same way step images do.
  Everything else downloads.
- i18n keys in `en.json` / `de.json`.

### 3.8 MCP impact

Read-only and minimal: `get_test_case` (`TestCaseTools`) includes attachment **metadata**, so an
agent executing a run knows a file exists and can tell the human to fetch it. There's no upload or
download tool (see Non-Goals).

## 4. Edge Cases

- **Declared type mismatch** (e.g. `.pdf` that is really HTML): magic bytes fail, so 400 with the
  allowed list. SVG is rejected even though it is an image.
- **Browsers declaring `application/octet-stream`** for CSV/JSON: the type is resolved from the
  extension only when the declared type is `application/octet-stream`, then the §3.2 check applies.
  An extension can never upgrade a file into an `inline` image.
- **Duplicate upload** (same case, same sha256): accepted, since people re-upload deliberately. The
  UI warns "identical file already attached".
- **Case deleted:** attachments cascade. **Case moved between folders:** unaffected.
- **Test case clone** (if and when it exists) must copy attachment rows. Called out here so it isn't
  forgotten.
- **Quota reached mid-import:** not possible, since import doesn't carry attachments.
- **Legacy or unsafe stored type** (e.g. an allowlist tightened later): download falls back to
  `application/octet-stream` + `attachment`, exactly as `StepImageController` handles legacy rows.
- **Very large DB:** Postgres TOAST-compresses and stores `BYTEA` out of line, so row scans on
  `test_case_attachments` don't read bytes. The project quota bounds the total, and the backup
  guidance in `USER_MANUAL.md` mentions attachment volume.
- **Concurrent delete during download:** 404 on the next request, which is harmless.

## 5. Testing

- `AttachmentMediaTypesTest`:
  - Each allowed type accepts real signature bytes and rejects a mismatched declaration.
  - SVG, HTML and JS are rejected.
  - A text type with NUL bytes is rejected.
  - The octet-stream extension fallback can't produce an inline type.
- Service tests: per-case and per-project limits, filename sanitising, sha256 and size stored, and
  the list projection doesn't load `data` (assert the query shape with a Hibernate statistics
  check).
- Controller tests:
  - VIEWER can list and download but gets 403 on upload and delete, and a TESTER can upload and
    delete.
  - A non-member gets 403/404.
  - A cross-project attachment id through another project's URL gets 404.
  - A cross-case attachment id gets 404.
- Header tests on download:
  - `nosniff`, `CSP: sandbox` and `Cache-Control: private` are present, with no `public`.
  - A PDF is served as `attachment` and a PNG as `inline`.
  - A text file is served as `text/plain`, and `If-None-Match` gets 304.
- Audit: upload and delete write entries with file name and sha256.
- Export: JSON includes metadata without bytes. Import ignores attachments and the dry run reports
  them.
- Refactor commit: the existing `ScreenshotController` / `StepImageController` tests stay green
  after the header helper is extracted.
- Frontend (Vitest): the card uploads and lists, delete is hidden for VIEWER, the run execution panel
  shows the case's attachments read-only, and server validation errors render.

## 6. Effort & Risk

- **Effort:** ~5–6 days (header-helper refactor 0.5, schema + service + type checks 2, endpoints +
  audit + export 1, frontend incl. execution view 2, docs 0.5).
- **Risk:** Medium, security-shaped.
  - A file-serving endpoint on the app origin is exactly where PRD-018's stored XSS lived. The
    mitigations (allowlist, magic bytes, `attachment` disposition, `nosniff`, `CSP: sandbox`,
    `text/plain` for text types) are layered so any single one failing isn't enough to execute
    script.
  - Storage growth is the operational risk, bounded by per-case and per-project limits.
- **Driver note:** unlike most v2.x items this one is common. Manual-testing teams hit it early.

## 7. Acceptance Criteria

- [ ] TESTER+ can upload, and VIEWER+ can list and download, multiple attachments on a test case,
      within per-file, per-case and per-project limits.
- [ ] Only allowlisted types whose magic bytes match are accepted. SVG and HTML are always rejected.
- [ ] Downloads send `nosniff`, `CSP: sandbox`, `Cache-Control: private`, and `attachment`
      disposition for all non-image types.
- [ ] All lookups are scoped by project and case, and cross-project or cross-case ids get 404.
- [ ] List queries never load file bytes.
- [ ] Attachment add and remove are audited. Versions are unaffected, and the history view says so.
- [ ] JSON export includes attachment metadata only, and import ignores attachments with a dry-run
      note.
- [ ] Attachments are visible read-only while executing a test run.
- [ ] `get_test_case` over MCP includes attachment metadata.
- [ ] Download header logic is shared by screenshots, step images and attachments, with existing
      tests green.
- [ ] Tests above pass. `en.json` / `de.json` and `USER_MANUAL.md` updated.

## 8. As Built (2026-09-19)

Built as specified, with these differences:

- **Table `attachments`** (V65), not `test_case_attachments`: PRD-051 adds bug reports as a second
  owner of the same table. `created_by` / `updated_by` are `UUID`, like every other `BaseEntity`
  table. The audit type is `AuditEntityType.ATTACHMENT`.
- **Every non-image downloads as `application/octet-stream`**, not only unknown types: that is what
  the shared `ImageResponses` helper already does for anything outside the image allowlist. It is
  stricter than §3.2's `text/plain` for text types, and the browser saves under the stored file
  name either way.
- **The ETag comes from `updatedAt`**, like the other media endpoints. Attachments are never
  updated, so it changes exactly when the sha256 would.
- **The header-helper refactor** had already landed as `8ab341c` (`ImageResponses`, shared by
  screenshots, step images and session images), so the feature commit only reuses it.
- **The list's paperclip** reads `attachments` from the list response: one batched summary query
  per page instead of a separate count, and the same field serves the detail page, JSON export and
  MCP. Write responses (create, update, review) leave it `null`.
- **The history note** is a sentence in the version-history hint. The test case's own history card
  keys on the case id, so attachment entries show in the project activity, not there.
- **No Hibernate-statistics test** that listing skips `data`: the list is a JPQL constructor
  projection that never names the column, so there is no code path that could load it.
- **Limits** are also in `application.yml` as `APP_ATTACHMENTS_MAX_PER_CASE` /
  `APP_ATTACHMENTS_MAX_PROJECT_BYTES`. PRD-051 renamed the first to `app.attachments.max-per-owner`
  (`APP_ATTACHMENTS_MAX_PER_OWNER`); the old variable is still read.
