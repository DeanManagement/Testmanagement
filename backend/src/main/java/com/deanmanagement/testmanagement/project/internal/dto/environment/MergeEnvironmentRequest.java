package com.deanmanagement.testmanagement.project.internal.dto.environment;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MergeEnvironmentRequest(@NotNull UUID targetId) {
}
