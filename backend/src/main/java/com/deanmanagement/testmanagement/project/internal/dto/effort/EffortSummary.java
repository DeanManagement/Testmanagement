package com.deanmanagement.testmanagement.project.internal.dto.effort;

import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;

import java.util.Collection;

/**
 * Estimated, remaining and spent effort over a set of results (PRD-036 §3.2). The one calculation
 * behind the run detail, the run report and the plan summary, so their numbers can't disagree.
 * Estimates are read live from the case: a corrected forecast corrects the remaining effort.
 *
 * @param estimatedMinutes   estimates of all results; a parameterized case counts once per set
 * @param remainingMinutes   estimates of the results still PENDING
 * @param actualMinutes      measured durations, rounded; includes a result set back to PENDING,
 *                           because that effort was really spent
 * @param pendingUnestimated PENDING results whose case has no estimate, so that a small
 *                           "remaining" isn't mistaken for "almost done"
 */
public record EffortSummary(long estimatedMinutes, long remainingMinutes, long actualMinutes,
                            int pendingUnestimated) {

    private static final long MILLIS_PER_MINUTE = 60_000L;

    public static EffortSummary of(Collection<TestResult> results) {
        long estimated = 0;
        long remaining = 0;
        long actualMs = 0;
        int pendingUnestimated = 0;
        for (TestResult result : results) {
            Integer estimate = result.getTestCase() != null ? result.getTestCase().getEstimateMinutes() : null;
            boolean pending = result.getStatus() == TestResultStatus.PENDING;
            if (estimate != null) {
                estimated += estimate;
                remaining += pending ? estimate : 0;
            } else if (pending) {
                pendingUnestimated++;
            }
            actualMs += result.getDurationMs() != null ? result.getDurationMs() : 0;
        }
        return new EffortSummary(estimated, remaining, Math.round((double) actualMs / MILLIS_PER_MINUTE),
                pendingUnestimated);
    }
}
