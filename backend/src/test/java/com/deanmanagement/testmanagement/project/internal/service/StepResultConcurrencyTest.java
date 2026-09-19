package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.UpdateStepResultRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.StepResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.StepResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TES-BUG-17: "Mark every step Passed" sends one update per step at once. Each update re-derives the
 * result's status from its sibling steps; without serialising them, every request saw the others
 * still Pending and the result stayed Pending although all steps were Passed. Not
 * {@code @Transactional}: the updates must commit concurrently, as they do in production.
 */
@SpringBootTest
@ActiveProfiles("dev")
class StepResultConcurrencyTest {

    private static final int STEPS = 4;

    @Autowired
    private TestRunService testRunService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestCaseRepository testCaseRepository;
    @Autowired
    private TestRunRepository testRunRepository;
    @Autowired
    private TestResultRepository testResultRepository;
    @Autowired
    private StepResultRepository stepResultRepository;

    private Project project;
    private UUID runId;
    private UUID resultId;
    private final List<UUID> stepIds = new ArrayList<>();

    @BeforeEach
    void aResultWithFourPendingSteps() {
        project = new Project();
        project.setName("Concurrency");
        project.setKey("CC" + Integer.toHexString(new java.util.Random().nextInt(0xFFFF)).toUpperCase());
        project = projectRepository.save(project);
        TestCase testCase = new TestCase();
        testCase.setProject(project);
        testCase.setKey(project.getKey() + "-1");
        testCase.setTitle("Checkout");
        testCase.setPriority(Priority.HIGH);
        testCase.setStatus(TestCaseStatus.ACTIVE);
        testCase = testCaseRepository.save(testCase);
        TestRun run = new TestRun();
        run.setProject(project);
        run.setKey(project.getKey() + "-Run-1");
        run.setName("Run");
        run.setStatus(TestRunStatus.IN_PROGRESS);
        run = testRunRepository.save(run);
        runId = run.getId();
        TestResult result = new TestResult();
        result.setTestRun(run);
        result.setTestCase(testCase);
        result.setStatus(TestResultStatus.PENDING, null);
        result = testResultRepository.save(result);
        resultId = result.getId();
        for (int i = 0; i < STEPS; i++) {
            StepResult step = new StepResult();
            step.setTestResult(result);
            step.setPosition(i);
            step.setStatus(TestResultStatus.PENDING);
            stepIds.add(stepResultRepository.save(step).getId());
        }
    }

    @AfterEach
    void removeTheProject() {
        projectService.delete(project.getId(), null);
    }

    @RepeatedTest(5)
    void passingEveryStepAtOnceLeavesTheResultPassed() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(STEPS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> updates = new ArrayList<>();
        for (UUID stepId : stepIds) {
            updates.add(pool.submit(() -> {
                start.await();
                return testRunService.updateStepResult(project.getId(), runId, resultId, stepId,
                        new UpdateStepResultRequest(TestResultStatus.PASSED, null), null);
            }));
        }
        start.countDown();
        for (Future<?> update : updates) {
            update.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(testResultRepository.findById(resultId).orElseThrow().getStatus())
                .isEqualTo(TestResultStatus.PASSED);
    }
}
