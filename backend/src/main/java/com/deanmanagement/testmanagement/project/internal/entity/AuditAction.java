package com.deanmanagement.testmanagement.project.internal.entity;

public enum AuditAction {
    CREATED,
    UPDATED,
    DELETED,
    STATUS_CHANGED,
    COMPLETED,
    REOPENED,
    CLONED,
    MOVED,
    /** Notification only (TM44): someone commented on a watched bug. Comments are audited as COMMENT CREATED. */
    COMMENTED,
    /** Notification only (TM44): someone named this user in a comment. */
    MENTIONED
}
