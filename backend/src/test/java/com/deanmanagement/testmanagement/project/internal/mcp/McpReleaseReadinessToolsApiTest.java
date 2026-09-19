package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse.CriterionName;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse.Outcome;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse.Verdict;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.CreateTestPlanRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.ReleaseGate;
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
import static org.assertj.core.api.Assertions.tuple;

/** An agent can ask "is release X ready?" and gets what REST would say (PRD-037 §3.5). */
class McpReleaseReadinessToolsApiTest extends McpToolApiTestSupport {

    @Autowired
    private ReportingTools reportingTools;
    @Autowired
    private TestPlanService testPlanService;
    @Autowired
    private ReleaseReadinessService readinessService;

    private UUID planWithGate() {
        return testPlanService.create(project.getId(), new CreateTestPlanRequest("Release", null, null, null,
                new ReleaseGate(new BigDecimal("50"), null, null, 0)), null).id();
    }

    @Test
    void returnsTheSameVerdictAsRest() {
        UUID planId = planWithGate();
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase pay = createCase("Pay");
        McpDtos.CreatedTestCase browse = createCase("Browse");
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun("Run", null, Set.of(pay.id(), browse.id()),
                null, planId);
        resultRecordingTools.recordTestResult(run.id().toString(), TestResultStatus.PASSED, pay.id(), null, null, null,
                null, null);
        authenticateAs(project, ProjectRole.VIEWER);

        ReadinessResponse viaMcp = reportingTools.getReleaseReadiness(planId);

        ReadinessResponse viaRest = readinessService.readiness(project.getId(), planId);
        assertThat(viaMcp.verdict()).isEqualTo(Verdict.GO).isEqualTo(viaRest.verdict());
        assertThat(viaMcp.criteria()).isEqualTo(viaRest.criteria());
        assertThat(viaMcp.criteria()).extracting(ReadinessResponse.Criterion::name, ReadinessResponse.Criterion::outcome)
                .containsExactly(tuple(CriterionName.PASS_RATE, Outcome.PASS),
                        tuple(CriterionName.FLAKY_TESTS, Outcome.PASS));
    }

    @Test
    void anotherProjectsPlanIsNotFound() {
        UUID foreignPlan = testPlanService.create(otherProject.getId(),
                new CreateTestPlanRequest("Theirs", null, null, null), null).id();
        authenticateAs(project, ProjectRole.VIEWER);

        assertThatThrownBy(() -> reportingTools.getReleaseReadiness(foreignPlan))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
