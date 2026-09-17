package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.testplan.UpdateTestPlanRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;
import com.deanmanagement.testmanagement.project.internal.service.TestPlanService;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Changing what already exists — suites, plans, requirements, folders, case status, parameter
 * sets. The recurring contract is "only what you pass changes", over services that are full
 * replace, so most tests here assert on the field that was <em>not</em> passed.
 */
class McpMaintenanceToolsApiTest extends McpToolApiTestSupport {

    @Autowired
    private TestPlanningTools planningTools;
    @Autowired
    private TestPlanningMaintenanceTools planningMaintenanceTools;
    @Autowired
    private RequirementTools requirementTools;
    @Autowired
    private ProjectDiscoveryTools discoveryTools;
    @Autowired
    private ParameterSetTools parameterSetTools;
    @Autowired
    private TestPlanService testPlanService;

    // --- suites ----------------------------------------------------------------------------

    @Test
    void renamingASuiteKeepsItsDescriptionAndItsCases() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        UUID suiteId = planningTools.createTestSuite("Smoke", "Runs nightly", Set.of(login.id())).id();

        McpDtos.SuiteSummary updated =
                planningMaintenanceTools.updateTestSuite(suiteId, "Smoke tests", null);

        assertThat(updated.name()).isEqualTo("Smoke tests");
        assertThat(updated.description()).isEqualTo("Runs nightly");
        assertThat(updated.testCaseCount()).isEqualTo(1);
    }

    @Test
    void anEmptyStringClearsASuitesDescription() {
        authenticateAs(project, ProjectRole.TESTER);
        UUID suiteId = planningTools.createTestSuite("Smoke", "Runs nightly", null).id();

        McpDtos.SuiteSummary updated = planningMaintenanceTools.updateTestSuite(suiteId, null, "");

        assertThat(updated.description()).isNull();
        assertThat(updated.name()).isEqualTo("Smoke");
    }

    @Test
    void aSuiteUpdateWithNoFieldsIsRefused() {
        authenticateAs(project, ProjectRole.TESTER);
        UUID suiteId = planningTools.createTestSuite("Smoke", null, null).id();

        assertThatThrownBy(() -> planningMaintenanceTools.updateTestSuite(suiteId, null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("Nothing to update");
    }

    @Test
    void addingCasesReportsOnlyTheOnesThatWereNew() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        McpDtos.CreatedTestCase logout = createCase("Logout");
        UUID suiteId = planningTools.createTestSuite("Smoke", null, Set.of(login.id())).id();

        McpDtos.SuiteMembership result = planningMaintenanceTools.addTestCasesToSuite(suiteId,
                Set.of(login.id(), logout.id()));

        assertThat(result.testCaseCount()).isEqualTo(2);
        assertThat(result.changed()).isEqualTo(1);
    }

    @Test
    void removingACaseFromASuiteLeavesTheCaseItself() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        UUID suiteId = planningTools.createTestSuite("Smoke", null, Set.of(login.id())).id();

        McpDtos.SuiteMembership result = planningMaintenanceTools.removeTestCasesFromSuite(suiteId,
                Set.of(login.id()));

        assertThat(result.testCaseCount()).isZero();
        assertThat(result.changed()).isEqualTo(1);
        assertThat(testCaseTools.getTestCase(login.key()).title()).isEqualTo("Login");
    }

    @Test
    void anotherProjectsCaseCannotBeAddedToASuite() {
        authenticateAs(otherProject, ProjectRole.TESTER);
        McpDtos.CreatedTestCase foreign = createCase("Foreign");
        SecurityContextHolder.clearContext();
        authenticateAs(project, ProjectRole.TESTER);
        UUID suiteId = planningTools.createTestSuite("Smoke", null, null).id();

        assertThatThrownBy(() -> planningMaintenanceTools.addTestCasesToSuite(suiteId,
                Set.of(foreign.id())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addingNoCasesIsRefused() {
        authenticateAs(project, ProjectRole.TESTER);
        UUID suiteId = planningTools.createTestSuite("Smoke", null, null).id();

        assertThatThrownBy(() -> planningMaintenanceTools.addTestCasesToSuite(suiteId, Set.of()))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("testCaseIds");
    }

    // --- plans -----------------------------------------------------------------------------

    @Test
    void movingAPlansStatusKeepsEverythingElse() {
        authenticateAs(project, ProjectRole.TESTER);
        LocalDate target = LocalDate.of(2026, 12, 1);
        UUID planId = planningTools.createTestPlan("Release 3", "Regression", target).id();

        McpDtos.PlanSummary updated = planningMaintenanceTools.updateTestPlan(planId, null, null,
                TestPlanStatus.IN_PROGRESS, null);

        assertThat(updated.status()).isEqualTo(TestPlanStatus.IN_PROGRESS);
        assertThat(updated.name()).isEqualTo("Release 3");
        assertThat(updated.description()).isEqualTo("Regression");
        assertThat(updated.targetDate()).isEqualTo(target);
    }

    /**
     * {@code TestPlanService.update} reads a null assignee as "clear it", because the SPA sends the
     * whole object. The tool has no assignee argument, so without the echo every agent edit would
     * silently unassign the plan from whoever a human gave it to.
     */
    @Test
    void updatingAPlanDoesNotUnassignIt() {
        UUID agent = authenticateAs(project, ProjectRole.TESTER);
        UUID planId = planningTools.createTestPlan("Release 3", null, null).id();
        testPlanService.update(project.getId(), planId,
                new UpdateTestPlanRequest("Release 3", null, null, null, agent), agent);

        planningMaintenanceTools.updateTestPlan(planId, "Release 3.1", null, null, null);

        assertThat(testPlanService.findById(project.getId(), planId).assigneeId()).isEqualTo(agent);
    }

    @Test
    void anotherProjectsPlanCannotBeUpdated() {
        authenticateAs(otherProject, ProjectRole.TESTER);
        UUID foreignPlan = planningTools.createTestPlan("Foreign", null, null).id();
        SecurityContextHolder.clearContext();
        authenticateAs(project, ProjectRole.TESTER);

        assertThatThrownBy(() -> planningMaintenanceTools.updateTestPlan(foreignPlan, "Mine", null,
                null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- requirements ----------------------------------------------------------------------

    @Test
    void retitlingARequirementKeepsItsExternalIdAndLinks() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        UUID requirementId = requirementTools.createRequirement("REQ-1", "Users can log in",
                "Any role").id();
        requirementTools.linkTestCasesToRequirement(requirementId, List.of(login.id()));

        McpDtos.Requirement updated = requirementTools.updateRequirement(requirementId, null,
                "Users can sign in", null);

        assertThat(updated.title()).isEqualTo("Users can sign in");
        assertThat(updated.externalId()).isEqualTo("REQ-1");
        assertThat(updated.description()).isEqualTo("Any role");
        assertThat(updated.testCases()).hasSize(1);
    }

    @Test
    void unlinkingRemovesTheLinkAndIsHarmlessTwice() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        UUID requirementId = requirementTools.createRequirement("REQ-1", "Users can log in",
                null).id();
        requirementTools.linkTestCasesToRequirement(requirementId, List.of(login.id()));

        requirementTools.unlinkTestCaseFromRequirement(requirementId, login.id());
        McpDtos.Requirement again =
                requirementTools.unlinkTestCaseFromRequirement(requirementId, login.id());

        assertThat(again.testCases()).isEmpty();
        assertThat(testCaseTools.getTestCase(login.key()).title()).isEqualTo("Login");
    }

    // --- folders ---------------------------------------------------------------------------

    @Test
    void renamingAFolderKeepsItsPlaceInTheTree() {
        authenticateAs(project, ProjectRole.TESTER);
        UUID parent = discoveryTools.createTestCaseFolder("Backend", null).id();
        UUID child = discoveryTools.createTestCaseFolder("Auth", parent).id();

        McpDtos.Folder renamed = discoveryTools.renameTestCaseFolder(child, "Authentication");

        assertThat(renamed.name()).isEqualTo("Authentication");
        assertThat(renamed.parentId()).isEqualTo(parent);
    }

    @Test
    void aBlankFolderNameIsRefused() {
        authenticateAs(project, ProjectRole.TESTER);
        UUID folder = discoveryTools.createTestCaseFolder("Backend", null).id();

        assertThatThrownBy(() -> discoveryTools.renameTestCaseFolder(folder, " "))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("name");
    }

    @Test
    void aFolderCanBeMovedUnderAnotherAndBackToTheTopLevel() {
        authenticateAs(project, ProjectRole.TESTER);
        UUID backend = discoveryTools.createTestCaseFolder("Backend", null).id();
        UUID auth = discoveryTools.createTestCaseFolder("Auth", null).id();

        McpDtos.Folder nested = discoveryTools.moveTestCaseFolder(auth, backend);
        McpDtos.Folder topLevel = discoveryTools.moveTestCaseFolder(auth, null);

        assertThat(nested.parentId()).isEqualTo(backend);
        assertThat(topLevel.parentId()).isNull();
    }

    @Test
    void aFolderCannotBeMovedIntoItsOwnSubfolder() {
        authenticateAs(project, ProjectRole.TESTER);
        UUID backend = discoveryTools.createTestCaseFolder("Backend", null).id();
        UUID auth = discoveryTools.createTestCaseFolder("Auth", backend).id();

        assertThatThrownBy(() -> discoveryTools.moveTestCaseFolder(backend, auth))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("own subfolders");
    }

    @Test
    void aFolderCannotBeMovedIntoAnotherProjectsFolder() {
        authenticateAs(otherProject, ProjectRole.TESTER);
        UUID foreign = discoveryTools.createTestCaseFolder("Foreign", null).id();
        SecurityContextHolder.clearContext();
        authenticateAs(project, ProjectRole.TESTER);
        UUID mine = discoveryTools.createTestCaseFolder("Mine", null).id();

        assertThatThrownBy(() -> discoveryTools.moveTestCaseFolder(mine, foreign))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- bulk status -----------------------------------------------------------------------

    @Test
    void manyCasesCanBeActivatedAtOnce() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        McpDtos.CreatedTestCase logout = createCase("Logout");

        McpDtos.BulkStatusResult result = testCaseBulkTools.changeTestCaseStatusBulk(
                Set.of(login.id(), logout.id()), TestCaseStatus.ACTIVE);

        assertThat(result.updated()).isEqualTo(2);
        assertThat(testCaseTools.getTestCase(login.key()).status()).isEqualTo(TestCaseStatus.ACTIVE);
    }

    @Test
    void oneForeignIdMeansNoStatusIsChanged() {
        authenticateAs(otherProject, ProjectRole.TESTER);
        McpDtos.CreatedTestCase foreign = createCase("Foreign");
        SecurityContextHolder.clearContext();
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");

        assertThatThrownBy(() -> testCaseBulkTools.changeTestCaseStatusBulk(
                Set.of(login.id(), foreign.id()), TestCaseStatus.ACTIVE))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("nothing was changed");
        assertThat(testCaseTools.getTestCase(login.key()).status()).isEqualTo(TestCaseStatus.DRAFT);
    }

    // --- parameter sets --------------------------------------------------------------------

    @Test
    void aParameterSetMakesTheCaseExpandInANewRun() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login", "Log in as {username}");
        parameterSetTools.createParameterSet(login.key(), "admin", Map.of("username", "root"));
        parameterSetTools.createParameterSet(login.key(), "guest", Map.of("username", "anon"));

        McpDtos.CreatedTestRun run = runOf(login);

        assertThat(parameterSetTools.listParameterSets(login.key()).total()).isEqualTo(2);
        assertThat(testRunReadTools.getTestRun(run.key(), null).results())
                .extracting(McpDtos.TestResult::parameterSetName)
                .containsExactlyInAnyOrder("admin", "guest");
    }

    @Test
    void renamingAParameterSetKeepsItsValues() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        UUID setId = parameterSetTools.createParameterSet(login.key(), "admin",
                Map.of("username", "root")).id();

        McpDtos.ParameterSet updated =
                parameterSetTools.updateParameterSet(login.key(), setId, "administrator", null);

        assertThat(updated.name()).isEqualTo("administrator");
        assertThat(updated.values()).containsEntry("username", "root");
    }

    @Test
    void anUnknownParameterSetPointsTheAgentAtTheList() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");

        assertThatThrownBy(() -> parameterSetTools.updateParameterSet(login.key(),
                UUID.randomUUID(), "x", null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("list_parameter_sets");
    }

    @Test
    void aParameterSetNeedsValues() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");

        assertThatThrownBy(() -> parameterSetTools.createParameterSet(login.key(), "admin", null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("values");
    }

    // --- roles -----------------------------------------------------------------------------

    @Test
    void aViewerKeyCanListParameterSetsButChangeNothing() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        UUID suiteId = planningTools.createTestSuite("Smoke", null, null).id();
        SecurityContextHolder.clearContext();
        authenticateAs(project, ProjectRole.VIEWER);

        assertThat(parameterSetTools.listParameterSets(login.key()).total()).isZero();
        assertThatThrownBy(() -> planningMaintenanceTools.updateTestSuite(suiteId, "x", null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("TESTER");
        assertThatThrownBy(() -> testCaseBulkTools.changeTestCaseStatusBulk(Set.of(login.id()),
                TestCaseStatus.ACTIVE))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("TESTER");
    }
}
