package com.deanmanagement.testmanagement.project.internal.buildserver;

import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Shared HTTP plumbing for build-server adapters: client construction, status-to-exception
 * mapping, and JSON helpers. Mirrors {@code HttpIssueProviderSupport} (PRD-010), with raw-response
 * access added because two providers need more than a JSON body — the Actions dispatch endpoints
 * answer 204 with no content, and Jenkins identifies the queued build via a {@code Location}
 * header.
 *
 * <p>The failure taxonomy lives here on purpose — the trigger service and the poller's backoff
 * both depend on "token rejected", "not found" and "rate limited" being distinguishable whichever
 * server produced them.
 */
abstract class HttpBuildProviderSupport {

    /** Guards against a server streaming an unbounded body into memory. */
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    protected final BuildServerProperties properties;
    protected final ObjectMapper objectMapper;

    private volatile HttpClient httpClient;

    protected HttpBuildProviderSupport(BuildServerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Human-readable provider name, used in error messages the user will see. */
    protected abstract String providerName();

    /** Adds whatever authentication header the server expects. */
    protected abstract HttpRequest.Builder authenticate(HttpRequest.Builder builder, String token);

    // ---- HTTP -------------------------------------------------------------

    private HttpClient client() {
        HttpClient local = httpClient;
        if (local == null) {
            synchronized (this) {
                local = httpClient;
                if (local == null) {
                    local = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                            // A redirect would re-send the auth header to whatever host the server
                            // names, so redirects are never followed.
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build();
                    httpClient = local;
                }
            }
        }
        return local;
    }

    protected HttpRequest.Builder request(BuildServerProvider.DecryptedConfig config, String url) {
        return authenticate(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.readTimeoutMs()))
                .header("Accept", "application/json"), config.token());
    }

    protected JsonNode getJson(BuildServerProvider.DecryptedConfig config, String url) {
        return parseBody(send(request(config, url).GET().build(), config));
    }

    protected JsonNode postJson(BuildServerProvider.DecryptedConfig config, String url,
                                Map<String, Object> payload) {
        return parseBody(send(request(config, url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(serialize(payload), StandardCharsets.UTF_8))
                .build(), config));
    }

    /**
     * Sends and applies the shared failure taxonomy, returning the raw response for callers that
     * need the status code or headers rather than a JSON body.
     */
    protected HttpResponse<String> send(HttpRequest request, BuildServerProvider.DecryptedConfig config) {
        HttpResponse<String> response;
        try {
            response = client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamServiceException("Interrupted while calling " + providerName());
        } catch (Exception e) {
            // The message may name the host but never the token, which lives only in a header.
            throw new UpstreamServiceException(
                    "Could not reach " + providerName() + " at " + config.baseUrl(), e);
        }

        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new UpstreamServiceException(
                    providerName() + " rejected the configured access token (HTTP " + status + ")");
        }
        if (status == 404) {
            throw new UpstreamServiceException(providerName()
                    + " could not find the requested resource — check the repository/workflow reference,"
                    + " or the token cannot see it");
        }
        if (status == 429) {
            throw new UpstreamServiceException(providerName() + " rate limit reached; try again shortly");
        }
        if (status < 200 || status >= 300) {
            throw new UpstreamServiceException(providerName() + " returned HTTP " + status);
        }
        return response;
    }

    /**
     * Like {@link #send} but hands one specific error status back to the caller instead of
     * throwing. Exists for the Actions dispatch endpoints, where 422 means "this workflow does
     * not declare these inputs" and the adapter wants to retry without them.
     */
    protected HttpResponse<String> sendTolerating(HttpRequest request,
                                                  BuildServerProvider.DecryptedConfig config,
                                                  int toleratedStatus) {
        HttpResponse<String> response;
        try {
            response = client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamServiceException("Interrupted while calling " + providerName());
        } catch (Exception e) {
            throw new UpstreamServiceException(
                    "Could not reach " + providerName() + " at " + config.baseUrl(), e);
        }
        if (response.statusCode() == toleratedStatus) {
            return response;
        }
        return checked(response, config);
    }

    private HttpResponse<String> checked(HttpResponse<String> response,
                                         BuildServerProvider.DecryptedConfig config) {
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new UpstreamServiceException(
                    providerName() + " rejected the configured access token (HTTP " + status + ")");
        }
        if (status == 404) {
            throw new UpstreamServiceException(providerName()
                    + " could not find the requested resource — check the repository/workflow reference,"
                    + " or the token cannot see it");
        }
        if (status == 429) {
            throw new UpstreamServiceException(providerName() + " rate limit reached; try again shortly");
        }
        if (status < 200 || status >= 300) {
            throw new UpstreamServiceException(providerName() + " returned HTTP " + status);
        }
        return response;
    }

    protected JsonNode parseBody(HttpResponse<String> response) {
        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new UpstreamServiceException(providerName() + " returned an empty response");
        }
        if (body.length() > MAX_RESPONSE_BYTES) {
            throw new UpstreamServiceException(providerName() + " response was too large to process");
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            // Deliberately not the parser's message, which would echo response content.
            throw new UpstreamServiceException(providerName() + " returned a malformed response");
        }
    }

    // ---- Helpers ----------------------------------------------------------

    protected String serialize(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new UpstreamServiceException("Could not build the " + providerName() + " request body");
        }
    }

    protected static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    /** Encodes a query-string value, where {@code +} correctly denotes a space. */
    protected static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Encodes a value destined for a URL <em>path</em> segment: form encoding's {@code +} for a
     * space must be promoted to {@code %20}. Slashes still become {@code %2F}, which is what
     * GitLab wants for a nested group path.
     */
    protected static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    protected static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Splits an {@code owner/repo} reference into its two path segments, each encoded separately.
     *
     * @throws IllegalArgumentException on any other shape — a nested GitLab-style path is caller
     *         error here, better rejected up front than 404-ing later.
     */
    protected String[] ownerRepo(String repoRef) {
        String[] parts = repoRef.split("/");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException(
                    providerName() + " repositories are addressed as owner/repo, got: " + repoRef);
        }
        return new String[]{encodePath(parts[0]), encodePath(parts[1])};
    }
}
