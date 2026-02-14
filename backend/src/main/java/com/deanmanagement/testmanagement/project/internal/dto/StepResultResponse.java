package com.deanmanagement.testmanagement.project.internal.dto;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;

import java.time.Instant;
import java.util.UUID;

public record StepResultResponse(
        UUID id,
        UUID testStepId,
        String action,
        String expectedResult,
        String testData,
        int orderIndex,
        TestResultStatus status,
        String actualResult,
        UUID screenshotId,
        UUID stepImageId,
        Instant createdAt,
        Instant updatedAt
) {
}
