package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.bugstatus.CreateBugStatusRequest;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.BugStatusService;
import com.deanmanagement.testmanagement.project.internal.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TM47 over MCP: changing a bug to one of the project's own statuses by name. */
class McpBugStatusApiTest extends McpToolApiTestSupport {

    @Autowired private BugReportTools bugReportTools;
    @Autowired private BugStatusService bugStatusService;
    @Autowired private ProjectService projectService;

    private McpDtos.BugDetail bug;

    @BeforeEach
    void aBugInASeededProject() {
        projectService.toggleBugReports(project.getId(), true, null);
        bugStatusService.seedDefaults(project);
        bugStatusService.create(project.getId(), new CreateBugStatusRequest("Ready for retest", "#0ea5e9",
                BugReportStatus.RESOLVED), null);
        authenticateAs(project, ProjectRole.TESTER);
        bug = bugReportTools.createBugReport("Checkout returns 500", Priority.HIGH, null, null, null, null, null,
                null, null, null, null);
    }

    @Test
    void aCustomStatusByNameDecidesTheCategory() {
        McpDtos.BugDetail moved = bugReportTools.changeBugReportStatus(bug.key(), null, "fix deployed", null, null,
                "ready FOR retest");

        assertThat(moved.status()).isEqualTo(BugReportStatus.RESOLVED);
        assertThat(moved.customStatus()).isEqualTo("Ready for retest");
    }

    @Test
    void anUnknownNameIsRefusedListingTheStatuses() {
        assertThatThrownBy(() -> bugReportTools.changeBugReportStatus(bug.key(), null, "x", null, null, "Done"))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("Ready for retest (RESOLVED)");
    }

    @Test
    void aNameOfAnotherCategoryThanTheStatusIsRefused() {
        assertThatThrownBy(() -> bugReportTools.changeBugReportStatus(bug.key(), BugReportStatus.CLOSED, "x", null,
                null, "Ready for retest"))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("counts as RESOLVED");
    }

    @Test
    void bugsAreListedByProjectStatusName() {
        bugReportTools.changeBugReportStatus(bug.key(), null, "fix deployed", null, null, "Ready for retest");
        bugReportTools.createBugReport("Another", Priority.LOW, null, null, null, null, null, null, null, null, null);

        assertThat(bugReportTools.listBugReports(null, null, null, null, null, null, List.of("Ready for retest")).bugReports())
                .extracting(McpDtos.BugSummary::key).containsExactly(bug.key());
    }

    @Test
    void theStatusesListWhereEachMayGo() {
        assertThat(bugReportTools.listBugStatuses().statuses())
                .extracting(McpDtos.BugStatus::name)
                .contains("New", "Ready for retest", "Closed");
    }
}
