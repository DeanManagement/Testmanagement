package com.deanmanagement.testmanagement.dto;

import com.deanmanagement.testmanagement.entity.TestResultStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTestResultRequest(
        @NotNull UUID testCaseId,
        @NotNull TestResultStatus status,
        String comment,
        String defectLink
) {
}
