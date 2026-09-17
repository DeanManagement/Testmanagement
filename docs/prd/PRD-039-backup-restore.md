# PRD-039 — Backup & Restore

| | |
|---|---|
| **Status** | 📝 Draft |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-17 |
| **Priority** | P2 for Phase A (cheap, closes a real self-hosting gap) · P3 for Phase B (driver-dependent) |
| **Target** | v2.4 |
| **Related** | PRD-004 (test case import/export), PRD-011 (versioning), PRD-019 (deployment & secrets hardening), PRD-012 (SSO secrets), PRD-024 (build-server tokens), PRD-010 (issue-tracker tokens) |

---

## 1. Summary

The tool is sold as self-hosted for simple organisations, and those organisations' admins do not run
`pg_dump` from memory. The ask was an admin-UI backup and restore. Having looked at how data is actually
stored, this PRD is honest that **most of that ask is already solved and should not be rebuilt in Java**,
and splits the work into two phases of very different value:

- **Phase A — instance backup, done properly outside the app (S).** Everything, including screenshots,
  step images and Allure reports, is in PostgreSQL (`Screenshot.data`, `StepImage.data`,
  `AllureReport.data` are `BYTEA`; USER_MANUAL §"Backups" already says the app container is stateless).
  A `pg_dump` is therefore a complete, consistent, version-exact backup. What is missing is not a
  feature but a safe, scripted wrapper: `scripts/backup.sh` and `scripts/restore.sh`, with a refusal to
  restore over a non-empty database and a schema-version check.
- **Phase B — per-project archive export/import in the admin UI (L).** The one thing `pg_dump` cannot
  do: take *one* project out of an instance and put it into another (or back into the same one after
  deletion) without rolling back every other project. Worth building only when a team needs to migrate,
  split, merge or archive projects.

**Recommendation:** ship Phase A now. Hold Phase B until a concrete migration or archiving request
appears.

## 2. Goals & Non-Goals

**Goals**
- A one-command, documented, tested instance backup and restore for the docker-compose deployment.
- Restore that cannot silently destroy data (empty target only) and refuses a dump newer than the app.
- Clear guidance that `.env` (`APP_ENCRYPTION_KEY`, `JWT_SECRET`) is part of the backup.
- *(Phase B)* A self-contained project archive — cases, suites, plans, runs, results, media,
  requirements, bugs, comments, history — importable as a new project on any instance with a compatible
  archive format.

**Non-Goals**
- **Full-instance backup/restore from the admin UI.** Rejected, see §3.1.
- Scheduled backups, retention policies, off-site upload, or S3 (PRD-009 §4). `cron` + the script does
  the first two; the operator's existing backup tooling does the rest.
- Point-in-time recovery / WAL archiving. That is a PostgreSQL operations decision, not an app feature.
- Merging an archive *into* an existing project.
- Transferring secrets or integrations in a project archive (§3.4).
- Kubernetes/Helm variants of the scripts. Compose is the only supported deployment.

## 3. Proposed Design

### 3.1 Why not full-instance backup in the admin UI

Considered and rejected:

1. **No `pg_dump` in the app image.** The runtime `Dockerfile` stage is `eclipse-temurin:25-jre` with
   only `curl` added. Shipping PostgreSQL client binaries that must match the server's major version, or
   reimplementing a dump over JDBC `COPY`, is a lot of moving parts to replace one shell command.
2. **Restoring the database the app is running on, from inside the app,** means dropping the schema under
   live connection pools, Flyway, schedulers (`McpAuditRetentionScheduler`, pipeline polling, webhook
   retries) and signed-in sessions. Getting that right is harder than the feature is valuable.
3. **A full dump downloaded through a browser** carries password hashes, `sso_providers` encrypted
   client secrets, webhook signing secrets and API key hashes over HTTP to a laptop. The shell path keeps
   it on the host.
4. **An instance restore is all-or-nothing.** In the UI it would look like an undo button and behave like
   a data-loss button for every other project.

### 3.2 Phase A — `scripts/backup.sh` and `scripts/restore.sh`

Plain POSIX shell alongside `scripts/dev.sh`, operating on the compose service `testmanagement-db`.

**backup.sh**
```bash
./scripts/backup.sh [output-dir]      # default ./backups
```
- `docker compose exec -T testmanagement-db pg_dump -U testmanagement -Fc testmanagement` → 
  `testmanagement-<UTC timestamp>-V<schema>.dump`. Custom format (`-Fc`) so restore can use
  `pg_restore --exit-on-error --single-transaction`.
- `<schema>` is `SELECT max(version::int) FROM flyway_schema_history WHERE success`, embedded in the
  filename so the operator can see compatibility without opening the file.
- Prints a reminder that `.env` must be backed up separately and that losing `APP_ENCRYPTION_KEY` means
  re-entering tracker tokens, build-server tokens and OIDC secrets (already in USER_MANUAL).
- Exits non-zero on any failure; writes to a temp file and renames on success so a half-written dump is
  never mistaken for a good one.

