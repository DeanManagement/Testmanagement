package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.buildserver.ParameterJsonCodec;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.BuildWorkflowResponse;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.ProjectWorkflowResponse;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.SaveBuildWorkflowRequest;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.BuildWorkflow;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectBuildWorkflow;
import com.deanmanagement.testmanagement.project.internal.repository.BuildWorkflowRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectBuildWorkflowRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Owns workflow definitions and their project assignments (PRD-024 §3.1). The assignment table is
 * the entire authorization model: a project sees exactly the workflows assigned to it, and the
 * admin-side responses are the only place server internals appear.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildWorkflowService {

    private final BuildWorkflowRepository workflowRepository;
    private final ProjectBuildWorkflowRepository assignmentRepository;
    private final ProjectRepository projectRepository;
    private final BuildServerConfigService configService;
    private final ParameterJsonCodec parameterCodec;

    public List<BuildWorkflowResponse> listForServer(UUID serverId) {
        configService.require(serverId);
        return workflowRepository.findByBuildServerConfigIdOrderByName(serverId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BuildWorkflowResponse create(UUID serverId, SaveBuildWorkflowRequest request) {
        BuildServerConfig config = configService.require(serverId);
        BuildWorkflow workflow = new BuildWorkflow();
        workflow.setBuildServerConfig(config);
        return toResponse(workflowRepository.save(apply(workflow, request)));
    }

    @Transactional
    public BuildWorkflowResponse update(UUID workflowId, SaveBuildWorkflowRequest request) {
        return toResponse(workflowRepository.save(apply(require(workflowId), request)));
    }

    private BuildWorkflow apply(BuildWorkflow workflow, SaveBuildWorkflowRequest request) {
        workflow.setName(request.name().trim());
        workflow.setRepoRef(request.repoRef().trim());
        workflow.setWorkflowRef(trimToNull(request.workflowRef()));
        workflow.setDefaultRef(trimToNull(request.defaultRef()));
        workflow.setDefaultParameters(parameterCodec.toJson(request.defaultParameters()));
        workflow.setActive(request.active() == null || request.active());
        return workflow;
    }

    @Transactional
    public void delete(UUID workflowId) {
        // Assignments cascade; past pipeline runs keep their denormalised name (FK goes null).
        workflowRepository.delete(require(workflowId));
    }

    public List<UUID> assignedProjects(UUID workflowId) {
        require(workflowId);
        return assignmentRepository.findByWorkflowId(workflowId).stream()
                .map(ProjectBuildWorkflow::getProjectId)
                .toList();
    }

    /** Replaces the assignment set. Removing a project leaves its past runs untouched. */
    @Transactional
    public void assignProjects(UUID workflowId, List<UUID> projectIds) {
        BuildWorkflow workflow = require(workflowId);
        Set<UUID> wanted = new HashSet<>(projectIds);
        for (UUID projectId : wanted) {
            if (!projectRepository.existsById(projectId)) {
                throw new ResourceNotFoundException("Project", projectId);
            }
        }

        Set<UUID> current = new HashSet<>();
        for (ProjectBuildWorkflow assignment : assignmentRepository.findByWorkflowId(workflowId)) {
            if (wanted.contains(assignment.getProjectId())) {
                current.add(assignment.getProjectId());
            } else {
                assignmentRepository.delete(assignment);
            }
        }
        for (UUID projectId : wanted) {
            if (!current.contains(projectId)) {
                ProjectBuildWorkflow assignment = new ProjectBuildWorkflow();
                assignment.setProjectId(projectId);
                assignment.setWorkflow(workflow);
                assignmentRepository.save(assignment);
            }
        }
    }

    /** What a project member may see: assigned, active workflows on active servers — no more. */
    public List<ProjectWorkflowResponse> listForProject(UUID projectId) {
        return assignmentRepository.findByProjectIdWithWorkflow(projectId).stream()
                .map(ProjectBuildWorkflow::getWorkflow)
                .filter(BuildWorkflow::isActive)
                .filter(workflow -> workflow.getBuildServerConfig().isActive())
                .map(workflow -> new ProjectWorkflowResponse(
                        workflow.getId(),
                        workflow.getName(),
                        workflow.getBuildServerConfig().getName(),
                        workflow.getBuildServerConfig().getProvider(),
                        workflow.getDefaultRef(),
                        parameterCodec.fromJson(workflow.getDefaultParameters())))
                .toList();
    }

    public BuildWorkflow require(UUID workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("BuildWorkflow", workflowId));
    }

    private BuildWorkflowResponse toResponse(BuildWorkflow workflow) {
        return new BuildWorkflowResponse(
                workflow.getId(),
                workflow.getBuildServerConfig().getId(),
                workflow.getName(),
                workflow.getRepoRef(),
                workflow.getWorkflowRef(),
                workflow.getDefaultRef(),
                parameterCodec.fromJson(workflow.getDefaultParameters()),
                workflow.isActive(),
                assignmentRepository.findByWorkflowId(workflow.getId()).stream()
                        .map(ProjectBuildWorkflow::getProjectId)
                        .toList(),
                workflow.getUpdatedAt());
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
