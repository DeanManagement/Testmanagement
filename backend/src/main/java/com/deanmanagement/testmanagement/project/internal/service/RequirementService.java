package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.requirement.CoverageSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.RequirementResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.SaveRequirementRequest;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.TraceabilityRowResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.TraceabilityRowResponse.CoverageStatus;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.dto.customField.CustomFieldValueMaps;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Requirement;
import com.deanmanagement.testmanagement.project.internal.entity.RequirementStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.repository.RequirementRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.spec.LikePatterns;
import com.deanmanagement.testmanagement.shared.exception.DuplicateKeyException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Requirements and the traceability matrix (PRD-014).
 *
 * <p>Coverage here means "a linked test has passed", not "a test is linked". Counting the latter
 * would let a project report 100% coverage while nothing had ever been executed, which is exactly
 * the false assurance a traceability report exists to prevent.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequirementService {

    private static final Map<IssueTrackerProviderType, String> SYNC_SOURCE_NAMES =
            Map.of(IssueTrackerProviderType.AZURE_DEVOPS, "Azure DevOps");

    private final RequirementRepository requirementRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestResultRepository testResultRepository;
    private final TestPlanRepository testPlanRepository;
    private final AuditService auditService;
    private final CustomFieldValueWriter customFieldWriter;

    // ---- CRUD -------------------------------------------------------------

    public Page<RequirementResponse> list(UUID projectId, Pageable pageable) {
        return requirementRepository.findByProjectIdOrderByExternalIdAsc(projectId, pageable)
                .map(RequirementService::toResponse);
    }

    /** Requirements whose external id or title contains {@code query}, ignoring case; blank lists all. */
    public Page<RequirementResponse> list(UUID projectId, String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return list(projectId, pageable);
        }
        return requirementRepository.search(projectId, LikePatterns.containing(query.trim()), pageable)
                .map(RequirementService::toResponse);
    }

    public RequirementResponse get(UUID projectId, UUID id) {
        return toResponse(require(projectId, id));
    }

    /** By UUID or by the exact external id (e.g. REQ-14), only within the project. */
    public RequirementResponse get(UUID projectId, String idOrExternalId) {
        String reference = idOrExternalId == null ? "" : idOrExternalId.trim();
        return requirementRepository.findByProjectIdAndExternalId(projectId, reference)
                .or(() -> parseUuid(reference).flatMap(id -> requirementRepository.findByIdAndProjectId(id, projectId)))
                .map(RequirementService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Requirement", reference));
    }

    /** The requirements the test case is linked to, ordered by external id. */
    public List<RequirementResponse> findLinkedTo(UUID projectId, UUID testCaseId) {
        return requirementRepository.findLinkedTo(projectId, testCaseId).stream()
                .map(RequirementService::toResponse)
                .toList();
    }

    @Transactional
    public RequirementResponse create(UUID projectId, SaveRequirementRequest request, UUID actorId) {
        return create(projectId, request, actorId, CustomFieldWriteMode.INTERACTIVE);
    }

    @Transactional
    public RequirementResponse create(UUID projectId, SaveRequirementRequest request, UUID actorId,
                                      CustomFieldWriteMode mode) {
        String externalId = request.externalId().trim();
        if (requirementRepository.existsByProjectIdAndExternalId(projectId, externalId)) {
            throw new DuplicateKeyException("externalId", externalId);
        }
        Requirement requirement = new Requirement();
        requirement.setProjectId(projectId);
        requirement.setExternalId(externalId);
        requirement.setTitle(request.title().trim());
        requirement.setDescription(request.description());
        applyFields(requirement, request);
        requirement.setParentId(validParent(projectId, null, request.parentId()));
        customFieldWriter.write(requirement, request.customFields(), mode);
        requirement = requirementRepository.save(requirement);

        auditService.log(projectId, actorId, AuditAction.CREATED,
                AuditEntityType.REQUIREMENT, requirement.getId(), requirement.getExternalId(), null);
        return toResponse(requirement);
    }

    @Transactional
    public RequirementResponse update(UUID projectId, UUID id, SaveRequirementRequest request, UUID actorId) {
        return update(projectId, id, request, actorId, CustomFieldWriteMode.INTERACTIVE);
    }

    @Transactional
    public RequirementResponse update(UUID projectId, UUID id, SaveRequirementRequest request, UUID actorId,
                                      CustomFieldWriteMode mode) {
        Requirement requirement = require(projectId, id);
        String externalId = request.externalId().trim();
        refuseSyncedFieldChanges(requirement, request, externalId);

        if (!requirement.getExternalId().equals(externalId)
                && requirementRepository.existsByProjectIdAndExternalId(projectId, externalId)) {
            throw new DuplicateKeyException("externalId", externalId);
        }
        requirement.setExternalId(externalId);
        requirement.setTitle(request.title().trim());
        requirement.setDescription(request.description());
        applyFields(requirement, request);
        requirement.setParentId(validParent(projectId, requirement.getId(), request.parentId()));
        customFieldWriter.write(requirement, request.customFields(), mode);
        requirement = requirementRepository.save(requirement);

        auditService.log(projectId, actorId, AuditAction.UPDATED,
                AuditEntityType.REQUIREMENT, requirement.getId(), requirement.getExternalId(), null);
        return toResponse(requirement);
    }

    /**
     * Its children move up to its own parent (TM18): deleting an epic must not silently delete, or
     * orphan out of sight, the stories under it.
     */
    @Transactional
    public void delete(UUID projectId, UUID id, UUID actorId) {
        Requirement requirement = require(projectId, id);
        for (Requirement child : requirementRepository.findByParentId(id)) {
            child.setParentId(requirement.getParentId());
        }
        auditService.log(projectId, actorId, AuditAction.DELETED,
                AuditEntityType.REQUIREMENT, requirement.getId(), requirement.getExternalId(), null);
        requirementRepository.delete(requirement);
    }

    // ---- linking ----------------------------------------------------------

    @Transactional
    public RequirementResponse linkTestCase(UUID projectId, UUID requirementId, UUID testCaseId, UUID actorId) {
        Requirement requirement = require(projectId, requirementId);
        TestCase testCase = testCaseRepository.findById(testCaseId)
                .filter(tc -> tc.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", testCaseId));

        if (requirement.getTestCases().add(testCase)) {
            requirement = requirementRepository.save(requirement);
            auditService.log(projectId, actorId, AuditAction.UPDATED,
                    AuditEntityType.REQUIREMENT, requirement.getId(), requirement.getExternalId(),
                    "Linked test case " + testCase.getKey());
        }
        return toResponse(requirement);
    }

    @Transactional
    public void unlinkTestCase(UUID projectId, UUID requirementId, UUID testCaseId, UUID actorId) {
        Requirement requirement = require(projectId, requirementId);
        boolean removed = requirement.getTestCases().removeIf(tc -> tc.getId().equals(testCaseId));
        if (removed) {
            requirementRepository.save(requirement);
            auditService.log(projectId, actorId, AuditAction.UPDATED,
                    AuditEntityType.REQUIREMENT, requirement.getId(), requirement.getExternalId(),
                    "Unlinked a test case");
        }
    }

    // ---- matrix & coverage -------------------------------------------------

    public List<TraceabilityRowResponse> matrix(UUID projectId) {
        return matrix(projectId, null);
    }

    /**
     * With {@code testPlanId}, "latest result" means the latest in that plan's completed runs (TM20),
     * so the matrix answers "what did this release prove?"; a case the plan never ran is UNTESTED.
     */
    public List<TraceabilityRowResponse> matrix(UUID projectId, UUID testPlanId) {
        if (testPlanId != null) {
            testPlanRepository.findByIdAndProjectId(testPlanId, projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("TestPlan", testPlanId));
        }
        List<Requirement> requirements = requirementRepository.findByProjectIdOrderByExternalIdAsc(projectId);
        if (requirements.isEmpty()) {
            return List.of();
        }
        Map<UUID, CoverageStatus> latestByCase = latestStatusByTestCase(projectId, testPlanId, requirements);

        Map<UUID, List<TraceabilityRowResponse.Cell>> cellsById = new HashMap<>();
        for (Requirement requirement : requirements) {
            cellsById.put(requirement.getId(), requirement.getTestCases().stream()
                    .sorted(Comparator.comparing(TestCase::getKey))
                    .map(tc -> new TraceabilityRowResponse.Cell(
                            tc.getId(), tc.getKey(), tc.getTitle(),
                            latestByCase.getOrDefault(tc.getId(), CoverageStatus.UNTESTED)))
                    .toList());
        }
        Map<UUID, List<Requirement>> liveChildren = liveChildrenByParent(requirements);
        Map<UUID, CoverageStatus> coverageById = new HashMap<>();

        List<TraceabilityRowResponse> rows = new ArrayList<>();
        for (Requirement requirement : requirements) {
            List<TraceabilityRowResponse.Cell> cells = cellsById.get(requirement.getId());
            rows.add(new TraceabilityRowResponse(
                    requirement.getId(), requirement.getExternalId(), requirement.getTitle(),
                    requirement.getParentId(), requirement.getStatus(), requirement.getPriority(), cells,
                    coverageOf(requirement, cellsById, liveChildren, coverageById)));
        }
        return rows;
    }

    /** Children that still need proving, by parent id; an obsolete child does not hold its parent back. */
    private static Map<UUID, List<Requirement>> liveChildrenByParent(List<Requirement> requirements) {
        Map<UUID, List<Requirement>> children = new HashMap<>();
        requirements.stream()
                .filter(r -> r.getParentId() != null && r.getStatus() != RequirementStatus.OBSOLETE)
                .forEach(r -> children.computeIfAbsent(r.getParentId(), k -> new ArrayList<>()).add(r));
        return children;
    }

    /**
     * A leaf's coverage is its worst cell; a parent's is the worst over its own cells and its live
     * children (TM18) — covered only when every leaf under it is. Memoised; cycles cannot occur
     * because writes refuse them.
     */
    private static CoverageStatus coverageOf(Requirement requirement,
                                             Map<UUID, List<TraceabilityRowResponse.Cell>> cellsById,
                                             Map<UUID, List<Requirement>> liveChildren,
                                             Map<UUID, CoverageStatus> memo) {
        CoverageStatus known = memo.get(requirement.getId());
        if (known != null) {
            return known;
        }
        List<TraceabilityRowResponse.Cell> cells = cellsById.get(requirement.getId());
        List<Requirement> children = liveChildren.getOrDefault(requirement.getId(), List.of());
        CoverageStatus coverage;
        if (children.isEmpty()) {
            coverage = rollUp(cells);
        } else {
            Set<CoverageStatus> statuses = new HashSet<>();
            cells.forEach(c -> statuses.add(c.status()));
            children.forEach(child -> statuses.add(coverageOf(child, cellsById, liveChildren, memo)));
            coverage = worst(statuses);
        }
        memo.put(requirement.getId(), coverage);
        return coverage;
    }

    public CoverageSummaryResponse coverage(UUID projectId) {
        return coverage(projectId, null);
    }

    public CoverageSummaryResponse coverage(UUID projectId, UUID testPlanId) {
        // Only live leaves count: a parent's coverage is derived from them (counting it too would
        // weigh one epic like each of its stories), and an obsolete requirement needs no proof.
        List<TraceabilityRowResponse> all = matrix(projectId, testPlanId);
        Set<UUID> liveParents = new HashSet<>();
        all.stream()
                .filter(r -> r.parentId() != null && r.status() != RequirementStatus.OBSOLETE)
                .forEach(r -> liveParents.add(r.parentId()));
        List<TraceabilityRowResponse> rows = all.stream()
                .filter(r -> r.status() != RequirementStatus.OBSOLETE && !liveParents.contains(r.requirementId()))
                .toList();
        long total = rows.size();
        long uncovered = rows.stream().filter(r -> r.coverage() == CoverageStatus.UNCOVERED).count();
        long untested = rows.stream().filter(r -> r.coverage() == CoverageStatus.UNTESTED).count();
        long failing = rows.stream().filter(r ->
                r.coverage() == CoverageStatus.FAILED
                        || r.coverage() == CoverageStatus.BLOCKED
                        || r.coverage() == CoverageStatus.SKIPPED).count();
        long passing = rows.stream().filter(r -> r.coverage() == CoverageStatus.PASSED).count();

        double percent = total == 0 ? 0.0 : Math.round((passing * 10000.0) / total) / 100.0;
        return new CoverageSummaryResponse(total, uncovered, untested, failing, passing, percent);
    }

    /**
     * Rolls a row up to its worst cell. A requirement is only as proven as its weakest test: one
     * failing case among five passing ones must not read as covered.
     */
    private static CoverageStatus rollUp(List<TraceabilityRowResponse.Cell> cells) {
        if (cells.isEmpty()) {
            return CoverageStatus.UNCOVERED;
        }
        Set<CoverageStatus> statuses = new HashSet<>();
        cells.forEach(c -> statuses.add(c.status()));
        return worst(statuses);
    }

    /** Severity order, worst first; a gap below a parent (UNCOVERED) outranks a skip. */
    private static CoverageStatus worst(Set<CoverageStatus> statuses) {
        for (CoverageStatus candidate : List.of(CoverageStatus.FAILED, CoverageStatus.BLOCKED,
                CoverageStatus.UNTESTED, CoverageStatus.UNCOVERED, CoverageStatus.SKIPPED)) {
            if (statuses.contains(candidate)) {
                return candidate;
            }
        }
        return CoverageStatus.PASSED;
    }

    /**
     * Latest terminal status per linked test case, reusing the completed-run query the suite report
     * already relies on (PRD-014 §3.3) rather than introducing a second definition of "latest".
     */
    private Map<UUID, CoverageStatus> latestStatusByTestCase(UUID projectId, UUID testPlanId,
                                                             List<Requirement> requirements) {
        Set<UUID> caseIds = new HashSet<>();
        requirements.forEach(r -> r.getTestCases().forEach(tc -> caseIds.add(tc.getId())));
        if (caseIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, CoverageStatus> latest = new HashMap<>();
        // Ordered newest first by the query, so the first entry per case wins.
        List<TestResult> results = testPlanId == null
                ? testResultRepository.findByTestCaseIdsAndCompletedRuns(caseIds, projectId)
                : testResultRepository.findByTestCaseIdsAndCompletedRunsOfPlan(caseIds, projectId, testPlanId);
        for (TestResult result : results) {
            latest.putIfAbsent(result.getTestCase().getId(), switch (result.getStatus()) {
                case PASSED -> CoverageStatus.PASSED;
                case FAILED -> CoverageStatus.FAILED;
                case BLOCKED -> CoverageStatus.BLOCKED;
                case SKIPPED -> CoverageStatus.SKIPPED;
                case PENDING -> CoverageStatus.UNTESTED;
            });
        }
        return latest;
    }

    // ---- helpers ----------------------------------------------------------

    /**
     * The parent, checked: in this project, and neither the requirement itself nor one of its
     * descendants — either would make a cycle. {@code id} is null for a new requirement.
     */
    UUID validParent(UUID projectId, UUID id, UUID parentId) {
        if (parentId == null) {
            return null;
        }
        Requirement ancestor = require(projectId, parentId);
        Set<UUID> seen = new HashSet<>();
        while (ancestor != null && seen.add(ancestor.getId())) {
            if (ancestor.getId().equals(id)) {
                throw new IllegalArgumentException("A requirement cannot be placed under itself or one of its children");
            }
            ancestor = ancestor.getParentId() == null ? null
                    : requirementRepository.findById(ancestor.getParentId()).orElse(null);
        }
        return parentId;
    }

    /**
     * TM19: a synced requirement's own fields belong to the tracker, so a change to any of them is
     * refused; sending them back unchanged is fine, which is what an edit of only the custom fields
     * through this full-replace API does.
     */
    private static void refuseSyncedFieldChanges(Requirement requirement, SaveRequirementRequest request,
                                                 String externalId) {
        if (requirement.getSource() == null) {
            return;
        }
        List<String> changed = new ArrayList<>();
        if (!requirement.getExternalId().equals(externalId)) {
            changed.add("externalId");
        }
        if (!requirement.getTitle().equals(request.title().trim())) {
            changed.add("title");
        }
        if (!Objects.equals(blankToNull(requirement.getDescription()), blankToNull(request.description()))) {
            changed.add("description");
        }
        if (statusOf(request) != requirement.getStatus()) {
            changed.add("status");
        }
        if (priorityOf(request) != requirement.getPriority()) {
            changed.add("priority");
        }
        if (!Objects.equals(requirement.getParentId(), request.parentId())) {
            changed.add("parent");
        }
        if (!changed.isEmpty()) {
            throw new IllegalArgumentException("Requirement " + requirement.getExternalId() + " is synced from "
                    + SYNC_SOURCE_NAMES.getOrDefault(requirement.getSource(), requirement.getSource().name())
                    + "; change " + String.join(", ", changed) + " there. Only custom fields and test case "
                    + "links can be edited here.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Null status / priority mean the defaults, as for a new requirement. */
    private static void applyFields(Requirement requirement, SaveRequirementRequest request) {
        requirement.setStatus(statusOf(request));
        requirement.setPriority(priorityOf(request));
    }

    private static RequirementStatus statusOf(SaveRequirementRequest request) {
        return request.status() == null ? RequirementStatus.DRAFT : request.status();
    }

    private static Priority priorityOf(SaveRequirementRequest request) {
        return request.priority() == null ? Priority.MEDIUM : request.priority();
    }

    private Requirement require(UUID projectId, UUID id) {
        return requirementRepository.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Requirement", id));
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    private static RequirementResponse toResponse(Requirement requirement) {
        List<RequirementResponse.LinkedTestCase> cases = requirement.getTestCases().stream()
                .sorted(Comparator.comparing(TestCase::getKey))
                .map(tc -> new RequirementResponse.LinkedTestCase(tc.getId(), tc.getKey(), tc.getTitle()))
                .toList();
        return new RequirementResponse(
                requirement.getId(),
                requirement.getExternalId(),
                requirement.getTitle(),
                requirement.getDescription(),
                requirement.getParentId(),
                requirement.getStatus(),
                requirement.getPriority(),
                CustomFieldValueMaps.toMap(requirement.getCustomFieldValues()),
                cases,
                requirement.getCreatedAt(),
                requirement.getUpdatedAt(),
                requirement.getSource(),
                requirement.getSourceUrl(),
                requirement.getLastSyncedAt(),
                requirement.getBugReports().stream()
                        .filter(bug -> bug.getStatus().isOpen())
                        .sorted(Comparator.comparing(BugReport::getKey))
                        .map(bug -> new RequirementResponse.OpenBug(bug.getId(), bug.getKey(), bug.getTitle(),
                                bug.getStatus()))
                        .toList());
    }
}
