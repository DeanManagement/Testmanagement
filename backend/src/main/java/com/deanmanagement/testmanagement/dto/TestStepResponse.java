package com.deanmanagement.testmanagement.dto;

import java.util.UUID;

public record TestStepResponse(
        UUID id,
        String action,
        String expectedResult,
        int orderIndex
) {
}