**restore.sh**
```bash
./scripts/restore.sh <file.dump>
```
- **Refuses unless the target is empty:** `SELECT count(*) FROM information_schema.tables WHERE
  table_schema='public'` must be 0. The message tells the operator to recreate the `pgdata_tm1` volume
  (the exact `docker compose down -v` consequence is spelled out). No `--force`.
- **Refuses a dump newer than the checkout:** compares the schema version in the filename with the
  highest `V{n}` in `backend/src/main/resources/db/migration`, and after restore re-checks with the same
  `flyway_schema_history` query (failing loudly if a renamed file lied). An older dump is fine: Flyway
  migrates it forward on next start, which is the normal upgrade path.
- Requires the app container to be stopped (`docker compose ps testmanagement` not running), so nothing
  writes during restore and Flyway runs once afterwards.
- `pg_restore --single-transaction --exit-on-error --no-owner`, then prints "start the app with
  `docker compose up -d`".

**Docs.** USER_MANUAL §"Backups" becomes: backup, restore, upgrade-with-backup, the `.env` warning, and a
`cron` example. §"Upgrading" links to it.

No backend or frontend code changes in Phase A.

### 3.3 Phase B — project archive: format

A ZIP streamed from the server:

```
manifest.json
project.json             # project, folders, members (by email + role)
test-cases.json          # cases, steps, labels, parameter sets, versions, permissions (by email)
test-suites.json
test-plans.json          # incl. PRD-037 gate thresholds if shipped
test-runs.json           # runs, results, step results (no binary)
requirements.json        # requirements + case links
bug-reports.json
comments.json
audit.json
media/step-images/<id>.<ext>
media/screenshots/<id>.<ext>
media/allure/<runId>.zip
```

`manifest.json`:
```json
{ "archiveFormat": 1, "appVersion": "2.4.0", "schemaVersion": 54,
  "exportedAt": "…", "projectKey": "PROJ", "counts": { "testCases": 812, "testRuns": 140, "mediaBytes": 48230112 } }
```

**Version compatibility is decoupled from Flyway.** The archive is written from explicit export records,
not table dumps, so a schema migration only matters if it changes what is exported — in which case
`archiveFormat` is bumped and the importer keeps a reader for each older format it still supports.
`schemaVersion` and `appVersion` are informational. Import rejects `archiveFormat` newer than it knows,
with a message naming the app version needed.

JSON records reuse existing DTO shapes where they fit (`TestCaseImportExportService.exportJson` already
serialises full cases with steps for PRD-004) and use internal ids only as archive-local references.

### 3.4 Phase B — what is deliberately excluded

- **Secrets and integrations:** `issue_tracker_configs` (encrypted token), `webhooks` (signing secret),
  `project_build_workflows` / `pipeline_runs` (point at instance-level `build_server_configs`),
  `api_keys` and their service users. These are re-entered on the target. This removes the whole
  "archive encrypted with source `APP_ENCRYPTION_KEY`, target has a different key" problem instead of
  solving it. `issue_links` are kept as plain URL + external id records so the history still links out.
- **Per-user state:** `notifications`, `notification_preferences`, `entity_watchers`.
- **Instance-level data:** users beyond the email/display name references, `sso_providers`,
  `auth_settings`, `mcp_tool_invocations`.

### 3.5 Phase B — import semantics

- **Always a new project.** The import dialog asks for the target project key, defaulting to the
  archive's. An existing key → 409 before anything is written. Case keys (`PROJ-17`) and run keys
  (`PROJ-Run-7`, globally unique on `test_runs.test_run_key`) are rewritten to the new prefix;
  `ProjectSequenceService` counters are set to the imported maxima.
- **All UUIDs regenerated** and references remapped, so importing a copy into the same instance works.
- **Users matched by email.** Matched users get their archived membership role and case permissions
  back. Unmatched authors (`comments.author_id` is `NOT NULL` with an FK to `users`) are created as
  password-less, non-admin, non-service users with the archived display name and **no project
  membership** — they cannot sign in locally, and access is granted only if an admin adds them. The
  dry run lists both groups before commit.
- **Timestamps and authorship preserved.** Import writes through `JdbcTemplate` batch inserts inside one
  transaction, not JPA repositories, because `AuditingEntityListener` would overwrite `createdAt` /
  `createdBy`, and entity services would fire audit entries, version snapshots, webhooks and
  notifications for history that already happened. One `AuditEntry` ("Project imported from archive")
  is written at the end.
- **Dry run** (`?dryRun=true`) validates the whole archive and returns counts, user matching and
  warnings without writing, matching the PRD-004 import flow.
- **Size.** Media makes archives large. Export streams (`StreamingResponseBody` + `ZipOutputStream`),
  never buffering the ZIP in memory. Import needs more than the global 10 MB `spring.servlet.multipart`
  cap: the import endpoint reads a raw `application/zip` request body via `InputStream` with its own
  limit `app.backup.max-archive-size` (default 2 GB), leaving the global multipart limit untouched.
  The upload is spooled to a temp file, then processed.

### 3.6 Phase B — endpoints (RBAC via PRD-001)

