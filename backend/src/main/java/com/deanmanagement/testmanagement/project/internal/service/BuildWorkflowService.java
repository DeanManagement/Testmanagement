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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owns workflow definitions and their project assignments (PRD-024 §3.1). An assignment, counted
 * only while the server is available to the project, is the entire authorization model: a project
 * sees exactly those workflows, and the admin-side responses are the only place server internals
 * appear. A system admin assigns per workflow; a project admin picks among what the project may use.
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
        workflow.setPullTestResults(Boolean.TRUE.equals(request.pullTestResults()));
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
                .map(this::toProjectResponse)
                .toList();
    }

    /** What a project admin may offer testers: every active workflow on a server open to the project. */
    public List<ProjectWorkflowResponse> listAvailableForProject(UUID projectId) {
        return workflowRepository.findAvailableToProject(projectId).stream()
                .map(this::toProjectResponse)
                .toList();
    }

    /**
     * A project admin's pick: exactly these of the available workflows are offered to testers.
     * Assignments outside what is available now (disabled, or on a server no longer open to the
     * project) are left alone, so re-enabling or re-opening brings them back.
     */
    @Transactional
    public void setProjectWorkflows(UUID projectId, List<UUID> workflowIds) {
        Map<UUID, BuildWorkflow> available = workflowRepository.findAvailableToProject(projectId).stream()
                .collect(Collectors.toMap(BuildWorkflow::getId, Function.identity()));
        Set<UUID> wanted = new HashSet<>(workflowIds);
        for (UUID workflowId : wanted) {
            if (!available.containsKey(workflowId)) {
                throw new ResourceNotFoundException("Workflow", workflowId);
            }
        }

        Set<UUID> current = new HashSet<>();
        for (ProjectBuildWorkflow assignment : assignmentRepository.findByProjectIdWithWorkflow(projectId)) {
            UUID workflowId = assignment.getWorkflow().getId();
            if (wanted.contains(workflowId)) {
                current.add(workflowId);
            } else if (available.containsKey(workflowId)) {
                assignmentRepository.delete(assignment);
            }
        }
        for (UUID workflowId : wanted) {
            if (!current.contains(workflowId)) {
                ProjectBuildWorkflow assignment = new ProjectBuildWorkflow();
                assignment.setProjectId(projectId);
                assignment.setWorkflow(available.get(workflowId));
                assignmentRepository.save(assignment);
            }
        }
    }

    public BuildWorkflow require(UUID workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("BuildWorkflow", workflowId));
    }

    private ProjectWorkflowResponse toProjectResponse(BuildWorkflow workflow) {
        return new ProjectWorkflowResponse(
                workflow.getId(),
                workflow.getName(),
                workflow.getBuildServerConfig().getName(),
                workflow.getBuildServerConfig().getProvider(),
                workflow.getDefaultRef(),
                parameterCodec.fromJson(workflow.getDefaultParameters()));
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
                workflow.getUpdatedAt(),
                workflow.isPullTestResults());
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
