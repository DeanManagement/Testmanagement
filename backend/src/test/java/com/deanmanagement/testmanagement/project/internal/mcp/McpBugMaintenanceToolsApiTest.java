package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.service.ProjectService;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Changing a filed bug (TM30, TM31) and the environment strictness on bug writes (TM34). */
class McpBugMaintenanceToolsApiTest extends McpToolApiTestSupport {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

    @Autowired private BugReportTools bugReportTools;
    @Autowired private BugReportMaintenanceTools maintenanceTools;
    @Autowired private ProjectService projectService;
    @Autowired private CommentTools commentTools;

    private McpDtos.BugDetail bug;

    @BeforeEach
    void aBug() {
        projectService.toggleBugReports(project.getId(), true, null);
        authenticateAs(project, ProjectRole.TESTER);
        bug = bugReportTools.createBugReport("Checkout returns 500", Priority.HIGH, "On submit",
                "1. Pay", "Receipt", "HTTP 500", "staging", null, null, null, null);
    }

    @Test
    void anUpdateChangesOnlyWhatIsPassedAndAnEmptyStringClears() {
        McpDtos.BugDetail updated = maintenanceTools.updateBugReport(bug.key(), "Checkout fails", "",
                null, null, null, Priority.CRITICAL, null, null, null, null);

        assertThat(updated.title()).isEqualTo("Checkout fails");
        assertThat(updated.description()).isNull();
        assertThat(updated.priority()).isEqualTo(Priority.CRITICAL);
        assertThat(updated.stepsToReproduce()).isEqualTo("1. Pay");
        assertThat(updated.environment()).isEqualTo("staging");
    }

    @Test
    void aBugHasADiscussion() {
        commentTools.addComment("Reproduced on staging", null, null, bug.key(), null);

        assertThat(commentTools.listComments(null, null, bug.key()).comments())
                .extracting(McpDtos.Comment::content).containsExactly("Reproduced on staging");
    }

    @Test
    void mentioningSomeoneWhoIsNotAMemberIsRefused() {
        assertThatThrownBy(() -> commentTools.addComment("Please look", null, null, bug.key(),
                List.of("nobody@test.local")))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("nobody@test.local");
    }

    @Test
    void aBugBlockedByAnotherListsTheBlockerAsIncoming() {
        McpDtos.BugDetail blocker = bugReportTools.createBugReport("Database down", Priority.CRITICAL, null, null,
                null, null, null, null, null, null, null);

        var relations = maintenanceTools.relateBugReport(bug.key(), null, null, blocker.key(), "blocked_by");

        assertThat(relations.relations()).singleElement().satisfies(relation -> {
            assertThat(relation.bugKey()).isEqualTo(blocker.key());
            assertThat(relation.outgoing()).isFalse();
        });
    }

    @Test
    void relatingNeedsExactlyOneTarget() {
        assertThatThrownBy(() -> maintenanceTools.relateBugReport(bug.key(), "PROJ-1", "REQ-1", null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void anUpdateWithNothingIsRefused() {
        assertThatThrownBy(() -> maintenanceTools.updateBugReport(bug.key(), null, null, null, null, null,
                null, null, null, null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("Nothing to update");
    }

    @Test
    void anUnknownEnvironmentIsRefusedListingTheKnownOnes() {
        assertThatThrownBy(() -> bugReportTools.createBugReport("Search is slow", Priority.LOW, null, null,
                null, null, "prod-eu", null, null, null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("prod-eu")
                .hasMessageContaining("staging");
        assertThatThrownBy(() -> maintenanceTools.updateBugReport(bug.key(), null, null, null, null, null,
                null, "prod-eu", null, null, null))
                .isInstanceOf(McpToolException.class);
    }

    @Test
    void aFurtherOccurrenceCanBeUnlinkedButTheFoundInResultCannot() {
        McpDtos.CreatedTestCase pay = createCase("Pay");
        McpDtos.CreatedTestRun run = runOf(pay);
        UUID resultId = resultRecordingTools.recordTestResult(run.key(), TestResultStatus.FAILED, pay.id(),
                null, null, null, null, null, null).resultId();
        McpDtos.BugDetail found = bugReportTools.createBugReport("Pay fails", Priority.HIGH, null, null,
                null, null, null, resultId, null, null, null);
        bugReportTools.linkBugReport(bug.key(), resultId, null);

        assertThat(maintenanceTools.unlinkBugReport(bug.key(), resultId).occurrences()).isEmpty();
        assertThatThrownBy(() -> maintenanceTools.unlinkBugReport(found.key(), resultId))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("found in");
    }

    @Test
    void anAttachmentAndThenTheBugCanBeDeleted() {
        McpDtos.Attachment stored = bugReportTools.addBugReportAttachment(bug.key(), "x.png", "image/png",
                Base64.getEncoder().encodeToString(PNG));

        maintenanceTools.deleteBugReportAttachment(bug.key(), stored.id());
        assertThat(bugReportTools.getBugReport(bug.key()).attachments()).isEmpty();

        maintenanceTools.deleteBugReport(bug.key());
        assertThatThrownBy(() -> bugReportTools.getBugReport(bug.key()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
