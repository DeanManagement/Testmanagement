package com.deanmanagement.testmanagement.project.internal.dto;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TestResultResponse(
        UUID id,
        UUID testCaseId,
        String testCaseTitle,
        TestResultStatus status,
        String comment,
        String defectLink,
        List<StepResultResponse> stepResults,
        Instant createdAt,
        Instant updatedAt
) {
}
