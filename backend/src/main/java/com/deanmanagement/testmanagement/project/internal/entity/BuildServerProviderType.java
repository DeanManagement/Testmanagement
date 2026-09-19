package com.deanmanagement.testmanagement.project.internal.entity;

/**
 * Build servers the tool can trigger pipelines on (PRD-024). AZURE_DEVOPS was declared before its
 * adapter existed (PRD-026), so stored configs never needed a migration (the PRD-010 convention).
 *
 * <p>FORGEJO_ACTIONS also covers Gitea and Codeberg, whose Actions API Forgejo stays compatible
 * with.
 */
public enum BuildServerProviderType {
    GITLAB_CI,
    GITHUB_ACTIONS,
    FORGEJO_ACTIONS,
    WOODPECKER,
    JENKINS,
    AZURE_DEVOPS
}
