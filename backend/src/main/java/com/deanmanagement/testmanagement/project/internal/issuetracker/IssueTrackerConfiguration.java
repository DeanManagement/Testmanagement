package com.deanmanagement.testmanagement.project.internal.issuetracker;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link IssueTrackerProperties}. Scheduling itself is already enabled by the webhook
 * configuration, so the poller only needs its properties here.
 */
@Configuration
@EnableConfigurationProperties(IssueTrackerProperties.class)
public class IssueTrackerConfiguration {
}
