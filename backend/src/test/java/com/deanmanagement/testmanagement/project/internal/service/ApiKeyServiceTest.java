package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.apiKey.ApiKeyCreatedResponse;
import com.deanmanagement.testmanagement.project.internal.dto.apiKey.ApiKeyResponse;
import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ApiKey;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.repository.ApiKeyRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private static final UUID KEY_ID = UUID.randomUUID();
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

    @Test
    void create_generatesKeyAndSaves() {
        var request = new CreateApiKeyRequest("CI Pipeline");

        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey saved = invocation.getArgument(0);
            saved.setId(KEY_ID);
            saved.setCreatedAt(NOW);
            return saved;
        });

        ApiKeyCreatedResponse result = apiKeyService.create(request);

        assertThat(result.name()).isEqualTo("CI Pipeline");
        assertThat(result.rawKey()).startsWith("tm_");
        assertThat(result.rawKey()).hasSize(43); // "tm_" (3) + 40 hex chars
        assertThat(result.keyPrefix()).hasSize(8);
        assertThat(result.id()).isEqualTo(KEY_ID);

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey saved = captor.getValue();
        assertThat(saved.getKeyHash()).isNotBlank();
        assertThat(saved.getKeyHash()).hasSize(64); // SHA-256 hex = 64 chars
        assertThat(saved.isRevoked()).isFalse();
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

        Optional<ApiKey> result = apiKeyService.validateKey(rawKey);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("CI Pipeline");
    }

    @Test
    void validateKey_revokedKey_returnsEmpty() {
        String rawKey = "tm_abc123";
        String hash = ApiKeyService.sha256(rawKey);
        ApiKey key = sampleApiKey();
        key.setKeyHash(hash);
        key.setRevoked(true);

        when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.of(key));

        Optional<ApiKey> result = apiKeyService.validateKey(rawKey);

        assertThat(result).isEmpty();
    }

    @Test
    void validateKey_unknownKey_returnsEmpty() {
        String rawKey = "tm_unknown";
        String hash = ApiKeyService.sha256(rawKey);

        when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.empty());

        Optional<ApiKey> result = apiKeyService.validateKey(rawKey);

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
