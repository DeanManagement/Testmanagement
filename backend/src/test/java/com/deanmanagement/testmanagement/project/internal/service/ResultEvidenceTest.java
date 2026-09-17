package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.StepResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResultEvidenceTest {

    private static StepResultResponse step(int orderIndex, TestResultStatus status, String actual) {
        return new StepResultResponse(UUID.randomUUID(), UUID.randomUUID(), "action", null, null,
                orderIndex, status, actual, null, null, null, null, null, null);
    }

    private static TestResultResponse result(String comment, List<StepResultResponse> steps) {
        return new TestResultResponse(UUID.randomUUID(), UUID.randomUUID(), "Checkout",
                TestResultStatus.FAILED, comment, null, 1, null, steps, null, null, null, null);
    }

    @Test
    void theCommentWinsWhenThereIsOne() {
        var result = result("Payment provider down",
                List.of(step(0, TestResultStatus.FAILED, "HTTP 500")));

        assertThat(ResultEvidence.of(result)).isEqualTo("Payment provider down");
    }

    @Test
    void withoutACommentTheFailingStepsExplainTheResult() {
        var result = result(null, List.of(
                step(0, TestResultStatus.PASSED, "Cart opened"),
                step(1, TestResultStatus.FAILED, "HTTP 500 on submit"),
                step(2, TestResultStatus.BLOCKED, "Could not continue")));

        assertThat(ResultEvidence.of(result))
                .isEqualTo("Step 2: HTTP 500 on submit\nStep 3: Could not continue");
    }

    @Test
    void stepsAreNumberedByPositionEvenWhenTheyArriveOutOfOrder() {
        var result = result("  ", List.of(
                step(7, TestResultStatus.FAILED, "second"),
                step(3, TestResultStatus.PASSED, "first")));

        assertThat(ResultEvidence.of(result)).isEqualTo("Step 2: second");
    }

    @Test
    void passedStepsAndStepsWithoutAnObservationExplainNothing() {
        var result = result(null, List.of(
                step(0, TestResultStatus.PASSED, "fine"),
                step(1, TestResultStatus.FAILED, " ")));

        assertThat(ResultEvidence.of(result)).isEqualTo("-");
    }

    @Test
    void aResultWithoutStepsOrCommentHasNoEvidence() {
        assertThat(ResultEvidence.of(result(null, null))).isEqualTo("-");
    }
}
