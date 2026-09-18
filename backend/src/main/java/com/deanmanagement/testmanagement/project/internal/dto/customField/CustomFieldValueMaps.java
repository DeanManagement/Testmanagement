package com.deanmanagement.testmanagement.project.internal.dto.customField;

import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldDefinition;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns stored values into the name-keyed map entity responses carry (PRD-035 §3.3): fields in
 * display order; a NUMBER as a plain decimal, a DATE as {@code yyyy-MM-dd}, a MULTI_SELECT as a
 * list in option order, everything else as its text.
 */
public final class CustomFieldValueMaps {

    private static final Comparator<CustomFieldDefinition> DISPLAY_ORDER =
            Comparator.comparingInt(CustomFieldDefinition::getOrderIndex).thenComparing(CustomFieldDefinition::getName);

    private CustomFieldValueMaps() {
    }

    public static Map<String, Object> toMap(List<CustomFieldValue> values) {
        Map<CustomFieldDefinition, List<CustomFieldValue>> byField = new LinkedHashMap<>();
        values.stream()
                .sorted(Comparator.comparing(CustomFieldValue::getField, DISPLAY_ORDER))
                .forEach(v -> byField.computeIfAbsent(v.getField(), f -> new ArrayList<>()).add(v));
        Map<String, Object> map = new LinkedHashMap<>();
        byField.forEach((field, fieldValues) -> map.put(field.getName(), toJsonValue(field, fieldValues)));
        return map;
    }

    private static Object toJsonValue(CustomFieldDefinition field, List<CustomFieldValue> values) {
        CustomFieldValue first = values.getFirst();
        return switch (field.getFieldType()) {
            case NUMBER -> plain(first.getValueNumber());
            case DATE -> first.getValueDate().toString();
            case TEXT, SELECT -> first.getValueText();
            case MULTI_SELECT -> {
                Set<String> selected = new LinkedHashSet<>();
                values.forEach(v -> selected.add(v.getValueText()));
                List<String> ordered = new ArrayList<>(field.getOptions().stream().filter(selected::contains).toList());
                selected.stream().filter(s -> !ordered.contains(s)).forEach(ordered::add);
                yield ordered;
            }
        };
    }

    /** {@code 12.5000} from NUMERIC(19,4) reads back as {@code 12.5}, and {@code 100} stays {@code 100}. */
    private static BigDecimal plain(BigDecimal number) {
        return new BigDecimal(number.stripTrailingZeros().toPlainString());
    }
}
