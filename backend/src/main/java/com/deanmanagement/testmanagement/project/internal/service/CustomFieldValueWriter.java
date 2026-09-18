package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldDefinition;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldValue;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.repository.CustomFieldDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The one place custom field values are validated and written (PRD-035 §3.3). Values are keyed by
 * field name, matched ignoring case, because import files and agents know names, not ids. In a
 * value map, a {@code null} (or blank, or empty list) clears that field; an absent key leaves it
 * alone; a {@code null} map leaves every field alone.
 * <p>
 * Callers must run inside their own write transaction; the owner's value collection is changed
 * in place and saved with the owner.
 */
@Service
@RequiredArgsConstructor
public class CustomFieldValueWriter {

    public static final int MAX_TEXT_LENGTH = 500;
    private static final int MAX_DECIMAL_PLACES = 4;
    private static final int MAX_INTEGER_DIGITS = 15;
    /** Joins MULTI_SELECT options in CSV cells and single-string writes (PRD-035 §3.7). */
    public static final String MULTI_SELECT_SEPARATOR = ";";

    private final CustomFieldDefinitionRepository definitionRepository;

    public void write(TestCase testCase, Map<String, Object> values, CustomFieldWriteMode mode) {
        write(new Owner(testCase.getProject().getId(), CustomFieldEntityType.TEST_CASE,
                testCase.getCustomFieldValues(), v -> v.setTestCase(testCase), testCase.getId() == null), values, mode);
    }

    public void write(TestRun testRun, Map<String, Object> values, CustomFieldWriteMode mode) {
        write(new Owner(testRun.getProject().getId(), CustomFieldEntityType.TEST_RUN,
                testRun.getCustomFieldValues(), v -> v.setTestRun(testRun), testRun.getId() == null), values, mode);
    }

    public void write(BugReport bugReport, Map<String, Object> values, CustomFieldWriteMode mode) {
        write(new Owner(bugReport.getProject().getId(), CustomFieldEntityType.BUG_REPORT,
                bugReport.getCustomFieldValues(), v -> v.setBugReport(bugReport), bugReport.getId() == null),
                values, mode);
    }

    /**
     * {@code isNew}: a new entity is checked for required fields even when no map was sent; an
     * existing one only when the caller sent values, so a status-only edit is never blocked.
     */
    private record Owner(UUID projectId, CustomFieldEntityType entityType, List<CustomFieldValue> values,
                         Consumer<CustomFieldValue> attach, boolean isNew) {
    }

    private void write(Owner owner, Map<String, Object> values, CustomFieldWriteMode mode) {
        boolean checksRequired = mode == CustomFieldWriteMode.INTERACTIVE && (values != null || owner.isNew());
        if (values == null && !checksRequired) {
            return;
        }
        List<CustomFieldDefinition> definitions = definitionRepository
                .findByProjectIdAndEntityTypeOrderByOrderIndexAscNameAsc(owner.projectId(), owner.entityType());
        if (values != null) {
            apply(definitions, owner, values);
        }
        if (checksRequired) {
            requireFilled(definitions, owner.values());
        }
    }

    /**
     * Checks names and values as {@link #write} would, without writing anything. For dry runs
     * (PRD-035 §3.7), so they report the same errors a real import would hit.
     */
    public void validate(UUID projectId, CustomFieldEntityType entityType, Map<String, Object> values) {
        if (values != null && !values.isEmpty()) {
            parseAll(definitionRepository.findByProjectIdAndEntityTypeOrderByOrderIndexAscNameAsc(projectId, entityType),
                    values);
        }
    }

    private static void apply(List<CustomFieldDefinition> definitions, Owner owner, Map<String, Object> values) {
        // Parse everything before touching the entity, so a bad value leaves it unchanged.
        parseAll(definitions, values).forEach((field, newValues) -> {
            owner.values().removeIf(v -> v.getField().getId().equals(field.getId()));
            for (CustomFieldValue value : newValues) {
                value.setField(field);
                owner.attach().accept(value);
                owner.values().add(value);
            }
        });
    }

