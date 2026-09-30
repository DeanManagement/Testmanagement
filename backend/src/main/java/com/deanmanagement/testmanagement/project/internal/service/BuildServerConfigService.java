package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.buildserver.BuildServerProvider;
import com.deanmanagement.testmanagement.project.internal.buildserver.BuildServerProviderRegistry;
import com.deanmanagement.testmanagement.project.internal.buildserver.BuildServerUrlValidator;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.BuildServerConfigResponse;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.DiscoverWorkflowsResponse;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.DiscoverWorkflowsResponse.DiscoveredWorkflowResponse;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.DiscoveryTarget;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.SaveBuildServerConfigRequest;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerProviderType;
import com.deanmanagement.testmanagement.project.internal.repository.BuildServerConfigRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.shared.crypto.AesGcmCipher;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
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
    private final ProjectRepository projectRepository;
    private final PlatformTransactionManager transactionManager;

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
        if (request.provider() == BuildServerProviderType.AZURE_DEVOPS) {
            requireAzureOrganizationUrl(request.baseUrl().trim());
        }
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
        config.setApiVersion(request.provider() == BuildServerProviderType.AZURE_DEVOPS
                ? trimToNull(request.apiVersion()) : null);
        applyProjectScope(config, request.projectIds());
        if (!isBlank(request.apiToken())) {
            config.setApiTokenEncrypted(secretCipher.encrypt(request.apiToken()));
            // A new token invalidates whatever the old one failed at.
            config.setLastError(null);
            config.setLastErrorAt(null);
        }
        return config;
    }

    /**
     * On Azure DevOps Services the URL ends at the organization. A project appended to it breaks
     * every call, and the error Azure returns does not say why; the project belongs in the workflow.
     * Server URLs ({@code …/tfs/Collection}) have more segments and are left alone.
     */
    private static void requireAzureOrganizationUrl(String baseUrl) {
        URI uri = URI.create(baseUrl);
        String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("^/+|/+$", "");
        if ("dev.azure.com".equalsIgnoreCase(uri.getHost()) && path.contains("/")) {
            String organization = path.substring(0, path.indexOf('/'));
            throw new IllegalArgumentException("The URL must end at the organization: https://dev.azure.com/"
                    + organization + ". Enter the project in each workflow's repository reference instead.");
        }
    }

    /**
     * Narrowing the scope keeps assignments to projects left outside it; they stop counting
     * (assignment queries check the scope) and count again if the project is added back.
     */
    private void applyProjectScope(BuildServerConfig config, List<UUID> projectIds) {
        config.setAllProjects(projectIds == null);
        config.getProjectIds().clear();
        if (projectIds == null) {
            return;
        }
        for (UUID projectId : new HashSet<>(projectIds)) {
            if (!projectRepository.existsById(projectId)) {
                throw new ResourceNotFoundException("Project", projectId);
            }
            config.getProjectIds().add(projectId);
        }
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
    public DiscoverWorkflowsResponse discover(UUID id, DiscoveryTarget target, String repoRef, String workflowRef) {
        BuildServerConfig config = require(id);
        BuildServerProvider provider = providerRegistry.require(config.getProvider());
        try {
            BuildServerProvider.DecryptedConfig decrypted = decrypt(config);
            List<BuildServerProvider.DiscoveredWorkflow> found = switch (target) {
                case REPOSITORIES -> provider.discoverRepositories(decrypted);
                case WORKFLOWS -> provider.discover(decrypted, repoRef);
                case BRANCHES -> provider.discoverBranches(decrypted, repoRef, workflowRef);
            };
            List<DiscoveredWorkflowResponse> workflows = found
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

    /**
     * Commits in a transaction of its own: callers record the error and rethrow, and the rollback
     * that follows must not take the recorded error with it.
     */
    public void recordError(BuildServerConfig config, String message) {
        String trimmed = message == null ? "Unknown error"
                : message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
        Instant now = Instant.now();
        config.setLastError(trimmed);
        config.setLastErrorAt(now);
        TransactionTemplate ownTransaction = new TransactionTemplate(transactionManager);
        ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        ownTransaction.executeWithoutResult(status -> configRepository.findById(config.getId())
                .ifPresent(stored -> {
                    stored.setLastError(trimmed);
                    stored.setLastErrorAt(now);
                }));
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
                config.getUpdatedAt(),
                config.getApiVersion(),
                config.isAllProjects() ? null : config.getProjectIds().stream().sorted().toList());
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
