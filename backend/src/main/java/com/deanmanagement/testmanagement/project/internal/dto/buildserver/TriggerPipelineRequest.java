package com.deanmanagement.testmanagement.project.internal.dto.buildserver;

import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/** Tester's trigger call: everything is optional and defaults come from the workflow definition. */
public record TriggerPipelineRequest(
        @Size(max = 200) String ref,
        Map<String, String> parameters,
        /* PRD-032: passed to the workflow as TM_ENVIRONMENT; the reported-back run inherits it. */
        UUID environmentId
) {
    public TriggerPipelineRequest(String ref, Map<String, String> parameters) {
        this(ref, parameters, null);
    }
}
