package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CloneTestRunRequest(
        @NotBlank @Size(max = 255) String name,
        String environment,
        UUID environmentId
) {
    public CloneTestRunRequest(String name, String environment) {
        this(name, environment, null);
    }
}
