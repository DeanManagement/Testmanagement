package com.deanmanagement.testmanagement.project.internal.entity;

import java.util.Set;

/** A bug's place in triage (PRD-045). Why a closed bug was closed is its {@link BugResolution}. */
public enum BugReportStatus {
    NEW, OPEN, IN_PROGRESS, RESOLVED, CLOSED;

    /** Still needs work: what the release gate, My queue and duplicate checks count as open. */
    public static final Set<BugReportStatus> OPEN_STATUSES = Set.of(NEW, OPEN, IN_PROGRESS);

    public boolean isOpen() {
        return OPEN_STATUSES.contains(this);
    }
}
