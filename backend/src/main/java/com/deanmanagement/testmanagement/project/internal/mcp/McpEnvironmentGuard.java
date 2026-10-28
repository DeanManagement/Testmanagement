package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.environment.EnvironmentResponse;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectEnvironment;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectEnvironmentRepository;
import com.deanmanagement.testmanagement.project.internal.service.ProjectEnvironmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Refuses an environment name the project's catalogue does not know (TM34).
 *
 * <p>{@link ProjectEnvironmentService#resolve} registers an unknown name on first use (PRD-032 §3.2)
 * so CI uploads never break, and the UI and CI keep that. An agent is different: it guesses, and
 * every guess ("staging", "Staging env", "stage") became one more entry an admin had to merge. So
 * the MCP write tools check here first and leave registering new environments to people.
 */
@Component
@RequiredArgsConstructor
class McpEnvironmentGuard {

    private final ProjectEnvironmentRepository environmentRepository;
    private final ProjectEnvironmentService environmentService;

    /** Null or blank means "no environment" (or "clear it") and always passes. */
    void requireKnown(UUID projectId, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        // Archived names still count: using one is how the service brings it back.
        if (environmentRepository.findByProjectIdAndNameNormalized(projectId,
                ProjectEnvironment.normalize(name)).isPresent()) {
            return;
        }
        String known = environmentService.list(projectId, false).stream()
                .map(EnvironmentResponse::name)
                .collect(Collectors.joining(", "));
        throw new McpToolException("Unknown environment \"" + name.strip() + "\". "
                + (known.isEmpty() ? "This project has no environments yet."
                        : "Use one of: " + known + ".")
                + " Omit environment to leave it unset, or ask a project administrator to add a "
                + "new one in the project settings.");
    }
}
