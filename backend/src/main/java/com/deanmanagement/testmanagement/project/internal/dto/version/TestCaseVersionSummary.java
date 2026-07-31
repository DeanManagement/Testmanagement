package com.deanmanagement.testmanagement.project.internal.dto.version;

import java.time.Instant;
import java.util.UUID;

/** List entry for the History tab — enough to choose two versions to compare. */
public record TestCaseVersionSummary(
        UUID id,
        int versionNumber,
        Instant versionAt,
        String title,
        UUID createdBy,
        /** True for the live state, which has no snapshot row of its own. */
        boolean current
) {
}
