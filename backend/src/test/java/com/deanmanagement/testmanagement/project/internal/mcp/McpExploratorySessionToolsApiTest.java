package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.SessionNoteType;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TM36: exploratory sessions over MCP. Not transactional, so every write commits. */
class McpExploratorySessionToolsApiTest extends McpToolApiTestSupport {

    /** The smallest valid PNG: a 1x1 transparent pixel. */
    private static final String PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";

    @Autowired private ExploratorySessionTools sessionTools;

    @Test
    void aSessionRunsFromPlannedToCompletedWithNotes() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.ExploratorySession created =
                sessionTools.createExploratorySession("Explore checkout", 30, null, "staging");

        sessionTools.startExploratorySession(created.key());
        sessionTools.addSessionNote(created.key(), SessionNoteType.BUG, "Total is off by one", PNG_BASE64,
                "image/png", "total.png");
        sessionTools.completeExploratorySession(created.id().toString(), "Found one bug");
        McpDtos.ExploratorySession read = sessionTools.getExploratorySession(created.key());

        assertThat(read.status()).isEqualTo(TestRunStatus.COMPLETED);
        assertThat(read.environment()).isEqualTo("staging");
        assertThat(read.summary()).isEqualTo("Found one bug");
        assertThat(read.notes()).singleElement().satisfies(note -> {
            assertThat(note.body()).isEqualTo("Total is off by one");
            assertThat(note.hasImage()).isTrue();
        });
    }

    @Test
    void anUnknownEnvironmentIsRefused() {
        authenticateAs(project, ProjectRole.TESTER);

        assertThatThrownBy(() -> sessionTools.createExploratorySession("Explore", 30, null, "stagin"))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("Unknown environment");
    }

    @Test
    void aViewerKeyCannotCreateASession() {
        authenticateAs(project, ProjectRole.VIEWER);

        assertThatThrownBy(() -> sessionTools.createExploratorySession("Explore", 30, null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("requires TESTER");
    }

    @Test
    void aNoteOnAPlannedSessionIsRefusedByTheService() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.ExploratorySession created = sessionTools.createExploratorySession("Explore", 30, null, null);

        assertThatThrownBy(() -> sessionTools.addSessionNote(created.key(), SessionNoteType.NOTE, "Too early",
                null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anOversizedImageIsRefused() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.ExploratorySession created = sessionTools.createExploratorySession("Explore", 30, null, null);
        sessionTools.startExploratorySession(created.key());
        String tooLarge = Base64.getEncoder().encodeToString(new byte[BugReportTools.MAX_ATTACHMENT_BYTES + 1]);

        assertThatThrownBy(() -> sessionTools.addSessionNote(created.key(), SessionNoteType.NOTE, "Big",
                tooLarge, "image/png", null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("larger than");
    }

    @Test
    void updateChangesOnlyWhatIsPassedAndListShowsTheSession() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.ExploratorySession created =
                sessionTools.createExploratorySession("Explore", 30, null, "staging");

        McpDtos.ExploratorySession updated =
                sessionTools.updateExploratorySession(created.key(), null, 60, null, null, null);

        assertThat(updated.timeboxMinutes()).isEqualTo(60);
        assertThat(updated.charter()).isEqualTo("Explore");
        assertThat(updated.environment()).isEqualTo("staging");
        assertThat(sessionTools.listExploratorySessions(TestRunStatus.PLANNED, null, null, null).sessions())
                .extracting(McpDtos.ExploratorySession::key)
                .containsExactly(created.key());
    }

    @Test
    void deleteRemovesTheSession() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.ExploratorySession created = sessionTools.createExploratorySession("Explore", 30, null, null);

        sessionTools.deleteExploratorySession(created.key());

        assertThatThrownBy(() -> sessionTools.getExploratorySession(created.id().toString()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aKeyFromAnotherProjectIsNotFound() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.ExploratorySession created = sessionTools.createExploratorySession("Explore", 30, null, null);
        authenticateAs(otherProject, ProjectRole.TESTER);

        assertThatThrownBy(() -> sessionTools.getExploratorySession(created.key()))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("No exploratory session");
    }
}
