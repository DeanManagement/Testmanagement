package com.deanmanagement.testmanagement.project.internal.dto;

import java.util.UUID;

public record TestStepResponse(
        UUID id,
        String action,
        String expectedResult,
        String testData,
        int orderIndex,
        UUID imageId
) {
}
