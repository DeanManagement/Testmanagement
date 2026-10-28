package com.deanmanagement.testmanagement.project.internal.dto;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record UpdateTestResultRequest(
        @NotNull TestResultStatus status,
        String comment,
        String defectLink,
        /* PRD-036: measured effort in milliseconds; null leaves it unchanged. */
        @PositiveOrZero Long durationMs,
        /* PRD-048: with PASSED or SKIPPED, the steps still PENDING take the same status. */
        Boolean cascadeSteps,
        /* TM24: a project result status; when set, its category replaces status. */
        UUID customStatusId
) {
    public UpdateTestResultRequest(TestResultStatus status, String comment, String defectLink, Long durationMs,
                                   Boolean cascadeSteps) {
        this(status, comment, defectLink, durationMs, cascadeSteps, null);
    }

    public UpdateTestResultRequest(TestResultStatus status, String comment, String defectLink) {
        this(status, comment, defectLink, null, null);
    }

    public UpdateTestResultRequest(TestResultStatus status, String comment, String defectLink, Long durationMs) {
        this(status, comment, defectLink, durationMs, null);
    }
}
