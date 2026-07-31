# PRD-003 — Outbound Webhooks

| | |
|---|---|
| **Status** | Implemented (2026-06-09) |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-09 |
| **Priority** | P1 — Integration |
| **Target** | v1.3 |
| **Related** | REQUIREMENTS.md §12.3 (documented, not built); REVIEW §4.4 |

---

## 1. Summary

Fire HTTP callbacks on key events so external systems (Slack, Teams, Discord, email relays, custom dashboards) can react without bespoke plugins. This is the lowest-cost integration feature — one mechanism unlocks many destinations. It was specified in the v1.2 plan but never implemented.

## 2. Goals & Non-Goals

**Goals**
- Per-project webhook configuration (project ADMIN only).
- HMAC-SHA256 signed, JSON payloads, asynchronous delivery.
- Delivery logging for debugging, with bounded retry.
- Air-gap safe: zero outbound calls unless a webhook is explicitly configured.

**Non-Goals**
- Tool-specific formatting (no Slack Block Kit templates) — generic JSON only.
- Inbound webhooks.
- Per-event payload customization / templating language.

## 3. Proposed Design

### 3.1 Data model (migrations V34, V35)

`webhooks`: `id`, `project_id` (FK, indexed), `url` (HTTPS), `secret`, `events` (element-collection of enum), `active` (bool), `created_at`, `updated_at`.

`webhook_deliveries`: `id`, `webhook_id` (FK), `event`, `request_body`, `response_status`, `attempt`, `success`, `error`, `created_at`. Indexed `(webhook_id, created_at DESC)`.

Events enum: `RUN_STARTED`, `RUN_COMPLETED`, `RUN_FAILED`, `TEST_FAILED`, `PLAN_COMPLETED`, `BUG_REPORT_CREATED`. (Adds `RUN_STARTED` + `BUG_REPORT_CREATED` to the documented list per REVIEW §4.4.)

### 3.2 Endpoints (project ADMIN — gated by PRD-001 `@RequireProjectRole(ADMIN)`)
- `GET/POST/PUT/DELETE /api/projects/{projectId}/webhooks`
- `POST /api/projects/{projectId}/webhooks/{whId}/test` — send a synthetic payload, return the delivery result.
- `GET /api/projects/{projectId}/webhooks/{whId}/deliveries?page=` — recent delivery log.

### 3.3 Delivery
- Payload: `{ event, projectId, projectKey, projectName, timestamp, data }`. `data` shape varies by event (e.g. `RUN_COMPLETED`: run key, name, pass rate, status counts).
- Signature: `X-TM-Signature: sha256=<hmac(secret, rawBody)>`; also send `X-TM-Event` and a unique `X-TM-Delivery` id.
- Hook into the existing `AuditService.log(...)` choke point (and run-completion / bug-create flows) to enqueue events — no double bookkeeping.
- `@Async` executor; **bounded retry** (3 attempts, ~1m/5m/30m backoff) — a single dropped notification often kills the integration's value (improves on the documented "no retry"). Persist attempt count on `webhook_deliveries`.
- Timeouts: 5s connect / 10s read. Only `2xx` counts as success.
- SSRF guardrail: require `https://`; optionally block RFC-1918/loopback targets behind a config flag (`app.webhooks.allow-private-targets=false` by default) since URLs are user-supplied.

### 3.4 Frontend
- Webhook management in project settings (project admins): list with active toggle, create/edit form (URL, event checkboxes, secret), and a "Test" button showing the response status.
- Delivery log viewer (last N, status + timestamp).

## 4. Edge Cases
- Inactive or zero webhooks → no work enqueued.
- Secret shown masked after creation (rotate via edit).
- Retry exhaustion → mark delivery failed; surface in the log, do not block the originating request.
- Air-gapped deployments simply configure none.

## 5. Testing
- Unit: HMAC signature correctness; payload builders per event; retry/backoff state machine (mock clock).
- Integration: `MockWebServer`/WireMock receives signed payload; 5xx triggers retry; test-send endpoint returns status.
- Authz: non-admin → 403 on management endpoints.

## 6. Effort & Risk
- **Effort:** ~1 week (REVIEW `M`).
- **Risk:** Medium — async + retry + SSRF need care. Keep delivery fully decoupled from request threads so a slow endpoint never affects users.

## 7. Acceptance Criteria
- [x] Project admins can CRUD webhooks and send a test payload (`@RequireProjectRole(ADMIN)` on all endpoints).
- [x] Configured events deliver signed JSON asynchronously with bounded retry.
- [x] Deliveries are logged and viewable (`GET .../deliveries`, paginated; UI delivery log).
- [x] No outbound traffic when no webhook is configured (events fan out only to active, subscribed hooks).
- [x] Non-HTTPS and, by default, private/loopback targets are rejected (`WebhookUrlValidator`).
- [x] Unit + integration + authz tests pass (129 backend tests green; frontend builds clean).

## 8. Implementation Notes (as shipped)

- **Decoupling:** domain flows publish a `WebhookEvent` via `ApplicationEventPublisher`; a `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("webhookExecutor")` listener dispatches, so a rolled-back operation never fires a webhook and a slow endpoint never blocks the request thread. Wired events: `RUN_STARTED` (PLANNED→IN_PROGRESS), `RUN_COMPLETED`/`RUN_FAILED` (on completion), `TEST_FAILED` (result set to FAILED), `BUG_REPORT_CREATED` (bug create). `PLAN_COMPLETED` is defined and selectable; its trigger can be wired when plan-completion semantics are finalized.
- **Delivery:** `java.net.http.HttpClient` (5s connect / 10s read), HMAC-SHA256 `X-TM-Signature: sha256=<hex>` plus `X-TM-Event`/`X-TM-Delivery`. Only `2xx` is success. State lives in `webhook_deliveries` (`success` null=pending/true=delivered/false=failed) so retries survive restarts; a `@Scheduled` poller re-attempts due deliveries with `1m/5m/30m` backoff up to `max-attempts`.
- **SSRF:** `WebhookUrlValidator` enforces https (toggle `app.webhooks.require-https`) and blocks loopback/private/link-local unless `app.webhooks.allow-private-targets=true`.
- **Migrations:** `V34` (webhooks + webhook_events), `V35` (webhook_deliveries). Secret is write-only (never serialized; update with a blank secret keeps the current value).
- **Frontend:** admin-only webhook settings page (`/projects/:id/webhooks`) with create/edit form, active toggle, test button, and an expandable delivery log; linked from project settings.
