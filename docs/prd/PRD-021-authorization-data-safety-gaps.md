# PRD-021 — Authorization & Data-Safety Gap Closure

| | |
|---|---|
| **Status** | Implemented (2026-06-10) — see §9 |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-10 |
| **Priority** | P0 (watcher leak) / P1 (API key scoping, CSV injection) |
| **Target** | v1.4 |
| **Related** | CODE_REVIEW_2026-06-09.md §H2, §H3, §H7; PRD-001 (RBAC), PRD-004 (import/export), PRD-006 (notifications) |

---

## 1. Summary

Three residual gaps from the v1.3 security work, bundled because each is small:

1. **Watcher info leak (H2):** `WatcherService.watch()` saves `(userId, entityType, entityId)` without checking the user can access the entity. Any authenticated user can watch arbitrary UUIDs and then receive notification payloads (entity names, status changes, actor names) from projects they are not a member of.
2. **Global API keys (H3):** `ApiKey` has no project association — any valid key can ingest CI results into *any* project's external endpoints.
3. **CSV formula injection (H7):** `TestCaseImportExportService` writes raw cell values; a test case titled `=HYPERLINK(...)` or `=cmd|...` executes when the exported CSV is opened in Excel/LibreOffice.

## 2. Current State (verified)

- `WatcherService.java:22-31`: direct `entityWatcherRepository.save(...)`, no `ProjectAccessService` call (contrast: `ScreenshotService` resolves the entity → project → `requireRoleForCurrentUser`).
- `entity/ApiKey.java`: no `project` field; `ApiKeyAuthenticationFilter` authenticates the key without scoping.
- No `=`/`+`/`-`/`@` prefix escaping anywhere in the export path.

## 3. Goals & Non-Goals

**Goals**
- Watching requires VIEWER access to the entity's project; notifications never reach non-members.
- API keys are scoped to a project; the external API rejects cross-project use.
- Exports are inert when opened in spreadsheet software.

**Non-Goals**
- Per-key fine-grained permissions (read vs ingest) — keep keys "ingest for project X"; revisit if needed.
- Sanitizing on *import* (importing a formula-looking title is legitimate data; the hazard is only on export rendering).

## 4. Proposed Design

### 4.1 Watcher access check
- `WatcherService.watch()`: resolve `(entityType, entityId)` → owning `projectId` (test plans/runs/bug reports all have a direct project FK; one small switch or a `WatchableResolver`), then `projectAccessService.requireRoleForCurrentUser(projectId, VIEWER)` before saving. Unknown entity id → 404.
- Defense in depth in `NotificationDispatcher`: when fanning out, skip watchers who are no longer project members (covers membership revoked *after* watching — currently watchers keep receiving updates forever). Add a cleanup: removing a project member deletes their watchers for that project's entities (or rely solely on the dispatch-time filter; pick dispatch-time as primary since it is always correct).

### 4.2 Project-scoped API keys
- Migration: `api_keys.project_id UUID NULL REFERENCES projects(id) ON DELETE CASCADE` + index. `NULL` = legacy/global key (still works, logged with a deprecation warning at startup listing global keys).
- `CreateApiKeyRequest` gains required `projectId`; creation requires ADMIN on that project (or system admin). `ApiKeyResponse` exposes the project.
- `ApiKeyAuthenticationFilter` stores the key's project in the authentication; external controllers (`ExternalTestRunController`, CI ingestion) compare it to the path's project and return 403 on mismatch.
- UI: project selector in the create-key dialog; key list shows project; docs updated.

### 4.3 CSV export escaping
- Single utility (`CsvSafe.escape`): if a cell starts with `=`, `+`, `-`, `@`, `\t`, or `\r`, prefix with `'`. Apply to every string cell in CSV export. JSON export untouched.
- Import: strip a single leading `'` *only* when round-tripping our own export? **No** — keep import verbatim and document the apostrophe (round-trip asymmetry is the standard, safe trade-off; OWASP-recommended behavior).

## 5. Edge Cases

- Existing watchers created before the fix who lack access: dispatch-time membership filter silently stops their notifications; a one-off cleanup migration deletes orphaned/un-entitled watcher rows (optional, nice for hygiene).
- Legacy global keys: continue to work for a deprecation window (one release), then `NULL` project keys are rejected — announce in release notes.
- Negative numbers in CSV (`-5`): numeric cells are written from typed fields, not user strings — escaping applies to free-text columns only (title, description, preconditions, steps, labels).

## 6. Testing

- Watch endpoint: member can watch; non-member gets 403; unknown id 404. Dispatcher: watcher removed from project no longer receives notifications.
- API keys: scoped key works on its project, 403 elsewhere; global legacy key warns; ADMIN-only creation enforced.
- Export: title `=HYPERLINK("http://x","y")` exports as `'=HYPERLINK(...)`; round-trip import/export documented behavior covered by test.

## 7. Effort & Risk

- **Effort:** S+S+S ≈ 1.5 days total (watcher ½ d, API keys ¾ d incl. UI, CSV ¼ d).
- **Risk:** Low. API key scoping is the only behavior change for existing integrations (mitigated by the legacy-NULL window).

## 8. Acceptance Criteria

- [x] Non-members cannot watch an entity (403) and never receive its notifications, even with pre-existing watcher rows — `WatcherService.watch` resolves entity → project and requires VIEWER (`requireRole`, 404 on unknown id); `NotificationDispatcher` additionally skips watchers without current membership (system admins exempt). Tests: `WatcherAccessApiTest`, `NotificationDispatcherTest` (revoked-member + sysadmin cases).
- [x] New API keys are project-bound (`api_keys.project_id`, V39; `projectId` required on create) and enforced **centrally in `ApiKeyAuthenticationFilter`** by comparing the key's project key to the `/api/external/projects/{key}/…` path segment — covers every current and future external endpoint; mismatch → 403. Legacy NULL keys keep working, with a per-use filter warning plus a startup deprecation warning listing them (`LegacyApiKeyWarner`). UI: project selector in the create dialog, project column in the key list (legacy shows "All projects (legacy)"). Tests: filter scope tests, `scopedKey_cannotIngestIntoOtherProject` integration test.
- [x] Exported CSVs neutralize formula-leading cells (`csvSafe`: `= + - @ \t \r` → `'` prefix; import stays verbatim). Test: `exportCsv_neutralizesFormulaInjection`.

## 9. Implementation Notes (as shipped)

- Watcher check ships in `WatcherService.watch` (entity → project → `requireRole(VIEWER)`) with a dispatch-time membership filter in `NotificationDispatcher` as defense in depth (system admins exempt). No cleanup migration — the dispatch-time filter makes stale watcher rows harmless.
- API-key scope is enforced in the filter (not per-controller) so new external endpoints are covered automatically. `ApiKeyService.validateKey` now returns a `ValidatedKey(id, name, projectKey)` record with the scope resolved inside the service transaction (avoids lazy-loading in the filter). `ApiKeyCreatedResponse`/`ApiKeyResponse` carry project info. Key management stays system-admin-only (the settings area), which subsumes the "ADMIN on that project" requirement.
- Deviation: per-key creation by project admins (rather than system admins) was not implemented — the settings UI is system-admin scoped today; revisit if project admins need self-service keys.
- Suite: 205 backend tests green; frontend builds clean.
