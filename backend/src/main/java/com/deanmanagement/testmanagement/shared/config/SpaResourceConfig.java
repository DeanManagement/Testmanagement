package com.deanmanagement.testmanagement.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.List;

/**
 * Serves the built Angular app from the jar and falls back to {@code index.html} for client-side
 * routes, so a deep link like {@code /projects/<id>/test-runs} survives a page reload.
 *
 * <p>The fallback is deliberately narrow. Anything the browser could not plausibly be asking the
 * SPA for — the API, actuator, the OpenAPI documents, the Spring error dispatch — returns null so
 * that Spring answers it properly. Without that guard a typo in an API path would return the HTML
 * shell with status 200, which is far harder to debug than a 404 (see PRD-023: the same class of
 * masking already cost us an afternoon on {@code /error}).
 */
@Configuration
public class SpaResourceConfig implements WebMvcConfigurer {

    private static final String STATIC_ROOT = "classpath:/static/";
    private static final String INDEX = "/static/index.html";

    /**
     * Prefixes the SPA must never answer for. {@code assets/} is in the list because a missing
     * asset has to 404 rather than return the shell — nginx used an explicit {@code try_files
     * $uri =404} for exactly this. A translation catalogue that answers with HTML instead of JSON
     * fails deep inside ngx-translate and surfaces as untranslated keys with no error at all.
     */
    private static final List<String> RESERVED = List.of(
            "api/", "actuator/", "error", "v3/api-docs", "swagger-ui", "assets/");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_ROOT)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (isReserved(resourcePath)) {
                            return null;
                        }
                        ClassPathResource index = new ClassPathResource(INDEX);
                        return index.exists() ? index : null;
                    }
                });
    }

    private static boolean isReserved(String resourcePath) {
        return RESERVED.stream().anyMatch(resourcePath::startsWith);
    }
}
