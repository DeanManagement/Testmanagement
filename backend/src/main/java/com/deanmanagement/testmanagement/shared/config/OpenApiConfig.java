package com.deanmanagement.testmanagement.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI testmanagementOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Testmanagement API")
                        .description("REST API for the self-hosted test management tool")
                        .version("0.1.0")
                        .contact(new Contact().name("DeanManagement"))
                        .license(new License().name("MIT")));
    }
}