| Method & path | Role |
|---|---|
| `GET /api/projects/{projectId}/archive` | `@RequireProjectRole(ProjectRole.ADMIN)` |
| `POST /api/admin/project-archives?targetKey=…&dryRun=…` (body `application/zip`) | System admin (`ROLE_ADMIN`, as `BuildServerAdminController`) |

Export is project-admin because it contains nothing the project admin cannot already read. Import is
instance-admin because it creates a project and users.

### 3.7 Phase B — frontend

- Project settings: "Download project archive" with a note on what is not included (§3.4).
- Admin settings (`features/settings`): "Import project" — file picker, target key, dry-run preview
  (counts, matched users, users to be created, warnings), then confirm. Reuses the layout of
  `import-test-cases-dialog`.
- i18n in `en.json` / `de.json`.

### 3.8 MCP impact

None. Archive transfer is an operator action, not an agent action.

## 4. Edge Cases

**Phase A**
- Restore target not empty → refuse, no partial write.
- Dump schema newer than checkout → refuse with "upgrade the app first".
- App container running during restore → refuse.
- Disk full during backup → non-zero exit; temp file removed; no truncated `.dump` left with a final name.
- Restored onto a host with a different `APP_ENCRYPTION_KEY` → app starts; tracker/build-server/OIDC
  secrets fail to decrypt at use. Documented; the existing error at point of use already names the key.
- Different `DB_PASSWORD` on the target → irrelevant to `pg_restore --no-owner`; the known volume-init
  caveat from PRD-019 is linked.

**Phase B**
- Archive with a newer `archiveFormat` → 422 naming the required version.
- Corrupt ZIP / missing referenced media → dry run reports it; import aborts the whole transaction.
- Key conflict → 409 before writing.
- Deleted test step referenced by a step result (`step_results.test_step_id` nullable since V28) → kept
  as null.
- Case versions referencing an old step layout → versions store steps as a JSON snapshot (`test_case_versions.steps_snapshot`, PRD-011) and import verbatim.
- Parameterized results keep `parameter_set_name` / `parameter_values_json` verbatim even if the set no
  longer exists.
- Archive larger than `app.backup.max-archive-size` → 413 before spooling completes.

## 5. Testing

**Phase A**
- A CI job (or a documented manual check, if CI has no Docker-in-Docker) that: starts the DB, runs the app
  once to migrate, seeds data, runs `backup.sh`, recreates the volume, runs `restore.sh`, starts the app,
  and checks a known test case and screenshot are served.
- `restore.sh` refuses a non-empty database and a too-new dump (shell test with a stub `docker`).

**Phase B**
- Round trip: export a fixture project with every excluded and included table populated, import into a
  fresh project, compare record counts, keys (rewritten prefix), timestamps and `createdBy`, and media
  bytes (hash).
- Import into the same instance produces a second independent project (no id or run-key collision).
- User matching: matched member regains role; unmatched comment author becomes a password-less user with
  no membership.
- Nothing from §3.4 appears in the archive (assert on file list and JSON keys, including no `secret`,
  `token`, `keyHash` fields anywhere).
- No webhooks, notifications, version snapshots or per-entity audit entries are produced by import.
- RBAC: project TESTER cannot export; project ADMIN can; only system admin can import.
- Newer `archiveFormat` rejected; dry run writes nothing.
- Large archive streams without exceeding a bounded heap (test with a generated ~200 MB Allure blob).

## 6. Effort & Risk

- **Phase A effort:** ~1–1.5 days (two scripts, docs, round-trip check). **Risk:** very low.
- **Phase B effort:** ~2–3 weeks. About twenty tables, id remapping, key rewriting, user matching, streaming,
  plus the round-trip suite. **Risk:** medium–high and ongoing — every future migration that adds a
  project-scoped table must also update the archive, or archives silently lose data. Mitigation: a test
  that lists all tables with a `project_id` column (or FK chain to `projects`) and fails when one is
  neither exported nor on the §3.4 exclusion list.
- **Driver-dependence:** Phase B should wait for a real need (migrating between instances, splitting an
  instance, archiving closed projects). Until then, Phase A plus the PRD-004 test case export covers the
  common cases.

## 7. Acceptance Criteria

**Phase A**
- [ ] `scripts/backup.sh` produces a custom-format dump named with timestamp and schema version, atomically.
- [ ] `scripts/restore.sh` refuses non-empty targets, dumps newer than the checkout, and a running app container.
- [ ] A backup → wipe → restore → start cycle serves previously stored cases and screenshots.
- [ ] USER_MANUAL documents backup, restore, cron scheduling and the `.env` requirement.

**Phase B (when driven)**
- [ ] Project admins can download a streamed project archive with a versioned manifest.
- [ ] System admins can dry-run and import an archive as a new project with a chosen key.
- [ ] Ids are regenerated, keys rewritten, timestamps and authorship preserved, users matched by email.
- [ ] Secrets, integrations, API keys and per-user state are never included.
- [ ] Import emits no webhooks, notifications, snapshots or per-entity audit noise.
- [ ] A guard test fails when a new project-scoped table is neither exported nor explicitly excluded.
- [ ] Round-trip, RBAC, format-version and streaming tests pass.
