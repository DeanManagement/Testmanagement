package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.apiKey.ApiKeyCreatedResponse;
import com.deanmanagement.testmanagement.project.internal.dto.apiKey.ApiKeyResponse;
import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ApiKey;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.repository.ApiKeyRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.UserService;
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
    private final ProjectMemberRepository projectMemberRepository;
    private final UserService userService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * A validated key with its scope resolved eagerly — safe to use outside the service
     * transaction (e.g. in the API-key filter). {@code projectKey == null} = legacy/global.
     */
    /**
     * @param projectId     scoped project's UUID, or null for a legacy global key
     * @param projectKey    scoped project's key, or null for a legacy global key
     * @param serviceUserId the user this key authenticates as (PRD-025 §3.2), or null for a legacy
     *                      global key, which has no project to hold a membership on
     * @param role          the key's role on its project
     */
    public record ValidatedKey(UUID id, String name, UUID projectId, String projectKey,
                               UUID serviceUserId, ProjectRole role) {}

    @Transactional
    public ApiKeyCreatedResponse create(CreateApiKeyRequest request) {
        // PRD-021 §4.2: every new key is bound to one project.
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.projectId()));

        ProjectRole role = request.role() == null ? ProjectRole.TESTER : request.role();
        if (role == ProjectRole.ADMIN) {
            throw new IllegalArgumentException("API keys may hold VIEWER or TESTER, not ADMIN");
        }

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
        apiKey.setRole(role);
        apiKey = apiKeyRepository.save(apiKey);

        // PRD-025 §3.2: the key authenticates as this user, which holds a real membership. Created
        // after the key so the address can key off its id and is guaranteed unique.
        attachServiceUser(apiKey, project, role);
        apiKey = apiKeyRepository.save(apiKey);

        return new ApiKeyCreatedResponse(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                rawKey,
                apiKey.getCreatedAt(),
                project.getId(),
                project.getName(),
                apiKey.getRole()
        );
    }

    /**
     * Creates the key's service user and its project membership. {@code .invalid} is reserved by
     * RFC 2606, so no identity provider can ever assert one of these addresses.
     */
    private void attachServiceUser(ApiKey apiKey, Project project, ProjectRole role) {
        User serviceUser = userService.createServiceAccount(
                "apikey-" + apiKey.getId() + "@service.invalid",
                "API key: " + apiKey.getName());

        ProjectMember member = new ProjectMember();
        member.setUser(serviceUser);
        member.setProject(project);
        member.setRole(role);
        projectMemberRepository.save(member);

        apiKey.setServiceUser(serviceUser);
    }

    public List<ApiKeyResponse> findAll() {
        return apiKeyRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Revokes the key and drops its service user's project membership, so even a request that races
     * past the filter is refused by {@link com.deanmanagement.testmanagement.project.internal.access.ProjectAccessService}.
     *
     * <p>The service user itself is kept: rows it authored still point at it through
     * {@code created_by}, and deleting it would leave that history unattributable.
     */
    @Transactional
    public void revoke(UUID id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", id));
        apiKey.setRevoked(true);
        apiKeyRepository.save(apiKey);

        if (apiKey.getServiceUser() != null && apiKey.getProject() != null) {
            projectMemberRepository
                    .findByUserIdAndProjectId(apiKey.getServiceUser().getId(), apiKey.getProject().getId())
                    .ifPresent(projectMemberRepository::delete);
        }
    }

    public Optional<ValidatedKey> validateKey(String rawKey) {
        String hash = sha256(rawKey);
        return apiKeyRepository.findByKeyHash(hash)
                .filter(key -> !key.isRevoked())
                .map(key -> new ValidatedKey(
                        key.getId(),
                        key.getName(),
                        key.getProject() == null ? null : key.getProject().getId(),
                        key.getProject() == null ? null : key.getProject().getKey(),
                        key.getServiceUser() == null ? null : key.getServiceUser().getId(),
                        key.getRole()));
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
                apiKey.getProject() == null ? null : apiKey.getProject().getName(),
                apiKey.getRole()
        );
    }

    /**
     * PRD-025 §3.2 backfill: gives a pre-existing project-scoped key the service user and
     * membership it now needs. Idempotent — a key that already has one is left alone.
     *
     * @return true if this call created the service user
     */
    @Transactional
    public boolean ensureServiceUser(UUID apiKeyId) {
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId).orElse(null);
        if (apiKey == null || apiKey.getProject() == null || apiKey.getServiceUser() != null) {
            return false;
        }
        attachServiceUser(apiKey, apiKey.getProject(), apiKey.getRole());
        apiKeyRepository.save(apiKey);
        return true;
    }

    /** @return ids of project-scoped keys that still have no service user. */
    public List<UUID> findKeyIdsWithoutServiceUser() {
        return apiKeyRepository.findByProjectIsNotNullAndServiceUserIsNullAndRevokedFalse().stream()
                .map(ApiKey::getId)
                .toList();
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
