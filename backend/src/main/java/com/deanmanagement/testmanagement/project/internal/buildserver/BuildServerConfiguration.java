package com.deanmanagement.testmanagement.project.internal.buildserver;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the build-server configuration properties. Scheduling is already enabled by the webhook
 * configuration, so the pipeline status poller only needs its properties here.
 */
@Configuration
@EnableConfigurationProperties(BuildServerProperties.class)
public class BuildServerConfiguration {
}
