package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.buildserver.SaveBuildServerConfigRequest;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerProviderType;
import com.deanmanagement.testmanagement.project.internal.repository.BuildServerConfigRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deliberately not {@code @Transactional}: a failed connection test records the error and rethrows,
 * and only real commits show whether the rollback that follows takes the recorded error with it.
 */
@SpringBootTest
@ActiveProfiles("dev")
class BuildServerErrorRecordingTest {

    /** Nothing listens on the discard port, so the connection is refused without any network. */
    private static final String UNREACHABLE_URL = "https://127.0.0.1:9";

    @Autowired
    private BuildServerConfigService configService;
    @Autowired
    private BuildServerConfigRepository configRepository;

    private UUID serverId;

    @AfterEach
    void deleteServer() {
        if (serverId != null) {
            configRepository.deleteById(serverId);
        }
    }

    @Test
    void failedConnectionTest_keepsTheErrorOnTheServer() {
        serverId = configService.create(new SaveBuildServerConfigRequest("Unreachable " + UUID.randomUUID(),
                BuildServerProviderType.GITLAB_CI, UNREACHABLE_URL, "token", true, null, null)).id();

        assertThatThrownBy(() -> configService.testConnection(serverId)).isInstanceOf(RuntimeException.class);

        assertThat(configRepository.findById(serverId).orElseThrow().getLastError()).isNotBlank();
    }
}
