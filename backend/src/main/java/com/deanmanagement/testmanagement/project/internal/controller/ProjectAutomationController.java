package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.PipelineRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.ProjectWorkflowResponse;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.TriggerPipelineRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.BuildWorkflowService;
import com.deanmanagement.testmanagement.project.internal.service.PipelineRunService;
import com.deanmanagement.testmanagement.shared.PageableUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The project-side of PRD-024: the workflows assigned to this project, triggering them, and the
 * resulting pipeline runs. Any member may look; triggering takes TESTER, matching who may execute
 * tests. Server URLs and credentials never appear on these endpoints.
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
@Tag(name = "Automation", description = "Assigned workflows and triggered pipeline runs")
@RequiredArgsConstructor
public class ProjectAutomationController {

    private final BuildWorkflowService workflowService;
    private final PipelineRunService pipelineRunService;

    @GetMapping("/workflows")
    @RequireProjectRole
    public List<ProjectWorkflowResponse> workflows(@PathVariable UUID projectId) {
        return workflowService.listForProject(projectId);
    }

    @PostMapping("/workflows/{workflowId}/trigger")
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.CREATED)
    public PipelineRunResponse trigger(@PathVariable UUID projectId,
                                       @PathVariable UUID workflowId,
                                       @Valid @RequestBody TriggerPipelineRequest request) {
        return pipelineRunService.trigger(projectId, workflowId, request);
    }

    @GetMapping("/pipeline-runs")
    @RequireProjectRole
    public Page<PipelineRunResponse> pipelineRuns(@PathVariable UUID projectId,
                                                  @PageableDefault(size = PageableUtils.DEFAULT_SIZE)
                                                  Pageable pageable) {
        return pipelineRunService.list(projectId, PageableUtils.normalize(pageable));
    }

    @GetMapping("/pipeline-runs/{runId}")
    @RequireProjectRole
    public PipelineRunResponse pipelineRun(@PathVariable UUID projectId, @PathVariable UUID runId) {
        return pipelineRunService.get(projectId, runId);
    }

    /** Forces an immediate upstream status fetch instead of waiting for the next poll pass. */
    @PostMapping("/pipeline-runs/{runId}/refresh")
    @RequireProjectRole(ProjectRole.TESTER)
    public PipelineRunResponse refresh(@PathVariable UUID projectId, @PathVariable UUID runId) {
        return pipelineRunService.refresh(projectId, runId);
    }
}
