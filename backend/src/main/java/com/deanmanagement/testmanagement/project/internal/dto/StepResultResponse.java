package com.deanmanagement.testmanagement.project.internal.dto;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;

import java.time.Instant;
import java.util.UUID;

public record StepResultResponse(
        UUID id,
        UUID testStepId,
        String action,
        String expectedResult,
        int orderIndex,
        TestResultStatus status,
        String actualResult,
        UUID screenshotId,
        Instant createdAt,
        Instant updatedAt
) {
}
