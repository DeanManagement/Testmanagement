package com.deanmanagement.testmanagement.project.internal.dto.buildserver;

import com.deanmanagement.testmanagement.project.internal.entity.PipelineRunStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One triggered pipeline as shown in the automation panel. {@code testRunId}/{@code testRunKey}
 * are set once the pipeline has reported results back and link to the created test run.
 */
public record PipelineRunResponse(
        UUID id,
        UUID workflowId,
        String workflowName,
        PipelineRunStatus status,
        String externalRunId,
        String externalUrl,
        String triggeredRef,
        Map<String, String> parameters,
        UUID testRunId,
        String testRunKey,
        String errorMessage,
        Instant createdAt,
        Instant finishedAt
) {
}
