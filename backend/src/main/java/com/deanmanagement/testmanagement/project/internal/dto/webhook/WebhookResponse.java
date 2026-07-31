package com.deanmanagement.testmanagement.project.internal.dto.webhook;

import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Webhook representation returned to clients. The secret is never included. */
public record WebhookResponse(
        UUID id,
        String url,
        Set<WebhookEventType> events,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
