package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.webhook.CreateWebhookRequest;
import com.deanmanagement.testmanagement.project.internal.dto.webhook.UpdateWebhookRequest;
import com.deanmanagement.testmanagement.project.internal.dto.webhook.WebhookResponse;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.Webhook;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookFormat;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.WebhookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class WebhookServiceTest {

    private static final String SLACK_URL = "https://hooks.slack.com/services/T000/B000/abcdWXYZ";
    private static final Set<WebhookEventType> RUN_EVENTS = Set.of(WebhookEventType.RUN_COMPLETED);

    @Autowired
    private WebhookService webhookService;
    @Autowired
    private WebhookRepository webhookRepository;
    @Autowired
    private ProjectRepository projectRepository;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        Project project = new Project();
        project.setName("Chatty");
        project.setKey("CHAT");
        projectId = projectRepository.save(project).getId();
    }

    private WebhookResponse createChatHook(WebhookFormat format) {
        return webhookService.create(projectId, new CreateWebhookRequest(SLACK_URL, null, RUN_EVENTS, true, format));
    }

    @Test
    void formatDefaultsToGeneric() {
        WebhookResponse created = webhookService.create(projectId,
                new CreateWebhookRequest("https://example.com/hook", "s3cret", RUN_EVENTS, true, null));

        assertThat(created.format()).isEqualTo(WebhookFormat.GENERIC);
        assertThat(created.url()).isEqualTo("https://example.com/hook");
    }

    @Test
    void genericHookStillRequiresASecret() {
        assertThatThrownBy(() -> webhookService.create(projectId,
                new CreateWebhookRequest("https://example.com/hook", " ", RUN_EVENTS, true, WebhookFormat.GENERIC)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secret");
    }

    @Test
    void chatHookGetsAGeneratedSecret() {
        WebhookResponse created = createChatHook(WebhookFormat.SLACK);

        Webhook stored = webhookRepository.findById(created.id()).orElseThrow();
        assertThat(stored.getSecret()).hasSize(64);
    }

    @Test
    void chatHookUrlIsMasked() {
        WebhookResponse created = createChatHook(WebhookFormat.SLACK);

        assertThat(created.url()).isEqualTo("https://hooks.slack.com/…WXYZ");
    }

    @Test
    void chatHookCannotSubscribeToTestFailed() {
        assertThatThrownBy(() -> webhookService.create(projectId, new CreateWebhookRequest(SLACK_URL, null,
                Set.of(WebhookEventType.TEST_FAILED), true, WebhookFormat.SLACK)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TEST_FAILED");
    }

    @Test
    void switchingToChatFormatWhileSubscribedToTestFailedIsRejected() {
        WebhookResponse generic = webhookService.create(projectId, new CreateWebhookRequest(
                "https://example.com/hook", "s3cret", Set.of(WebhookEventType.TEST_FAILED), true, null));

        assertThatThrownBy(() -> webhookService.update(projectId, generic.id(), new UpdateWebhookRequest(
                null, null, Set.of(WebhookEventType.TEST_FAILED), null, WebhookFormat.SLACK)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateWithoutUrlKeepsTheStoredUrl() {
        WebhookResponse created = createChatHook(WebhookFormat.SLACK);

        webhookService.update(projectId, created.id(), new UpdateWebhookRequest(null, null, RUN_EVENTS, false, null));

        Webhook stored = webhookRepository.findById(created.id()).orElseThrow();
        assertThat(stored.getUrl()).isEqualTo(SLACK_URL);
        assertThat(stored.isActive()).isFalse();
        assertThat(stored.getFormat()).isEqualTo(WebhookFormat.SLACK);
    }
}
