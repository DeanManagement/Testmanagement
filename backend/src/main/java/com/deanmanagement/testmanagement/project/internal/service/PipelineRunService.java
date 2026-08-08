package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.buildserver.BuildServerProperties;
import com.deanmanagement.testmanagement.project.internal.buildserver.BuildServerProvider;
import com.deanmanagement.testmanagement.project.internal.buildserver.BuildServerProviderRegistry;
import com.deanmanagement.testmanagement.project.internal.buildserver.ParameterJsonCodec;
import com.deanmanagement.testmanagement.project.internal.buildserver.PipelineRunRefresher;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.PipelineRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.TriggerPipelineRequest;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.BuildWorkflow;
import com.deanmanagement.testmanagement.project.internal.entity.PipelineRun;
import com.deanmanagement.testmanagement.project.internal.entity.PipelineRunStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectBuildWorkflow;
import com.deanmanagement.testmanagement.project.internal.repository.PipelineRunRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectBuildWorkflowRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Triggers pipelines and serves run history (PRD-024 §3.3).
 *
 * <p>{@link #trigger} is deliberately not transactional: the run row is committed before the
 * provider is called (webhook convention — no HTTP inside a DB transaction), so a trigger whose
 * upstream call fails still leaves an ERROR run visible in the panel instead of vanishing with
 * the rollback.
 */
@Service
@RequiredArgsConstructor
public class PipelineRunService {

    /** Injected into every triggered pipeline; the workflow uses it to report results back. */
    public static final String VAR_PIPELINE_RUN_ID = "TM_PIPELINE_RUN_ID";
    public static final String VAR_PROJECT_KEY = "TM_PROJECT_KEY";
    public static final String VAR_BASE_URL = "TM_BASE_URL";

    private final PipelineRunRepository runRepository;
    private final ProjectBuildWorkflowRepository assignmentRepository;
    private final ProjectRepository projectRepository;
    private final BuildServerConfigService configService;
    private final BuildServerProviderRegistry providerRegistry;
    private final BuildServerProperties properties;
    private final ParameterJsonCodec parameterCodec;
    private final PipelineRunRefresher refresher;

    public PipelineRunResponse trigger(UUID projectId, UUID workflowId, TriggerPipelineRequest request) {
        // The assignment is the authorization: an unassigned workflow is not found, not forbidden.
        ProjectBuildWorkflow assignment = assignmentRepository
                .findByProjectIdAndWorkflowId(projectId, workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", workflowId));
        BuildWorkflow workflow = assignment.getWorkflow();
        BuildServerConfig config = workflow.getBuildServerConfig();
        if (!workflow.isActive() || !config.isActive()) {
            throw new IllegalArgumentException("This workflow is currently disabled");
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        String ref = request.ref() != null && !request.ref().isBlank()
                ? request.ref().trim() : workflow.getDefaultRef();

        PipelineRun run = new PipelineRun();
        run.setWorkflow(workflow);
        run.setProjectId(projectId);
        run.setWorkflowName(workflow.getName());
        run.setStatus(PipelineRunStatus.TRIGGERED);
        run.setTriggeredRef(ref);

        Map<String, String> parameters = mergedParameters(workflow, request, run, project);
        run.setParameters(parameterCodec.toJson(parameters));
        run = runRepository.save(run);
        // The id only exists after the first save; re-merge so TM_PIPELINE_RUN_ID is real.
        parameters = mergedParameters(workflow, request, run, project);
        run.setParameters(parameterCodec.toJson(parameters));
        run = runRepository.save(run);

        BuildServerProvider provider = providerRegistry.require(config.getProvider());
        try {
            BuildServerProvider.TriggerResult result = provider.trigger(configService.decrypt(config),
                    new BuildServerProvider.TriggerSpec(workflow.getRepoRef(), workflow.getWorkflowRef(),
                            ref, parameters, run.getId()));
            run.setStatus(result.status());
            run.setExternalRunId(result.externalRunId());
            run.setExternalUrl(result.externalUrl());
            configService.clearError(config);
        } catch (UpstreamServiceException e) {
            failRun(run, e.getMessage());
            configService.recordError(config, e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            failRun(run, e.getMessage());
            throw e;
        }
        run = runRepository.save(run);
        return toResponse(run, workflow.getId(), null, null);
    }

    private Map<String, String> mergedParameters(BuildWorkflow workflow, TriggerPipelineRequest request,
                                                 PipelineRun run, Project project) {
        Map<String, String> merged = new LinkedHashMap<>(parameterCodec.fromJson(workflow.getDefaultParameters()));
        if (request.parameters() != null) {
            merged.putAll(request.parameters());
        }
        // TM_* last, so neither defaults nor overrides can spoof the correlation id.
        if (run.getId() != null) {
            merged.put(VAR_PIPELINE_RUN_ID, run.getId().toString());
        }
        merged.put(VAR_PROJECT_KEY, project.getKey());
        if (properties.publicBaseUrl() != null && !properties.publicBaseUrl().isBlank()) {
            merged.put(VAR_BASE_URL, properties.publicBaseUrl());
        }
        return merged;
    }

    private void failRun(PipelineRun run, String message) {
        run.setStatus(PipelineRunStatus.ERROR);
        run.setErrorMessage(message);
        run.setFinishedAt(Instant.now());
        runRepository.save(run);
    }

    @Transactional(readOnly = true)
    public Page<PipelineRunResponse> list(UUID projectId, Pageable pageable) {
        return runRepository.findByProjectIdOrderByCreatedAtDesc(projectId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PipelineRunResponse get(UUID projectId, UUID runId) {
        return toResponse(requireRun(projectId, runId));
    }

    /** Forces an immediate upstream status fetch, for the tester who cannot wait 15 seconds. */
    public PipelineRunResponse refresh(UUID projectId, UUID runId) {
        requireRun(projectId, runId);
        refresher.refreshOne(runId);
        return get(projectId, runId);
    }

    private PipelineRun requireRun(UUID projectId, UUID runId) {
        return runRepository.findByIdAndProjectId(runId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("PipelineRun", runId));
    }

    /** Only call within a transaction: touches the lazy workflow and test-run associations. */
    private PipelineRunResponse toResponse(PipelineRun run) {
        UUID workflowId = run.getWorkflow() != null ? run.getWorkflow().getId() : null;
        UUID testRunId = run.getTestRun() != null ? run.getTestRun().getId() : null;
        String testRunKey = run.getTestRun() != null ? run.getTestRun().getKey() : null;
        return toResponse(run, workflowId, testRunId, testRunKey);
    }

    private PipelineRunResponse toResponse(PipelineRun run, UUID workflowId, UUID testRunId,
                                           String testRunKey) {
        return new PipelineRunResponse(
                run.getId(),
                workflowId,
                run.getWorkflowName(),
                run.getStatus(),
                run.getExternalRunId(),
                run.getExternalUrl(),
                run.getTriggeredRef(),
                parameterCodec.fromJson(run.getParameters()),
                testRunId,
                testRunKey,
                run.getErrorMessage(),
                run.getCreatedAt(),
                run.getFinishedAt());
    }
}
