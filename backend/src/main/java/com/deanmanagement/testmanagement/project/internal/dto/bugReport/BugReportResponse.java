package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;

import java.time.Instant;
import java.util.UUID;

public record BugReportResponse(
        UUID id,
        String title,
        String description,
        String stepsToReproduce,
        String expectedBehavior,
        String actualBehavior,
        Priority priority,
        BugReportStatus status,
        String environment,
        UUID projectId,
        UUID testResultId,
        String testCaseTitle,
        UUID testRunId,
        String testRunName,
        UUID assigneeId,
        String assigneeName,
        UUID createdBy,
        String reporterName,
        Instant createdAt,
        Instant updatedAt,
        String projectKey
) {
}
