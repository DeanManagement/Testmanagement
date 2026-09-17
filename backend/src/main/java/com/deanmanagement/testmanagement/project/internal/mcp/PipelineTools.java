package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.buildserver.PipelineRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.TriggerPipelineRequest;
import com.deanmanagement.testmanagement.project.internal.service.BuildWorkflowService;
import com.deanmanagement.testmanagement.project.internal.service.PipelineRunService;
import com.deanmanagement.testmanagement.shared.PageableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Starting an automated test pipeline on a build server and following it to its results.
 *
 * <p>Which workflows exist, and which projects may trigger them, is an instance administrator's
 * decision made in the UI; an agent can only trigger what its project has been assigned.
 *
 * <p>None of these methods is {@code @Transactional}, deliberately. {@code PipelineRunService}
 * calls out to the build server and then records a failed run when that call throws; inside a
 * caller's transaction that record would roll back with the exception, and the network call would
 * hold a database connection for its duration.
 */
@Service
@RequiredArgsConstructor
public class PipelineTools {

    private final McpCallerContext callerContext;
    private final BuildWorkflowService workflowService;
    private final PipelineRunService pipelineRunService;
    private final McpWriteThrottle writeThrottle;
    private final McpValidator validator;

    @McpTool(
            name = "list_pipeline_workflows",
            description = """
                    The build-server workflows this project may trigger, with the git ref and
                    parameters each uses by default. An empty list means an administrator has not
                    assigned any workflow to this project.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public McpDtos.WorkflowList listPipelineWorkflows() {
        var caller = callerContext.require();
        List<McpDtos.Workflow> workflows = workflowService.listForProject(caller.projectId())
                .stream()
                .map(w -> new McpDtos.Workflow(w.id(), w.name(), w.serverName(),
                        w.provider() == null ? null : w.provider().name(), w.defaultRef(),
                        w.defaultParameters()))
                .toList();
        return new McpDtos.WorkflowList(workflows, workflows.size());
    }

    @McpTool(
            name = "trigger_pipeline",
            description = """
                    Start a workflow on its build server. This runs real CI: it consumes build
                    minutes and may deploy or test against shared environments, so trigger it
                    because you were asked to, not to see what happens.
                    ref overrides the workflow's default git ref; parameters are merged over its
                    default parameters. The pipeline reports its results back as a test run —
                    poll refresh_pipeline_run until testRunKey appears, then read it with
                    get_test_run.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false,
                    openWorldHint = true))
    public McpDtos.PipelineRun triggerPipeline(
            @McpToolParam(description = "Workflow UUID from list_pipeline_workflows") UUID workflowId,
            @McpToolParam(description = "Git ref to run, e.g. main; omit for the workflow default",
                    required = false) String ref,
            @McpToolParam(description = "Parameters to merge over the workflow defaults",
                    required = false) Map<String, String> parameters) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (workflowId == null) {
            throw new McpToolException("workflowId is required. Call list_pipeline_workflows.");
        }
        var request = new TriggerPipelineRequest(ref, parameters);
        validator.validate(request);

        return toPipelineRun(pipelineRunService.trigger(caller.projectId(), workflowId, request));
    }

    @McpTool(
            name = "list_pipeline_runs",
            description = """
                    Pipeline runs triggered from this project, newest first, with their status and
                    the test run each produced once it reported back.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public McpDtos.PipelineRunPage listPipelineRuns(
            @McpToolParam(description = "Zero-based page number, default 0", required = false)
            Integer page,
            @McpToolParam(description = "Page size, default 50, max 200", required = false)
            Integer size) {

        var caller = callerContext.require();
        Page<PipelineRunResponse> result = pipelineRunService.list(caller.projectId(),
                PageableUtils.normalize(PageRequest.of(
                        page == null || page < 0 ? 0 : page,
                        size == null || size < 1 ? PageableUtils.DEFAULT_SIZE : size)));
        return new McpDtos.PipelineRunPage(
                result.getContent().stream().map(PipelineTools::toPipelineRun).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.hasNext());
    }

    @McpTool(
            name = "get_pipeline_run",
            description = """
                    One pipeline run as last recorded. This does not ask the build server; use
                    refresh_pipeline_run to update the status of a run that is still going.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public McpDtos.PipelineRun getPipelineRun(
            @McpToolParam(description = "Pipeline run UUID") UUID id) {
        var caller = callerContext.require();
        return toPipelineRun(pipelineRunService.get(caller.projectId(), id));
    }

    @McpTool(
            name = "refresh_pipeline_run",
            description = """
                    Ask the build server for a pipeline run's current status and record it. Poll
                    this after trigger_pipeline — every 30 seconds or so is plenty — until status
                    is no longer TRIGGERED, PENDING or RUNNING.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = true))
    public McpDtos.PipelineRun refreshPipelineRun(
            @McpToolParam(description = "Pipeline run UUID") UUID id) {
        var caller = callerContext.requireWriter();
        return toPipelineRun(pipelineRunService.refresh(caller.projectId(), id));
    }

    private static McpDtos.PipelineRun toPipelineRun(PipelineRunResponse run) {
        return new McpDtos.PipelineRun(run.id(), run.workflowId(), run.workflowName(),
                run.status() == null ? null : run.status().name(), run.externalUrl(),
                run.triggeredRef(), run.testRunId(), run.testRunKey(), run.errorMessage(),
                run.createdAt(), run.finishedAt());
    }
}
