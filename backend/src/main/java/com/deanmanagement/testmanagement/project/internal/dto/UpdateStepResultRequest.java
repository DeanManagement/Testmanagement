package com.deanmanagement.testmanagement.project.internal.dto;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateStepResultRequest(
        @NotNull TestResultStatus status,
        String actualResult,
        /* TM24: a project result status; when set, its category replaces status. */
        UUID customStatusId
) {
    public UpdateStepResultRequest(TestResultStatus status, String actualResult) {
        this(status, actualResult, null);
    }
}
