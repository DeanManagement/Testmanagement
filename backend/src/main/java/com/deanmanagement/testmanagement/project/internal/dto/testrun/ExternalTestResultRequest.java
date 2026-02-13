package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ExternalTestResultRequest(
        @NotBlank String testCaseKey,
        @NotNull TestResultStatus status,
        String comment,
        String defectLink,
        List<@Valid ExternalStepResultRequest> stepResults
) {
}
