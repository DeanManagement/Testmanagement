package com.deanmanagement.testmanagement.project.internal.entity;

/** How two bug reports relate (TM46). A duplicate is a resolution, not a relation. */
public enum BugRelationType {
    /** Directed: the bug must be fixed before the related one can be. */
    BLOCKS,
    /** Symmetric. */
    RELATES_TO
}
