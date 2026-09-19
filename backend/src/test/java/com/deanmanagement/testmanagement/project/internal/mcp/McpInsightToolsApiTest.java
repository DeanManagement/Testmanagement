package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The read-only views: version history, dashboard, flaky tests and the suite report. */
class McpInsightToolsApiTest extends McpToolApiTestSupport {

    @Autowired
    private TestCaseHistoryTools historyTools;
    @Autowired
    private ReportingTools reportingTools;
    @Autowired
    private TestPlanningTools planningTools;

    // --- versions --------------------------------------------------------------------------

    @Test
    void anEditLeavesTheOldTitleReadableAsAVersion() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login", "Open page");
        testCaseTools.updateTestCase(login.key(), "Sign in", null, null, null, null, null, null,
                null, null, null);

        McpDtos.VersionList history = historyTools.listTestCaseVersions(login.key());

        assertThat(history.versions().getFirst().current()).isTrue();
        assertThat(history.versions().getFirst().title()).isEqualTo("Sign in");
        McpDtos.VersionSummary previous = history.versions().get(1);
        McpDtos.VersionDetail old =
                historyTools.getTestCaseVersion(login.key(), previous.versionNumber());
        assertThat(old.title()).isEqualTo("Login");
        assertThat(old.steps()).extracting(McpDtos.Step::action).containsExactly("Open page");
    }

    @Test
    void aVersionThatDoesNotExistIsNotFound() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");

        assertThatThrownBy(() -> historyTools.getTestCaseVersion(login.key(), 99))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anotherProjectsHistoryIsNotFound() {
        authenticateAs(otherProject, ProjectRole.TESTER);
        McpDtos.CreatedTestCase foreign = createCase("Foreign");
        SecurityContextHolder.clearContext();
        authenticateAs(project, ProjectRole.VIEWER);

        assertThatThrownBy(() -> historyTools.listTestCaseVersions(foreign.id().toString()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- dashboard -------------------------------------------------------------------------

    @Test
    void theDashboardCountsOnlyTheKeysOwnProject() {
        authenticateAs(otherProject, ProjectRole.TESTER);
        createCase("Foreign");
        SecurityContextHolder.clearContext();
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        McpDtos.CreatedTestRun run = runOf(login);
        resultRecordingTools.recordTestResult(run.key(), TestResultStatus.PASSED, login.id(), null,
                null, null, null, null);
        testRunWriteTools.completeTestRun(run.key(), null, null);

        McpDtos.Dashboard dashboard = reportingTools.getProjectDashboard();

        assertThat(dashboard.totalTestCases()).isEqualTo(1);
        assertThat(dashboard.completedTestRuns()).isEqualTo(1);
        assertThat(dashboard.passRateTrend()).hasSize(1);
    }

    @Test
    void aViewerKeyCanReadTheDashboardOfAnEmptyProject() {
        authenticateAs(project, ProjectRole.VIEWER);

        McpDtos.Dashboard dashboard = reportingTools.getProjectDashboard();

        assertThat(dashboard.totalTestCases()).isZero();
        assertThat(dashboard.passRateTrend()).isEmpty();
    }

    /**
     * Found on production: a 20-case run completed at 75%, then a one-case re-test passed, and the
     * project's headline pass rate read 100%. "Overall" was the last completed run alone.
     */
    @Test
    void aSmallPassingReTestDoesNotRewriteTheProjectsPassRate() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase a = createCase("A");
        McpDtos.CreatedTestCase b = createCase("B");
        McpDtos.CreatedTestCase c = createCase("C");
        McpDtos.CreatedTestCase d = createCase("D");
        completeRun(runOf(a, b, c, d), Map.of(a, TestResultStatus.PASSED, b, TestResultStatus.PASSED,
                c, TestResultStatus.PASSED, d, TestResultStatus.FAILED));
        completeRun(runOf(a), Map.of(a, TestResultStatus.PASSED));

        McpDtos.Dashboard dashboard = reportingTools.getProjectDashboard();

        assertThat(dashboard.overallPassRate()).isEqualTo(75.0);
        assertThat(dashboard.latestResultsByStatus()).containsExactly(Map.entry("PASSED", 1L));
    }

    @Test
    void reTestingTheFailedCaseIsWhatMovesThePassRate() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase a = createCase("A");
        McpDtos.CreatedTestCase d = createCase("D");
        completeRun(runOf(a, d), Map.of(a, TestResultStatus.PASSED, d, TestResultStatus.FAILED));
        completeRun(runOf(d), Map.of(d, TestResultStatus.PASSED));

        assertThat(reportingTools.getProjectDashboard().overallPassRate()).isEqualTo(100.0);
    }

    /** A result left PENDING in a completed run was never executed, so it is not an outcome. */
    @Test
    void aCaseLeftPendingInALaterRunKeepsItsLastRealOutcome() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase a = createCase("A");
        McpDtos.CreatedTestCase d = createCase("D");
        completeRun(runOf(a, d), Map.of(a, TestResultStatus.PASSED, d, TestResultStatus.PASSED));
        completeRun(runOf(a, d), Map.of(a, TestResultStatus.PASSED));

        assertThat(reportingTools.getProjectDashboard().overallPassRate()).isEqualTo(100.0);
    }

    @Test
    void anOpenRunDoesNotCountYet() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase a = createCase("A");
        resultRecordingTools.recordTestResult(runOf(a).key(), TestResultStatus.FAILED, a.id(), null,
                null, null, null, null);

        assertThat(reportingTools.getProjectDashboard().overallPassRate()).as("nothing executed yet").isNull();
    }

    private void completeRun(McpDtos.CreatedTestRun run,
                             Map<McpDtos.CreatedTestCase, TestResultStatus> outcomes) {
        outcomes.forEach((testCase, status) -> resultRecordingTools.recordTestResult(run.key(),
                status, testCase.id(), null, null, null, null, null));
        testRunWriteTools.completeTestRun(run.key(), null, null);
    }

    // --- flaky -----------------------------------------------------------------------------

    @Test
    void aProjectWithNoHistoryHasNoFlakyTests() {
        authenticateAs(project, ProjectRole.VIEWER);

        assertThat(reportingTools.listFlakyTests(null).total()).isZero();
    }

    // --- suite report ----------------------------------------------------------------------

    @Test
    void theSuiteReportShowsTheLatestCompletedResultAndWhatIsUntested() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        McpDtos.CreatedTestCase logout = createCase("Logout");
        UUID suiteId = planningTools.createTestSuite("Smoke", null,
                Set.of(login.id(), logout.id())).id();
        McpDtos.CreatedTestRun run = runOf(login);
        resultRecordingTools.recordTestResult(run.key(), TestResultStatus.FAILED, login.id(), null,
                "broken", null, null, null);
        testRunWriteTools.completeTestRun(run.key(), null, null);

        McpDtos.SuiteReport report = reportingTools.getTestSuiteReport(suiteId);

        assertThat(report.total()).isEqualTo(2);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.untested()).isEqualTo(1);
        assertThat(report.results())
                .filteredOn(r -> r.testCaseId().equals(logout.id()))
                .singleElement()
                .satisfies(untested -> assertThat(untested.status()).isNull());
    }

    @Test
    void anotherProjectsSuiteReportIsNotFound() {
        authenticateAs(otherProject, ProjectRole.TESTER);
        UUID foreignSuite = planningTools.createTestSuite("Foreign", null, null).id();
        SecurityContextHolder.clearContext();
        authenticateAs(project, ProjectRole.VIEWER);

        assertThatThrownBy(() -> reportingTools.getTestSuiteReport(foreignSuite))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
