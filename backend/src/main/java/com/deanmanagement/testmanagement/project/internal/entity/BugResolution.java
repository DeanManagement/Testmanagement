package com.deanmanagement.testmanagement.project.internal.entity;

/**
 * Why a bug was closed (PRD-045). Required on CLOSED, allowed on RESOLVED, cleared on reopen.
 * DUPLICATE names the bug it duplicates.
 */
public enum BugResolution {
    FIXED, WONT_FIX, DUPLICATE, CANNOT_REPRODUCE, NOT_A_BUG, DEFERRED
}
