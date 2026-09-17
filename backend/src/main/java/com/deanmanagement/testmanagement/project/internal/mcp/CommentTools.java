package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.comment.CommentResponse;
import com.deanmanagement.testmanagement.project.internal.dto.comment.CreateCommentRequest;
import com.deanmanagement.testmanagement.project.internal.entity.CommentEntityType;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The discussion thread on a test case or on one result of a run.
 *
 * <p>Not the same thing as the {@code comment} argument of {@code record_test_result}: that is the
 * result's own evidence field and is overwritten on re-record, whereas these are an append-only
 * conversation with humans. No edit or delete tool, per PRD-025's no-deletion rule.
 */
@Service
@RequiredArgsConstructor
public class CommentTools {

    private final McpCallerContext callerContext;
    private final CommentService commentService;
    private final TestCaseRepository testCaseRepository;
    private final McpWriteThrottle writeThrottle;
    private final McpValidator validator;

    private record Target(CommentEntityType type, UUID id) {}

    @McpTool(
            name = "list_comments",
            description = """
                    The discussion on a test case, or on one result of a test run, oldest first.
                    Pass exactly one of testCaseIdOrKey or resultId (the result's id from
                    get_test_run).
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.CommentList listComments(
            @McpToolParam(description = "Test case key (PROJ-12) or UUID", required = false)
            String testCaseIdOrKey,
            @McpToolParam(description = "Result UUID from get_test_run", required = false)
            UUID resultId) {

        var caller = callerContext.require();
        Target target = target(caller.projectId(), testCaseIdOrKey, resultId);
        List<McpDtos.Comment> comments =
                commentService.findByEntity(caller.projectId(), target.type(), target.id()).stream()
                        .map(CommentTools::toComment)
                        .toList();
        return new McpDtos.CommentList(comments, comments.size());
    }

    @McpTool(
            name = "add_comment",
            description = """
                    Add a comment to the discussion on a test case, or on one result of a test run
                    — for a question or a note to the humans on the project. Max 2000 characters.
                    Pass exactly one of testCaseIdOrKey or resultId.
                    To record what you observed while executing, use the comment argument of
                    record_test_result instead; that is the result's evidence, this is the thread.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.Comment addComment(
            @McpToolParam(description = "The comment text, max 2000 characters") String content,
            @McpToolParam(description = "Test case key (PROJ-12) or UUID", required = false)
            String testCaseIdOrKey,
            @McpToolParam(description = "Result UUID from get_test_run", required = false)
            UUID resultId) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());

        var request = new CreateCommentRequest(content);
        validator.validate(request);

        Target target = target(caller.projectId(), testCaseIdOrKey, resultId);
        return toComment(commentService.create(caller.projectId(), target.type(), target.id(),
                request, caller.userId()));
    }

    private Target target(UUID projectId, String testCaseIdOrKey, UUID resultId) {
        boolean hasTestCase = testCaseIdOrKey != null && !testCaseIdOrKey.isBlank();
        if (hasTestCase == (resultId != null)) {
            throw new McpToolException("Pass exactly one of testCaseIdOrKey or resultId.");
        }
        if (hasTestCase) {
            return new Target(CommentEntityType.TEST_CASE,
                    McpTestCaseReferences.resolve(testCaseRepository, projectId, testCaseIdOrKey)
                            .getId());
        }
        // CommentService checks the result belongs to this project and reports not-found otherwise.
        return new Target(CommentEntityType.TEST_RESULT, resultId);
    }

    private static McpDtos.Comment toComment(CommentResponse comment) {
        return new McpDtos.Comment(comment.id(), comment.content(), comment.authorDisplayName(),
                comment.createdAt());
    }
}
