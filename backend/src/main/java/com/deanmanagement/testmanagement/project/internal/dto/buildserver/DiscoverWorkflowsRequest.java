package com.deanmanagement.testmanagement.project.internal.dto.buildserver;

import jakarta.validation.constraints.Size;

/** Ask a server what can be triggered. {@code repoRef} is optional where the provider lists globally. */
public record DiscoverWorkflowsRequest(@Size(max = 300) String repoRef) {
}
