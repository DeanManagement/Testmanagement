package com.deanmanagement.testmanagement.project.internal.entity;

/**
 * Events an outbound webhook can subscribe to (PRD-003).
 */
public enum WebhookEventType {
    RUN_STARTED,
    RUN_COMPLETED,
    RUN_FAILED,
    TEST_FAILED,
    PLAN_COMPLETED,
    BUG_REPORT_CREATED
}
