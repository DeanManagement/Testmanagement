package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.entity.Attachment;
import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.project.internal.service.AttachmentService;
import com.deanmanagement.testmanagement.project.internal.service.StepAttachmentService;
import com.deanmanagement.testmanagement.project.internal.service.StepExpansion;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Files attached to a test case (PRD-044). {@code get_test_case} lists them; these fetch, add and
 * remove one. Type sniffing, the per-case count and the project quota are {@link AttachmentService}'s
 * rules, the same ones the UI upload goes through.
 */
@Service
@InToolGroup(McpToolGroup.AUTHORING)
@RequiredArgsConstructor
public class TestCaseAttachmentTools {

    /** Base64 grows a file by a third, and the whole of it lands in the agent's context. */
    static final long MAX_DOWNLOAD_BYTES = 5L * 1024 * 1024;

    private final McpCallerContext callerContext;
    private final AttachmentService attachmentService;
    private final StepAttachmentService stepAttachmentService;
    private final TestCaseRepository testCaseRepository;
    private final McpWriteThrottle writeThrottle;

    @McpTool(
            name = "download_test_case_attachment",
            description = """
                    The content of one file attached to a test case, base64-encoded, with its type.
                    attachmentId comes from the attachments of get_test_case, a file on one of its
                    steps included. Files over 5 MB are
                    refused: ask a human to open those in the app.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.AttachmentContent downloadTestCaseAttachment(
            @McpToolParam(description = "Test case key (PROJ-12) or UUID") String testCaseIdOrKey,
            @McpToolParam(description = "Attachment UUID from get_test_case") UUID attachmentId) {

        var caller = callerContext.require();
        Attachment attachment = attachmentService.get(caller.projectId(),
                testCaseId(caller, testCaseIdOrKey), attachmentId);
        if (attachment.getSizeBytes() > MAX_DOWNLOAD_BYTES) {
            throw new McpToolException(attachment.getFileName() + " is "
                    + attachment.getSizeBytes() / (1024 * 1024) + " MB; files over "
                    + MAX_DOWNLOAD_BYTES / (1024 * 1024) + " MB cannot be fetched over MCP. Ask a "
                    + "human to open it in the app.");
        }
        return new McpDtos.AttachmentContent(attachment.getId(), attachment.getFileName(),
                attachment.getContentType(), attachment.getSizeBytes(),
                Base64.getEncoder().encodeToString(attachment.getData()));
    }

    @McpTool(
            name = "add_test_case_attachment",
            description = """
                    Attach a file to a test case, e.g. a sample input file or a reference
                    screenshot. contentBase64: the file's bytes, base64-encoded, at most 2 MB
                    decoded; larger files have to be attached by a human in the UI.
                    contentType: image/png, image/jpeg, image/gif, image/webp, application/pdf,
                    text/plain, text/csv, application/json and similar. The bytes must match the
                    type; SVG and HTML are refused. A case holds 20 attachments by default.
                    stepNumber: attach to that step instead of the whole case, numbered as
                    get_test_case lists the steps (1 = first); a step holds 20 too. A step that comes
                    from a shared step belongs to the shared step, so its file shows in every case
                    using it. Replacing the steps with update_test_case keeps files by position.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.Attachment addTestCaseAttachment(
            @McpToolParam(description = "Test case key (PROJ-12) or UUID") String testCaseIdOrKey,
            @McpToolParam(description = "File name shown to humans, e.g. invoice-sample.pdf") String fileName,
            @McpToolParam(description = "MIME type of the file, e.g. application/pdf") String contentType,
            @McpToolParam(description = "The file's bytes, base64-encoded") String contentBase64,
            @McpToolParam(description = "Step number (1 = first) to attach to; omit to attach to the case",
                    required = false) Integer stepNumber) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        byte[] data = BugReportTools.decodeAttachment(contentBase64);
        TestCase testCase = McpTestCaseReferences.resolve(testCaseRepository, caller.projectId(), testCaseIdOrKey);
        if (stepNumber == null) {
            AttachmentSummary stored = attachmentService.upload(caller.projectId(), testCase.getId(), fileName,
                    contentType, data, caller.userId());
            return new McpDtos.Attachment(stored.id(), stored.fileName(), stored.contentType(), stored.sizeBytes());
        }
        List<TestStep> steps = StepExpansion.expandedSteps(testCase.getSteps());
        if (stepNumber < 1 || stepNumber > steps.size()) {
            throw new McpToolException("Step " + stepNumber + " does not exist: " + testCase.getKey() + " has "
                    + steps.size() + " steps.");
        }
        AttachmentSummary stored = stepAttachmentService.uploadToStep(caller.projectId(),
                steps.get(stepNumber - 1).getId(), fileName, contentType, data, caller.userId());
        return new McpDtos.Attachment(stored.id(), stored.fileName(), stored.contentType(), stored.sizeBytes(),
                stepNumber);
    }

    @McpTool(
            name = "delete_test_case_attachment",
            description = """
                    Permanently remove a file attached to a test case or one of its steps. Cannot be
                    undone.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = false))
    @Transactional
    public McpDtos.Deleted deleteTestCaseAttachment(
            @McpToolParam(description = "Test case key (PROJ-12) or UUID") String testCaseIdOrKey,
            @McpToolParam(description = "Attachment UUID from get_test_case") UUID attachmentId) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        UUID testCaseId = testCaseId(caller, testCaseIdOrKey);
        String fileName = attachmentService.get(caller.projectId(), testCaseId, attachmentId).getFileName();
        attachmentService.delete(caller.projectId(), testCaseId, attachmentId, caller.userId());
        return new McpDtos.Deleted("ATTACHMENT", attachmentId, fileName);
    }

    private UUID testCaseId(McpCallerContext.Caller caller, String testCaseIdOrKey) {
        return McpTestCaseReferences.resolve(testCaseRepository, caller.projectId(), testCaseIdOrKey)
                .getId();
    }
}
