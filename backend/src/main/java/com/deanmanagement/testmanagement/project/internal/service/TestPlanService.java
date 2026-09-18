package com.deanmanagement.testmanagement.project.internal.service;

import java.time.Instant;
import java.time.Duration;
import java.time.Clock;
import com.deanmanagement.testmanagement.project.internal.repository.ExploratorySessionRepository;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.CreateTestPlanRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanMapper;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanRunSummary;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.UpdateTestPlanRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlan;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestPlanService {

    private final TestPlanRepository testPlanRepository;
    private final ProjectRepository projectRepository;
    private final TestPlanMapper testPlanMapper;
    private final AuditService auditService;
    private final UserService userService;
    private final ProjectMemberRepository projectMemberRepository;
    private final ExploratorySessionRepository sessionRepository;
    private final Clock clock;

    /**
     * Resolves an assignee who must already be a member of this project (PRD-027 §3.5).
     *
     * <p>Was {@code userService.findEntityById(..).orElse(null)}: any user in the instance could be
     * made assignee of any plan, and a mistyped id quietly produced an unassigned plan. Assigned
     * plans surface in the assignee's "My queue" widget, which reads by assignee id and never
     * checks membership — so this also stopped a plan from appearing in the queue of someone with
     * no access to it.
     */
    private com.deanmanagement.testmanagement.user.User requireProjectMember(UUID projectId, UUID userId) {
        if (!projectMemberRepository.existsByUserIdAndProjectId(userId, projectId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        return userService.findEntityById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    public List<TestPlanResponse> findByProject(UUID projectId) {
        return testPlanRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(testPlanMapper::toResponse)
                .toList();
    }

    public TestPlanResponse findById(UUID projectId, UUID id) {
        TestPlan plan = testPlanRepository.findById(id)
                .filter(p -> p.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestPlan", id));
        return testPlanMapper.toResponse(plan);
    }

    public TestPlanSummaryResponse getSummary(UUID projectId, UUID planId) {
        TestPlan plan = testPlanRepository.findById(planId)
                .filter(p -> p.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestPlan", planId));

        List<TestRun> runs = plan.getTestRuns();
        int totalRuns = runs.size();
        int completedRuns = (int) runs.stream()
                .filter(r -> r.getStatus() == TestRunStatus.COMPLETED)
                .count();

        int totalResults = 0;
        int passed = 0;
        int failed = 0;
        int blocked = 0;
        int skipped = 0;
        int pending = 0;

        List<TestPlanRunSummary> runSummaries = new java.util.ArrayList<>();

        for (TestRun run : runs) {
            List<TestResult> results = run.getResults();
            int runTotal = results.size();
            int runPassed = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.PASSED).count();
            int runFailed = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.FAILED).count();
            int runBlocked = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.BLOCKED).count();
            int runSkipped = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.SKIPPED).count();
            int runPending = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.PENDING).count();

            totalResults += runTotal;
            passed += runPassed;
            failed += runFailed;
            blocked += runBlocked;
            skipped += runSkipped;
            pending += runPending;

            runSummaries.add(new TestPlanRunSummary(
                    run.getId(), run.getName(), run.getEnvironment(), run.getStatus(),
                    runTotal, runPassed, runFailed, run.getEndTime()
            ));
        }

        double passRate = totalResults > 0 ? Math.round(passed * 10000.0 / totalResults) / 100.0 : 0.0;

        return new TestPlanSummaryResponse(
                plan.getId(), plan.getName(), plan.getStatus(), plan.getTargetDate(),
                totalRuns, completedRuns, totalResults,
                passed, failed, blocked, skipped, pending, passRate,
                runSummaries, sessionsSummary(planId)
        );
    }

    private TestPlanSummaryResponse.SessionsSummary sessionsSummary(UUID planId) {
        Instant now = clock.instant();
        List<TestPlanSummaryResponse.SessionItem> items = sessionRepository.findByTestPlanIdOrderByCreatedAtAsc(planId)
                .stream()
                .map(s -> new TestPlanSummaryResponse.SessionItem(s.getId(), s.getKey(), s.getCharter(), s.getStatus(),
                        s.getTester() != null ? s.getTester().getDisplayName() : null,
                        s.getStartedAt(), s.getEndedAt(), minutesSpent(s.getStartedAt(), s.getEndedAt(), now)))
                .toList();
        int completed = (int) items.stream().filter(i -> i.status() == TestRunStatus.COMPLETED).count();
        long totalMinutes = items.stream().mapToLong(TestPlanSummaryResponse.SessionItem::minutes).sum();
        return new TestPlanSummaryResponse.SessionsSummary(items.size(), completed, totalMinutes, items);
    }

    private static long minutesSpent(Instant startedAt, Instant endedAt, Instant now) {
        if (startedAt == null) {
            return 0;
        }
        return Duration.between(startedAt, endedAt != null ? endedAt : now).toMinutes();
    }

    @Transactional
    public TestPlanResponse create(UUID projectId, CreateTestPlanRequest request, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        TestPlan plan = testPlanMapper.toEntity(request);
        plan.setProject(project);
        plan.setStatus(TestPlanStatus.OPEN);

        if (request.assigneeId() != null) {
            plan.setAssignee(requireProjectMember(projectId, request.assigneeId()));
        }

        plan = testPlanRepository.save(plan);
        auditService.log(projectId, userId, AuditAction.CREATED,
                AuditEntityType.TEST_PLAN, plan.getId(), plan.getName(), null);
        return testPlanMapper.toResponse(plan);
    }

    @Transactional
    public TestPlanResponse update(UUID projectId, UUID id, UpdateTestPlanRequest request, UUID userId) {
        TestPlan plan = testPlanRepository.findById(id)
                .filter(p -> p.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestPlan", id));

        plan.setName(request.name());
        plan.setDescription(request.description());
        plan.setTargetDate(request.targetDate());
        if (request.status() != null) {
            plan.setStatus(request.status());
        }
        // Null clears the assignee (the SPA sends the whole object); a supplied id that does not
        // name a member of this project now fails rather than silently clearing (PRD-027 §3.5).
        plan.setAssignee(request.assigneeId() == null
                ? null : requireProjectMember(projectId, request.assigneeId()));

        plan = testPlanRepository.save(plan);
        auditService.log(projectId, userId, AuditAction.UPDATED,
                AuditEntityType.TEST_PLAN, plan.getId(), plan.getName(), null);
        return testPlanMapper.toResponse(plan);
    }

    public List<TestPlanResponse> findByAssignee(UUID assigneeId) {
        return testPlanRepository.findByAssigneeIdOrderByCreatedAtDesc(assigneeId).stream()
                .map(testPlanMapper::toResponse)
                .toList();
    }

    @Transactional
    public void delete(UUID projectId, UUID id, UUID userId) {
        TestPlan plan = testPlanRepository.findById(id)
                .filter(p -> p.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestPlan", id));
        auditService.log(projectId, userId, AuditAction.DELETED,
                AuditEntityType.TEST_PLAN, plan.getId(), plan.getName(), null);
        testPlanRepository.delete(plan);
    }
}
