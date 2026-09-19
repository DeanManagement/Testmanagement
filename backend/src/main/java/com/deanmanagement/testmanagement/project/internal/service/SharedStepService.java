package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SaveSharedStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepResponse;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepSummary;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepUsage;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseMapper;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.SharedStep;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.SharedStepRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.spec.LikePatterns;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import com.deanmanagement.testmanagement.shared.exception.DuplicateKeyException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Shared step blocks (PRD-030): named, ordered steps kept once per project and referenced from
 * test cases. Every lookup is scoped to the project in the path, so a foreign id is a 404.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SharedStepService {

    private final SharedStepRepository sharedStepRepository;
    private final ProjectRepository projectRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestCaseMapper testCaseMapper;
    private final AuditService auditService;
    private final TestCaseVersionService versionService;
    private final TestCaseReviewService reviewService;

    public Page<SharedStepSummary> list(UUID projectId, String query, Pageable pageable) {
        Page<SharedStep> page = sharedStepRepository.search(projectId,
                LikePatterns.containing(query == null ? "" : query.trim()), pageable);
        Map<UUID, Long> usage = usageCounts(page.getContent().stream().map(SharedStep::getId).toList());
        return page.map(s -> new SharedStepSummary(s.getId(), s.getTitle(), s.getDescription(), s.getSteps().size(),
                usage.getOrDefault(s.getId(), 0L), s.getUpdatedAt()));
    }

    public SharedStepResponse get(UUID projectId, UUID id) {
        return toResponse(require(projectId, id));
    }

    public List<SharedStepUsage> usages(UUID projectId, UUID id) {
        require(projectId, id);
        return testCaseRepository.findUsingSharedStep(id).stream()
                .map(tc -> new SharedStepUsage(tc.getId(), tc.getKey(), tc.getTitle()))
                .toList();
    }

    @Transactional
    public SharedStepResponse create(UUID projectId, SaveSharedStepRequest request, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        String title = request.title().trim();
        if (sharedStepRepository.existsByProjectIdAndTitle(projectId, title)) {
            throw new DuplicateKeyException("title", title);
        }
        SharedStep block = new SharedStep();
        block.setProject(project);
        block.setTitle(title);
        block.setDescription(request.description());
        applySteps(block, request.steps());
        block = sharedStepRepository.save(block);
        auditService.log(projectId, userId, AuditAction.CREATED, AuditEntityType.SHARED_STEP,
                block.getId(), block.getTitle(), null);
        return toResponse(block);
    }

    /**
     * Steps are matched by id and updated in place, not cleared and rebuilt: a rebuilt step is a new
     * row, and every recorded result pointing at the old one — in every run of every case using the
     * block — would lose its step text.
     *
     * <p>When the steps change, every case using the block gets a version first, holding the wording
     * it had until now (PRD-011), and counts as a content edit for review (PRD-033): an approved case
     * whose block changed goes back to review, as it would if its own steps had been edited.
     */
    @Transactional
    public SharedStepResponse update(UUID projectId, UUID id, SaveSharedStepRequest request, UUID userId) {
        SharedStep block = require(projectId, id);
        String title = request.title().trim();
        if (!block.getTitle().equals(title) && sharedStepRepository.existsByProjectIdAndTitle(projectId, title)) {
            throw new DuplicateKeyException("title", title);
        }
        List<TestCase> users = testCaseRepository.findUsingSharedStep(id);
        if (isStepsChanged(block, request.steps())) {
            // Before applySteps: the snapshot must read the block's old text.
            for (TestCase tc : users) {
                TestCaseStatus statusBefore = tc.getStatus();
                int versionBefore = tc.getCurrentVersion();
                versionService.snapshotBeforeEdit(tc);
                reviewService.afterEdit(tc, statusBefore, versionBefore, true);
            }
        }
        if (!block.getTitle().equals(title)) {
            // Reference rows keep the title as their fallback text.
            users.forEach(tc -> tc.getSteps().stream()
                    .filter(step -> step.getUsesSharedStep() != null && id.equals(step.getUsesSharedStep().getId()))
                    .forEach(step -> step.setAction(title)));
        }
        block.setTitle(title);
        block.setDescription(request.description());
        applySteps(block, request.steps());
        block = sharedStepRepository.save(block);
        auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.SHARED_STEP,
                block.getId(), block.getTitle(), null);
        return toResponse(block);
    }

    /** Refused while any case references the block: those cases would silently lose steps. */
    @Transactional
    public void delete(UUID projectId, UUID id, UUID userId) {
        SharedStep block = require(projectId, id);
        long users = usageCounts(List.of(id)).getOrDefault(id, 0L);
        if (users > 0) {
            throw new ConflictException("\"" + block.getTitle() + "\" is used by " + users
                    + " test case(s). Convert or remove those references first.");
        }
        sharedStepRepository.delete(block);
        auditService.log(projectId, userId, AuditAction.DELETED, AuditEntityType.SHARED_STEP,
                block.getId(), block.getTitle(), null);
    }

    /** The block, if it belongs to the project; otherwise a 404, never another project's block. */
    public SharedStep require(UUID projectId, UUID id) {
        return sharedStepRepository.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("SharedStep", id));
    }

    // ---- helpers --------------------------------------------------------------------------------

    /** Whether what a tester executes changes: a step added, removed, moved or reworded. */
    private static boolean isStepsChanged(SharedStep block, List<SharedStepStepRequest> requested) {
        List<TestStep> current = block.getSteps().stream()
                .sorted(Comparator.comparingInt(TestStep::getOrderIndex)).toList();
        if (current.size() != requested.size()) {
            return true;
        }
        for (int i = 0; i < current.size(); i++) {
            TestStep have = current.get(i);
            SharedStepStepRequest want = requested.get(i);
            if (!have.getId().equals(want.id())
                    || !Objects.equals(emptyToNull(have.getAction()), emptyToNull(want.action()))
                    || !Objects.equals(emptyToNull(have.getExpectedResult()), emptyToNull(want.expectedResult()))
                    || !Objects.equals(emptyToNull(have.getTestData()), emptyToNull(want.testData()))) {
                return true;
            }
        }
        return false;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private void applySteps(SharedStep block, List<SharedStepStepRequest> requested) {
        Map<UUID, TestStep> existing = block.getSteps().stream()
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(TestStep::getId, s -> s));
        Set<UUID> kept = new HashSet<>();
        for (SharedStepStepRequest step : requested) {
            if (step.id() != null && (!existing.containsKey(step.id()) || !kept.add(step.id()))) {
                throw new IllegalArgumentException("Step " + step.id() + " is not a step of this shared step");
            }
        }
        // Removed steps go (orphan removal); their recorded results keep their row, step unset (V28).
        block.getSteps().removeIf(s -> s.getId() != null && !kept.contains(s.getId()));
        for (int i = 0; i < requested.size(); i++) {
            SharedStepStepRequest request = requested.get(i);
            TestStep step = request.id() != null ? existing.get(request.id()) : newStep(block);
            step.setAction(request.action());
            step.setExpectedResult(request.expectedResult());
            step.setTestData(request.testData());
            step.setOrderIndex(i);
        }
    }

    private static TestStep newStep(SharedStep block) {
        TestStep step = new TestStep();
        step.setSharedStep(block);
        block.getSteps().add(step);
        return step;
    }

    private Map<UUID, Long> usageCounts(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : sharedStepRepository.countUsages(ids)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private SharedStepResponse toResponse(SharedStep block) {
        return new SharedStepResponse(block.getId(), block.getTitle(), block.getDescription(),
                block.getSteps().stream()
                        .sorted((a, b) -> Integer.compare(a.getOrderIndex(), b.getOrderIndex()))
                        .map(testCaseMapper::toStepResponse)
                        .toList(),
                usageCounts(List.of(block.getId())).getOrDefault(block.getId(), 0L),
                block.getCreatedAt(), block.getUpdatedAt());
    }
}
