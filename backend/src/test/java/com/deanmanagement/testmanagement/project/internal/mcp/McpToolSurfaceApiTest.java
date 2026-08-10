package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.apiKey.ApiKeyCreatedResponse;
import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ApiKeyRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseFolderService;
import com.deanmanagement.testmanagement.project.internal.service.ProjectService;
import com.deanmanagement.testmanagement.project.internal.service.TestRunService;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PRD-025 §3.4. Exercises the tools as an authenticated API key would reach them — the security
 * context is set to the key's service user, exactly as {@code ApiKeyAuthenticationFilter} leaves
 * it, so the project scoping and role checks under test are the real ones.
 *
 * <p>Deliberately <strong>not</strong> {@code @Transactional}. Writes go through
 * {@link McpTestCaseWriter}, which commits each test case in its own transaction so that one bad
 * item in a bulk call cannot take the rest with it. Wrapping the test in a rolled-back transaction
 * would hide exactly that behaviour — the writer's transaction cannot see an uncommitted project,
 * so everything would fail with "Project not found". Data is torn down explicitly instead, and
 * each test gets its own project keys so a leak cannot bleed into a sibling.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {"app.mcp.enabled=true", "app.mcp.max-bulk-size=5"})
class McpToolSurfaceApiTest {

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ApiKeyRepository apiKeyRepository;
    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private ProjectDiscoveryTools discoveryTools;
    @Autowired
    private TestCaseTools testCaseTools;
    @Autowired
    private TestPlanningTools planningTools;
    @Autowired
    private McpToolInvocationRepository invocationRepository;
    @Autowired
    private McpWriteThrottle writeThrottle;
    @Autowired
    private TestCaseFolderService folderService;
    @Autowired
    private RequirementTools requirementTools;
    @Autowired
    private TestRunReadTools testRunReadTools;
    @Autowired
    private TestRunService testRunService;
    @Autowired
    private ProjectService projectService;

    private Project project;
    private Project otherProject;

    @BeforeEach
    void setUp() {
        writeThrottle.reset();
        // Unique keys per test: nothing is rolled back here, so two tests sharing a project key
        // would collide on the unique constraint.
        String suffix = Integer.toHexString(new java.util.Random().nextInt(0xFFFFF));
        project = newProject("MCP Project", "A" + suffix);
        otherProject = newProject("Other Project", "B" + suffix);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        // Through the service rather than the repository: it removes the project's executions
        // first, which a plain cascade cannot do (see ProjectDeletionWithResultsTest).
        projectService.delete(project.getId(), null);
        projectService.delete(otherProject.getId(), null);
    }

    private Project newProject(String name, String key) {
        Project p = new Project();
        p.setName(name);
        p.setKey(key);
        return projectRepository.save(p);
    }

