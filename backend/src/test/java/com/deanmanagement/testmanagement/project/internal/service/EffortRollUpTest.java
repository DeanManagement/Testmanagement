package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.effort.BurnDownResponse;
import com.deanmanagement.testmanagement.project.internal.dto.effort.EffortSummary;
import com.deanmanagement.testmanagement.project.internal.dto.io.ImportResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.CreateTestPlanRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Roll-ups, burn-down, median actual and import/export of PRD-036, against the real queries. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class EffortRollUpTest {

    @Autowired private TestCaseService testCaseService;
    @Autowired private TestRunService testRunService;
    @Autowired private TestPlanService testPlanService;
    @Autowired private TestCaseImportExportService importExportService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    private UUID projectId;
    private UUID planId;
    private UUID thirtyMinuteCase;
    private UUID unestimatedCase;

    @BeforeEach
    void setUp() {
        Project project = new Project();
        project.setName("Effort");
        project.setKey("EFF");
        projectId = projectRepository.save(project).getId();
        planId = testPlanService.create(projectId,
                new CreateTestPlanRequest("Release", null, LocalDate.now(ZoneOffset.UTC).plusDays(7), null), null).id();
        thirtyMinuteCase = newCase("Pay", 30);
        unestimatedCase = newCase("Browse", null);
    }

    private UUID newCase(String title, Integer estimateMinutes) {
        return testCaseService.create(projectId, new CreateTestCaseRequest(title, null, null, Priority.MEDIUM,
                TestCaseStatus.DRAFT, null, null, null, null, estimateMinutes), null).id();
    }

    private TestRunResponse planRun(String name, UUID... caseIds) {
        TestRunResponse run = testRunService.create(projectId,
                new CreateTestRunRequest(name, null, Set.of(caseIds), planId, null), null);
        return reload(run.id());
    }

    private TestRunResponse reload(UUID runId) {
        entityManager.flush();
        entityManager.clear();
        return testRunService.findById(projectId, runId);
    }

    private UUID resultFor(TestRunResponse run, UUID caseId) {
        return run.results().stream().filter(r -> r.testCaseId().equals(caseId)).findFirst().orElseThrow().id();
    }

    private void execute(TestRunResponse run, UUID caseId, Long durationMs) {
        testRunService.updateResult(projectId, run.id(), resultFor(run, caseId),
                new UpdateTestResultRequest(TestResultStatus.PASSED, null, null, durationMs));
    }

    @Nested
    class RollUps {

        @Test
        void theRunDetailAndTheRunReportAgree() {
            TestRunResponse run = planRun("Run", thirtyMinuteCase, unestimatedCase);
            execute(run, unestimatedCase, 120_000L);

            EffortSummary expected = new EffortSummary(30, 30, 2, 0);
            assertThat(reload(run.id()).effort()).isEqualTo(expected);
            assertThat(testRunService.getReport(projectId, run.id()).effort()).isEqualTo(expected);
        }

        @Test
        void thePlanSummaryLeavesOutAbortedRuns() {
            planRun("Kept", thirtyMinuteCase, unestimatedCase);
            TestRunResponse aborted = planRun("Aborted", thirtyMinuteCase);
            testRunService.update(projectId, aborted.id(),
                    new UpdateTestRunRequest(null, null, TestRunStatus.ABORTED, null, null), null);
            entityManager.flush();
            entityManager.clear();

            assertThat(testPlanService.getSummary(projectId, planId).effort()).isEqualTo(new EffortSummary(30, 30, 0, 1));
        }
    }

    @Nested
    class BurnDown {

        @Test
        void readsThePlansResultsThroughTheProjection() {
            TestRunResponse run = planRun("Run", thirtyMinuteCase, unestimatedCase);
            execute(run, thirtyMinuteCase, null);
            entityManager.flush();
            entityManager.clear();

            BurnDownResponse burnDown = testPlanService.getBurnDown(projectId, planId);

            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            assertThat(burnDown.hasEstimates()).isTrue();
            assertThat(burnDown.scopeStartsAt()).isEqualTo(today);
            assertThat(burnDown.days().getLast()).isEqualTo(new BurnDownResponse.Point(today, 0));
            assertThat(burnDown.idealLine().getLast().date()).isEqualTo(today.plusDays(7));
            assertThat(burnDown.historyAvailableFrom()).isNull();
        }

        @Test
        void leavesOutAbortedRuns() {
            TestRunResponse aborted = planRun("Aborted", thirtyMinuteCase);
            testRunService.update(projectId, aborted.id(),
                    new UpdateTestRunRequest(null, null, TestRunStatus.ABORTED, null, null), null);
            entityManager.flush();
            entityManager.clear();

            assertThat(testPlanService.getBurnDown(projectId, planId).scopeStartsAt()).isNull();
        }
    }

    @Nested
    class MedianActual {

        /** One ad-hoc result per duration, executed one day apart, oldest first. */
        private void executions(Long... durationsOldestFirst) {
            UUID runId = testRunService.create(projectId, new CreateTestRunRequest("History", null, null, null, null),
                    null).id();
            Instant oldest = Instant.now().minus(durationsOldestFirst.length, ChronoUnit.DAYS);
            for (int i = 0; i < durationsOldestFirst.length; i++) {
                UUID resultId = testRunService.addResult(projectId, runId, new CreateTestResultRequest(
                        thirtyMinuteCase, TestResultStatus.PASSED, null, null, durationsOldestFirst[i])).id();
                entityManager.flush();
                jdbc.update("UPDATE test_results SET executed_at = ? WHERE id = ?",
                        Timestamp.from(oldest.plus(i, ChronoUnit.DAYS)), resultId);
            }
            entityManager.clear();
        }

        @Test
        void isNullUntilAnExecutionWasMeasured() {
            executions((Long) null);

            assertThat(testCaseService.findById(projectId, thirtyMinuteCase).medianActualMs()).isNull();
        }

        @Test
        void usesTheLastFiveMeasuredExecutions() {
            executions(9_000_000L, 100L, 200L, null, 300L, 400L, 500L);

            assertThat(testCaseService.findById(projectId, thirtyMinuteCase).medianActualMs()).isEqualTo(300L);
        }

        @Test
        void averagesTheTwoMiddleValuesOfAnEvenCount() {
            executions(100L, 200L);

            assertThat(testCaseService.findById(projectId, thirtyMinuteCase).medianActualMs()).isEqualTo(150L);
        }
    }

    @Nested
    class ImportExport {

        private ImportResultResponse importCsv(String csv, boolean dryRun) {
            return importExportService.importData(projectId, "cases.csv", csv.getBytes(StandardCharsets.UTF_8),
                    dryRun, null);
        }

        @Test
        void csvExportCarriesTheEstimateAndABlankForNone() {
            String csv = new String(importExportService.exportCsv(projectId, false), StandardCharsets.UTF_8);

            assertThat(csv.lines().findFirst().orElseThrow()).endsWith("steps,estimateMinutes");
            assertThat(csv).contains("Pay,,,MEDIUM,DRAFT,,,30").contains("Browse,,,MEDIUM,DRAFT,,,\r\n");
        }

        @Test
        void csvImportReadsTheEstimate() {
            importCsv("title,estimateMinutes\nImported,45\nNo estimate,\n", false);
            entityManager.flush();
            entityManager.clear();

            String exported = new String(importExportService.exportCsv(projectId, false), StandardCharsets.UTF_8);
            assertThat(exported).contains("Imported,,,MEDIUM,DRAFT,,,45").contains("No estimate,,,MEDIUM,DRAFT,,,\r\n");
        }

        @Test
        void anInvalidEstimateFailsOnlyItsRowInADryRun() {
            ImportResultResponse result = importCsv("title,estimateMinutes\nGood,5\nZero,0\nHuge,1441\nWords,soon\n", true);

            assertThat(result.imported()).isEqualTo(1);
            assertThat(result.errors()).extracting(ImportResultResponse.ImportError::row).containsExactly(3, 4, 5);
            assertThat(result.errors().getFirst().message()).contains("estimateMinutes");
        }

        @Test
        void jsonRoundTripsTheEstimate() {
            byte[] json = importExportService.exportJson(projectId);
            UUID target = projectRepository.save(newProject("EFT")).getId();

            importExportService.importData(target, "cases.json", json, false, null);
            entityManager.flush();
            entityManager.clear();

            assertThat(new String(importExportService.exportCsv(target, false), StandardCharsets.UTF_8))
                    .contains("Pay,,,MEDIUM,DRAFT,,,30");
        }

        private Project newProject(String key) {
            Project project = new Project();
            project.setName(key);
            project.setKey(key);
            return project;
        }
    }
}
