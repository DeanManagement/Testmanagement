package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ApiKeyRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Step results, run housekeeping and comments — the tester interactions PRD-027 left out. Same
 * harness as {@link McpToolSurfaceApiTest}, and not {@code @Transactional} for the reason given
 * there.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "app.mcp.enabled=true")
class McpRunMaintenanceToolsApiTest {

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ApiKeyRepository apiKeyRepository;
    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private TestRunService testRunService;
    @Autowired
    private McpWriteThrottle writeThrottle;
    @Autowired
    private TestCaseTools testCaseTools;
    @Autowired
    private TestRunWriteTools testRunWriteTools;
    @Autowired
    private TestRunReadTools testRunReadTools;
    @Autowired
    private TestRunMaintenanceTools maintenanceTools;
    @Autowired
    private CommentTools commentTools;

    private Project project;
    private Project otherProject;

    @BeforeEach
    void setUp() {
        writeThrottle.reset();
        String suffix = Integer.toHexString(new java.util.Random().nextInt(0xFFFFF));
        project = newProject("MCP Project", "C" + suffix);
        otherProject = newProject("Other Project", "D" + suffix);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        projectService.delete(project.getId(), null);
        projectService.delete(otherProject.getId(), null);
    }

    private Project newProject(String name, String key) {
        Project p = new Project();
        p.setName(name);
        p.setKey(key);
        return projectRepository.save(p);
    }

    private void authenticateAs(Project target, ProjectRole role) {
        var created = apiKeyService.create(new CreateApiKeyRequest(
                "agent-" + role + "-" + target.getKey(), target.getId(), role));
        UUID serviceUserId = apiKeyRepository.findById(created.id()).orElseThrow()
                .getServiceUser().getId();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(serviceUserId.toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_API_KEY"),
                                new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private McpDtos.CreatedTestCase createCase(String title, String... stepActions) {
        List<McpDtos.Step> steps = java.util.Arrays.stream(stepActions)
                .map(action -> new McpDtos.Step(action, null, null))
                .toList();
        return testCaseTools.createTestCase(title, Priority.MEDIUM, null, null, null, null,
                steps, null, null);
    }

    private McpDtos.CreatedTestRun runOf(McpDtos.CreatedTestCase... cases) {
        Set<UUID> ids = java.util.Arrays.stream(cases).map(McpDtos.CreatedTestCase::id)
                .collect(java.util.stream.Collectors.toSet());
        return testRunWriteTools.createTestRun("Run", null, ids, null, null);
    }

    // --- record_step_result ----------------------------------------------------------------

    @Test
    void recordingAStepStartsTheRunAndAFailedStepFailsTheCase() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login", "Open page", "Submit form");
        McpDtos.CreatedTestRun run = runOf(login);

        McpDtos.RecordedStepResult recorded = testRunWriteTools.recordStepResult(run.key(), 2,
                TestResultStatus.FAILED, login.id(), null, "Button does nothing");

        assertThat(recorded.runStatus()).isEqualTo(TestRunStatus.IN_PROGRESS);
        assertThat(recorded.resultStatus()).isEqualTo(TestResultStatus.FAILED);
        assertThat(recorded.steps()).extracting(McpDtos.StepOutcome::action)
                .containsExactly("Open page", "Submit form");
        assertThat(recorded.steps().get(1).actualResult()).isEqualTo("Button does nothing");
        assertThat(recorded.steps().get(0).status()).isEqualTo(TestResultStatus.PENDING);
    }

    @Test
    void theCaseStaysPendingUntilEveryStepIsRecordedThenPasses() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login", "Open page", "Submit form");
        McpDtos.CreatedTestRun run = runOf(login);

        McpDtos.RecordedStepResult afterFirst = testRunWriteTools.recordStepResult(run.key(), 1,
                TestResultStatus.PASSED, login.id(), null, null);
        McpDtos.RecordedStepResult afterSecond = testRunWriteTools.recordStepResult(run.key(), 2,
                TestResultStatus.PASSED, login.id(), null, null);

        assertThat(afterFirst.resultStatus()).isEqualTo(TestResultStatus.PENDING);
        assertThat(afterSecond.resultStatus()).isEqualTo(TestResultStatus.PASSED);
    }

    @Test
    void reRecordingAStepWithoutActualResultKeepsTheOneAlreadyThere() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login", "Open page");
        McpDtos.CreatedTestRun run = runOf(login);
        testRunWriteTools.recordStepResult(run.key(), 1, TestResultStatus.FAILED, login.id(), null,
                "HTTP 500");

        McpDtos.RecordedStepResult again = testRunWriteTools.recordStepResult(run.key(), 1,
                TestResultStatus.FAILED, login.id(), null, null);

