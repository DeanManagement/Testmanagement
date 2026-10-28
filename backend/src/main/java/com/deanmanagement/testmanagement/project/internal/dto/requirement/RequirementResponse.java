package com.deanmanagement.testmanagement.project.internal.dto.requirement;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.RequirementStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RequirementResponse(
        UUID id,
        String externalId,
        String title,
        String description,
        RequirementStatus status,
        Priority priority,
        /* PRD-035: values keyed by field name, in display order (CustomFieldValueMaps). */
        Map<String, Object> customFields,
        List<LinkedTestCase> testCases,
        Instant createdAt,
        Instant updatedAt
) {
    public record LinkedTestCase(UUID id, String key, String title) {
    }
}
