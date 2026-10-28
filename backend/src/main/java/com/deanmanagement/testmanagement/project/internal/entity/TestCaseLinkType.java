package com.deanmanagement.testmanagement.project.internal.entity;

/** How two test cases relate (TM07). */
public enum TestCaseLinkType {
    /** The target should run, and pass, before the source. */
    PRECONDITION,
    /** Informational only. */
    RELATES_TO
}
