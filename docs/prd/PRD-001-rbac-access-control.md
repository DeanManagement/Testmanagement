# PRD-001 — Role-Based Access Control & Project Authorization

| | |
|---|---|
| **Status** | Implemented (2026-06-09) |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-09 |
| **Priority** | P0 — Security / correctness |
| **Target** | v1.3 |
| **Related** | REQUIREMENTS.md §3.7, §12.6 ("Explicit project role enforcement"); REVIEW_AND_PROPOSALS.md §3.6, §4.8 |

---

## 1. Summary

Testmanagement defines three project roles — `ADMIN`, `TESTER`, `VIEWER` — and a global `systemAdmin` flag, but **the project roles are almost entirely unenforced**. Worse than the documented "a Viewer can probably still write" gap, most project-scoped endpoints perform **no membership check at all**: any authenticated user who knows or guesses a project UUID can read, modify, and delete another project's data, including deleting the project itself.

This PRD specifies a single, consistent authorization layer that (a) confirms the caller is a member of the project they are acting on, and (b) enforces the minimum role required for each operation. The model is RBAC with a strict role hierarchy, applied declaratively so it is hard to forget on new endpoints.

This is a correctness and security fix, not a new capability. The role model itself does not change.

---

## 2. Problem & Current State

### 2.1 What the model promises

`ProjectRole` (`project/internal/entity/ProjectRole.java`):

```java
public enum ProjectRole { ADMIN, TESTER, VIEWER }
```

Per REQUIREMENTS.md §3.7:

- **Admin** — full CRUD on all project artifacts, manage members.
- **Tester** — create/edit test cases, execute runs.
- **Viewer** — read-only.
- **System Admin** (global) — manage users, API keys, system settings.

### 2.2 What the code actually enforces

Authorization is enforced in only **2 of 20** project controllers. A scan for any access check (`existsByUserIdAndProjectId`, `findByUserIdAndProjectId`, `getRole()`, `ForbiddenException`, `isSystemAdmin`, `@PreAuthorize`) across `project/internal/controller` and `project/internal/service`:

| Controller | Membership/role check? |
|---|---|
| `ProjectController` | Partial — `create` (system admin) and `toggleBugReports` (project admin) only. `findById`, `update`, `delete`, `dashboard` are **unguarded**. |
| `ProjectMemberController` | Yes — project-admin gated. |
| `ApiKeyController` | Yes — `@PreAuthorize("hasRole('ADMIN')")` (system admin). |
| `AllureReportController` | Partial — file-view path checks membership; upload path does not. |
| `TestCaseController` | **None** |
| `TestRunController` | **None** |
| `TestSuiteController` | **None** |
| `TestPlanController` | **None** |
| `TestCaseFolderController` | **None** |
| `BugReportController` | **None** (service only checks the `bugReportsEnabled` toggle) |
| `CommentController` | **None** at controller; service checks author/admin only for *delete* |
| `ScreenshotController` | **None** |
| `StepImageController` | **None** |
| `AuditController` | **None** |
| `WatcherController` | **None** |
| `MyQueueController`, `MyTestRunController`, `MyBugReportController`, `MyTestPlanController` | Self-scoped by `userId` (acceptable) |
| `ExternalTestRunController` | API-key auth via separate filter chain (acceptable) |

The service layer offers no backstop. For example `TestCaseService.create/update/delete` accept a `userId` purely to write the audit entry; they never verify the user is a member, let alone their role:

```java
// TestCaseService.update(...) — abridged
TestCase tc = testCaseRepository.findById(id)
        .filter(t -> t.getProject().getId().equals(projectId))   // confirms TC belongs to project
        .orElseThrow(() -> new ResourceNotFoundException("TestCase", id));
// ...mutates and saves. No membership check, no role check.
```

`ProjectService` is the same:

```java
public ProjectResponse findById(UUID id) { /* loads by id, no caller check */ }
public ProjectResponse update(UUID id, UpdateProjectRequest request, UUID userId) { /* mutates, no check */ }
public void delete(UUID id, UUID userId) { /* deletes, no check */ }
```

