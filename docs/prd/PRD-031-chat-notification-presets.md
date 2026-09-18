# PRD-031 — Chat Notification Presets (Slack, Microsoft Teams, Mattermost)

| | |
|---|---|
| **Status** | ✅ Implemented 2026-09-18 — see §8; manual screenshots from real Slack/Teams/Mattermost still open |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-17 |
| **Priority** | P2 — cheapest high-visibility win |
| **Target** | v2.4 |
| **Related** | PRD-003 (webhooks), PRD-005 (CI ingestion), PRD-006 (notifications), PRD-024 (`public-base-url`), PRD-001 (RBAC) |

---

## 1. Summary

PRD-003 shipped signed, retried outbound webhooks with a generic JSON envelope
(`WebhookDispatchService.buildPayload`: `event`, `projectKey`, `timestamp`, `data`). Chat tools
won't accept that body. A Slack incoming webhook wants `text`/`attachments`, and a Teams Workflows
webhook wants an Adaptive Card. Today a team that wants "nightly regression finished: 182/190 passed"
in their channel has to run a relay service, which small organisations won't do.

This PRD adds a **format** to each webhook: `GENERIC` (today's behaviour), `SLACK` (also Mattermost,
Rocket.Chat and Discord's `/slack` endpoint), or `TEAMS`. Formatting happens at dispatch.
Delivery, retry (`WebhookRetryScheduler`), the delivery log and the SSRF guard are reused unchanged.

## 2. Goals & Non-Goals

**Goals**
- Choose a chat format when creating a webhook; the message renders natively in the channel.
- Readable messages for run completed/failed/started and bug report created, with a link back to
  the app when a public URL is configured.
- CI-ingested runs (PRD-005), the main nightly use case, notify too. **They currently publish no
  run events at all**; only `TestRunService` status transitions do.
- The chat webhook URL, which *is* the credential, is not shown back in full once saved.
- The "Send test" button produces a real chat message.

**Non-Goals**
- Slack/Teams **apps** (OAuth, bot tokens, interactive buttons, slash commands). Incoming webhooks only.
- Per-user chat DMs. In-app and email notifications (PRD-006) cover individuals.
- Message templating by admins. The formats are fixed and code-owned.
- Localised chat messages. Server-side English only, as with webhook payloads today.
- Encrypting webhook URLs at rest. They sit next to the HMAC `secret`, which is already stored
  plaintext. Worth doing for both together if the threat model changes; see §4.
- Fixing `WebhookEventType.PLAN_COMPLETED`, which is declared but never published. That's a separate
  bug; presets won't offer it until it fires.

## 3. Proposed Design

### 3.1 Data model (next free V-number, V54+ at time of writing)

```sql
ALTER TABLE webhooks ADD COLUMN format VARCHAR(20) NOT NULL DEFAULT 'GENERIC';
```

- `WebhookFormat` enum: `GENERIC`, `SLACK`, `TEAMS`. Mattermost and Rocket.Chat accept Slack's
  incoming-webhook body (`text` plus legacy `attachments`), so a separate enum value would duplicate a
  formatter for a label. The UI names the option "Slack / Mattermost / Rocket.Chat".
- `Webhook` gains `format`. Existing rows default to `GENERIC`, so there's no behaviour change.
- `secret` stays `NOT NULL`. For chat formats the server generates it, since the request no longer
  needs to supply one, and still signs. Chat services ignore the `X-TM-*` headers.

### 3.2 Backend

**Payload building.** `WebhookDispatchService.dispatch` currently builds one body before looping over
hooks. It changes to build lazily **per format** (at most three bodies per event) and passes each
hook the body for its format. The body is still stored on the delivery row, so
`WebhookRetryScheduler` resends exactly what was first attempted.

- `buildPayload` moves into a small `WebhookPayloadBuilder` with a `switch` over `WebhookFormat`:
  - `GENERIC` → today's envelope, byte-for-byte (existing tests pin it).
  - `SLACK` → `SlackMessages` (package-private static helpers):
    `{"text": "<fallback>", "attachments": [{"color": "#2e7d32|#c62828|#f9a825", "title": "...",
    "title_link": "<deep link>", "fields": [{"title":"Passed","value":"182/190","short":true}, ...]}]}`.
    Legacy attachments are chosen over Block Kit because they're the one shape Slack, Mattermost and
    Rocket.Chat all render.
  - `TEAMS` → `TeamsMessages`:
    `{"type":"message","attachments":[{"contentType":"application/vnd.microsoft.card.adaptive",
    "content":{"type":"AdaptiveCard","version":"1.4","body":[TextBlock, FactSet],
    "actions":[{"type":"Action.OpenUrl","title":"Open run","url":"..."}]}}]}`. This is the Teams
    **Workflows** "When a Teams webhook request is received" shape. The legacy Office 365 connector
    `MessageCard` is not supported because Microsoft has retired those connectors.
- User-controlled text (run names, bug titles) is escaped for each target: `&`, `<`, `>` for Slack
  mrkdwn, and Adaptive Card TextBlocks are sent with markdown-significant characters escaped. A run
  named `<!channel>` must not ping a whole channel.

**Message content** (from the existing `data` maps in `TestRunService.publishRunEvent` and
`BugReportService`):

| Event | Headline | Fields |
|---|---|---|
| `RUN_COMPLETED` | `✅ PROJ-R12 · Nightly regression completed` (❌ if `failed > 0`) | Environment, Passed x/y (rate %), Failed, Blocked, Skipped, first 5 failed tests |
| `RUN_FAILED` | `❌ PROJ-R12 · Nightly regression failed` | same |
| `RUN_STARTED` | `▶️ PROJ-R12 · Nightly regression started` | Environment, Total |
| `BUG_REPORT_CREATED` | `🐞 New bug: <title>` | Priority, Status |

Additive changes to the event `data` (which also benefit `GENERIC` consumers): `environment` and
`failedTests` (up to 10 `{key, title}`) on run events, and `testCaseKey` on `TEST_FAILED`. All fields are added, none removed, so it isn't a breaking change for existing
receivers.

**Deep links.** `app.buildserver.public-base-url` (PRD-024, `PUBLIC_BASE_URL`) becomes the general
`app.public-base-url`, bound from the same env var, with the old property kept as a fallback alias.
Links are `{base}/projects/{projectId}/test-runs/{runId}` and `.../bug-reports/{id}`. If no base URL
is set, messages have no link or button rather than a broken one.

**CI ingestion.** `CiIngestionService.ingest` and `ExternalTestRunService` publish `RUN_COMPLETED`
(or `RUN_FAILED` when any result failed, mirroring `TestRunService`) after saving. Only the run-level
event is published, never one `TEST_FAILED` per ingested result (see noise, below).

**Noise guard.** `TEST_FAILED` fires once per failed result. A 200-failure CI upload would post 200
chat messages and hit Slack's ~1 message/second limit (429s filling the retry queue). Chat-format
webhooks therefore **cannot subscribe to `TEST_FAILED`** (400 on save). The run summary lists the
first failed tests instead. `GENERIC` hooks are unchanged.

**Validation.**
- The existing `WebhookUrlValidator` (→ `OutboundUrlValidator`) SSRF check applies on save and on
  every send, as today.
- Chat formats additionally require `https` unless `app.webhooks.allow-private-targets` is on (the
  self-hosted Mattermost-on-LAN case), because the URL carries the token.
- No host allowlist (`hooks.slack.com`, `*.logic.azure.com`, ...). It would break self-hosted
  Mattermost and Teams' changing Power Platform hosts for no SSRF gain over the address checks.

**Test delivery.** `WebhookDispatchService.sendTest` renders a format-specific "Test message from
Testmanagement for project PROJ" instead of `{"test": true}`.

### 3.3 Endpoints (RBAC via PRD-001)

Unchanged paths, all `@RequireProjectRole(ProjectRole.ADMIN)` on `WebhookController` as today:

- `POST /api/projects/{projectId}/webhooks`. `CreateWebhookRequest` gains `format` (default
  `GENERIC`). `secret` becomes optional when `format != GENERIC` and is generated if absent.
- `PUT /api/projects/{projectId}/webhooks/{webhookId}`. `UpdateWebhookRequest` gains `format`. Changing
  the format of an existing hook is allowed and re-validates events.
- `GET` list/detail: `WebhookResponse` gains `format`. For chat formats `url` is **masked** to
  scheme + host + `/…` + last 4 characters. Updating without sending a new `url` keeps the stored one.
- `POST .../webhooks/{webhookId}/test`: unchanged.

### 3.4 Frontend

`features/webhooks/webhook-settings.component`:
- A format selector (Generic / Slack · Mattermost · Rocket.Chat / Microsoft Teams) shown first, with
  a one-line hint and a link to the vendor's "create an incoming webhook" docs.
- For chat formats: hide the secret field, disable `TEST_FAILED` with a tooltip explaining why, and
  pre-tick `RUN_COMPLETED`, `RUN_FAILED` and `BUG_REPORT_CREATED`.
- Show the masked URL with a "Replace URL" action instead of an editable prefilled field.
- A format badge in the webhook list. i18n keys in `en.json` / `de.json`.

### 3.5 MCP impact

None. Webhook management is not exposed through MCP.

### 3.6 Docs

`docs/USER_MANUAL.md` webhooks section: per-tool setup (Slack app incoming webhook, Teams Workflows
template, Mattermost incoming webhook, Discord `…/slack` URL), and the `PUBLIC_BASE_URL` requirement
for links.

## 4. Edge Cases

- **No `PUBLIC_BASE_URL`** → messages without links. The settings page shows a hint that links are
  disabled.
- **Teams Workflows returns 202 Accepted** → 2xx, counted as success.
- **Slack returns 200 with body `invalid_payload` for some errors, and 4xx `no_service` for a deleted
  webhook** → the 4xx is recorded as a failure in the delivery log, as today. Stopping retries on a
  permanent 404/410 would be a nice extra but isn't required.
- **Rate limiting (429)** → retried by the existing backoff (1, 5, 30 minutes). The `TEST_FAILED`
  exclusion keeps volume to a few messages per run.
- **Run name or bug title containing mention syntax** (`<!here>`, `@channel`) → escaped, never
  rendered as a mention.
- **Very long names / many failed tests** → titles truncated to 150 characters, failed-test list
  capped at 5 with "+N more", keeping well within Slack's 40k and Teams' ~28KB card limits.
- **Format changed from GENERIC to SLACK on a hook subscribed to `TEST_FAILED`** → 400 until that
  event is removed. No silent unsubscribe.
- **URL masking** → admins can no longer copy a chat URL back out of the UI. That's intended, because
  the vendor can re-issue it. Generic webhook URLs stay unmasked, as receivers often need them shown.
- **At-rest exposure** → a database dump reveals chat URLs, as it already reveals webhook secrets and
  delivery bodies. It's documented. Encrypting both with the existing `AesGcmCipher` (as
  `IssueTrackerTokenCipher` does) is a follow-up if needed.
