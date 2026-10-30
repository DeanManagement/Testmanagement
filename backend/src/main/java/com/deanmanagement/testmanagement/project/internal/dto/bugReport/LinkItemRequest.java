package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import jakarta.validation.constraints.NotBlank;

/** TM46: a test case key or a requirement's external id, or either's UUID. */
public record LinkItemRequest(@NotBlank String reference) {
}
