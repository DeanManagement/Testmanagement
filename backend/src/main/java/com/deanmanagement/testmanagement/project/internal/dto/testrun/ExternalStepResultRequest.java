package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ExternalStepResultRequest(
        @NotNull UUID testStepId,
        @NotNull TestResultStatus status,
        String actualResult
) {
}
