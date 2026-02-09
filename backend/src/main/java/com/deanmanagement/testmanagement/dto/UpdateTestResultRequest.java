package com.deanmanagement.testmanagement.dto;

import com.deanmanagement.testmanagement.entity.TestResultStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTestResultRequest(
        @NotNull TestResultStatus status,
        String comment,
        String defectLink
) {
}
