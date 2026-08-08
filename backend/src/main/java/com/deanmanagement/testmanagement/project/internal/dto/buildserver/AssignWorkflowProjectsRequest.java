package com.deanmanagement.testmanagement.project.internal.dto.buildserver;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Replaces a workflow's project assignments with exactly this set. An empty list unassigns all. */
public record AssignWorkflowProjectsRequest(@NotNull List<UUID> projectIds) {
}
