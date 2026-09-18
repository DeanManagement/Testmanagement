package com.deanmanagement.testmanagement.project.internal.dto.filter;

import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldType;

import java.util.List;
import java.util.UUID;

/**
 * One custom field constraint of a list filter (PRD-035 §3.5), already resolved against the
 * project's definitions. {@code values} match with OR (a substring for TEXT, equality otherwise);
 * {@code min}/{@code max} are inclusive bounds for NUMBER and DATE. Values and bounds are typed to
 * match the column: String, BigDecimal or LocalDate.
 */
public record CustomFieldCriterion(
        UUID fieldId,
        CustomFieldType fieldType,
        List<? extends Comparable<?>> values,
        Comparable<?> min,
        Comparable<?> max
) {
    /** The {@code CustomFieldValue} attribute this field's values live in. */
    public String valueAttribute() {
        return switch (fieldType) {
            case NUMBER -> "valueNumber";
            case DATE -> "valueDate";
            case TEXT, SELECT, MULTI_SELECT -> "valueText";
        };
    }
}
