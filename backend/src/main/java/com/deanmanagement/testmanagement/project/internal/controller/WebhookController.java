package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.webhook.CreateWebhookRequest;
import com.deanmanagement.testmanagement.project.internal.dto.webhook.UpdateWebhookRequest;
import com.deanmanagement.testmanagement.project.internal.dto.webhook.WebhookDeliveryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.webhook.WebhookResponse;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.WebhookService;
import com.deanmanagement.testmanagement.shared.PageableUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/webhooks")
@Tag(name = "Webhooks", description = "Outbound webhook management (project admin only)")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @GetMapping
    @RequireProjectRole(ProjectRole.ADMIN)
    public List<WebhookResponse> findAll(@PathVariable UUID projectId) {
        return webhookService.findByProject(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectRole(ProjectRole.ADMIN)
    public WebhookResponse create(@PathVariable UUID projectId,
                                  @Valid @RequestBody CreateWebhookRequest request) {
        return webhookService.create(projectId, request);
    }

    @PutMapping("/{webhookId}")
    @RequireProjectRole(ProjectRole.ADMIN)
    public WebhookResponse update(@PathVariable UUID projectId,
                                  @PathVariable UUID webhookId,
                                  @Valid @RequestBody UpdateWebhookRequest request) {
        return webhookService.update(projectId, webhookId, request);
    }

    @DeleteMapping("/{webhookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectRole(ProjectRole.ADMIN)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID webhookId) {
        webhookService.delete(projectId, webhookId);
    }

    @PostMapping("/{webhookId}/test")
    @RequireProjectRole(ProjectRole.ADMIN)
    public WebhookDeliveryResponse test(@PathVariable UUID projectId, @PathVariable UUID webhookId) {
        return webhookService.sendTest(projectId, webhookId);
    }

    @GetMapping("/{webhookId}/deliveries")
    @RequireProjectRole(ProjectRole.ADMIN)
    public Page<WebhookDeliveryResponse> deliveries(@PathVariable UUID projectId,
                                                    @PathVariable UUID webhookId,
                                                    @PageableDefault(size = PageableUtils.DEFAULT_SIZE) Pageable pageable) {
        return webhookService.getDeliveries(projectId, webhookId, PageableUtils.normalize(pageable));
    }
}
