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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private static final UUID KEY_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID SERVICE_USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    private ApiKey sampleApiKey() {
        ApiKey key = new ApiKey();
        key.setId(KEY_ID);
        key.setName("CI Pipeline");
        key.setKeyHash("abc123hash");
        key.setKeyPrefix("tm_abcde");
        key.setRevoked(false);
        key.setCreatedAt(NOW);
        return key;
    }

    private Project sampleProject() {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setName("Demo");
        project.setKey("DEMO");
        return project;
    }

    private User serviceUser() {
        User user = new User();
        user.setId(SERVICE_USER_ID);
        user.setServiceAccount(true);
        return user;
    }

    private void stubKeySave() {
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey saved = invocation.getArgument(0);
            saved.setId(KEY_ID);
            saved.setCreatedAt(NOW);
            return saved;
        });
    }

    @Test
    void create_generatesKeyAndSaves() {
        var request = new CreateApiKeyRequest("CI Pipeline", PROJECT_ID, null);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(sampleProject()));
        when(userService.createServiceAccount(any(), any())).thenReturn(serviceUser());
        stubKeySave();

        ApiKeyCreatedResponse result = apiKeyService.create(request);

        assertThat(result.name()).isEqualTo("CI Pipeline");
        assertThat(result.rawKey()).startsWith("tm_");
        assertThat(result.rawKey()).hasSize(43); // "tm_" (3) + 40 hex chars
        assertThat(result.keyPrefix()).hasSize(8);
        assertThat(result.id()).isEqualTo(KEY_ID);
        assertThat(result.role()).isEqualTo(ProjectRole.TESTER); // null request role defaults

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository, atLeastOnce()).save(captor.capture());
        ApiKey saved = captor.getValue();
        assertThat(saved.getKeyHash()).isNotBlank();
        assertThat(saved.getKeyHash()).hasSize(64); // SHA-256 hex = 64 chars
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getProject().getId()).isEqualTo(PROJECT_ID); // PRD-021: project-bound
    }

    /**
     * PRD-025 §3.2: without a service user and a membership, the key has no principal that
     * {@code @RequireProjectRole} can evaluate — which is exactly how it used to fail open.
     */
    @Test
    void create_bindsKeyToAServiceUserWithAProjectMembership() {
        var request = new CreateApiKeyRequest("Agent", PROJECT_ID, ProjectRole.VIEWER);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(sampleProject()));
        when(userService.createServiceAccount(any(), any())).thenReturn(serviceUser());
        stubKeySave();

        ApiKeyCreatedResponse result = apiKeyService.create(request);

        assertThat(result.role()).isEqualTo(ProjectRole.VIEWER);
        verify(userService).createServiceAccount(
                argThat(email -> email.startsWith("apikey-") && email.endsWith("@service.invalid")),
                eq("API key: Agent"));

        ArgumentCaptor<ProjectMember> memberCaptor = ArgumentCaptor.forClass(ProjectMember.class);
        verify(projectMemberRepository).save(memberCaptor.capture());
        ProjectMember member = memberCaptor.getValue();
        assertThat(member.getUser().getId()).isEqualTo(SERVICE_USER_ID);
        assertThat(member.getProject().getId()).isEqualTo(PROJECT_ID);
        assertThat(member.getRole()).isEqualTo(ProjectRole.VIEWER);
    }

    @Test
    void create_rejectsAdminRole() {
        var request = new CreateApiKeyRequest("Too powerful", PROJECT_ID, ProjectRole.ADMIN);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(sampleProject()));

        assertThatThrownBy(() -> apiKeyService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VIEWER or TESTER");

        verify(userService, never()).createServiceAccount(any(), any());
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void ensureServiceUser_isIdempotent() {
        ApiKey key = sampleApiKey();
        key.setProject(sampleProject());
        key.setServiceUser(serviceUser());
        when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(key));

        assertThat(apiKeyService.ensureServiceUser(KEY_ID)).isFalse();

        verify(userService, never()).createServiceAccount(any(), any());
        verify(projectMemberRepository, never()).save(any());
    }

    @Test
    void ensureServiceUser_backfillsAKeyThatHasNone() {
        ApiKey key = sampleApiKey();
        key.setProject(sampleProject());
        when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(key));
        when(userService.createServiceAccount(any(), any())).thenReturn(serviceUser());

        assertThat(apiKeyService.ensureServiceUser(KEY_ID)).isTrue();

        assertThat(key.getServiceUser().getId()).isEqualTo(SERVICE_USER_ID);
        verify(projectMemberRepository).save(any(ProjectMember.class));
    }

    @Test
    void ensureServiceUser_skipsLegacyProjectlessKeys() {
        ApiKey key = sampleApiKey(); // no project
        when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(key));

        assertThat(apiKeyService.ensureServiceUser(KEY_ID)).isFalse();

        verify(userService, never()).createServiceAccount(any(), any());
    }

    @Test
    void findAll_returnsMappedKeys() {
        ApiKey key = sampleApiKey();
        when(apiKeyRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(key));

        List<ApiKeyResponse> result = apiKeyService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("CI Pipeline");
        assertThat(result.getFirst().keyPrefix()).isEqualTo("tm_abcde");
        assertThat(result.getFirst().revoked()).isFalse();
    }

    @Test
    void revoke_setsRevokedTrue() {
        ApiKey key = sampleApiKey();
        when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(key)).thenReturn(key);

        apiKeyService.revoke(KEY_ID);

        assertThat(key.isRevoked()).isTrue();
        verify(apiKeyRepository).save(key);
    }

    /**
     * PRD-025 §3.2: dropping the membership means a request that races past the filter is still
     * refused, by ProjectAccessService rather than by the revoked flag.
     */
    @Test
    void revoke_dropsTheServiceUsersProjectMembership() {
        ApiKey key = sampleApiKey();
        key.setProject(sampleProject());
        key.setServiceUser(serviceUser());
        ProjectMember membership = new ProjectMember();
        when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(key)).thenReturn(key);
        when(projectMemberRepository.findByUserIdAndProjectId(SERVICE_USER_ID, PROJECT_ID))
                .thenReturn(Optional.of(membership));

        apiKeyService.revoke(KEY_ID);

        verify(projectMemberRepository).delete(membership);
    }

    @Test
    void revoke_notFound_throwsException() {
        when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.revoke(KEY_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void validateKey_validKey_returnsApiKey() {
        String rawKey = "tm_abc123";
        String hash = ApiKeyService.sha256(rawKey);
        ApiKey key = sampleApiKey();
        key.setKeyHash(hash);

        when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.of(key));

        Optional<ApiKeyService.ValidatedKey> result = apiKeyService.validateKey(rawKey);

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("CI Pipeline");
        assertThat(result.get().projectKey()).isNull(); // sample key is legacy/global
    }

    @Test
    void validateKey_revokedKey_returnsEmpty() {
        String rawKey = "tm_abc123";
        String hash = ApiKeyService.sha256(rawKey);
        ApiKey key = sampleApiKey();
        key.setKeyHash(hash);
        key.setRevoked(true);

        when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.of(key));

        Optional<ApiKeyService.ValidatedKey> result = apiKeyService.validateKey(rawKey);

        assertThat(result).isEmpty();
    }

    @Test
    void validateKey_unknownKey_returnsEmpty() {
        String rawKey = "tm_unknown";
        String hash = ApiKeyService.sha256(rawKey);

        when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.empty());

        Optional<ApiKeyService.ValidatedKey> result = apiKeyService.validateKey(rawKey);

        assertThat(result).isEmpty();
    }

    @Test
    void updateLastUsed_updatesTimestamp() {
        ApiKey key = sampleApiKey();
        when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(key)).thenReturn(key);

        apiKeyService.updateLastUsed(KEY_ID);

        assertThat(key.getLastUsedAt()).isNotNull();
        verify(apiKeyRepository).save(key);
    }

    @Test
    void sha256_producesConsistentHash() {
        String hash1 = ApiKeyService.sha256("test_input");
        String hash2 = ApiKeyService.sha256("test_input");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }

    @Test
    void sha256_differentInputs_produceDifferentHashes() {
        String hash1 = ApiKeyService.sha256("input_a");
        String hash2 = ApiKeyService.sha256("input_b");

        assertThat(hash1).isNotEqualTo(hash2);
    }
}
