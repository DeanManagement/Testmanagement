package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.testCaseLink.TestCaseLinkResponse;
import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseLinkType;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Links between test cases (TM07); get_test_case shows them. */
@Service
@InToolGroup(McpToolGroup.AUTHORING)
@RequiredArgsConstructor
public class TestCaseLinkTools {

    private final McpCallerContext callerContext;
    private final TestCaseRepository testCaseRepository;
    private final TestCaseLinkService linkService;
    private final McpWriteThrottle writeThrottle;

    @McpTool(
            name = "link_test_cases",
            description = """
                    Link two test cases of this project. linkType PRECONDITION means the source
                    needs the target run first (a reusable setup case, e.g. "Log in as admin");
                    RELATES_TO is informational. Linking twice is harmless. Returns the source
                    case's links in both directions.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.TestCaseLinks linkTestCases(
            @McpToolParam(description = "Source test case key (PROJ-12) or UUID") String source,
            @McpToolParam(description = "Target test case key or UUID; for PRECONDITION, the case to run first")
            String target,
            @McpToolParam(description = "PRECONDITION or RELATES_TO") TestCaseLinkType linkType) {

        var caller = callerContext.requireWriter();
        if (linkType == null) {
            throw new McpToolException("linkType is required: PRECONDITION or RELATES_TO.");
        }
        writeThrottle.recordWrite(caller.apiKeyId());
        return new McpDtos.TestCaseLinks(linkService.link(caller.projectId(), resolve(caller.projectId(), source),
                        resolve(caller.projectId(), target), linkType, caller.userId()).stream()
                .map(TestCaseLinkTools::toRef)
                .toList());
    }

    @McpTool(
            name = "unlink_test_cases",
            description = """
                    Remove the link between two test cases, whichever way round it was made.
                    Without linkType both PRECONDITION and RELATES_TO links go. Neither case is
                    deleted; unlinking cases that were not linked is harmless.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.UnlinkedTestCases unlinkTestCases(
            @McpToolParam(description = "One test case key or UUID") String source,
            @McpToolParam(description = "The other test case key or UUID") String target,
            @McpToolParam(description = "PRECONDITION or RELATES_TO; omit for both", required = false)
            TestCaseLinkType linkType) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        int removed = linkService.unlink(caller.projectId(), resolve(caller.projectId(), source),
                resolve(caller.projectId(), target), linkType, caller.userId());
        return new McpDtos.UnlinkedTestCases(removed);
    }

    static McpDtos.TestCaseLinkRef toRef(TestCaseLinkResponse link) {
        return new McpDtos.TestCaseLinkRef(link.linkType().name(), link.direction().name(),
                link.testCaseId(), link.testCaseKey(), link.testCaseTitle());
    }

    private UUID resolve(UUID projectId, String idOrKey) {
        return McpTestCaseReferences.resolve(testCaseRepository, projectId, idOrKey).getId();
    }
}
