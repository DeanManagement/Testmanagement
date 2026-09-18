package com.deanmanagement.testmanagement.project.internal.dto.testCase;

import jakarta.validation.constraints.Size;

/** @param comment optional; posted as a regular comment on the case. */
public record RequestChangesRequest(@Size(max = 2000) String comment) {
}
