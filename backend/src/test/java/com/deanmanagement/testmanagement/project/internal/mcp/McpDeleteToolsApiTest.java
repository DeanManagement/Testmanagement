package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.service.TestPlanService;
import com.deanmanagement.testmanagement.project.internal.service.TestSuiteService;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The delete tools and their siblings (TM01, TM15, TM26, TM27, TM29, TM32): each delegates to the
 * service the UI uses, so these assert what an agent sees — what goes, what stays, and what is refused.
 */
class McpDeleteToolsApiTest extends McpToolApiTestSupport {

    @Autowired private TestPlanningTools planningTools;
    @Autowired private TestPlanningMaintenanceTools planningMaintenanceTools;
    @Autowired private TestRunMaintenanceTools runMaintenanceTools;
    @Autowired private ProjectDiscoveryTools discoveryTools;
    @Autowired private ParameterSetTools parameterSetTools;
    @Autowired private RequirementTools requirementTools;
    @Autowired private TestPlanService testPlanService;
    @Autowired private TestSuiteService testSuiteService;

    // --- test cases ------------------------------------------------------------------------

    @Test
    void deletingACaseThatNeverRanRemovesIt() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");

        McpDtos.Deleted deleted = testCaseTools.deleteTestCase(login.key());

        assertThat(deleted.name()).isEqualTo(login.key());
        assertThatThrownBy(() -> testCaseTools.getTestCase(login.key()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletingACaseWithResultsIsRefusedNamingTheRun() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        McpDtos.CreatedTestRun run = runOf(login);
        resultRecordingTools.recordTestResult(run.key(), TestResultStatus.PASSED, login.id(), null,
                null, null, null, null);

        assertThatThrownBy(() -> testCaseTools.deleteTestCase(login.key()))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining(run.key())
                .hasMessageContaining("DEPRECATED");
        assertThat(testCaseTools.getTestCase(login.key()).id()).isEqualTo(login.id());
    }

    @Test
    void aViewerKeyCannotDelete() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        authenticateAs(project, ProjectRole.VIEWER);

        assertThatThrownBy(() -> testCaseTools.deleteTestCase(login.key()))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("TESTER");
    }

