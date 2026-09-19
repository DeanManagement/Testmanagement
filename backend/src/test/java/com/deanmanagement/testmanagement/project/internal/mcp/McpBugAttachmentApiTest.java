package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PRD-051 §3.6: an agent that reproduced a bug in a browser attaches its screenshot. It cannot send
 * multipart, so the bytes arrive as base64, bounded before they are decoded.
 */
class McpBugAttachmentApiTest extends McpToolApiTestSupport {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

    @Autowired
    private BugReportTools bugReportTools;
    @Autowired
    private ProjectService projectService;

    private McpDtos.BugDetail bug;

    @BeforeEach
    void aBug() {
        projectService.toggleBugReports(project.getId(), true, null);
        authenticateAs(project, ProjectRole.TESTER);
        bug = bugReportTools.createBugReport("Checkout returns 500", Priority.HIGH, null, null, null, null, null,
                null, null, null, null);
    }

    @Test
    void aBase64ScreenshotIsStoredAndListedOnTheBug() {
        McpDtos.Attachment stored = bugReportTools.addBugReportAttachment(bug.key(), "checkout.png", "image/png",
                Base64.getEncoder().encodeToString(PNG));

        assertThat(stored.sizeBytes()).isEqualTo(PNG.length);
        assertThat(bugReportTools.getBugReport(bug.key()).attachments())
                .extracting(McpDtos.Attachment::id, McpDtos.Attachment::fileName)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(stored.id(), "checkout.png"));
    }

    @Test
    void contentOverTheCapIsRefusedBeforeDecoding() {
        String tooLong = "A".repeat((BugReportTools.MAX_ATTACHMENT_BYTES / 3 + 2) * 4);

        assertThatThrownBy(() -> bugReportTools.addBugReportAttachment(bug.key(), "big.png", "image/png", tooLong))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("2 MB");
    }

    @Test
    void invalidBase64IsRefused() {
        assertThatThrownBy(() -> bugReportTools.addBugReportAttachment(bug.key(), "x.png", "image/png", "not*base64"))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("base64");
    }

    @Test
    void bytesThatDoNotMatchTheTypeAreRefused() {
        String html = Base64.getEncoder().encodeToString("<html><script>x</script>".getBytes());

        assertThatThrownBy(() -> bugReportTools.addBugReportAttachment(bug.key(), "x.png", "image/png", html))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void aViewerKeyCannotAttach() {
        authenticateAs(project, ProjectRole.VIEWER);

        assertThatThrownBy(() -> bugReportTools.addBugReportAttachment(bug.key(), "x.png", "image/png",
                Base64.getEncoder().encodeToString(PNG)))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("TESTER");
    }
}
