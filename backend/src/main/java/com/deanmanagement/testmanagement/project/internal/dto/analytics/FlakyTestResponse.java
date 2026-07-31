package com.deanmanagement.testmanagement.project.internal.dto.analytics;

import java.util.UUID;

/**
 * A test case's flakiness (PRD-016).
 *
 * <p>{@code runsConsidered} is exposed alongside the score so the UI can distinguish "stable" from
 * "not enough history to say" — a case with two results and one flip would otherwise read as
 * maximally flaky.
 */
public record FlakyTestResponse(
        UUID testCaseId,
        String testCaseKey,
        String title,
        /** Proportion of consecutive PASSED/FAILED pairs that changed outcome, in [0,1]. */
        double flakyScore,
        /** Proportion of considered results that failed, in [0,1]. */
        double failRate,
        int runsConsidered,
        boolean flaky
) {
}
