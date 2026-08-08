package com.deanmanagement.testmanagement.project.internal.dto.buildserver;

import com.deanmanagement.testmanagement.project.internal.entity.BuildServerProviderType;

import java.util.Map;
import java.util.UUID;

/**
 * A workflow as a project member sees it (PRD-024): enough to trigger it and prefill the dialog,
 * deliberately without the server's URL, repository internals, or any credential material.
 * {@code serverName} is the admin-chosen display name, e.g. "Company GitLab".
 */
public record ProjectWorkflowResponse(
        UUID id,
        String name,
        String serverName,
        BuildServerProviderType provider,
        String defaultRef,
        Map<String, String> defaultParameters
) {
}
