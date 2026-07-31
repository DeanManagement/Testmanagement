package com.deanmanagement.testmanagement.project.internal.dto.apiKey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateApiKeyRequest(
        @NotBlank @Size(max = 255) String name,
        // PRD-021 §4.2: every new key is bound to one project.
        @NotNull UUID projectId
) {
}
