package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.dto.parameter.ParameterSetResponse;
import com.deanmanagement.testmanagement.project.internal.dto.parameter.SaveParameterSetRequest;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.service.ParameterSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Parameter sets (PRD-015) — the data rows of a data-driven test case.
 *
 * <p>The run tools have always coped with the results a parameterized case expands into; without
 * these an agent could execute such a case but never author one.
 */
@Service
@InToolGroup(McpToolGroup.AUTHORING)
@RequiredArgsConstructor
public class ParameterSetTools {

    private final McpCallerContext callerContext;
    private final ParameterSetService parameterSetService;
    private final TestCaseRepository testCaseRepository;
    private final McpWriteThrottle writeThrottle;
    private final McpValidator validator;

    @McpTool(
            name = "list_parameter_sets",
            description = """
                    The parameter sets of a test case, in execution order. A case with parameter
                    sets is data-driven: a run holds one result per set, and a {name} placeholder
                    in a step is replaced by that set's value for name.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.ParameterSetList listParameterSets(
            @McpToolParam(description = "Test case key (PROJ-12) or UUID") String testCaseIdOrKey) {

        var caller = callerContext.require();
        UUID testCaseId = testCaseId(caller, testCaseIdOrKey);
        List<McpDtos.ParameterSet> sets =
                parameterSetService.list(caller.projectId(), testCaseId).stream()
                        .map(ParameterSetTools::toParameterSet)
                        .toList();
        return new McpDtos.ParameterSetList(sets, sets.size());
    }

    @McpTool(
            name = "create_parameter_set",
            description = """
                    Add one data row to a test case, making it data-driven. values maps parameter
                    names to values, e.g. {"username": "admin", "expected": "dashboard"}; write
                    {username} in a step's action, expectedResult or testData to use one.
                    Parameter names may contain only letters, digits, dot, dash and underscore.
                    Every run created afterwards gets one result per set — runs that already exist
                    are not changed. A case may have at most 50 sets, and a set name must be unique
                    within its case.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.ParameterSet createParameterSet(
            @McpToolParam(description = "Test case key (PROJ-12) or UUID") String testCaseIdOrKey,
            @McpToolParam(description = "Name of the set, e.g. \"admin user\", max 200 characters")
            String name,
            @McpToolParam(description = "Parameter name to value") Map<String, String> values) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        var request = new SaveParameterSetRequest(name, values, null);
        validator.validate(request);

        return toParameterSet(parameterSetService.create(caller.projectId(),
                testCaseId(caller, testCaseIdOrKey), request));
    }

    @McpTool(
            name = "update_parameter_set",
            description = """
                    Rename a parameter set or replace its values. Only what you pass is changed,
                    but values is replaced as a whole — send every name the set should keep, not
                    just the one you are changing. Results already recorded keep the values they
                    ran with.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.ParameterSet updateParameterSet(
            @McpToolParam(description = "Test case key (PROJ-12) or UUID") String testCaseIdOrKey,
            @McpToolParam(description = "Parameter set UUID from list_parameter_sets")
            UUID parameterSetId,
            @McpToolParam(description = "New name, max 200 characters", required = false) String name,
            @McpToolParam(description = "The complete new parameter name to value map",
                    required = false) Map<String, String> values) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (name == null && values == null) {
            throw new McpToolException("Nothing to update: pass name or values.");
        }

        UUID testCaseId = testCaseId(caller, testCaseIdOrKey);
        ParameterSetResponse current = parameterSetService.list(caller.projectId(), testCaseId)
                .stream()
                .filter(set -> set.id().equals(parameterSetId))
                .findFirst()
                .orElseThrow(() -> new McpToolException("No parameter set " + parameterSetId
                        + " on this test case. Call list_parameter_sets to see what there is."));

        // Merged here because the service is a full replace; see TestPlanningMaintenanceTools.
        var request = new SaveParameterSetRequest(
                name == null ? current.name() : name,
                values == null ? current.values() : values,
                null);
        validator.validate(request);

        return toParameterSet(parameterSetService.update(caller.projectId(), testCaseId,
                parameterSetId, request));
    }

    private UUID testCaseId(McpCallerContext.Caller caller, String testCaseIdOrKey) {
        return McpTestCaseReferences.resolve(testCaseRepository, caller.projectId(),
                testCaseIdOrKey).getId();
    }

    private static McpDtos.ParameterSet toParameterSet(ParameterSetResponse set) {
        return new McpDtos.ParameterSet(set.id(), set.name(), set.values());
    }
}
