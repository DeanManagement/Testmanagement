# PRD-043 — Scheduled Email Reports (Project & Test Plan Digests)

| | |
|---|---|
| **Status** | ⏸ Backlog — postponed 2026-09-19; kept as a draft, not planned for now |
| **Author** | Engineering (Claude) |
| **Created** | 2026-09-17 |
| **Priority** | P3 — driver-dependent (stakeholders who don't log in) |
| **Target** | v2.4 |
| **Related** | PRD-006 (watcher notifications, email), REQUIREMENTS.md §11.1 (PDF reports), §11.2 (dashboard), §12.2 (test plans), PRD-001 (RBAC), PRD-016 (flaky analytics) |

---

## 1. Summary

The people who most want test status (product owners, release managers, a team lead) are often the
people who never open the tool. Today they get status by asking a tester, or by someone downloading
a PDF from a run (`GET .../test-runs/{id}/report/pdf`) and forwarding it.

This PRD adds **per-user subscriptions** to a daily or weekly email digest for a **project** or a
**test plan**. The digest is a short summary (pass rate, what changed, open blockers, links) built
from data the dashboard and plan summary already compute, with an optional PDF attachment. It reuses
`NotificationEmailService` for sending and `PdfReportService` for rendering.

## 2. Goals & Non-Goals

**Goals**
- A project member can subscribe themselves to a project digest or a plan digest, daily or weekly
  (choosing the weekday), delivered at a fixed local hour in their timezone.
- The digest content comes from existing services: `DashboardService.getDashboard`,
  `TestPlanService.getSummary`, and `FlakyTestService` for a top-3 flaky line.
- Project membership is re-checked **at send time**. Someone removed from a project stops receiving
  its data without anyone remembering to unsubscribe them.
- One-click unsubscribe from the email, safe against link scanners.
- The whole feature is invisible when `app.mail.enabled=false` (the air-gap default).

**Non-Goals**
- Subscribing **other people**, or mailing lists and external addresses. Every recipient is an
  existing user who opted in, which keeps RBAC meaningful and avoids the tool becoming a spam relay.
- A report designer, custom sections, or arbitrary cron expressions.
- Slack/Teams delivery. Webhooks (PRD-003) exist, and chat presets are a separate PRD.
- Multi-instance scheduling infrastructure (ShedLock, Quartz). See §3.3.
- A digest for single test runs. Run completion already notifies watchers (PRD-006).

## 3. Proposed Design

### 3.1 Data model (migration: next free V-number, V54+ at time of writing)

New table `report_subscriptions` (entity `ReportSubscription extends BaseEntity`, so it needs
`created_by` / `updated_by`):

| Column | Type | Notes |
|---|---|---|
| `user_id` | `UUID NOT NULL` → `users` `ON DELETE CASCADE` | Recipient, always the subscriber |
| `project_id` | `UUID NOT NULL` → `projects` `ON DELETE CASCADE` | Always set, even for plan digests, so the RBAC check is a single lookup |
| `test_plan_id` | `UUID NULL` → `test_plans` `ON DELETE CASCADE` | Null means a project digest |
| `frequency` | `VARCHAR(10) NOT NULL` | `DAILY` / `WEEKLY` |
| `weekday` | `SMALLINT NULL` | 1–7 (ISO), required for WEEKLY |
| `timezone` | `VARCHAR(64) NOT NULL` | IANA zone taken from the browser at subscribe time |
| `include_pdf` | `BOOLEAN NOT NULL DEFAULT FALSE` | |
| `next_send_at` | `TIMESTAMP NOT NULL` | UTC. Doubles as the claim for sending (§3.3) |
| `last_sent_at` | `TIMESTAMP NULL` | Also the "changes since" boundary for the digest body |
| `unsubscribe_token` | `VARCHAR(64) NOT NULL UNIQUE` | 32 random bytes, base64url |

Constraints: unique `(user_id, project_id, test_plan_id)` (with a partial unique index for the
null-plan case on Postgres, and the equivalent in `db/specific/h2` if H2 rejects it), plus an index
on `next_send_at`.

The send hour is **not** a column. `app.reports.send-hour` (default `7`) applies to everyone. One
knob is enough until someone asks for more.

### 3.2 Content

Plain-text body, because `NotificationEmailService.send` uses `SimpleMailMessage` today and plain
text renders the same in every client:

- **Project digest:** overall pass rate and its change since `last_sent_at` (from
  `DashboardResponse.passRateTrend`), runs completed in the period with pass/fail counts
  (`recentTestRuns` filtered by `endTime > last_sent_at`), open CRITICAL/HIGH bug count
  (`BugReportRepository`), top 3 flaky cases (`FlakyTestService`, omitted when there is too little
  history), and a link to the project dashboard.
- **Plan digest:** everything in `TestPlanSummaryResponse` (status, target date, runs
  completed/total, passed/failed/blocked/skipped/pending, pass rate), days to `targetDate`, a
  per-run line for runs that changed in the period, and a link to the plan.
- Links use `app.buildserver.public-base-url` (`PUBLIC_BASE_URL`), which is already the instance's
  public URL. That property moves to a neutral `app.public-base-url`, with the old name kept as a
  fallback. If it is unset, the digest omits links rather than emitting relative URLs that mean
  nothing in a mail client.
- **Nothing happened in the period:** the digest is still sent, with a single line saying so. A
  missing email reads as "the job broke".

**PDF attachment (`include_pdf`).** For a plan digest, add `PdfReportService.generateTestPlanReport`,
reusing that class's existing `renderPdf` and `CSS` and the structure of `buildTestSuiteHtml`, with
`TestPlanSummaryResponse` as input. For a project digest, the attachment is the PDF of the most
recent completed run (`generateTestRunReport`), so no new layout is needed. Attachments need a MIME
message, so `NotificationEmailService` gains a `send(to, subject, body, attachmentName, bytes)`
overload using `MimeMessageHelper`. The existing text-only method stays as is.

### 3.3 Scheduling, and why there is no ShedLock

One `ReportDigestScheduler` in `project/internal/notification/`, in the style of
`WebhookRetryScheduler` and `McpAuditRetentionScheduler`:

```java
@Scheduled(fixedDelayString = "${app.reports.poll-ms:300000}")   // every 5 minutes
public void sendDue() { ... }
```

1. Return immediately if `!notificationEmailService.isEnabled()`, so an air-gapped instance costs
   nothing.
2. Load up to `app.reports.batch-size` (default 50) subscriptions with `next_send_at <= now`.
3. For each, **claim** it with a conditional update in its own transaction:
   `UPDATE report_subscriptions SET next_send_at = :next, last_sent_at = :now WHERE id = :id AND next_send_at = :expected`.
   Zero rows updated means someone else has it, so skip.
4. After the claim, re-check access, build the content, and send.

The deployment is a single instance (CLAUDE.md, PRD-020's in-memory throttle makes the same
assumption), so ShedLock would be a new dependency and a new table solving a problem that doesn't
exist. The conditional update costs one `WHERE` clause and still prevents double sends if someone
does run two replicas.

`next` is computed in the subscription's timezone: next occurrence of `send-hour` (DAILY), or of
`weekday` + `send-hour` (WEEKLY), strictly after `now`. That makes it DST-safe via `ZonedDateTime`
and **skips missed slots instead of replaying them**. An instance down over a weekend sends one
digest on restart, not three.

**Delivery semantics are at-most-once.** The claim advances `next_send_at` before sending. If SMTP
fails, that digest is lost and logged with a WARN (the existing `send` already catches and logs).
Retrying a digest is worth less than guaranteeing no duplicates. Summaries are not transactional
mail.

### 3.4 RBAC at send time

Scheduler threads have no security context, and `DashboardService` / `TestPlanService.getSummary`
rely on the controller-level `@RequireProjectRole` aspect, so they don't check access themselves.
The scheduler therefore checks explicitly before building anything:

- The user still exists and is not a service account. Otherwise delete the subscription.
- `ProjectAccessService.requireMember(userId, projectId)` (VIEWER is enough). On failure, **delete**
  the subscription and log INFO. Nothing is sent.
- A plan digest's plan still belongs to that project (cascade covers deletion; the explicit check
  covers a moved or misreferenced id, following the PRD-027 §3.5 lesson).

