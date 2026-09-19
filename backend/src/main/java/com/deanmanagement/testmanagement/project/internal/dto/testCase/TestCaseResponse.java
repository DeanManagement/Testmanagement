package com.deanmanagement.testmanagement.project.internal.dto.testCase;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepResponse;
import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record TestCaseResponse(
        UUID id,
        String key,
        String title,
        String description,
        String preconditions,
        Priority priority,
        TestCaseStatus status,
        Set<String> labels,
        List<TestStepResponse> steps,
        UUID folderId,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        int currentVersion,
        /* PRD-033: null when never approved (or approved before review was switched on). */
        UUID approvedBy,
        Instant approvedAt,
        Integer approvedVersion,
        /* PRD-035: values keyed by field name, in display order (CustomFieldValueMaps). */
        Map<String, Object> customFields,
        /* PRD-036 */
        Integer estimateMinutes,
        /* PRD-036: median measured duration of the last 5 executions; on the detail response only. */
        Long medianActualMs,
        /* PRD-044: metadata only, never bytes; null on write responses, where nothing reads it. */
        List<AttachmentSummary> attachments
) {
}
