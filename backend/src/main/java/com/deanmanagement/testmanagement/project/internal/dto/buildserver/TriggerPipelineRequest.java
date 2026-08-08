package com.deanmanagement.testmanagement.project.internal.dto.buildserver;

import jakarta.validation.constraints.Size;

import java.util.Map;

/** Tester's trigger call: everything is optional and defaults come from the workflow definition. */
public record TriggerPipelineRequest(
        @Size(max = 200) String ref,
        Map<String, String> parameters
) {
}
