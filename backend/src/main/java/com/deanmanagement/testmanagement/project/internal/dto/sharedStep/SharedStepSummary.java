package com.deanmanagement.testmanagement.project.internal.dto.sharedStep;

import java.time.Instant;
import java.util.UUID;

/** A row of the shared step list: no steps, just their count. */
public record SharedStepSummary(
        UUID id,
        String title,
        String description,
        int stepCount,
        long usedByCount,
        Instant updatedAt
) {
}
