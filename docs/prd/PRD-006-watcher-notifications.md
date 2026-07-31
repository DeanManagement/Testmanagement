# PRD-006 — Watcher Notifications (In-App + Optional Email)

| | |
|---|---|
| **Status** | Implemented (2026-06-09) |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-09 |
| **Priority** | P2 — Collaboration |
| **Target** | v1.3+ |
| **Related** | REQUIREMENTS.md §3.15, §13.6; REVIEW §4.10 |

---

## 1. Summary

The watcher infrastructure already ships: `entity_watchers` stores `(userId, entityType, entityId)` subscriptions and the `/api/watchers` endpoints let users watch/unwatch — but **nothing is ever delivered**. This PRD cashes in that investment by fanning out audit events to watchers via an in-app notification bell, with optional email (off by default for air-gap).

## 2. Current State (verified)
- `EntityWatcher` entity + `entity_watchers` table exist.
- `WatchableEntityType` currently covers `TEST_PLAN`, `TEST_RUN`, `BUG_REPORT` (note: narrower than REQUIREMENTS.md §3.15, which also lists `TEST_CASE`).
- `AuditService.log(projectId, userId, action, entityType, entityId, entityName, details)` is called after every mutation — a single, reliable fan-out point.
- No `notifications` table, no dispatcher, no bell.

## 3. Goals & Non-Goals

**Goals**
- A `NotificationDispatcher` that reacts to audit-entry creation and notifies watchers of the affected entity.
- In-app bell: unread count + dropdown of recent notifications, each linking to the entity.
- Per-user, per-event-type opt-out.
- Optional SMTP email, disabled by default.

**Non-Goals**
- Real-time push/WebSockets (poll on an interval is fine at 5 concurrent users).
- Digest emails / batching (later).
- Expanding watchable types (could add `TEST_CASE` later; not required here).

## 4. Proposed Design

### 4.1 Data model (migration V34)
`notifications`: `id`, `user_id` (indexed), `project_id`, `entity_type`, `entity_id`, `action`, `summary`, `read_at` (nullable), `created_at`. Index `(user_id, created_at DESC)` and a partial/secondary path for unread.

Optional `notification_preferences`: `user_id`, `action`/`event`, `in_app` (bool), `email` (bool). Absent row = defaults (in-app on, email off).

### 4.2 Dispatch
- After an `AuditEntry` is persisted, the dispatcher looks up watchers for `(entityType, entityId)` and inserts a `Notification` per watcher (excluding the actor themselves).
- Map `AuditAction` (`CREATED/UPDATED/DELETED/STATUS_CHANGED/COMPLETED/REOPENED/CLONED/MOVED`) + entity type to a human summary, using i18n keys resolved on the frontend (store a key + params, not a baked string).
- Trigger off the existing audit insert — **no double-writes**, no new instrumentation in each service.
- Email (if enabled and the user opted in) sent via `@Async` through Spring `JavaMailSender`. Air-gap default: `app.mail.enabled=false`.

### 4.3 Endpoints
- `GET /api/me/notifications?unread=&page=` — paginated, newest first.
- `POST /api/me/notifications/{id}/read` and `POST /api/me/notifications/read-all`.
- `GET/PUT /api/me/notification-preferences`.

### 4.4 Frontend
- Top-nav bell with unread badge; dropdown lists last ~20 with relative time and a link; "mark all read".
- Poll unread count on an interval (e.g., 60s) while the app is focused.
- Preferences panel in user settings (per-event in-app/email toggles).

## 5. Edge Cases
- Actor doesn't get notified of their own action.
- Deleted entity: notification still readable but the link resolves to a "no longer available" state.
- Duplicate suppression: one notification per (user, entity, action, ~time window).
- Watcher with all event types opted out → no notification.

## 6. Testing
- Dispatcher unit tests: watchers found/none; actor excluded; preference opt-out respected.
- Summary mapping for each action/type.
- Endpoint tests: unread filter, mark-read, pagination.
- Email path behind a mocked `JavaMailSender`; disabled by default verified.

## 7. Effort & Risk
- **Effort:** ~1 week in-app; email +1–2 days (REVIEW `M`).
- **Risk:** Low–medium. Hooking the audit insert keeps it simple; main care is not flooding users (dedup + actor-exclude).

## 8. Acceptance Criteria
- [x] Creating an audit event on a watched entity produces in-app notifications for its watchers (not the actor).
- [x] Bell shows unread count and links to entities; mark-read / mark-all-read works.
- [x] Per-user, per-action opt-out honored.
- [x] Email is off by default and only sends when enabled + opted in.
- [x] Dispatcher + endpoint + preference tests pass (156 backend tests green; frontend builds clean).

## 9. Implementation Notes (as shipped)

- **Trigger:** `AuditService.log` calls `NotificationDispatcher` after persisting each audit entry (single choke point, in a try/catch so a notification failure never breaks the audited action). Notifications are created in the same transaction as the action (roll back together).
- **Dispatch:** maps `AuditEntityType` → `WatchableEntityType` (only TEST_PLAN/TEST_RUN/BUG_REPORT are watchable), loads watchers, excludes the actor, honors per-action `NotificationPreference` (default in-app on / email off), and de-duplicates by `(user, entity, action)` within a 5s window. Notifications store `action` + `entityType` + `entityName` + `actorName` so the frontend composes an i18n summary (no baked strings).
- **Endpoints** (`/api/me`, JWT, self-scoped): `GET /notifications?unread=&page=`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all`, `GET/PUT /notification-preferences`.
- **Email** (`NotificationEmailService`): `@Async`, guarded by `app.mail.enabled` (default false) and an optional `JavaMailSender` (`ObjectProvider`) — a no-op in air-gapped setups.
- **Migrations:** V36 `notifications`, V37 `notification_preferences`.
- **Frontend:** top-nav bell (unread badge, dropdown of last 20 with relative time + entity links, mark-all-read, 60s visibility-aware poll) and a per-user notification-settings page (in-app/email toggles per action), linked from the profile menu.
- **Note:** `WatchableEntityType` still excludes `TEST_CASE` (unchanged from before); adding it later is a small follow-up.
