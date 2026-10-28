package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.customField.CreateCustomFieldRequest;
import com.deanmanagement.testmanagement.project.internal.dto.customField.CustomFieldResponse;
import com.deanmanagement.testmanagement.project.internal.dto.customField.UpdateCustomFieldRequest;
import com.deanmanagement.testmanagement.project.internal.dto.environment.CreateEnvironmentRequest;
import com.deanmanagement.testmanagement.project.internal.dto.environment.EnvironmentResponse;
import com.deanmanagement.testmanagement.project.internal.dto.environment.UpdateEnvironmentRequest;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldType;
import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.service.CustomFieldService;
import com.deanmanagement.testmanagement.project.internal.service.ProjectEnvironmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Environments and custom fields (TM34, TM35): project settings, so every tool here needs an ADMIN
 * key, as the settings pages need a project admin. Both are addressed by name, the way every other
 * tool refers to them.
 */
@Service
@InToolGroup(McpToolGroup.AUTHORING)
@RequiredArgsConstructor
public class ProjectSettingsTools {

    private final McpCallerContext callerContext;
    private final ProjectEnvironmentService environmentService;
    private final CustomFieldService customFieldService;
    private final McpWriteThrottle writeThrottle;
    private final McpValidator validator;

    // --- environments ----------------------------------------------------------------------

    @McpTool(
            name = "create_environment",
            description = """
                    Add an environment (e.g. "staging") that runs and bug reports can then name.
                    Requires an ADMIN key. Check list_environments first: a near-duplicate such as
                    "Staging" next to "staging" splits the project's history in two.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    @Transactional
    public McpDtos.Environment createEnvironment(
            @McpToolParam(description = "Name, max 255 characters") String name,
            @McpToolParam(description = "Optional description", required = false) String description) {
        var caller = callerContext.requireAdmin();
        writeThrottle.recordWrite(caller.apiKeyId());
        var request = new CreateEnvironmentRequest(name, description);
        validator.validate(request);
        return toDto(environmentService.create(caller.projectId(), request, caller.userId()));
    }

    @McpTool(
            name = "update_environment",
            description = """
                    Rename an environment, change its description, or archive / restore it.
                    Requires an ADMIN key. Only what you pass changes; "" clears the description.
                    A rename carries over to every run and bug report that names it. Archiving
                    hides it from list_environments; history keeps pointing at it.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.Environment updateEnvironment(
            @McpToolParam(description = "Current name of the environment") String name,
            @McpToolParam(description = "New name", required = false) String newName,
            @McpToolParam(description = "New description; \"\" clears it", required = false) String description,
            @McpToolParam(description = "true archives it, false restores it", required = false) Boolean archived) {
        var caller = callerContext.requireAdmin();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (newName == null && description == null && archived == null) {
            throw new McpToolException("Nothing to update: pass newName, description or archived.");
        }
        var request = new UpdateEnvironmentRequest(newName, description, null, archived);
        validator.validate(request);
        EnvironmentResponse environment = environmentNamed(caller, name);
        return toDto(environmentService.update(caller.projectId(), environment.id(), request, caller.userId()));
    }

    // --- custom fields ---------------------------------------------------------------------

    @McpTool(
            name = "create_custom_field",
            description = """
                    Add a custom field to test cases, test runs, bug reports or requirements.
                    Requires an ADMIN key. fieldType: TEXT | NUMBER | DATE | SELECT | MULTI_SELECT;
                    SELECT and MULTI_SELECT need options. required makes the field mandatory in the
                    UI and in create tools from then on, so leave it off unless a human asked.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    @Transactional
    public McpDtos.CustomField createCustomField(
            @McpToolParam(description = "TEST_CASE, TEST_RUN, BUG_REPORT or REQUIREMENT") CustomFieldEntityType entityType,
            @McpToolParam(description = "Name, max 100 characters; values are keyed by it") String name,
            @McpToolParam(description = "TEXT, NUMBER, DATE, SELECT or MULTI_SELECT") CustomFieldType fieldType,
            @McpToolParam(description = "Choices for SELECT / MULTI_SELECT", required = false) List<String> options,
            @McpToolParam(description = "Whether a value is mandatory, default false", required = false) Boolean required) {
        var caller = callerContext.requireAdmin();
        writeThrottle.recordWrite(caller.apiKeyId());
        var request = new CreateCustomFieldRequest(entityType, name, fieldType, options, required);
        validator.validate(request);
        return toDto(customFieldService.create(caller.projectId(), request, caller.userId()));
    }

    @McpTool(
            name = "update_custom_field",
            description = """
                    Rename a custom field, replace its options, make it required or optional, or
                    archive / restore it. Requires an ADMIN key. Only what you pass changes.
                    options replaces the whole list; removing a choice that values still use is
                    refused. Renaming a field renames the key agents must use for its values.
                    Archiving keeps existing values but hides the field from new input.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.CustomField updateCustomField(
            @McpToolParam(description = "TEST_CASE, TEST_RUN, BUG_REPORT or REQUIREMENT") CustomFieldEntityType entityType,
            @McpToolParam(description = "Current name of the field") String name,
            @McpToolParam(description = "New name", required = false) String newName,
            @McpToolParam(description = "Full new list of choices (SELECT / MULTI_SELECT)", required = false)
            List<String> options,
            @McpToolParam(description = "Whether a value is mandatory", required = false) Boolean required,
            @McpToolParam(description = "true archives it, false restores it", required = false) Boolean archived) {
        var caller = callerContext.requireAdmin();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (newName == null && options == null && required == null && archived == null) {
            throw new McpToolException("Nothing to update: pass newName, options, required or archived.");
        }
        CustomFieldResponse field = fieldNamed(caller, entityType, name);
        if (newName != null || options != null || required != null) {
            var request = new UpdateCustomFieldRequest(newName, null, options, null, required, null);
            validator.validate(request);
            field = customFieldService.update(caller.projectId(), field.id(), request, caller.userId());
        }
        if (archived != null && archived != field.archived()) {
            field = archived
                    ? customFieldService.archive(caller.projectId(), field.id(), caller.userId())
                    : customFieldService.unarchive(caller.projectId(), field.id(), caller.userId());
        }
        return toDto(field);
    }

    private EnvironmentResponse environmentNamed(McpCallerContext.Caller caller, String name) {
        List<EnvironmentResponse> all = environmentService.list(caller.projectId(), true);
        return all.stream()
                .filter(e -> name != null && e.name().equalsIgnoreCase(name.strip()))
                .findFirst()
                .orElseThrow(() -> new McpToolException("Unknown environment \"" + name + "\". Known: "
                        + all.stream().map(e -> "\"" + e.name() + "\"").collect(Collectors.joining(", ")) + "."));
    }

    private CustomFieldResponse fieldNamed(McpCallerContext.Caller caller, CustomFieldEntityType entityType,
                                           String name) {
        List<CustomFieldResponse> fields = customFieldService.list(caller.projectId(), entityType);
        return fields.stream()
                .filter(f -> f.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new McpToolException("Unknown " + entityType + " field \"" + name + "\". Known: "
                        + fields.stream().map(f -> "\"" + f.name() + "\"").collect(Collectors.joining(", ")) + "."));
    }

    private static McpDtos.Environment toDto(EnvironmentResponse e) {
        return new McpDtos.Environment(e.id(), e.name(), e.description());
    }

    private static McpDtos.CustomField toDto(CustomFieldResponse f) {
        return new McpDtos.CustomField(f.name(), f.entityType(), f.fieldType(),
                f.options().isEmpty() ? null : f.options(), f.required(), f.archived());
    }
}
