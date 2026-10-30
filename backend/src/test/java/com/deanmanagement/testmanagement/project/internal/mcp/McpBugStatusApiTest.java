package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.bugstatus.CreateBugStatusRequest;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.BugResolution;
import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.BugStatusService;
import com.deanmanagement.testmanagement.project.internal.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TM47 over MCP: changing a bug to one of the project's own statuses by name. */
class McpBugStatusApiTest extends McpToolApiTestSupport {

    private static final Set<McpToolGroup> CONFIGURATION = Set.of(McpToolGroup.CONFIGURATION);

    @Autowired private BugReportTools bugReportTools;
    @Autowired private ProjectConfigurationTools configurationTools;
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
    void anAdminKeyWithConfigurationAddsAStatus() {
        authenticateAs(project, ProjectRole.ADMIN, CONFIGURATION);

        var statuses = configurationTools.createBugStatus("Waiting for vendor", BugReportStatus.IN_PROGRESS, null);

        assertThat(statuses.statuses()).extracting(McpDtos.BugStatus::name).contains("Waiting for vendor");
    }

    @Test
    void aTesterKeyCannotEditStatuses() {
        authenticateAs(project, ProjectRole.TESTER, CONFIGURATION);

        assertThatThrownBy(() -> configurationTools.createBugStatus("Mine", BugReportStatus.OPEN, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("ADMIN");
    }

    @Test
    void anArchivedStatusIsFoundByNameToRestoreIt() {
        authenticateAs(project, ProjectRole.ADMIN, CONFIGURATION);
        configurationTools.updateBugStatus("Ready for retest", null, null, null, true);

        var statuses = configurationTools.updateBugStatus("ready for retest", "Retest", null, null, false);

        assertThat(statuses.statuses()).filteredOn(s -> s.name().equals("Retest"))
                .singleElement().satisfies(s -> assertThat(s.archived()).isFalse());
    }

    @Test
    void transitionsSetByNameRestrictTheMoves() {
        authenticateAs(project, ProjectRole.ADMIN, CONFIGURATION);
        configurationTools.setBugStatusTransitions(List.of(new McpDtos.BugTransition("New", "In progress")));
        authenticateAs(project, ProjectRole.TESTER);

        assertThatThrownBy(() -> bugReportTools.changeBugReportStatus(bug.key(), null, "done", BugResolution.FIXED,
                null, "Closed"))
                .hasMessageContaining("Allowed: In progress");
    }

    @Test
    void anEmptyTransitionListAllowsAnyMoveAgain() {
        authenticateAs(project, ProjectRole.ADMIN, CONFIGURATION);
        configurationTools.setBugStatusTransitions(List.of(new McpDtos.BugTransition("New", "In progress")));

        var statuses = configurationTools.setBugStatusTransitions(List.of());

        assertThat(statuses.statuses()).filteredOn(s -> s.name().equals("New"))
                .singleElement().satisfies(s -> assertThat(s.allowedNext()).contains("Closed", "Ready for retest"));
    }

    @Test
    void anUnknownStatusNameIsRefusedListingTheStatuses() {
        authenticateAs(project, ProjectRole.ADMIN, CONFIGURATION);

        assertThatThrownBy(() -> configurationTools.setBugStatusTransitions(
                List.of(new McpDtos.BugTransition("New", "Done"))))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("'Done'")
                .hasMessageContaining("Closed (CLOSED)");
    }

    @Test
    void theStatusesListWhereEachMayGo() {
        assertThat(bugReportTools.listBugStatuses().statuses())
                .extracting(McpDtos.BugStatus::name)
                .contains("New", "Ready for retest", "Closed");
    }
}
