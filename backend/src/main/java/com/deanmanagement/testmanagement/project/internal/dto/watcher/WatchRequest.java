package com.deanmanagement.testmanagement.project.internal.dto.watcher;

import com.deanmanagement.testmanagement.project.internal.entity.WatchableEntityType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WatchRequest(
        @NotNull WatchableEntityType entityType,
        @NotNull UUID entityId
) {}
