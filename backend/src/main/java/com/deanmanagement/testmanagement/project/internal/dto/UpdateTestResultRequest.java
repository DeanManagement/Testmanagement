package com.deanmanagement.testmanagement.project.internal.dto;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateTestResultRequest(
        @NotNull TestResultStatus status,
        String comment,
        String defectLink,
        /* PRD-036: measured effort in milliseconds; null leaves it unchanged. */
        @PositiveOrZero Long durationMs
) {
    public UpdateTestResultRequest(TestResultStatus status, String comment, String defectLink) {
        this(status, comment, defectLink, null);
    }
}
