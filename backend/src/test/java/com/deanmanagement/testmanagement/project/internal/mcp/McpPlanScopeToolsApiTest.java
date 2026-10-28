package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse.CriterionName;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.CreateTestPlanRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.ReleaseGate;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanScopeResponse;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.service.ReleaseReadinessService;
import com.deanmanagement.testmanagement.project.internal.service.TestPlanService;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plan scope over MCP and REST's service (PRD-053 §8). Not transactional (see the support class), so
 * every read below proves the write before it committed.
 */
class McpPlanScopeToolsApiTest extends McpToolApiTestSupport {

    @Autowired
    private TestPlanningTools planningTools;
    @Autowired
    private TestPlanningMaintenanceTools planningMaintenanceTools;
    @Autowired
    private TestPlanService testPlanService;
    @Autowired
    private ReleaseReadinessService readinessService;

    private UUID plan(ReleaseGate gate) {
        return testPlanService.create(project.getId(),
                new CreateTestPlanRequest("Release", null, null, null, gate), null).id();
    }

    @Test
    void addedCasesAreInScopeAndNotExecutedYet() {
        UUID planId = plan(null);
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase pay = createCase("Pay");
        McpDtos.CreatedTestCase browse = createCase("Browse");

        McpDtos.PlanScopeChange change = planningMaintenanceTools.addCasesToPlan(planId, Set.of(pay.id(), browse.id()));

        assertThat(change.changed()).isEqualTo(2);
        assertThat(planningTools.getTestPlan(planId).scope())
                .isEqualTo(new TestPlanScopeResponse.Summary(2, 0, 2, 0, 0, 0, 0));
    }

    @Test
    void addingACaseAlreadyInScopeChangesNothing() {
        UUID planId = plan(null);
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase pay = createCase("Pay");
        planningMaintenanceTools.addCasesToPlan(planId, Set.of(pay.id()));

        McpDtos.PlanScopeChange change = planningMaintenanceTools.addCasesToPlan(planId, Set.of(pay.id()));

        assertThat(change.changed()).isZero();
        assertThat(change.scope().total()).isEqualTo(1);
    }

    @Test
    void scopeCountsEachCaseByItsLatestExecutedResultInThePlansRuns() {
        UUID planId = plan(null);
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase pay = createCase("Pay");
        McpDtos.CreatedTestCase browse = createCase("Browse");
        planningMaintenanceTools.addCasesToPlan(planId, Set.of(pay.id(), browse.id()));
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Run", null, Set.of(pay.id(), browse.id()),
                null, planId);
        resultRecordingTools.recordTestResult(run.id().toString(), TestResultStatus.FAILED, pay.id(), null, null, null,
                null, null);

        TestPlanScopeResponse scope = testPlanService.getScope(project.getId(), planId);

        assertThat(scope.summary()).isEqualTo(new TestPlanScopeResponse.Summary(2, 1, 1, 0, 1, 0, 0));
        assertThat(scope.cases()).filteredOn(c -> c.testCaseId().equals(browse.id()))
                .singleElement().extracting(TestPlanScopeResponse.Item::latestStatus).isNull();
    }

    @Test
    void removedCasesLeaveTheScope() {
        UUID planId = plan(null);
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase pay = createCase("Pay");
        McpDtos.CreatedTestCase browse = createCase("Browse");
        planningMaintenanceTools.addCasesToPlan(planId, Set.of(pay.id(), browse.id()));

        McpDtos.PlanScopeChange change = planningMaintenanceTools.removeCasesFromPlan(planId,
                Set.of(pay.id(), UUID.randomUUID()));

        assertThat(change.changed()).isEqualTo(1);
        assertThat(testPlanService.getScope(project.getId(), planId).cases())
                .extracting(TestPlanScopeResponse.Item::testCaseId).containsExactly(browse.id());
    }

    @Test
    void anotherProjectsCaseIsRefusedAndNothingIsAdded() {
        UUID planId = plan(null);
        authenticateAs(otherProject, ProjectRole.TESTER);
        UUID foreign = createCase("Theirs").id();
        authenticateAs(project, ProjectRole.TESTER);
        UUID own = createCase("Ours").id();

        assertThatThrownBy(() -> planningMaintenanceTools.addCasesToPlan(planId, Set.of(own, foreign)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(testPlanService.getScope(project.getId(), planId).summary().total()).isZero();
    }

    @Test
    void aViewerCannotChangeTheScope() {
        UUID planId = plan(null);
        authenticateAs(project, ProjectRole.VIEWER);

        assertThatThrownBy(() -> planningMaintenanceTools.addCasesToPlan(planId, Set.of(UUID.randomUUID())))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("TESTER");
    }

    @Test
    void anOptedInGateMeasuresCoverageAgainstTheScope() {
        UUID planId = plan(new ReleaseGate(null, null, new BigDecimal("50"), null, true));
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase pay = createCase("Pay");
        McpDtos.CreatedTestCase browse = createCase("Browse");
        planningMaintenanceTools.addCasesToPlan(planId, Set.of(pay.id(), browse.id()));
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Run", null, Set.of(pay.id()), null, planId);
        resultRecordingTools.recordTestResult(run.id().toString(), TestResultStatus.PASSED, pay.id(), null, null, null,
                null, null);

        ReadinessResponse readiness = readinessService.readiness(project.getId(), planId);

        assertThat(readiness.criteria()).singleElement().satisfies(c -> {
            assertThat(c.name()).isEqualTo(CriterionName.SCOPE_COVERAGE);
            assertThat(c.actual()).isEqualByComparingTo("50");
            assertThat(c.outcome()).isEqualTo(ReadinessResponse.Outcome.PASS);
        });
    }

    @Test
    void aGateThatDidNotOptInKeepsRequirementCoverage() {
        UUID planId = plan(new ReleaseGate(null, null, new BigDecimal("50"), null));
        authenticateAs(project, ProjectRole.TESTER);
        planningMaintenanceTools.addCasesToPlan(planId, Set.of(createCase("Pay").id()));

        ReadinessResponse readiness = readinessService.readiness(project.getId(), planId);

        assertThat(readiness.criteria()).singleElement()
                .extracting(ReadinessResponse.Criterion::name).isEqualTo(CriterionName.COVERAGE);
    }
}
