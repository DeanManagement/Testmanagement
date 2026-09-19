package com.deanmanagement.testmanagement.project.internal.dto.audit;

import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;

import com.deanmanagement.testmanagement.project.internal.service.FieldChanges;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id,
        UUID projectId,
        UUID userId,
        String userDisplayName,
        AuditAction action,
        AuditEntityType entityType,
        UUID entityId,
        String entityName,
        String details,
        Instant createdAt,
        /* PRD-046: empty for entries written before field changes were recorded. */
        List<FieldChanges.Change> changes,
        AuditEntityType parentEntityType,
        UUID parentEntityId,
        /* Null when the object is gone or has no page of its own. */
        AuditLink link
) {
}
