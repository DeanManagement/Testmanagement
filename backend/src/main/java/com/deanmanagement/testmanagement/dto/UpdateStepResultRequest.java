package com.deanmanagement.testmanagement.dto;

import com.deanmanagement.testmanagement.entity.TestResultStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStepResultRequest(
        @NotNull TestResultStatus status,
        String actualResult
) {
}
