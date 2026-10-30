package com.deanmanagement.testmanagement.project.internal.dto.watcher;

import com.deanmanagement.testmanagement.project.internal.entity.WatchableEntityType;

import java.time.Instant;
import java.util.UUID;

public record WatchedItemResponse(
        WatchableEntityType entityType,
        UUID entityId,
        String name,
        String status,
        /* TM47: a bug's own status name; null for plans, runs and bugs without one. */
        String statusName,
        UUID projectId,
        Instant watchedAt
) {}
