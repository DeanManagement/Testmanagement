package com.deanmanagement.testmanagement.project.internal.dto.buildserver;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** Create or update a workflow definition on a registered build server. */
public record SaveBuildWorkflowRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 300) String repoRef,
        @Size(max = 300) String workflowRef,
        @Size(max = 200) String defaultRef,
        Map<String, String> defaultParameters,
        Boolean active
) {
}
