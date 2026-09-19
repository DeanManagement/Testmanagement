package com.deanmanagement.testmanagement.project.internal.dto.audit;

import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Activity filters (PRD-046 §3.3); nulls and empty lists mean "any". {@code entityId} matches an
 * entry about that object or about something inside it, such as a comment on it.
 */
public record AuditFilter(
        UUID entityId,
        List<AuditEntityType> entityTypes,
        List<UUID> userIds,
        List<AuditAction> actions,
        Instant from,
        Instant to
) {
    public static AuditFilter forEntity(UUID entityId) {
        return new AuditFilter(entityId, List.of(), List.of(), List.of(), null, null);
    }
}
