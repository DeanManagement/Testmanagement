package com.deanmanagement.testmanagement.dto;

import com.deanmanagement.testmanagement.entity.TestResultStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ExternalStepResultRequest(
        @NotNull UUID testStepId,
        @NotNull TestResultStatus status,
        String actualResult
) {
}
