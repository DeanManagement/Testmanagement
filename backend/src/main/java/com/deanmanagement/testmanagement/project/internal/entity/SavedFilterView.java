package com.deanmanagement.testmanagement.project.internal.entity;

/** The list a saved filter belongs to (PRD-053 §3). Other lists can adopt saved filters by adding theirs. */
public enum SavedFilterView {
    TEST_CASES,
    /** TM48 */
    BUG_REPORTS
}
