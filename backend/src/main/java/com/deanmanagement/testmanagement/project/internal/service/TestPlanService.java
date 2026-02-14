package com.deanmanagement.testmanagement.project.internal.service;

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
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
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
                runSummaries
        );
    }

    @Transactional
    public TestPlanResponse create(UUID projectId, CreateTestPlanRequest request, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        TestPlan plan = testPlanMapper.toEntity(request);
        plan.setProject(project);
        plan.setStatus(TestPlanStatus.OPEN);

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

        plan = testPlanRepository.save(plan);
        auditService.log(projectId, userId, AuditAction.UPDATED,
                AuditEntityType.TEST_PLAN, plan.getId(), plan.getName(), null);
        return testPlanMapper.toResponse(plan);
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
