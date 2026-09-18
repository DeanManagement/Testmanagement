package com.deanmanagement.testmanagement.project.internal.dto.webhook;

import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookFormat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Webhook representation returned to clients. The secret is never included, and a chat-format
 * {@code url} is masked because the URL itself is the credential (PRD-031).
 */
public record WebhookResponse(
        UUID id,
        String url,
        Set<WebhookEventType> events,
        boolean active,
        WebhookFormat format,
        Instant createdAt,
        Instant updatedAt
) {
}
