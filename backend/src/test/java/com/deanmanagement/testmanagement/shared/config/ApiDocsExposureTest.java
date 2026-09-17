package com.deanmanagement.testmanagement.shared.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The OpenAPI document and Swagger UI describe every endpoint. They used to be served to anyone,
 * unconditionally, which on an internet-facing instance publishes the whole API surface to
 * anonymous visitors. They are now off unless a deployment asks for them.
 */
class ApiDocsExposureTest {

    /**
     * The tests below run against {@code src/test/resources/application.yml}, which shadows the
     * shipped file. What a deployment actually gets is decided by the shipped one, so that is read
     * here directly: everything must hang off one switch, and the switch must default to off.
     */
    @Test
    void theShippedConfigurationKeepsTheDocsOffUnlessAskedFor() throws Exception {
        var shipped = new YamlPropertySourceLoader()
                .load("shipped", new FileSystemResource("src/main/resources/application.yml"))
                .getFirst();

        assertThat(shipped.getProperty("app.api-docs.enabled")).isEqualTo("${API_DOCS_ENABLED:false}");
        assertThat(shipped.getProperty("springdoc.api-docs.enabled"))
                .isEqualTo("${app.api-docs.enabled:false}");
        assertThat(shipped.getProperty("springdoc.swagger-ui.enabled"))
                .isEqualTo("${app.api-docs.enabled:false}");
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("dev")
    @AutoConfigureMockMvc
    class ByDefault {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void theOpenApiDocumentIsNotServed() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isNotFound());
        }

        /** 404, and specifically not the SPA shell with a 200, which would look like it exists. */
        @Test
        void swaggerUiIsNotServed() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().string(not(containsString("<app-root"))));
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("dev")
    @AutoConfigureMockMvc
    @TestPropertySource(properties = "app.api-docs.enabled=true")
    class WhenADeploymentEnablesThem {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void theOpenApiDocumentIsServedWithoutALogin() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("\"openapi\"")));
        }
    }
}
