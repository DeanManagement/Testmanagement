package com.deanmanagement.testmanagement.project.internal.dto.buildserver;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A workflow definition as returned to the instance admin, with its project assignments. */
public record BuildWorkflowResponse(
        UUID id,
        UUID buildServerConfigId,
        String name,
        String repoRef,
        String workflowRef,
        String defaultRef,
        Map<String, String> defaultParameters,
        boolean active,
        List<UUID> projectIds,
        Instant updatedAt
) {
}
