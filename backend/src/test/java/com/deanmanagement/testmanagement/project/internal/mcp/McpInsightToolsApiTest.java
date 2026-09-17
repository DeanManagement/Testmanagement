package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

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
                null);

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
                null, null);
        testRunWriteTools.completeTestRun(run.key(), null);

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
                "broken", null);
        testRunWriteTools.completeTestRun(run.key(), null);

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
