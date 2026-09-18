package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record ExternalTestResultRequest(
        @NotBlank String testCaseKey,
        @NotNull TestResultStatus status,
        String comment,
        String defectLink,
        List<@Valid ExternalStepResultRequest> stepResults,
        /* PRD-036: how long the test took, in milliseconds. */
        @PositiveOrZero Long durationMs
) {
    public ExternalTestResultRequest(String testCaseKey, TestResultStatus status, String comment, String defectLink,
                                     List<ExternalStepResultRequest> stepResults) {
        this(testCaseKey, status, comment, defectLink, stepResults, null);
    }
}
