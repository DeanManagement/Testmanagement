package com.deanmanagement.testmanagement.project.internal.webhook;

import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;

import java.util.Map;
import java.util.UUID;

/**
 * Application event published from domain flows (run/result/bug) and consumed asynchronously by
 * {@link WebhookEventListener}. {@code data} is the event-specific payload body.
 */
public record WebhookEvent(WebhookEventType type, UUID projectId, Map<String, Object> data) {
}
