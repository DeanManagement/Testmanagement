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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

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

        ApiKey apiKey = new ApiKey();
        apiKey.setName(request.name());
        String rawKey = assignNewSecret(apiKey);
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
     * Replaces a key's secret, keeping everything else about it.
     *
     * <p>Rotation is a change of secret, not a new key: the row, the project, the role and above
     * all the service user stay as they were. That last part is what makes the audit trail survive
     * — {@code created_by} on every test case the key has written, and every row in its MCP
     * activity log, points at that service user. Revoke-and-recreate would have split the history
     * in two and attributed the first half to a key that no longer exists.
     *
     * <p>The old secret stops working immediately, so whatever is using it has to be updated. The
     * last-used timestamp is cleared, which gives the admin the signal that matters afterwards:
     * once it is populated again, the new secret has been picked up.
     *
     * @return the new key, shown once and never again
     */
    @Transactional
    public ApiKeyCreatedResponse rotate(UUID id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", id));
        if (apiKey.isRevoked()) {
            // Rotating would silently bring it back to life, along with a project membership that
            // revocation deliberately removed.
            throw new IllegalArgumentException(
                    "This key is revoked. Create a new one rather than rotating it.");
        }

        String rawKey = assignNewSecret(apiKey);
        apiKey.setRotatedAt(Instant.now());
        apiKey.setLastUsedAt(null);
        apiKey = apiKeyRepository.save(apiKey);

        log.info("Rotated API key '{}' ({}) — the previous secret is no longer accepted",
                apiKey.getName(), apiKey.getKeyPrefix());

        return new ApiKeyCreatedResponse(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                rawKey,
                apiKey.getCreatedAt(),
                apiKey.getProject() == null ? null : apiKey.getProject().getId(),
                apiKey.getProject() == null ? null : apiKey.getProject().getName(),
                apiKey.getRole());
    }

    /**
     * Mints a secret and stamps its hash and prefix onto the key. Shared by create and rotate so
     * the two cannot drift — a rotation that generated a weaker secret than a fresh key would be a
     * quiet downgrade.
     *
     * @return the raw key, which exists only in this return value and is never stored
     */
    private String assignNewSecret(ApiKey apiKey) {
        String rawKey = KEY_PREFIX + generateRandomHex(KEY_HEX_LENGTH);
        apiKey.setKeyHash(sha256(rawKey));
        apiKey.setKeyPrefix(rawKey.substring(0, 8));
        return rawKey;
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
                apiKey.getRotatedAt(),
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
