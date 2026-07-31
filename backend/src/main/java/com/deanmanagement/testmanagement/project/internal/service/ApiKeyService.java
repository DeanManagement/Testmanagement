package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.apiKey.ApiKeyCreatedResponse;
import com.deanmanagement.testmanagement.project.internal.dto.apiKey.ApiKeyResponse;
import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ApiKey;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.repository.ApiKeyRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiKeyService {

    private static final String KEY_PREFIX = "tm_";
    private static final int KEY_HEX_LENGTH = 40;

    private final ApiKeyRepository apiKeyRepository;
    private final ProjectRepository projectRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * A validated key with its scope resolved eagerly — safe to use outside the service
     * transaction (e.g. in the API-key filter). {@code projectKey == null} = legacy/global.
     */
    public record ValidatedKey(UUID id, String name, String projectKey) {}

    @Transactional
    public ApiKeyCreatedResponse create(CreateApiKeyRequest request) {
        // PRD-021 §4.2: every new key is bound to one project.
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.projectId()));

        String randomHex = generateRandomHex(KEY_HEX_LENGTH);
        String rawKey = KEY_PREFIX + randomHex;
        String hash = sha256(rawKey);
        String prefix = rawKey.substring(0, 8);

        ApiKey apiKey = new ApiKey();
        apiKey.setName(request.name());
        apiKey.setKeyHash(hash);
        apiKey.setKeyPrefix(prefix);
        apiKey.setRevoked(false);
        apiKey.setProject(project);

        apiKey = apiKeyRepository.save(apiKey);

        return new ApiKeyCreatedResponse(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                rawKey,
                apiKey.getCreatedAt(),
                project.getId(),
                project.getName()
        );
    }

    public List<ApiKeyResponse> findAll() {
        return apiKeyRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void revoke(UUID id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", id));
        apiKey.setRevoked(true);
        apiKeyRepository.save(apiKey);
    }

    public Optional<ValidatedKey> validateKey(String rawKey) {
        String hash = sha256(rawKey);
        return apiKeyRepository.findByKeyHash(hash)
                .filter(key -> !key.isRevoked())
                .map(key -> new ValidatedKey(
                        key.getId(),
                        key.getName(),
                        key.getProject() == null ? null : key.getProject().getKey()));
    }

    /** @return names of legacy keys without a project scope (for the startup warning). */
    public List<String> findGlobalKeyNames() {
        return apiKeyRepository.findByProjectIsNullAndRevokedFalse().stream()
                .map(ApiKey::getName)
                .toList();
    }

    @Transactional
    public void updateLastUsed(UUID id) {
        apiKeyRepository.findById(id).ifPresent(key -> {
            key.setLastUsedAt(Instant.now());
            apiKeyRepository.save(key);
        });
    }

    private ApiKeyResponse toResponse(ApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                apiKey.isRevoked(),
                apiKey.getLastUsedAt(),
                apiKey.getCreatedAt(),
                apiKey.getProject() == null ? null : apiKey.getProject().getId(),
                apiKey.getProject() == null ? null : apiKey.getProject().getName()
        );
    }

    private String generateRandomHex(int length) {
        byte[] bytes = new byte[length / 2];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
