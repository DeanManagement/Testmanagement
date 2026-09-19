package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.comparison.RunComparisonResponse;
import com.deanmanagement.testmanagement.project.internal.dto.comparison.RunComparisonResponse.Category;
import com.deanmanagement.testmanagement.project.internal.dto.comparison.RunComparisonResponse.RunRef;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Compares two runs of one project (PRD-038 §3.3). Read-only; the classification itself is the
 * pure {@link RunComparator}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RunComparisonService {

    private static final Pageable FIRST = PageRequest.of(0, 1);

    private final TestRunRepository testRunRepository;
    private final TestResultRepository testResultRepository;

    /**
     * @param baseId null to pick the base: the latest earlier non-aborted run with the head's name,
     *               else with its test plan and environment
     */
    public RunComparisonResponse compare(UUID projectId, UUID headId, UUID baseId, ComparisonRows rows) {
        if (headId.equals(baseId)) {
            throw new IllegalArgumentException("Base and head are the same run; pick two different runs");
        }
        TestRun head = require(projectId, headId);
        TestRun base = baseId != null ? require(projectId, baseId) : previousOf(projectId, head)
                .orElseThrow(() -> new ResourceNotFoundException("No earlier run with the same name, or the same test "
                        + "plan and environment, to compare " + head.getKey() + " with; pick a base run"));

        RunComparator.Comparison comparison = RunComparator.compare(
                testResultRepository.findComparableResults(base.getId()),
                testResultRepository.findComparableResults(head.getId()));
        return new RunComparisonResponse(refOf(base), refOf(head), baseId == null, comparison.counts(),
                rows == ComparisonRows.CHANGES_ONLY
                        ? comparison.rows().stream().filter(row -> row.category() != Category.UNCHANGED).toList()
                        : comparison.rows());
    }

    /** Which rows a comparison lists; unchanged ones are always counted. */
    public enum ComparisonRows { CHANGES_ONLY, ALL }

    /**
     * CI runs from one workflow share a name, so that is the strongest signal. Same plan and
     * environment is the fallback for manual runs, and only when the head has a plan: two unrelated
     * ad-hoc runs that merely share having no plan are not a sensible pair.
     */
    private Optional<TestRun> previousOf(UUID projectId, TestRun head) {
        Instant before = happenedAt(head);
        List<TestRun> sameName = testRunRepository.findPreviousWithName(projectId, head.getId(), head.getName(), before,
                FIRST);
        if (!sameName.isEmpty()) {
            return Optional.of(sameName.getFirst());
        }
        if (head.getTestPlan() == null) {
            return Optional.empty();
        }
        return testRunRepository.findPreviousInPlanAndEnvironment(projectId, head.getId(), head.getTestPlan().getId(),
                head.getEnvironment() == null ? "" : head.getEnvironment(), before, FIRST).stream().findFirst();
    }

    private TestRun require(UUID projectId, UUID runId) {
        return testRunRepository.findByIdAndProjectId(runId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", runId));
    }

    private static RunRef refOf(TestRun run) {
        return new RunRef(run.getId(), run.getKey(), run.getName(), run.getEnvironment(), run.getStatus(),
                run.getEndTime(), happenedAt(run));
    }

    /** The ordering PRD-016 settled on, so a backfilled CI run sorts by when it ran. */
    private static Instant happenedAt(TestRun run) {
        if (run.getEndTime() != null) {
            return run.getEndTime();
        }
        return run.getStartTime() != null ? run.getStartTime() : run.getCreatedAt();
    }
}
