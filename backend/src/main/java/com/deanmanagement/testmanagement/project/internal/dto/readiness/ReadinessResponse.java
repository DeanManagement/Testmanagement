package com.deanmanagement.testmanagement.project.internal.dto.readiness;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Whether a test plan meets its release gate (PRD-037). Computed on read, never stored. Only
 * configured criteria are listed; a NO_GO names the ones that failed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadinessResponse(
        UUID planId,
        String planName,
        Verdict verdict,
        Instant evaluatedAt,
        List<Criterion> criteria,
        Counts counts
) {
    public enum Verdict { GO, NO_GO, NO_CRITERIA }

    public enum Outcome { PASS, FAIL, NOT_APPLICABLE }

    public enum CriterionName { PASS_RATE, BLOCKER_BUGS, COVERAGE, FLAKY_TESTS }

    /** @param actual null when the criterion does not apply, e.g. coverage without requirements */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Criterion(CriterionName name, @Nullable BigDecimal actual, BigDecimal threshold, Outcome outcome) {
    }

    /** The latest result per case and parameter set across the plan's non-aborted runs. */
    public record Counts(int considered, int passed, int failed, int blocked, int skipped, int pending) {
    }
}
