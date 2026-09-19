package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;

import java.util.Collection;

/**
 * The one pass-rate definition (PRD-049): PASSED of the <em>executed</em> results, where executed is
 * every status but PENDING. SKIPPED counts as executed, since someone decided to skip it. A pending
 * result is progress not yet made, not a failure, so it is left out of the pass rate and counted by
 * {@link #progress()} instead.
 *
 * <p>The release gate (PRD-037) deliberately does not use this: it wants "passed of in-scope", where
 * an unexecuted test is no evidence.
 */
public record PassRate(int passed, int executed, int total) {

    private static final double HUNDRED = 100.0;

    public static PassRate of(Collection<TestResultStatus> statuses) {
        int passed = 0;
        int executed = 0;
        for (TestResultStatus status : statuses) {
            if (status != TestResultStatus.PENDING) {
                executed++;
            }
            if (status == TestResultStatus.PASSED) {
                passed++;
            }
        }
        return new PassRate(passed, executed, statuses.size());
    }

    /** Percent with two decimals; null when nothing was executed, which is not the same as 0 %. */
    public Double percent() {
        return executed == 0 ? null : round(passed, executed);
    }

    /** Executed of total in percent, two decimals; null when there is nothing to execute. */
    public Double progress() {
        return total == 0 ? null : round(executed, total);
    }

    private static double round(int part, int whole) {
        return Math.round(part * HUNDRED * HUNDRED / whole) / HUNDRED;
    }
}
