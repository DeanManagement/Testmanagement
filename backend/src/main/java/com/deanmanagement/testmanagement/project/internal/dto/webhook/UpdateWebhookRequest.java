package com.deanmanagement.testmanagement.project.internal.dto.webhook;

import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Update a webhook. {@code secret} is optional: blank/null leaves the existing secret unchanged
 * (it is never returned to clients, so the UI can't echo it back).
 */
public record UpdateWebhookRequest(
        @NotBlank @Size(max = 2048) String url,
        @Size(max = 255) String secret,
        @NotEmpty Set<WebhookEventType> events,
        Boolean active
) {
}
