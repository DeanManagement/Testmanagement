package com.deanmanagement.testmanagement.user.internal.sso;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The OAuth2 login chain (PRD-012), separate from and ahead of the JWT chain.
 *
 * <p>It matches only the two OAuth2 endpoints, so every other route keeps behaving exactly as
 * before — this feature cannot change how existing requests are authenticated.
 *
 * <p>Sessions are permitted here, unlike the stateless API chain, because the authorization-code
 * flow needs somewhere to keep the {@code state} and PKCE verifier between the redirect out and the
 * callback back. The session ends at the callback: the handler mints the app's own JWT and the rest
 * of the application stays stateless.
 */
@Configuration
@EnableConfigurationProperties(SsoProperties.class)
public class SsoSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SsoSecurityConfig.class);

    @Bean
    @Order(1)
    public SecurityFilterChain oauth2LoginFilterChain(
            HttpSecurity http,
            DynamicClientRegistrationRepository clientRegistrationRepository,
            SsoAuthenticationSuccessHandler successHandler,
            GitHubOAuth2UserService gitHubUserService,
            SsoProperties properties) throws Exception {

        http
                .securityMatcher("/oauth2/authorization/**", "/login/oauth2/code/**")
                // No CSRF token exists yet at this point in the flow; the OAuth2 `state` parameter
                // is what protects the callback against forgery.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .clientRegistrationRepository(clientRegistrationRepository)
                        // Only consulted for non-OIDC providers; Spring routes an OIDC login
                        // through OidcUserService instead, so GitHub's repairs cannot affect them.
                        .userInfoEndpoint(userInfo -> userInfo.userService(gitHubUserService))
                        .successHandler(successHandler)
                        .failureHandler((request, response, exception) -> {
                            // Logged here because the redirect deliberately drops the detail, and
                            // without this an admin debugging a provider has the browser saying
                            // "sign-in failed" and the server saying nothing at all.
                            log.warn("SSO login failed before the success handler", exception);
                            redirectWithError(response, properties);
                        }));
        return http.build();
    }

    /**
     * Failures before our success handler runs — an unknown or deactivated provider, a rejected
     * state parameter, an error from the IdP. The message is generic on purpose: the detail is in
     * the server log, and echoing an upstream error into the browser risks reflecting attacker
     * content back to the user.
     */
    private static void redirectWithError(HttpServletResponse response, SsoProperties properties)
            throws IOException {
        String message = "Single sign-on failed. Please try again or contact an administrator.";
        response.sendRedirect(properties.frontendCallbackUrl()
                + "#error=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }
}