### 2.3 Concrete vulnerabilities

1. **Broken access control / IDOR (OWASP A01).** `GET/PUT/DELETE /api/projects/{id}` and `GET /api/projects/{id}/dashboard` have no caller-membership check. Any logged-in user can read, edit, or **delete any project** by ID. Project IDs are UUIDs (not trivially enumerable), but they leak via `GET /api/projects/search?key=...` — which is also unguarded — and in shared URLs, making this practically exploitable, not merely theoretical.
2. **Non-members can fully CRUD project content.** Test cases, runs, results, suites, plans, folders, bug reports, comments, screenshots, watchers — all writable by any authenticated user regardless of membership.
3. **Roles are decorative.** A `VIEWER` has the same effective write access as an `ADMIN` on every endpoint except member management and the bug-report toggle.
4. **Cross-project data exposure.** A user who is a member of Project A can read and mutate Project B's data.

### 2.4 Why it has gone unnoticed

The frontend only surfaces actions the user's role permits, so the UI *appears* to respect roles. Enforcement, however, must live on the server — the API is directly reachable. The `dev` profile also runs security permit-all, which masks the gap during local testing.

---

## 3. Goals & Non-Goals

### Goals

- Every project-scoped endpoint confirms the caller is a member of the project before any read or write.
- Every write enforces a minimum role using a strict hierarchy: `ADMIN > TESTER > VIEWER`.
- Enforcement is **declarative and centralized** so it cannot be silently omitted on a new endpoint.
- A global `systemAdmin` is implicitly authorized for everything (super-user).
- Unauthorized access returns the correct HTTP status: `403` for "authenticated but not allowed", `404` where leaking existence is itself a concern (see §6.3).
- Full test coverage proving Viewer→403, Tester→allowed/denied, Admin→allowed on representative endpoints.
- No behavior change for correctly-permissioned users; no role-model changes.

### Non-Goals

- No new roles, custom roles, or per-entity ACLs (explicitly resisted — see REVIEW §6). Three roles remain.
- No changes to system-admin user management, API-key auth, or the external CI endpoints (already gated).
- No OIDC / SSO work (separate v2 item).
- No frontend redesign — only minor alignment so the UI never offers an action the API will reject (§7).

---

## 4. Proposed Design

### 4.1 Role hierarchy

Introduce an ordered rank so "minimum role" comparisons are unambiguous. Lower rank = more privilege.

```java
public enum ProjectRole {
    ADMIN(0), TESTER(1), VIEWER(2);
    private final int rank;
    ProjectRole(int rank) { this.rank = rank; }
    public boolean satisfies(ProjectRole required) { return this.rank <= required.rank; }
}
```

`ADMIN.satisfies(TESTER)` → true; `VIEWER.satisfies(TESTER)` → false.

### 4.2 Central authorization service

A single component encapsulates all project-authorization logic so controllers, the aspect, and services share one implementation.

```java
@Service
@RequiredArgsConstructor
public class ProjectAccessService {

    private final ProjectMemberRepository projectMemberRepository;
    private final UserService userService;

    /** Caller must be a project member OR a system admin. Returns their effective role
        (ADMIN for system admins). Throws 403 otherwise. */
    public ProjectRole requireMember(UUID userId, UUID projectId) { ... }

    /** Caller must hold at least {@code minRole} on the project (system admin always passes). */
    public void requireRole(UUID userId, UUID projectId, ProjectRole minRole) {
        if (isSystemAdmin(userId)) return;
        ProjectRole role = projectMemberRepository.findByUserIdAndProjectId(userId, projectId)
                .map(ProjectMember::getRole)
                .orElseThrow(() -> new ForbiddenException("Not a member of this project"));
        if (!role.satisfies(minRole))
            throw new ForbiddenException("Requires " + minRole + " role on this project");
    }
}
```

The required repository methods already exist: `ProjectMemberRepository.existsByUserIdAndProjectId` and `findByUserIdAndProjectId`. `UserService` already exposes system-admin lookup (used by `ProjectController.requireProjectAdmin`).

