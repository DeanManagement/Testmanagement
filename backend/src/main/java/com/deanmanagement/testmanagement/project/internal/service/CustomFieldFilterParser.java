package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.filter.CustomFieldCriterion;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldDefinition;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldType;
import com.deanmanagement.testmanagement.project.internal.repository.CustomFieldDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns list-endpoint query parameters into {@link CustomFieldCriterion}s (PRD-035 §3.5):
 * {@code cf.<name>=v} (repeatable, OR within a field; a substring for TEXT) and
 * {@code cf.<name>.min=} / {@code .max=} for NUMBER and DATE. Different fields AND together.
 * Names resolve against the project's own definitions only, so another project's field is
 * simply unknown (400).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomFieldFilterParser {

    public static final String PARAM_PREFIX = "cf.";
    private static final String MIN_SUFFIX = ".min";
    /** Same length as {@link #MIN_SUFFIX}, which {@link #rangeField} relies on. */
    private static final String MAX_SUFFIX = ".max";

    private final CustomFieldDefinitionRepository definitionRepository;

    /** Every parameter not starting with {@value #PARAM_PREFIX} is ignored; blank values too. */
    public List<CustomFieldCriterion> parse(UUID projectId, CustomFieldEntityType entityType,
                                            Map<String, List<String>> params) {
        Map<String, List<String>> cfParams = new LinkedHashMap<>();
        params.forEach((key, values) -> {
            if (key.startsWith(PARAM_PREFIX)) {
                cfParams.put(key.substring(PARAM_PREFIX.length()), values);
            }
        });
        if (cfParams.isEmpty()) {
            return List.of();
        }
        Map<String, CustomFieldDefinition> byName = new LinkedHashMap<>();
        definitionRepository.findByProjectIdAndEntityTypeOrderByOrderIndexAscNameAsc(projectId, entityType)
                .forEach(d -> byName.put(CustomFieldValueWriter.normalize(d.getName()), d));

        Map<CustomFieldDefinition, Builder> builders = new LinkedHashMap<>();
        cfParams.forEach((key, values) -> addParam(byName, builders, key, values));
        return builders.values().stream()
                .filter(Builder::hasConstraint)
                .map(Builder::build)
                .toList();
    }

    private static void addParam(Map<String, CustomFieldDefinition> byName, Map<CustomFieldDefinition, Builder> builders,
                                 String key, List<String> values) {
        // A field literally named "Size.min" wins over a range on "Size".
        CustomFieldDefinition exact = byName.get(CustomFieldValueWriter.normalize(key));
        CustomFieldDefinition field = exact != null ? exact : rangeField(byName, key);
        if (field == null) {
            throw new IllegalArgumentException("Unknown custom field filter: " + PARAM_PREFIX + key);
        }
        Builder builder = builders.computeIfAbsent(field, Builder::new);
        for (String raw : values) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            if (exact != null) {
                builder.values.add(toColumnValue(field, raw.trim()));
            } else if (key.endsWith(MIN_SUFFIX)) {
                builder.min = toColumnValue(field, raw.trim());
            } else {
                builder.max = toColumnValue(field, raw.trim());
            }
        }
    }

    private static CustomFieldDefinition rangeField(Map<String, CustomFieldDefinition> byName, String key) {
        if (!key.endsWith(MIN_SUFFIX) && !key.endsWith(MAX_SUFFIX)) {
            return null;
        }
        String name = key.substring(0, key.length() - MIN_SUFFIX.length());
        CustomFieldDefinition field = byName.get(CustomFieldValueWriter.normalize(name));
        if (field == null) {
            return null;
        }
        if (field.getFieldType() != CustomFieldType.NUMBER && field.getFieldType() != CustomFieldType.DATE) {
            throw new IllegalArgumentException("Custom field '" + field.getName()
                    + "' is " + field.getFieldType() + "; only NUMBER and DATE fields take .min/.max");
        }
        return field;
    }

    private static Comparable<?> toColumnValue(CustomFieldDefinition field, String raw) {
        return switch (field.getFieldType()) {
            case TEXT -> raw;
            case NUMBER -> {
                try {
                    yield new BigDecimal(raw);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Custom field '" + field.getName() + "' filter expects a number");
                }
            }
            case DATE -> {
                try {
                    yield LocalDate.parse(raw);
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Custom field '" + field.getName()
                            + "' filter expects a date as yyyy-MM-dd");
                }
            }
            case SELECT, MULTI_SELECT -> field.getOptions().stream()
                    .filter(option -> option.equalsIgnoreCase(raw))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Custom field '" + field.getName()
                            + "' has no option '" + raw + "'"));
        };
    }

    private static final class Builder {
        private final CustomFieldDefinition field;
        private final List<Comparable<?>> values = new ArrayList<>();
        private Comparable<?> min;
        private Comparable<?> max;

        private Builder(CustomFieldDefinition field) {
            this.field = field;
        }

        private boolean hasConstraint() {
            return !values.isEmpty() || min != null || max != null;
        }

        private CustomFieldCriterion build() {
            return new CustomFieldCriterion(field.getId(), field.getFieldType(), List.copyOf(values), min, max);
        }
    }
}
