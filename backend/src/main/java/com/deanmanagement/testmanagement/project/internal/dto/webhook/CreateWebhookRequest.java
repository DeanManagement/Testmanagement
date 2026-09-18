package com.deanmanagement.testmanagement.project.internal.dto.webhook;

import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Create a webhook. {@code format} defaults to {@code GENERIC}; {@code secret} is required for
 * {@code GENERIC} and generated when omitted for chat formats (PRD-031).
 */
public record CreateWebhookRequest(
        @NotBlank @Size(max = 2048) String url,
        @Size(max = 255) String secret,
        @NotEmpty Set<WebhookEventType> events,
        Boolean active,
        WebhookFormat format
) {
}
