package com.deanmanagement.testmanagement.project.internal.issuetracker;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the project module's configuration properties. Scheduling itself is already enabled by the
 * webhook configuration, so the issue-tracker poller only needs its properties here.
 */
@Configuration
@EnableConfigurationProperties({IssueTrackerProperties.class,
        com.deanmanagement.testmanagement.project.internal.service.FlakyProperties.class})
public class IssueTrackerConfiguration {
}
