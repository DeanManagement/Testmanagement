package com.deanmanagement.testmanagement.project.internal.entity;

/**
 * Body shape a webhook is delivered in (PRD-031). {@code SLACK} also covers Mattermost,
 * Rocket.Chat and Discord's {@code /slack} endpoint, which all accept Slack's incoming-webhook body.
 */
public enum WebhookFormat {
    GENERIC,
    SLACK,
    TEAMS;

    public boolean isChat() {
        return this != GENERIC;
    }
}
