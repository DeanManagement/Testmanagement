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
        /* PRD-030: the shared block this step belongs to, null for a case's own step. Consecutive
           steps with the same title are one block, shown under one heading. */
        String sharedStepTitle,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
) {
    public StepResultResponse(UUID id, UUID testStepId, String action, String expectedResult, String testData,
                              int orderIndex, TestResultStatus status, String actualResult, UUID screenshotId,
                              UUID stepImageId, Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy) {
        this(id, testStepId, action, expectedResult, testData, orderIndex, status, actualResult, screenshotId,
                stepImageId, null, createdAt, updatedAt, createdBy, updatedBy);
    }
}
