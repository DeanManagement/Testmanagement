package com.deanmanagement.testmanagement.project.internal.entity;

/**
 * Build servers the tool can trigger pipelines on (PRD-024). All but AZURE_DEVOPS have adapters;
 * it is declared up front so stored configs survive the addition of its adapter without a
 * migration (the PRD-010 convention).
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