### 4.3 Declarative enforcement via annotation + AOP aspect

Make the common case impossible to forget. Annotate controller methods; an aspect reads the `projectId` path variable and the authenticated user and calls `ProjectAccessService`.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequireProjectRole {
    ProjectRole value() default ProjectRole.VIEWER; // default: read access requires membership
}
```

```java
@Aspect @Component @RequiredArgsConstructor
public class ProjectRoleAspect {
    private final ProjectAccessService access;

    @Before("@annotation(requireProjectRole)")
    public void check(JoinPoint jp, RequireProjectRole requireProjectRole) {
        UUID userId = currentUserId();                 // from SecurityContext
        UUID projectId = projectIdArg(jp);             // resolve @PathVariable("projectId")
        access.requireRole(userId, projectId, requireProjectRole.value());
    }
}
```

Usage:

```java
@PostMapping
@RequireProjectRole(ProjectRole.TESTER)
public TestCaseResponse create(@PathVariable UUID projectId, ...) { ... }

@GetMapping
@RequireProjectRole // defaults to VIEWER — membership required to read
public List<TestCaseResponse> findAll(@PathVariable UUID projectId, ...) { ... }
```

**Why annotation + aspect rather than `@PreAuthorize` SpEL:** the logic (membership lookup + system-admin bypass + role rank) is shared and testable in one place; SpEL expressions sprinkled across 18 controllers are error-prone and hard to unit test. `@EnableMethodSecurity` is already on, so `@PreAuthorize("hasRole('ADMIN')")` continues to work for the *system-admin* endpoints (API keys, user management) — those are unchanged.

### 4.4 Service-layer backstop (defense in depth)

The aspect covers the standard `/{projectId}/...` endpoints. For the few endpoints where `projectId` is **not** in the path, the owning service resolves the project and calls `ProjectAccessService` directly:

- `ScreenshotController` (`/api/screenshots/{id}`) and `StepImageController` (`/api/step-images/{id}`): resolve project via the entity chain (screenshot → stepResult → testResult → testRun → project; stepImage → testStep → testCase → project) then `requireRole`. Upload requires `TESTER`; download requires `VIEWER`; delete requires `TESTER`.
- `AllureReportController` upload path: add the membership/role check that the view path already has.

Putting at least the critical mutations behind the service call (not only the aspect) means a future controller that forgets the annotation still fails closed for these object-reference endpoints.

### 4.5 Permission matrix

Minimum role required per operation. "Member (VIEWER)" means any member may read.

| Area | Read | Create / Update | Delete | Notes |
|---|---|---|---|---|
| Project (`update`, `delete`, settings) | Member | ADMIN | ADMIN | `delete` arguably system-admin only — see §10 open Q1 |
| Project dashboard | Member | — | — | Currently unguarded |
| Project search by key | Member of matched project | — | — | Filter results to caller's projects |
| Project members | Member | ADMIN | ADMIN | Already enforced |
| Test cases (+ bulk ops) | Member | TESTER | TESTER | |
| Test case folders | Member | TESTER | TESTER | |
| Test suites | Member | TESTER | TESTER | |
| Test runs / clone / reopen | Member | TESTER | TESTER | |
| Test results & step results | Member | TESTER | TESTER | The execution hot path |
| Test plans | Member | TESTER | TESTER | |
| Bug reports | Member | TESTER | TESTER | Plus existing `bugReportsEnabled` gate |
| Comments | Member | TESTER (create) | author or ADMIN | Keep existing author/admin delete rule |
| Screenshots / step images | Member | TESTER | TESTER | Resolved via entity chain (§4.4) |
| Activity / audit log | Member | — | — | Read-only |
| Watchers | self | self | self | A user manages only their own watches |
| API keys | — | system admin | system admin | Unchanged |
| External CI (`/api/external/**`) | API key | API key | — | Unchanged |

System admins bypass all project-role checks.

---

## 5. API & Behavioral Changes

- **No new endpoints, no changed routes, no changed request/response bodies.**
- Status codes for unauthorized callers change from incorrectly-`200` to `403 Forbidden` (or `404` per §6.3) with the standard `ErrorResponse` shape (`GlobalExceptionHandler` already maps `ForbiddenException` → 403).
- `GET /api/projects/search` now returns only projects the caller can access.
- Clients that were (incorrectly) relying on cross-project access will break — this is the intended fix. The frontend does not rely on it.

---

## 6. Edge Cases & Decisions

1. **Dev profile (security permit-all).** When `SecurityContext` has no authentication (dev), the aspect must not NPE. Behavior: if no authenticated principal is present, skip the check (dev/permit-all parity with the rest of the app). Document clearly that this is dev-only; the JWT chain always populates the principal in prod.
2. **System admin who is not a project member.** Always authorized (super-user). `requireMember` returns `ADMIN` effective role for them.
3. **Existence leaking (404 vs 403).** For a non-member hitting a valid project, returning `403` confirms the project exists. For the strictest posture, return `404` for non-members on read of projects they don't belong to (treat as "not found for you"), while using `403` for role-insufficient writes by actual members. Recommend: `404` on cross-project reads of the project resource, `403` on insufficient-role writes by members. (Open Q2.)
4. **Allure view via cookie session.** The existing cookie-based `allure_session` path already checks membership — keep it; just add the same check to upload.
5. **Bulk operations.** The `projectId` is in the path, so the aspect covers them; the existing "all IDs must belong to the project" validation stays.

---

## 7. Frontend Alignment (minor)

The UI already hides actions by role, so functional impact is small. Required:

- Ensure a `403`/`404` from these endpoints surfaces a clear, translated message (`auth.forbidden`, `auth.notProjectMember`) rather than a generic error — add keys to `en.json` / `de.json`.
- Confirm no component issues a write the user's role forbids (e.g., a Viewer landing on a deep-linked edit URL); guard the relevant routes/buttons by the member's role already present in project state.

No NgRx store shape changes.

---

## 8. Testing Strategy

This is the acceptance backbone. Add an authorization integration test per representative controller using `@SpringBootTest` + `MockMvc` (the project already has `ProjectControllerTest`, `TestPlanControllerTest`, etc. to extend).

For each protected controller assert, with seeded users:

- **Non-member** → `403`/`404` on read and write.
- **VIEWER** → `200` on read, `403` on every write (create/update/delete/bulk).
- **TESTER** → `200` on test-artifact writes; `403` on project update/delete and member management.
- **ADMIN** → `200` on project-scoped writes and member management; `403` only on system-admin-only endpoints.
- **System admin (non-member)** → `200` everywhere project-scoped.

Plus unit tests for `ProjectRole.satisfies(...)` and `ProjectAccessService.requireRole(...)` (member found / not found / system admin / insufficient role).

A single parameterized "authorization sweep" test that walks every `@RequireProjectRole`-annotated handler and asserts a Viewer is rejected on writes would catch future regressions cheaply.

---

## 9. Rollout & Migration

- **No DB migration.** Roles and memberships already exist; this only adds enforcement.
- **Creator-membership gap (must fix as part of this work):** `ProjectService.create` currently does **not** add the creator (or anyone) as a `ProjectMember` — it only saves the project and writes an audit entry. Today this is invisible because only system admins can create projects and they reach everything via the super-user bypass. Once enforcement lands, a project with zero members is unmanageable by any non-system-admin. Fix: on create, insert a `ProjectMember(creator, ADMIN)`.
- **Data backfill check:** verify every existing project has at least one `ADMIN` member; for any project with none, seed one (e.g., the creator from the audit log, or flag for manual assignment). Add a one-off startup validation log.
- **Sequencing to limit blast radius:**
  1. Land `ProjectRole.satisfies`, `ProjectAccessService`, the annotation, and the aspect (no annotations applied yet) + their unit tests.
  2. Annotate read endpoints (`VIEWER`) and run the full suite.
  3. Annotate write endpoints (`TESTER`/`ADMIN`) + service-layer backstops for screenshots/step-images/allure upload.
  4. Add the per-controller authorization integration tests; fix any gaps they reveal.
- **Smoke test in a staging deployment** with three real accounts (admin/tester/viewer) before release.
- Refresh REQUIREMENTS.md §3.7 to remove the "known gap" note once shipped.

---

## 10. Open Questions

1. **Project delete** — restrict to project `ADMIN`, or to **system admin only**? Deleting a project is destructive and rare; system-admin-only is safer. *Recommendation: system admin only (or project ADMIN with a typed confirmation).*
2. **404 vs 403 on cross-project reads** — adopt the "404 for non-members, 403 for insufficient role" split in §6.3, or use 403 uniformly for simplicity?
3. **Should `VIEWER` be allowed to add comments?** The matrix sets comment-create at `TESTER`. If viewers are expected to leave review feedback, lower it to `VIEWER`. *Recommendation: keep `TESTER`; revisit if users ask.*

---

## 10a. Implementation Decisions (as shipped)

- **Open Q1 (project delete):** restricted to project **ADMIN** (system admins bypass) via `@RequireProjectRole(ADMIN)`, matching the §4.5 matrix.
- **Open Q2 (404 vs 403):** **403 uniformly** for both non-members and insufficient-role members, for simplicity and a single client-handling path. `ForbiddenException` → 403 via the existing `GlobalExceptionHandler`.
- **Open Q3 (viewer comments):** kept at **TESTER** to create; comment delete additionally enforces the existing author-or-admin rule in `CommentService`.
- **Mechanism:** declarative `@RequireProjectRole` + `ProjectRoleAspect` (Spring AOP, `spring-boot-starter-aspectj`). The aspect resolves the project id from the configured path variable (`projectId`, or `id` on `ProjectController`). Because AOP aspects are not loaded by `@WebMvcTest` slices, existing controller slice tests are unaffected; enforcement is covered by `ProjectAuthorizationIntegrationTest` (full-context `@SpringBootTest`).
- **Backfill:** implemented as a non-destructive `ProjectMembershipValidationRunner` (logs projects with no `ADMIN` member) rather than a data migration, since the correct owner cannot be inferred safely in SQL.

## 11. Effort & Risk

- **Effort:** ~2 days for the core (enum, service, annotation, aspect, service backstops) + ~1–2 days for the per-controller authorization tests. Matches the REVIEW's `S` (≤2 days) estimate plus thorough testing.
- **Risk:** Low-to-medium. The main risk is *over*-restricting a legitimate flow (e.g., a permissioned action returning 403). Mitigated by the staged rollout and the integration-test matrix. The aspect is a single, well-tested choke point.
- **Risk of not doing it:** High. This is a live broken-access-control vulnerability allowing cross-tenant data destruction by any authenticated user.

---

## 12. Acceptance Criteria

- [ ] `ProjectRole` exposes an ordered hierarchy with a `satisfies(required)` check, unit-tested.
- [ ] `ProjectAccessService.requireRole` / `requireMember` implemented and unit-tested (member, non-member, insufficient role, system admin).
- [ ] `@RequireProjectRole` + aspect applied to every project-scoped controller method per the §4.5 matrix.
- [ ] Screenshot, step-image, and Allure-upload endpoints enforce access via the service backstop.
- [ ] `ProjectService.create` adds the creator as an `ADMIN` member; every existing project has ≥1 `ADMIN` member after backfill.
- [ ] `GET /api/projects/search` returns only the caller's accessible projects.
- [ ] Authorization integration tests pass for non-member / VIEWER / TESTER / ADMIN / system-admin on all protected controllers.
- [ ] A Viewer can no longer create/update/delete any project artifact (verified by test, replacing the curl check in REVIEW §3.6).
- [ ] A non-member receives 403/404 (not 200) on another project's resources.
- [ ] No regression for correctly-permissioned users (full existing suite green).
- [ ] REQUIREMENTS.md §3.7 "known gap" note removed.