    private static Map<CustomFieldDefinition, List<CustomFieldValue>> parseAll(List<CustomFieldDefinition> definitions,
                                                                                Map<String, Object> values) {
        Map<String, CustomFieldDefinition> byName = new LinkedHashMap<>();
        definitions.forEach(d -> byName.put(normalize(d.getName()), d));

        List<String> unknown = values.keySet().stream().filter(name -> !byName.containsKey(normalize(name))).toList();
        if (!unknown.isEmpty()) {
            String valid = definitions.stream().map(CustomFieldDefinition::getName).collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Unknown custom field(s): " + String.join(", ", unknown)
                    + ". Valid fields: " + (valid.isEmpty() ? "none" : valid));
        }
        Map<CustomFieldDefinition, List<CustomFieldValue>> parsed = new LinkedHashMap<>();
        values.forEach((name, raw) -> {
            CustomFieldDefinition field = byName.get(normalize(name));
            parsed.put(field, parse(field, raw));
        });
        return parsed;
    }

    private static void requireFilled(List<CustomFieldDefinition> definitions, List<CustomFieldValue> values) {
        Set<UUID> filled = values.stream().map(v -> v.getField().getId()).collect(Collectors.toSet());
        List<String> missing = definitions.stream()
                .filter(d -> d.isRequired() && !d.isArchived() && !filled.contains(d.getId()))
                .map(CustomFieldDefinition::getName)
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Required custom field(s) missing: " + String.join(", ", missing));
        }
    }

    /** The rows a raw JSON value becomes; empty means "no value". */
    private static List<CustomFieldValue> parse(CustomFieldDefinition field, Object raw) {
        if (raw == null) {
            return List.of();
        }
        return switch (field.getFieldType()) {
            case TEXT -> parseText(field, raw);
            case NUMBER -> parseNumber(field, raw);
            case DATE -> parseDate(field, raw);
            case SELECT -> parseSelect(field, raw);
            case MULTI_SELECT -> parseMultiSelect(field, raw);
        };
    }

    private static List<CustomFieldValue> parseText(CustomFieldDefinition field, Object raw) {
        String text = requireText(field, raw).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw invalid(field, "must be at most " + MAX_TEXT_LENGTH + " characters");
        }
        return List.of(textValue(text));
    }

    private static List<CustomFieldValue> parseNumber(CustomFieldDefinition field, Object raw) {
        if (raw instanceof String s && s.isBlank()) {
            return List.of();
        }
        if (!(raw instanceof Number) && !(raw instanceof String)) {
            throw invalid(field, "expects a number");
        }
        BigDecimal number;
        try {
            number = new BigDecimal(raw.toString().trim());
        } catch (NumberFormatException e) {
            throw invalid(field, "expects a number, got '" + raw + "'");
        }
        BigDecimal stripped = number.stripTrailingZeros();
        if (stripped.scale() > MAX_DECIMAL_PLACES) {
            throw invalid(field, "allows at most " + MAX_DECIMAL_PLACES + " decimal places");
        }
        if (stripped.precision() - stripped.scale() > MAX_INTEGER_DIGITS) {
            throw invalid(field, "allows at most " + MAX_INTEGER_DIGITS + " digits before the decimal point");
        }
        CustomFieldValue value = new CustomFieldValue();
        value.setValueNumber(number);
        return List.of(value);
    }

    private static List<CustomFieldValue> parseDate(CustomFieldDefinition field, Object raw) {
        String text = requireText(field, raw).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        CustomFieldValue value = new CustomFieldValue();
        try {
            value.setValueDate(LocalDate.parse(text));
        } catch (DateTimeParseException e) {
            throw invalid(field, "expects a date as yyyy-MM-dd, got '" + text + "'");
        }
        return List.of(value);
    }

    private static List<CustomFieldValue> parseSelect(CustomFieldDefinition field, Object raw) {
        String text = requireText(field, raw).trim();
        return text.isEmpty() ? List.of() : List.of(textValue(canonicalOption(field, text)));
    }

    private static List<CustomFieldValue> parseMultiSelect(CustomFieldDefinition field, Object raw) {
        List<String> options = new ArrayList<>();
        for (Object item : multiSelectItems(raw)) {
            String text = requireText(field, item).trim();
            if (!text.isEmpty()) {
                String option = canonicalOption(field, text);
                if (!options.contains(option)) {
                    options.add(option);
                }
            }
        }
        return options.stream().map(CustomFieldValueWriter::textValue).toList();
    }

    /** A list, or one string of ;-separated options as a CSV cell holds; options can't contain ';'. */
    private static Collection<?> multiSelectItems(Object raw) {
        if (raw instanceof Collection<?> items) {
            return items;
        }
        if (raw instanceof String text) {
            return List.of(text.split(MULTI_SELECT_SEPARATOR));
        }
        return List.of(raw);
    }

    /** Options match ignoring case; the stored label is always the definition's spelling. */
    private static String canonicalOption(CustomFieldDefinition field, String text) {
        return field.getOptions().stream()
                .filter(option -> option.equalsIgnoreCase(text))
                .findFirst()
                .orElseThrow(() -> invalid(field, "has no option '" + text + "'. Options: "
                        + String.join(", ", field.getOptions())));
    }

    private static String requireText(CustomFieldDefinition field, Object raw) {
        if (raw instanceof String text) {
            return text;
        }
        throw invalid(field, "expects text, got " + raw);
    }

    private static CustomFieldValue textValue(String text) {
        CustomFieldValue value = new CustomFieldValue();
        value.setValueText(text);
        return value;
    }

    private static IllegalArgumentException invalid(CustomFieldDefinition field, String problem) {
        return new IllegalArgumentException("Custom field '" + field.getName() + "' " + problem);
    }

    static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
