package com.deanmanagement.testmanagement.project.internal.dto.testCaseLink;

import com.deanmanagement.testmanagement.project.internal.entity.TestCaseLinkType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Links the path's case (the source) to {@code targetTestCaseId}. */
public record CreateTestCaseLinkRequest(@NotNull UUID targetTestCaseId, @NotNull TestCaseLinkType linkType) {
}
