package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.bugstatus.BugStatusResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugstatus.BugStatusTransitionsRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugstatus.CreateBugStatusRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugstatus.UpdateBugStatusRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.BugStatusTransition;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectBugStatus;
import com.deanmanagement.testmanagement.project.internal.repository.BugStatusTransitionRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectBugStatusRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A project's own bug statuses and which may follow which (TM47, PRD-054 §1). The TM24 pattern:
 * every status counts as one fixed {@link BugReportStatus} category, which metrics keep reading.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BugStatusService {

    /** What every project starts with, one per category, in category order. */
    private static final Map<BugReportStatus, String[]> DEFAULTS = Map.of(
            BugReportStatus.NEW, new String[]{"New", "#64748b"},
            BugReportStatus.OPEN, new String[]{"Open", "#2563eb"},
            BugReportStatus.IN_PROGRESS, new String[]{"In progress", "#d97706"},
            BugReportStatus.RESOLVED, new String[]{"Resolved", "#16a34a"},
            BugReportStatus.CLOSED, new String[]{"Closed", "#475569"});

    private final ProjectBugStatusRepository repository;
    private final BugStatusTransitionRepository transitionRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;

    public List<BugStatusResponse> list(UUID projectId, boolean includeArchived) {
        List<ProjectBugStatus> statuses = repository.findByProjectIdOrderByPositionAscNameAsc(projectId);
        Map<UUID, Set<UUID>> rules = rules(projectId);
        List<UUID> active = statuses.stream().filter(s -> !s.isArchived()).map(ProjectBugStatus::getId).toList();
        return statuses.stream()
                .filter(s -> includeArchived || !s.isArchived())
                .map(s -> toResponse(s, allowedTargets(s.getId(), active, rules)))
                .toList();
    }

    /** The project's transition table, or empty when any move is allowed. */
    public List<BugStatusTransitionsRequest.Transition> transitions(UUID projectId) {
        return transitionRepository.findByProjectId(projectId).stream()
                .map(t -> new BugStatusTransitionsRequest.Transition(t.getFromStatusId(), t.getToStatusId()))
                .toList();
    }

    /** Replaces the table; an empty one allows any move again. */
    @Transactional
    public List<BugStatusTransitionsRequest.Transition> saveTransitions(UUID projectId,
                                                                        BugStatusTransitionsRequest request,
                                                                        UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        Set<UUID> own = repository.findByProjectIdOrderByPositionAscNameAsc(projectId).stream()
                .map(ProjectBugStatus::getId).collect(Collectors.toSet());
        Set<List<UUID>> seen = new HashSet<>();
        for (var transition : request.transitions()) {
            if (!own.contains(transition.fromStatusId()) || !own.contains(transition.toStatusId())) {
                throw new IllegalArgumentException("Transitions must be between this project's bug statuses");
            }
            if (transition.fromStatusId().equals(transition.toStatusId())) {
                throw new IllegalArgumentException("A status always may stay as it is; it needs no transition to itself");
            }
            seen.add(List.of(transition.fromStatusId(), transition.toStatusId()));
        }
        transitionRepository.deleteByProjectId(projectId);
        transitionRepository.flush();
        for (List<UUID> pair : seen) {
            BugStatusTransition transition = new BugStatusTransition();
            transition.setProjectId(projectId);
            transition.setFromStatusId(pair.get(0));
            transition.setToStatusId(pair.get(1));
            transitionRepository.save(transition);
        }
        auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.PROJECT, projectId, project.getName(),
                seen.isEmpty() ? "Bug status transitions cleared: any move allowed"
                        : "Bug status transitions set (" + seen.size() + ")");
        return transitions(projectId);
    }

    /** The defaults V100 gave every existing project. */
    @Transactional
    public void seedDefaults(Project project) {
        for (BugReportStatus category : BugReportStatus.values()) {
            String[] defaults = DEFAULTS.get(category);
            repository.save(newStatus(project, defaults[0], defaults[1], category, category.ordinal()));
        }
    }

    @Transactional
    public BugStatusResponse create(UUID projectId, CreateBugStatusRequest request, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        requireNameFree(projectId, request.name(), null);
        int position = repository.findByProjectIdOrderByPositionAscNameAsc(projectId).stream()
                .mapToInt(ProjectBugStatus::getPosition).max().orElse(-1) + 1;
        ProjectBugStatus created = repository.save(
                newStatus(project, request.name(), request.color(), request.category(), position));
        auditService.log(projectId, userId, AuditAction.CREATED, AuditEntityType.PROJECT, projectId,
                project.getName(), "Bug status " + created.getName() + " (" + created.getCategory() + ") added");
        return one(projectId, created.getId());
    }

    @Transactional
    public BugStatusResponse update(UUID projectId, UUID id, UpdateBugStatusRequest request, UUID userId) {
        ProjectBugStatus status = require(projectId, id);
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
        repository.save(status);
        auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.PROJECT, projectId,
                status.getProject().getName(), "Bug status " + oldName + " changed");
        return one(projectId, id);
    }

    /**
     * The status a change asks for: {@code statusId} if given, otherwise the first active status of
     * {@code category}, so callers that only know the category keep working. Null when the project
     * has no statuses (bugs made outside the services, in tests).
     */
    public ProjectBugStatus resolveTarget(UUID projectId, UUID statusId, BugReportStatus category) {
        if (statusId != null) {
            ProjectBugStatus status = require(projectId, statusId);
            if (status.isArchived()) {
                throw new IllegalArgumentException("Bug status '" + status.getName() + "' is archived");
            }
            return status;
        }
        if (category == null) {
            throw new IllegalArgumentException("Pass a status or a statusId");
        }
        return firstOf(projectId, category).orElse(null);
    }

    /** The category's first active status: where a bug lands when only the category is known. */
    public Optional<ProjectBugStatus> firstOf(UUID projectId, BugReportStatus category) {
        return repository.findFirstByProjectIdAndCategoryAndArchivedFalseOrderByPositionAscNameAsc(projectId, category);
    }

    /** Refuses a move the project's transition table does not allow, naming the ones it does. */
    public void requireTransition(UUID projectId, BugReport bug, ProjectBugStatus target) {
        if (target == null || !transitionRepository.existsByProjectId(projectId)) {
            return;
        }
        ProjectBugStatus from = bug.getCustomStatus() != null ? bug.getCustomStatus()
                : firstOf(projectId, bug.getStatus()).orElse(null);
        if (from == null || from.getId().equals(target.getId())
                || transitionRepository.existsByFromStatusIdAndToStatusId(from.getId(), target.getId())) {
            return;
        }
        Set<UUID> allowed = rules(projectId).getOrDefault(from.getId(), Set.of());
        String names = repository.findByProjectIdOrderByPositionAscNameAsc(projectId).stream()
                .filter(s -> !s.isArchived() && allowed.contains(s.getId()))
                .map(ProjectBugStatus::getName)
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException(bug.getKey() + " cannot move from '" + from.getName() + "' to '"
                + target.getName() + "'. Allowed: " + (names.isEmpty() ? "none" : names));
    }

    /** By name, ignoring case and surrounding whitespace, archived ones included: to change them. */
    public Optional<ProjectBugStatus> findByName(UUID projectId, String name) {
        return repository.findByProjectIdAndNameNormalized(projectId, ProjectBugStatus.normalize(name));
    }

    /** By name, ignoring case and surrounding whitespace; archived statuses are not found. */
    public Optional<ProjectBugStatus> findActiveByName(UUID projectId, String name) {
        return repository.findByProjectIdAndNameNormalized(projectId, ProjectBugStatus.normalize(name))
                .filter(s -> !s.isArchived());
    }

    /** "New (NEW), Ready for retest (RESOLVED), …" — what a caller could have named instead. */
    public String describeActive(UUID projectId) {
        return list(projectId, false).stream()
                .map(s -> s.name() + " (" + s.category() + ")")
                .collect(Collectors.joining(", "));
    }

    private BugStatusResponse one(UUID projectId, UUID id) {
        return list(projectId, true).stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    private Map<UUID, Set<UUID>> rules(UUID projectId) {
        return transitionRepository.findByProjectId(projectId).stream()
                .collect(Collectors.groupingBy(BugStatusTransition::getFromStatusId,
                        Collectors.mapping(BugStatusTransition::getToStatusId, Collectors.toSet())));
    }

    /** Without rules every other active status; with rules the active ones the table names. */
    private static List<UUID> allowedTargets(UUID from, List<UUID> active, Map<UUID, Set<UUID>> rules) {
        if (rules.isEmpty()) {
            return active.stream().filter(id -> !id.equals(from)).toList();
        }
        Set<UUID> targets = rules.getOrDefault(from, Set.of());
        return active.stream().filter(targets::contains).toList();
    }

    private ProjectBugStatus require(UUID projectId, UUID id) {
        return repository.findById(id)
                .filter(s -> s.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("BugStatus", id));
    }

    /** Every category keeps a status to land on, or a bug could no longer reach that category. */
    private void requireAnotherActive(ProjectBugStatus status) {
        if (repository.countByProjectIdAndCategoryAndArchivedFalse(status.getProject().getId(), status.getCategory()) <= 1) {
            throw new ConflictException("'" + status.getName() + "' is the last active " + status.getCategory()
                    + " status; add another one first");
        }
    }

    private void requireNameFree(UUID projectId, String name, UUID self) {
        repository.findByProjectIdAndNameNormalized(projectId, ProjectBugStatus.normalize(name))
                .filter(existing -> !existing.getId().equals(self))
                .ifPresent(existing -> {
                    throw new ConflictException("A bug status named '" + existing.getName() + "' already exists");
                });
    }

    private static ProjectBugStatus newStatus(Project project, String name, String color, BugReportStatus category,
                                              int position) {
        ProjectBugStatus status = new ProjectBugStatus();
        status.setProject(project);
        status.setName(name);
        status.setColor(color);
        status.setCategory(category);
        status.setPosition(position);
        return status;
    }

    private static BugStatusResponse toResponse(ProjectBugStatus s, List<UUID> allowedTargetIds) {
        return new BugStatusResponse(s.getId(), s.getName(), s.getColor(), s.getCategory(), s.getPosition(),
                s.isArchived(), allowedTargetIds);
    }
}
