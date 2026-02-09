package com.deanmanagement.testmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record UpdateTestSuiteRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        Set<UUID> testCaseIds
) {
}