### 3.5 Endpoints (RBAC via PRD-001)

| Method | Path | Role | Notes |
|---|---|---|---|
| `GET` | `/api/my/report-subscriptions` | authenticated | Caller's own subscriptions, next to `MyNotificationController` |
| `POST` | `/api/projects/{projectId}/report-subscriptions` | `@RequireProjectRole` (VIEWER) | Body `{ testPlanId?, frequency, weekday?, timezone, includePdf }`. Subscribes the **caller** only. `testPlanId` is validated against `projectId`. Returns 409 on duplicate |
| `PUT` | `/api/my/report-subscriptions/{id}` | owner | Change frequency, weekday or PDF. Recomputes `next_send_at` |
| `DELETE` | `/api/my/report-subscriptions/{id}` | owner | Returns 404 (not 403) for someone else's id |
| `POST` | `/api/public/report-subscriptions/unsubscribe` | **permitAll** | Body `{ token }`. Deletes the subscription. Always returns 204, so the endpoint doesn't reveal whether a token is valid |

All endpoints return 404 when mail is disabled, so the frontend has one signal to hide the feature.

**Unsubscribe.** The email link goes to the SPA route `/unsubscribe?token=…`, which shows a confirm
button that POSTs. It doesn't unsubscribe on GET, because corporate link scanners pre-fetch every
URL in an email and would silently unsubscribe everyone. The message also sets
`List-Unsubscribe: <{base}/api/public/report-subscriptions/unsubscribe-one-click?token=…>` and
`List-Unsubscribe-Post: List-Unsubscribe=One-Click` (RFC 8058). The one-click endpoint accepts only
POST, which is what mail clients send. Both public paths are added to `UserSecurityConfig` next to
the existing `permitAll` entries.

### 3.6 Frontend

