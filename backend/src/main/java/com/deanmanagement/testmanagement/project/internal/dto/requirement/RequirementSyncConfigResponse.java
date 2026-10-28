package com.deanmanagement.testmanagement.project.internal.dto.requirement;

import java.time.Instant;

/** TM19 sync settings plus the outcome of the last sync, for the admin settings card. */
public record RequirementSyncConfigResponse(
        boolean enabled,
        boolean scheduled,
        String query,
        String externalIdPrefix,
        String approvedStates,
        String obsoleteStates,
        Instant lastSyncedAt,
        String lastError,
        Instant lastErrorAt
) {
}
