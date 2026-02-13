package com.deanmanagement.testmanagement.project.internal.dto;

public record CompletionInfoResponse(
        int total,
        int passed,
        int failed,
        int blocked,
        int skipped,
        int pending,
        String worstStatus
) {
}
