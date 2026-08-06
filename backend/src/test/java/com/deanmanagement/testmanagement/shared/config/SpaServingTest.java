package com.deanmanagement.testmanagement.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The jar now serves the Angular app as well as the API, which puts two failure modes one
 * character apart: a client-side route must return the HTML shell, and a mistyped API path must
 * NOT — a 200 full of HTML where JSON was expected is far harder to debug than a 404.
 *
 * <p>Also pins the security headers nginx used to add, including the deliberate exclusion of
 * {@code /api/**} (PRD-018: {@code X-Frame-Options: DENY} would block the Allure iframe).
 *
 * <p>The shell served here is {@code src/test/resources/static/index.html}, a fixture standing in
 * for the real build output.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class SpaServingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void clientSideRoute_servesTheShell() throws Exception {
        mockMvc.perform(get("/projects/3f5df005-d029-41de-a563-da70f874966b/test-runs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<app-root>")));
    }

    @Test
    void rootPath_servesTheShell() throws Exception {
        // Boot's welcome-page mapping forwards "/" to index.html. MockMvc does not follow the
        // forward, so assert the forward itself rather than the body.
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    @WithMockUser
    void unknownApiPath_is404_notTheShell() throws Exception {
        // Authenticated, so security is out of the way and the routing is what is under test:
        // a mistyped API path must 404 rather than fall through to the SPA and return HTML 200.
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownExternalApiPath_is401_notTheShell() throws Exception {
        // The API-key filter still owns /api/external/**; the SPA must not answer for it.
        mockMvc.perform(get("/api/external/projects/TES/test-runs/whatever"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorIsNotSwallowedByTheShell() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UP")));
    }

    @Test
    void shellCarriesTheSecurityHeaders() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")));
    }

    @Test
    void apiDoesNotGetTheShellFrameHeader() throws Exception {
        // PRD-018: DENY here would break the sandboxed iframe the Allure report renders in.
        mockMvc.perform(get("/api/auth/config"))
                .andExpect(header().string("Content-Security-Policy", (String) null))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }

    @Test
    void translationCataloguesAreNotCached() throws Exception {
        mockMvc.perform(get("/assets/i18n/en.json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-cache")));
    }

    @Test
    void theShellIsNeverCached() throws Exception {
        // Without this the shell goes out with a Last-Modified and no Cache-Control, which makes
        // it heuristically cacheable. A browser holding yesterday's index.html then requests
        // asset hashes this jar does not have; each one falls through to the SPA fallback and
        // returns index.html, and the page dies on "Refused to apply style … MIME type
        // ('text/html')" with nothing wrong on the server. Deep links matter as much as "/":
        // they are the shell too, and they are what people bookmark.
        mockMvc.perform(get("/dashboard"))
                .andExpect(header().string("Cache-Control", containsString("no-cache")));
    }

    @Test
    void hashedAssetsAreCachedForever() throws Exception {
        // The filename changes with the content, so revalidating is pure waste.
        mockMvc.perform(get("/main-A1B2C3D4.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("immutable")));
    }

    @Test
    void missingAsset_is404_notTheShell() throws Exception {
        // A translation catalogue answering with HTML fails silently inside ngx-translate and
        // surfaces as untranslated keys, so assets must 404 rather than fall back to the shell.
        mockMvc.perform(get("/assets/i18n/nope.json"))
                .andExpect(status().isNotFound());
    }
}
