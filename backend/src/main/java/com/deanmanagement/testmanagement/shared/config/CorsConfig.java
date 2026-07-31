package com.deanmanagement.testmanagement.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:4200}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split("\\s*,\\s*")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-API-Key", "X-Requested-With"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // PRD-018: the Allure report runs in a sandboxed iframe with an OPAQUE origin
        // (Origin: null). Its internal fetches for report JSON would otherwise be rejected
        // by the credentialed CORS config below. The view path is read-only, authenticated
        // by a short-lived single-report token in the path, and uncredentialed — so a
        // wildcard is safe and required for the sandbox to work. Registered first because
        // the most specific pattern wins.
        CorsConfiguration allureView = new CorsConfiguration();
        allureView.setAllowedOrigins(List.of("*"));
        allureView.setAllowedMethods(List.of("GET"));
        allureView.setAllowedHeaders(List.of("Accept", "Content-Type"));
        allureView.setAllowCredentials(false);
        source.registerCorsConfiguration("/api/projects/*/test-runs/*/allure-report/view/**", allureView);

        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
