package com.deanmanagement.testmanagement.project.internal.entity;

/**
 * Issue trackers the tool can talk to. Only GITLAB has an adapter today (PRD-010); the rest are
 * declared so stored configs and links survive the addition of their adapters without a migration.
 */
public enum IssueTrackerProviderType {
    GITLAB,
    GITHUB,
    JIRA,
    LINEAR
}
