package com.deanmanagement.testmanagement.project.internal.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The executedAt rule of PRD-036 §3.2, which lives in {@link TestResult#setStatus}. */
class TestResultExecutedAtTest {

    @Test
    void aNewPendingResultHasNotBeenExecuted() {
        TestResult result = new TestResult();

        result.setStatus(TestResultStatus.PENDING);

        assertThat(result.getExecutedAt()).isNull();
    }

    @Test
    void leavingPendingStampsTheTime() {
        TestResult result = new TestResult();
        result.setStatus(TestResultStatus.PENDING);
        Instant before = Instant.now();

        result.setStatus(TestResultStatus.PASSED);

        assertThat(result.getExecutedAt()).isBetween(before, Instant.now());
    }

    @Test
    void aResultCreatedAlreadyExecutedIsStamped() {
        TestResult result = new TestResult();

        result.setStatus(TestResultStatus.FAILED);

        assertThat(result.getExecutedAt()).isNotNull();
    }

    @Test
    void aCorrectionKeepsTheOriginalTime() {
        TestResult result = new TestResult();
        result.setStatus(TestResultStatus.PASSED);
        Instant executedAt = result.getExecutedAt();

        result.setStatus(TestResultStatus.FAILED);

        assertThat(result.getExecutedAt()).isSameAs(executedAt);
    }

    @Test
    void returningToPendingClearsTheTimeAndKeepsTheDuration() {
        TestResult result = new TestResult();
        result.setStatus(TestResultStatus.PASSED);
        result.setDurationMs(90_000L);

        result.setStatus(TestResultStatus.PENDING);

        assertThat(result.getExecutedAt()).isNull();
        assertThat(result.getDurationMs()).isEqualTo(90_000L);
    }
}
