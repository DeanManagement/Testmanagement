package com.deanmanagement.testmanagement.project.internal.webhook;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.Webhook;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.WebhookRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds, signs, and sends webhook payloads, recording each attempt via {@link WebhookDeliveryStore}.
 * The HTTP call is performed outside any DB transaction.
 */
@Service
@RequiredArgsConstructor
public class WebhookDispatchService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatchService.class);

    private final WebhookRepository webhookRepository;
    private final ProjectRepository projectRepository;
    private final WebhookDeliveryStore deliveryStore;
    private final WebhookSigner signer;
    private final WebhookUrlValidator urlValidator;
    private final WebhookProperties properties;
    private final ObjectMapper objectMapper;

    private HttpClient httpClient;

    private HttpClient client() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
        }
        return httpClient;
    }

    /** Fan out an event to every active webhook subscribed to it. Called asynchronously after commit. */
    public void dispatch(WebhookEventType type, UUID projectId, Map<String, Object> data) {
        List<Webhook> hooks = webhookRepository.findActiveForEvent(projectId, type);
        if (hooks.isEmpty()) {
            return;
        }
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return;
        }
        String body = buildPayload(type, project, data);
        for (Webhook hook : hooks) {
            try {
                UUID deliveryId = deliveryStore.createPending(hook.getId(), type, body);
                deliver(deliveryId);
            } catch (Exception e) {
                log.warn("Failed to enqueue webhook delivery for {}: {}", hook.getId(), e.getMessage());
            }
        }
    }

    /** Performs one delivery attempt for an existing (pending) delivery, updating its state. */
    public void deliver(UUID deliveryId) {
        WebhookDeliveryStore.DeliveryContext ctx = deliveryStore.beginAttempt(deliveryId);
        try {
            int status = send(ctx);
            if (status >= 200 && status < 300) {
                deliveryStore.recordSuccess(deliveryId, status);
            } else {
                deliveryStore.recordFailure(deliveryId, status, "Non-2xx response: " + status);
            }
        } catch (Exception e) {
            deliveryStore.recordFailure(deliveryId, null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Sends a synthetic payload to a webhook immediately and returns the delivery id. */
    public UUID sendTest(UUID webhookId) {
        Webhook hook = webhookRepository.findById(webhookId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook", webhookId));
        WebhookEventType sampleEvent = hook.getEvents().stream().findFirst().orElse(WebhookEventType.RUN_COMPLETED);
        Project project = hook.getProject();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("test", true);
        data.put("message", "This is a test delivery from Testmanagement.");
        String body = buildPayload(sampleEvent, project, data);
        UUID deliveryId = deliveryStore.createPending(webhookId, sampleEvent, body);
        deliver(deliveryId);
        return deliveryId;
    }

    private int send(WebhookDeliveryStore.DeliveryContext ctx) throws Exception {
        urlValidator.validate(ctx.url());
        String signature = signer.sign(ctx.secret(), ctx.body());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ctx.url()))
                .timeout(Duration.ofMillis(properties.readTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("X-TM-Event", ctx.event().name())
                .header("X-TM-Delivery", ctx.deliveryId().toString())
                .header("X-TM-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(ctx.body()))
                .build();
        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    private String buildPayload(WebhookEventType type, Project project, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", type.name());
        payload.put("projectId", project.getId().toString());
        payload.put("projectKey", project.getKey());
        payload.put("projectName", project.getName());
        payload.put("timestamp", Instant.now().toString());
        payload.put("data", data != null ? data : Map.of());
        return objectMapper.writeValueAsString(payload);
    }
}
