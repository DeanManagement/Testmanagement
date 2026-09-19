package com.deanmanagement.testmanagement.project.internal.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The executedAt rule of PRD-036 §3.2, which lives in {@link TestResult#setStatus}. */
class TestResultExecutedAtTest {

    @Test
    void aNewPendingResultHasNotBeenExecuted() {
        TestResult result = new TestResult();

        result.setStatus(TestResultStatus.PENDING, null);

        assertThat(result.getExecutedAt()).isNull();
    }

    @Test
    void leavingPendingStampsTheTime() {
        TestResult result = new TestResult();
        result.setStatus(TestResultStatus.PENDING, null);
        Instant before = Instant.now();

        result.setStatus(TestResultStatus.PASSED, null);

        assertThat(result.getExecutedAt()).isBetween(before, Instant.now());
    }

    @Test
    void aResultCreatedAlreadyExecutedIsStamped() {
        TestResult result = new TestResult();

        result.setStatus(TestResultStatus.FAILED, null);

        assertThat(result.getExecutedAt()).isNotNull();
    }

    @Test
    void aCorrectionKeepsTheOriginalTime() {
        TestResult result = new TestResult();
        result.setStatus(TestResultStatus.PASSED, null);
        Instant executedAt = result.getExecutedAt();

        result.setStatus(TestResultStatus.FAILED, null);

        assertThat(result.getExecutedAt()).isSameAs(executedAt);
    }

    @Test
    void returningToPendingClearsTheTimeAndKeepsTheDuration() {
        TestResult result = new TestResult();
        result.setStatus(TestResultStatus.PASSED, null);
        result.setDurationMs(90_000L);

        result.setStatus(TestResultStatus.PENDING, null);

        assertThat(result.getExecutedAt()).isNull();
        assertThat(result.getDurationMs()).isEqualTo(90_000L);
    }

    /** PRD-048: the executor goes with the time, so a later edit by someone else does not change it. */
    @Test
    void theFirstExecutorIsKeptThroughCorrectionsAndClearedByPending() {
        TestResult result = new TestResult();
        UUID tester = UUID.randomUUID();

        result.setStatus(TestResultStatus.PASSED, tester);
        result.setStatus(TestResultStatus.FAILED, UUID.randomUUID());
        assertThat(result.getExecutedBy()).isEqualTo(tester);

        result.setStatus(TestResultStatus.PENDING, UUID.randomUUID());
        assertThat(result.getExecutedBy()).isNull();
    }
}
