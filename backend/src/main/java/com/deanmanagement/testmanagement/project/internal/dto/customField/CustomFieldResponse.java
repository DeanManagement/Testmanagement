package com.deanmanagement.testmanagement.project.internal.dto.customField;

import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldType;

import java.util.List;
import java.util.UUID;

public record CustomFieldResponse(
        UUID id,
        CustomFieldEntityType entityType,
        String name,
        CustomFieldType fieldType,
        List<String> options,
        boolean required,
        boolean archived,
        int orderIndex
) {
}
