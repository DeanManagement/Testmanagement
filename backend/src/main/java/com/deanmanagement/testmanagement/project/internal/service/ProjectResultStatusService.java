package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.resultstatus.CreateResultStatusRequest;
import com.deanmanagement.testmanagement.project.internal.dto.resultstatus.ResultStatusResponse;
import com.deanmanagement.testmanagement.project.internal.dto.resultstatus.UpdateResultStatusRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectResultStatusRepository;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A project's result statuses (TM24). Each names one of the fixed {@link TestResultStatus}
 * categories, which is all any metric reads; this service only manages the names on top.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectResultStatusService {

    /** What every project starts with: one status per category, in the order the run view lists them. */
    private static final Map<TestResultStatus, String> DEFAULT_COLORS = Map.of(
            TestResultStatus.PENDING, "#64748b",
            TestResultStatus.PASSED, "#16a34a",
            TestResultStatus.FAILED, "#dc2626",
            TestResultStatus.BLOCKED, "#d97706",
            TestResultStatus.SKIPPED, "#475569");

    private final ProjectResultStatusRepository repository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;

    public List<ResultStatusResponse> list(UUID projectId, boolean includeArchived) {
        return repository.findByProjectIdOrderByPositionAscNameAsc(projectId).stream()
                .filter(s -> includeArchived || !s.isArchived())
                .map(ProjectResultStatusService::toResponse)
                .toList();
    }

    /** The defaults V81 gave every existing project: "Pending", "Passed", … in category order. */
    @Transactional
    public void seedDefaults(Project project) {
        for (TestResultStatus category : TestResultStatus.values()) {
            String name = category.name().charAt(0) + category.name().substring(1).toLowerCase(Locale.ROOT);
            repository.save(newStatus(project, name, DEFAULT_COLORS.get(category), category, category.ordinal()));
        }
    }

    @Transactional
    public ResultStatusResponse create(UUID projectId, CreateResultStatusRequest request, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        requireNameFree(projectId, request.name(), null);
        int position = repository.findByProjectIdOrderByPositionAscNameAsc(projectId).stream()
                .mapToInt(ProjectResultStatus::getPosition).max().orElse(-1) + 1;
        ProjectResultStatus created = repository.save(
                newStatus(project, request.name(), request.color(), request.category(), position));
        auditService.log(projectId, userId, AuditAction.CREATED, AuditEntityType.PROJECT, projectId,
                project.getName(), "Result status " + created.getName() + " (" + created.getCategory() + ") added");
        return toResponse(created);
    }

    @Transactional
    public ResultStatusResponse update(UUID projectId, UUID id, UpdateResultStatusRequest request, UUID userId) {
        ProjectResultStatus status = require(projectId, id);
        String oldName = status.getName();
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new IllegalArgumentException("Status name must not be blank");
            }
            requireNameFree(projectId, request.name(), id);
            status.setName(request.name());
        }
        if (request.color() != null) {
            status.setColor(request.color());
        }
        if (request.position() != null) {
            status.setPosition(request.position());
        }
        if (Boolean.TRUE.equals(request.archived()) && !status.isArchived()) {
            requireAnotherActive(status);
        }
        if (request.archived() != null) {
            status.setArchived(request.archived());
        }
        status = repository.save(status);
        auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.PROJECT, projectId,
                status.getProject().getName(), "Result status " + oldName + " changed");
        return toResponse(status);
    }

    /** A status a result is recorded with; archived ones are kept for history but no longer picked. */
    public ProjectResultStatus requireActive(UUID projectId, UUID id) {
        ProjectResultStatus status = require(projectId, id);
        if (status.isArchived()) {
            throw new IllegalArgumentException("Result status '" + status.getName() + "' is archived");
        }
        return status;
    }

    /** By name, ignoring case and surrounding whitespace; archived statuses are not found. */
    public Optional<ProjectResultStatus> findActiveByName(UUID projectId, String name) {
        return repository.findByProjectIdAndNameNormalized(projectId, ProjectResultStatus.normalize(name))
                .filter(s -> !s.isArchived());
    }

    /** "Passed (PASSED), Not applicable (SKIPPED), …" — what a caller could have named instead. */
    public String describeActive(UUID projectId) {
        return list(projectId, false).stream()
                .map(s -> s.name() + " (" + s.category() + ")")
                .collect(Collectors.joining(", "));
    }

    private ProjectResultStatus require(UUID projectId, UUID id) {
        return repository.findById(id)
                .filter(s -> s.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("ResultStatus", id));
    }

    /** Every category keeps a status to pick, or the run view could no longer record that outcome. */
    private void requireAnotherActive(ProjectResultStatus status) {
        if (repository.countByProjectIdAndCategoryAndArchivedFalse(status.getProject().getId(), status.getCategory()) <= 1) {
            throw new ConflictException("'" + status.getName() + "' is the last active " + status.getCategory()
                    + " status; add another one first");
        }
    }

    private void requireNameFree(UUID projectId, String name, UUID self) {
        repository.findByProjectIdAndNameNormalized(projectId, ProjectResultStatus.normalize(name))
                .filter(existing -> !existing.getId().equals(self))
                .ifPresent(existing -> {
                    throw new ConflictException("A result status named '" + existing.getName() + "' already exists");
                });
    }

    private static ProjectResultStatus newStatus(Project project, String name, String color,
                                                 TestResultStatus category, int position) {
        ProjectResultStatus status = new ProjectResultStatus();
        status.setProject(project);
        status.setName(name);
        status.setColor(color);
        status.setCategory(category);
        status.setPosition(position);
        return status;
    }

    private static ResultStatusResponse toResponse(ProjectResultStatus s) {
        return new ResultStatusResponse(s.getId(), s.getName(), s.getColor(), s.getCategory(), s.getPosition(),
                s.isArchived());
    }
}
