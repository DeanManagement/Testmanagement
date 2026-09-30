package com.deanmanagement.testmanagement.project.internal.dto.buildserver;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** A project admin's choice of the workflows testers are offered. An empty list offers none. */
public record SetProjectWorkflowsRequest(@NotNull List<UUID> workflowIds) {
}
