package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.TestRunMapper;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.WatchableEntityType;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.user.UserService;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Which cases a run holds, and in which order, after it was created: add (TM22), remove (TM22) and
 * reorder (TM21). A completed or aborted run is a record of what happened, so all three need it
 * reopened first.
 */
@Service
@RequiredArgsConstructor
public class TestRunCaseService {

    /** Key order with numbers read as numbers: PROJ-2 before PROJ-10. */
    private static final Comparator<TestCase> BY_KEY =
            Comparator.comparingInt((TestCase tc) -> tc.getKey().length()).thenComparing(TestCase::getKey);

    private final TestRunRepository testRunRepository;
    private final TestRunService testRunService;
    private final TestRunMapper testRunMapper;
    private final AuditService auditService;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserService userService;
    private final WatcherService watcherService;

    /**
     * Adds pending results exactly as creating the run would, at the end of the run. Cases already in
     * the run are skipped rather than added twice.
     */
    @Transactional
    public TestRunResponse addTestCases(UUID projectId, UUID runId, Set<UUID> testCaseIds, UUID userId) {
        TestRun run = requireOpenRun(projectId, runId);
        Set<UUID> present = run.getResults().stream().map(r -> r.getTestCase().getId()).collect(Collectors.toSet());
        List<TestCase> added = testRunService.resolveTestCases(projectId, testCaseIds).stream()
                .filter(tc -> !present.contains(tc.getId()))
                .sorted(BY_KEY)
                .toList();
        added.forEach(tc -> testRunService.appendPendingResults(run, tc));

        TestRun saved = testRunRepository.save(run);
        if (!added.isEmpty()) {
            auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.TEST_RUN, saved.getId(),
                    saved.getName(), "Added " + keys(added));
        }
        return testRunMapper.toResponse(saved);
    }

    /**
     * Removes every result of the given cases. Refused while one of them holds a recorded outcome,
     * unless {@code discardRecorded}: the caller must have said, knowingly, that it may go.
     */
    @Transactional
    public TestRunResponse removeTestCases(UUID projectId, UUID runId, Set<UUID> testCaseIds, boolean discardRecorded,
                                           UUID userId) {
        TestRun run = requireOpenRun(projectId, runId);
        List<TestResult> removed = run.getResults().stream()
                .filter(r -> testCaseIds.contains(r.getTestCase().getId()))
                .toList();
        Set<UUID> found = removed.stream().map(r -> r.getTestCase().getId()).collect(Collectors.toSet());
        Set<UUID> missing = new HashSet<>(testCaseIds);
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            throw new ResourceNotFoundException("TestCase(s) not in this run: " + sorted(missing));
        }
        List<TestResult> recorded = removed.stream().filter(r -> r.getStatus() != TestResultStatus.PENDING).toList();
        if (!recorded.isEmpty() && !discardRecorded) {
            throw new ConflictException("These test cases already have a recorded result, which removing them "
                    + "would discard: " + recorded.stream()
                    .map(r -> r.getTestCase().getKey() + " (" + r.getStatus() + ")")
                    .distinct().sorted().collect(Collectors.joining(", ")));
        }
        run.getResults().removeAll(removed);

        TestRun saved = testRunRepository.save(run);
        auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.TEST_RUN, saved.getId(),
                saved.getName(), "Removed " + keys(removed.stream().map(TestResult::getTestCase).distinct().toList())
                        + (recorded.isEmpty() ? "" : ", discarding " + recorded.size() + " recorded result(s)"));
        return testRunMapper.toResponse(saved);
    }

    /** {@code resultIds} names every result of the run once, in the new order. */
    @Transactional
    public TestRunResponse reorder(UUID projectId, UUID runId, List<UUID> resultIds, UUID userId) {
        TestRun run = requireOpenRun(projectId, runId);
        Map<UUID, TestResult> byId = run.getResults().stream()
                .collect(Collectors.toMap(TestResult::getId, Function.identity()));
        if (resultIds.size() != byId.size() || !byId.keySet().equals(new HashSet<>(resultIds))) {
            throw new IllegalArgumentException("Name every result of the run exactly once: the run has "
                    + byId.size() + " results, " + resultIds.size() + " ids were given");
        }
        for (int i = 0; i < resultIds.size(); i++) {
            byId.get(resultIds.get(i)).setPosition(i);
        }
        run.getResults().sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));

        TestRun saved = testRunRepository.save(run);
        auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.TEST_RUN, saved.getId(),
                saved.getName(), "Reordered the test cases");
        return testRunMapper.toResponse(saved);
    }

    /**
     * TM23: assigns results to a project member, or with a null {@code assigneeId} back to the run's
     * executor. The assignee starts watching the run, so the audit entry below notifies them (PRD-006).
     */
    @Transactional
    public TestRunResponse assign(UUID projectId, UUID runId, Set<UUID> resultIds, UUID assigneeId, UUID userId) {
        TestRun run = requireOpenRun(projectId, runId);
        List<TestResult> results = run.getResults().stream().filter(r -> resultIds.contains(r.getId())).toList();
        Set<UUID> missing = new HashSet<>(resultIds);
        results.forEach(r -> missing.remove(r.getId()));
        if (!missing.isEmpty()) {
            throw new ResourceNotFoundException("Result(s) not in this run: " + sorted(missing));
        }
        String assigneeName = null;
        if (assigneeId != null) {
            if (!projectMemberRepository.existsByUserIdAndProjectId(assigneeId, projectId)) {
                throw new ResourceNotFoundException("User", assigneeId);
            }
            assigneeName = userService.findDisplayNamesByIds(Set.of(assigneeId)).get(assigneeId);
            watcherService.watch(assigneeId, WatchableEntityType.TEST_RUN, runId);
        }
        results.forEach(r -> r.setAssigneeId(assigneeId));

        TestRun saved = testRunRepository.save(run);
        String cases = keys(results.stream().map(TestResult::getTestCase).distinct().toList());
        auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.TEST_RUN, saved.getId(),
                saved.getName(), assigneeId == null ? "Unassigned " + cases : "Assigned " + cases + " to " + assigneeName);
        return testRunMapper.toResponse(saved);
    }

    private TestRun requireOpenRun(UUID projectId, UUID runId) {
        TestRun run = testRunRepository.findById(runId)
                .filter(r -> r.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", runId));
        if (TestRunService.isClosed(run.getStatus())) {
            throw new ConflictException("A " + run.getStatus().name().toLowerCase()
                    + " test run cannot change its test cases; reopen it first");
        }
        return run;
    }

    private static String keys(List<TestCase> cases) {
        return cases.stream().map(TestCase::getKey).sorted().collect(Collectors.joining(", "));
    }

    private static String sorted(Set<UUID> ids) {
        return ids.stream().map(UUID::toString).sorted().collect(Collectors.joining(", "));
    }
}
