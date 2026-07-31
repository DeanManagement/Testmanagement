package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.requirement.CoverageSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.RequirementResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.SaveRequirementRequest;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.TraceabilityRowResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.TraceabilityRowResponse.CoverageStatus;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Requirement;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.repository.RequirementRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
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

    private final RequirementRepository requirementRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestResultRepository testResultRepository;
    private final AuditService auditService;

    // ---- CRUD -------------------------------------------------------------

    public Page<RequirementResponse> list(UUID projectId, Pageable pageable) {
        return requirementRepository.findByProjectIdOrderByExternalIdAsc(projectId, pageable)
                .map(RequirementService::toResponse);
    }

    public RequirementResponse get(UUID projectId, UUID id) {
        return toResponse(require(projectId, id));
    }

    @Transactional
    public RequirementResponse create(UUID projectId, SaveRequirementRequest request, UUID actorId) {
        String externalId = request.externalId().trim();
        if (requirementRepository.existsByProjectIdAndExternalId(projectId, externalId)) {
            throw new DuplicateKeyException("externalId", externalId);
        }
        Requirement requirement = new Requirement();
        requirement.setProjectId(projectId);
        requirement.setExternalId(externalId);
        requirement.setTitle(request.title().trim());
        requirement.setDescription(request.description());
        requirement = requirementRepository.save(requirement);

        auditService.log(projectId, actorId, AuditAction.CREATED,
                AuditEntityType.REQUIREMENT, requirement.getId(), requirement.getExternalId(), null);
        return toResponse(requirement);
    }

    @Transactional
    public RequirementResponse update(UUID projectId, UUID id, SaveRequirementRequest request, UUID actorId) {
        Requirement requirement = require(projectId, id);
        String externalId = request.externalId().trim();

        if (!requirement.getExternalId().equals(externalId)
                && requirementRepository.existsByProjectIdAndExternalId(projectId, externalId)) {
            throw new DuplicateKeyException("externalId", externalId);
        }
        requirement.setExternalId(externalId);
        requirement.setTitle(request.title().trim());
        requirement.setDescription(request.description());
        requirement = requirementRepository.save(requirement);

        auditService.log(projectId, actorId, AuditAction.UPDATED,
                AuditEntityType.REQUIREMENT, requirement.getId(), requirement.getExternalId(), null);
        return toResponse(requirement);
    }

    @Transactional
    public void delete(UUID projectId, UUID id, UUID actorId) {
        Requirement requirement = require(projectId, id);
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
        List<Requirement> requirements = requirementRepository.findByProjectIdOrderByExternalIdAsc(projectId);
        if (requirements.isEmpty()) {
            return List.of();
        }
        Map<UUID, CoverageStatus> latestByCase = latestStatusByTestCase(projectId, requirements);

        List<TraceabilityRowResponse> rows = new ArrayList<>();
        for (Requirement requirement : requirements) {
            List<TraceabilityRowResponse.Cell> cells = requirement.getTestCases().stream()
                    .sorted(Comparator.comparing(TestCase::getKey))
                    .map(tc -> new TraceabilityRowResponse.Cell(
                            tc.getId(), tc.getKey(), tc.getTitle(),
                            latestByCase.getOrDefault(tc.getId(), CoverageStatus.UNTESTED)))
                    .toList();

            rows.add(new TraceabilityRowResponse(
                    requirement.getId(), requirement.getExternalId(), requirement.getTitle(),
                    cells, rollUp(cells)));
        }
        return rows;
    }

    public CoverageSummaryResponse coverage(UUID projectId) {
        List<TraceabilityRowResponse> rows = matrix(projectId);
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

        // Severity order, worst first.
        for (CoverageStatus candidate : List.of(CoverageStatus.FAILED, CoverageStatus.BLOCKED,
                CoverageStatus.UNTESTED, CoverageStatus.SKIPPED)) {
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
    private Map<UUID, CoverageStatus> latestStatusByTestCase(UUID projectId, List<Requirement> requirements) {
        Set<UUID> caseIds = new HashSet<>();
        requirements.forEach(r -> r.getTestCases().forEach(tc -> caseIds.add(tc.getId())));
        if (caseIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, CoverageStatus> latest = new HashMap<>();
        // Ordered newest first by the query, so the first entry per case wins.
        for (TestResult result : testResultRepository.findByTestCaseIdsAndCompletedRuns(caseIds, projectId)) {
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

    private Requirement require(UUID projectId, UUID id) {
        return requirementRepository.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Requirement", id));
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
                cases,
                requirement.getCreatedAt(),
                requirement.getUpdatedAt());
    }
}
