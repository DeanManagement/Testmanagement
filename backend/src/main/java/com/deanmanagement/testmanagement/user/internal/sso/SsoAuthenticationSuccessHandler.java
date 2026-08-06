package com.deanmanagement.testmanagement.user.internal.sso;

import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.services.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Completes an SSO login by resolving the local user and issuing the application's own JWT
 * (PRD-012 §3.1), then handing it to the frontend.
 *
 * <p>The token travels in the URL <em>fragment</em>, not the query string. A fragment is never sent
 * to a server, so it stays out of access logs, proxy logs and {@code Referer} headers — a session
 * token in a query string tends to end up written down somewhere.
 *
 * <p>The redirect target comes from configuration only. Honouring a {@code redirect_uri} parameter
 * here would turn the callback into an open redirect that hands the freshly minted token to
 * whatever host an attacker names.
 */
@Component
public class SsoAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(SsoAuthenticationSuccessHandler.class);

    private final SsoProviderRepository providerRepository;
    private final SsoLoginService loginService;
    private final AuthService authService;
    private final SsoProperties properties;

    public SsoAuthenticationSuccessHandler(SsoProviderRepository providerRepository,
                                           SsoLoginService loginService,
                                           AuthService authService,
                                           SsoProperties properties) {
        this.providerRepository = providerRepository;
        this.loginService = loginService;
        this.authService = authService;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        // OidcUser extends OAuth2User, so this accepts both an OIDC login and a GitHub one.
        if (!(authentication instanceof OAuth2AuthenticationToken token)
                || !(token.getPrincipal() instanceof OAuth2User principal)) {
            redirectWithError(response, "Unsupported authentication response");
            return;
        }

        SsoProvider provider = providerRepository.findBySlug(token.getAuthorizedClientRegistrationId())
                .filter(SsoProvider::isActive)
                .orElse(null);
        if (provider == null) {
            redirectWithError(response, "This sign-in method is no longer available");
            return;
        }

        try {
            User user = loginService.resolveUser(provider, principal);
            String jwt = authService.issueToken(user);
            response.sendRedirect(callbackUrl() + "#token=" + URLEncoder.encode(jwt, StandardCharsets.UTF_8));
        } catch (SsoLoginException e) {
            // Expected refusals (unverified email, no account) — the message is meant for the user.
            log.info("Refused SSO login on provider {}: {}", provider.getSlug(), e.getMessage());
            redirectWithError(response, e.getMessage());
        } catch (RuntimeException e) {
            log.error("SSO login failed on provider {}", provider.getSlug(), e);
            redirectWithError(response, "Sign-in failed. Please try again or contact an administrator.");
        }
    }

    private void redirectWithError(HttpServletResponse response, String message) throws IOException {
        response.sendRedirect(callbackUrl() + "#error=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }

    private String callbackUrl() {
        return properties.frontendCallbackUrl();
    }
}
