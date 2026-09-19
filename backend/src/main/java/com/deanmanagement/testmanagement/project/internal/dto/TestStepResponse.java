package com.deanmanagement.testmanagement.project.internal.dto;

import java.util.List;
import java.util.UUID;

public record TestStepResponse(
        UUID id,
        String action,
        String expectedResult,
        String testData,
        int orderIndex,
        UUID imageId,
        /* PRD-030, set on a reference to a shared block: which block, its current title, and its
           steps as they will run, so a client can show the block without a second call. */
        UUID sharedStepId,
        String sharedStepTitle,
        List<TestStepResponse> expandedSteps
) {
}
