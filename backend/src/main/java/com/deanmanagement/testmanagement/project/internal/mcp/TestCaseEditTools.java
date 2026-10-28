package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseCopyService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edits to a test case that are smaller than {@code update_test_case}: copying a case (TM09). Kept
 * apart from {@link TestCaseTools} so each class stays one concern.
 */
@Service
@InToolGroup(McpToolGroup.AUTHORING)
@RequiredArgsConstructor
public class TestCaseEditTools {

    private static final int MAX_TITLE_LENGTH = 255;

    private final McpCallerContext callerContext;
    private final TestCaseRepository testCaseRepository;
    private final TestCaseCopyService copyService;
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
}
