package com.deanmanagement.testmanagement.shared.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * An exception that ends up as a response body is written as its message and its own fields, never
 * its stack trace, cause or suppressed exceptions. The MCP transport answers a malformed message
 * with the SDK's {@code McpError} as the body, and serialized as-is that exposed the server's class
 * names, line numbers and framework versions to any caller. Applied to every {@link Throwable}, so
 * no other path that hands an exception to Jackson can do the same.
 */
@Configuration
public class ThrowableJsonConfig {

    @Bean
    JsonMapperBuilderCustomizer hideThrowableInternals() {
        return builder -> builder.addMixIn(Throwable.class, ThrowableMixIn.class);
    }

    @JsonIgnoreProperties({"stackTrace", "cause", "suppressed", "localizedMessage"})
    abstract static class ThrowableMixIn {
    }
}
