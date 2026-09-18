package com.deanmanagement.testmanagement.project.internal.dto.customField;

import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code options} is required for SELECT and MULTI_SELECT and ignored otherwise. */
public record CreateCustomFieldRequest(
        @NotNull CustomFieldEntityType entityType,
        @NotBlank @Size(max = 100) String name,
        @NotNull CustomFieldType fieldType,
        List<String> options,
        Boolean required
) {
}
