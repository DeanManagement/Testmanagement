package com.deanmanagement.testmanagement.project.internal.dto.requirement;

import java.util.List;
import java.util.UUID;

/**
 * One requirement's row in the matrix (PRD-014): every linked case with the status of its most
 * recent result.
 */
public record TraceabilityRowResponse(
        UUID requirementId,
        String externalId,
        String title,
        List<Cell> cells,
        /** Worst status across the row — what the coverage report counts. */
        CoverageStatus coverage
) {
    public record Cell(UUID testCaseId, String testCaseKey, String testCaseTitle, CoverageStatus status) {
    }

    /**
     * Result statuses plus UNTESTED. A linked case that has never run is a real and important
     * state: the requirement looks covered on paper but nothing has proved it.
     */
    public enum CoverageStatus {
        PASSED, FAILED, BLOCKED, SKIPPED, UNTESTED, UNCOVERED
    }
}
