package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseCopyService;
import com.deanmanagement.testmanagement.project.internal.service.TestStepEditService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Function;

/**
 * Edits to a test case that are smaller than {@code update_test_case}: copying a case (TM09) and
 * changing one step at a time (TM06). Kept apart from {@link TestCaseTools} so each class stays one
 * concern.
 */
@Service
@InToolGroup(McpToolGroup.AUTHORING)
@RequiredArgsConstructor
public class TestCaseEditTools {

    private static final int MAX_TITLE_LENGTH = 255;

    private final McpCallerContext callerContext;
    private final TestCaseRepository testCaseRepository;
    private final TestCaseCopyService copyService;
    private final TestStepEditService stepEditService;
    private final McpWriteThrottle writeThrottle;

    @McpTool(
            name = "copy_test_case",
            description = """
                    Copy a test case into a new one: description, preconditions, priority, labels,
                    folder, custom fields, parameter sets, steps (with test data, images and shared
                    step references) and attachments. The copy is a new DRAFT case with its own key;
                    results, history, approval and requirement links are not copied.
                    title defaults to the original's title plus " (copy)".
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.CreatedTestCase copyTestCase(
            @McpToolParam(description = "Test case key (PROJ-12) or UUID of the case to copy") String idOrKey,
            @McpToolParam(description = "Title of the copy, max 255 characters", required = false) String title) {

        var caller = callerContext.requireWriter();
        if (title != null && title.length() > MAX_TITLE_LENGTH) {
            throw new McpToolException("title must be at most " + MAX_TITLE_LENGTH + " characters.");
        }
        writeThrottle.recordWrite(caller.apiKeyId());
        TestCase source = McpTestCaseReferences.resolve(testCaseRepository, caller.projectId(), idOrKey);
        TestCaseResponse copy = copyService.copy(caller.projectId(), source.getId(), title, caller.userId());
        return new McpDtos.CreatedTestCase(copy.id(), copy.key(), copy.title(), copy.status());
    }

    @McpTool(
            name = "insert_test_step",
            description = """
                    Insert ONE step into a test case at a position, leaving every other step as it
                    is (images and screenshots on them included) — prefer this to update_test_case
                    when you only add a step. position is 1-based in the case's own step list,
                    where a shared step counts as ONE step even though get_test_case shows its steps
                    expanded; omit it to append. Give an action (with optional expectedResult and
                    testData), or a sharedStepId to insert a shared step.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.CreatedTestCase insertTestStep(
            @McpToolParam(description = "Test case key (PROJ-12) or UUID") String idOrKey,
            @McpToolParam(description = "1-based position the new step takes; omit to append", required = false)
            Integer position,
            @McpToolParam(description = "What the tester does", required = false) String action,
            @McpToolParam(description = "What should happen", required = false) String expectedResult,
            @McpToolParam(description = "Data the step uses", required = false) String testData,
            @McpToolParam(description = "Insert this shared step instead of text (see list_shared_steps)",
                    required = false) UUID sharedStepId) {

        if (sharedStepId == null && (action == null || action.isBlank())) {
            throw new McpToolException("A step needs an action, or a sharedStepId.");
        }
        var request = new TestStepRequest(action, expectedResult, testData, sharedStepId);
        return editSteps(idOrKey, (testCaseId) -> {
            var caller = callerContext.require();
            int at = position == null ? stepCount(testCaseId) : position - 1;
            return stepEditService.insertStep(caller.projectId(), testCaseId, at, request, caller.userId());
        });
    }

    @McpTool(
            name = "move_test_step",
            description = """
                    Move ONE step of a test case to another position; the steps in between shift by
                    one and every step keeps its images. Positions are 1-based in the case's own
                    step list, where a shared step counts as ONE step.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.CreatedTestCase moveTestStep(
            @McpToolParam(description = "Test case key (PROJ-12) or UUID") String idOrKey,
            @McpToolParam(description = "1-based position of the step to move") int fromPosition,
            @McpToolParam(description = "1-based position it should end up at") int toPosition) {

        return editSteps(idOrKey, (testCaseId) -> {
            var caller = callerContext.require();
            return stepEditService.moveStep(caller.projectId(), testCaseId, fromPosition - 1, toPosition - 1,
                    caller.userId());
        });
    }

    @McpTool(
            name = "remove_test_step",
            description = """
                    Remove ONE step of a test case, with its image; the other steps and their images
                    stay. position is 1-based in the case's own step list, where a shared step counts
                    as ONE step (removing it removes the reference, not the shared step).
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = false))
    @Transactional
    public McpDtos.CreatedTestCase removeTestStep(
            @McpToolParam(description = "Test case key (PROJ-12) or UUID") String idOrKey,
            @McpToolParam(description = "1-based position of the step to remove") int position) {

        return editSteps(idOrKey, (testCaseId) -> {
            var caller = callerContext.require();
            return stepEditService.removeStep(caller.projectId(), testCaseId, position - 1, caller.userId());
        });
    }

    /** Writer check, throttle and case lookup shared by the step tools; a bad position reads as a refusal. */
    private McpDtos.CreatedTestCase editSteps(String idOrKey, Function<UUID, TestCaseResponse> edit) {
        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        TestCase testCase = McpTestCaseReferences.resolve(testCaseRepository, caller.projectId(), idOrKey);
        try {
            TestCaseResponse updated = edit.apply(testCase.getId());
            return new McpDtos.CreatedTestCase(updated.id(), updated.key(), updated.title(), updated.status());
        } catch (IllegalArgumentException e) {
            throw new McpToolException(e.getMessage());
        }
    }

    private int stepCount(UUID testCaseId) {
        return testCaseRepository.findById(testCaseId).map(tc -> tc.getSteps().size()).orElse(0);
    }
}
