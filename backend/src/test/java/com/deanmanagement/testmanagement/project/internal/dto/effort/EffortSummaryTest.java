package com.deanmanagement.testmanagement.project.internal.dto.effort;

import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The one effort calculation behind the run detail, run report and plan summary (PRD-036 §3.2). */
class EffortSummaryTest {

    private static TestCase caseWith(Integer estimateMinutes) {
        TestCase testCase = new TestCase();
        testCase.setEstimateMinutes(estimateMinutes);
        return testCase;
    }

    private static TestResult result(TestCase testCase, TestResultStatus status, Long durationMs) {
        TestResult result = new TestResult();
        result.setTestCase(testCase);
        result.setStatus(status, null);
        result.setDurationMs(durationMs);
        return result;
    }

    @Test
    void nothingToSummariseIsAllZero() {
        assertThat(EffortSummary.of(List.of())).isEqualTo(new EffortSummary(0, 0, 0, 0));
    }

    @Test
    void estimatedCountsEveryResultAndRemainingOnlyThePendingOnes() {
        EffortSummary effort = EffortSummary.of(List.of(
                result(caseWith(30), TestResultStatus.PASSED, null),
                result(caseWith(20), TestResultStatus.FAILED, null),
                result(caseWith(45), TestResultStatus.PENDING, null)));

        assertThat(effort.estimatedMinutes()).isEqualTo(95);
        assertThat(effort.remainingMinutes()).isEqualTo(45);
    }

    @Test
    void aParameterizedCaseCountsOncePerResult() {
        TestCase parameterized = caseWith(10);

        EffortSummary effort = EffortSummary.of(List.of(
                result(parameterized, TestResultStatus.PENDING, null),
                result(parameterized, TestResultStatus.PENDING, null),
                result(parameterized, TestResultStatus.PASSED, null)));

        assertThat(effort.estimatedMinutes()).isEqualTo(30);
        assertThat(effort.remainingMinutes()).isEqualTo(20);
    }

    @Test
    void pendingResultsWithoutAnEstimateAreCountedSeparately() {
        EffortSummary effort = EffortSummary.of(List.of(
                result(caseWith(null), TestResultStatus.PENDING, null),
                result(caseWith(null), TestResultStatus.PENDING, null),
                result(caseWith(null), TestResultStatus.PASSED, null),
                result(caseWith(15), TestResultStatus.PENDING, null)));

        assertThat(effort.pendingUnestimated()).isEqualTo(2);
        assertThat(effort.remainingMinutes()).isEqualTo(15);
    }

    @Test
    void actualSumsMeasuredDurationsAndRoundsToMinutes() {
        EffortSummary effort = EffortSummary.of(List.of(
                result(caseWith(null), TestResultStatus.PASSED, 90_000L),
                result(caseWith(null), TestResultStatus.FAILED, 45_000L),
                result(caseWith(null), TestResultStatus.SKIPPED, null)));

        assertThat(effort.actualMinutes()).isEqualTo(2); // 135 s
    }

    @Test
    void effortSpentOnAResultSetBackToPendingStillCounts() {
        EffortSummary effort = EffortSummary.of(List.of(result(caseWith(10), TestResultStatus.PENDING, 600_000L)));

        assertThat(effort.actualMinutes()).isEqualTo(10);
        assertThat(effort.remainingMinutes()).isEqualTo(10);
    }
}
