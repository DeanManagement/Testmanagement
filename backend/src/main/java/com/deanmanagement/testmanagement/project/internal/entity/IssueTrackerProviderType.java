package com.deanmanagement.testmanagement.project.internal.entity;

/**
 * Issue trackers the tool can talk to. GITLAB and FORGEJO have adapters (PRD-010); the rest are
 * declared so stored configs and links survive the addition of their adapters without a migration.
 *
 * <p>FORGEJO also covers Gitea, which Forgejo forked from and whose v1 REST API it stays
 * compatible with.
 */
public enum IssueTrackerProviderType {
    GITLAB,
    FORGEJO,
    GITHUB,
    JIRA,
    LINEAR
}
