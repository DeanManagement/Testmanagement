package com.deanmanagement.testmanagement.project.internal.config;

import com.deanmanagement.testmanagement.project.internal.entity.ApiKey;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.repository.ApiKeyRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A wrong URL under /api/external/** used to come back as an empty 403, because the unmatched
 * request is re-dispatched to /error and that dispatch fell through to {@code anyRequest().denyAll()}
 * — so "no such endpoint" was indistinguishable from "your API key was rejected".
 *
 * <p>This needs a real servlet container: MockMvc does not perform the ERROR dispatch, so the bug
 * is invisible to a MockMvc test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ExternalApiErrorDispatchTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ApiKeyRepository apiKeyRepository;

    private static final String RAW_KEY = "tm_error_dispatch_probe";

    @BeforeEach
    void seedScopedKey() {
        Project project = projectRepository.findByKey("EDT").orElseGet(() -> {
            Project p = new Project();
            p.setKey("EDT");
            p.setName("Error Dispatch Test");
            return projectRepository.save(p);
        });

        String hash = sha256(RAW_KEY);
        if (apiKeyRepository.findByKeyHash(hash).isEmpty()) {
            ApiKey key = new ApiKey();
            key.setName("error-dispatch-probe");
            key.setKeyHash(hash);
            key.setKeyPrefix(RAW_KEY.substring(0, 8));
            key.setProject(project);
            apiKeyRepository.save(key);
        }
    }

    @Test
    void unmatchedExternalUrl_returns404_not403() throws Exception {
        // The shape the user hit: a run reference where the /allure-report suffix belongs, which
        // matches no handler at all.
        assertThat(post("/api/external/projects/EDT/test-runs/EDT-Run-1", RAW_KEY)).isEqualTo(404);
    }

    @Test
    void wrongProjectInUrl_stillReturns403() throws Exception {
        // The scope check must keep rejecting a key used against another project — permitting
        // /error must not have widened anything.
        assertThat(post("/api/external/projects/OTHER/test-runs", RAW_KEY)).isEqualTo(403);
    }

    @Test
    void missingKey_stillReturns401() throws Exception {
        assertThat(post("/api/external/projects/EDT/test-runs", null)).isEqualTo(401);
    }

    private int post(String path, String apiKey) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (apiKey != null) {
            request.header("X-API-Key", apiKey);
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
