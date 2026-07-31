package com.deanmanagement.testmanagement.project.internal.dto.webhook;

import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateWebhookRequest(
        @NotBlank @Size(max = 2048) String url,
        @NotBlank @Size(max = 255) String secret,
        @NotEmpty Set<WebhookEventType> events,
        Boolean active
) {
}
