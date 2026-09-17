package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.UpdateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.service.BugReportService;
import com.deanmanagement.testmanagement.project.internal.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A test result belongs to exactly one run, so a bug attached to a result is attached to that run
 * whether or not the caller says so. Found by dogfooding: every bug an agent filed over MCP with
 * only a {@code testResultId} — which is what the tool tells it to pass — showed no run in the UI,
 * while bugs filed from the SPA, which sends both ids, did.
 */
class McpBugRunLinkApiTest extends McpToolApiTestSupport {

    @Autowired
    private BugReportTools bugReportTools;
    @Autowired
    private BugReportService bugReportService;
    @Autowired
    private ProjectService projectService;

    private UUID agent;
    private McpDtos.CreatedTestRun run;
    private UUID resultId;

    @BeforeEach
    void aFailedResult() {
        projectService.toggleBugReports(project.getId(), true, null);
        agent = authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase checkout = createCase("Checkout");
        run = runOf(checkout);
        resultId = resultRecordingTools.recordTestResult(run.key(), TestResultStatus.FAILED,
                checkout.id(), null, "HTTP 500", null).resultId();
    }

    private McpDtos.BugDetail fileBug(String title, UUID testResultId, UUID testRunId) {
        return bugReportTools.createBugReport(title, Priority.HIGH, null, null, null, null, null,
                testResultId, testRunId, null);
    }

    @Test
    void aBugFiledWithOnlyAResultIsLinkedToThatResultsRun() {
        McpDtos.BugDetail bug = fileBug("Checkout returns 500", resultId, null);

        assertThat(bug.testRunId()).isEqualTo(run.id());
        assertThat(bug.testRunName()).isEqualTo("Run");
    }

    @Test
    void aRunThatIsNotTheResultsRunIsRefused() {
        McpDtos.CreatedTestRun otherRun = runOf(createCase("Unrelated"));

        assertThatThrownBy(() -> fileBug("Checkout returns 500", resultId, otherRun.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belongs to");
    }

    @Test
    void namingTheResultsOwnRunExplicitlyStillWorks() {
        assertThat(fileBug("Checkout returns 500", resultId, run.id()).testRunId())
                .isEqualTo(run.id());
    }

    @Test
    void aBugCanStillBeFiledAgainstARunWithoutAResult() {
        assertThat(fileBug("Environment was down", null, run.id()).testRunId())
                .isEqualTo(run.id());
    }

    /** The SPA's update is a full replace; omitting the run while keeping the result must not unlink it. */
    @Test
    void anUpdateThatKeepsTheResultKeepsItsRun() {
        McpDtos.BugDetail bug = fileBug("Checkout returns 500", resultId, null);

        BugReportResponse updated = bugReportService.update(project.getId(), bug.id(),
                new UpdateBugReportRequest("Checkout returns 500", null, null, null, null,
                        Priority.HIGH, BugReportStatus.OPEN, null, resultId, null, null), agent);

        assertThat(updated.testRunId()).isEqualTo(run.id());
    }

    @Test
    void anUpdateThatClearsTheResultCanStillClearTheRun() {
        McpDtos.BugDetail bug = fileBug("Checkout returns 500", resultId, null);

        BugReportResponse updated = bugReportService.update(project.getId(), bug.id(),
                new UpdateBugReportRequest("Checkout returns 500", null, null, null, null,
                        Priority.HIGH, BugReportStatus.OPEN, null, null, null, null), agent);

        assertThat(updated.testRunId()).isNull();
    }
}
