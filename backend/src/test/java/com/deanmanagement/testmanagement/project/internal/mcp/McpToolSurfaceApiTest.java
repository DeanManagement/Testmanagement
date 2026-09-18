package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.apiKey.ApiKeyCreatedResponse;
import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.dto.parameter.SaveParameterSetRequest;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ApiKeyRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestRunRequest;
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
import java.util.Map;
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
    private McpProperties mcpProperties;

    @Autowired
    private com.deanmanagement.testmanagement.project.internal.service.ParameterSetService
            parameterSetService;

    @Autowired
    private TestRunWriteTools testRunWriteTools;
    @Autowired
    private TestResultRecordingTools resultRecordingTools;
    @Autowired
    private TestCaseBulkTools testCaseBulkTools;

    @Autowired
    private BugReportTools bugReportTools;
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
                null, null, null, null);
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
        assertThat(testCaseTools.searchTestCases(null, null, null, null, null, null, null, null, null)
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
                null, null, null);

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
                null, null, null, null, null, null);

        testCaseTools.updateTestCase(created.key(), null, null, null, Priority.CRITICAL, null,
                null, null, null, null);

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
                Priority.LOW, "Something to remove", null, null, null, null, null, null, null);

        testCaseTools.updateTestCase(created.key(), null, "", null, null, null, null, null, null, null);

        assertThat(testCaseTools.getTestCase(created.key()).description()).isEmpty();
    }

    @Test
    void updateTestCase_rejectsABlankTitle() {
        // Bean validation on the DTO does not run on this path by itself — the tools build the
        // records by hand and call the services directly, bypassing the controllers' @Valid.
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase created = createCase("A real title");

        assertThatThrownBy(() ->
                testCaseTools.updateTestCase(created.key(), "  ", null, null, null, null, null, null, null, null))
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

        testCaseTools.updateTestCase(created.key(), null, null, null, null, null, null, null, folderId, null);

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
                Priority.MEDIUM, null, null, null, null, null, null, null, true);

        assertThat(second.id()).isNotNull();
    }

    @Test
    void bulkCreate_reportsPerItemOutcomesIncludingDuplicatesWithinTheBatch() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        createCase("Already here");

        McpDtos.BulkResult result = testCaseBulkTools.createTestCasesBulk(List.of(
                new TestCaseBulkTools.BulkCase("Brand new one", Priority.LOW, null, null, null, null, null, null),
                new TestCaseBulkTools.BulkCase("Already here", Priority.LOW, null, null, null, null, null, null),
                new TestCaseBulkTools.BulkCase("Brand new one", Priority.LOW, null, null, null, null, null, null),
                new TestCaseBulkTools.BulkCase(null, Priority.LOW, null, null, null, null, null, null)
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

        McpDtos.BulkResult result = testCaseBulkTools.createTestCasesBulk(List.of(
                new TestCaseBulkTools.BulkCase("Would be created", Priority.LOW, null, null, null, null, null, null)
        ), true);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.created()).isEqualTo(1);
        assertThat(testCaseTools.searchTestCases(null, null, null, null, null, null, null, null, null)
                .totalElements()).isZero();
    }

    @Test
    void bulkCreate_refusesABatchOverTheConfiguredCap() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        List<TestCaseBulkTools.BulkCase> tooMany = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> new TestCaseBulkTools.BulkCase("Case " + i, Priority.LOW, null, null,
                        null, null, null, null))
                .toList();

        assertThatThrownBy(() -> testCaseBulkTools.createTestCasesBulk(tooMany, null))
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
                testRunReadTools.getTestRun(runId.toString(), List.of(TestResultStatus.FAILED));
        assertThat(onlyFailures.results()).singleElement().satisfies(result -> {
            assertThat(result.testCaseId()).isEqualTo(failing.id());
            assertThat(result.comment()).isEqualTo("Schaltfläche reagiert nicht");
        });
        assertThat(testRunReadTools.getTestRun(runId.toString(), null).results()).hasSize(2);
    }

    @Test
    void aRunFromAnotherProjectIsNotFound() {
        authenticateAs(otherProject, ProjectRole.TESTER, "other-agent");
        UUID foreignRun = testRunService.create(otherProject.getId(),
                new CreateTestRunRequest("Fremder Lauf", null, Set.of(), null, null), null).id();
        SecurityContextHolder.clearContext();

        authenticateAs(project, ProjectRole.TESTER, "agent");

        assertThatThrownBy(() -> testRunReadTools.getTestRun(foreignRun.toString(), null))
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
        // Read from the configured limit rather than hard-coded: this test asserted 60 until
        // PRD-027 §3.6 raised the default to 120, and the value is not the point — that the budget
        // binds, and refuses in terms an agent can act on, is.
        int limit = mcpProperties.getMaxWritesPerMinute();
        for (int i = 0; i < limit; i++) {
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

    /**
     * The audit row has to say <em>what</em> was created, not just that something was. The aspect
     * picks up new tools automatically but its result-to-entity mapping does not: runs and bug
     * reports were landing with a null entity type until it was extended, which is precisely the
     * kind of gap that survives because the row itself looks fine.
     */
    @Test
    void auditRowsLinkTheRunAndBugTheyCreated() {
        enableBugReports(project);
        ApiKeyCreatedResponse key = authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestRun run =
                testRunWriteTools.createTestRun("Geprüfter Lauf", null, null, null, null);
        McpDtos.BugDetail bug = bugReportTools.createBugReport("Geprüfter Fehler", Priority.LOW,
                null, null, null, null, null, null, null, null);

        List<McpToolInvocation> records = invocationRepository.findAll().stream()
                .filter(r -> key.id().equals(r.getApiKeyId()))
                .toList();

        assertThat(records)
                .filteredOn(r -> "create_test_run".equals(r.getToolName()))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getCreatedEntityType()).isEqualTo("TEST_RUN");
                    assertThat(r.getCreatedEntityId()).isEqualTo(run.id());
                });
        assertThat(records)
                .filteredOn(r -> "create_bug_report".equals(r.getToolName()))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getCreatedEntityType()).isEqualTo("BUG_REPORT");
                    assertThat(r.getCreatedEntityId()).isEqualTo(bug.id());
                });
    }

    // --- executing runs (PRD-027) ----------------------------------------------------------

    /**
     * The loop PRD-027 exists to close, end to end: open a run, work through it, close it. Before
     * these tools an agent could read the run and do nothing else.
     */
    @Test
    void anAgentCanExecuteARunFromStartToFinish() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase first = createCase("Anmeldung funktioniert");
        McpDtos.CreatedTestCase second = createCase("Abmeldung funktioniert");

        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Rauchtest", "staging",
                Set.of(first.id(), second.id()), null, null);
        assertThat(run.status()).isEqualTo(TestRunStatus.PLANNED);
        assertThat(run.totalResults()).isEqualTo(2);

        // Recording the first result starts the run — no separate start tool to forget.
        McpDtos.RecordedResult recorded = resultRecordingTools.recordTestResult(run.id().toString(),
                TestResultStatus.PASSED, first.id(), null, "sauber durchgelaufen", null);
        assertThat(recorded.runStatus()).isEqualTo(TestRunStatus.IN_PROGRESS);
        assertThat(recorded.added()).isFalse();

        resultRecordingTools.recordTestResult(run.id().toString(), TestResultStatus.FAILED, second.id(), null,
                "Schaltfläche reagiert nicht", null);

        McpDtos.CompletedTestRun done = testRunWriteTools.completeTestRun(run.id().toString(), null);
        assertThat(done.status()).isEqualTo(TestRunStatus.COMPLETED);
        assertThat(done.total()).isEqualTo(2);
        assertThat(done.passed()).isEqualTo(1);
        assertThat(done.failed()).isEqualTo(1);
        assertThat(done.pending()).isZero();
    }

    /**
     * The assertion that matters most in this file. {@code TestRunService.addResult} appends a new
     * row without checking whether the case already has one, so recording through it would leave
     * the seeded PENDING result in place beside the new one — a run reporting three results for
     * two cases, one permanently pending, and every pass rate downstream wrong.
     *
     * <p>Counted rather than inspected on purpose: a status assertion passes just as happily
     * against the broken version, because the appended row does have the right status. Only the
     * count catches it.
     */
    @Test
    void recordingFillsInTheSeededResultRatherThanAppendingASecond() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase testCase = createCase("Nur ein Ergebnis");
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Lauf", null,
                Set.of(testCase.id()), null, null);

        resultRecordingTools.recordTestResult(run.id().toString(), TestResultStatus.FAILED, testCase.id(), null,
                "erster Versuch", null);
        // Re-recording is how an agent corrects itself; it must still not add a row.
        resultRecordingTools.recordTestResult(run.id().toString(), TestResultStatus.PASSED, testCase.id(), null,
                "nach dem Fix", null);

        McpDtos.TestRunDetail detail = testRunReadTools.getTestRun(run.id().toString(), null);
        assertThat(detail.results()).hasSize(1);
        assertThat(detail.results().getFirst().status()).isEqualTo(TestResultStatus.PASSED);
        assertThat(detail.results().getFirst().comment()).isEqualTo("nach dem Fix");
    }

    /**
     * Re-recording must not erase the evidence. {@code UpdateTestResultRequest} has no
     * absent-versus-null distinction and {@code updateResult} assigns all three fields, so passing
     * the arguments straight through would clear the comment on any call that omits it — and the
     * tool advertises {@code idempotentHint} and tells agents to retry, which is exactly that call.
     */
    @Test
    void reRecordingWithoutACommentKeepsTheOneAlreadyThere() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase testCase = createCase("Beweis behalten");
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Lauf", null,
                Set.of(testCase.id()), null, null);

        resultRecordingTools.recordTestResult(run.id().toString(), TestResultStatus.FAILED, testCase.id(), null,
                "Stacktrace: NullPointerException in PaymentService", "https://tracker/1");
        // The retry an agent makes after a dropped response: status only, no comment.
        resultRecordingTools.recordTestResult(run.id().toString(), TestResultStatus.FAILED, testCase.id(), null,
                null, null);

        McpDtos.TestResult result =
                testRunReadTools.getTestRun(run.id().toString(), null).results().getFirst();
        assertThat(result.comment()).isEqualTo("Stacktrace: NullPointerException in PaymentService");
        assertThat(result.defectLink()).isEqualTo("https://tracker/1");
    }

    @Test
    void aResultIdThatContradictsTheTestCaseIdIsRefused() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase a = createCase("Fall A");
        McpDtos.CreatedTestCase b = createCase("Fall B");
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Lauf", null,
                Set.of(a.id(), b.id()), null, null);
        UUID resultForA = testRunReadTools.getTestRun(run.id().toString(), null).results().stream()
                .filter(r -> r.testCaseId().equals(a.id()))
                .findFirst().orElseThrow().id();

        assertThatThrownBy(() -> resultRecordingTools.recordTestResult(run.id().toString(),
                TestResultStatus.PASSED, b.id(), resultForA, null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("belongs to test case");
    }

    /**
     * Idempotency must not extend to the opposite terminal state: answering "abort this" with a
     * cheerful COMPLETED leaves the agent to notice by diffing a field against its own request.
     */
    @Test
    void abortingAnAlreadyCompletedRunIsRefusedRatherThanSilentlyIgnored() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestRun run =
                testRunWriteTools.createTestRun("Lauf", null, null, null, null);
        testRunWriteTools.completeTestRun(run.id().toString(), null);

        assertThatThrownBy(() ->
                testRunWriteTools.completeTestRun(run.id().toString(), TestRunStatus.ABORTED))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("already COMPLETED");
    }

    /**
     * Closing a run must not revert a rename made while the agent was executing. The tools send a
     * status-only update; {@code TestRunService.update} treats null name/environment as unchanged.
     */
    @Test
    void completingARunDoesNotRevertAConcurrentRename() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Ursprünglicher Name",
                "staging", null, null, null);

        // A human renames it in the UI while the agent is mid-run.
        testRunService.update(project.getId(), run.id(),
                new UpdateTestRunRequest("Vom Menschen umbenannt", "produktion", null, null, null),
                null);

        testRunWriteTools.completeTestRun(run.id().toString(), null);

        McpDtos.TestRunDetail after = testRunReadTools.getTestRun(run.id().toString(), null);
        assertThat(after.name()).isEqualTo("Vom Menschen umbenannt");
        assertThat(after.environment()).isEqualTo("produktion");
    }

    /** A run completed straight from PLANNED would otherwise have an endTime and no startTime. */
    @Test
    void completingAPlannedRunStillStampsAStartTime() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestRun run =
                testRunWriteTools.createTestRun("Nie begonnen", null, null, null, null);

        testRunWriteTools.completeTestRun(run.id().toString(), null);

        McpDtos.TestRunDetail after = testRunReadTools.getTestRun(run.id().toString(), null);
        assertThat(after.startTime()).isNotNull();
        assertThat(after.endTime()).isNotNull();
    }

    /**
     * Every run response leads with its key and the tool descriptions tell the agent to quote it,
     * and then the run tools demanded a UUID — costing a translation round trip on every call.
     * Reported from a real session (PRD-027 §8.3).
     */
    @Test
    void theRunToolsTakeTheRunKeyAsWellAsItsUuid() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase testCase = createCase("Per Schlüssel erreichbar");
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Schlüssellauf", null,
                Set.of(testCase.id()), null, null);

        assertThat(testRunReadTools.getTestRun(run.key(), null).id()).isEqualTo(run.id());
        assertThat(resultRecordingTools.recordTestResult(run.key(), TestResultStatus.PASSED,
                testCase.id(), null, null, null).added()).isFalse();
        assertThat(testRunWriteTools.completeTestRun(run.key(), null).status())
                .isEqualTo(TestRunStatus.COMPLETED);
    }

    @Test
    void anotherProjectsRunKeyIsNotFound() {
        authenticateAs(otherProject, ProjectRole.TESTER, "other-agent");
        McpDtos.CreatedTestRun foreign =
                testRunWriteTools.createTestRun("Fremder Lauf", null, null, null, null);
        SecurityContextHolder.clearContext();

        // Run keys are unique instance-wide, so the key lookup has to be project-scoped or it
        // would resolve — and name — a stranger's run.
        authenticateAs(project, ProjectRole.TESTER, "agent");
        assertThatThrownBy(() -> testRunReadTools.getTestRun(foreign.key(), null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("No test run");
    }

    // --- bulk recording (PRD-027 §8.3) -----------------------------------------------------

    /**
     * The fan-out this exists to remove: re-recording 29 results was 29 calls, and 29 writes
     * against a budget of 120 a minute.
     */
    @Test
    void manyResultsCanBeRecordedInOneCall() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase a = createCase("Fall A");
        McpDtos.CreatedTestCase b = createCase("Fall B");
        McpDtos.CreatedTestCase c = createCase("Fall C");
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Sammellauf", null,
                Set.of(a.id(), b.id(), c.id()), null, null);

        McpDtos.RecordedResults recorded = resultRecordingTools.recordTestResults(run.key(), List.of(
                new McpDtos.ResultEntry(TestResultStatus.PASSED, a.id(), null, "gut", null),
                new McpDtos.ResultEntry(TestResultStatus.FAILED, b.id(), null, "kaputt", null),
                new McpDtos.ResultEntry(TestResultStatus.SKIPPED, c.id(), null, null, null)));

        assertThat(recorded.recorded()).isEqualTo(3);
        assertThat(recorded.runStatus()).isEqualTo(TestRunStatus.IN_PROGRESS);
        assertThat(recorded.passed()).isEqualTo(1);
        assertThat(recorded.failed()).isEqualTo(1);
        assertThat(recorded.skipped()).isEqualTo(1);
        assertThat(recorded.pending()).isZero();
        assertThat(testRunReadTools.getTestRun(run.id().toString(), null).results())
                .as("filled in, not appended").hasSize(3);
    }

    /**
     * All-or-nothing, and the reason it can be: recording is idempotent, so resending a corrected
     * batch is safe — which makes "nothing happened, entry 1 is wrong" a better answer than a
     * half-recorded run the agent has to reconcile.
     */
    @Test
    void oneBadEntryFailsTheWholeBatchAndNamesItsPosition() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase inRun = createCase("Im Lauf");
        McpDtos.CreatedTestCase notInRun = createCase("Nicht im Lauf");
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Sammellauf", null,
                Set.of(inRun.id()), null, null);

        assertThatThrownBy(() -> resultRecordingTools.recordTestResults(run.key(), List.of(
                new McpDtos.ResultEntry(TestResultStatus.PASSED, inRun.id(), null, null, null),
                new McpDtos.ResultEntry(TestResultStatus.PASSED, notInRun.id(), null, null, null))))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("results[1]")
                .hasMessageContaining("Nothing was recorded");

        assertThat(testRunReadTools.getTestRun(run.id().toString(), null).results())
                .allSatisfy(r -> assertThat(r.status()).isEqualTo(TestResultStatus.PENDING));
    }

    @Test
    void aBulkBatchOverTheLimitIsRefusedBeforeAnyWrite() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestRun run =
                testRunWriteTools.createTestRun("Zu viel", null, null, null, null);
        // max-bulk-size is 5 in this test's properties.
        List<McpDtos.ResultEntry> tooMany = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> new McpDtos.ResultEntry(TestResultStatus.PASSED, UUID.randomUUID(),
                        null, null, null))
                .toList();

        assertThatThrownBy(() -> resultRecordingTools.recordTestResults(run.key(), tooMany))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("limit is 5");
    }

    @Test
    void bulkRecordingAlsoPreservesACommentItWasNotGiven() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase testCase = createCase("Beweis behalten, gesammelt");
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Sammellauf", null,
                Set.of(testCase.id()), null, null);
        resultRecordingTools.recordTestResult(run.key(), TestResultStatus.FAILED, testCase.id(), null,
                "Ursprünglicher Beweis", null);

        resultRecordingTools.recordTestResults(run.key(), List.of(
                new McpDtos.ResultEntry(TestResultStatus.FAILED, testCase.id(), null, null, null)));

        assertThat(testRunReadTools.getTestRun(run.id().toString(), null).results().getFirst().comment())
                .isEqualTo("Ursprünglicher Beweis");
    }

    @Test
    void aRunCanBeSeededFromASuite() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase a = createCase("Fall A");
        McpDtos.CreatedTestCase b = createCase("Fall B");
        McpDtos.CreatedSuite suite =
                planningTools.createTestSuite("Rauchtests", null, Set.of(a.id(), b.id()));

        McpDtos.CreatedTestRun run =
                testRunWriteTools.createTestRun("Aus Suite", null, null, suite.id(), null);

        assertThat(run.totalResults()).isEqualTo(2);
    }

    @Test
    void recordingForACaseNotInTheRunAppendsAndSaysSo() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase seeded = createCase("Im Lauf");
        McpDtos.CreatedTestCase adHoc = createCase("Nicht im Lauf");
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Lauf", null,
                Set.of(seeded.id()), null, null);

        McpDtos.RecordedResult recorded = resultRecordingTools.recordTestResult(run.id().toString(),
                TestResultStatus.BLOCKED, adHoc.id(), null, "unterwegs entdeckt", null);

        // Flagged because this is also what a mistyped id looks like.
        assertThat(recorded.added()).isTrue();
        assertThat(testRunReadTools.getTestRun(run.id().toString(), null).results()).hasSize(2);
    }

    @Test
    void resultsOfACompletedRunAreFinal() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase testCase = createCase("Abgeschlossen");
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Lauf", null,
                Set.of(testCase.id()), null, null);
        resultRecordingTools.recordTestResult(run.id().toString(), TestResultStatus.PASSED, testCase.id(), null,
                null, null);
        testRunWriteTools.completeTestRun(run.id().toString(), null);

        assertThatThrownBy(() -> resultRecordingTools.recordTestResult(run.id().toString(),
                TestResultStatus.FAILED, testCase.id(), null, "zu spät", null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("new run");
    }

    /** An agent retrying after a dropped response should get the counts, not an error. */
    @Test
    void completingATwiceCompletedRunIsANoOp() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestRun run =
                testRunWriteTools.createTestRun("Leerlauf", null, null, null, null);

        testRunWriteTools.completeTestRun(run.id().toString(), null);
        McpDtos.CompletedTestRun again = testRunWriteTools.completeTestRun(run.id().toString(), null);

        assertThat(again.status()).isEqualTo(TestRunStatus.COMPLETED);
    }

    @Test
    void pendingResultsDoNotBlockCompletionButAreReported() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase done = createCase("Erledigt");
        McpDtos.CreatedTestCase notDone = createCase("Nicht erledigt");
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Abgebrochen", null,
                Set.of(done.id(), notDone.id()), null, null);
        resultRecordingTools.recordTestResult(run.id().toString(), TestResultStatus.PASSED, done.id(), null,
                null, null);

        McpDtos.CompletedTestRun aborted =
                testRunWriteTools.completeTestRun(run.id().toString(), TestRunStatus.ABORTED);

        assertThat(aborted.status()).isEqualTo(TestRunStatus.ABORTED);
        assertThat(aborted.pending()).isEqualTo(1);
    }

    /**
     * The reason {@code get_test_run} publishes result ids at all.
     *
     * <p>A parameterized case (PRD-015) expands into one result per parameter set, so a test case
     * id names three rows and there is no correct one to pick. Guessing would record an outcome
     * against the wrong data set and look like it worked. The refusal names the candidates, and
     * the second half of this test proves the escape it offers actually works — a refusal pointing
     * at an unusable alternative is just a dead end.
     */
    @Test
    void aParameterizedCaseIsAmbiguousByTestCaseIdAndAddressableByResultId() {
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase testCase = createCase("Anmeldung je Rolle");
        addParameterSet(testCase.id(), "Admin", Map.of("rolle", "admin"));
        addParameterSet(testCase.id(), "Gast", Map.of("rolle", "gast"));

        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Rollenlauf", null,
                Set.of(testCase.id()), null, null);
        assertThat(run.totalResults()).as("one result per parameter set").isEqualTo(2);

        assertThatThrownBy(() -> resultRecordingTools.recordTestResult(run.id().toString(),
                TestResultStatus.PASSED, testCase.id(), null, null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("parameterized")
                .hasMessageContaining("resultId");

        McpDtos.TestRunDetail detail = testRunReadTools.getTestRun(run.id().toString(), null);
        assertThat(detail.results()).allSatisfy(r -> assertThat(r.parameterSetName()).isNotNull());
        UUID guestResultId = detail.results().stream()
                .filter(r -> "Gast".equals(r.parameterSetName()))
                .findFirst().orElseThrow().id();

        McpDtos.RecordedResult recorded = resultRecordingTools.recordTestResult(run.id().toString(),
                TestResultStatus.FAILED, null, guestResultId, "Gast kommt nicht rein", null);

        assertThat(recorded.added()).isFalse();
        assertThat(testRunReadTools.getTestRun(run.id().toString(), null).results())
                .filteredOn(r -> r.status() == TestResultStatus.FAILED)
                .singleElement()
                .satisfies(r -> assertThat(r.parameterSetName()).isEqualTo("Gast"));
    }

    @Test
    void aViewerKeyCannotExecute() {
        authenticateAs(project, ProjectRole.VIEWER, "read-only-agent");

        assertThatThrownBy(() ->
                testRunWriteTools.createTestRun("Nicht erlaubt", null, null, null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("TESTER");
    }

    @Test
    void aSuiteFromAnotherProjectCannotSeedARun() {
        authenticateAs(otherProject, ProjectRole.TESTER, "other-agent");
        McpDtos.CreatedSuite foreign = planningTools.createTestSuite("Fremde Suite", null, null);
        SecurityContextHolder.clearContext();

        authenticateAs(project, ProjectRole.TESTER, "agent");
        assertThatThrownBy(() ->
                testRunWriteTools.createTestRun("Lauf", null, null, foreign.id(), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- bug reports (PRD-027) -------------------------------------------------------------

    /**
     * {@code bugReportsEnabled} defaults to false, so without translation the agent gets a bare
     * ForbiddenException — which it will read as a transient error and retry forever. The refusal
     * has to say what the fix is and that the agent cannot apply it.
     */
    @Test
    void filingOnAProjectWithBugReportsOffExplainsHowToTurnThemOn() {
        authenticateAs(project, ProjectRole.TESTER, "agent");

        assertThatThrownBy(() -> bugReportTools.createBugReport("Kaputt", Priority.HIGH, null,
                null, null, null, null, null, null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("not enabled")
                .hasMessageContaining("ADMIN");
    }

    @Test
    void aBugCanBeFiledAgainstTheResultThatProducedIt() {
        enableBugReports(project);
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.CreatedTestCase testCase = createCase("Zahlung schlägt fehl");
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Lauf", "staging",
                Set.of(testCase.id()), null, null);
        McpDtos.RecordedResult failure = resultRecordingTools.recordTestResult(run.id().toString(),
                TestResultStatus.FAILED, testCase.id(), null, "500 vom Zahlungsdienst", null);

        McpDtos.BugDetail bug = bugReportTools.createBugReport("Zahlung wirft 500",
                Priority.CRITICAL, "Beim Bezahlen", "1. Warenkorb 2. Bezahlen", "Bestätigung",
                "HTTP 500", "staging", failure.resultId(), run.id(), null);

        assertThat(bug.status()).isEqualTo(BugReportStatus.OPEN);
        assertThat(bug.testResultId()).isEqualTo(failure.resultId());
        assertThat(bug.testCaseTitle()).isEqualTo("Zahlung schlägt fehl");
        assertThat(bug.stepsToReproduce()).isEqualTo("1. Warenkorb 2. Bezahlen");
    }

    /**
     * An agent running the same suite nightly will otherwise file the same bug every night.
     * Matching is on the normalised title, so punctuation and case do not defeat it.
     */
    @Test
    void filingABugThatDuplicatesAnOpenOneIsRefused() {
        enableBugReports(project);
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.BugDetail first = bugReportTools.createBugReport("Zahlung wirft 500",
                Priority.HIGH, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> bugReportTools.createBugReport("zahlung wirft 500!",
                Priority.HIGH, null, null, null, null, null, null, null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining(first.id().toString())
                .hasMessageContaining("allowDuplicateTitle");
    }

    /**
     * A regression of something closed last month is a new report, not a duplicate — refusing it
     * would suppress the most interesting thing a nightly run can tell you.
     */
    @Test
    void theSameTitleIsAllowedOnceTheEarlierBugIsClosed() {
        enableBugReports(project);
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.BugDetail first = bugReportTools.createBugReport("Zahlung wirft 500",
                Priority.HIGH, null, null, null, null, null, null, null, null);
        bugReportTools.changeBugReportStatus(first.id(), BugReportStatus.CLOSED,
                "In 2.3 behoben und nachgeprüft");

        McpDtos.BugDetail regression = bugReportTools.createBugReport("Zahlung wirft 500",
                Priority.HIGH, null, null, null, null, null, null, null, null);

        assertThat(regression.id()).isNotEqualTo(first.id());
        assertThat(regression.status()).isEqualTo(BugReportStatus.OPEN);
    }

    @Test
    void bugsCanBeListedAndFilteredBeforeFilingANewOne() {
        enableBugReports(project);
        authenticateAs(project, ProjectRole.TESTER, "agent");
        bugReportTools.createBugReport("Offener Fehler", Priority.HIGH, null, null, null, null,
                null, null, null, null);
        McpDtos.BugDetail closed = bugReportTools.createBugReport("Behobener Fehler", Priority.LOW,
                null, null, null, null, null, null, null, null);
        bugReportTools.changeBugReportStatus(closed.id(), BugReportStatus.CLOSED, "nachgeprüft");

        McpDtos.BugPage open = bugReportTools.listBugReports(List.of(BugReportStatus.OPEN), null,
                null, null);

        assertThat(open.totalElements()).isEqualTo(1);
        assertThat(open.bugReports()).singleElement()
                .satisfies(b -> assertThat(b.title()).isEqualTo("Offener Fehler"));
    }

    @Test
    void aStatusChangeNeedsAReason() {
        enableBugReports(project);
        authenticateAs(project, ProjectRole.TESTER, "agent");
        McpDtos.BugDetail bug = bugReportTools.createBugReport("Irgendwas", Priority.LOW, null,
                null, null, null, null, null, null, null);

        assertThatThrownBy(() ->
                bugReportTools.changeBugReportStatus(bug.id(), BugReportStatus.RESOLVED, "  "))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void aViewerKeyCannotFileBugs() {
        enableBugReports(project);
        authenticateAs(project, ProjectRole.VIEWER, "read-only-agent");

        assertThatThrownBy(() -> bugReportTools.createBugReport("Nicht erlaubt", Priority.LOW,
                null, null, null, null, null, null, null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("TESTER");
    }

    private void enableBugReports(Project target) {
        projectService.toggleBugReports(target.getId(), true, null);
    }

    private void addParameterSet(UUID testCaseId, String name, Map<String, String> values) {
        parameterSetService.create(project.getId(), testCaseId,
                new SaveParameterSetRequest(name, values, null));
    }
}
