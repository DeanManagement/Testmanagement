package com.deanmanagement.testmanagement.project.internal.dto.requirement;

import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
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
        /* TM18: the parent requirement, null at the top of the tree. */
        UUID parentId,
        RequirementStatus status,
        Priority priority,
        /* PRD-035: values keyed by field name, in display order (CustomFieldValueMaps). */
        Map<String, Object> customFields,
        List<LinkedTestCase> testCases,
        Instant createdAt,
        Instant updatedAt,
        /* TM19: the tracker it is synced from (null when local), its work item, and the last sync. */
        IssueTrackerProviderType source,
        String sourceUrl,
        Instant lastSyncedAt,
        /* TM46: the bugs linked to it that are still open, by key. */
        List<OpenBug> openBugs
) {
    public record LinkedTestCase(UUID id, String key, String title) {
    }

    public record OpenBug(UUID id, String key, String title, BugReportStatus status) {
    }
}
