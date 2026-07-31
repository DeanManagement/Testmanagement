package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.webhook.CreateWebhookRequest;
import com.deanmanagement.testmanagement.project.internal.dto.webhook.UpdateWebhookRequest;
import com.deanmanagement.testmanagement.project.internal.dto.webhook.WebhookDeliveryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.webhook.WebhookResponse;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.Webhook;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookDelivery;
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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WebhookService {

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
        urlValidator.validate(request.url());

        Webhook webhook = new Webhook();
        webhook.setProject(project);
        webhook.setUrl(request.url());
        webhook.setSecret(request.secret());
        webhook.setEvents(request.events());
        webhook.setActive(request.active() == null || request.active());
        return toResponse(webhookRepository.save(webhook));
    }

    @Transactional
    public WebhookResponse update(UUID projectId, UUID webhookId, UpdateWebhookRequest request) {
        Webhook webhook = requireWebhook(projectId, webhookId);
        urlValidator.validate(request.url());

        webhook.setUrl(request.url());
        if (request.secret() != null && !request.secret().isBlank()) {
            webhook.setSecret(request.secret());
        }
        webhook.setEvents(request.events());
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

    private WebhookResponse toResponse(Webhook webhook) {
        return new WebhookResponse(
                webhook.getId(),
                webhook.getUrl(),
                webhook.getEvents(),
                webhook.isActive(),
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
