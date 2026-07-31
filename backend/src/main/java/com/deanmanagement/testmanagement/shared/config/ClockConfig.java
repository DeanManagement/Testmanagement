package com.deanmanagement.testmanagement.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Exposes a system {@link Clock} bean so time-sensitive services (e.g. the
 * "My queue" dashboard widget) can pin "now" in tests instead of reading
 * wall time directly.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
