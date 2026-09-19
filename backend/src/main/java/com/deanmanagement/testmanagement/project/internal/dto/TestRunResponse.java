package com.deanmanagement.testmanagement.project.internal.dto;

import com.deanmanagement.testmanagement.project.internal.dto.effort.EffortSummary;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TestRunResponse(
        UUID id,
        String key,
        String name,
        String environment,
        TestRunStatus status,
        Instant startTime,
        Instant endTime,
        String executorName,
        String completedByName,
        String reopenReason,
        UUID testPlanId,
        String testPlanName,
        UUID allureReportId,
        UUID projectId,
        String projectKey,
        List<TestResultResponse> results,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        /* PRD-035: values keyed by field name, in display order (CustomFieldValueMaps). */
        Map<String, Object> customFields,
        /* PRD-036 */
        EffortSummary effort,
        /* Why the run was aborted; null unless it is ABORTED. */
        String abortReason
) {
    public TestRunResponse(UUID id, String key, String name, String environment, TestRunStatus status,
                           Instant startTime, Instant endTime, String executorName, String completedByName,
                           String reopenReason, UUID testPlanId, String testPlanName, UUID allureReportId,
                           UUID projectId, String projectKey, List<TestResultResponse> results, Instant createdAt,
                           Instant updatedAt, UUID createdBy, UUID updatedBy, Map<String, Object> customFields,
                           EffortSummary effort) {
        this(id, key, name, environment, status, startTime, endTime, executorName, completedByName, reopenReason,
                testPlanId, testPlanName, allureReportId, projectId, projectKey, results, createdAt, updatedAt,
                createdBy, updatedBy, customFields, effort, null);
    }
}
