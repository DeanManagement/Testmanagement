package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.buildserver.BuildServerProvider;
import com.deanmanagement.testmanagement.project.internal.buildserver.BuildServerProviderRegistry;
import com.deanmanagement.testmanagement.project.internal.buildserver.BuildServerUrlValidator;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.BuildServerConfigResponse;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.DiscoverWorkflowsResponse;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.DiscoverWorkflowsResponse.DiscoveredWorkflowResponse;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.SaveBuildServerConfigRequest;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerConfig;
import com.deanmanagement.testmanagement.project.internal.repository.BuildServerConfigRepository;
import com.deanmanagement.testmanagement.shared.crypto.AesGcmCipher;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owns the instance-wide build-server registry (PRD-024): storage, token encryption, and turning
 * a stored config into something an adapter can call.
 *
 * <p>The plaintext token exists only inside {@link #decrypt}'s return value for the duration of
 * one provider call. It is never held on the entity, returned in a DTO, or written to a log.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildServerConfigService {

    private static final int MAX_ERROR_LENGTH = 500;

    private final BuildServerConfigRepository configRepository;
    private final BuildServerProviderRegistry providerRegistry;
    private final AesGcmCipher secretCipher;
    private final BuildServerUrlValidator urlValidator;

    public List<BuildServerConfigResponse> list() {
        return configRepository.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(BuildServerConfigService::toResponse)
                .toList();
    }

    @Transactional
    public BuildServerConfigResponse create(SaveBuildServerConfigRequest request) {
        if (isBlank(request.apiToken())) {
            throw new IllegalArgumentException("An API token is required when registering a build server");
        }
        return toResponse(configRepository.save(apply(new BuildServerConfig(), request)));
    }

    @Transactional
    public BuildServerConfigResponse update(UUID id, SaveBuildServerConfigRequest request) {
        return toResponse(configRepository.save(apply(require(id), request)));
    }

    private BuildServerConfig apply(BuildServerConfig config, SaveBuildServerConfigRequest request) {
        providerRegistry.require(request.provider());
        urlValidator.validate(request.baseUrl());
        configRepository.findByName(request.name().trim())
                .filter(existing -> !existing.getId().equals(config.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "A build server named '" + request.name().trim() + "' already exists");
                });

        config.setName(request.name().trim());
        config.setProvider(request.provider());
        config.setBaseUrl(request.baseUrl().trim());
        config.setActive(request.active() == null || request.active());
        if (!isBlank(request.apiToken())) {
            config.setApiTokenEncrypted(secretCipher.encrypt(request.apiToken()));
            // A new token invalidates whatever the old one failed at.
            config.setLastError(null);
            config.setLastErrorAt(null);
        }
        return config;
    }

    @Transactional
    public void delete(UUID id) {
        // Workflows and assignments cascade away; pipeline_runs survive with their denormalised
        // workflow name and external URL, so run history stays readable.
        configRepository.delete(require(id));
    }

    /**
     * Calls the provider's connection check and records the outcome on the config, so a bad token
     * surfaces in the settings UI rather than only at the next trigger.
     */
    @Transactional
    public void testConnection(UUID id) {
        BuildServerConfig config = require(id);
        try {
            providerRegistry.require(config.getProvider()).testConnection(decrypt(config));
            clearError(config);
        } catch (RuntimeException e) {
            recordError(config, e.getMessage());
            throw e;
        }
    }

    /** Provider discovery for the admin's pick-list; "nothing to list" is a state, not an error. */
    @Transactional
    public DiscoverWorkflowsResponse discover(UUID id, String repoRef) {
        BuildServerConfig config = require(id);
        BuildServerProvider provider = providerRegistry.require(config.getProvider());
        try {
            List<DiscoveredWorkflowResponse> workflows = provider.discover(decrypt(config), repoRef)
                    .stream()
                    .map(w -> new DiscoveredWorkflowResponse(w.name(), w.repoRef(), w.workflowRef(),
                            w.defaultRef()))
                    .toList();
            clearError(config);
            return new DiscoverWorkflowsResponse(true, workflows);
        } catch (UnsupportedOperationException e) {
            return new DiscoverWorkflowsResponse(false, List.of());
        } catch (RuntimeException e) {
            recordError(config, e.getMessage());
            throw e;
        }
    }

    public BuildServerConfig require(UUID id) {
        return configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BuildServerConfig", id));
    }

    public BuildServerProvider.DecryptedConfig decrypt(BuildServerConfig config) {
        return new BuildServerProvider.DecryptedConfig(config,
                secretCipher.decrypt(config.getApiTokenEncrypted()));
    }

    @Transactional
    public void recordError(BuildServerConfig config, String message) {
        String trimmed = message == null ? "Unknown error"
                : message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
        config.setLastError(trimmed);
        config.setLastErrorAt(Instant.now());
        configRepository.save(config);
    }

    @Transactional
    public void clearError(BuildServerConfig config) {
        if (config.getLastError() != null) {
            config.setLastError(null);
            config.setLastErrorAt(null);
            configRepository.save(config);
        }
    }

    private static BuildServerConfigResponse toResponse(BuildServerConfig config) {
        return new BuildServerConfigResponse(
                config.getId(),
                config.getName(),
                config.getProvider(),
                config.getBaseUrl(),
                config.isActive(),
                config.getApiTokenEncrypted() != null && !config.getApiTokenEncrypted().isBlank(),
                config.getLastError(),
                config.getLastErrorAt(),
                config.getUpdatedAt());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
