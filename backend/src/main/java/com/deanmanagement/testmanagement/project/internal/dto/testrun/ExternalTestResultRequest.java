package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ExternalTestResultRequest(
        @NotNull UUID testCaseId,
        @NotNull TestResultStatus status,
        String comment,
        String defectLink,
        List<@Valid ExternalStepResultRequest> stepResults
) {
}