    /** Authenticates as the key's service user, the way the API-key filter does. */
    private ApiKeyCreatedResponse authenticateAs(Project target, ProjectRole role, String name) {
        ApiKeyCreatedResponse created =
                apiKeyService.create(new CreateApiKeyRequest(name, target.getId(), role));
        UUID serviceUserId = apiKeyRepository.findById(created.id()).orElseThrow()
                .getServiceUser().getId();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(serviceUserId.toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_API_KEY"),
                                new SimpleGrantedAuthority("ROLE_USER"))));
        return created;
    }

    private McpDtos.CreatedTestCase createCase(String title) {
        return testCaseTools.createTestCase(title, Priority.MEDIUM, null, null, null, null,
                null, null, null);
    }

    // --- scoping ---------------------------------------------------------------------------

    @Test
    void getProject_returnsTheKeysOwnProjectAndRole() {
        authenticateAs(project, ProjectRole.TESTER, "agent");

        McpDtos.ProjectInfo info = discoveryTools.getProject();

        assertThat(info.key()).isEqualTo(project.getKey());
        assertThat(info.yourRole()).isEqualTo("TESTER");
        assertThat(info.testCaseCount()).isZero();
    }

    /**
     * The scoping guarantee: no tool takes a project id, so there is no parameter with which to
     * name another project — and an id belonging to one reports as not-found rather than
     * forbidden, which would confirm it exists (PRD-021 discipline).
     */
    @Test
    void aCaseInAnotherProjectIsNotFound() {
        authenticateAs(otherProject, ProjectRole.TESTER, "other-agent");
        McpDtos.CreatedTestCase foreign = createCase("Belongs to the other project");
        SecurityContextHolder.clearContext();

        authenticateAs(project, ProjectRole.TESTER, "agent");

        assertThatThrownBy(() -> testCaseTools.getTestCase(foreign.id().toString()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(testCaseTools.searchTestCases(null, null, null, null, null, null, null)
                .testCases())
                .noneMatch(tc -> tc.id().equals(foreign.id()));
    }

    /**
     * The subtler half of the isolation guarantee. {@code create_test_suite} takes caller-supplied
     * test case ids, and {@code findAllById} does not know about projects — so without a scoped
     * lookup an agent could attach project B's cases to its own suite and then read their titles
     * back out through {@code get_test_suite}. A cross-project write and a disclosure in one move.
     */
    @Test
    void aSuiteCannotBeBuiltFromAnotherProjectsTestCases() {
        authenticateAs(otherProject, ProjectRole.TESTER, "other-agent");
        McpDtos.CreatedTestCase foreign = createCase("Secret case in the other project");
        SecurityContextHolder.clearContext();

        authenticateAs(project, ProjectRole.TESTER, "agent");

        assertThatThrownBy(() -> planningTools.createTestSuite("Sneaky", null, Set.of(foreign.id())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aSuiteWithAnUnknownTestCaseIdIsRefusedRatherThanSilentlyEmpty() {
        authenticateAs(project, ProjectRole.TESTER, "agent");

        assertThatThrownBy(() ->
                planningTools.createTestSuite("Typo", null, Set.of(UUID.randomUUID())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- roles -----------------------------------------------------------------------------

    @Test
    void aViewerKeyCanReadButNotWrite() {
        authenticateAs(project, ProjectRole.VIEWER, "read-only-agent");

        assertThatCode(() -> discoveryTools.getProject()).doesNotThrowAnyException();
        assertThatThrownBy(() -> createCase("Should not exist"))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("VIEWER")
                .hasMessageContaining("TESTER");
    }

    @Test
    void anUnauthenticatedCallerIsRefused() {
        assertThatThrownBy(() -> discoveryTools.getProject())
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("API key");
    }

    // --- authoring -------------------------------------------------------------------------

    @Test
    void createTestCase_defaultsToDraftAndKeepsStepOrder() {
        authenticateAs(project, ProjectRole.TESTER, "agent");

        McpDtos.CreatedTestCase created = testCaseTools.createTestCase(
                "Login with valid credentials", Priority.HIGH, "Covers the happy path", "A user exists",
                null, Set.of("auth"),
                List.of(new McpDtos.Step("Open the login page", "The form is shown", null),
                        new McpDtos.Step("Submit valid credentials", "The dashboard opens", null)),
                null, null);

        assertThat(created.status()).isEqualTo(TestCaseStatus.DRAFT);
        assertThat(created.key()).startsWith(project.getKey() + "-");

        McpDtos.TestCaseDetail detail = testCaseTools.getTestCase(created.key());
        assertThat(detail.steps()).extracting(McpDtos.Step::action)
                .containsExactly("Open the login page", "Submit valid credentials");
        assertThat(detail.labels()).containsExactly("auth");
    }

    @Test
    void updateTestCase_leavesOmittedFieldsAlone() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase created = testCaseTools.createTestCase(
                "Original title", Priority.LOW, "Original description", "Original preconditions",
                null, null, null, null, null);

        testCaseTools.updateTestCase(created.key(), null, null, null, Priority.CRITICAL, null,
                null, null, null);

        McpDtos.TestCaseDetail detail = testCaseTools.getTestCase(created.key());
        assertThat(detail.priority()).isEqualTo(Priority.CRITICAL);
        assertThat(detail.title()).isEqualTo("Original title");
        assertThat(detail.description()).isEqualTo("Original description");
        assertThat(detail.preconditions()).isEqualTo("Original preconditions");
    }

    /** The documented way to clear a text field — omitting it means "leave alone", not "clear". */
    @Test
    void updateTestCase_anEmptyStringClearsATextField() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase created = testCaseTools.createTestCase("Has a description",
                Priority.LOW, "Something to remove", null, null, null, null, null, null);

        testCaseTools.updateTestCase(created.key(), null, "", null, null, null, null, null, null);

        assertThat(testCaseTools.getTestCase(created.key()).description()).isEmpty();
    }

    @Test
    void updateTestCase_rejectsABlankTitle() {
        // Bean validation on the DTO does not run on this path by itself — the tools build the
        // records by hand and call the services directly, bypassing the controllers' @Valid.
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase created = createCase("A real title");

        assertThatThrownBy(() ->
                testCaseTools.updateTestCase(created.key(), "  ", null, null, null, null, null, null, null))
                .hasMessageContaining("title");
    }

    @Test
    void createTestCase_rejectsAnOversizedTitle() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        String tooLong = "x".repeat(300);

        assertThatThrownBy(() -> createCase(tooLong))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("title");
    }

    @Test
    void updateTestCase_canMoveTheCaseToAFolder() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase created = createCase("Filed in the wrong place");
        UUID folderId = folderService.create(project.getId(),
                new com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder
                        .CreateTestCaseFolderRequest("Authentication", null), null).id();

        testCaseTools.updateTestCase(created.key(), null, null, null, null, null, null, null, folderId);

        assertThat(testCaseTools.getTestCase(created.key()).folderId()).isEqualTo(folderId);
    }

    // --- duplicate guard -------------------------------------------------------------------

    @Test
    void createTestCase_refusesANearDuplicateTitleAndNamesTheExistingCase() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase first = createCase("Login with valid credentials");

        // Same title bar punctuation and casing — what a re-run of the same prompt produces.
        assertThatThrownBy(() -> createCase("login with valid credentials."))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining(first.key())
                .hasMessageContaining("allowDuplicateTitle");
    }

    @Test
    void createTestCase_allowDuplicateTitleOverridesTheGuard() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        createCase("Search returns results");

        McpDtos.CreatedTestCase second = testCaseTools.createTestCase("Search returns results",
                Priority.MEDIUM, null, null, null, null, null, null, true);

        assertThat(second.id()).isNotNull();
    }

    @Test
    void bulkCreate_reportsPerItemOutcomesIncludingDuplicatesWithinTheBatch() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        createCase("Already here");

        McpDtos.BulkResult result = testCaseTools.createTestCasesBulk(List.of(
                new TestCaseTools.BulkCase("Brand new one", Priority.LOW, null, null, null, null, null, null),
                new TestCaseTools.BulkCase("Already here", Priority.LOW, null, null, null, null, null, null),
                new TestCaseTools.BulkCase("Brand new one", Priority.LOW, null, null, null, null, null, null),
                new TestCaseTools.BulkCase(null, Priority.LOW, null, null, null, null, null, null)
        ), null);

        assertThat(result.created()).isEqualTo(1);
        // One collides with the database, one with an earlier item of the same batch.
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.results()).extracting(McpDtos.BulkItemResult::outcome)
                .containsExactly("CREATED", "SKIPPED", "SKIPPED", "ERROR");
    }

    @Test
    void bulkCreate_dryRunWritesNothing() {
        authenticateAs(project, ProjectRole.TESTER, "agent");

        McpDtos.BulkResult result = testCaseTools.createTestCasesBulk(List.of(
                new TestCaseTools.BulkCase("Would be created", Priority.LOW, null, null, null, null, null, null)
        ), true);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.created()).isEqualTo(1);
        assertThat(testCaseTools.searchTestCases(null, null, null, null, null, null, null)
                .totalElements()).isZero();
    }

    @Test
    void bulkCreate_refusesABatchOverTheConfiguredCap() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        List<TestCaseTools.BulkCase> tooMany = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> new TestCaseTools.BulkCase("Case " + i, Priority.LOW, null, null,
                        null, null, null, null))
                .toList();

        assertThatThrownBy(() -> testCaseTools.createTestCasesBulk(tooMany, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("At most 5 cases per call");
    }

    // --- suites and plans ------------------------------------------------------------------

    @Test
    void createSuiteAndPlan() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase testCase = createCase("A case for the suite");

        McpDtos.CreatedSuite suite = planningTools.createTestSuite("Regression", "Nightly",
                Set.of(testCase.id()));
        McpDtos.CreatedPlan plan = planningTools.createTestPlan("Release 2.2", "Sign-off",
                LocalDate.of(2026, 9, 1));

        assertThat(suite.testCaseCount()).isEqualTo(1);
        assertThat(planningTools.getTestSuite(suite.id()).testCases())
                .extracting(McpDtos.TestCaseRef::id).containsExactly(testCase.id());
        assertThat(planningTools.listTestPlans(null)).extracting(McpDtos.PlanSummary::id)
                .contains(plan.id());
        assertThat(planningTools.getTestPlan(plan.id()).targetDate())
                .isEqualTo(LocalDate.of(2026, 9, 1));
    }

    // --- folders ---------------------------------------------------------------------------

    @Test
    void createFolderAndFileCasesIntoIt() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase one = createCase("Erster Fall");
        McpDtos.CreatedTestCase two = createCase("Zweiter Fall");

        McpDtos.Folder parent = discoveryTools.createTestCaseFolder("Navigation", null);
        McpDtos.Folder child = discoveryTools.createTestCaseFolder("Hauptmenü", parent.id());

        McpDtos.MoveResult moved = discoveryTools.moveTestCasesToFolder(
                List.of(one.id(), two.id()), child.id());

        assertThat(moved.moved()).isEqualTo(2);
        assertThat(child.parentId()).isEqualTo(parent.id());
        assertThat(testCaseTools.getTestCase(one.key()).folderId()).isEqualTo(child.id());
        assertThat(discoveryTools.listTestCaseFolders())
                .singleElement()
                .satisfies(root -> {
                    assertThat(root.name()).isEqualTo("Navigation");
                    assertThat(root.children()).extracting(McpDtos.Folder::name)
                            .containsExactly("Hauptmenü");
                });
    }

    @Test
    void movingCasesWithNoFolderReturnsThemToTheRoot() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase testCase = createCase("Wandert zurück");
        McpDtos.Folder folder = discoveryTools.createTestCaseFolder("Zwischenablage", null);
        discoveryTools.moveTestCasesToFolder(List.of(testCase.id()), folder.id());

        discoveryTools.moveTestCasesToFolder(List.of(testCase.id()), null);

        assertThat(testCaseTools.getTestCase(testCase.key()).folderId()).isNull();
    }

    /** Same scoping rule as everywhere else: a foreign id is not-found, and nothing moves. */
    @Test
    void casesFromAnotherProjectCannotBeFiled() {
        authenticateAs(otherProject, ProjectRole.TESTER, "other-agent");
        McpDtos.CreatedTestCase foreign = createCase("Gehört dem anderen Projekt");
        SecurityContextHolder.clearContext();

        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase own = createCase("Eigener Fall");
        McpDtos.Folder folder = discoveryTools.createTestCaseFolder("Ziel", null);

        assertThatThrownBy(() -> discoveryTools.moveTestCasesToFolder(
                List.of(own.id(), foreign.id()), folder.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(testCaseTools.getTestCase(own.key()).folderId())
                .as("the whole move is refused, so the caller's own case stays put")
                .isNull();
    }

    @Test
    void aFolderFromAnotherProjectCannotBeUsedAsParent() {
        authenticateAs(otherProject, ProjectRole.TESTER, "other-agent");
        McpDtos.Folder foreign = discoveryTools.createTestCaseFolder("Fremder Ordner", null);
        SecurityContextHolder.clearContext();

        authenticateAs(project, ProjectRole.TESTER, "agent");

        assertThatThrownBy(() -> discoveryTools.createTestCaseFolder("Untergeordnet", foreign.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aViewerKeyCannotCreateOrMoveFolders() {
        authenticateAs(project, ProjectRole.VIEWER, "read-only-agent");

        assertThatThrownBy(() -> discoveryTools.createTestCaseFolder("Nicht erlaubt", null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("TESTER");
        assertThatThrownBy(() ->
                discoveryTools.moveTestCasesToFolder(List.of(UUID.randomUUID()), null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("TESTER");
    }

    @Test
    void aFolderNeedsAName() {
        authenticateAs(project, ProjectRole.TESTER, "agent");

        assertThatThrownBy(() -> discoveryTools.createTestCaseFolder("  ", null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("name");
    }

    // --- reading executions ----------------------------------------------------------------

    /**
     * The loop these tools exist to close: after results arrive — from CI, or from a human running
     * the suite — an agent must be able to ask what failed, so it can act on it.
     */
    @Test
    void runsAndTheirResultsCanBeReadBack() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase passing = createCase("Geht durch");
        McpDtos.CreatedTestCase failing = createCase("Fällt durch");
        UUID runId = testRunService.create(project.getId(),
                new CreateTestRunRequest("Regression", "Produktion",
                        Set.of(passing.id(), failing.id()), null, null), null).id();
        markResult(runId, failing.id(), TestResultStatus.FAILED, "Schaltfläche reagiert nicht");
        markResult(runId, passing.id(), TestResultStatus.PASSED, null);

        McpDtos.TestRunPage runs = testRunReadTools.listTestRuns(null, null, null, null);
        assertThat(runs.testRuns()).singleElement().satisfies(run -> {
            assertThat(run.name()).isEqualTo("Regression");
            assertThat(run.total()).isEqualTo(2);
            assertThat(run.failed()).isEqualTo(1);
        });

        McpDtos.TestRunDetail onlyFailures =
                testRunReadTools.getTestRun(runId, List.of(TestResultStatus.FAILED));
        assertThat(onlyFailures.results()).singleElement().satisfies(result -> {
            assertThat(result.testCaseId()).isEqualTo(failing.id());
            assertThat(result.comment()).isEqualTo("Schaltfläche reagiert nicht");
        });
        assertThat(testRunReadTools.getTestRun(runId, null).results()).hasSize(2);
    }

    @Test
    void aRunFromAnotherProjectIsNotFound() {
        authenticateAs(otherProject, ProjectRole.TESTER, "other-agent");
        UUID foreignRun = testRunService.create(otherProject.getId(),
                new CreateTestRunRequest("Fremder Lauf", null, Set.of(), null, null), null).id();
        SecurityContextHolder.clearContext();

        authenticateAs(project, ProjectRole.TESTER, "agent");

        assertThatThrownBy(() -> testRunReadTools.getTestRun(foreignRun, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(testRunReadTools.listTestRuns(null, null, null, null).testRuns()).isEmpty();
    }

    private void markResult(UUID runId, UUID testCaseId, TestResultStatus status, String comment) {
        UUID resultId = testRunService.findById(project.getId(), runId).results().stream()
                .filter(r -> r.testCaseId().equals(testCaseId))
                .findFirst().orElseThrow().id();
        testRunService.updateResult(project.getId(), runId, resultId,
                new UpdateTestResultRequest(status, comment, null));
    }

    // --- requirements and traceability -----------------------------------------------------

    /**
     * The workflow the requirement tools exist for: record what the spec asks, link the cases that
     * cover it, then find out what nothing actually proves. UNTESTED is the interesting verdict —
     * a case is linked, so it looks covered, but it has never run.
     */
    @Test
    void requirementsCanBeRecordedLinkedAndTraced() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase covering = createCase("Prüft die Anmeldung");

        McpDtos.Requirement covered = requirementTools.createRequirement(
                "REQ-1", "Benutzer können sich anmelden", "Aus dem Pflichtenheft, Abschnitt 3.1");
        requirementTools.createRequirement("REQ-2", "Benutzer können ihr Passwort zurücksetzen", null);

        requirementTools.linkTestCasesToRequirement(covered.id(), List.of(covering.id()));

        McpDtos.TraceabilityMatrix matrix = requirementTools.getTraceabilityMatrix();

        assertThat(matrix.summary().totalRequirements()).isEqualTo(2);
        assertThat(matrix.summary().uncovered())
                .as("REQ-2 has no linked case at all")
                .isEqualTo(1);
        assertThat(matrix.summary().untested())
                .as("REQ-1 is linked to a case that has never been executed")
                .isEqualTo(1);
        assertThat(matrix.requirements())
                .filteredOn(r -> r.externalId().equals("REQ-1"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.coverage()).isEqualTo("UNTESTED");
                    assertThat(row.cells()).singleElement()
                            .satisfies(cell -> assertThat(cell.testCaseKey()).isEqualTo(covering.key()));
                });
        assertThat(matrix.requirements())
                .filteredOn(r -> r.externalId().equals("REQ-2"))
                .singleElement()
                .satisfies(row -> assertThat(row.coverage()).isEqualTo("UNCOVERED"));
    }

    @Test
    void requirementsListShowsTheirLinkedCases() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase testCase = createCase("Ein abdeckender Fall");
        McpDtos.Requirement requirement = requirementTools.createRequirement("REQ-9", "Etwas", null);
        requirementTools.linkTestCasesToRequirement(requirement.id(), List.of(testCase.id()));

        assertThat(requirementTools.listRequirements(null, null).requirements())
                .singleElement()
                .satisfies(r -> assertThat(r.testCases()).extracting(McpDtos.TestCaseRef::id)
                        .containsExactly(testCase.id()));
    }

    @Test
    void aTestCaseFromAnotherProjectCannotBeLinked() {
        authenticateAs(otherProject, ProjectRole.TESTER, "other-agent");
        McpDtos.CreatedTestCase foreign = createCase("Fremder Fall");
        SecurityContextHolder.clearContext();

        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.Requirement requirement = requirementTools.createRequirement("REQ-3", "Etwas", null);

        assertThatThrownBy(() ->
                requirementTools.linkTestCasesToRequirement(requirement.id(), List.of(foreign.id())))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(requirementTools.listRequirements(null, null).requirements())
                .singleElement()
                .satisfies(r -> assertThat(r.testCases()).isEmpty());
    }

    @Test
    void aViewerKeyCanReadTheMatrixButNotWriteRequirements() {
        authenticateAs(project, ProjectRole.VIEWER, "read-only-agent");

        assertThatCode(() -> requirementTools.getTraceabilityMatrix()).doesNotThrowAnyException();
        assertThatThrownBy(() -> requirementTools.createRequirement("REQ-4", "Nicht erlaubt", null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("TESTER");
    }

    // --- guardrails and audit --------------------------------------------------------------

    @Test
    void writeBudgetIsEnforcedPerKey() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        // The dev/test default is 60 writes a minute; spend them, then check the next one is
        // refused with something an agent can act on.
        for (int i = 0; i < 60; i++) {
            createCase("Budgeted case " + i);
        }

        assertThatThrownBy(() -> createCase("One too many"))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("Write budget");
    }

    @Test
    void everyInvocationIsAudited() {
        ApiKeyCreatedResponse key = authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase created = createCase("Audited case");
        assertThatThrownBy(() -> createCase("audited case")).isInstanceOf(McpToolException.class);

        // Scoped to this test's key on purpose: audit rows are written REQUIRES_NEW and therefore
        // COMMIT, surviving this test's rollback. That is the design — a refused call must leave a
        // trace after its own transaction is gone — so rows from sibling tests are still present.
        List<McpToolInvocation> records = invocationRepository.findAll().stream()
                .filter(r -> key.id().equals(r.getApiKeyId()))
                .toList();

        assertThat(records).extracting(McpToolInvocation::getToolName).contains("create_test_case");
        assertThat(records).extracting(McpToolInvocation::getOutcome)
                .contains("SUCCESS", "REFUSED");
        assertThat(records).allSatisfy(r -> assertThat(r.getProjectId()).isEqualTo(project.getId()));
        assertThat(records)
                .filteredOn(r -> "SUCCESS".equals(r.getOutcome()))
                .anySatisfy(r -> {
                    assertThat(r.getCreatedEntityType()).isEqualTo("TEST_CASE");
                    assertThat(r.getCreatedEntityId()).isEqualTo(created.id());
                });
    }
}
