package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.effort.EffortSummary;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Agents that execute tests report real durations, and can read and set estimates (PRD-036 §3.4). */
class McpTimeTrackingToolsApiTest extends McpToolApiTestSupport {

    private McpDtos.CreatedTestCase estimatedCase(String title, Integer estimateMinutes) {
        return testCaseTools.createTestCase(title, Priority.MEDIUM, null, null, null, null, null, null, null,
                estimateMinutes, null);
    }

    @Test
    void anEstimateCanBeSetReadChangedAndCleared() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase created = estimatedCase("Pay", 20);
        assertThat(testCaseTools.getTestCase(created.key()).estimateMinutes()).isEqualTo(20);

        testCaseTools.updateTestCase(created.key(), null, null, null, null, null, null, null, null, null, 35);
        assertThat(testCaseTools.getTestCase(created.key()).estimateMinutes()).isEqualTo(35);

        testCaseTools.updateTestCase(created.key(), null, null, null, null, null, null, null, null, null, 0);
        assertThat(testCaseTools.getTestCase(created.key()).estimateMinutes()).isNull();
    }

    @Test
    void aRecordedDurationShowsOnTheRunItsEffortAndTheCasesMedian() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase pay = estimatedCase("Pay", 20);
        McpDtos.CreatedTestCase browse = estimatedCase("Browse", 10);
        McpDtos.CreatedTestRun run = runOf(pay, browse);

        resultRecordingTools.recordTestResult(run.id().toString(), TestResultStatus.PASSED, pay.id(), null, null,
                null, 180_000L, null);

        McpDtos.TestRunDetail detail = testRunReadTools.getTestRun(run.key(), null);
        assertThat(detail.effort()).isEqualTo(new EffortSummary(30, 10, 3, 0));
        assertThat(detail.results()).filteredOn(r -> r.testCaseId().equals(pay.id()))
                .extracting(McpDtos.TestResult::durationMs).containsExactly(180_000L);
        assertThat(testCaseTools.getTestCase(pay.key()).medianActualMs()).isEqualTo(180_000L);
    }

    @Test
    void bulkRecordingCarriesDurationsToo() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase pay = estimatedCase("Pay", null);
        McpDtos.CreatedTestRun run = runOf(pay);

        resultRecordingTools.recordTestResults(run.id().toString(), List.of(
                new McpDtos.ResultEntry(TestResultStatus.PASSED, pay.id(), null, null, null, 60_000L)));

        assertThat(testRunReadTools.getTestRun(run.key(), null).effort().actualMinutes()).isEqualTo(1);
    }
}
