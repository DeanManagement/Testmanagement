# Testmanagement — User Manual

A self-hosted test management tool. This manual covers everything from writing your first test
case to deploying the application and wiring it into CI.

**Who should read what**

| You are | Start at |
|---|---|
| A tester or QA engineer | [Getting started](#1-getting-started), then [Test cases](#4-test-cases) onward |
| A project lead | [Roles and permissions](#3-roles-and-permissions), [Test plans](#8-test-plans), [Requirements](#9-requirements-and-traceability) |
| An administrator | [Administration](#14-administration) |
| Whoever installs and runs it | [Installation and operations](#16-installation-and-operations) |

---

## Table of contents

1. [Getting started](#1-getting-started)
2. [Core concepts](#2-core-concepts)
3. [Roles and permissions](#3-roles-and-permissions)
4. [Test cases](#4-test-cases)
5. [Folders, labels and search](#5-folders-labels-and-search)
6. [Importing and exporting test cases](#6-importing-and-exporting-test-cases)
7. [Test suites](#7-test-suites)
8. [Test plans](#8-test-plans)
9. [Requirements and traceability](#9-requirements-and-traceability)
10. [Test runs](#10-test-runs)
11. [Reports and dashboards](#11-reports-and-dashboards)
12. [Bug reports](#12-bug-reports)
13. [Notifications and watching](#13-notifications-and-watching)
14. [Administration](#14-administration)
15. [CI/CD integration](#15-cicd-integration)
16. [Installation and operations](#16-installation-and-operations)
17. [Troubleshooting](#17-troubleshooting)
18. [Reference](#18-reference)

---

## 1. Getting started

### Signing in

Open the application URL (`http://localhost:8012` for a default Docker install) and choose
**Sign In**. Enter your email address and password.

If your organisation has configured single sign-on, the login screen also shows a button per
provider — for example *Sign in with Okta*. Use that instead of a password.

The very first account is `admin@localhost.ch`. Its password is either the one set in
`ADMIN_PASSWORD` or a random one printed once to the backend log at first start. You are
required to change it on first login.

### Finding your way around

The left sidebar has three entries:

- **Dashboard** — your landing page: a welcome banner, your queue, and the projects you belong to
- **Projects** — every project you are a member of
- **Settings** — administration; only visible to system administrators

The top bar holds, from left to right: the menu toggle, the application title, and then

| Control | What it does |
|---|---|
| Search (magnifier) | Opens the command palette — also **Ctrl+K** / **Cmd+K** |
| Bell | Notifications, with an unread count |
| Sun / moon | Theme: Light, Dark, or System |
| Globe | Language: English or Deutsch |
| Your name | Account menu |

The account menu is where your personal, cross-project views live: **My Test Runs**, **My Bug
Reports**, **My Watched Items** and **Notification settings** — plus **Sign Out**.

### The command palette

**Ctrl+K** (**Cmd+K** on a Mac) searches across projects, test cases, test runs and bug reports
that are already loaded in your session. Type to filter, arrow keys to move, Enter to open,
Esc to close.

It searches what you have open. If you have not opened a project yet, open it first to widen
the search.

### Theme and language

Both are personal preferences, stored in your browser, and apply immediately.

The theme has three settings: **Light**, **Dark**, and **System**. System follows your
operating system and switches with it, including after dark.

### Your session

Sessions last 12 hours by default. A minute before expiry you get a warning, and you are
returned to the sign-in screen when it lapses. Signing out invalidates the session on the
server, not just in your browser — so does changing your password, which signs out every other
device.

---

## 2. Core concepts

```
Project
├── Requirements ──────── linked to ──┐
├── Test cases                        │
│   ├── Steps (action + expected result, optionally with test data and an image)
│   ├── Parameter sets ──── expand one case into several runs
│   ├── Version history ─── every edit is snapshotted
│   └── Folders ─────────── an optional tree
├── Test suites ─────────── reusable groupings of test cases
├── Test runs ───────────── an execution: one result per case, one step result per step
│   ├── Screenshots
│   └── Allure report (optional)
├── Test plans ──────────── a milestone grouping several runs
└── Bug reports ─────────── defects, optionally linked to a result and run
```

**Project** — the top-level container. Everything else belongs to exactly one project. A project
has a short **key**, e.g. `TES`, which prefixes every generated identifier.

**Test case** — what should be tested, written once and executed many times. Cases are numbered
`TES-1`, `TES-2`, … and carry a priority, a status, labels and an ordered list of steps.

**Test suite** — a named set of test cases. Suites do not own cases; they reference them, so one
case can sit in several suites.

**Test run** — an execution of a set of cases in an environment, at a point in time. The run is
where results are recorded. Runs are numbered `TES-Run-1`, `TES-Run-2`, …

**Test result** — the outcome of one test case within one run, with an outcome per step.

**Test plan** — a milestone that groups runs, so you can track "Release 4.2" as a whole.

**Requirement** — something the product must do, linked to the test cases that prove it.

**Bug report** — a defect. Can be raised straight from a failed result, which pre-fills it.

---

## 3. Roles and permissions

There are two independent things called "admin": a **system administrator** (an account-level
flag) and a **project Admin** (your role within one project).

### Project roles

Every project member has one of three roles. They are cumulative — Admin can do everything a
Tester can, and Tester everything a Viewer can.

| | Viewer | Tester | Admin |
|---|:---:|:---:|:---:|
| Read cases, suites, runs, plans, requirements, bugs, reports, dashboards | ● | ● | ● |
| Create and edit test cases, folders, suites | | ● | ● |
| Import test cases | | ● | ● |
| Start runs, record results, upload screenshots and Allure reports | | ● | ● |
| Create and edit test plans, requirements, parameter sets | | ● | ● |
| Create and edit bug reports | | ● | ● |
| Link, file and unlink issue-tracker issues | | ● | ● |
| Post comments | | ● | ● |
| Manage project members and their roles | | | ● |
| Configure webhooks and the issue tracker | | | ● |
| Edit or delete the project, toggle bug reports | | | ● |

Posting a comment needs Tester. Editing and deleting are author-scoped rather than role-scoped:
you can always edit your own comments, and delete your own — a project or system admin can delete
anyone's.

### System administrators

A system administrator is set per user account, not per project. They:

- see and enter **every** project without being a member
- are treated as project Admin everywhere
- are the **only** ones who can create a project
- own the **Settings** area — users, API keys and SSO
- keep password sign-in even when it has been switched off for everyone else, so a broken
  identity provider cannot lock you out of your own installation

---

## 4. Test cases

Open a project and choose **Test Cases**.

### Writing a test case

**Create Test Case** opens the editor, which has two tabs.

**Details**

| Field | Notes |
|---|---|
| Title | Required |
| Description | What this case covers |
| Preconditions | What must be true before you start |
| Priority | Low, Medium, High, Critical |
| Status | Draft, Active, Deprecated |
| Labels | Comma-separated; used for filtering and grouping |

**Script** — the ordered steps. Each step has:

- **Action** — what the tester does
- **Expected Result** — what should happen
- **Test Data** — optional input values or configuration for this step
- **Image** — an optional reference screenshot

Add steps with **Add Step**; they are numbered automatically and can be reordered.

Leaving the editor with unsaved changes prompts you first.

### Status meanings

| Status | Use it when |
|---|---|
| **Draft** | Still being written; not ready to execute |
| **Active** | Ready to be included in runs |
| **Deprecated** | Kept for history but no longer executed |

### Version history

Every edit snapshots the previous wording. Open a case and choose **Version history** to see
what changed and when, and to compare any two versions field by field, including steps.

This exists so you can answer "what did the tester actually execute?" months later. A result
records the version it ran against, and shows it as *Executed v3*.

There is no rollback: to return to an earlier wording, open that version and re-apply it by hand.

### Parameter sets

A parameter set turns one test case into several data-driven executions without duplicating it.

Write placeholders in your step text using braces — `Log in as {user} with {password}` — then add
one parameter set per data combination, each with a name and a list of key/value pairs. When the
case is included in a run it expands to one result per set, and each expanded result shows its
set name.

Notes:

- Placeholders match letters, digits, `_`, `.` and `-` only, so ordinary prose like
  "press {enter} to continue" is left alone unless you define `enter` as a key.
- An unresolved placeholder is left visible as written rather than blanked, and the editor warns
  you about it — a missing value is obvious instead of silent.
- Limits: 50 parameter sets per case, 50 keys per set. Set names must be unique within a case.
- Results already recorded keep the values they ran with, even if you later change the set.

---

## 5. Folders, labels and search

**Folders** organise cases into a tree, shown beside the list. Create, rename, drag to move, and
nest as deep as you like. Deleting a folder does not delete its contents: subfolders move up a
level and test cases move to the root.

Folders are a filing system, not a permission boundary.

**Labels** are free-text tags on a case (`smoke`, `regression`, `flaky`). Use them to filter, and
to pick out a themed set of cases when starting a run.

**Filtering** — the list has a search box plus Status and Priority filters. The row-density
toggle switches between comfortable and compact rows.

**Bulk actions** — tick the checkboxes to select cases, then set a status on all of them, add them to a suite, or
delete them. **Bulk delete refuses cases that already have results** — retire those by setting
them Deprecated instead. (Deleting a single case from its own page has no such guard and takes its
results with it, so prefer bulk delete when you want the safety net.)

---

## 6. Importing and exporting test cases

### Importing

**Import** on the test case list accepts a **CSV** or **JSON** file, up to 500 rows.

Columns: `title`, `description`, `preconditions`, `priority`, `status`, `labels`, `steps`.

- **`title` is the only required column.** If the header is missing the whole file is rejected;
  if an individual row has a blank title, only that row fails.
- `priority` defaults to `MEDIUM` and `status` to `DRAFT` when omitted. An unrecognised value
  fails that row rather than being silently coerced.
- **Labels** are separated by semicolons: `smoke;regression`
- **Steps** are `action|expectedResult` pairs joined by double semicolons:
  `Open login|Form appears;;Enter credentials|Dashboard loads`
- Unknown columns are ignored, so you can import a spreadsheet with extra columns as-is.

Always use **Preview (dry run)** first. It validates every row and saves nothing, then reports
how many rows would be imported, how many skipped, and the row number and reason for each
failure.

CSV cannot carry per-step test data. Use JSON if you need it.

### Exporting

**Export** offers three formats:

- **JSON** — round-trips everything, including step test data
- **CSV** — for spreadsheets and diffing
- **CSV (Excel)** — same, with a byte-order mark so Excel opens UTF-8 correctly

Values that begin with `=`, `+`, `-`, `@`, a tab or a carriage return are prefixed with an
apostrophe on export, so spreadsheets treat them as text rather than formulas.

Test run and test suite **reports** export as PDF from their own screens.

---

## 7. Test suites

A suite is a named, reusable set of test cases — "Smoke", "Checkout regression", "Release
candidate".

Create one under **Test Suites**, give it a name and description, and pick its cases. A case can
belong to any number of suites, and adding it to a suite does not move or copy it.

Each suite has a **report** showing the latest known result for every case in it, and that report
downloads as PDF.

Suites are the usual starting point for a run: you assemble the set once and reuse it every
release, instead of hand-picking cases each time.

---

## 8. Test plans

A test plan is a milestone that groups several runs — typically one release.

Create one under **Test Plans** with a name, description, target date, status and assignee. Then,
when starting a run, pick the plan in the **Test Plan** field to attach it.

| Plan status | Meaning |
|---|---|
| **Open** | Created, not started |
| **In Progress** | Runs are under way |
| **Completed** | Finished |
| **Cancelled** | Abandoned; kept for the record |

The plan detail page rolls up everything attached to it: total runs, completed runs, overall pass
rate, result distribution, runs by status, and pass rate per run. It is the screen to project on
the wall during a release.

---

## 9. Requirements and traceability

Requirements answer a different question from test runs: not "did the tests pass?" but "is what
we promised actually proven?"

Under **Requirements**, add each requirement with an ID from your spec or tracker, a title and a
description. Then link the test cases that prove it.

Two views come out of this:

- **Traceability matrix** — one row per requirement, listing every linked case with the status of
  its most recent result, plus the worst status across the row
- **Coverage** — a summary: total requirements, how many are uncovered, untested, failing, and
  passing

| Requirement status | Meaning |
|---|---|
| **Covered** | Every linked test passed in its most recent run |
| **Failing** | At least one linked test failed most recently |
| **Blocked** | At least one linked test was blocked |
| **Skipped** | Linked tests were skipped |
| **Untested** | A test is linked but has never been executed — nothing proves this yet |
| **No tests** | No test case is linked at all |

**Read the coverage percentage carefully.** It counts requirements whose linked tests have
actually *passed* — not merely those that have a test attached. A requirement with a linked test
that has never run counts as untested, not covered. That is deliberate: coverage that counts
intentions rather than evidence is worse than no number at all.

---

## 10. Test runs

### Starting a run

**Test Runs → Start Test Run**. You choose:

| Field | Notes |
|---|---|
| Name | e.g. "Release 4.2 smoke" |
| Environment | Free text: `staging`, `prod`, `iOS 18` |
| Test Plan | Optional; attaches this run to a milestone |
| Executor | Who is expected to run it; can be left unassigned |
| Test cases | Pick them individually, filter by folder, or search |

Cases with parameter sets expand to one result per set.

| Run status | Meaning |
|---|---|
| **Planned** | Created, not started |
| **In Progress** | Being executed |
| **Completed** | Finished; the UI stops you editing results |
| **Aborted** | Stopped early |

### Executing

The execution screen has the case list on the left and the current case on the right. For each
case, set an outcome per step and an overall outcome:

| Result | Meaning |
|---|---|
| **Pending** | Not yet executed |
| **Passed** | Behaved as expected |
| **Failed** | Did not behave as expected |
| **Blocked** | Could not be executed — environment down, dependency broken |
| **Skipped** | Deliberately not executed this time |

Record what actually happened in **Actual Result**, and attach a screenshot per step with **Add
Screenshot**. Reference images from the test case are shown inline for comparison.

### Keyboard shortcuts

Executing a long run with the mouse is slow. While a run is **In Progress**, press **?** for the
cheat sheet, or use:

| Key | Action |
|---|---|
| `J` or `↓` | Next test result |
| `K` or `↑` | Previous test result |
| `P` | Mark the current result **Passed** |
| `Shift+P` | Mark **every step** Passed and the result Passed |
| `F` | Mark the current result **Failed** |
| `B` | Mark the current result **Blocked** |
| `S` | Mark the current result **Skipped** |
| `C` | Focus the comment field |
| `?` | Show this list |

Shortcuts are ignored while you are typing in a field or while a dialog is open, so `f` in a
comment stays an `f`.

### Finishing a run

**Complete** closes the run. If any results failed or were blocked, you are told how many and
what the resulting run status will be before you confirm. A run with everything passing says so.

**Abort** stops a run early — use it when the environment collapsed, not when tests failed.

**Reopen** on a completed run requires a written reason, which is recorded against the run. This
is intentional friction: reopening changes history, so the record says why.

**Clone** copies a run's case selection into a fresh run with a new name and environment, without
copying results. This is how you re-test the same set next release.

### Allure reports

If your automation produces an [Allure](https://allurereport.org/) report, zip the generated
`allure-report` directory and attach it with **Upload Allure Report**. The ZIP must contain
`index.html`. A wrapping directory is detected and stripped automatically.

Once uploaded the button becomes **Allure Report** and opens the full report in a new tab, with
CSS, JavaScript, images and fonts served correctly.

Reports can also be uploaded straight from CI — see [CI/CD integration](#15-cicd-integration).

---

## 11. Reports and dashboards

**Run report** (**Report** on a run) — totals per outcome, pass rate, a status distribution chart
and the per-case results. **Download PDF** produces a shareable copy.

**Suite report** — the latest known result for every case in a suite, also as PDF.

**Project dashboard** — test case and suite counts, cases by status and by priority, the latest
run's results, overall pass rate, a pass-rate trend across recent runs, and recent runs.

**Your dashboard** — the projects you belong to plus **your queue**: test plans due soon, runs in
progress, bug reports without recent activity, and old draft test cases.

### Flaky test detection

The dashboard flags cases that keep changing outcome between runs.

The score counts **how often consecutive runs disagreed**, not how often the test failed. A test
that fails every single time scores 0% — it is broken, not flaky. A test alternating pass, fail,
pass, fail scores 100%.

Only finished Passed/Failed results in the recent window count, so a test stops being flagged
once it has enough clean runs behind it. Defaults: a window of the 20 most recent results, a
threshold of 30%, and a minimum of 5 runs before anything is reported at all — deliberately
conservative, because under-reporting beats calling a team's tests flaky on thin evidence.

A project Admin can sync a `flaky` label onto the offending cases. This is off by default,
because labels are user-owned.

---

## 12. Bug reports

Built-in defect tracking, for teams without a separate tracker. A project Admin can switch it off
per project under Project Settings.

**Report Bug** from a failed result pre-fills the test case, run, result and environment. You can
also raise one directly from **Bug Reports**.

| Field | Notes |
|---|---|
| Title, Description | What is wrong |
| Steps to Reproduce | |
| Expected / Actual Behavior | |
| Priority | Low, Medium, High, Critical |
| Environment | Where it happened |
| Assignee | Who owns it |

| Status | Meaning |
|---|---|
| **Open** | Reported, not started |
| **In Progress** | Being worked on |
| **Resolved** | Fixed, awaiting verification |
| **Closed** | Verified and done |
| **Won't Fix** | Acknowledged, deliberately not fixing |

Status changes require a reason, which is kept in the history. Linked test cases and runs stay
clickable from the bug, and linked bugs are shown on the result they came from.

**My Bug Reports** in the account menu lists everything assigned to you across all projects.

---

## 13. Notifications and watching

### Watching

Test plans, test runs and bug reports can be **watched**. Use the bookmark control on the item.
**My Watched Items**, in the account menu, lists everything you watch.

Test cases and suites cannot be watched — the things worth following are the ones with a lifecycle.

### What you get told

Notifications fire on: created, updated, deleted, status changed, completed, reopened, cloned and
moved.

You are never notified about your own actions. If you lose access to a project you stop receiving
its notifications, even if you are still nominally watching an item in it.

### Channels

**Notification settings** (from the account menu) has a row per event and a toggle per channel.
By default in-app notifications are on and email is off.

Email only sends if the administrator has enabled and configured mail on the server. If it has
not been, the toggle still moves but nothing is sent — ask your administrator.

The bell in the top bar shows the unread count; **Mark all read** clears it.

---

## 14. Administration

### Project members

A project Admin manages access under **Members** on the project page: add a user, set their role
(Admin, Tester, Viewer), or remove them.

System administrators see every project regardless of membership, so you do not need to add them.

### Users

**Settings → Users** (system administrators only). Create accounts with a display name, email,
password and an optional System Administrator flag. Editing a user lets you reset their password;
leave the field blank to keep the current one.

There is no self-service registration. Accounts are created here, or provisioned by SSO.

### API keys

**Settings → API Keys**. Keys let CI pipelines submit results without a user account.

Create a key with a name and **the project it is scoped to**. The raw key is shown **once** — copy
it into your CI secret store immediately, because it cannot be retrieved again. Only a prefix is
stored afterwards, alongside the created and last-used timestamps.

A scoped key works only against its own project; used against another it is rejected with `403`.
Keys created before scoping existed show as *All projects (legacy)* and still work, but the
backend logs a warning on every use and they will stop being accepted in a future release —
replace them with scoped keys.

**Revoke** stops a key working immediately. The row stays visible, marked Revoked, for the audit
trail.

### Single sign-on

**Settings → Single sign-on** (system administrators only). Any OpenID Connect provider works —
Keycloak, Authentik, Auth0, Okta, Entra ID, Google.

Per provider you configure:

| Field | Notes |
|---|---|
| Display name | The label on the sign-in button |
| Slug | Lowercase letters, digits and hyphens. Appears in the callback URL and cannot be changed afterwards, because your provider already knows it |
| Issuer URL | The OIDC discovery root. Must be HTTPS; private and loopback addresses are rejected |
| Client ID / Client secret | From your provider. The secret is stored encrypted and requires `APP_ENCRYPTION_KEY` on the server. On edit, leaving it blank keeps the stored one |
| Scopes | Defaults to `openid,profile,email`; `openid` is always included |
| Email / name claim | Default `email` and `name` |
| Admin claim + value | Optional. A match grants the system administrator flag. Group and array claims match on membership |

**Redirect URI** — the screen shows the exact URL to register with your provider. It has the shape
`https://your-host/login/oauth2/code/<slug>`.

**Test connection** fetches the discovery document and reports what went wrong if it fails. Recent
errors are kept on the provider row.

Two settings decide how accounts are handled:

- **Create accounts on first sign-in** (on by default) — an unknown user gets an account
  automatically. They can sign in, but see nothing until an admin adds them to a project.
- **Trust this provider's email for account linking** (**off** by default) — when on, someone
  signing in with a *verified* email that matches an existing account takes over that account.
  Only enable this for a provider that controls which addresses its users can claim. This is an
  account-takeover boundary, which is why it is off by default.

**Password sign-in** can be switched off entirely once at least one provider is active. Two
safeguards prevent lockout: you cannot disable it without an active provider, and system
administrators can always sign in with a password regardless.

### Webhooks

**Webhooks** on a project (project Admin). Send a signed HTTPS callback to an external system when
something happens.

Configure a payload URL, a signing secret, and which events to send:

`RUN_STARTED`, `RUN_COMPLETED`, `RUN_FAILED`, `TEST_FAILED`, `PLAN_COMPLETED`, `BUG_REPORT_CREATED`

**Send test** delivers a sample immediately. **View deliveries** shows each attempt with its HTTP
status and time. A delivery is attempted up to three times in total — the initial attempt plus two
retries, one minute and then five minutes later.

Private and loopback URLs are refused by default as an SSRF guard; an operator can allow them with
`WEBHOOKS_ALLOW_PRIVATE_TARGETS`.

### Issue tracker

**Issue Tracker** on a project (project Admin) connects it to **GitLab** or **Forgejo/Gitea**.

Provide the instance URL (HTTPS; private addresses rejected by default), the project reference,
and an API token. The token is stored encrypted and requires `APP_ENCRYPTION_KEY` on the server —
without it, saving is refused rather than storing the token in plain text. **Test connection**
verifies it before you save.

Once connected, a tester working through a failed result can:

- search the tracker and **link** an existing issue
- **file a new issue** in one click, with title and body pre-filled from the failed result
- see linked issues with an Open / Closed / Unknown badge, refreshed periodically and on demand
- **unlink** an issue

Disconnecting the tracker keeps issues already linked to results; they stay clickable.

---

## 15. CI/CD integration

External tools submit results with an API key in the `X-API-Key` header. No user account is
involved.

The examples below use `:8089`, the port the application listens on inside the container and when
you run it directly. A default Docker Compose install publishes it as `:8012` — use whichever
address your users reach the UI on, since the API is served from the same origin.

Two path segments accept either form:

- `{projectRef}` — the project key (`TES`) **or** its UUID
- `{testRunRef}` — the run key (`TES-Run-1`, returned as `key` when the run is created) **or** its UUID

### Submitting a completed run

```bash
curl -X POST \
  http://localhost:8089/api/external/projects/TES/test-runs \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $TM_API_KEY" \
  -d '{
    "name": "CI Build #142",
    "environment": "staging",
    "results": [
      { "testCaseKey": "TES-1", "status": "PASSED" },
      { "testCaseKey": "TES-2", "status": "FAILED",
        "comment": "Assertion failed on line 42",
        "defectLink": "https://issues.example.com/BUG-789",
        "stepResults": [
          { "stepIndex": 1, "status": "PASSED", "actualResult": "Login page shown" },
          { "stepIndex": 2, "status": "FAILED", "actualResult": "500 instead of dashboard" }
        ]
      }
    ]
  }'
```

Results reference test cases by **key** (`TES-1`), and steps by **1-based index**, not by UUID. If
`stepResults` is omitted, every step of the case takes the result's status.

The response is `201 Created` with the full run, including the `key` you need for an Allure upload.

### Importing a JUnit or Cucumber report

If your framework already produces a standard report, post it directly and let the server parse
it. Test cases that do not exist yet are created automatically and labelled `ci-imported`.

```bash
# JUnit XML
curl -X POST "http://localhost:8089/api/external/projects/TES/test-runs/junit?runName=Nightly&environment=staging" \
  -H "Content-Type: application/xml" -H "X-API-Key: $TM_API_KEY" \
  --data-binary @target/surefire-reports/junit.xml

# Cucumber JSON
curl -X POST "http://localhost:8089/api/external/projects/TES/test-runs/cucumber?runName=Nightly" \
  -H "Content-Type: application/json" -H "X-API-Key: $TM_API_KEY" \
  --data-binary @target/cucumber.json
```

Optional query parameters: `runName`, `environment`, `testPlanId`. Reports are capped at 10 MB.

### Attaching an Allure report

```bash
allure generate allure-results -o allure-report
zip -r allure-report.zip allure-report/

curl -X POST \
  http://localhost:8089/api/external/projects/TES/test-runs/TES-Run-1/allure-report \
  -H "X-API-Key: $TM_API_KEY" \
  -F "file=@allure-report.zip"
```

The run must belong to the project named in the URL.

### Status codes

| Code | Meaning |
|---|---|
| `201` | Created |
| `400` | Validation error — blank name, empty results, malformed report |
| `401` | Missing, invalid or revoked API key |
| `403` | The key is scoped to a different project than the URL names |
| `404` | Project, run, test case or step not found — **also what a wrong URL returns** |

The most common cause of a `404` here is a wrong URL rather than missing data — most often the
missing `/allure-report` suffix. Both the project and the run may be named by key or by UUID, so
neither form is the problem.

---

## 16. Installation and operations

### Requirements

Docker and Docker Compose. Nothing else — no identity provider, no mail server, no external
services. The application is designed to run air-gapped.

Two containers: the application (one image containing both the API and the web UI) and
PostgreSQL.

### Installing

```bash
git clone <your-fork> testmanagement
cd testmanagement

cp .env.example .env
# Fill in at minimum:
#   DB_PASSWORD  — any strong value
#   JWT_SECRET   — openssl rand -base64 48

docker compose up --build -d
```

The application is served on `http://localhost:8012` — UI and API on the same origin, from a
single container. The backend refuses to start without a `JWT_SECRET` rather than falling back to
a known default.

> **Note:** PostgreSQL only applies `DB_PASSWORD` when its data volume is first created. On an
> existing volume the variable is ignored and the app fails to connect until the password is
> changed inside the database as well. If you set it after a first run, either recreate the volume
> or `ALTER USER testmanagement WITH PASSWORD …`.

Sign in as `admin@localhost.ch`. If you left `ADMIN_PASSWORD` empty, a random password is printed
**once** at first start:

```bash
docker compose logs testmanagement | grep -i password
```

You must change it at first login.

### First-run checklist

1. Change the admin password
2. **Settings → Users** — create accounts for your team
3. Create a project (system administrators only) and add members with roles
4. Optional: **Settings → Single sign-on**, project **Webhooks**, project **Issue Tracker**
5. Optional: **Settings → API Keys** for CI, scoped to the project that needs them

### Configuration

Everything is environment variables on the backend container. Required:

| Variable | Notes |
|---|---|
| `JWT_SECRET` | No default — startup fails without it. `openssl rand -base64 48`. Changing it signs everyone out |
| `DB_PASSWORD` | Used by both the database and the app. See the note above about existing volumes |

Required only for certain features:

| Variable | Needed for |
|---|---|
| `APP_ENCRYPTION_KEY` | Storing issue-tracker tokens and OIDC client secrets. Base64 AES key, `openssl rand -base64 32`. Without it those features refuse to save a secret rather than storing it in plain text. **Changing it makes stored secrets undecryptable** — you must re-enter them |
| `MAIL_ENABLED` + `spring.mail.*` | Email notifications. Without a configured mail sender, email toggles have no effect |

Optional, with defaults:

| Variable | Default |
|---|---|
| `ADMIN_EMAIL` | `admin@localhost.ch` |
| `ADMIN_PASSWORD` | *(generated and logged once)* |
| `ADMIN_DISPLAY_NAME` | `Administrator` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` — the UI is same-origin with the API now, so this only matters for a separate dev server |
| `JWT_EXPIRATION_MS` | `43200000` (12 hours) |
| `SEARCH_FULL_TEXT` | `true` — Postgres full-text search; set `false` on other databases |
| `MAIL_ENABLED` / `MAIL_FROM` | `false` / `no-reply@testmanagement.local` |
| `SSO_CALLBACK_URL` | `/login/callback` — set to your public frontend URL if the UI is not served from the API's origin |
| `SSO_ALLOW_PRIVATE_ISSUERS` | `false` |
| `ISSUE_TRACKER_ALLOW_PRIVATE_TARGETS` | `false` |
| `WEBHOOKS_ALLOW_PRIVATE_TARGETS` | `false` |
| `FLAKY_AUTO_LABEL` | `false` |
| `APP_VERSION` | `dev` |

The three `ALLOW_PRIVATE_*` flags are SSRF guards. They stop an administrator — or anyone who has
compromised an admin account — pointing a webhook, tracker or OIDC issuer at something inside your
network. Turn one on only when you genuinely need to reach an internal host.

Some tunables are YAML-only and need a rebuilt image: server port, the 10 MB upload cap, page
sizes (50, max 200), the flaky window/threshold/minimum, issue-tracker timeouts and poll interval,
and the webhook retry schedule.

### Backups

Everything lives in PostgreSQL, including uploaded screenshots and Allure reports. The
application container holds no state and can be recreated freely.

```bash
docker compose exec testmanagement-db \
  pg_dump -U testmanagement testmanagement | gzip > backup-$(date +%F).sql.gz
```

Back up your `.env` separately and just as carefully. Losing `APP_ENCRYPTION_KEY` means re-entering
every stored tracker token and OIDC secret; losing `JWT_SECRET` signs everyone out.

Note that PostgreSQL only applies its password when the data volume is first created. Changing it
later means changing it inside the database too, not just in `.env`.

### Upgrading

```bash
git pull
docker compose up --build -d
```

Flyway applies database migrations automatically at startup. Take a backup first — migrations are
not reversible.

### Health

`GET /actuator/health` for container probes and `GET /actuator/info` for the version. Nothing else
is exposed.

---

## 17. Troubleshooting

**I can't sign in.** If password sign-in has been disabled organisation-wide, use the SSO button.
System administrators can always use a password. After too many failed attempts, sign-in is
throttled for a while — wait, rather than retrying harder.

**I signed in but there's nothing here.** You have an account but no project membership. This is
normal for a fresh SSO account. Ask an administrator to add you to a project.

**I can see a project but can't change anything.** You have the Viewer role. Ask a project Admin
for Tester.

**"You're not a member of this project."** Your access was removed, or you followed a link to a
project you were never in.

**My session keeps expiring.** Sessions last 12 hours. Changing your password signs out every
other device, by design.

**CI gets `401`.** The `X-API-Key` header is missing, or the key is wrong or revoked. The raw key
is only shown once — if it was not saved, create a new one.

**CI gets `403`.** The key is scoped to a different project than the URL names. Check that the
project key in the URL matches the project the key was created for.

**CI gets `404`.** Usually a wrong URL rather than missing data. Check the `/allure-report` suffix
is present and that the run actually belongs to the project named in the URL. Both the project and
the run accept either their key or their UUID, so that is not the cause.

**The Allure report won't open.** The ZIP must contain `index.html`. Zip the generated
`allure-report` directory, not the raw `allure-results`.

**Email notifications never arrive.** Email is off unless the operator set `MAIL_ENABLED=true`
*and* configured a mail server. The toggle in the UI does not turn on the server side.

**Saving an issue-tracker token or SSO secret is refused.** `APP_ENCRYPTION_KEY` is not set on the
backend. The application will not store those secrets in plain text.

**A webhook or SSO issuer is rejected.** Private and loopback addresses are blocked by default.
See the `ALLOW_PRIVATE_*` variables above, and be sure you want to.

---

## 18. Reference

### Statuses at a glance

| Object | Values |
|---|---|
| Test case | `DRAFT`, `ACTIVE`, `DEPRECATED` |
| Test run | `PLANNED`, `IN_PROGRESS`, `COMPLETED`, `ABORTED` |
| Test result | `PENDING`, `PASSED`, `FAILED`, `BLOCKED`, `SKIPPED` |
| Test plan | `OPEN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| Bug report | `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `WONTFIX` |
| Priority | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| Requirement coverage | Covered, Failing, Blocked, Skipped, Untested, No tests |
| Linked issue | Open, Closed, Unknown |

### Identifier formats

| Thing | Format | Example |
|---|---|---|
| Project key | Up to 10 characters | `TES` |
| Test case key | `{projectKey}-{n}` | `TES-1` |
| Test run key | `{projectKey}-Run-{n}` | `TES-Run-1` |

### Keyboard shortcuts

| Key | Where | Action |
|---|---|---|
| `Ctrl+K` / `Cmd+K` | Anywhere | Command palette |
| `J` / `↓` | Run execution | Next result |
| `K` / `↑` | Run execution | Previous result |
| `P` | Run execution | Mark Passed |
| `Shift+P` | Run execution | Mark every step and the result Passed |
| `F` | Run execution | Mark Failed |
| `B` | Run execution | Mark Blocked |
| `S` | Run execution | Mark Skipped |
| `C` | Run execution | Focus comment |
| `?` | Run execution | Shortcut help |

### Limits

| | Limit |
|---|---|
| Test case import | 500 rows per file |
| File upload | 10 MB |
| CI report body | 10 MB |
| Parameter sets per case | 50 |
| Parameter keys per set | 50 |
| Comment length | 2000 characters |
| Page size | 50 by default, 200 maximum |
| Webhook delivery | 3 attempts total (2 retries, after 1 and 5 minutes) |

### Further reading

- [README](../README.md) — architecture, quick start, API summary
- [CONTRIBUTING](../CONTRIBUTING.md) — development workflow and conventions
- [docs/prd/](prd/) — the design document behind each feature
