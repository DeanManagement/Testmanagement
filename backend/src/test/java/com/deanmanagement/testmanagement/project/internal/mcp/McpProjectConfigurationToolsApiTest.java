package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookFormat;
import com.deanmanagement.testmanagement.project.internal.repository.WebhookRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TM37: webhooks, the tracker connection and pipeline workflows over MCP, with keys that hold the
 * CONFIGURATION group; the group gate itself is covered over HTTP by {@link McpToolGroupsApiTest}.
 * Not transactional, so every write commits.
 */
class McpProjectConfigurationToolsApiTest extends McpToolApiTestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<WebhookEventType> RUN_EVENTS =
            Set.of(WebhookEventType.RUN_COMPLETED, WebhookEventType.RUN_FAILED);

    private static final Set<McpToolGroup> CONFIGURATION = EnumSet.of(McpToolGroup.CONFIGURATION);

    @Autowired private ProjectConfigurationTools configurationTools;
    @Autowired private WebhookRepository webhookRepository;

    /**
     * Before the support class deletes the projects: ProjectService.delete does not remove webhooks,
     * and their foreign key has no ON DELETE CASCADE.
     */
    @AfterEach
    void deleteWebhooks() {
        webhookRepository.deleteAll(webhookRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()));
        webhookRepository.deleteAll(webhookRepository.findByProjectIdOrderByCreatedAtDesc(otherProject.getId()));
    }

    @Test
    void aTesterKeyCannotReadWebhooks() {
        authenticateAs(project, ProjectRole.TESTER, CONFIGURATION);

        assertThatThrownBy(() -> configurationTools.listWebhooks())
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("requires ADMIN");
    }

    @Test
    void aCreatedWebhookNeverEchoesItsSecretOrUrlCredentials() {
        authenticateAs(project, ProjectRole.ADMIN, CONFIGURATION);

        McpDtos.Webhook created = configurationTools.createWebhook(
                "https://user:pa55@hooks.example.com/in?token=t0ken", RUN_EVENTS, null, "s3cret-value", null);

        String json = JSON.writeValueAsString(configurationTools.listWebhooks());
        assertThat(created.secretSet()).isTrue();
        assertThat(created.url()).isEqualTo("https://hooks.example.com/in?…");
        assertThat(json).doesNotContain("s3cret-value", "pa55", "t0ken");
    }

    @Test
    void aWebhookUrlGoesThroughTheOutboundUrlValidator() {
        authenticateAs(project, ProjectRole.ADMIN, CONFIGURATION);

        assertThatThrownBy(() -> configurationTools.createWebhook("ftp://example.com/hook", RUN_EVENTS, null,
                "secret", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http(s)");
    }

    @Test
    void updateChangesOnlyWhatIsPassedAndDeleteRemovesIt() {
        authenticateAs(project, ProjectRole.ADMIN, CONFIGURATION);
        McpDtos.Webhook created = configurationTools.createWebhook("https://example.com/hook", RUN_EVENTS,
                WebhookFormat.GENERIC, "secret", null);

        McpDtos.Webhook paused = configurationTools.updateWebhook(created.id(), null, null, null, null, false);
        configurationTools.deleteWebhook(created.id());

        assertThat(paused.active()).isFalse();
        assertThat(paused.events()).isEqualTo(RUN_EVENTS);
        assertThat(paused.url()).isEqualTo("https://example.com/hook");
        assertThat(configurationTools.listWebhooks().webhooks()).isEmpty();
    }

    @Test
    void anotherProjectsWebhookIsNotFound() {
        authenticateAs(otherProject, ProjectRole.ADMIN, CONFIGURATION);
        UUID foreign = configurationTools.createWebhook("https://example.com/hook", RUN_EVENTS, null, "secret",
                null).id();
        authenticateAs(project, ProjectRole.ADMIN, CONFIGURATION);

        assertThatThrownBy(() -> configurationTools.deleteWebhook(foreign))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("No webhook");
    }

    @Test
    void theTrackerTokenIsWriteOnlyAndKeptAcrossPartialUpdates() {
        authenticateAs(project, ProjectRole.ADMIN, CONFIGURATION);
        assertThat(configurationTools.getIssueTrackerConfig().configured()).isFalse();
        configurationTools.setIssueTrackerConfig(IssueTrackerProviderType.GITLAB, "https://gitlab.example.com",
                "group/repo", "glpat-secret-token", null, null, null, null);

        McpDtos.IssueTrackerConfig updated = configurationTools.setIssueTrackerConfig(null, null, "group/other",
                null, null, null, null, null);

        assertThat(updated.tokenSet()).isTrue();
        assertThat(updated.projectRef()).isEqualTo("group/other");
        assertThat(updated.baseUrl()).isEqualTo("https://gitlab.example.com");
        assertThat(JSON.writeValueAsString(configurationTools.getIssueTrackerConfig()))
                .doesNotContain("glpat-secret-token");
    }

    @Test
    void deletingTheTrackerConfigLeavesNone() {
        authenticateAs(project, ProjectRole.ADMIN, CONFIGURATION);
        configurationTools.setIssueTrackerConfig(IssueTrackerProviderType.GITLAB, "https://gitlab.example.com",
                "group/repo", "token", null, null, null, null);

        configurationTools.deleteIssueTrackerConfig();

        assertThat(configurationTools.getIssueTrackerConfig().configured()).isFalse();
    }

    @Test
    void aWorkflowNotOpenToTheProjectCannotBeOffered() {
        authenticateAs(project, ProjectRole.ADMIN, CONFIGURATION);

        assertThatThrownBy(() -> configurationTools.setPipelineWorkflows(List.of(UUID.randomUUID())))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