- **Subscribe:** a "Email me a digest" menu action on the project dashboard (`features/dashboard`)
  and on the test plan detail (`features/test-plans`). It opens a small dialog with daily/weekly,
  weekday, include PDF, and a preview line ("Next: Monday 07:00 Europe/Zurich").
- **Manage:** a "Report digests" section on the existing `notification-settings` page, which already
  owns email preferences. It lists subscriptions with edit and delete.
- **Unsubscribe page:** the public route `unsubscribe` with a confirm button and a result message. No
  login required.
- Everything is hidden when the list endpoint returns 404 (mail disabled).
- i18n in `en.json` / `de.json`. The email body itself is English in v1, because the mail is sent
  without a request locale; the language is noted in §4.

### 3.7 MCP impact

None. Digests are a human delivery channel, and agents already have `ReportingTools` for pulling the
same numbers.

## 4. Edge Cases

- **Mail disabled after subscriptions exist:** the scheduler no-ops and subscriptions stay. When
  mail is re-enabled, overdue ones send once each (missed slots skipped).
- **Membership removed:** the subscription is deleted at next send, with no email (§3.4).
- **Plan deleted:** cascade removes the subscription. **Project deleted:** same.
- **Plan completed or archived:** still sent, with the status in the subject ("[Completed]"), since
  the subscriber may want the final state. They can unsubscribe.
- **Invalid timezone string:** rejected at create with 400 (`ZoneId.of` validation). A zone that
  later disappears from tzdata falls back to UTC with a WARN.
- **DST transitions:** `ZonedDateTime` resolution. A nonexistent 02:xx is never hit with the default
  send hour 7.
- **PDF generation fails:** send the text digest without the attachment and add a line saying the
  attachment failed. Log ERROR with subscription id.
- **Large projects:** digest content is bounded (top-N lists, counts only), and the PDF is the same
  size as today's manual download.
- **Language:** English only in v1. If German-only teams ask, add a `locale` column captured at
  subscribe time.
- **Recipient email changed:** the address is read from `users` at send time, never stored on the
  subscription.
- **Token leak:** a forwarded email lets the recipient unsubscribe the original subscriber. That is
  the standard, accepted trade-off for one-click unsubscribe, and it exposes no data.

## 5. Testing

- `ReportScheduleCalculator` (pure function): daily and weekly next occurrence, strictly-after-now,
  weekday wrap, DST spring/fall in `Europe/Zurich`, missed slots collapse to one.
- `ReportDigestScheduler`, with a fixed `Clock` and a fake mail sender:
  - Due subscriptions are sent and advanced, and a second pass sends nothing.
  - The conditional claim prevents a double send when two passes race (two threads, one row).
  - A non-member's subscription is deleted and not sent.
  - Mail disabled means no queries and no sends.
  - An SMTP failure advances the subscription and logs.
- Content builders: a project digest with no activity produces the "nothing happened" line, a plan
  digest includes all summary counts, and links are omitted when no public URL is set.
- `PdfReportService.generateTestPlanReport` renders a non-empty PDF for a plan with runs, and for an
  empty plan.
- Controller tests:
  - The caller can only subscribe themselves, and a plan id from another project gets 404.
  - Someone else's subscription gets 404 on PUT and DELETE.
  - Unsubscribe with a valid or invalid token always returns 204, and a valid token deletes.
  - Every endpoint returns 404 when mail is disabled.
- Frontend (Vitest): the dialog computes the preview, the settings section lists and deletes, and
  the unsubscribe page posts only on click.

## 6. Effort & Risk

- **Effort:** ~5–6 days (schema, scheduler and calculator ~2, content + PDF ~1.5, endpoints +
  unsubscribe ~1, frontend ~1.5).
- **Risk:** Low–medium.
  - A data-exposure bug would send project data to a former member. §3.4's explicit check and its
    test are the mitigation.
  - Double-send noise is prevented by the conditional claim.
  - Deliverability (SPF/DKIM) is the operator's SMTP setup, as for PRD-006. The docs say so.

## 7. Acceptance Criteria

- [ ] Users can subscribe themselves to a daily or weekly digest for a project or a test plan, and
      edit or remove it from notification settings.
- [ ] Digests send at `app.reports.send-hour` in the subscriber's timezone, skip missed slots, and
      are never sent twice for one slot.
- [ ] Project membership is re-checked at send time, and non-members' subscriptions are deleted
      without sending.
- [ ] Digest content comes from the existing dashboard, plan summary and flaky services, with deep
      links when a public base URL is configured.
- [ ] Optional PDF attachment: a plan report (new, built on `PdfReportService` helpers) or the latest
      run report.
- [ ] Unsubscribe works via the confirm page and RFC 8058 one-click POST, and a GET never
      unsubscribes.
- [ ] The feature is hidden and the scheduler is inert when `app.mail.enabled=false`.
- [ ] No ShedLock or other new scheduling dependency.
- [ ] Tests above pass. `USER_MANUAL.md` documents digests and the `PUBLIC_BASE_URL` dependency for
      links.
