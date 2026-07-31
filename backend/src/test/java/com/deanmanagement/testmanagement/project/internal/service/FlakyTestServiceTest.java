package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.analytics.FlakyTestResponse;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flakiness scoring (PRD-016). The score answers "does this test keep changing its mind", which is
 * a different question from "does it fail" — several of these tests exist to keep those apart.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class FlakyTestServiceTest {

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

    private Project project;
    private final AtomicInteger sequence = new AtomicInteger();

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setName("Flaky " + UUID.randomUUID());
        project.setKey("FLK" + sequence.incrementAndGet());
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

    /**
     * Records a sequence of outcomes, oldest first, one run each an hour apart.
     * {@code P} passed, {@code F} failed, {@code B} blocked, {@code S} skipped.
     */
    private void record(TestCase testCase, String outcomes, TestRunStatus runStatus) {
        for (int i = 0; i < outcomes.length(); i++) {
            TestRun run = new TestRun();
            run.setProject(project);
            run.setKey(testCase.getKey() + "-R" + sequence.incrementAndGet());
            run.setName("run");
            run.setStatus(runStatus);
            run.setEndTime(BASE.plus(i, ChronoUnit.HOURS));
            run = testRunRepository.save(run);

            TestResult result = new TestResult();
            result.setTestRun(run);
            result.setTestCase(testCase);
            result.setStatus(switch (outcomes.charAt(i)) {
                case 'P' -> TestResultStatus.PASSED;
                case 'F' -> TestResultStatus.FAILED;
                case 'B' -> TestResultStatus.BLOCKED;
                case 'S' -> TestResultStatus.SKIPPED;
                default -> throw new IllegalArgumentException("Unknown outcome " + outcomes.charAt(i));
            });
            testResultRepository.save(result);
        }
    }

    private void record(TestCase testCase, String outcomes) {
        record(testCase, outcomes, TestRunStatus.COMPLETED);
    }

    private FlakyTestResponse scoreOf(TestCase testCase) {
        return service.analyse(project.getId()).stream()
                .filter(r -> r.testCaseId().equals(testCase.getId()))
                .findFirst()
                .orElseThrow();
    }

    // ---- the score means "changes its mind" -------------------------------

    @Test
    void alternatingPassFailScoresOne() {
        TestCase testCase = saveCase("FLAKY-1");
        record(testCase, "PFPFPFPF");

        FlakyTestResponse result = scoreOf(testCase);

        assertThat(result.flakyScore()).isEqualTo(1.0);
        assertThat(result.runsConsidered()).isEqualTo(8);
        assertThat(result.flaky()).isTrue();
    }

    @Test
    void alwaysPassingScoresZero() {
        TestCase testCase = saveCase("STABLE-1");
        record(testCase, "PPPPPPPP");

        assertThat(scoreOf(testCase).flakyScore()).isZero();
        assertThat(scoreOf(testCase).flaky()).isFalse();
    }

    @Test
    void alwaysFailingIsBrokenNotFlaky() {
        TestCase testCase = saveCase("BROKEN-1");
        record(testCase, "FFFFFFFF");

        FlakyTestResponse result = scoreOf(testCase);

        // The distinction that matters: a consistently failing test needs fixing, not quarantining.
        assertThat(result.flakyScore()).isZero();
        assertThat(result.flaky()).isFalse();
        assertThat(result.failRate()).isEqualTo(1.0);
    }

    @Test
    void oneRegressionIsNotFlakiness() {
        TestCase testCase = saveCase("REGRESSED-1");
        record(testCase, "PPPPPFFFFF");

        FlakyTestResponse result = scoreOf(testCase);

        // A single P→F step in nine pairs: it broke and stayed broken.
        assertThat(result.flakyScore()).isCloseTo(0.11, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.flaky()).isFalse();
        assertThat(result.failRate()).isEqualTo(0.5);
    }

    // ---- window and min-runs ----------------------------------------------

    @Test
    void onlyTheMostRecentWindowCounts() {
        TestCase testCase = saveCase("FIXED-1");
        // Twenty flaky results long ago, then twenty clean ones. With a window of 20 the old
        // behaviour has aged out entirely.
        record(testCase, "PFPFPFPFPFPFPFPFPFPF" + "PPPPPPPPPPPPPPPPPPPP");

        FlakyTestResponse result = scoreOf(testCase);

        assertThat(result.runsConsidered()).isEqualTo(20);
        assertThat(result.flakyScore()).isZero();
        assertThat(result.flaky()).isFalse();
    }

    @Test
    void tooFewRunsIsNeverFlakyEvenAtScoreOne() {
        TestCase testCase = saveCase("SPARSE-1");
        record(testCase, "PF");

        FlakyTestResponse result = scoreOf(testCase);

        // One flip out of one pair is a perfect score on almost no evidence.
        assertThat(result.flakyScore()).isEqualTo(1.0);
        assertThat(result.runsConsidered()).isEqualTo(2);
        assertThat(result.flaky()).isFalse();
    }

    @Test
    void aSingleResultScoresZeroRatherThanDividingByZero() {
        TestCase testCase = saveCase("SINGLE-1");
        record(testCase, "P");

        FlakyTestResponse result = scoreOf(testCase);

        assertThat(result.flakyScore()).isZero();
        assertThat(result.runsConsidered()).isEqualTo(1);
    }

    // ---- what counts as a result ------------------------------------------

    @Test
    void blockedAndSkippedResultsAreIgnored() {
        TestCase testCase = saveCase("NOISY-1");
        record(testCase, "PBSPBSPBSP");

        FlakyTestResponse result = scoreOf(testCase);

        // Only the four passes count; blocked and skipped say something about the environment.
        assertThat(result.runsConsidered()).isEqualTo(4);
        assertThat(result.flakyScore()).isZero();
    }

    @Test
    void abortedRunsAreExcluded() {
        TestCase testCase = saveCase("ABORTED-1");
        record(testCase, "PPPPPP");
        record(testCase, "FFFFFF", TestRunStatus.ABORTED);

        FlakyTestResponse result = scoreOf(testCase);

        // A run someone cut short must not read as the test starting to fail.
        assertThat(result.runsConsidered()).isEqualTo(6);
        assertThat(result.flakyScore()).isZero();
    }

    @Test
    void inProgressRunsStillCount() {
        TestCase testCase = saveCase("LIVE-1");
        record(testCase, "PFPFPF", TestRunStatus.IN_PROGRESS);

        // Only ABORTED is excluded; a result recorded in a live run is a real observation.
        assertThat(scoreOf(testCase).runsConsidered()).isEqualTo(6);
    }

    // ---- ordering and listing ---------------------------------------------

    @Test
    void findFlakyReturnsOnlyQualifyingCasesMostFlakyFirst() {
        TestCase flaky = saveCase("A-FLAKY");
        TestCase stable = saveCase("B-STABLE");
        TestCase mild = saveCase("C-MILD");
        record(flaky, "PFPFPFPF");
        record(stable, "PPPPPPPP");
        record(mild, "PPPFPPPP");

        List<FlakyTestResponse> flakyOnly = service.findFlaky(project.getId(), 10);

        assertThat(flakyOnly).extracting(FlakyTestResponse::testCaseKey).containsExactly("A-FLAKY");
    }

    @Test
    void limitIsRespected() {
        for (int i = 0; i < 4; i++) {
            record(saveCase("MANY-" + i), "PFPFPFPF");
        }

        assertThat(service.findFlaky(project.getId(), 2)).hasSize(2);
    }

    @Test
    void casesWithNoTerminalResultsAreAbsentRatherThanScoredZero() {
        saveCase("NEVER-RUN");

        // "Insufficient data" is not the same claim as "stable", so the case simply is not listed.
        assertThat(service.analyse(project.getId())).isEmpty();
    }

    @Test
    void anotherProjectsResultsDoNotLeakIn() {
        TestCase mine = saveCase("MINE-1");
        record(mine, "PPPP");

        Project other = new Project();
        other.setName("Other");
        other.setKey("OTH" + sequence.incrementAndGet());
        other = projectRepository.save(other);

        TestCase theirs = new TestCase();
        theirs.setProject(other);
        theirs.setKey("THEIRS-1");
        theirs.setTitle("Theirs");
        theirs.setPriority(Priority.MEDIUM);
        theirs.setStatus(TestCaseStatus.ACTIVE);
        theirs = testCaseRepository.save(theirs);

        TestRun otherRun = new TestRun();
        otherRun.setProject(other);
        otherRun.setKey("OTH-R1");
        otherRun.setName("run");
        otherRun.setStatus(TestRunStatus.COMPLETED);
        otherRun.setEndTime(BASE);
        otherRun = testRunRepository.save(otherRun);

        TestResult otherResult = new TestResult();
        otherResult.setTestRun(otherRun);
        otherResult.setTestCase(theirs);
        otherResult.setStatus(TestResultStatus.FAILED);
        testResultRepository.save(otherResult);

        assertThat(service.analyse(project.getId()))
                .extracting(FlakyTestResponse::testCaseKey)
                .containsExactly("MINE-1");
    }

    // ---- auto-label --------------------------------------------------------

    @Test
    void labelSyncIsANoOpWhileAutoLabelIsOff() {
        TestCase testCase = saveCase("LABEL-1");
        record(testCase, "PFPFPFPF");

        // Default configuration: labels are user-owned, so nothing is touched.
        assertThat(service.syncLabels(project.getId(), null)).isZero();
        assertThat(testCaseRepository.findById(testCase.getId()).orElseThrow().getLabels())
                .doesNotContain("flaky");
    }
}
