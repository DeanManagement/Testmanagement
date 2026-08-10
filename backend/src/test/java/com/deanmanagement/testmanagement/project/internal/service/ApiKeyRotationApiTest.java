package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.apiKey.ApiKeyCreatedResponse;
import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ApiKey;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ApiKeyRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rotation replaces a key's secret and nothing else.
 *
 * <p>The identity half is the part worth guarding. A key's service user is what {@code created_by}
 * points at on everything it has written and what its MCP activity log is keyed on, so rotating by
 * revoke-and-recreate would split that history in two and attribute the first half to a key that no
 * longer exists. These tests pin the invariant so a later refactor cannot quietly reintroduce it.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ApiKeyRotationApiTest {

    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private ApiKeyRepository apiKeyRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    private Project project;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setName("Rotation");
        project.setKey("ROT" + Integer.toHexString(new java.util.Random().nextInt(0xFFF)));
        project = projectRepository.save(project);
    }

    private ApiKeyCreatedResponse createKey() {
        return apiKeyService.create(
                new CreateApiKeyRequest("ci", project.getId(), ProjectRole.TESTER));
    }

    @Test
    void theOldSecretStopsWorkingAndTheNewOneStarts() {
        ApiKeyCreatedResponse original = createKey();
        assertThat(apiKeyService.validateKey(original.rawKey())).isPresent();

        ApiKeyCreatedResponse rotated = apiKeyService.rotate(original.id());

        assertThat(rotated.rawKey()).isNotEqualTo(original.rawKey());
        assertThat(apiKeyService.validateKey(original.rawKey()))
                .as("the previous secret must be dead the moment it is replaced")
                .isEmpty();
        assertThat(apiKeyService.validateKey(rotated.rawKey())).isPresent();
    }

    @Test
    void rotationKeepsTheKeysIdentitySoItsHistoryStaysAttached() {
        ApiKeyCreatedResponse original = createKey();
        ApiKey before = apiKeyRepository.findById(original.id()).orElseThrow();
        UUID serviceUserId = before.getServiceUser().getId();

        ApiKeyCreatedResponse rotated = apiKeyService.rotate(original.id());

        assertThat(rotated.id()).isEqualTo(original.id());
        assertThat(rotated.projectId()).isEqualTo(project.getId());
        assertThat(rotated.role()).isEqualTo(ProjectRole.TESTER);

        ApiKey after = apiKeyRepository.findById(original.id()).orElseThrow();
        assertThat(after.getServiceUser().getId())
                .as("created_by on everything this key wrote points here")
                .isEqualTo(serviceUserId);
        assertThat(projectMemberRepository.findByUserIdAndProjectId(serviceUserId, project.getId()))
                .as("and its project membership, which is what authorizes it")
                .isPresent();
    }

    @Test
    void theNewSecretAuthenticatesAsTheSameServiceUser() {
        ApiKeyCreatedResponse original = createKey();
        UUID serviceUserId = apiKeyService.validateKey(original.rawKey()).orElseThrow().serviceUserId();

        ApiKeyCreatedResponse rotated = apiKeyService.rotate(original.id());

        assertThat(apiKeyService.validateKey(rotated.rawKey()).orElseThrow().serviceUserId())
                .isEqualTo(serviceUserId);
    }

    @Test
    void rotationRecordsWhenItHappenedAndClearsLastUsed() {
        ApiKeyCreatedResponse original = createKey();
        apiKeyService.updateLastUsed(original.id());
        assertThat(apiKeyRepository.findById(original.id()).orElseThrow().getLastUsedAt()).isNotNull();

        apiKeyService.rotate(original.id());

        ApiKey after = apiKeyRepository.findById(original.id()).orElseThrow();
        assertThat(after.getRotatedAt()).isNotNull();
        assertThat(after.getLastUsedAt())
                .as("cleared so that its reappearance means the new secret has been picked up")
                .isNull();
    }

    @Test
    void aRevokedKeyCannotBeRotatedBackToLife() {
        ApiKeyCreatedResponse original = createKey();
        apiKeyService.revoke(original.id());

        assertThatThrownBy(() -> apiKeyService.rotate(original.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revoked");
        // Revocation removed the membership; rotating must not have restored it.
        UUID serviceUserId = apiKeyRepository.findById(original.id()).orElseThrow()
                .getServiceUser().getId();
        assertThat(projectMemberRepository.findByUserIdAndProjectId(serviceUserId, project.getId()))
                .isEmpty();
    }

    @Test
    void rotatingAKeyThatDoesNotExistIsNotFound() {
        assertThatThrownBy(() -> apiKeyService.rotate(UUID.randomUUID()))
                .isInstanceOf(com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException.class);
    }

    @Test
    void aRotatedSecretIsAsStrongAsAFreshOne() {
        // create and rotate share one generator so a rotation cannot quietly issue a weaker key.
        ApiKeyCreatedResponse original = createKey();
        ApiKeyCreatedResponse rotated = apiKeyService.rotate(original.id());

        assertThat(rotated.rawKey()).hasSameSizeAs(original.rawKey()).startsWith("tm_");
        assertThat(rotated.keyPrefix()).hasSize(8).isEqualTo(rotated.rawKey().substring(0, 8));
    }
}
