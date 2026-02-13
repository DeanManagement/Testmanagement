package com.deanmanagement.testmanagement.project.internal.dto.audit;

import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;

import java.time.Instant;
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
        Instant createdAt
) {
}
