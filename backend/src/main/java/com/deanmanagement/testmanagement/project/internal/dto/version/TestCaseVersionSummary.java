package com.deanmanagement.testmanagement.project.internal.dto.version;

import java.time.Instant;
import java.util.UUID;

/** List entry for the History tab — enough to choose two versions to compare. */
public record TestCaseVersionSummary(
        UUID id,
        int versionNumber,
        /** When the snapshot was taken, i.e. when this version was replaced; kept for API clients. */
        Instant versionAt,
        String title,
        UUID createdBy,
        /** True for the live state, which has no snapshot row of its own. */
        boolean current,
        /** When this version became the case's wording: the previous version's end, or creation. */
        Instant validFrom,
        /** When it was replaced; null for the live version. */
        Instant validUntil
) {
}
