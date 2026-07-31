package com.deanmanagement.testmanagement.project.internal.service;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The auto-label path with the flag switched on (PRD-016 §3.3). Separate class because the setting
 * is deployment-level, and the default-off behaviour is asserted in {@link FlakyTestServiceTest}.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "app.flaky.auto-label=true")
@Transactional
class FlakyAutoLabelTest {

    private static final Instant BASE = Instant.parse("2026-01-01T09:00:00Z");

    @Autowired
    private FlakyTestService service;
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
        project.setName("Labelled");
        project.setKey("LBL" + sequence.incrementAndGet());
        project = projectRepository.save(project);
    }

    private TestCase saveCase(String key) {
        TestCase testCase = new TestCase();
        testCase.setProject(project);
        testCase.setKey(key);
        testCase.setTitle("Case " + key);
        testCase.setPriority(Priority.MEDIUM);
        testCase.setStatus(TestCaseStatus.ACTIVE);
        return testCaseRepository.save(testCase);
    }

    private void record(TestCase testCase, String outcomes) {
        for (int i = 0; i < outcomes.length(); i++) {
            TestRun run = new TestRun();
            run.setProject(project);
            run.setKey(testCase.getKey() + "-R" + sequence.incrementAndGet());
            run.setName("run");
            run.setStatus(TestRunStatus.COMPLETED);
            run.setEndTime(BASE.plus(i, ChronoUnit.HOURS));
            run = testRunRepository.save(run);

            TestResult result = new TestResult();
            result.setTestRun(run);
            result.setTestCase(testCase);
            result.setStatus(outcomes.charAt(i) == 'P' ? TestResultStatus.PASSED : TestResultStatus.FAILED);
            testResultRepository.save(result);
        }
    }

    private java.util.Set<String> labelsOf(UUID testCaseId) {
        return testCaseRepository.findById(testCaseId).orElseThrow().getLabels();
    }

    @Test
    void addsTheLabelToAFlakyCase() {
        TestCase testCase = saveCase("AL-1");
        record(testCase, "PFPFPFPF");

        assertThat(service.syncLabels(project.getId(), null)).isEqualTo(1);
        assertThat(labelsOf(testCase.getId())).contains("flaky");
    }

    @Test
    void leavesStableCasesAlone() {
        TestCase testCase = saveCase("AL-2");
        record(testCase, "PPPPPPPP");

        assertThat(service.syncLabels(project.getId(), null)).isZero();
        assertThat(labelsOf(testCase.getId())).doesNotContain("flaky");
    }

    @Test
    void removesTheLabelWhenACaseSettlesDown() {
        TestCase testCase = saveCase("AL-3");
        testCase.getLabels().add("flaky");
        testCaseRepository.save(testCase);
        record(testCase, "PPPPPPPP");

        assertThat(service.syncLabels(project.getId(), null)).isEqualTo(1);
        assertThat(labelsOf(testCase.getId())).doesNotContain("flaky");
    }

    @Test
    void isIdempotentSoRepeatedRunsDoNotFloodTheAuditLog() {
        TestCase testCase = saveCase("AL-4");
        record(testCase, "PFPFPFPF");

        assertThat(service.syncLabels(project.getId(), null)).isEqualTo(1);
        // Nothing changed the second time, so nothing is written.
        assertThat(service.syncLabels(project.getId(), null)).isZero();
    }

    @Test
    void doesNotDisturbOtherLabels() {
        TestCase testCase = saveCase("AL-5");
        testCase.getLabels().add("smoke");
        testCaseRepository.save(testCase);
        record(testCase, "PFPFPFPF");

        service.syncLabels(project.getId(), null);

        assertThat(labelsOf(testCase.getId())).containsExactlyInAnyOrder("smoke", "flaky");
    }

    @Test
    void doesNotLabelACaseWithTooLittleHistory() {
        TestCase testCase = saveCase("AL-6");
        record(testCase, "PF");

        // Score is 1.0 but only two results — below min-runs, so it must not be labelled.
        assertThat(service.syncLabels(project.getId(), null)).isZero();
        assertThat(labelsOf(testCase.getId())).doesNotContain("flaky");
    }
}
