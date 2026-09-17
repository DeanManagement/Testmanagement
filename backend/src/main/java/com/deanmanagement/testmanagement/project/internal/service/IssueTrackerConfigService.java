package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.issuetracker.IssueTrackerConfigResponse;
import com.deanmanagement.testmanagement.project.internal.dto.issuetracker.IssueTrackerStatusResponse;
import com.deanmanagement.testmanagement.project.internal.dto.issuetracker.SaveIssueTrackerConfigRequest;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.project.internal.issuetracker.IssueTrackerProvider;
import com.deanmanagement.testmanagement.project.internal.issuetracker.JiraIssueProvider;
import com.deanmanagement.testmanagement.project.internal.issuetracker.IssueTrackerProviderRegistry;
import com.deanmanagement.testmanagement.project.internal.issuetracker.IssueTrackerTokenCipher;
import com.deanmanagement.testmanagement.project.internal.issuetracker.IssueTrackerUrlValidator;
import com.deanmanagement.testmanagement.project.internal.repository.IssueTrackerConfigRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns a project's issue-tracker connection (PRD-010): storage, token encryption, and turning a
 * stored config into something an adapter can call.
 *
 * <p>The plaintext token exists only inside {@link #decrypt}'s return value for the duration of one
 * provider call. It is never held on the entity, returned in a DTO, or written to a log.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueTrackerConfigService {

    private static final int MAX_ERROR_LENGTH = 500;

    private final IssueTrackerConfigRepository configRepository;
    private final IssueTrackerProviderRegistry providerRegistry;
    private final IssueTrackerTokenCipher tokenCipher;
    private final IssueTrackerUrlValidator urlValidator;

    public Optional<IssueTrackerConfigResponse> find(UUID projectId) {
        return configRepository.findByProjectId(projectId).map(IssueTrackerConfigService::toResponse);
    }

    /**
     * The minimum any project member may know: whether linking issues is possible here, and to
     * which kind of tracker. Deliberately excludes the base URL, project reference and error state,
     * which stay admin-only.
     */
    public IssueTrackerStatusResponse status(UUID projectId) {
        return activeConfig(projectId)
                .map(config -> new IssueTrackerStatusResponse(true, config.getProvider()))
                .orElseGet(() -> new IssueTrackerStatusResponse(false, null));
    }

    @Transactional
    public IssueTrackerConfigResponse save(UUID projectId, SaveIssueTrackerConfigRequest request) {
        providerRegistry.require(request.provider());
        urlValidator.validate(request.baseUrl());

        IssueTrackerConfig config = configRepository.findByProjectId(projectId)
                .orElseGet(() -> {
                    IssueTrackerConfig created = new IssueTrackerConfig();
                    created.setProjectId(projectId);
                    return created;
                });

        boolean isNew = config.getId() == null;
        if (isNew && isBlank(request.apiToken())) {
            throw new IllegalArgumentException("An API token is required when configuring a tracker");
        }

        config.setAuthUsername(authUsernameFor(request));
        config.setProvider(request.provider());
        config.setBaseUrl(request.baseUrl().trim());
        config.setProjectRef(request.projectRef().trim());
        config.setActive(request.active() == null || request.active());
        if (!isBlank(request.apiToken())) {
            config.setApiTokenEncrypted(tokenCipher.encrypt(request.apiToken()));
            // A new token invalidates whatever the old one failed at.
            config.setLastError(null);
            config.setLastErrorAt(null);
        }

        return toResponse(configRepository.save(config));
    }

    @Transactional
    public void delete(UUID projectId) {
        // Existing issue_links are intentionally left behind: they keep their own url and provider,
        // so previously filed defects remain visible and clickable after the config is removed.
        configRepository.deleteByProjectId(projectId);
    }

    /**
     * Calls the provider's connection check and records the outcome on the config, so a bad token
     * surfaces in the settings UI rather than only at the next search.
     */
    @Transactional
    public void testConnection(UUID projectId) {
        IssueTrackerConfig config = requireConfig(projectId);
        try {
            providerRegistry.require(config.getProvider()).testConnection(decrypt(config));
            clearError(config);
        } catch (RuntimeException e) {
            recordError(config, e.getMessage());
            throw e;
        }
    }

    /** The config for outbound use, or empty when the project has none or has disabled it. */
    public Optional<IssueTrackerConfig> activeConfig(UUID projectId) {
        return configRepository.findByProjectId(projectId).filter(IssueTrackerConfig::isActive);
    }

    public IssueTrackerConfig requireActiveConfig(UUID projectId) {
        return activeConfig(projectId).orElseThrow(() ->
                new IllegalArgumentException("This project has no active issue tracker configured"));
    }

    public IssueTrackerProvider.DecryptedConfig decrypt(IssueTrackerConfig config) {
        return new IssueTrackerProvider.DecryptedConfig(config, tokenCipher.decrypt(config.getApiTokenEncrypted()));
    }

    /** Runs a provider call, recording success or failure on the config for the settings UI. */
    @Transactional
    public <T> T call(IssueTrackerConfig config, java.util.function.Function<IssueTrackerProvider.DecryptedConfig, T> action) {
        try {
            T result = action.apply(decrypt(config));
            clearError(config);
            return result;
        } catch (UpstreamServiceException e) {
            recordError(config, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void recordError(IssueTrackerConfig config, String message) {
        String trimmed = message == null ? "Unknown error"
                : message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
        config.setLastError(trimmed);
        config.setLastErrorAt(Instant.now());
        configRepository.save(config);
    }

    private void clearError(IssueTrackerConfig config) {
        if (config.getLastError() != null) {
            config.setLastError(null);
            config.setLastErrorAt(null);
            configRepository.save(config);
        }
    }

    private IssueTrackerConfig requireConfig(UUID projectId) {
        return configRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("IssueTrackerConfig", projectId));
    }

    private static IssueTrackerConfigResponse toResponse(IssueTrackerConfig config) {
        return new IssueTrackerConfigResponse(
                config.getId(),
                config.getProvider(),
                config.getBaseUrl(),
                config.getProjectRef(),
                config.isActive(),
                config.getApiTokenEncrypted() != null && !config.getApiTokenEncrypted().isBlank(),
                config.getLastError(),
                config.getLastErrorAt(),
                config.getUpdatedAt(),
                config.getAuthUsername());
    }

    /**
     * The account email, which only Jira Cloud uses: its Basic auth is {@code email:apiToken}, so
     * the token alone cannot authenticate (PRD-029 §3.1). Required there and dropped everywhere
     * else, so switching a config to another tracker does not leave a stale address behind.
     */
    private static String authUsernameFor(SaveIssueTrackerConfigRequest request) {
        boolean isJiraCloud = request.provider() == IssueTrackerProviderType.JIRA
                && JiraIssueProvider.isAtlassianCloud(request.baseUrl());
        if (!isJiraCloud) {
            return null;
        }
        if (isBlank(request.authUsername())) {
            throw new IllegalArgumentException(
                    "Jira Cloud needs the account email that the API token belongs to");
        }
        return request.authUsername().trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
