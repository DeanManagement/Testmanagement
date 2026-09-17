package com.deanmanagement.testmanagement.project.internal.dto.apiKey;

import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateApiKeyRequest(
        @NotBlank @Size(max = 255) String name,
        // PRD-021 §4.2: every new key is bound to one project.
        @NotNull UUID projectId,
        // PRD-025 §3.2: the role the key holds on that project. Null defaults to TESTER, which is
        // what every pre-existing key was implicitly granted. ADMIN is rejected.
        ProjectRole role,
        // PRD-027 §9: the MCP tool groups the key may use. Null or empty means all of them. CORE is
        // implicit and rejected here, as is a group the key's role could never use.
        Set<McpToolGroup> mcpToolGroups
) {

    /** An unrestricted key — the only kind there was before tool groups. */
    public CreateApiKeyRequest(String name, UUID projectId, ProjectRole role) {
        this(name, projectId, role, null);
    }
}
