package com.deanmanagement.testmanagement.project.internal.dto.testCase;

import java.util.List;
import java.util.UUID;

/**
 * Where a test case sits and who made it (PRD-050): kept out of {@link TestCaseResponse}, which is
 * also the list payload, so the list does not pay a lookup per row for what only the case page shows.
 *
 * @param createdByName null for a user who no longer exists
 * @param folderPath    root first; empty when the case is in no folder
 * @param suites        by name
 */
public record TestCaseContextResponse(
        String createdByName,
        String updatedByName,
        List<Ref> folderPath,
        List<Ref> suites
) {
    public record Ref(UUID id, String name) {
    }
}
