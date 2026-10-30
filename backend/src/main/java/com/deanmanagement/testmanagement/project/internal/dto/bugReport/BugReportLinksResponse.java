package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import com.deanmanagement.testmanagement.project.internal.entity.BugRelationType;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;

import java.util.List;
import java.util.UUID;

/** What a bug report is linked to by hand (TM46). */
public record BugReportLinksResponse(
        List<Item> testCases,
        List<Item> requirements,
        List<Relation> relations
) {
    /** {@code key}: a case's key, or a requirement's external id. */
    public record Item(UUID id, String key, String title) {
    }

    /**
     * {@code outgoing}: this bug is the subject ("blocks BUG-7"); otherwise the other bug is
     * ("blocked by BUG-7"). Always true for RELATES_TO, which reads the same both ways.
     */
    public record Relation(UUID id, BugRelationType type, boolean outgoing, UUID bugId, String bugKey,
                           String bugTitle, BugReportStatus bugStatus) {
    }
}
