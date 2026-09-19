package com.deanmanagement.testmanagement.project.internal.dto.sharedStep;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SharedStepResponse(
        UUID id,
        String title,
        String description,
        List<TestStepResponse> steps,
        /* How many test cases reference this block: what an edit reaches. */
        long usedByCount,
        Instant createdAt,
        Instant updatedAt
) {
}
