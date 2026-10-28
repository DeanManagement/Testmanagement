package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseMapper;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Changes one step of a case and leaves the others as they are — same rows, same ids, same images
 * (TM06). A full {@code update} rebuilds every step; these exist so an agent can fix one step
 * without putting the screenshots a human attached to the others at risk.
 *
 * <p>Positions are zero-based in the case's own step list, where a shared step counts as one.
 */
@Service
@RequiredArgsConstructor
public class TestStepEditService {

    private final TestCaseRepository testCaseRepository;
    private final TestCaseService testCaseService;
    private final TestCaseVersionService versionService;
    private final TestCaseReviewService reviewService;
    private final AuditService auditService;
    private final TestCaseMapper testCaseMapper;

    /** Inserts at {@code position}; the step there and those after it move down one. */
    @Transactional
    public TestCaseResponse insertStep(UUID projectId, UUID testCaseId, int position, TestStepRequest request,
                                       UUID userId) {
        return edit(projectId, testCaseId, userId, (tc) -> {
            List<TestStep> ordered = ordered(tc);
            requirePosition(position, ordered.size() + 1);
            TestStep step = testCaseService.buildSteps(List.of(request), tc).getFirst();
            ordered.add(position, step);
            tc.getSteps().add(step);
            reindex(tc, ordered);
            return "Inserted step " + (position + 1);
        });
    }

    @Transactional
    public TestCaseResponse moveStep(UUID projectId, UUID testCaseId, int from, int to, UUID userId) {
        return edit(projectId, testCaseId, userId, (tc) -> {
            List<TestStep> ordered = ordered(tc);
            requirePosition(from, ordered.size());
            requirePosition(to, ordered.size());
            ordered.add(to, ordered.remove(from));
            reindex(tc, ordered);
            return "Moved step " + (from + 1) + " to " + (to + 1);
        });
    }

    /** Removes one step, its image with it; the steps after it move up one. */
    @Transactional
    public TestCaseResponse removeStep(UUID projectId, UUID testCaseId, int position, UUID userId) {
        return edit(projectId, testCaseId, userId, (tc) -> {
            List<TestStep> ordered = ordered(tc);
            requirePosition(position, ordered.size());
            tc.getSteps().remove(ordered.remove(position));
            reindex(tc, ordered);
            return "Removed step " + (position + 1);
        });
    }

    /** Versions, review and audit exactly as a full edit of the steps would. */
    private TestCaseResponse edit(UUID projectId, UUID testCaseId, UUID userId, Function<TestCase, String> change) {
        TestCase tc = testCaseRepository.findByIdAndProjectId(testCaseId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", testCaseId));
        TestCaseStatus statusBefore = tc.getStatus();
        int versionBefore = tc.getCurrentVersion();
        versionService.snapshotBeforeEdit(tc);

        String detail = change.apply(tc);
        reviewService.afterEdit(tc, statusBefore, versionBefore, true);

        TestCase saved = testCaseRepository.save(tc);
        auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.TEST_CASE,
                saved.getId(), saved.getTitle(), detail);
        return testCaseMapper.toResponse(saved);
    }

    private static List<TestStep> ordered(TestCase tc) {
        return new ArrayList<>(tc.getSteps().stream().sorted(Comparator.comparingInt(TestStep::getOrderIndex)).toList());
    }

    /** Renumbers from the given order, and sorts the case's list too: the response is mapped from it. */
    private static void reindex(TestCase tc, List<TestStep> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setOrderIndex(i);
        }
        tc.getSteps().sort(Comparator.comparingInt(TestStep::getOrderIndex));
    }

    private static void requirePosition(int position, int bound) {
        if (position < 0 || position >= bound) {
            String range = bound == 0 ? "the case has no steps" : "valid positions are 1-" + bound;
            throw new IllegalArgumentException("Step position " + (position + 1) + " is out of range: " + range);
        }
    }
}
