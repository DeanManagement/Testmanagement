package com.deanmanagement.testmanagement.project.internal.entity;

/**
 * Issue trackers the tool can talk to. All but LINEAR have adapters (PRD-010, PRD-029, PRD-026);
 * LINEAR is declared so stored configs and links survive the addition of its adapter without a
 * migration.
 *
 * <p>FORGEJO also covers Gitea, which Forgejo forked from and whose v1 REST API it stays
 * compatible with.
 */
public enum IssueTrackerProviderType {
    GITLAB,
    FORGEJO,
    GITHUB,
    JIRA,
    LINEAR,
    /** Azure Boards work items, cloud and Server (PRD-026). */
    AZURE_DEVOPS
}
