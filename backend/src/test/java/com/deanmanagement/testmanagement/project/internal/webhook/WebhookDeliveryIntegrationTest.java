package com.deanmanagement.testmanagement.project.internal.webhook;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.Webhook;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookDelivery;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.WebhookDeliveryRepository;
import com.deanmanagement.testmanagement.project.internal.repository.WebhookRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class WebhookDeliveryIntegrationTest {

    @Autowired
    private WebhookDispatchService dispatchService;
    @Autowired
    private WebhookRepository webhookRepository;
    @Autowired
    private WebhookDeliveryRepository deliveryRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private WebhookSigner signer;

    private static final String SECRET = "topsecret";

    private HttpServer server;
    private final AtomicInteger responseCode = new AtomicInteger(200);
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastSignature = new AtomicReference<>();
    private final AtomicReference<String> lastEvent = new AtomicReference<>();
    private final AtomicReference<String> lastDeliveryId = new AtomicReference<>();

    private UUID projectId;
    private UUID webhookId;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            lastBody.set(new String(body, StandardCharsets.UTF_8));
            lastSignature.set(exchange.getRequestHeaders().getFirst("X-TM-Signature"));
            lastEvent.set(exchange.getRequestHeaders().getFirst("X-TM-Event"));
            lastDeliveryId.set(exchange.getRequestHeaders().getFirst("X-TM-Delivery"));
            exchange.sendResponseHeaders(responseCode.get(), -1);
            exchange.close();
        });
        server.start();

        Project project = new Project();
        project.setName("Hooked");
        project.setKey("HOOK");
        projectId = projectRepository.save(project).getId();

        Webhook webhook = new Webhook();
        webhook.setProject(project);
        webhook.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/hook");
        webhook.setSecret(SECRET);
        webhook.setEvents(Set.of(WebhookEventType.RUN_COMPLETED));
        webhook.setActive(true);
        webhookId = webhookRepository.save(webhook).getId();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private List<WebhookDelivery> deliveries() {
        return deliveryRepository.findByWebhookIdOrderByCreatedAtDesc(webhookId, PageRequest.of(0, 50)).getContent();
    }

    @Test
    void successfulDelivery_recordsSuccessAndSignsPayload() {
        responseCode.set(200);

        dispatchService.dispatch(WebhookEventType.RUN_COMPLETED, projectId, Map.of("runKey", "HOOK-Run-1"));

        List<WebhookDelivery> deliveries = deliveries();
        assertThat(deliveries).hasSize(1);
        WebhookDelivery d = deliveries.get(0);
        assertThat(d.getSuccess()).isTrue();
        assertThat(d.getResponseStatus()).isEqualTo(200);
        assertThat(d.getAttempt()).isEqualTo(1);

        assertThat(lastEvent.get()).isEqualTo("RUN_COMPLETED");
        assertThat(lastDeliveryId.get()).isEqualTo(d.getId().toString());
        assertThat(lastSignature.get()).isEqualTo(signer.sign(SECRET, lastBody.get()));
        assertThat(lastBody.get()).contains("\"projectKey\":\"HOOK\"").contains("\"event\":\"RUN_COMPLETED\"");
    }

    @Test
    void serverError_marksPendingForRetry() {
        responseCode.set(500);

        dispatchService.dispatch(WebhookEventType.RUN_COMPLETED, projectId, Map.of());

        WebhookDelivery d = deliveries().get(0);
        assertThat(d.getSuccess()).isNull();
        assertThat(d.getAttempt()).isEqualTo(1);
        assertThat(d.getResponseStatus()).isEqualTo(500);
        assertThat(d.getNextAttemptAt()).isNotNull();
    }

    @Test
    void sendTest_deliversAndReturnsId() {
        responseCode.set(200);

        UUID deliveryId = dispatchService.sendTest(webhookId);

        WebhookDelivery d = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(d.getSuccess()).isTrue();
        assertThat(lastBody.get()).contains("\"test\":true");
    }

    @Test
    void inactiveWebhook_noDelivery() {
        Webhook webhook = webhookRepository.findById(webhookId).orElseThrow();
        webhook.setActive(false);
        webhookRepository.save(webhook);

        dispatchService.dispatch(WebhookEventType.RUN_COMPLETED, projectId, Map.of());

        assertThat(deliveries()).isEmpty();
    }

    @Test
    void unsubscribedEvent_noDelivery() {
        dispatchService.dispatch(WebhookEventType.BUG_REPORT_CREATED, projectId, Map.of());
        assertThat(deliveries()).isEmpty();
    }
}
