package com.deanmanagement.testmanagement.project.internal.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus.BLOCKED;
import static com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus.FAILED;
import static com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus.PASSED;
import static com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus.PENDING;
import static com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;

/** PRD-049: the one pass-rate definition. */
class PassRateTest {

    @Test
    void nothingAtAllHasNeitherPassRateNorProgress() {
        PassRate rate = PassRate.of(List.of());

        assertThat(rate.percent()).isNull();
        assertThat(rate.progress()).isNull();
    }

    @Test
    void onlyPendingHasNoPassRateAndNoProgress() {
        PassRate rate = PassRate.of(List.of(PENDING, PENDING));

        assertThat(rate.percent()).isNull();
        assertThat(rate.progress()).isEqualTo(0.0);
    }

    @Test
    void pendingResultsCountTowardProgressOnly() {
        PassRate rate = PassRate.of(List.of(PASSED, PENDING, PENDING, PENDING));

        assertThat(rate.percent()).isEqualTo(100.0);
        assertThat(rate.progress()).isEqualTo(25.0);
    }

    @Test
    void skippedIsExecutedButNotPassed() {
        PassRate rate = PassRate.of(List.of(PASSED, FAILED, BLOCKED, SKIPPED));

        assertThat(rate.executed()).isEqualTo(4);
        assertThat(rate.percent()).isEqualTo(25.0);
    }

    @Test
    void allSkippedIsAnHonestZero() {
        assertThat(PassRate.of(List.of(SKIPPED, SKIPPED)).percent()).isEqualTo(0.0);
    }

    @Test
    void roundsToTwoDecimals() {
        assertThat(PassRate.of(List.of(PASSED, FAILED, FAILED)).percent()).isEqualTo(33.33);
        assertThat(new PassRate(11, 11, 105).progress()).isEqualTo(10.48);
    }
}
