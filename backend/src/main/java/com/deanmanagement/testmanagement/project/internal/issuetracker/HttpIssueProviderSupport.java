package com.deanmanagement.testmanagement.project.internal.issuetracker;

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
 * Shared HTTP plumbing for REST-based issue trackers: client construction, status-to-exception
 * mapping, and JSON helpers.
 *
 * <p>Subclasses supply the tracker's name, its auth header, and its URL shapes. The failure
 * taxonomy lives here on purpose — the service layer and the poller's backoff both depend on
 * "token rejected", "project missing" and "rate limited" being distinguishable regardless of which
 * tracker produced them.
 */
abstract class HttpIssueProviderSupport {

    /** Guards against a tracker streaming an unbounded body into memory. */
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    protected final IssueTrackerProperties properties;
    protected final ObjectMapper objectMapper;

    private volatile HttpClient httpClient;

    protected HttpIssueProviderSupport(IssueTrackerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Human-readable tracker name, used in error messages the user will see. */
    protected abstract String providerName();

    /** Adds whatever authentication header the tracker expects. */
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
                            // A redirect would re-send the auth header to whatever host the tracker
                            // names, so redirects are never followed.
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build();
                    httpClient = local;
                }
            }
        }
        return local;
    }

    protected HttpRequest.Builder request(IssueTrackerProvider.DecryptedConfig config, String url) {
        return authenticate(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.readTimeoutMs()))
                .header("Accept", "application/json"), config.token());
    }

    protected JsonNode getJson(IssueTrackerProvider.DecryptedConfig config, String url) {
        return send(request(config, url).GET().build(), config);
    }

    protected JsonNode postJson(IssueTrackerProvider.DecryptedConfig config, String url, Map<String, Object> payload) {
        HttpRequest httpRequest = request(config, url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(serialize(payload), StandardCharsets.UTF_8))
                .build();
        return send(httpRequest, config);
    }

    protected JsonNode send(HttpRequest request, IssueTrackerProvider.DecryptedConfig config) {
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
            throw new UpstreamServiceException(providerName() + " project '" + config.projectRef()
                    + "' was not found, or the token cannot see it");
        }
        if (status == 429) {
            throw new UpstreamServiceException(providerName() + " rate limit reached; try again shortly");
        }
        if (status < 200 || status >= 300) {
            throw new UpstreamServiceException(providerName() + " returned HTTP " + status);
        }

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

    protected String serialize(Map<String, Object> payload) {
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
     * Encodes a value destined for a URL <em>path</em> segment.
     *
     * <p>{@link URLEncoder} implements form encoding, where a space becomes {@code +}. In a path a
     * {@code +} is a literal plus, not a space, so it has to be promoted to {@code %20}. Slashes
     * still become {@code %2F}, which is what GitLab wants for a nested group path.
     */
    protected static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    protected static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    protected static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Splits the trailing {@code #<number>} the tool stores off an external id.
     *
     * @throws IllegalArgumentException if what follows is not a number, which is the caller's error
     *         rather than the tracker's — hence not an upstream failure.
     */
    protected String issueNumber(String externalId) {
        int hash = externalId.lastIndexOf('#');
        String number = hash >= 0 ? externalId.substring(hash + 1) : externalId;
        if (number.isBlank() || !number.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException(
                    "Not a valid " + providerName() + " issue reference: " + externalId);
        }
        return number;
    }
}
