package com.deanmanagement.testmanagement.project.internal.entity;

/**
 * The units an API key's MCP access is granted in (PRD-027 §9).
 *
 * <p>Groups rather than individual tool names on purpose. The point of restricting a key is that
 * an authoring agent should not carry the descriptions of pipeline tools on every turn, and that is
 * a decision about kinds of work. It also means a tool added later lands in a group existing keys
 * already hold or do not, instead of being invisible to every restricted key until each is
 * re-issued.
 */
public enum McpToolGroup {

    /** Identity only ({@code get_project}). Always granted, so it is never stored or offered. */
    CORE,
    /** Test cases, folders, suites, plans, requirements, parameter sets and their history. */
    AUTHORING,
    /** Test runs, results, comments and native bug reports. */
    EXECUTION,
    /** Dashboard, flaky tests, the suite report and the traceability matrix. */
    REPORTING,
    /** Build-server workflows and pipeline runs. */
    PIPELINES,
    /** Issues in the project's external tracker. */
    ISSUE_TRACKER,
    /**
     * Webhooks, the issue-tracker connection and the project's pipeline workflows (TM37): settings
     * that hold credentials and outbound URLs. Never implied by an unrestricted key — see
     * {@link #isImpliedByUnrestrictedKey()}.
     */
    CONFIGURATION;

    /**
     * Whether a key with no stored groups (NULL, "everything") holds this group. CONFIGURATION is
     * not: an agent that can point a webhook at any URL can exfiltrate every event the project
     * emits, so that has to be granted on purpose, by naming the group (PRD-053 §9).
     */
    public boolean isImpliedByUnrestrictedKey() {
        return this != CONFIGURATION;
    }
}
