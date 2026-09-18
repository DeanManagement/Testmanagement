package com.deanmanagement.testmanagement.project.internal.dto.customField;

import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldType;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Every field is optional; null leaves it unchanged. {@code options} replaces the whole list;
 * {@code renamedOptions} (old label → new label) says which new labels are renames, so stored
 * values follow them. An option that disappears without a rename must be unused.
 */
public record UpdateCustomFieldRequest(
        @Size(max = 100) String name,
        CustomFieldType fieldType,
        List<String> options,
        Map<String, String> renamedOptions,
        Boolean required,
        Integer orderIndex
) {
}
