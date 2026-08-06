package com.deanmanagement.testmanagement.user.internal.sso;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthenticationMethod;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GitHub's {@code /user} response is not an identity — it omits the email unless the user made one
 * public, and never says whether an address is verified. This service is what turns it into
 * something {@link SsoLoginService} can apply the same rules to as an OIDC claim set, so the cases
 * that matter are the ones where GitHub's answer is incomplete or untrustworthy.
 */
class GitHubOAuth2UserServiceTest {

    private HttpServer server;
    private GitHubOAuth2UserService service;
    private SsoProvider provider;
    private String baseUrl;

    private final AtomicReference<String> userBody = new AtomicReference<>(
            "{\"id\": 4711, \"login\": \"octocat\", \"name\": \"The Octocat\"}");
    private final AtomicReference<String> emailsBody = new AtomicReference<>("[]");
    private final AtomicInteger emailsStatus = new AtomicInteger(200);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v3/user", exchange -> {
            boolean emails = exchange.getRequestURI().getPath().endsWith("/emails");
            byte[] body = (emails ? emailsBody.get() : userBody.get()).getBytes(StandardCharsets.UTF_8);
            int status = emails ? emailsStatus.get() : 200;
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        provider = new SsoProvider();
        provider.setSlug("gh");
        provider.setProtocol(SsoProtocol.GITHUB);
        provider.setIssuerUri(baseUrl);

        SsoProviderRepository repository = mock(SsoProviderRepository.class);
        when(repository.findBySlug(anyString())).thenReturn(Optional.of(provider));
        service = new GitHubOAuth2UserService(repository, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void primaryVerifiedEmailIsUsed() {
        emailsBody.set("""
                [
                  {"email": "secondary@example.com", "primary": false, "verified": true},
                  {"email": "primary@example.com", "primary": true, "verified": true}
                ]
                """);

        OAuth2User user = service.loadUser(request());

        assertThat(user.<Object>getAttribute("email")).isEqualTo("primary@example.com");
        assertThat(user.<Object>getAttribute("email_verified")).isEqualTo(true);
    }

    @Test
    void anUnverifiedPrimaryLosesToAVerifiedSecondary() {
        emailsBody.set("""
                [
                  {"email": "unverified@example.com", "primary": true, "verified": false},
                  {"email": "verified@example.com", "primary": false, "verified": true}
                ]
                """);

        assertThat(service.loadUser(request()).<Object>getAttribute("email"))
                .isEqualTo("verified@example.com");
    }

    @Test
    void anUnverifiedPublicEmailIsDiscardedRatherThanPassedOn() {
        // GitHub lets anyone put any string in their public profile email. Passing it along would
        // let a stranger have an account provisioned under someone else's address.
        userBody.set("{\"id\": 4711, \"login\": \"octocat\", \"email\": \"victim@example.com\"}");
        emailsBody.set("[]");

        OAuth2User user = service.loadUser(request());

        assertThat(user.<Object>getAttribute("email")).isNull();
        assertThat(user.<Object>getAttribute("email_verified")).isEqualTo(false);
    }

    @Test
    void aFailingEmailsCallLeavesTheLoginWithoutAnEmailRatherThanBreaking() {
        // 403 is what a token missing the user:email scope gets.
        emailsStatus.set(403);

        OAuth2User user = service.loadUser(request());

        assertThat(user.<Object>getAttribute("email")).isNull();
        assertThat(user.getName()).isEqualTo("4711");
    }

    @Test
    void theSubjectIsTheNumericIdNotTheLogin() {
        // A login can be renamed and, once released, claimed by someone else; the id cannot.
        assertThat(service.loadUser(request()).getName()).isEqualTo("4711");
    }

    @Test
    void theLoginStandsInForAMissingDisplayName() {
        userBody.set("{\"id\": 4711, \"login\": \"octocat\"}");

        assertThat(service.loadUser(request()).<Object>getAttribute("name")).isEqualTo("octocat");
    }

    @Test
    void aNonGitHubRegistrationIsLeftAlone() {
        provider.setProtocol(SsoProtocol.OIDC);
        userBody.set("{\"id\": 4711, \"login\": \"octocat\", \"email\": \"as-returned@example.com\"}");

        OAuth2User user = service.loadUser(request());

        assertThat(user.<Object>getAttribute("email")).isEqualTo("as-returned@example.com");
        assertThat(user.<Object>getAttribute("email_verified")).isNull();
    }

    private OAuth2UserRequest request() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("gh")
                .clientId("client")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/gh")
                .authorizationUri(baseUrl + "/login/oauth/authorize")
                .tokenUri(baseUrl + "/login/oauth/access_token")
                .userInfoUri(baseUrl + "/api/v3/user")
                .userInfoAuthenticationMethod(AuthenticationMethod.HEADER)
                .userNameAttributeName("id")
                .build();

        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "access-token", Instant.now(), Instant.now().plusSeconds(300));
        return new OAuth2UserRequest(registration, token);
    }
}
