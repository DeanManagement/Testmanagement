package com.deanmanagement.testmanagement.project.internal.dto.testplan;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * A test plan's release-gate thresholds (PRD-037). Each is optional; null means that criterion is
 * not used, and a gate whose four fields are all null has no criteria.
 *
 * @param minPassRate    effective pass rate the plan must reach, in percent
 * @param maxBlockerBugs open or in-progress CRITICAL bugs the project may have
 * @param minCoverage    requirement coverage the project must reach, in percent
 * @param maxFlaky       flaky test cases the plan may execute
 */
public record ReleaseGate(
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal minPassRate,
        @Min(0) Integer maxBlockerBugs,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal minCoverage,
        @Min(0) Integer maxFlaky
) {
    public static final ReleaseGate NONE = new ReleaseGate(null, null, null, null);

    public boolean hasCriteria() {
        return minPassRate != null || maxBlockerBugs != null || minCoverage != null || maxFlaky != null;
    }
}
