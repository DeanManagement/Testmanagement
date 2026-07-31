package com.deanmanagement.testmanagement.project.internal.entity;

/**
 * Cached state of a linked issue. UNKNOWN covers both "never polled" and "the last poll failed",
 * which the UI shows alongside {@code stateCheckedAt} so a stale pill is visibly stale.
 */
public enum IssueState {
    OPEN,
    CLOSED,
    UNKNOWN
}
