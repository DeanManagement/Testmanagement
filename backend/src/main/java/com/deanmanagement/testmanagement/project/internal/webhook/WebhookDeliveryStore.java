package com.deanmanagement.testmanagement.project.internal.webhook;

import com.deanmanagement.testmanagement.project.internal.entity.Webhook;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookDelivery;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.repository.WebhookDeliveryRepository;
import com.deanmanagement.testmanagement.project.internal.repository.WebhookRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Owns all transactional state changes for webhook deliveries. Kept separate from
 * {@link WebhookDispatchService} so the HTTP call happens outside any DB transaction and so the
 * transactional boundaries are real (no self-invocation).
 */
@Service
@RequiredArgsConstructor
public class WebhookDeliveryStore {

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookRepository webhookRepository;
    private final WebhookProperties properties;

    /** Context copied out of the transaction so the HTTP send doesn't touch lazy entity state. */
    public record DeliveryContext(UUID deliveryId, String url, String secret, String body,
                                  WebhookEventType event, int attempt) {
    }

    @Transactional
    public UUID createPending(UUID webhookId, WebhookEventType event, String body) {
        Webhook webhook = webhookRepository.findById(webhookId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook", webhookId));
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWebhook(webhook);
        delivery.setEvent(event);
        delivery.setRequestBody(body);
        delivery.setAttempt(0);
        delivery.setSuccess(null);
        delivery.setNextAttemptAt(Instant.now());
        return deliveryRepository.save(delivery).getId();
    }

    /** Increments the attempt counter and returns the data needed to perform the HTTP send. */
    @Transactional
    public DeliveryContext beginAttempt(UUID deliveryId) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookDelivery", deliveryId));
        delivery.setAttempt(delivery.getAttempt() + 1);
        Webhook webhook = delivery.getWebhook();
        return new DeliveryContext(delivery.getId(), webhook.getUrl(), webhook.getSecret(),
                delivery.getRequestBody(), delivery.getEvent(), delivery.getAttempt());
    }

    @Transactional
    public void recordSuccess(UUID deliveryId, int responseStatus) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        delivery.setResponseStatus(responseStatus);
        delivery.setSuccess(true);
        delivery.setError(null);
        delivery.setNextAttemptAt(null);
    }

    @Transactional
    public void recordFailure(UUID deliveryId, Integer responseStatus, String error) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        delivery.setResponseStatus(responseStatus);
        delivery.setError(error);
        if (delivery.getAttempt() >= properties.maxAttempts()) {
            delivery.setSuccess(false);
            delivery.setNextAttemptAt(null);
        } else {
            delivery.setSuccess(null);
            long minutes = properties.backoffMinutesForAttempt(delivery.getAttempt() + 1);
            delivery.setNextAttemptAt(Instant.now().plusSeconds(minutes * 60));
        }
    }
}
