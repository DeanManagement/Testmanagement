package com.deanmanagement.testmanagement.project.internal.dto.notification;

import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.WatchableEntityType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID projectId,
        WatchableEntityType entityType,
        UUID entityId,
        AuditAction action,
        String entityName,
        String actorName,
        boolean read,
        Instant createdAt
) {
}
