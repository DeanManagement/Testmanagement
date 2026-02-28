package com.deanmanagement.testmanagement.project.internal.dto.watcher;

import com.deanmanagement.testmanagement.project.internal.entity.WatchableEntityType;

import java.time.Instant;
import java.util.UUID;

public record WatchedItemResponse(
        WatchableEntityType entityType,
        UUID entityId,
        String name,
        String status,
        UUID projectId,
        Instant watchedAt
) {}
