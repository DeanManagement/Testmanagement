package com.deanmanagement.testmanagement.project.internal.dto.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;

import java.time.Instant;
import java.util.UUID;

/**
 * The config as returned to clients. There is deliberately no token field of any kind — not even a
 * masked one — so the secret cannot leak through this DTO; {@code tokenSet} tells the UI whether to
 * render "replace token" or "add token".
 */
public record IssueTrackerConfigResponse(
        UUID id,
        IssueTrackerProviderType provider,
        String baseUrl,
        String projectRef,
        boolean active,
        boolean tokenSet,
        String lastError,
        Instant lastErrorAt,
        Instant updatedAt
) {
}
