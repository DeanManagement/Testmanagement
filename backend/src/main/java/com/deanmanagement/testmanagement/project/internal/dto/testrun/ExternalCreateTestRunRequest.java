package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ExternalCreateTestRunRequest(
        @NotBlank @Size(max = 255) String name,
        String environment,
        @NotEmpty List<@Valid ExternalTestResultRequest> results
) {
}
