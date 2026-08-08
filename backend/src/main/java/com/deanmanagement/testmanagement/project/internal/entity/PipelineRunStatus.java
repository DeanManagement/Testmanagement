package com.deanmanagement.testmanagement.project.internal.entity;

/**
 * Lifecycle of a triggered pipeline (PRD-024). {@code TRIGGERED} means the dispatch was accepted
 * but no external run id is known yet (GitHub/Forgejo return 204 without one); {@code PENDING}
 * means the run is identified but not yet executing. Everything from {@code SUCCESS} on is
 * terminal and no longer polled.
 */
public enum PipelineRunStatus {
    TRIGGERED,
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
    TIMED_OUT,
    ERROR;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED
                || this == TIMED_OUT || this == ERROR;
    }
}