- **CI ingestion now emits run events** → existing `GENERIC` receivers subscribed to `RUN_COMPLETED`
  start receiving CI runs. Called out in release notes as a behaviour change.

## 5. Testing

- `WebhookPayloadBuilder`: the `GENERIC` body is unchanged (snapshot test against today's output);
  `SLACK` and `TEAMS` bodies for each event are valid JSON with the expected keys and colour by
  outcome; links present only with a base URL; mention and markup escaping; truncation and "+N more".
- `WebhookDispatchService`: one event with GENERIC + SLACK + TEAMS hooks produces three
  distinct stored bodies; retry resends the stored body.
- Validation: chat format + `TEST_FAILED` → 400; chat format over http without allow-private → 400;
  secret generated when omitted; SSRF checks still applied.
- `WebhookResponse` masks chat URLs; update without a URL keeps the stored one.
- `CiIngestionService`: ingestion publishes exactly one run-level event, `RUN_FAILED` when any result
  failed, and no `TEST_FAILED`.
- Frontend: format selector toggles the secret field, disables `TEST_FAILED`, shows the masked URL.
- Manual: one message each into a real Slack, Teams (Workflows) and Mattermost channel, with
  screenshots in the PR.

## 6. Effort & Risk

- **Effort:** ~4–5 days. Builder and two formatters ~2, dispatch/validation/masking ~1, CI ingestion
  events ~0.5, frontend and docs ~1.
- **Risk:** Low. It's additive on a proven delivery pipeline, and the default format keeps existing
  hooks identical. The two things to watch are the new CI run events reaching existing generic
  receivers (documented) and Microsoft changing the Teams webhook product again (formatter isolated
  in one class).

## 7. Acceptance Criteria

- [x] Webhooks have a `format` (`GENERIC` default, `SLACK`, `TEAMS`); existing webhooks are unaffected.
- [x] Slack/Mattermost and Teams channels render run and bug messages natively, with deep links when `PUBLIC_BASE_URL` is set.
- [x] User-supplied text cannot trigger channel mentions or break markup.
- [x] Chat-format webhooks cannot subscribe to `TEST_FAILED`; run summaries list the first failed tests.
- [x] CI-ingested runs publish a run-level completed/failed event.
- [x] Chat webhook URLs are masked in API responses and the settings UI.
- [x] "Send test" posts a real message in the chosen format.
- [ ] Backend and frontend tests pass (**done**); manual screenshots recorded for Slack, Teams and Mattermost (**open**, needs real workspaces).

## 8. As Built (2026-09-18)

Built as specified, with these differences:

- **CI ingestion sends `RUN_COMPLETED` and then `RUN_FAILED`**, the same pair `TestRunService` sends,
  not one or the other as §3.2 described. Both paths now go through one `RunEventPublisher`. So that a
  chat channel doesn't get two posts for one failed run, dispatch skips `RUN_FAILED` for a chat hook
  that is also subscribed to `RUN_COMPLETED`, whose message already shows ❌ and the failed tests.
  A chat hook subscribed only to `RUN_FAILED` still gets it, which is how you get "failures only".
- **`app.buildserver.public-base-url` was not renamed.** A general `app.public-base-url` was added,
  bound to the same `PUBLIC_BASE_URL`, and `WebhookPayloadBuilder` falls back to the build-server
  property when it's blank. Nothing that already worked changes.
- **The "links disabled" hint is static.** No endpoint exposes whether `PUBLIC_BASE_URL` is set, so
  the chat format hints say links need it instead of detecting it.
- **Mentions:** Slack control characters become entities (`<!channel>` can't ping), and a zero-width
  space follows every `@` so Mattermost's plain `@channel`/`@all` can't either. Teams text is
  backslash-escaped for its markdown subset. Plain text can't mention anyone in Teams anyway, because
  mentions need `<at>` plus an entities block.
- **`UpdateWebhookRequest.url` became optional** (blank keeps the stored URL). Masking needs this: the
  UI's active toggle used to echo `url` back, which would have saved the masked value.
- **Formatting is split into `ChatMessage`**, a vendor-neutral record with the headline, fields,
  failed tests and link, plus `SlackMessages` and `TeamsMessages`, which render and escape it. This
  replaces a switch with per-event branches in each formatter.
- **Bug headlines include the project key** (`🐞 New bug in PROJ: <title>`), since a channel often
  serves several projects.

Tests: `WebhookPayloadBuilderTest` (12), `RunEventPublisherTest` (3), `WebhookServiceTest` (7), four new
cases in `WebhookDeliveryIntegrationTest`, three in `WebhookUrlValidatorTest`, one in
`CiIngestionApiTest`, and `webhook-form.spec.ts`. **827 backend tests, 22 frontend spec files.**

**Still open:** the manual screenshots. The Teams markdown escaping in particular is written against
the documented subset and hasn't been checked in a real client.
