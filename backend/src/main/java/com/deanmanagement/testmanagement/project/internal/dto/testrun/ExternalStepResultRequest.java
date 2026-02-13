package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ExternalStepResultRequest(
        @NotNull @Min(1) int stepIndex,
        @NotNull TestResultStatus status,
        String actualResult
) {
}
