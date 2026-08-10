package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.apiKey.ApiKeyCreatedResponse;
import com.deanmanagement.testmanagement.project.internal.dto.apiKey.ApiKeyResponse;
import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/api-keys")
@Tag(name = "API Keys", description = "API key management endpoints")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    public List<ApiKeyResponse> findAll() {
        return apiKeyService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyCreatedResponse create(@Valid @RequestBody CreateApiKeyRequest request) {
        return apiKeyService.create(request);
    }

    /**
     * Replaces the key's secret and returns the new one. The previous secret stops working
     * immediately, so anything using it — a CI pipeline, an MCP client — has to be updated.
     */
    @PostMapping("/{id}/rotate")
    public ApiKeyCreatedResponse rotate(@PathVariable UUID id) {
        return apiKeyService.rotate(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id) {
        apiKeyService.revoke(id);
    }
}
