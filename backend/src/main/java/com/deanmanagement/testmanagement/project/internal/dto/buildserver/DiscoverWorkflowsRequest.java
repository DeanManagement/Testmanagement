package com.deanmanagement.testmanagement.project.internal.dto.buildserver;

import jakarta.validation.constraints.Size;

/**
 * Ask a server what can be picked. {@code target} defaults to workflows; {@code repoRef} is
 * optional where the provider lists globally, and branches also need the chosen {@code workflowRef}.
 */
public record DiscoverWorkflowsRequest(
        @Size(max = 300) String repoRef,
        @Size(max = 300) String workflowRef,
        DiscoveryTarget target
) {
}
