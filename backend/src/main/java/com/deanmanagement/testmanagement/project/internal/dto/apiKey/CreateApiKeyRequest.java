package com.deanmanagement.testmanagement.project.internal.dto.apiKey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiKeyRequest(
        @NotBlank @Size(max = 255) String name
) {
}
