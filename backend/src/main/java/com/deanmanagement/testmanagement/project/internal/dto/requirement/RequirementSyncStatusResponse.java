package com.deanmanagement.testmanagement.project.internal.dto.requirement;

import java.time.Instant;

/** What any member may know about TM19 sync: whether it is on, and when it last ran or failed. */
public record RequirementSyncStatusResponse(boolean enabled, Instant lastSyncedAt, String lastError) {
}
