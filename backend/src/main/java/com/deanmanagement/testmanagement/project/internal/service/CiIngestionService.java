package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.ci.CiResult;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.TestRunMapper;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.StepResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseParameterSet;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlan;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
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
 * run, auto-creating any missing test cases. A result carrying a case key ({@code @tm:} tag, PRD-040)
 * goes to that case; otherwise cases are matched by title. Auto-created cases are labelled {@code ci-imported} and
 * de-duplicated within a single submission.
 */
@Service
@RequiredArgsConstructor
public class CiIngestionService {

    public static final String CI_LABEL = "ci-imported";
    private static final int MAX_TITLE_LENGTH = 255;

    private final ExternalRefResolver refResolver;
    private final TestCaseRepository testCaseRepository;
    private final TestRunRepository testRunRepository;
    private final TestPlanRepository testPlanRepository;
    private final TestRunMapper testRunMapper;
    private final ProjectSequenceService projectSequenceService;
    private final PipelineRunLinker pipelineRunLinker;
    private final RunEventPublisher runEventPublisher;
    private final ProjectEnvironmentService environmentService;
    private final ParameterSetService parameterSetService;

    /**
     * @param projectRef the project key or UUID from the URL.
     * @param pipelineRunId optional PRD-024 correlation: the {@code TM_PIPELINE_RUN_ID} a
     *        triggered workflow passes back, linking the created run to its pipeline run.
     */
    @Transactional
    public TestRunResponse ingest(String projectRef, String runName, String environment,
                                  UUID testPlanId, List<CiResult> results, UUID pipelineRunId) {
        Project project = refResolver.resolveProject(projectRef);
        var pipelineRun = pipelineRunLinker.resolve(pipelineRunId, project.getId());

        Instant now = Instant.now();
        TestRun run = new TestRun();
        // An unnamed run from a triggered pipeline is named after the workflow that produced it.
        run.setName(runName != null && !runName.isBlank() ? runName
                : pipelineRun != null ? pipelineRun.getWorkflowName() : "CI import");
        run.assignEnvironment(environmentService.resolve(project.getId(), null,
                pipelineRunLinker.environmentFor(pipelineRun, environment)));
        run.setProject(project);
        run.setStatus(TestRunStatus.COMPLETED);
        run.setStartTime(now);
        run.setEndTime(now);
        if (testPlanId != null) {
            // Scoped like the pipelineRunId on the line above, which always was (PRD-027 §3.5).
            // testPlanId arrives as a free @RequestParam and requireTester() only authorizes the
            // project in the path, so an unscoped lookup let a CI key file its run against another
            // project's plan — where the results then count toward that plan's pass rate.
            TestPlan plan = testPlanRepository.findByIdAndProjectId(testPlanId, project.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("TestPlan", testPlanId));
            run.setTestPlan(plan);
        }
        run.setKey(project.getKey() + "-Run-" + projectSequenceService.nextTestRunNumber(project.getId()));

        Map<String, TestCase> casesByTitle = new HashMap<>();
        Map<String, TestCase> casesByKey = new HashMap<>();
        Map<UUID, Integer> rowsPerKeyedCase = new HashMap<>();
        for (CiResult ciResult : results) {
            TestCase keyed = ciResult.testCaseKey() == null ? null
                    : casesByKey.computeIfAbsent(ciResult.testCaseKey(), key ->
                            testCaseRepository.findByKeyAndProjectId(key, project.getId()).orElse(null));
            TestCase testCase = keyed != null ? keyed : resolveOrCreate(project, ciResult, casesByTitle);

            TestResult result = new TestResult();
            result.setTestRun(run);
            result.setTestCase(testCase);
            result.setExecutedVersion(testCase.getCurrentVersion());
            result.setStatus(ciResult.status());
            result.setComment(ciResult.message());
            result.setDurationMs(ciResult.durationMs());
            if (ciResult.testCaseKey() != null && keyed == null) {
                // Visible rather than silently filed under a lookalike: a typo, or another project's file.
                prependComment(result, "Unknown test case key tm:" + ciResult.testCaseKey());
            }
            if (keyed != null) {
                assignParameterSet(result, keyed, rowsPerKeyedCase.merge(keyed.getId(), 1, Integer::sum) - 1);
            }
            run.getResults().add(result);

            // Matched by index against the steps as executed, shared blocks expanded (PRD-030).
            List<TestStep> steps = StepExpansion.expandedSteps(testCase.getSteps());
            if (!ciResult.steps().isEmpty() && steps != null && !steps.isEmpty()) {
                int count = Math.min(ciResult.steps().size(), steps.size());
                for (int i = 0; i < count; i++) {
                    StepResult stepResult = new StepResult();
                    stepResult.setTestResult(result);
                    stepResult.setTestStep(steps.get(i));
                    stepResult.setPosition(i);
                    stepResult.setStatus(ciResult.steps().get(i).status());
                    result.getStepResults().add(stepResult);
                }
            }
        }

        run = testRunRepository.save(run);
        pipelineRunLinker.attach(pipelineRun, run);
        // Run-level only: one TEST_FAILED per ingested result would flood chat channels (PRD-031).
        runEventPublisher.publishFinished(run);
        return testRunMapper.toResponse(run);
    }

    /**
     * A Scenario Outline reports one result per example row, in order, so the n-th result for a
     * keyed case is its n-th parameter set (PRD-040 §3.5). A row beyond the sets gets none, and says so.
     */
    private void assignParameterSet(TestResult result, TestCase testCase, int row) {
        List<TestCaseParameterSet> sets = parameterSetService.setsFor(testCase.getId());
        if (sets.isEmpty()) {
            return;
        }
        if (row < sets.size()) {
            result.setParameterSetName(sets.get(row).getName());
            result.setParameterValuesJson(sets.get(row).getValuesJson());
        } else {
            prependComment(result, "Example row " + (row + 1) + " has no parameter set: "
                    + testCase.getKey() + " has " + sets.size());
        }
    }

    private static void prependComment(TestResult result, String note) {
        result.setComment(result.getComment() == null ? note : note + "\n" + result.getComment());
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
        // An automated test's title isn't reviewed wording, so with review on it waits for review (PRD-033).
        testCase.setStatus(project.isReviewRequired() ? TestCaseStatus.IN_REVIEW : TestCaseStatus.ACTIVE);
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
