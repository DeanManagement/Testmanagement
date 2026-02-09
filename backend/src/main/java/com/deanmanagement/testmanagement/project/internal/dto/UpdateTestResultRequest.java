package com.deanmanagement.testmanagement.project.internal.dto;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTestResultRequest(
        @NotNull TestResultStatus status,
        String comment,
        String defectLink
) {
}
