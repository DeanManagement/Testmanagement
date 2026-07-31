package com.deanmanagement.testmanagement.project.internal.dto.requirement;

/**
 * Coverage at a glance (PRD-014 §3.2).
 *
 * <p>{@code coveragePercent} counts requirements whose linked tests have actually passed — not
 * merely those with a test attached. "A test exists" and "a test proves it works" are different
 * claims, and only the second is worth reporting.
 */
public record CoverageSummaryResponse(
        long totalRequirements,
        long uncovered,
        long untested,
        long failing,
        long passing,
        double coveragePercent
) {
}
