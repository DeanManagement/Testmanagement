package com.deanmanagement.testmanagement.project.internal.dto.requirement;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.RequirementStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * A full replace of the requirement's own fields. {@code status} and {@code priority} default to
 * DRAFT / MEDIUM when null; {@code customFields} follows {@code CustomFieldValueWriter} (null leaves
 * values alone); a null {@code parentId} puts the requirement at the top of the tree.
 */
public record SaveRequirementRequest(
        @NotBlank @Size(max = 100) String externalId,
        @NotBlank @Size(max = 500) String title,
        String description,
        RequirementStatus status,
        Priority priority,
        Map<String, Object> customFields,
        UUID parentId
) {
    public SaveRequirementRequest(String externalId, String title, String description) {
        this(externalId, title, description, null, null, null, null);
    }
}
