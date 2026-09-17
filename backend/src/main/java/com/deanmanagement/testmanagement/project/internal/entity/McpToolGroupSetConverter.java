package com.deanmanagement.testmanagement.project.internal.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Stores a set of {@link McpToolGroup}s as comma-separated names; an empty set is stored as null. */
@Converter
public class McpToolGroupSetConverter implements AttributeConverter<Set<McpToolGroup>, String> {

    private static final String SEPARATOR = ",";

    @Override
    public String convertToDatabaseColumn(Set<McpToolGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        return groups.stream().sorted().map(Enum::name).collect(Collectors.joining(SEPARATOR));
    }

    @Override
    public Set<McpToolGroup> convertToEntityAttribute(String column) {
        if (column == null || column.isBlank()) {
            return null;
        }
        return Arrays.stream(column.split(SEPARATOR))
                .map(String::trim)
                .map(McpToolGroup::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(McpToolGroup.class)));
    }
}
