package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record CreateTestResultRequest(
        @NotNull UUID testCaseId,
        @NotNull TestResultStatus status,
        String comment,
        String defectLink,
        /* PRD-036: measured effort in milliseconds; null leaves it unchanged. */
        @PositiveOrZero Long durationMs
) {
    public CreateTestResultRequest(UUID testCaseId, TestResultStatus status, String comment, String defectLink) {
        this(testCaseId, status, comment, defectLink, null);
    }
}
