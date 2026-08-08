package com.deanmanagement.testmanagement.project.internal.dto.buildserver;

import java.util.List;

/**
 * Discovery result for the admin's pick-list. {@code supported} false means this provider has
 * nothing useful to enumerate and the form should fall back to manual entry — not an error.
 */
public record DiscoverWorkflowsResponse(boolean supported, List<DiscoveredWorkflowResponse> workflows) {

    public record DiscoveredWorkflowResponse(String name, String repoRef, String workflowRef,
                                             String defaultRef) {
    }
}
