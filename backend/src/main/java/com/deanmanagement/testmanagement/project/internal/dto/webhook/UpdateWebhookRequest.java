package com.deanmanagement.testmanagement.project.internal.dto.webhook;

import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookFormat;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Update a webhook. {@code url}, {@code secret} and {@code format} are optional: blank/null keeps
 * the stored value. The secret is never returned, and chat URLs are returned masked, so the UI
 * can't echo either back.
 */
public record UpdateWebhookRequest(
        @Size(max = 2048) String url,
        @Size(max = 255) String secret,
        @NotEmpty Set<WebhookEventType> events,
        Boolean active,
        WebhookFormat format
) {
}
