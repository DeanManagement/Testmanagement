package com.deanmanagement.testmanagement.project.internal.dto.testCase;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * TM11: one edit applied to every selected case. Each part is optional and null leaves it alone.
 * {@code moveToFolder} true moves the cases to {@code folderId}, or to the root when that is null.
 * {@code customFields} is keyed by field name; only the names given change, a null value clears one.
 */
public record BulkEditTestCasesRequest(
        @NotNull @Size(min = 1, max = 500) Set<UUID> testCaseIds,
        Priority priority,
        Set<@Size(max = 255) String> addLabels,
        Set<String> removeLabels,
        Boolean moveToFolder,
        UUID folderId,
        Map<String, Object> customFields
) {
}
