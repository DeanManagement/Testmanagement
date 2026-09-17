package com.deanmanagement.testmanagement.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The caching contract of {@link SpaServingTest}, but over a real embedded Tomcat rather than
 * MockMvc, and with the {@code HEAD} request that {@code curl -I} in the CI smoke test sends.
 *
 * <p>That difference is why this exists. {@code SpaServingTest} only ever sent GET and passed,
 * while HEAD on the shell was answered 403 by the security chain; the smoke test that would have
 * caught it had never got that far, because an earlier step in it always failed first.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class SpaCachingOverHttpTest {

    @LocalServerPort
    private int port;

    private HttpResponse<String> send(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void aDeepLinkFetchedWithGetMustBeRevalidated() throws Exception {
        HttpResponse<String> response = send("GET", "/dashboard");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-cache");
    }

    @Test
    void aDeepLinkFetchedWithHeadMustBeRevalidated() throws Exception {
        HttpResponse<String> response = send("HEAD", "/dashboard");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-cache");
    }

    /** Permitting HEAD on the shell must not open a side door into what the rules above it guard. */
    @Test
    void headDoesNotReachTheApiOrActuatorUnauthenticated() throws Exception {
        assertThat(send("HEAD", "/api/projects").statusCode()).isIn(401, 403);
        assertThat(send("HEAD", "/actuator/env").statusCode()).isIn(401, 403);
    }

    @Test
    void writesToTheShellAreStillDenied() throws Exception {
        assertThat(send("POST", "/dashboard").statusCode()).isIn(401, 403);
        assertThat(send("DELETE", "/dashboard").statusCode()).isIn(401, 403);
    }

    @Test
    void aHashedAssetIsImmutable() throws Exception {
        HttpResponse<String> response = send("HEAD", "/main-A1B2C3D4.js");

        assertThat(response.headers().firstValue("Cache-Control").orElse(""))
                .contains("immutable");
    }
}
