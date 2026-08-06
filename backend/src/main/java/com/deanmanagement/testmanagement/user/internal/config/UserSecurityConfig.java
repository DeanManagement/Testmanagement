package com.deanmanagement.testmanagement.user.internal.config;

import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.CacheControlHeadersWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter.XFrameOptionsMode;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class UserSecurityConfig {

    private final JwtDecoder jwtDecoder;
    private final UserRepository userRepository;

    public UserSecurityConfig(JwtDecoder jwtDecoder, UserRepository userRepository) {
        this.jwtDecoder = jwtDecoder;
        this.userRepository = userRepository;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain jwtFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                // The jar serves the SPA as well as the API, and the two need different header
                // policies. Spring Security writes its headers when the response commits, so it
                // wins over any filter — both values therefore have to be decided here.
                //   X-Frame-Options: SAMEORIGIN on /api so the Allure report can render in its
                //     sandboxed iframe (PRD-018); DENY on the app shell, as nginx had it.
                //   Cache-Control: no-store on /api, because responses carry authorization-scoped
                //     data (PRD-017). Static assets are left to the resource handler's
                //     ETag/Last-Modified, or the whole hashed bundle is re-fetched every load.
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
                        .cacheControl(HeadersConfigurer.CacheControlConfig::disable)
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                UserSecurityConfig::isApiRequest,
                                new XFrameOptionsHeaderWriter(XFrameOptionsMode.SAMEORIGIN)))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                UserSecurityConfig::isApiRequest,
                                new CacheControlHeadersWriter()))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                request -> !isApiRequest(request),
                                new XFrameOptionsHeaderWriter(XFrameOptionsMode.DENY))))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new JwtAuthenticationFilter(jwtDecoder, userRepository),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        // Spring re-dispatches unhandled statuses (404/405/500) to /error, and
                        // that dispatch is authorized too. Without this it falls through to
                        // denyAll below and every such response is rewritten to an empty 403,
                        // which hides the real cause — a wrong URL looks like a rejected key.
                        // The error body carries no message or stack trace by default.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/auth/login").permitAll()
                        // PRD-012: the login screen must know which SSO buttons to draw
                        // before anyone has a token. Exposes only slugs and labels.
                        .requestMatchers(HttpMethod.GET, "/api/auth/config").permitAll()
                        // PRD-018: Allure report view is rendered in a sandboxed (opaque-origin)
                        // iframe that cannot send credentials. Access is authenticated inside the
                        // controller via a short-lived single-report view token in the path.
                        .requestMatchers(HttpMethod.GET,
                                "/api/projects/*/test-runs/*/allure-report/view/**").permitAll()
                        .requestMatchers("/api/external/**").hasRole("API_KEY")
                        .requestMatchers("/api/**").authenticated()
                        // Everything above is the API. What is left is the Angular app the jar now
                        // serves: the shell, its hashed assets, and every client-side route, all of
                        // which an unauthenticated browser must be able to fetch — the app itself
                        // decides what to show once it has loaded. Actuator is denied explicitly
                        // first so that widening its exposure later cannot leak through this rule.
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    /** The API and the app shell get different header policies; this is the boundary. */
    private static boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }
}
