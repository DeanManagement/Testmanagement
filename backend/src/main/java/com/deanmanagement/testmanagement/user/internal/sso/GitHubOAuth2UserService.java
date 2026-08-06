package com.deanmanagement.testmanagement.user.internal.sso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Assembles a GitHub login into the same shape an OIDC provider would have produced.
 *
 * <p>Two things have to be repaired before {@link SsoLoginService} can treat it uniformly:
 *
 * <ul>
 *   <li><strong>The email.</strong> {@code GET /user} returns {@code email} only when the user has
 *       made one public, and says nothing about whether it is verified. The addresses GitHub has
 *       actually confirmed live at {@code GET /user/emails}, behind the {@code user:email} scope.
 *   <li><strong>The display name.</strong> {@code name} is optional on GitHub and frequently null,
 *       so the login is used instead — an account showing a raw numeric id helps nobody.
 * </ul>
 *
 * <p>An unverified address is <em>removed</em> rather than passed along. GitHub lets anyone put any
 * string in their public profile email, so keeping it would let a stranger have an account
 * provisioned under someone else's address and quietly reserve it. Only what GitHub has confirmed
 * survives, and it is marked {@code email_verified} so the account-linking rules can rely on it.
 *
 * <p>Applies only to {@link SsoProtocol#GITHUB} rows; every other registration is returned exactly
 * as the default service produced it. OIDC logins never reach here at all — Spring routes those
 * through {@code OidcUserService}.
 */
@Component
public class GitHubOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuth2UserService.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    /** A login is interactive; a hung API call would leave the user staring at a blank tab. */
    private static final String ACCEPT = "application/vnd.github+json";

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final SsoProviderRepository providerRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GitHubOAuth2UserService(SsoProviderRepository providerRepository, ObjectMapper objectMapper) {
        this.providerRepository = providerRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                // A redirect would re-send the access token to whatever host the response names.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(request);

        String registrationId = request.getClientRegistration().getRegistrationId();
        SsoProvider provider = providerRepository.findBySlug(registrationId).orElse(null);
        if (provider == null || provider.getProtocol() != SsoProtocol.GITHUB) {
            return user;
        }

        Map<String, Object> attributes = new LinkedHashMap<>(user.getAttributes());
        if (isBlank(attributes.get("name")) && !isBlank(attributes.get("login"))) {
            attributes.put("name", attributes.get("login"));
        }

        // Drop whatever /user reported before deciding: absent beats unverified, because an
        // unverified address that reaches provisioning creates an account under it.
        attributes.remove("email");
        attributes.put("email_verified", false);

        Optional<String> verified = verifiedEmail(provider, request.getAccessToken().getTokenValue());
        if (verified.isPresent()) {
            attributes.put("email", verified.get());
            attributes.put("email_verified", true);
        } else {
            log.warn("GitHub provider {} returned no verified email. The OAuth app needs the "
                    + "user:email scope, and the account needs at least one verified address.",
                    provider.getSlug());
        }

        // "id" matches the userNameAttributeName set on the registration: the numeric id is the
        // only GitHub identifier that cannot be renamed or reclaimed by someone else.
        return new DefaultOAuth2User(user.getAuthorities(), attributes, "id");
    }

    /**
     * The primary verified address if there is one, otherwise any verified address.
     *
     * <p>Failures are swallowed into {@link Optional#empty()} on purpose. A missing email produces a
     * clear refusal further along ("did not return an email address"); turning a transient GitHub
     * outage into an authentication exception here would instead surface as a generic sign-in
     * failure with no indication of which of the two happened.
     */
    private Optional<String> verifiedEmail(SsoProvider provider, String accessToken) {
        String url = GitHubEndpoints.forBaseUrl(provider.getIssuerUri()).userEmailsUri();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(READ_TIMEOUT)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", ACCEPT)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("GitHub {} returned HTTP {} — check the user:email scope",
                        url, response.statusCode());
                return Optional.empty();
            }
            return pickVerified(objectMapper.readTree(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            // Never the exception message: it can carry response content.
            log.warn("Could not read verified emails from {}: {}", url, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static Optional<String> pickVerified(JsonNode body) {
        if (body == null || !body.isArray()) {
            return Optional.empty();
        }
        String firstVerified = null;
        for (JsonNode entry : body) {
            if (!entry.path("verified").asBoolean(false)) {
                continue;
            }
            String email = entry.path("email").asString(null);
            if (email == null || email.isBlank()) {
                continue;
            }
            if (entry.path("primary").asBoolean(false)) {
                return Optional.of(email);
            }
            if (firstVerified == null) {
                firstVerified = email;
            }
        }
        return Optional.ofNullable(firstVerified);
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }
}
