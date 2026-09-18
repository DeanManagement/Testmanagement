package com.deanmanagement.testmanagement.project.internal.webhook;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.Webhook;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookFormat;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.WebhookRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumMap;
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
    private final WebhookPayloadBuilder payloadBuilder;

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
        Map<WebhookFormat, String> bodies = new EnumMap<>(WebhookFormat.class);
        for (Webhook hook : hooks) {
            if (isRedundantForChat(hook, type)) {
                continue;
            }
            try {
                String body = bodies.computeIfAbsent(hook.getFormat(),
                        format -> payloadBuilder.build(format, type, project, data));
                UUID deliveryId = deliveryStore.createPending(hook.getId(), type, body);
                deliver(deliveryId);
            } catch (Exception e) {
                log.warn("Failed to enqueue webhook delivery for {}: {}", hook.getId(), e.getMessage());
            }
        }
    }

    /**
     * A failed run publishes RUN_COMPLETED and then RUN_FAILED. A chat hook subscribed to both
     * would post twice, and the completed message already shows the failure, so it skips the second.
     */
    private static boolean isRedundantForChat(Webhook hook, WebhookEventType type) {
        return hook.getFormat().isChat() && type == WebhookEventType.RUN_FAILED
                && hook.getEvents().contains(WebhookEventType.RUN_COMPLETED);
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
        String body = payloadBuilder.buildTest(hook.getFormat(), sampleEvent, hook.getProject());
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
}
