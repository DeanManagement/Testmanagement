package com.deanmanagement.testmanagement.project.internal.dto.apiKey;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateApiKeyRequest(
        @NotBlank @Size(max = 255) String name,
        // PRD-021 §4.2: every new key is bound to one project.
        @NotNull UUID projectId,
        // PRD-025 §3.2: the role the key holds on that project. Null defaults to TESTER, which is
        // what every pre-existing key was implicitly granted. ADMIN is rejected.
        ProjectRole role
) {
}
