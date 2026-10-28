package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse;
import com.deanmanagement.testmanagement.project.internal.dto.resultstatus.CreateResultStatusRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.CreateTestPlanRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.ReleaseGate;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.service.ProjectResultStatusService;
import com.deanmanagement.testmanagement.project.internal.service.ReleaseReadinessService;
import com.deanmanagement.testmanagement.project.internal.service.TestPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TM24: a project's own result statuses only rename an outcome. Not transactional, so every write
 * here has committed before it is read back.
 */
class McpCustomResultStatusApiTest extends McpToolApiTestSupport {

    @Autowired private ProjectResultStatusService resultStatusService;
    @Autowired private TestPlanService testPlanService;
    @Autowired private ReleaseReadinessService readinessService;

    private McpDtos.CreatedTestCase pay;
    private McpDtos.CreatedTestCase browse;

    @BeforeEach
    void seed() {
        resultStatusService.seedDefaults(project);
        resultStatusService.create(project.getId(),
                new CreateResultStatusRequest("Not applicable", "#123456", TestResultStatus.SKIPPED), null);
        authenticateAs(project, ProjectRole.TESTER);
        pay = createCase("Pay", "Open the cart", "Pay");
        browse = createCase("Browse", "Open the shop");
    }

    /** A plan with one run of both cases: Pay passed, Browse skipped the way {@code skip} records it. */
    private UUID planWhereBrowseIs(String name, String customStatus) {
        UUID planId = testPlanService.create(project.getId(), new CreateTestPlanRequest(name, null, null, null,
                new ReleaseGate(new BigDecimal("75"), null, null, 0)), null).id();
        McpDtos.CreatedTestRun run = testRunWriteTools.createTestRun(name, null, Set.of(pay.id(), browse.id()),
                null, planId);
        resultRecordingTools.recordTestResult(run.key(), TestResultStatus.PASSED, pay.id(), null, null, null, null,
                null, null);
        resultRecordingTools.recordTestResult(run.key(), TestResultStatus.SKIPPED, browse.id(), null, null, null, null,
                null, customStatus);
        return planId;
    }

    @Test
    void passRateAndReleaseGateAreTheSameAsForThePlainCategory() {
        UUID plain = planWhereBrowseIs("Plain", null);
        UUID custom = planWhereBrowseIs("Custom", "Not applicable");

        TestPlanSummaryResponse plainSummary = testPlanService.getSummary(project.getId(), plain);
        TestPlanSummaryResponse customSummary = testPlanService.getSummary(project.getId(), custom);
        ReadinessResponse plainGate = readinessService.readiness(project.getId(), plain);
        ReadinessResponse customGate = readinessService.readiness(project.getId(), custom);

        assertThat(customSummary.passRate()).isNotNull().isEqualTo(plainSummary.passRate());
        assertThat(customSummary.skipped()).isEqualTo(plainSummary.skipped()).isEqualTo(1);
        assertThat(customSummary.executed()).isEqualTo(plainSummary.executed());
        assertThat(customGate.verdict()).isEqualTo(plainGate.verdict());
        assertThat(customGate.criteria()).isEqualTo(plainGate.criteria());
        assertThat(customGate.counts()).isEqualTo(plainGate.counts());
    }

    @Test
    void theRunShowsTheCustomNameOverTheCategory() {
        McpDtos.CreatedTestRun run = runOf(browse);

        resultRecordingTools.recordTestResult(run.key(), TestResultStatus.SKIPPED, browse.id(), null, null, null, null,
                null, "not APPLICABLE ");

        McpDtos.TestResult result = testRunReadTools.getTestRun(run.key(), null).results().getFirst();
        assertThat(result.status()).isEqualTo(TestResultStatus.SKIPPED);
        assertThat(result.customStatus()).isEqualTo("Not applicable");
    }

    @Test
    void anUnknownNameIsRefusedWithTheValidOnes() {
        McpDtos.CreatedTestRun run = runOf(browse);

        assertThatThrownBy(() -> resultRecordingTools.recordTestResult(run.key(), TestResultStatus.SKIPPED,
                browse.id(), null, null, null, null, null, "Irrelevant"))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("\"Irrelevant\"")
                .hasMessageContaining("Not applicable (SKIPPED)");
    }

    @Test
    void aNameOfAnotherCategoryIsRefused() {
        McpDtos.CreatedTestRun run = runOf(browse);

        assertThatThrownBy(() -> resultRecordingTools.recordTestResult(run.key(), TestResultStatus.PASSED,
                browse.id(), null, null, null, null, null, "Not applicable"))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("is a SKIPPED status");
    }

    @Test
    void recordingAnotherOutcomeDropsTheCustomName() {
        McpDtos.CreatedTestRun run = runOf(browse);
        resultRecordingTools.recordTestResult(run.key(), TestResultStatus.SKIPPED, browse.id(), null, null, null, null,
                null, "Not applicable");

        resultRecordingTools.recordTestResult(run.key(), TestResultStatus.FAILED, browse.id(), null, null, null, null,
                null, null);

        McpDtos.TestResult result = testRunReadTools.getTestRun(run.key(), null).results().getFirst();
        assertThat(result.status()).isEqualTo(TestResultStatus.FAILED);
        assertThat(result.customStatus()).isNull();
    }

    @Test
    void aStepTakesACustomStatusAndTheResultStillDerivesTheCategory() {
        McpDtos.CreatedTestRun run = runOf(pay);
        stepRecordingTools.recordStepResult(run.key(), 1, TestResultStatus.PASSED, pay.id(), null, null, null);

        McpDtos.RecordedStepResult recorded = stepRecordingTools.recordStepResult(run.key(), 2,
                TestResultStatus.SKIPPED, pay.id(), null, null, "Not applicable");

        assertThat(recorded.steps().get(1).customStatus()).isEqualTo("Not applicable");
        assertThat(recorded.resultStatus()).isEqualTo(TestResultStatus.SKIPPED);
    }
}