    @Test
    void aCaseInAnotherProjectCannotBeDeleted() {
        authenticateAs(otherProject, ProjectRole.TESTER);
        McpDtos.CreatedTestCase foreign = createCase("Foreign");
        authenticateAs(project, ProjectRole.TESTER);

        assertThatThrownBy(() -> testCaseTools.deleteTestCase(foreign.id().toString()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- folders, suites, plans ------------------------------------------------------------

    @Test
    void deletingAFolderMovesItsCasesToTheRootAndItsSubfoldersUp() {
        authenticateAs(project, ProjectRole.TESTER);
        UUID backend = discoveryTools.createTestCaseFolder("Backend", null).id();
        UUID auth = discoveryTools.createTestCaseFolder("Auth", backend).id();
        McpDtos.CreatedTestCase login = testCaseTools.createTestCase("Login", Priority.LOW, null, null,
                null, null, null, auth, null, null, null);

        discoveryTools.deleteTestCaseFolder(auth);

        assertThat(testCaseTools.getTestCase(login.key()).folderId()).isNull();
        assertThat(discoveryTools.listTestCaseFolders().folders())
                .extracting(McpDtos.Folder::name).containsExactly("Backend");
    }

    @Test
    void deletingASuiteKeepsItsCases() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        UUID suiteId = planningTools.createTestSuite("Smoke", null, Set.of(login.id())).id();

        planningMaintenanceTools.deleteTestSuite(suiteId);

        assertThatThrownBy(() -> testSuiteService.findById(project.getId(), suiteId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(testCaseTools.getTestCase(login.key()).id()).isEqualTo(login.id());
    }

    @Test
    void deletingAPlanKeepsItsRuns() {
        authenticateAs(project, ProjectRole.TESTER);
        UUID planId = planningTools.createTestPlan("Release 3", null, null).id();
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Run", null, null, null, planId);

        planningMaintenanceTools.deleteTestPlan(planId);

        assertThatThrownBy(() -> testPlanService.findById(project.getId(), planId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(testRunReadTools.getTestRun(run.key(), null).id()).isEqualTo(run.id());
    }

    @Test
    void anEmptyStringClearsAPlansTargetDate() {
        authenticateAs(project, ProjectRole.TESTER);
        UUID planId = planningTools.createTestPlan("Release 3", null, LocalDate.of(2026, 12, 1)).id();

        McpDtos.PlanSummary updated = planningMaintenanceTools.updateTestPlan(planId, null, null, null, "");

        assertThat(updated.targetDate()).isNull();
        assertThat(updated.name()).isEqualTo("Release 3");
    }

    @Test
    void aMalformedTargetDateIsRefused() {
        authenticateAs(project, ProjectRole.TESTER);
        UUID planId = planningTools.createTestPlan("Release 3", null, null).id();

        assertThatThrownBy(() -> planningMaintenanceTools.updateTestPlan(planId, null, null, null, "next week"))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }

    // --- runs ------------------------------------------------------------------------------

    @Test
    void deletingARunRemovesItAndFreesItsCasesForDeletion() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        McpDtos.CreatedTestRun run = runOf(login);
        resultRecordingTools.recordTestResult(run.key(), TestResultStatus.FAILED, login.id(), null,
                null, null, null, null);

        runMaintenanceTools.deleteTestRun(run.key());

        assertThatThrownBy(() -> testRunReadTools.getTestRun(run.key(), null))
                .isInstanceOf(RuntimeException.class);
        assertThat(testCaseTools.deleteTestCase(login.key()).id()).isEqualTo(login.id());
    }

    @Test
    void aCompletedRunCanBeReopenedWithAReason() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestRun run = runOf(createCase("Login"));
        testRunWriteTools.completeTestRun(run.key(), null, null);

        McpDtos.UpdatedTestRun reopened = runMaintenanceTools.reopenTestRun(run.key(), "Fix deployed");

        assertThat(reopened.status()).isEqualTo(TestRunStatus.IN_PROGRESS);
    }

    @Test
    void reopeningARunThatIsNotClosedIsRefused() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestRun run = runOf(createCase("Login"));

        assertThatThrownBy(() -> runMaintenanceTools.reopenTestRun(run.key(), "Why not"))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("PLANNED");
    }

    @Test
    void reopeningNeedsAReason() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestRun run = runOf(createCase("Login"));
        testRunWriteTools.completeTestRun(run.key(), null, null);

        assertThatThrownBy(() -> runMaintenanceTools.reopenTestRun(run.key(), " "))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("reason");
    }

    // --- parameter sets, requirements ------------------------------------------------------

    @Test
    void deletingAParameterSetLeavesTheOthers() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login as {user}");
        UUID admin = parameterSetTools.createParameterSet(login.key(), "admin", Map.of("user", "admin")).id();
        parameterSetTools.createParameterSet(login.key(), "guest", Map.of("user", "guest"));

        parameterSetTools.deleteParameterSet(login.key(), admin);

        assertThat(parameterSetTools.listParameterSets(login.key()).parameterSets())
                .extracting(McpDtos.ParameterSet::name).containsExactly("guest");
    }

    @Test
    void deletingARequirementKeepsItsCases() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        UUID requirementId = requirementTools.createRequirement("REQ-1", "Users can log in", null).id();
        requirementTools.linkTestCasesToRequirement(requirementId, java.util.List.of(login.id()));

        McpDtos.Deleted deleted = requirementTools.deleteRequirement("REQ-1");

        assertThat(deleted.id()).isEqualTo(requirementId);
        assertThat(requirementTools.listRequirements(null, null, null).totalElements()).isZero();
        assertThat(testCaseTools.getTestCase(login.key()).requirements()).isEmpty();
    }
}
