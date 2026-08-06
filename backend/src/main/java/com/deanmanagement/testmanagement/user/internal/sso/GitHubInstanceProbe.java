package com.deanmanagement.testmanagement.user.internal.sso;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Stands in for the discovery fetch that "Test connection" performs on an OIDC issuer.
 *
 * <p>GitHub publishes nothing to discover, so building its registration touches the network at all —
 * meaning a typo in the URL, a GitHub Enterprise Server that is only reachable over VPN, or a base
 * URL missing its {@code /api/v3} path would all pass validation and only surface mid-login, after
 * the user has already approved the app. This asks the API root whether it is there.
 *
 * <p>An authentication challenge counts as success: a GHES instance in private mode answers 401 to
 * an anonymous request, and that still proves the host, TLS and path are right. A 404 does not —
 * that is the shape of a base URL pointing at something which is not a GitHub API.
 */
@Component
public class GitHubInstanceProbe {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * @throws IllegalStateException if the instance cannot be reached or does not look like GitHub;
     *         the caller records the message against the provider row
     */
    public void probe(String baseUrl) {
        // /user is the endpoint the login itself will call, so this tests the URL that matters
        // rather than one that merely shares a host.
        String url = GitHubEndpoints.forBaseUrl(baseUrl).userInfoUri();
        int status;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(READ_TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();
            status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while contacting " + url);
        } catch (Exception e) {
            throw new IllegalStateException("Could not reach " + url);
        }

        if (status == 404) {
            throw new IllegalStateException(url + " returned 404 — check the GitHub URL");
        }
        if (status >= 500) {
            throw new IllegalStateException(url + " returned HTTP " + status);
        }
    }
}
