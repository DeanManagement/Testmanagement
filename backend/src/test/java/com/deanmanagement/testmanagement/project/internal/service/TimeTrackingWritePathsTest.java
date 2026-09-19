package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.ci.CiResult;
import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateStepResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.BulkResultStatusRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.ExternalCreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.ExternalTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every path that writes a result stamps {@code executedAt} and carries a duration the same way
 * (PRD-036 §3.2), and a test case keeps an optional estimate.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class TimeTrackingWritePathsTest {

    @Autowired private TestCaseService testCaseService;
    @Autowired private TestRunService testRunService;
    @Autowired private ExternalTestRunService externalTestRunService;
    @Autowired private CiIngestionService ciIngestionService;
    @Autowired private TestCaseVersionService versionService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private EntityManager entityManager;

    private UUID projectId;
    private TestCaseResponse testCase;

    @BeforeEach
    void setUp() {
        Project project = new Project();
        project.setName("Time tracking");
        project.setKey("TTW");
        projectId = projectRepository.save(project).getId();
        testCase = testCaseService.create(projectId, new CreateTestCaseRequest("Pay", null, null, Priority.MEDIUM,
                TestCaseStatus.DRAFT, null, List.of(new TestStepRequest("Open", "Shown", null)), null, null, 15), null);
    }

    private TestRunResponse runWithOnePendingResult() {
        TestRunResponse run = testRunService.create(projectId,
                new CreateTestRunRequest("Run", null, Set.of(testCase.id()), null, null), null);
        entityManager.flush();
        entityManager.clear();
        return testRunService.findById(projectId, run.id());
    }

    private TestResultResponse onlyResultOf(UUID runId) {
        entityManager.flush();
        entityManager.clear();
        return testRunService.findById(projectId, runId).results().getFirst();
    }

    @Nested
    class ExecutedAt {

        @Test
        void aSeededResultIsPendingAndNotExecuted() {
            assertThat(runWithOnePendingResult().results().getFirst().executedAt()).isNull();
        }

        @Test
        void updatingAResultStampsIt() {
            TestRunResponse run = runWithOnePendingResult();

            testRunService.updateResult(projectId, run.id(), run.results().getFirst().id(),
                    new UpdateTestResultRequest(TestResultStatus.PASSED, null, null), null);

            assertThat(onlyResultOf(run.id()).executedAt()).isNotNull();
        }

        @Test
        void settingAResultBackToPendingClearsIt() {
            TestRunResponse run = runWithOnePendingResult();
            UUID resultId = run.results().getFirst().id();
            testRunService.updateResult(projectId, run.id(), resultId,
                    new UpdateTestResultRequest(TestResultStatus.PASSED, null, null), null);

            testRunService.updateResult(projectId, run.id(), resultId,
                    new UpdateTestResultRequest(TestResultStatus.PENDING, null, null), null);

            assertThat(onlyResultOf(run.id()).executedAt()).isNull();
        }

        @Test
        void aBulkStatusChangeStampsIt() {
            TestRunResponse run = runWithOnePendingResult();

            testRunService.bulkUpdateResultStatus(projectId, run.id(),
                    new BulkResultStatusRequest(Set.of(run.results().getFirst().id()), TestResultStatus.SKIPPED, false),
                    null);

            assertThat(onlyResultOf(run.id()).executedAt()).isNotNull();
        }

        @Test
        void aStepResultThatMovesTheParentOffPendingStampsIt() {
            TestRunResponse run = runWithOnePendingResult();
            TestResultResponse result = run.results().getFirst();

            testRunService.updateStepResult(projectId, run.id(), result.id(), result.stepResults().getFirst().id(),
                    new UpdateStepResultRequest(TestResultStatus.FAILED, "Blank page"), null);

            assertThat(onlyResultOf(run.id()).executedAt()).isNotNull();
        }

        @Test
        void anAdHocResultIsStampedWithItsDuration() {
            TestRunResponse run = testRunService.create(projectId,
                    new CreateTestRunRequest("Run", null, null, null, null), null);

            TestResultResponse added = testRunService.addResult(projectId, run.id(),
                    new CreateTestResultRequest(testCase.id(), TestResultStatus.PASSED, null, null, 61_000L), null);

            assertThat(added.executedAt()).isNotNull();
            assertThat(added.durationMs()).isEqualTo(61_000L);
        }

        @Test
        void theExternalApiStampsResultsAndKeepsTheirDuration() {
            TestRunResponse run = externalTestRunService.createExternalRun(projectId.toString(),
                    new ExternalCreateTestRunRequest("CI", null, List.of(new ExternalTestResultRequest(
                            testCase.key(), TestResultStatus.PASSED, null, null, null, 420L))), null, null);

            TestResultResponse result = onlyResultOf(run.id());
            assertThat(result.executedAt()).isNotNull();
            assertThat(result.durationMs()).isEqualTo(420L);
        }

        @Test
        void ciIngestionStampsResultsAndKeepsTheirDuration() {
            TestRunResponse run = ciIngestionService.ingest(projectId.toString(), "Nightly", null, null,
                    List.of(new CiResult("suite", "Pay", TestResultStatus.FAILED, "boom", List.of(), 1234L)), null, null);

            TestResultResponse result = onlyResultOf(run.id());
            assertThat(result.executedAt()).isNotNull();
            assertThat(result.durationMs()).isEqualTo(1234L);
        }
    }

    @Nested
    class Duration {

        @Test
        void anUpdateWithoutADurationLeavesTheRecordedOneAlone() {
            TestRunResponse run = runWithOnePendingResult();
            UUID resultId = run.results().getFirst().id();
            testRunService.updateResult(projectId, run.id(), resultId,
                    new UpdateTestResultRequest(TestResultStatus.PASSED, null, null, 300_000L), null);

            testRunService.updateResult(projectId, run.id(), resultId,
                    new UpdateTestResultRequest(TestResultStatus.FAILED, "Wrong total", null), null);

            assertThat(onlyResultOf(run.id()).durationMs()).isEqualTo(300_000L);
        }

        @Test
        void aDurationCanBeCorrected() {
            TestRunResponse run = runWithOnePendingResult();
            UUID resultId = run.results().getFirst().id();
            testRunService.updateResult(projectId, run.id(), resultId,
                    new UpdateTestResultRequest(TestResultStatus.PASSED, null, null, 3_600_000L), null);

            testRunService.updateResult(projectId, run.id(), resultId,
                    new UpdateTestResultRequest(TestResultStatus.PASSED, null, null, 600_000L), null);

            assertThat(onlyResultOf(run.id()).durationMs()).isEqualTo(600_000L);
        }
    }

    @Nested
    class Estimate {

        private static UpdateTestCaseRequest estimate(Integer minutes) {
            return new UpdateTestCaseRequest(null, null, null, null, null, null, null, null, minutes);
        }

        @Test
        void isStoredOnCreate() {
            assertThat(testCaseService.findById(projectId, testCase.id()).estimateMinutes()).isEqualTo(15);
        }

        @Test
        void isLeftAloneWhenAnUpdateDoesNotMentionIt() {
            testCaseService.update(projectId, testCase.id(), new UpdateTestCaseRequest("Renamed", null, null, null,
                    null, null, null), null);

            assertThat(testCaseService.findById(projectId, testCase.id()).estimateMinutes()).isEqualTo(15);
        }

        @Test
        void zeroClearsIt() {
            testCaseService.update(projectId, testCase.id(), estimate(0), null);

            assertThat(testCaseService.findById(projectId, testCase.id()).estimateMinutes()).isNull();
        }

        @Test
        void theVersionSnapshotKeepsTheEstimateFromBeforeTheEdit() {
            testCaseService.update(projectId, testCase.id(), estimate(40), null);
            entityManager.flush();
            entityManager.clear();

            assertThat(versionService.get(projectId, testCase.id(), 1).estimateMinutes()).isEqualTo(15);
            assertThat(versionService.get(projectId, testCase.id(), 2).estimateMinutes()).isEqualTo(40);
        }
    }
}
