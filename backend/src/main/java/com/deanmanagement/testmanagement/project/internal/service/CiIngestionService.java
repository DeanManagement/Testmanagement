package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.ci.CiResult;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.TestRunMapper;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.StepResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlan;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns normalized {@link CiResult}s (parsed from JUnit XML / Cucumber JSON) into a completed test
 * run, auto-creating any missing test cases. Auto-created cases are labelled {@code ci-imported} and
 * de-duplicated within a single submission.
 */
@Service
@RequiredArgsConstructor
public class CiIngestionService {

    public static final String CI_LABEL = "ci-imported";
    private static final int MAX_TITLE_LENGTH = 255;

    private final ProjectRepository projectRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestRunRepository testRunRepository;
    private final TestPlanRepository testPlanRepository;
    private final TestRunMapper testRunMapper;
    private final ProjectSequenceService projectSequenceService;

    @Transactional
    public TestRunResponse ingest(String projectKey, String runName, String environment,
                                  UUID testPlanId, List<CiResult> results) {
        Project project = projectRepository.findByKey(projectKey)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectKey));

        Instant now = Instant.now();
        TestRun run = new TestRun();
        run.setName(runName != null && !runName.isBlank() ? runName : "CI import");
        run.setEnvironment(environment);
        run.setProject(project);
        run.setStatus(TestRunStatus.COMPLETED);
        run.setStartTime(now);
        run.setEndTime(now);
        if (testPlanId != null) {
            TestPlan plan = testPlanRepository.findById(testPlanId)
                    .orElseThrow(() -> new ResourceNotFoundException("TestPlan", testPlanId));
            run.setTestPlan(plan);
        }
        run.setKey(project.getKey() + "-Run-" + projectSequenceService.nextTestRunNumber(project.getId()));

        Map<String, TestCase> casesByTitle = new HashMap<>();
        for (CiResult ciResult : results) {
            TestCase testCase = resolveOrCreate(project, ciResult, casesByTitle);

            TestResult result = new TestResult();
            result.setTestRun(run);
            result.setTestCase(testCase);
            result.setExecutedVersion(testCase.getCurrentVersion());
            result.setStatus(ciResult.status());
            result.setComment(ciResult.message());
            run.getResults().add(result);

            List<TestStep> steps = testCase.getSteps();
            if (!ciResult.steps().isEmpty() && steps != null && !steps.isEmpty()) {
                int count = Math.min(ciResult.steps().size(), steps.size());
                for (int i = 0; i < count; i++) {
                    StepResult stepResult = new StepResult();
                    stepResult.setTestResult(result);
                    stepResult.setTestStep(steps.get(i));
                    stepResult.setStatus(ciResult.steps().get(i).status());
                    result.getStepResults().add(stepResult);
                }
            }
        }

        run = testRunRepository.save(run);
        return testRunMapper.toResponse(run);
    }

    private TestCase resolveOrCreate(Project project, CiResult ciResult, Map<String, TestCase> cache) {
        String title = truncate(ciResult.title());
        TestCase cached = cache.get(title);
        if (cached != null) {
            return cached;
        }
        TestCase existing = testCaseRepository.findFirstByProjectIdAndTitle(project.getId(), title).orElse(null);
        if (existing != null) {
            cache.put(title, existing);
            return existing;
        }

        TestCase testCase = new TestCase();
        testCase.setProject(project);
        testCase.setTitle(title);
        testCase.setStatus(TestCaseStatus.ACTIVE);
        testCase.setPriority(Priority.MEDIUM);
        testCase.setLabels(new HashSet<>(List.of(CI_LABEL)));

        List<TestStep> steps = new ArrayList<>();
        for (int i = 0; i < ciResult.steps().size(); i++) {
            TestStep step = new TestStep();
            step.setAction(ciResult.steps().get(i).name());
            step.setExpectedResult("");
            step.setOrderIndex(i);
            step.setTestCase(testCase);
            steps.add(step);
        }
        testCase.setSteps(steps);
        testCase.setKey(project.getKey() + "-" + projectSequenceService.nextTestCaseNumber(project.getId()));

        TestCase saved = testCaseRepository.save(testCase);
        cache.put(title, saved);
        return saved;
    }

    private String truncate(String title) {
        if (title == null) {
            return "Untitled";
        }
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
    }
}
