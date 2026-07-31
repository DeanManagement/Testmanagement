package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The audit property versioning exists for (PRD-011): a result records which wording it executed,
 * and later edits to the test case cannot rewrite that record.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ExecutedVersionStampingTest {

    @Autowired
    private TestCaseService testCaseService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestCaseRepository testCaseRepository;
    @Autowired
    private TestRunRepository testRunRepository;
    @Autowired
    private TestResultRepository testResultRepository;

    private final AtomicInteger sequence = new AtomicInteger();
    private Project project;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setName("Stamped");
        project.setKey("STM" + sequence.incrementAndGet());
        project = projectRepository.save(project);
    }

    private UUID createCase(String title) {
        return testCaseService.create(project.getId(), new CreateTestCaseRequest(
                title, "d", "p", Priority.MEDIUM, TestCaseStatus.ACTIVE,
                new HashSet<>(Set.of("smoke")),
                List.of(new TestStepRequest("do a thing", "it works", null)), null), null).id();
    }

    private void edit(UUID id, String title) {
        testCaseService.update(project.getId(), id, new UpdateTestCaseRequest(
                title, "d", "p", Priority.MEDIUM, TestCaseStatus.ACTIVE,
                new HashSet<>(Set.of("smoke")),
                List.of(new TestStepRequest("do a different thing", "it works", null))), null);
    }

    /** Records a result the way the run services do, stamping the case's current version. */
    private UUID recordResult(TestCase testCase) {
        TestRun run = new TestRun();
        run.setProject(project);
        run.setKey("STM-R" + sequence.incrementAndGet());
        run.setName("run");
        run.setStatus(TestRunStatus.COMPLETED);
        run = testRunRepository.save(run);

        TestResult result = new TestResult();
        result.setTestRun(run);
        result.setTestCase(testCase);
        result.setExecutedVersion(testCase.getCurrentVersion());
        result.setStatus(TestResultStatus.PASSED);
        return testResultRepository.save(result).getId();
    }

    private Integer executedVersionOf(UUID resultId) {
        return testResultRepository.findById(resultId).orElseThrow().getExecutedVersion();
    }

    @Test
    void aResultRecordsTheVersionItExecuted() {
        UUID caseId = createCase("Original");
        UUID resultId = recordResult(testCaseRepository.findById(caseId).orElseThrow());

        assertThat(executedVersionOf(resultId)).isEqualTo(1);
    }

    @Test
    void editingTheCaseAfterwardsDoesNotRewriteHistory() {
        UUID caseId = createCase("Original");
        UUID resultId = recordResult(testCaseRepository.findById(caseId).orElseThrow());

        edit(caseId, "Revised");
        edit(caseId, "Revised again");

        // The whole point of the feature: the record of what ran must be immutable.
        assertThat(executedVersionOf(resultId)).isEqualTo(1);
        assertThat(testCaseRepository.findById(caseId).orElseThrow().getCurrentVersion()).isEqualTo(3);
    }

    @Test
    void resultsRecordedAfterAnEditCarryTheNewVersion() {
        UUID caseId = createCase("Original");
        UUID first = recordResult(testCaseRepository.findById(caseId).orElseThrow());

        edit(caseId, "Revised");
        UUID second = recordResult(testCaseRepository.findById(caseId).orElseThrow());

        assertThat(executedVersionOf(first)).isEqualTo(1);
        assertThat(executedVersionOf(second)).isEqualTo(2);
    }

    @Test
    void aResultPredatingVersioningStaysUnrecorded() {
        UUID caseId = createCase("Original");
        TestCase testCase = testCaseRepository.findById(caseId).orElseThrow();

        TestRun run = new TestRun();
        run.setProject(project);
        run.setKey("STM-OLD" + sequence.incrementAndGet());
        run.setName("legacy run");
        run.setStatus(TestRunStatus.COMPLETED);
        run = testRunRepository.save(run);

        TestResult legacy = new TestResult();
        legacy.setTestRun(run);
        legacy.setTestCase(testCase);
        legacy.setStatus(TestResultStatus.PASSED);
        // No executed version — the backfill deliberately leaves these null rather than claiming
        // they ran against v1, which was reconstructed from today's text.
        UUID legacyId = testResultRepository.save(legacy).getId();

        assertThat(executedVersionOf(legacyId)).isNull();
    }
}
