package com.deanmanagement.testmanagement.project.internal.entity;

/**
 * Lifecycle of a test case. With a project's review switch on (PRD-033), {@code ACTIVE} means
 * approved and is reached only through the approve action; {@code IN_REVIEW} is waiting for it.
 */
public enum TestCaseStatus {
    DRAFT, IN_REVIEW, ACTIVE, DEPRECATED
}
