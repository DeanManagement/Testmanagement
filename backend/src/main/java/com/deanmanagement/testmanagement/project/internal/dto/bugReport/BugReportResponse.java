package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.BugResolution;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record BugReportResponse(
        UUID id,
        /* PRD-045: PROJ-BUG-12 */
        String key,
        String title,
        String description,
        String stepsToReproduce,
        String expectedBehavior,
        String actualBehavior,
        Priority priority,
        BugReportStatus status,
        /* PRD-045: why a resolved or closed bug is; null while open. */
        BugResolution resolution,
        UUID duplicateOfId,
        String duplicateOfKey,
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
        String projectKey,
        /* PRD-034: the exploratory session this bug was found in, if any. */
        UUID exploratorySessionId,
        String exploratorySessionKey,
        /* PRD-035: values keyed by field name, in display order (CustomFieldValueMaps). */
        Map<String, Object> customFields
) {
}
