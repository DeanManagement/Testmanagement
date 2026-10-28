package com.deanmanagement.testmanagement.project.internal.dto.testCase;

import jakarta.validation.constraints.Size;

/** TM09. {@code title} is optional: without one the copy is named after the original plus " (copy)". */
public record CopyTestCaseRequest(@Size(max = 255) String title) {
}
