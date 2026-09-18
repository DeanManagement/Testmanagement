package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.webhook.CreateWebhookRequest;
import com.deanmanagement.testmanagement.project.internal.dto.webhook.UpdateWebhookRequest;
import com.deanmanagement.testmanagement.project.internal.dto.webhook.WebhookDeliveryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.webhook.WebhookResponse;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.Webhook;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookDelivery;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookFormat;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.WebhookDeliveryRepository;
import com.deanmanagement.testmanagement.project.internal.repository.WebhookRepository;
import com.deanmanagement.testmanagement.project.internal.webhook.WebhookDispatchService;
import com.deanmanagement.testmanagement.project.internal.webhook.WebhookUrlValidator;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WebhookService {

    private static final int GENERATED_SECRET_BYTES = 32;
    private static final int MASK_VISIBLE_CHARS = 4;

    private final SecureRandom secureRandom = new SecureRandom();

    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ProjectRepository projectRepository;
    private final WebhookUrlValidator urlValidator;
    private final WebhookDispatchService dispatchService;

    public List<WebhookResponse> findByProject(UUID projectId) {
        return webhookRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WebhookResponse create(UUID projectId, CreateWebhookRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        WebhookFormat format = request.format() != null ? request.format() : WebhookFormat.GENERIC;
        urlValidator.validate(request.url(), format);
        validateEvents(format, request.events());

        Webhook webhook = new Webhook();
        webhook.setProject(project);
        webhook.setUrl(request.url());
        webhook.setFormat(format);
        webhook.setSecret(resolveNewSecret(format, request.secret()));
        webhook.setEvents(new HashSet<>(request.events()));
        webhook.setActive(request.active() == null || request.active());
        return toResponse(webhookRepository.save(webhook));
    }

    @Transactional
    public WebhookResponse update(UUID projectId, UUID webhookId, UpdateWebhookRequest request) {
        Webhook webhook = requireWebhook(projectId, webhookId);
        WebhookFormat format = request.format() != null ? request.format() : webhook.getFormat();
        String url = isBlank(request.url()) ? webhook.getUrl() : request.url();
        urlValidator.validate(url, format);
        validateEvents(format, request.events());

        webhook.setUrl(url);
        webhook.setFormat(format);
        if (!isBlank(request.secret())) {
            webhook.setSecret(request.secret());
        }
        webhook.setEvents(new HashSet<>(request.events()));
        if (request.active() != null) {
            webhook.setActive(request.active());
        }
        return toResponse(webhookRepository.save(webhook));
    }

    @Transactional
    public void delete(UUID projectId, UUID webhookId) {
        Webhook webhook = requireWebhook(projectId, webhookId);
        webhookRepository.delete(webhook);
    }

    public Page<WebhookDeliveryResponse> getDeliveries(UUID projectId, UUID webhookId, Pageable pageable) {
        requireWebhook(projectId, webhookId);
        return deliveryRepository.findByWebhookIdOrderByCreatedAtDesc(webhookId, pageable)
                .map(this::toDeliveryResponse);
    }

    public WebhookDeliveryResponse sendTest(UUID projectId, UUID webhookId) {
        requireWebhook(projectId, webhookId);
        UUID deliveryId = dispatchService.sendTest(webhookId);
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookDelivery", deliveryId));
        return toDeliveryResponse(delivery);
    }

    private Webhook requireWebhook(UUID projectId, UUID webhookId) {
        return webhookRepository.findById(webhookId)
                .filter(w -> w.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Webhook", webhookId));
    }

    /**
     * One TEST_FAILED per failed result would flood a channel (and hit Slack's rate limit) on a
     * large CI upload, so chat hooks get run summaries only. No silent unsubscribe on format change.
     */
    private static void validateEvents(WebhookFormat format, Set<WebhookEventType> events) {
        if (format.isChat() && events.contains(WebhookEventType.TEST_FAILED)) {
            throw new IllegalArgumentException(
                    "Chat webhooks can't subscribe to TEST_FAILED; run events list the failed tests instead");
        }
    }

    /** Chat services ignore the signature, so their hooks get a generated secret and still sign. */
    private String resolveNewSecret(WebhookFormat format, String secret) {
        if (!isBlank(secret)) {
            return secret;
        }
        if (format.isChat()) {
            byte[] bytes = new byte[GENERATED_SECRET_BYTES];
            secureRandom.nextBytes(bytes);
            return HexFormat.of().formatHex(bytes);
        }
        throw new IllegalArgumentException("secret is required for GENERIC webhooks");
    }

    /** Chat URLs are the credential: show only scheme, host and the last few characters. */
    static String maskUrl(Webhook webhook) {
        String url = webhook.getUrl();
        if (!webhook.getFormat().isChat()) {
            return url;
        }
        URI uri = URI.create(url);
        String tail = url.substring(Math.max(0, url.length() - MASK_VISIBLE_CHARS));
        return uri.getScheme() + "://" + uri.getRawAuthority() + "/…" + tail;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private WebhookResponse toResponse(Webhook webhook) {
        return new WebhookResponse(
                webhook.getId(),
                maskUrl(webhook),
                webhook.getEvents(),
                webhook.isActive(),
                webhook.getFormat(),
                webhook.getCreatedAt(),
                webhook.getUpdatedAt()
        );
    }

    private WebhookDeliveryResponse toDeliveryResponse(WebhookDelivery delivery) {
        return new WebhookDeliveryResponse(
                delivery.getId(),
                delivery.getEvent(),
                delivery.getResponseStatus(),
                delivery.getAttempt(),
                delivery.getSuccess(),
                delivery.getError(),
                delivery.getCreatedAt()
        );
    }
}
