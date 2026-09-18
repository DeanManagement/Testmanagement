package com.deanmanagement.testmanagement.project.internal.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/** Stores a list of strings as a JSON array; an empty or absent list is stored as null. */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<String>> LIST_OF_STRINGS = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> values) {
        return values == null || values.isEmpty() ? null : MAPPER.writeValueAsString(values);
    }

    @Override
    public List<String> convertToEntityAttribute(String column) {
        return column == null || column.isBlank() ? List.of() : MAPPER.readValue(column, LIST_OF_STRINGS);
    }
}
