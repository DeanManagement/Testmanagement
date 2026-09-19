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
| Trigger assigned build-server workflows and refresh pipeline status | | ● | ● |
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
- own the **Settings** area — users, API keys, SSO and build servers
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
| Status | Draft, In review, Active, Deprecated (see [Review and approval](#review-and-approval)) |
| Labels | Comma-separated; used for filtering and grouping |

**Script** — the ordered steps. Each step has:

- **Action** — what the tester does
- **Expected Result** — what should happen
- **Test Data** — optional input values or configuration for this step
- **Image** — an optional reference screenshot

Add steps with **Add Step**; they are numbered automatically and can be reordered.

**Estimate** is optional: how many minutes one execution takes (1 to 1440). Estimates add up to the
remaining effort on runs and plans, and to the plan's burn-down. The case page shows the estimate
next to the median of the last five measured executions, so you can correct it from evidence.

Leaving the editor with unsaved changes prompts you first.

### Attachments

The **Attachments** card on the case page holds the files a tester needs to carry the case out: a
sample invoice to upload, a CSV to import, a spec PDF, an expected-output image. Testers and admins
choose **Attach file** or drop a file onto the card; viewers can list and download.

- **Allowed:** PNG, JPEG, GIF and WebP images, PDF, ZIP, Word/Excel/PowerPoint (.docx, .xlsx,
  .pptx), and text files (.txt, .log, .csv, .json, .xml). The file's content must match its type:
  an HTML page renamed to `.pdf` is refused. SVG and HTML are never accepted.
- **Limits:** 10 MB per file, 20 files per case, and 500 MB per project in total. Administrators
  change the last two with `APP_ATTACHMENTS_MAX_PER_CASE` and `APP_ATTACHMENTS_MAX_PROJECT_BYTES`
  (`0` = unlimited).
- Images show as thumbnails; everything else downloads when you click its name.
- Uploading a file identical to one already attached works, but the card points it out.

While executing a run, the case's attachments appear, collapsed, above the steps.

Attachments are **not versioned**: adding or deleting one doesn't create a new version of the case.
The project's activity log records who attached or removed which file, and when.

### Status meanings

| Status | Use it when |
|---|---|
| **Draft** | Still being written; not ready to execute |
| **In review** | Written, waiting for someone else to check it |
| **Active** | Ready to be included in runs. Shown as **Approved** in projects that require review |
| **Deprecated** | Kept for history but no longer executed |

### Review and approval

Projects can require that test cases are reviewed before they count: **Project settings → Test
case review** (project Admin). It is off by default, and a project without it works exactly as
before.

With review on, **Active means approved**:

1. The author writes the case as **Draft** and chooses **Submit for review** on the case page.
2. A reviewer opens it and chooses **Approve vN** or **Request changes** (optionally with a
   comment, which lands in the case's comments and sends it back to Draft). *Awaiting my review* in
   **My queue** lists what's waiting for you.
3. The case page then shows *Approved v4 by X on date*.

Rules:

- **Who can approve:** project admins, or testers too if you choose *Testers and admins*. Never
  the case's author or its last editor: approving your own wording is what review prevents. If
  fewer than two members could approve, the settings page warns you.
- **Approving is the only way to Active.** Setting Active in the editor, in bulk, through
  import, CI or an MCP agent is refused. Import turns Active rows into *In review* and says so,
  and cases created by CI uploads start *In review*. AI agents can submit for review but never
  approve.
- **Editing the wording** (title, description, preconditions or steps) of an approved case
  sends it back to review. *Compare with approved version* shows exactly what changed. Labels,
  priority and folder moves keep it approved.
- **Stale approvals are refused:** if the case changed after you opened it, approving fails and
  asks you to review the latest version.
- **Deprecating** needs a project admin.
- Runs may still include cases that aren't approved (exploratory re-runs, a first dry run). Their
  **run report** and PDF mark each such result *Executed unapproved wording (vN)*, which is the
  audit record.

Cases that were Active before review was switched on stay Active and show *Approved before review
was switched on*. Switching review off leaves cases in review where they are.

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

### Shared steps

A **shared step** is a named block of steps, such as "Log in as admin" or "Reset the basket", kept
once per project and used by any number of test cases. When the login page changes, you edit the
shared step once instead of every case that copied its steps.

**Managing them.** Open **Shared steps** from the project page. The list shows each shared step's
number of steps and how many test cases use it. Creating or editing one works like editing a
case's steps: action, expected result, test data and an image per step.

**Using one in a test case.** On the case's **Steps** tab, choose **Insert shared step** and pick
one. It appears as a single row, which you can expand to read its steps; you edit them on the
shared step's own page. A case can mix its own steps and any number of shared steps.

**Editing a shared step changes every case that uses it.** The editor says how many do and lists
them. When you save a change to the steps:

- every case that uses it records a [version](#version-history) first, with the wording it had
  until now, so the history of each case stays complete;
- in a project that [requires review](#review-and-approval), those cases go back to review, just as
  if their own steps had been edited;
- runs created afterwards use the new steps. **A run already created shows the new wording of steps
  whose text you changed**, the same as when you edit a case's own steps; steps you add or remove do
  not change existing runs.

Renaming a shared step without touching its steps records no versions.

**How it shows elsewhere.** On the test case page, in run execution and in version history, a
shared step's steps appear in place, numbered along with the case's own steps, under the shared
step's title. The parameter editor sees `{placeholders}` inside shared steps too, and fills them
from the calling case's [parameter sets](#parameter-sets): a shared step with `{user}` works for a
case whose sets define `user`.

**Converting to local steps.** On the test case page, **Convert to local steps** next to a shared
step's title replaces it with a copy of its steps, images included, and records a version. The
case no longer follows the shared step.

**Deleting.** A shared step can only be deleted while no case uses it. Convert or remove it in
those cases first; the editor lists them.

Notes:

- A shared step cannot contain another shared step.
- A shared step with no steps adds nothing when run; the case form says so.
- **Edit as Gherkin** is unavailable while a case uses a shared step, because Gherkin has no
  shared steps and applying would turn them into local ones.

---

## 5. Folders, labels and search

**Folders** organise cases into a tree, shown beside the list. Create, rename, drag to move, and
nest as deep as you like. Deleting a folder does not delete its contents: subfolders move up a
level and test cases move to the root.

Selecting a folder lists everything in its subtree, and the count beside each folder is the
subtree total. Switch off **Include subfolders** above the tree to see only a folder's direct
contents (and direct counts); the choice is kept in the URL, so a bookmarked list keeps it.

Folders are a filing system, not a permission boundary.

**Labels** are free-text tags on a case (`smoke`, `regression`, `flaky`). Use them to filter, and
to pick out a themed set of cases when starting a run.

**Filtering** — the list has a search box plus Status and Priority filters. The row-density
toggle switches between comfortable and compact rows.

**Bulk actions** — tick the checkboxes to select cases, then set a status on all of them, add them to a suite, or
delete them. **Bulk delete refuses cases that already have results** — retire those by setting
them Deprecated instead. (Deleting a single case from its own page has no such guard and takes its
results with it, so prefer bulk delete when you want the safety net.)

### Custom fields

When labels aren't enough (`component:checkout` has no fixed list, no numbers, no dates), a
project admin can add typed fields under **Custom fields** on the project page. Each field
belongs to test cases, test runs or bug reports, and is one of:

| Type | Holds |
|---|---|
| Text | one line, up to 500 characters |
| Number | a decimal with up to 4 decimal places |
| Date | a calendar date |
| Single select | one option from a list you define |
| Multi select | any number of options from a list you define |

A project can have 20 active fields per kind of item, and 50 options per list. Drag to reorder;
the order is the order on forms and detail pages.

- **Required** fields must be filled in when a person saves a form. Imports, CI uploads and API
  agents are never blocked by them, and items that existed before the field aren't invalid: the
  form asks the next time someone edits them.
- **Archive** a field (the switch) to retire it. It disappears from forms and filters and keeps
  its values, which still show on detail pages and in exports. Switch it back on at any time.
- **Delete** removes a field for good. If it holds values you are asked a second time, because
  they are discarded everywhere.
- A field's **type can't change**. Create a new field and archive the old one.
- Editing an option **renames it everywhere** it is used. An option that is in use can't be
  removed; rename it, or archive the field.

Fields appear on the test case, test run and bug report forms, and on their detail pages once they
hold a value. Run fields are filled in when the run is started.

**Filtering** — on the test case and test run lists, **More filters** has one control per field:
a list of options (any of the chosen ones matches), a text fragment, or a from/to range for
numbers and dates. Like every other filter it lives in the URL, so a filtered list can be
bookmarked or shared.

---

## 6. Importing and exporting test cases

### Importing

**Import** on the test case list accepts a **CSV** or **JSON** file, up to 500 rows, or Gherkin
`.feature` files (see [BDD / Gherkin](#bdd--gherkin) below).

**Import into folder** puts the cases in that folder instead of the project root. It starts on the
folder selected in the list.

Columns: `title`, `description`, `preconditions`, `priority`, `status`, `labels`, `steps`,
`estimateMinutes` (whole minutes, 1 to 1440; blank for none).

- **`title` is the only required column.** If the header is missing the whole file is rejected;
  if an individual row has a blank title, only that row fails.
- `priority` defaults to `MEDIUM` and `status` to `DRAFT` when omitted. An unrecognised value
  fails that row rather than being silently coerced.
- **Labels** are separated by semicolons: `smoke;regression`
- **Steps** are `action|expectedResult` pairs joined by double semicolons:
  `Open login|Form appears;;Enter credentials|Dashboard loads`
- **Custom fields** are columns named `cf:<Field name>`, for example `cf:Component`. Multi-select
  options are separated by semicolons, dates are `yyyy-MM-dd`. In JSON they are a
  `"customFields": { "Component": "Checkout", "Browsers": ["Chrome", "Firefox"] }` object. A field
  name or option the project doesn't have fails that row.
- Other unknown columns are ignored, so you can import a spreadsheet with extra columns as-is.

Always use **Preview (dry run)** first. It validates every row and saves nothing, then reports
how many rows would be imported, how many skipped, and the row number and reason for each
failure.

CSV cannot carry per-step test data. Use JSON if you need it.

**Shared steps** travel in JSON by title: a step `{"sharedStepTitle": "Log in as admin"}` uses the
project's shared step of that title, so export from one project and import into another that has
the same shared steps. A title the project does not have fails that row, which the dry run shows.
Create the shared steps first.

### Exporting

**Export** offers four formats:

- **JSON** — round-trips everything, including step test data and custom fields
- **CSV** — for spreadsheets and diffing. [Shared steps](#shared-steps) are written out as their
  steps, so importing the file again gives the case local copies
- **CSV (Excel)** — same, with a byte-order mark so Excel opens UTF-8 correctly
- **Gherkin (.feature)** — the selected folder, or the whole project; see below

JSON lists each case's attachments by name, type, size and checksum, but not their content, so the
file stays readable. Importing such a file skips the attachments and the preview says so for each
case. CSV has no attachment column.

CSV has one `cf:<Field name>` column per test case custom field, archived ones included, so
exporting and importing again loses nothing.

Values that begin with `=`, `+`, `-`, `@`, a tab or a carriage return are prefixed with an
apostrophe on export, so spreadsheets treat them as text rather than formulas.

Test run and test suite **reports** export as PDF from their own screens.

### BDD / Gherkin

Teams that write Cucumber scenarios can keep their `.feature` files as the source of truth. A
scenario becomes an ordinary test case, so it can be planned, run by hand, reported on and linked
like any other; there is no separate "BDD case".

| Gherkin | Becomes |
|---|---|
| `Feature:` | a folder (under the folder you import into) |
| `Rule:` | a folder inside the feature's folder |
| `Scenario:` / `Scenario Outline:` | a test case; its name is the title |
| Scenario description | the description |
| Each step (`Given`, `When`, `Then`, `And`, `But`, `*`) | one step; the action is the line as written, keyword included |
| A step's doc string or data table | that step's test data |
| `Background:` steps | the preconditions, one line per step |
| Tags on the feature, rule and scenario | labels (without the `@`) |
| `@priority:high` (on the scenario) | the priority; otherwise new cases are `MEDIUM` |
| `@tm:BDD-17` (on the scenario) | which test case this scenario *is* (see below) |
| `Examples:` rows | [parameter sets](#parameter-sets), named `Example #1`, `Example #2`… (or after the Examples block's name); `<name>` becomes `{name}` |

Imported scenarios are `ACTIVE` — they are already executable specs — or `IN_REVIEW` in a project
that [requires review](#review-and-approval). Other languages work: start the file with
`# language: de` and write `Funktionalität`, `Szenario`, `Angenommen` and so on.

Upload one `.feature` file or a `.zip` of them (other files in the ZIP are ignored). Up to 500
scenarios per upload.

**The `@tm:` tag.** An untagged scenario always creates a new case. A scenario tagged with a case's
key, `@tm:BDD-17`, *updates* that case instead: title, description, preconditions, labels, steps and
parameter sets are replaced by what the file says, and a new [version](#version-history) is recorded.
If nothing changed, nothing is written and no version is added, so re-importing the same file is
harmless. The dry run shows how many cases would be **created**, **updated** and left **unchanged**.

- A `@tm:` key that is not a case of this project fails that scenario: it is a typo or a file from
  another project, and creating a lookalike would hide that.
- Cases already in a feature's folder that no scenario in the upload claims are listed as warnings.
  They are never deleted; removing a test case stays your decision.
- A tagged case is updated where it is; it is not moved if the scenario is now in another feature.

**Recommended flow**

1. Import your existing `.feature` files once.
2. Export them again (**Export → Gherkin**). Every scenario now carries its `@tm:` tag.
3. Commit the exported files to your repository, replacing the originals.
4. When scenarios change, import the changed files again: tagged scenarios update their cases.
5. Post the Cucumber JSON report from CI (see [Importing a JUnit or Cucumber report](#importing-a-junit-or-cucumber-report)).
   Results go to the case with the scenario's `@tm:` tag, even if the scenario was renamed.

Step 4 is done in the app, by someone with at least the Tester role: API keys are only accepted on
the `/api/external/…` CI endpoints, which do not include the import.

**Exporting.** Exporting a folder gives one file for it, with its sub-folders as `Rule:` blocks.
Exporting the project gives one file per top-level folder, plus `Unfiled.feature` for cases in no
folder, downloaded together as a ZIP. Each scenario starts with its `@tm:` tag, then
`@priority:` (unless `MEDIUM`), then its labels. Parameterised cases become `Scenario Outline`s. When
every case in a file shares its preconditions and they are written as steps, they become a
`Background:`. A suite written entirely in German exports in German.

**What does not round-trip.** Gherkin has no place for some of what a test case holds, and a file
has details the tool does not keep:

- A step written without a Gherkin keyword ("Open the login page") exports as `* Open the login page`.
  Re-importing that file changes the step to include the `*`.
- **Expected results** export as a `# expected: …` comment under the step. Comments are not imported,
  so re-importing removes the expected results.
- Preconditions that are not steps export as a `# preconditions:` comment, and are removed on re-import.
- Folders nested deeper than feature → rule are exported into their rule; the file notes this.
- A label with spaces is written with dashes (`@needs-review`), noted in a comment.
- A case using [shared steps](#shared-steps) exports with their steps written out. Re-importing the
  unchanged file leaves the case alone; if the scenario changed, the file's steps replace the shared
  step with local steps, and the import warns you.
- Not kept from your files: comments, the feature's own description, tag order, blank-line layout,
  and tags on `Examples:` blocks. The import lists what it dropped as warnings.

**Editing a case as Gherkin.** On a test case's **Steps** tab, **Edit as Gherkin** shows the case as
one scenario in a plain text box. Edit it and choose **Apply to steps**: the title, description,
labels and steps are replaced by what you wrote. Nothing is saved until you save the form. Expected
results are removed (you are warned first), an `Examples:` table is not saved here (manage
[parameter sets](#parameter-sets) on the case page instead), and text that does not parse shows the
line and what was expected. Text in another language needs its `# language:` line first.

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

It also answers "will we finish by the target date?": the remaining estimated effort across the
plan's runs (aborted runs excluded), and a **burn-down** of remaining effort per day against a
straight line to zero on the target date. New runs added later show as a step up. The chart uses
each case's current estimate, so correcting an estimate also moves past days. Results executed
before execution times were recorded (older than this version) are left out, and the chart says
from when its history is complete.

### Release gate

A plan can say what "ready to ship" means. Under **Release gate** on the plan form, set any of:

| Threshold | Met when |
|---|---|
| **Minimum effective pass rate** | at least this percentage of the plan's latest results passed |
| **Maximum open critical bugs** | the project has at most this many Open or In Progress bugs of priority Critical |
| **Minimum requirement coverage** | at least this percentage of the project's requirements is [covered](#9-requirements-and-traceability) |
| **Maximum flaky tests** | at most this many [flaky](#flaky-test-detection) cases were executed in this plan |

Leave a field empty to not use it. A value exactly at the threshold meets it.

The plan page then shows **Release readiness**: **GO** when every threshold you set is met, **NO GO**
when any is not (with the failing ones marked), or **No criteria** without a gate. It is worked out
each time you look; nothing is stored.

The *effective* pass rate counts each test case once, by its latest result across the plan's runs,
so a failure that was retested and passed counts as a pass. Pending results count as not passed,
and aborted runs are ignored. The plan's own pass rate above it counts every result, so the two can
differ. A parameterized case counts once per parameter set. If a case ran in several environments,
the latest run wins. Coverage shows *Not applicable* when the project has no requirements, and does
not block a GO.

A pipeline can check the gate before deploying; see
[Checking the release gate](#checking-the-release-gate).

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
| Environment | Pick one from the project's list, or type a new name to add it (see [Environments](#environments)) |
| Test Plan | Optional; attaches this run to a milestone |
| Executor | Who is expected to run it; can be left unassigned |
| Test cases | Pick them individually, filter by folder, or search |

Cases with parameter sets expand to one result per set.

**Run on multiple environments** swaps the environment field for a multi-select and creates one
run per environment (up to 20) in one go: same cases, plan and executor, each named
`<name> · <environment>`, and each with its own key. You land on the run list filtered to them.

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

**Time is recorded for you.** Opening a case starts a timer (shown under **Duration**); setting the
case's outcome saves the time with it, keyboard shortcuts included. If you went for lunch, type
the real minutes into **Duration**, before or after setting the outcome. Correcting an outcome
later keeps the time first recorded, and anything over 8 hours asks before saving. Outcomes set in
bulk record no time.

The run header shows the remaining estimated effort, how many pending cases have no estimate (so a
small number isn't mistaken for "almost done"), and the time spent so far.

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

**Abort** stops a run early — use it when the environment collapsed, not when tests failed. It
asks for a reason, which is shown on the run and kept in its history.

**Reopen** on a completed or aborted run requires a written reason, which is recorded against the
run. This is intentional friction: reopening changes history, so the record says why. A run never
goes back to *Planned*, and a closed run is reopened before it can be closed the other way.

**Clone** copies a run's case selection into a fresh run with a new name and environment, without
copying results. This is how you re-test the same set next release. The dialog starts with the
source run's environment; clear it for a run with none.

### Comparing runs

"What broke since last time?" **Compare with…** on a run (or the ⇄ icon in the run list) opens
that run beside an earlier one. The earlier run is picked for you: the previous run with the same
name (CI runs from one workflow share a name), else the previous run of the same test plan and
environment. Aborted runs are never picked. Choose another under **Compare against** at any time.

Every test case, and every parameter set of a parameterized case, lands in one group:

| Group | From → to |
|---|---|
| **Newly failing** | passed → failed or blocked |
| **Fixed** | failed or blocked → passed |
| **Still failing** | failed → failed, or blocked → blocked |
| **Added** / **Removed** | only in the newer / only in the earlier run |
| **Other change** | anything else, including blocked → failed (the environment problem went away and a real failure appeared) |
| **Unchanged** | the same result in both; counted, and listed with **Show unchanged** |

*case edited* marks a result that ran different wording of the test case than the other one did.
Each status links to that result in its run. The page address includes both runs, so you can share
a comparison. If the run you compare against is newer, the page says so and offers **Swap**.

### Environments

Each project keeps a list of environments, such as `Staging`, `Production` or `Chrome · Staging`.
Runs and bug reports point at an entry in that list instead of storing free text, which is what
makes "has checkout passed on staging?" answerable:

- The **run list** filters by environment. **My test runs** filters by environment name across
  projects.
- A **test case** shows its latest executed result in each environment (by run time, so late CI
  uploads land in the right place). Runs without an environment are listed as *Unspecified*.
- A **test plan** can group its runs by environment, with a pass rate for each.

Names are matched ignoring case and surrounding spaces, so `Staging`, `staging` and ` staging `
are one environment. A name nobody has used yet is **added automatically**, whether it comes from
the UI, a CI upload, the external runs API or an MCP agent. Spelling variants (`stage`, `stg`) are
not guessed and become separate entries until someone merges them.

**Project settings → Environments** (project Admin) lets you:

- drag environments into the order pickers show them;
- **rename** one, which relabels every run and bug report using it;
- **archive** one, which hides it from pickers but keeps it on past runs and in filters (using an
  archived name again, e.g. from CI, reactivates it);
- **merge** one into another, which moves its runs and bug reports over and deletes it (this is how
  to clean up duplicates);
- **delete** one that nothing uses. An environment in use can only be archived or merged.

Existing projects were migrated automatically: each distinct name became an environment, with case
and whitespace variants folded together. Where variants differed only in case, the migration kept
one spelling (often the uppercase one), so you may want to rename a few.

### Allure reports

If your automation produces an [Allure](https://allurereport.org/) report, zip the generated
`allure-report` directory and attach it with **Upload Allure Report**. The ZIP must contain
`index.html`. A wrapping directory is detected and stripped automatically.

Once uploaded the button becomes **Allure Report** and opens the full report in a new tab, with
CSS, JavaScript, images and fonts served correctly.

Reports can also be uploaded straight from CI — see [CI/CD integration](#15-cicd-integration).

### Triggering automated suites

If a system administrator has assigned build-server workflows to your project (see
[Build servers](#build-servers)), an **Automation** panel appears at the top of the test-runs
page. Each assigned workflow has a **Run** button; the dialog is pre-filled with the workflow's
default branch and parameters, both editable per run. Triggering needs the Tester role.

The panel below the buttons lists recent pipeline runs. While a pipeline is in flight its status
chip updates live — Triggered → Pending → Running → Success / Failed — without reloading the
page; a refresh button forces an immediate status check. Each row links to the pipeline on the
build server and, once the pipeline has reported its results back, to the **test run** it created
(including its Allure report, if the pipeline uploaded one).

A pipeline that finishes green but never reports results shows a "no results received" hint —
that means the workflow ran but is missing the report-back step described in
[CI/CD integration](#15-cicd-integration).

---


### Exploratory sessions

For testing that follows a mission rather than a script ("spend an hour attacking checkout with
odd currencies"): **Exploratory sessions** on the project page (tester role to create).

A session has a **charter** (what to explore and why), a **time box** of 5 to 480 minutes, and
optionally a test plan, an [environment](#environments) and a tester. Its key looks like
`SHOP-Session-3`.

1. **Start session** starts the clock. The bar shows time spent against the time box. It turns
   amber past 100% but never stops you.
2. Log what you notice in the one-line input. **Enter** saves each note straight away, so closing
   the tab loses nothing. **Alt+1..4** picks the type (Note, Bug, Question, Idea), and pasting an
   image attaches it as the note's screenshot (PNG, JPEG, GIF or WebP, one per note).
3. On a **Bug** note, **File bug** opens a bug report pre-filled from the note. It is linked to the
   session and takes the session's environment.
4. **Complete** (or **Abort**) ends the session with a debrief summary, which you can edit later.
   Late notes are accepted for 24 hours after completion.

Only a note's author or a project admin can edit or delete it. Sessions filed under a test plan
appear on the plan page in their own card. They never count towards the plan's runs or pass
rate. **My test runs** lists the sessions assigned to you that are planned or running.

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

**Settings → Single sign-on** (system administrators only).

Pick a **protocol** first, because it decides what the rest of the form means:

- **OpenID Connect** — almost everything. Keycloak, Authentik, Auth0, Okta, Entra ID, Google,
  **GitLab** and **Forgejo/Gitea** are all OIDC providers and need no special handling.
- **GitHub** — the exception. GitHub's user sign-in is plain OAuth 2.0: no ID token, and no
  `/.well-known/openid-configuration` to discover. Identity is fetched from its API instead.

The protocol cannot be changed after the provider is saved. An OIDC subject and a GitHub numeric
user id are different kinds of identifier, and reinterpreting already-linked accounts under the
other one could match a stored subject to a different person.

Per provider you configure:

| Field | Notes |
|---|---|
| Display name | The label on the sign-in button |
| Slug | Lowercase letters, digits and hyphens. Appears in the callback URL and cannot be changed afterwards, because your provider already knows it |
| Issuer URL / GitHub URL | For OIDC, the discovery root. For GitHub, `https://github.com` or your GitHub Enterprise Server address. Must be HTTPS; private and loopback addresses are rejected |
| Client ID / Client secret | From your provider. The secret is stored encrypted and requires `APP_ENCRYPTION_KEY` on the server. On edit, leaving it blank keeps the stored one |
| Scopes | OIDC defaults to `openid,profile,email` and always includes `openid`. GitHub uses `read:user,user:email` |
| Email / name claim | OIDC only; defaults `email` and `name`. GitHub has no claims, so the fields are hidden |
| Admin claim + value | OIDC only. A match grants the system administrator flag; group and array claims match on membership. GitHub returns no claims, so grant system administrator on the user instead |

**Redirect URI** — the screen shows the exact URL to register with your provider. It has the shape
`https://your-host/login/oauth2/code/<slug>`.

**Test connection** fetches the discovery document, or for GitHub asks the API whether it is
reachable. Recent errors are kept on the provider row.

#### Setting up GitHub

1. On GitHub: **Settings → Developer settings → OAuth Apps → New OAuth App**. (An OAuth App, not
   a GitHub App — a GitHub App authenticates an installation, not a person.)
2. Set **Authorization callback URL** to the redirect URI shown on the provider form. It must
   match exactly.
3. Copy the Client ID, generate a client secret, and paste both into the provider form with
   protocol **GitHub** and URL `https://github.com`.

The account's **primary verified email** is what gets used. A profile email that GitHub has not
verified is deliberately ignored — anyone can type any address into their public profile, so
honouring it would let a stranger have an account created under someone else's address. A user
with no verified email cannot sign in; they must verify one on GitHub first.

The display name falls back to the GitHub login when the account has no name set.

#### Setting up GitLab or Forgejo

Both are full OIDC providers, so use protocol **OpenID Connect**:

- **GitLab** — issuer `https://gitlab.com`, or your own instance's root URL. Create the client
  under **Applications** with scopes `openid profile email` and the redirect URI from the form.
- **Forgejo / Gitea** — issuer is the instance root. Create the client under **Site
  administration → Applications → OAuth2 Applications**.

Self-hosted instances have one recurring trap: **the discovery document must advertise the same
public URL you typed into the issuer field.** Both GitLab and Forgejo build that document from
their configured root URL, so an instance still set to an internal address publishes endpoints
nobody outside can reach, and the login is rejected before it starts with a mismatched-issuer
error. Check with:

```bash
curl -s https://your-instance/.well-known/openid-configuration | head -5
```

If `issuer` and the endpoints do not read as your public HTTPS URL, fix it at the source —
`ROOT_URL` in Forgejo's `app.ini`, or `external_url` in GitLab's `gitlab.rb` — rather than
pointing this app at the internal address. Endpoints on a private address only work from inside
the network, and over plain HTTP the authorization code and token cross the wire in clear text.

An instance genuinely only reachable on a private network needs `SSO_ALLOW_PRIVATE_ISSUERS=true`,
which relaxes an SSRF guard. Set it only if that is really the situation.

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

Runs imported from CI (JUnit XML, Cucumber JSON, or the external runs API) send `RUN_COMPLETED`, plus
`RUN_FAILED` when a result failed, exactly like runs completed in the UI. They never send one
`TEST_FAILED` per imported result. Run events include `environment` and up to ten `failedTests`
(`key`, `title`), and `TEST_FAILED` includes `testCaseKey`.

#### Chat notifications

Pick a **Format** to post straight into a chat channel instead of running a relay service:

| Format | Works with | Setup |
|---|---|---|
| Generic JSON | Your own receiver | The signed JSON envelope above |
| Slack · Mattermost · Rocket.Chat | Slack, Mattermost, Rocket.Chat, Discord | Slack: create an app with *Incoming Webhooks* and add one to the channel. Mattermost: *Integrations → Incoming Webhooks*. Rocket.Chat: *Administration → Integrations → Incoming*. Discord: channel *Integrations → Webhooks*, then append `/slack` to the URL |
| Microsoft Teams | Teams | In the channel, *Workflows → "Post to a channel when a webhook request is received"* and copy the URL. The retired Office 365 connectors are not supported |

A chat message shows the run key and name, the environment, passed/failed/blocked/skipped counts and
the first five failed tests, coloured by outcome. New bug reports post their title, priority and
status. With `PUBLIC_BASE_URL` set, each message links back to the run or bug report; without it,
messages have no link. **Send test** posts a real test message.

Differences from generic webhooks:

- **No `TEST_FAILED`.** One message per failed test would flood the channel and hit the vendor's
  rate limit; run messages list the failed tests instead. A chat webhook subscribed to both run
  completed and run failed posts once per run, not twice.
- **No secret to enter.** Chat services ignore the signature, so one is generated.
- **The URL is masked** once saved (`https://hooks.slack.com/…WXYZ`), because the URL is the
  credential. Use **Replace URL** to change it; to recover it, re-issue it in the chat tool.
- **https only**, even if plain http is allowed for testing, unless private targets are allowed (a
  Mattermost on your LAN).

Names and titles are escaped, so a run called `<!channel>` or `@here` can't ping a channel. Webhook
URLs, secrets and delivery bodies are stored unencrypted in the database, so treat a database dump
as sensitive.

### Issue tracker

**Issue Tracker** on a project (project Admin) connects it to **GitLab**, **Forgejo/Gitea**,
**GitHub** or **Jira**.

Provide the instance URL (HTTPS; private addresses rejected by default), the project reference,
and an API token. The token is stored encrypted and requires `APP_ENCRYPTION_KEY` on the server —
without it, saving is refused rather than storing the token in plain text. **Test connection**
verifies it before you save.

| Tracker | Instance URL | Project reference | Token |
|---|---|---|---|
| GitLab | `https://gitlab.com` or your instance | `group/project` or the numeric id | project or personal access token, scope `api` |
| Forgejo / Gitea | the instance root | `owner/repository` | access token with read/write on issues |
| GitHub | `https://github.com`, or your Enterprise Server's root | `owner/repository` | fine-grained token with *Issues: Read and write* on the repository, or a classic token with `repo` |
| Jira Cloud | `https://your-site.atlassian.net` | project key, e.g. `WEB` | API token from id.atlassian.com **plus the account email** it belongs to |
| Jira Data Center | your instance root | project key, e.g. `WEB` | personal access token from your profile (8.14+) |

Notes on the newer two:

- **GitHub.** Pull requests are never offered or linked, although GitHub's API lists them among
  issues. Typing a number (`123` or `#123`) fetches that issue directly. **Test connection** fails
  for a repository whose issues are switched off, which is common on forks.
- **Jira — issue type.** New issues are filed as a **Bug**. To file something else, put it after the
  key: `WEB:Task`. **Test connection** checks that the project actually has that issue type and
  lists the ones it offers if not.
- **Jira — required fields.** If the project's create screen requires fields beyond summary and
  description (Components, Fix Version, …), filing fails and names them. Create the issue in Jira
  and **link** it instead; linking always works.
- **Jira — status.** Open/Closed follows Jira's status *category* (To Do and In Progress are open,
  Done is closed), so it is right whatever your workflow calls its statuses.
- **Jira — account email.** The form asks for it only when the URL is an `atlassian.net` site. It
  is not a secret and is shown again when you reopen the settings.

Once connected, a tester working through a failed result can:

- search the tracker and **link** an existing issue
- **file a new issue** in one click, with title and body pre-filled from the failed result
- see linked issues with an Open / Closed / Unknown badge, refreshed periodically and on demand
- **unlink** an issue

Disconnecting the tracker keeps issues already linked to results; they stay clickable.

### Build servers

**Settings → Build servers** (system administrators) registers CI servers **once, globally** —
the credential lives in one place, and projects never see it. Supported providers: **GitLab CI**,
**GitHub Actions**, **Forgejo/Gitea Actions**, **Woodpecker CI** and **Jenkins**.

For each server provide a display name, the server URL (HTTPS; private addresses rejected by
default — see `BUILDSERVER_ALLOW_PRIVATE_TARGETS`), and an API token. Tokens are stored encrypted
and require `APP_ENCRYPTION_KEY`, like issue-tracker tokens. **Test connection** verifies the
credential before you rely on it, and the last provider error is shown on the server card.

Provider notes:

| Provider | Server URL | Token | Repository / job reference |
|---|---|---|---|
| GitLab CI | Instance root, e.g. `https://gitlab.com` | Personal/project access token, `api` scope | `group/project` or numeric id |
| GitHub Actions | API root: `https://api.github.com` (or `…/api/v3` for Enterprise Server) | Token with Actions read/write | `owner/repo` |
| Forgejo / Gitea | Instance root, e.g. `https://codeberg.org` | Access token with repository scope | `owner/repo` |
| Woodpecker | Instance root | Personal token from user settings | Numeric repo id (use discovery) |
| Jenkins | Instance root | **`user:apiToken`** — both halves, colon-separated | Job path, e.g. `folder/jobname` |

**Workflows.** On each server the admin defines what can be triggered: a display name testers
will see, the repository/job reference, for GitHub/Forgejo the workflow file (e.g. `tests.yml`),
a default branch, and default parameters (one `KEY=value` per line). The **Discover** button asks
the server what exists — workflow files on GitHub/Forgejo, jobs on Jenkins, repositories on
Woodpecker, branches on GitLab — so most workflows are a pick, not a form. Manual entry always
works when discovery has nothing to offer.

**Project assignment.** Each workflow has a project multi-select. Assignment is the entire
authorization: a project's members see and trigger **only** the workflows assigned to that
project, and the project-side API never exposes the server URL, the repository reference or any
credential. Unassigning a workflow (or deleting a server) leaves past pipeline runs readable in
the projects' history.

For what the triggered pipeline must do to report its results back, see
[CI/CD integration](#15-cicd-integration).

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
      { "testCaseKey": "TES-1", "status": "PASSED", "durationMs": 1830 },
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
`stepResults` is omitted, every step of the case takes the result's status. `durationMs` is
optional and counts towards the run's and plan's time spent.

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

A Cucumber scenario tagged `@tm:<case key>` (see [BDD / Gherkin](#bdd--gherkin)) is recorded on
that case, even if the scenario has been renamed since; untagged scenarios are matched by the title
`<Feature> - <Scenario>` as before. A key the project does not know falls back to the title and
starts the result's comment with `Unknown test case key tm:<key>`. Each example row of a
`Scenario Outline` is recorded on the case's parameter sets in order: the first row on the first
set, and so on. Rows beyond the last set are recorded without one, with a comment saying so.
Durations are kept: JUnit's `time` attribute and the sum of a Cucumber scenario's step durations. A
missing or unreadable `time` just means no duration; it never fails the upload.
`environment` is matched against the project's [environments](#environments) and added if new;
the run's `environment` in the response is the canonical name, which may differ in case from
what you sent.

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

### Reporting back from a triggered pipeline

When a tester triggers a workflow from the Automation panel (see
[Build servers](#build-servers)), the trigger injects these non-secret variables into the
pipeline — as CI variables on GitLab/Woodpecker/Jenkins, as `workflow_dispatch` inputs on
GitHub/Forgejo:

| Variable | Content |
|---|---|
| `TM_PIPELINE_RUN_ID` | Correlation id for this specific trigger |
| `TM_PROJECT_KEY` | The project key, e.g. `TES` |
| `TM_BASE_URL` | This instance's public URL — only when `PUBLIC_BASE_URL` is configured |
| `TM_ENVIRONMENT` | The environment the trigger named, if any. A run reported back with `pipelineRunId` and no `environment` parameter gets this environment. Currently set only through the API (`environmentId` in the trigger request body); the Automation panel doesn't offer it yet |

The API key is **not** sent to the build server. Configure it as a CI-side secret once (e.g.
`TM_API_KEY`), like any other credential your pipeline uses.

To report results, the workflow posts to the normal ingestion endpoints and appends
`?pipelineRunId=$TM_PIPELINE_RUN_ID`. That links the created test run to the pipeline run in the
Automation panel, and — when no `runName` is given — names the run after the workflow:

```bash
curl -f -X POST \
  -H "X-API-Key: $TM_API_KEY" \
  -H "Content-Type: application/xml" \
  --data-binary @target/surefire-reports/report.xml \
  "$TM_BASE_URL/api/external/projects/$TM_PROJECT_KEY/test-runs/junit?pipelineRunId=$TM_PIPELINE_RUN_ID"
```

The `pipelineRunId` parameter is accepted by all three submission endpoints (native JSON, JUnit,
Cucumber). An Allure upload needs no extra parameter — attach it to the run key returned by the
submission, as above. A `pipelineRunId` from another project is rejected as `404`; if the same id
is reported twice, the first report keeps the link.

**GitHub and Forgejo only:** the dispatch API returns no run id, so the poller has to find the
run afterwards. Declare the inputs and set `run-name` to make that exact:

```yaml
run-name: TM ${{ inputs.TM_PIPELINE_RUN_ID }}
on:
  workflow_dispatch:
    inputs:
      TM_PIPELINE_RUN_ID: { required: false }
      TM_PROJECT_KEY: { required: false }
      TM_BASE_URL: { required: false }
      TM_ENVIRONMENT: { required: false }
```

Without this the trigger still works (a dispatch rejected for undeclared inputs is retried
without them), but run matching falls back to timing, and the workflow has no way to read its
`TM_PIPELINE_RUN_ID` — so results arrive unlinked. GitLab, Woodpecker and Jenkins need nothing:
they accept arbitrary variables and expose them as environment variables.

### Checking the release gate

A deploy job can ask whether a test plan is ready and stop if it is not. An API key with the
**Viewer** role is enough.

```bash
curl --fail-with-body -H "X-API-Key: $TM_API_KEY" \
  "http://localhost:8089/api/external/projects/TES/test-plans/$PLAN_ID/readiness?enforce=true"
```

With `enforce=true` the answer is `200` only for **GO**. **NO GO** and **No criteria** are
`412 Precondition Failed`, so `curl --fail-with-body` exits non-zero and prints the reason: each
criterion with its actual value, threshold and outcome. *No criteria* fails too, because a
pipeline that asks for a gate and finds none has been misconfigured. Without `enforce` the verdict
is always returned as `200`, for dashboards and scripts that decide for themselves.

Plans have no key, so `$PLAN_ID` is the plan's UUID (in its page URL).

### Status codes

| Code | Meaning |
|---|---|
| `201` | Created |
| `412` | Release gate not met (`enforce=true`): NO GO or no criteria |
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
6. Optional: **Settings → Build servers** — register CI servers, define workflows, assign them to
   projects; set `PUBLIC_BASE_URL` so triggered pipelines know where to report back

### Configuration

Everything is environment variables on the backend container. Required:

| Variable | Notes |
|---|---|
| `JWT_SECRET` | No default — startup fails without it. `openssl rand -base64 48`. Changing it signs everyone out |
| `DB_PASSWORD` | Used by both the database and the app. See the note above about existing volumes |

Required only for certain features:

| Variable | Needed for |
|---|---|
| `APP_ENCRYPTION_KEY` | Storing issue-tracker tokens, build-server tokens and OIDC client secrets. Base64 AES key, `openssl rand -base64 32`. Without it those features refuse to save a secret rather than storing it in plain text. **Changing it makes stored secrets undecryptable** — you must re-enter them |
| `PUBLIC_BASE_URL` | This instance's public URL. Used for links in chat notifications, and injected into triggered pipelines as `TM_BASE_URL` so a workflow can report results back without hardcoding the address. Unset = no links in chat messages, and the variable is omitted |
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
| `APP_ATTACHMENTS_MAX_PER_CASE` | `20` files per test case |
| `APP_ATTACHMENTS_MAX_PROJECT_BYTES` | `524288000` (500 MB) of attachments per project; `0` = unlimited |
| `MAIL_ENABLED` / `MAIL_FROM` | `false` / `no-reply@testmanagement.local` |
| `SSO_CALLBACK_URL` | `/login/callback` — set to your public frontend URL if the UI is not served from the API's origin |
| `SSO_ALLOW_PRIVATE_ISSUERS` | `false` |
| `ISSUE_TRACKER_ALLOW_PRIVATE_TARGETS` | `false` |
| `WEBHOOKS_ALLOW_PRIVATE_TARGETS` | `false` |
| `BUILDSERVER_ALLOW_PRIVATE_TARGETS` | `false` |
| `FLAKY_AUTO_LABEL` | `false` |
| `APP_VERSION` | `dev` |

The four `ALLOW_PRIVATE_*` flags are SSRF guards. They stop an administrator — or anyone who has
compromised an admin account — pointing a webhook, tracker, build server or OIDC issuer at
something inside your network. Turn one on only when you genuinely need to reach an internal
host. A build server on your LAN is the most common legitimate reason to enable
`BUILDSERVER_ALLOW_PRIVATE_TARGETS`.

Some tunables are YAML-only and need a rebuilt image: server port, the 10 MB upload cap, page
sizes (50, max 200), the flaky window/threshold/minimum, issue-tracker timeouts and poll interval,
the build-server timeouts, poll interval (15 s) and run timeout (120 min), and the webhook retry
schedule.

### Backups

Everything lives in PostgreSQL, including uploaded screenshots, step images, test case attachments
and Allure reports. Attachments are usually what makes a dump large; the per-project quota (see
[Attachments](#attachments)) keeps them bounded. The
application container holds no state and can be recreated freely, so a database dump is a complete
backup. Two scripts in the repository take and restore one; run them from the checkout, next to
`docker-compose.yml` and your `.env`. Neither needs PostgreSQL installed on the host.

**Taking a backup** — the database must be running; the app may keep running:

```bash
./scripts/backup.sh                 # into ./backups
./scripts/backup.sh /srv/tm-backups # or anywhere else
```

Each backup is one file, `testmanagement-<date and time, UTC>-V<schema>.dump`. It only gets that name
once the dump has finished, so a failed or interrupted backup never leaves a file that looks usable.

**Back up your `.env` too, separately and just as carefully**: it is not in the dump. Losing
`APP_ENCRYPTION_KEY` means re-entering every stored issue-tracker token, build-server token and OIDC
secret; losing `JWT_SECRET` signs everyone out.

**Scheduling** — the script is cron-friendly: it prints only on success and exits non-zero on
failure. For a nightly backup that keeps two weeks:

```cron
30 2 * * *  cd /opt/testmanagement && ./scripts/backup.sh /srv/tm-backups && find /srv/tm-backups -name '*.dump' -mtime +14 -delete
```

Copying the backups off the machine is up to your usual tooling.

**Restoring** — a restore replaces the whole instance, every project. It only goes into an **empty**
database, and refuses otherwise rather than mixing old and new data:

```bash
docker compose stop testmanagement           # the app must not be running
docker compose down -v                       # DELETES the current database volume
docker compose up -d testmanagement-db       # a fresh, empty database
./scripts/restore.sh backups/testmanagement-20260919T020000Z-V61.dump
docker compose up -d                         # start the app
```

`restore.sh` checks, before writing anything, that the app is stopped, that the database is empty,
and that the backup is not from a **newer** version of the app than this checkout (reading the
version from the backup itself, not from its file name). A backup from an older version is fine: the
app upgrades it on start. The restore runs in one transaction, so a failure leaves the database
empty rather than half-filled.

Restoring onto a machine with a different `APP_ENCRYPTION_KEY` works, but stored tracker,
build-server and OIDC secrets can't be decrypted; the app says so where they are used, and you
re-enter them.

Note that PostgreSQL only applies its password when the data volume is first created. Changing it
later means changing it inside the database too, not just in `.env`.

### Upgrading

```bash
./scripts/backup.sh
git pull
docker compose up --build -d
```

Flyway applies database migrations automatically at startup. Migrations are not reversible, so take
a backup first: if an upgrade goes wrong, check out the previous version and
[restore](#backups) that backup.

### Health

`GET /actuator/health` for container probes and `GET /actuator/info` for the version. Nothing else
is exposed.

---

## 17. Troubleshooting

**The container exits with "JWT secret is not configured".** `JWT_SECRET` is unset or shorter than
32 characters. There is deliberately no fallback. If you deploy the compose file from somewhere
that does not read the repository's `.env` — a Portainer stack, for instance — the variable has to
be supplied by that environment instead.

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

**Saving an issue-tracker token, build-server token or SSO secret is refused.**
`APP_ENCRYPTION_KEY` is not set on the backend. The application will not store those secrets in
plain text.

**A webhook, build server or SSO issuer is rejected.** Private and loopback addresses are blocked
by default. See the `ALLOW_PRIVATE_*` variables above, and be sure you want to.

**A triggered pipeline finished but shows "no results received".** The workflow ran but never
posted results back. Check that it calls the JUnit/Cucumber/JSON endpoint with its
`TM_PIPELINE_RUN_ID` and a valid API key, and that `PUBLIC_BASE_URL` (or a hardcoded URL in the
workflow) actually reaches this instance from the build agent.

**A GitHub or Forgejo run stays "Triggered" and never picks up a run link.** The dispatch was
accepted but the run cannot be matched. Declare the `TM_*` inputs and the `run-name` line shown
in [CI/CD integration](#15-cicd-integration) — without declared inputs the server rejects them
and the trigger is retried bare, which loses exact correlation.

**Triggering fails immediately with a token error.** Use **Test connection** on the server in
**Settings → Build servers**; the card also shows the last provider error. For Jenkins remember
the credential is `user:apiToken`, both halves.

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
| Pipeline run | `TRIGGERED`, `PENDING`, `RUNNING`, `SUCCESS`, `FAILED`, `CANCELLED`, `TIMED_OUT`, `ERROR` |

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
