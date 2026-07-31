package com.deanmanagement.testmanagement.project.internal.dto.parameter;

import java.util.Map;
import java.util.UUID;

public record ParameterSetResponse(
        UUID id,
        String name,
        Map<String, String> values,
        int orderIndex
) {
}
