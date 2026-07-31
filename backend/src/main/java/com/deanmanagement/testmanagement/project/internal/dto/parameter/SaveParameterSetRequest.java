package com.deanmanagement.testmanagement.project.internal.dto.parameter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record SaveParameterSetRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull Map<String, String> values,
        Integer orderIndex
) {
}
