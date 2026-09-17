package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.dto.issuetracker.CreateIssueLinkRequest;
import com.deanmanagement.testmanagement.project.internal.dto.issuetracker.IssueLinkResponse;
import com.deanmanagement.testmanagement.project.internal.service.IssueLinkService;
import com.deanmanagement.testmanagement.project.internal.service.IssueTrackerConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Issues in the project's external tracker (PRD-010), attached to the test result that found them
 * — the {@code link_issue} follow-on PRD-027 §9 promised.
 *
 * <p>Complementary to {@link BugReportTools}: a native bug report needs no configuration, this
 * needs an administrator to have connected a tracker. Linking and filing are two tools rather than
 * one with a {@code create} flag, so that filing — which writes to a system outside this one —
 * cannot happen by getting an optional argument wrong. There is no unlink tool, per PRD-025's
 * no-deletion rule.
 *
 * <p>Not {@code @Transactional} here: the service calls the tracker over the network.
 */
@Service
@InToolGroup(McpToolGroup.ISSUE_TRACKER)
@RequiredArgsConstructor
public class IssueLinkTools {

    private final McpCallerContext callerContext;
    private final IssueLinkService issueLinkService;
    private final IssueTrackerConfigService trackerConfigService;
    private final McpWriteThrottle writeThrottle;
    private final McpValidator validator;

    @McpTool(
            name = "list_issue_links",
            description = """
                    The external-tracker issues linked to one test result, with the state each had
                    when last checked.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public McpDtos.IssueLinkList listIssueLinks(
            @McpToolParam(description = "Result UUID from get_test_run") UUID resultId) {
        var caller = callerContext.require();
        List<McpDtos.IssueLink> issues =
                issueLinkService.findForResult(caller.projectId(), requireResultId(resultId))
                        .stream().map(IssueLinkTools::toIssueLink).toList();
        return new McpDtos.IssueLinkList(issues, issues.size());
    }

    @McpTool(
            name = "link_issue",
            description = """
                    Attach an issue that ALREADY EXISTS in the project's external tracker to a test
                    result. issueReference is whatever the tracker calls it — typically the issue
                    number. Linking the same issue twice just refreshes its state.
                    To file a new issue use create_linked_issue; with no tracker configured, use
                    create_bug_report instead.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = true))
    public McpDtos.IssueLink linkIssue(
            @McpToolParam(description = "Result UUID from get_test_run") UUID resultId,
            @McpToolParam(description = "The existing issue's number or key in the tracker")
            String issueReference) {

        if (issueReference == null || issueReference.isBlank()) {
            throw new McpToolException("issueReference is required. To file a new issue use "
                    + "create_linked_issue.");
        }
        return link(resultId, new CreateIssueLinkRequest(issueReference, false, null, null));
    }

    @McpTool(
            name = "create_linked_issue",
            description = """
                    File a NEW issue in the project's external tracker and attach it to a test
                    result. This writes to a system outside Testmanagement where people will be
                    notified, so check list_issue_links first and do not file the same failure
                    twice. Omit title and body to have them written from the result — test case
                    key, run key and the recorded actual results.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false,
                    openWorldHint = true))
    public McpDtos.IssueLink createLinkedIssue(
            @McpToolParam(description = "Result UUID from get_test_run") UUID resultId,
            @McpToolParam(description = "Issue title, max 500 characters", required = false)
            String title,
            @McpToolParam(description = "Issue body, max 20000 characters", required = false)
            String body) {
        return link(resultId, new CreateIssueLinkRequest(null, true, title, body));
    }

    private McpDtos.IssueLink link(UUID resultId, CreateIssueLinkRequest request) {
        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        validator.validate(request);
        if (trackerConfigService.activeConfig(caller.projectId()).isEmpty()) {
            throw new McpToolException("This project has no issue tracker configured, which only "
                    + "a project administrator can do. Use create_bug_report to file the defect "
                    + "natively instead.");
        }
        return toIssueLink(issueLinkService.link(caller.projectId(), requireResultId(resultId),
                request, caller.userId()));
    }

    private static UUID requireResultId(UUID resultId) {
        if (resultId == null) {
            throw new McpToolException("resultId is required: the result's id from get_test_run.");
        }
        return resultId;
    }

    private static McpDtos.IssueLink toIssueLink(IssueLinkResponse link) {
        return new McpDtos.IssueLink(link.id(), link.testResultId(),
                link.provider() == null ? null : link.provider().name(), link.externalId(),
                link.url(), link.title(), link.state() == null ? null : link.state().name());
    }
}
