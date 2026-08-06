package com.deanmanagement.testmanagement.user.internal.config;

import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
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
                        .anyRequest().denyAll());
        return http.build();
    }
}
