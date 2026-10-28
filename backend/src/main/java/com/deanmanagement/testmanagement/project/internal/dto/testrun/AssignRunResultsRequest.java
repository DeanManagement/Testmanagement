package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/** TM23: results to assign to a project member; a null {@code assigneeId} hands them back to the run's executor. */
public record AssignRunResultsRequest(@NotEmpty @Size(max = 1000) Set<UUID> resultIds, UUID assigneeId) {
}
