package com.deanmanagement.testmanagement.project.internal.buildserver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * (De)serialises the string-to-string parameter maps stored on workflows and pipeline runs.
 * Order-preserving, so parameters render in the order the admin defined them.
 */
@Component
@RequiredArgsConstructor
public class ParameterJsonCodec {

    private final ObjectMapper objectMapper;

    public String toJson(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (Exception e) {
            throw new IllegalArgumentException("Parameters could not be serialised", e);
        }
    }

    public Map<String, String> fromJson(String json) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return parameters;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            node.properties().forEach(entry ->
                    parameters.put(entry.getKey(), entry.getValue().asString()));
            return parameters;
        } catch (Exception e) {
            // Stored by us, so this is defensive; an unreadable blob degrades to "no parameters".
            return parameters;
        }
    }
}
