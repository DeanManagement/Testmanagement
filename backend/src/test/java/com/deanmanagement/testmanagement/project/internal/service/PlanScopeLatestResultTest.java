package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResultRow;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Which result a planned case shows on the plan's Scope tab, and scope coverage (PRD-053 §8). */
class PlanScopeLatestResultTest {

    private static final UUID CASE_A = UUID.randomUUID();
    private static final UUID CASE_B = UUID.randomUUID();
    private static final Instant MONDAY = Instant.parse("2026-09-14T10:00:00Z");
    private static final Instant WEDNESDAY = Instant.parse("2026-09-16T10:00:00Z");

    private static ReadinessResultRow row(UUID testCase, TestResultStatus status, Instant runAt) {
        return new ReadinessResultRow(testCase, null, status, runAt, runAt);
    }

    @Test
    void aLaterExecutionReplacesAnEarlierOne() {
        var latest = TestPlanService.latestExecutedStatus(List.of(
                row(CASE_A, TestResultStatus.PASSED, WEDNESDAY),
                row(CASE_A, TestResultStatus.FAILED, MONDAY)));

        assertThat(latest).containsEntry(CASE_A, TestResultStatus.PASSED);
    }

    @Test
    void aPendingRetestDoesNotHideTheLastExecution() {
        var latest = TestPlanService.latestExecutedStatus(List.of(
                row(CASE_A, TestResultStatus.FAILED, MONDAY),
                row(CASE_A, TestResultStatus.PENDING, WEDNESDAY)));

        assertThat(latest).containsEntry(CASE_A, TestResultStatus.FAILED);
    }

    @Test
    void aCaseWithOnlyPendingResultsIsNotExecuted() {
        var latest = TestPlanService.latestExecutedStatus(List.of(row(CASE_A, TestResultStatus.PENDING, MONDAY)));

        assertThat(latest).doesNotContainKey(CASE_A);
    }

    @Test
    void scopeCoverageIsTheExecutedShareOfTheScope() {
        BigDecimal coverage = ReleaseReadinessService.scopeCoverageOf(Set.of(CASE_A, CASE_B),
                List.of(row(CASE_A, TestResultStatus.SKIPPED, MONDAY), row(UUID.randomUUID(), TestResultStatus.PASSED, MONDAY)));

        assertThat(coverage).isEqualByComparingTo("50");
    }

    @Test
    void anEmptyScopeHasNoCoverage() {
        assertThat(ReleaseReadinessService.scopeCoverageOf(Set.of(), List.of())).isNull();
    }
}
