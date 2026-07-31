package com.deanmanagement.testmanagement.project.internal.dto.webhook;

import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;

import java.time.Instant;
import java.util.UUID;

public record WebhookDeliveryResponse(
        UUID id,
        WebhookEventType event,
        Integer responseStatus,
        int attempt,
        Boolean success,
        String error,
        Instant createdAt
) {
}