        assertThat(again.steps().getFirst().actualResult()).isEqualTo("HTTP 500");
    }

    @Test
    void aStepNumberOutsideTheCaseIsRefusedNamingTheRange() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login", "Open page", "Submit form");
        McpDtos.CreatedTestRun run = runOf(login);

        assertThatThrownBy(() -> testRunWriteTools.recordStepResult(run.key(), 3,
                TestResultStatus.PASSED, login.id(), null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("between 1 and 2");
    }

    @Test
    void aCaseWithoutStepsPointsTheAgentAtRecordTestResult() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase stepless = createCase("No steps");
        McpDtos.CreatedTestRun run = runOf(stepless);

        assertThatThrownBy(() -> testRunWriteTools.recordStepResult(run.key(), 1,
                TestResultStatus.PASSED, stepless.id(), null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("record_test_result");
    }

    @Test
    void stepsOfACompletedRunAreFinal() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login", "Open page");
        McpDtos.CreatedTestRun run = runOf(login);
        testRunWriteTools.completeTestRun(run.key(), null);

        assertThatThrownBy(() -> testRunWriteTools.recordStepResult(run.key(), 1,
                TestResultStatus.PASSED, login.id(), null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("final");
    }

    @Test
    void aViewerKeyCannotRecordAStep() {
        authenticateAs(project, ProjectRole.VIEWER);

        assertThatThrownBy(() -> testRunWriteTools.recordStepResult("any", 1,
                TestResultStatus.PASSED, UUID.randomUUID(), null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("TESTER");
    }

    // --- update_test_run -------------------------------------------------------------------

    @Test
    void updatingARunChangesOnlyWhatWasPassed() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Old name", "staging",
                null, null, null);

        McpDtos.UpdatedTestRun updated = maintenanceTools.updateTestRun(run.key(), "New name",
                null, null);

        assertThat(updated.name()).isEqualTo("New name");
        assertThat(updated.environment()).isEqualTo("staging");
        assertThat(updated.status()).isEqualTo(TestRunStatus.PLANNED);
    }

    @Test
    void anUpdateWithNoFieldsIsRefused() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Run", null, null, null, null);

        assertThatThrownBy(() -> maintenanceTools.updateTestRun(run.key(), null, null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("Nothing to update");
    }

    @Test
    void aBlankNameIsRefused() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Run", null, null, null, null);

        assertThatThrownBy(() -> maintenanceTools.updateTestRun(run.key(), "  ", null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void anotherProjectsRunCannotBeUpdated() {
        authenticateAs(otherProject, ProjectRole.TESTER);
        McpDtos.CreatedTestRun foreign = testRunWriteTools.createTestRun("Foreign", null, null,
                null, null);
        SecurityContextHolder.clearContext();
        authenticateAs(project, ProjectRole.TESTER);

        assertThatThrownBy(() -> maintenanceTools.updateTestRun(foreign.id().toString(), "Mine now",
                null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- clone_test_run --------------------------------------------------------------------

    @Test
    void aCloneHoldsTheSameCasesAllPendingWithTheAgentAsExecutor() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase first = createCase("First");
        McpDtos.CreatedTestCase second = createCase("Second");
        McpDtos.CreatedTestRun source = runOf(first, second);
        testRunWriteTools.recordTestResult(source.key(), TestResultStatus.FAILED, first.id(), null,
                "broken", null);
        testRunWriteTools.completeTestRun(source.key(), null);

        McpDtos.CreatedTestRun clone = maintenanceTools.cloneTestRun(source.key(), "Re-test", null);

        assertThat(clone.id()).isNotEqualTo(source.id());
        assertThat(clone.status()).isEqualTo(TestRunStatus.PLANNED);
        McpDtos.TestRunDetail detail = testRunReadTools.getTestRun(clone.key(), null);
        assertThat(detail.results()).extracting(McpDtos.TestResult::testCaseId)
                .containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(detail.results()).allMatch(r -> r.status() == TestResultStatus.PENDING);
        assertThat(testRunService.findById(project.getId(), clone.id()).executorName()).isNotNull();
    }

    @Test
    void aCloneNeedsAName() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestRun source = testRunWriteTools.createTestRun("Run", null, null, null,
                null);

        assertThatThrownBy(() -> maintenanceTools.cloneTestRun(source.key(), null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("name");
    }

    // --- comments --------------------------------------------------------------------------

    @Test
    void aCommentOnATestCaseCanBeAddedByKeyAndReadBack() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");

        commentTools.addComment("Is the SSO path in scope?", login.key(), null);

        McpDtos.CommentList thread = commentTools.listComments(login.key(), null);
        assertThat(thread.total()).isEqualTo(1);
        assertThat(thread.comments().getFirst().content()).isEqualTo("Is the SSO path in scope?");
    }

    @Test
    void aCommentOnAResultIsSeparateFromTheTestCasesThread() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        McpDtos.CreatedTestRun run = runOf(login);
        UUID resultId = testRunReadTools.getTestRun(run.key(), null).results().getFirst().id();

        commentTools.addComment("Flaky on staging only", null, resultId);

        assertThat(commentTools.listComments(null, resultId).total()).isEqualTo(1);
        assertThat(commentTools.listComments(login.key(), null).total()).isZero();
    }

    @Test
    void aCommentNeedsExactlyOneTarget() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");

        assertThatThrownBy(() -> commentTools.addComment("hi", null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("exactly one");
        assertThatThrownBy(() -> commentTools.addComment("hi", login.key(), UUID.randomUUID()))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void anEmptyCommentIsRefused() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");

        assertThatThrownBy(() -> commentTools.addComment(" ", login.key(), null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("content");
    }

    @Test
    void aResultInAnotherProjectCannotBeCommentedOnOrRead() {
        authenticateAs(otherProject, ProjectRole.TESTER);
        McpDtos.CreatedTestRun foreignRun = runOf(createCase("Foreign"));
        UUID foreignResult = testRunReadTools.getTestRun(foreignRun.key(), null).results()
                .getFirst().id();
        SecurityContextHolder.clearContext();
        authenticateAs(project, ProjectRole.TESTER);

        assertThatThrownBy(() -> commentTools.addComment("hello", null, foreignResult))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> commentTools.listComments(null, foreignResult))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aViewerKeyCanReadCommentsButNotAddThem() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        commentTools.addComment("note", login.key(), null);
        SecurityContextHolder.clearContext();
        authenticateAs(project, ProjectRole.VIEWER);

        assertThat(commentTools.listComments(login.key(), null).total()).isEqualTo(1);
        assertThatThrownBy(() -> commentTools.addComment("nope", login.key(), null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("TESTER");
    }
}
