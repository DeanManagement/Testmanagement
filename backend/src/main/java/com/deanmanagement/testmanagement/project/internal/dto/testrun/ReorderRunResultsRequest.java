package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** TM21: every result of the run, each once, in the order they should be executed. */
public record ReorderRunResultsRequest(@NotNull List<UUID> resultIds) {
}
