package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.environment.CreateEnvironmentRequest;
import com.deanmanagement.testmanagement.project.internal.dto.environment.EnvironmentResponse;
import com.deanmanagement.testmanagement.project.internal.dto.environment.EnvironmentResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.environment.UpdateEnvironmentRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectEnvironment;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectEnvironmentRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import com.deanmanagement.testmanagement.shared.exception.DuplicateKeyException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The per-project environment catalogue (PRD-032). Every write path that sets a run's or bug's
 * environment goes through {@link #resolve}, and every change to a name goes through here, which
 * is what keeps the denormalised {@code environment} strings in step.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectEnvironmentService {

    private final ProjectEnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;
    private final TestCaseRepository testCaseRepository;
    private final TestResultRepository testResultRepository;

    public List<EnvironmentResponse> list(UUID projectId, boolean includeArchived) {
        Map<UUID, Long> runCounts = toCounts(environmentRepository.countRunsByEnvironment(projectId));
        Map<UUID, Long> bugCounts = toCounts(environmentRepository.countBugsByEnvironment(projectId));
        return environmentRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId).stream()
                .filter(e -> includeArchived || !e.isArchived())
                .map(e -> toResponse(e, runCounts.getOrDefault(e.getId(), 0L), bugCounts.getOrDefault(e.getId(), 0L)))
                .toList();
    }

    /**
     * The environment a write should point at. The id wins when both are given; a name is matched
     * ignoring case and surrounding whitespace, and an unknown one is registered (PRD-032 §3.2) so
     * CI and MCP callers never break. A blank name, or neither argument, means "no environment".
     */
    @Transactional
    public ProjectEnvironment resolve(UUID projectId, UUID environmentId, String name) {
        if (environmentId != null) {
            return require(projectId, environmentId);
        }
        if (name == null || name.isBlank()) {
            return null;
        }
        Optional<ProjectEnvironment> existing = environmentRepository
                .findByProjectIdAndNameNormalized(projectId, ProjectEnvironment.normalize(name));
        if (existing.isPresent()) {
            // Something just used it, so it is evidently not retired.
            existing.get().setArchived(false);
            return existing.get();
        }
        // ponytail: two concurrent first uses of the same new name race on the unique key and one
        // write fails with 409; a retry resolves to the winner. Fine at CI-upload rates.
        ProjectEnvironment created = insert(projectId, name, null);
        auditService.log(projectId, null, AuditAction.CREATED, AuditEntityType.ENVIRONMENT,
                created.getId(), created.getName(), "Registered on first use");
        return created;
    }

    @Transactional
    public EnvironmentResponse create(UUID projectId, CreateEnvironmentRequest request, UUID userId) {
        requireNameFree(projectId, request.name(), null);
        ProjectEnvironment created = insert(projectId, request.name(), blankToNull(request.description()));
        auditService.log(projectId, userId, AuditAction.CREATED, AuditEntityType.ENVIRONMENT,
                created.getId(), created.getName(), null);
        return toResponse(created, 0, 0);
    }

    @Transactional
    public EnvironmentResponse update(UUID projectId, UUID id, UpdateEnvironmentRequest request, UUID userId) {
        ProjectEnvironment environment = require(projectId, id);
        String oldName = environment.getName();
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new IllegalArgumentException("Environment name must not be blank");
            }
            requireNameFree(projectId, request.name(), id);
            environment.setName(request.name());
        }
        if (request.description() != null) {
            environment.setDescription(blankToNull(request.description()));
        }
        if (request.sortOrder() != null) {
            environment.setSortOrder(request.sortOrder());
        }
        if (request.archived() != null) {
            environment.setArchived(request.archived());
        }
        environment = environmentRepository.saveAndFlush(environment);
        if (!environment.getName().equals(oldName)) {
            environmentRepository.repointRuns(environment, environment, environment.getName());
            environmentRepository.repointBugs(environment, environment, environment.getName());
            auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.ENVIRONMENT,
                    id, environment.getName(), "Renamed from: " + oldName);
        } else {
            auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.ENVIRONMENT,
                    id, environment.getName(), null);
        }
        return toResponse(require(projectId, id), environmentRepository.countRuns(id), environmentRepository.countBugs(id));
    }

    /** Only an unused environment can be deleted; history keeps pointing at archived ones. */
    @Transactional
    public void delete(UUID projectId, UUID id, UUID userId) {
        ProjectEnvironment environment = require(projectId, id);
        long runs = environmentRepository.countRuns(id);
        long bugs = environmentRepository.countBugs(id);
        if (runs + bugs > 0) {
            throw new ConflictException("Environment '" + environment.getName() + "' is used by " + runs
                    + " run(s) and " + bugs + " bug report(s); archive it or merge it into another instead");
        }
        environmentRepository.delete(environment);
        auditService.log(projectId, userId, AuditAction.DELETED, AuditEntityType.ENVIRONMENT,
                id, environment.getName(), null);
    }

    /** Moves every run and bug report from {@code sourceId} to {@code targetId}, then deletes the source. */
    @Transactional
    public EnvironmentResponse merge(UUID projectId, UUID sourceId, UUID targetId, UUID userId) {
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot merge an environment into itself");
        }
        ProjectEnvironment source = require(projectId, sourceId);
        ProjectEnvironment target = require(projectId, targetId);
        String sourceName = source.getName();
        String targetName = target.getName();
        environmentRepository.repointRuns(source, target, targetName);
        environmentRepository.repointBugs(source, target, targetName);
        environmentRepository.deleteById(sourceId);
        auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.ENVIRONMENT,
                targetId, targetName, "Merged in: " + sourceName);
        return toResponse(require(projectId, targetId),
                environmentRepository.countRuns(targetId), environmentRepository.countBugs(targetId));
    }

    /** The newest executed result of a test case in each environment, most recent first. */
    public List<EnvironmentResultResponse> latestResultsByEnvironment(UUID projectId, UUID testCaseId) {
        testCaseRepository.findByIdAndProjectId(testCaseId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", testCaseId));
        // ponytail: loads the case's whole result history and keeps the first per environment;
        // a window-function query if a single case ever has tens of thousands of results.
        Map<UUID, EnvironmentResultResponse> latest = new LinkedHashMap<>();
        EnvironmentResultResponse unspecified = null;
        for (EnvironmentResultResponse row : testResultRepository.findExecutedResultsNewestFirst(projectId, testCaseId)) {
            if (row.environmentId() == null) {
                unspecified = unspecified != null ? unspecified : row;
            } else {
                latest.putIfAbsent(row.environmentId(), row);
            }
        }
        List<EnvironmentResultResponse> rows = new ArrayList<>(latest.values());
        if (unspecified != null) {
            rows.add(unspecified);
        }
        return rows;
    }

    private ProjectEnvironment require(UUID projectId, UUID id) {
        return environmentRepository.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Environment", id));
    }

    private ProjectEnvironment insert(UUID projectId, String name, String description) {
        ProjectEnvironment environment = new ProjectEnvironment();
        environment.setProject(projectRepository.getReferenceById(projectId));
        environment.setName(name);
        environment.setDescription(description);
        environment.setSortOrder(environmentRepository.maxSortOrder(projectId) + 1);
        return environmentRepository.save(environment);
    }

    private void requireNameFree(UUID projectId, String name, UUID exceptId) {
        environmentRepository.findByProjectIdAndNameNormalized(projectId, ProjectEnvironment.normalize(name))
                .filter(existing -> !existing.getId().equals(exceptId))
                .ifPresent(existing -> {
                    throw new DuplicateKeyException("environment name", existing.getName());
                });
    }

    private static Map<UUID, Long> toCounts(List<Object[]> rows) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static EnvironmentResponse toResponse(ProjectEnvironment e, long runCount, long bugCount) {
        return new EnvironmentResponse(e.getId(), e.getName(), e.getDescription(), e.getSortOrder(),
                e.isArchived(), runCount, bugCount);
    }
}
