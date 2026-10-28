package com.deanmanagement.testmanagement.project.internal.dto.testCaseLink;

import com.deanmanagement.testmanagement.project.internal.entity.TestCaseLinkType;

import java.util.UUID;

/**
 * A link seen from one case (TM07). OUTGOING: this case is the source, so for PRECONDITION the
 * other case runs first. INCOMING: the other case links to this one.
 */
public record TestCaseLinkResponse(
        UUID id,
        TestCaseLinkType linkType,
        Direction direction,
        UUID testCaseId,
        String testCaseKey,
        String testCaseTitle
) {
    public enum Direction { OUTGOING, INCOMING }
}
