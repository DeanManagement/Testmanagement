package com.deanmanagement.testmanagement.project.internal.dto.testCase;

public record BulkOperationResponse(
        int affected,
        String message
) {
}
