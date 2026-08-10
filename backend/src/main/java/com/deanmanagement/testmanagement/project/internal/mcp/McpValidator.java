package com.deanmanagement.testmanagement.project.internal.mcp;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies the request DTOs' bean-validation constraints on the MCP path.
 *
 * <p>They would otherwise not run at all. {@code @NotBlank}/{@code @Size} live on
 * {@code CreateTestCaseRequest} and friends, but they only fire because the controllers annotate
 * the body {@code @Valid} — and the tools construct those records by hand and call the services
 * directly. Without this, a 5 000-character title reaches the database and comes back as a JDBC
 * constraint message, and an empty-string title is stored happily.
 *
 * <p>Violations are rendered as one readable line naming each offending field, because the reader
 * is a model deciding what to send next.
 */
@Component
@RequiredArgsConstructor
public class McpValidator {

    private final Validator validator;

    public <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (violations.isEmpty()) {
            return;
        }
        String detail = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        throw new McpToolException("Invalid arguments — " + detail);
    }
}
