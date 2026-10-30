package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportLinkResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.UpdateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.service.BugReportAttachmentService;
import com.deanmanagement.testmanagement.project.internal.service.BugReportLinkService;
import com.deanmanagement.testmanagement.project.internal.service.BugReportRelationService;
import com.deanmanagement.testmanagement.project.internal.service.BugTrackerHandoverService;
import com.deanmanagement.testmanagement.project.internal.dto.issuetracker.IssueLinkResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportLinksResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.RelateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.entity.BugRelationType;
import com.deanmanagement.testmanagement.project.internal.service.BugReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Changing a bug report after it was filed: its text, its occurrences, its attachments, or the
 * report itself. Status moves stay in {@link BugReportTools#changeBugReportStatus}, which demands a
 * reason for the audit log.
 *
 * <p>{@code update_bug_report} is partial over a full-replace service, merged here as in
 * {@link TestPlanningMaintenanceTools} — and for the same reason it must stay
 * {@code @Transactional}: the service then writes the very entity the read loaded, so a field set
 * back to its own value is not dirty and the agent does not become the author of fields it never
 * touched. Links, assignee and custom fields are echoed, never chosen.
 */
@Service
@InToolGroup(McpToolGroup.EXECUTION)
@RequiredArgsConstructor
public class BugReportMaintenanceTools {

    private final McpCallerContext callerContext;
    private final BugReportService bugReportService;
    private final BugReportLinkService linkService;
    private final BugReportRelationService relationService;
    private final BugTrackerHandoverService handoverService;
    private final BugReportAttachmentService attachmentService;
    private final McpEnvironmentGuard environmentGuard;
    private final McpWriteThrottle writeThrottle;
    private final McpValidator validator;

    @McpTool(
            name = "update_bug_report",
            description = """
                    Edit a bug report's text, priority or environment. Only the fields you pass are
                    changed; to CLEAR a text field or the environment pass an empty string "" (the
                    title cannot be cleared). priority: LOW | MEDIUM | HIGH | CRITICAL. environment
                    must be a name from list_environments.
                    labels replaces the bug's labels ([] removes them all). foundInPlan and
                    fixedInPlan take a test plan UUID from list_test_plans, "" clears one.
                    Status is not changed here: use change_bug_report_status. The assignee, the
                    linked result and run, and custom fields are left as they are.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.BugDetail updateBugReport(
            @McpToolParam(description = "Bug key (PROJ-BUG-12) or UUID") String idOrKey,
            @McpToolParam(description = "New title, max 255 characters", required = false) String title,
            @McpToolParam(description = "New description; \"\" clears it", required = false)
            String description,
            @McpToolParam(description = "New steps to reproduce; \"\" clears them", required = false)
            String stepsToReproduce,
            @McpToolParam(description = "New expected behaviour; \"\" clears it", required = false)
            String expectedBehavior,
            @McpToolParam(description = "New actual behaviour; \"\" clears it", required = false)
            String actualBehavior,
            @McpToolParam(description = "LOW, MEDIUM, HIGH or CRITICAL", required = false) Priority priority,
            @McpToolParam(description = "A name from list_environments; \"\" clears it", required = false)
            String environment,
            @McpToolParam(description = "The bug's labels, replacing the current ones", required = false)
            List<String> labels,
            @McpToolParam(description = "Test plan UUID the bug was found in; \"\" clears it", required = false)
            String foundInPlan,
            @McpToolParam(description = "Test plan UUID the fix ships in; \"\" clears it", required = false)
            String fixedInPlan) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (title == null && description == null && stepsToReproduce == null && expectedBehavior == null
                && actualBehavior == null && priority == null && environment == null && labels == null
                && foundInPlan == null && fixedInPlan == null) {
            throw new McpToolException("Nothing to update: pass title, description, stepsToReproduce, "
                    + "expectedBehavior, actualBehavior, priority, environment, labels, foundInPlan or fixedInPlan.");
        }
        if (title != null && title.isBlank()) {
            throw new McpToolException("title must not be blank. Omit it to keep the current title.");
        }
        environmentGuard.requireKnown(caller.projectId(), environment);

        return BugReportTools.detail(attachmentService, BugReportTools.enabled(() -> {
            BugReportResponse current = bugReportService.findById(caller.projectId(), idOrKey);
            var request = new UpdateBugReportRequest(
                    title == null ? current.title() : title,
                    merge(description, current.description()),
                    merge(stepsToReproduce, current.stepsToReproduce()),
                    merge(expectedBehavior, current.expectedBehavior()),
                    merge(actualBehavior, current.actualBehavior()),
                    priority == null ? current.priority() : priority,
                    merge(environment, current.environment()),
                    current.testResultId(), current.testRunId(), current.assigneeId(), null, null,
                    labels == null ? null : new HashSet<>(labels),
                    planId(foundInPlan, current.foundInPlanId()), planId(fixedInPlan, current.fixedInPlanId()));
            validator.validate(request);
            return bugReportService.update(caller.projectId(), current.id(), request, caller.userId());
        }));
    }

    @McpTool(
            name = "unlink_bug_report",
            description = """
                    Undo link_bug_report: record that a bug did NOT show up in this test result
                    after all. Only further occurrences can be unlinked — the result a bug was
                    filed from stays its found-in result. The bug itself is kept.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.BugDetail unlinkBugReport(
            @McpToolParam(description = "Bug key (PROJ-BUG-12) or UUID") String idOrKey,
            @McpToolParam(description = "The test result to unlink it from") UUID testResultId) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (testResultId == null) {
            throw new McpToolException("testResultId is required: the result the bug was linked to.");
        }
        return BugReportTools.detail(attachmentService, BugReportTools.enabled(() -> {
            BugReportResponse bug = bugReportService.findById(caller.projectId(), idOrKey);
            if (testResultId.equals(bug.testResultId())) {
                throw new McpToolException("That is the result " + bug.key() + " was found in, which "
                        + "cannot be unlinked. Only occurrences added by link_bug_report can.");
            }
            UUID linkId = bug.links().stream()
                    .filter(link -> testResultId.equals(link.testResultId()))
                    .map(BugReportLinkResponse::id)
                    .findFirst()
                    .orElseThrow(() -> new McpToolException(bug.key() + " is not linked to result "
                            + testResultId + ". get_bug_report lists its occurrences."));
            return linkService.unlink(caller.projectId(), bug.id().toString(), linkId, caller.userId());
        }));
    }

    @McpTool(
            name = "delete_bug_report_attachment",
            description = """
                    Permanently remove a file attached to a bug report. attachmentId comes from
                    the attachments of get_bug_report. Cannot be undone.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = false))
    @Transactional
    public McpDtos.Deleted deleteBugReportAttachment(
            @McpToolParam(description = "Bug key (PROJ-BUG-12) or UUID") String bugReportId,
            @McpToolParam(description = "Attachment UUID from get_bug_report") UUID attachmentId) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        return BugReportTools.enabled(() -> {
            String fileName = attachmentService.get(caller.projectId(), bugReportId, attachmentId).getFileName();
            attachmentService.delete(caller.projectId(), bugReportId, attachmentId, caller.userId());
            return new McpDtos.Deleted("ATTACHMENT", attachmentId, fileName);
        });
    }

    @McpTool(
            name = "delete_bug_report",
            description = """
                    Permanently delete a bug report with its attachments and occurrence links.
                    Cannot be undone. To record that a bug is fixed, a duplicate or not a defect,
                    use change_bug_report_status instead — that keeps the history.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = false))
    @Transactional
    public McpDtos.Deleted deleteBugReport(
            @McpToolParam(description = "Bug key (PROJ-BUG-12) or UUID") String idOrKey) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        return BugReportTools.enabled(() -> {
            BugReportResponse bug = bugReportService.findById(caller.projectId(), idOrKey);
            bugReportService.delete(caller.projectId(), bug.id(), caller.userId());
            return new McpDtos.Deleted("BUG_REPORT", bug.id(), bug.key());
        });
    }

    /** Null keeps the current value; blank clears it. */
    private static String merge(String requested, String current) {
        if (requested == null) {
            return current;
        }
        return requested.isBlank() ? null : requested;
    }


    @McpTool(
            name = "get_bug_report_relations",
            description = """
                    The test cases, requirements and other bug reports a bug is linked to by hand
                    (relations: BLOCKS or RELATES_TO; outgoing=false means the other bug blocks
                    this one). Where the bug showed up in runs is in get_bug_report instead.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public BugReportLinksResponse getBugReportRelations(
            @McpToolParam(description = "Bug key (PROJ-BUG-12) or UUID") String idOrKey) {
        var caller = callerContext.require();
        return relationService.links(caller.projectId(), idOrKey);
    }

    @McpTool(
            name = "relate_bug_report",
            description = """
                    Link a bug report by hand to a test case, a requirement, or another bug report.
                    Pass exactly one of testCase (key or UUID), requirement (external id or UUID)
                    or relatedBug (bug key or UUID); with relatedBug also pass relation: BLOCKS
                    (this bug blocks relatedBug), BLOCKED_BY or RELATES_TO. Idempotent.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public BugReportLinksResponse relateBugReport(
            @McpToolParam(description = "Bug key (PROJ-BUG-12) or UUID") String idOrKey,
            @McpToolParam(description = "Test case key or UUID", required = false) String testCase,
            @McpToolParam(description = "Requirement external id or UUID", required = false) String requirement,
            @McpToolParam(description = "Other bug key or UUID", required = false) String relatedBug,
            @McpToolParam(description = "BLOCKS, BLOCKED_BY or RELATES_TO; with relatedBug only",
                    required = false) String relation) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        requireExactlyOne(testCase, requirement, relatedBug);
        return BugReportTools.enabled(() -> {
            if (isGiven(testCase)) {
                return relationService.linkTestCase(caller.projectId(), idOrKey, testCase, caller.userId());
            }
            if (isGiven(requirement)) {
                return relationService.linkRequirement(caller.projectId(), idOrKey, requirement, caller.userId());
            }
            if (relation == null) {
                throw new McpToolException("relation is required with relatedBug: BLOCKS, BLOCKED_BY or RELATES_TO.");
            }
            return switch (relation.strip().toUpperCase()) {
                case "BLOCKS" -> relationService.relate(caller.projectId(), idOrKey,
                        new RelateBugReportRequest(relatedBug, BugRelationType.BLOCKS), caller.userId());
                case "RELATES_TO" -> relationService.relate(caller.projectId(), idOrKey,
                        new RelateBugReportRequest(relatedBug, BugRelationType.RELATES_TO), caller.userId());
                case "BLOCKED_BY" -> {
                    relationService.relate(caller.projectId(), relatedBug,
                            new RelateBugReportRequest(idOrKey, BugRelationType.BLOCKS), caller.userId());
                    yield relationService.links(caller.projectId(), idOrKey);
                }
                default -> throw new McpToolException("Unknown relation '" + relation
                        + "'. Use BLOCKS, BLOCKED_BY or RELATES_TO.");
            };
        });
    }

    @McpTool(
            name = "unrelate_bug_report",
            description = """
                    Remove a link made with relate_bug_report. Pass exactly one of testCase,
                    requirement or relationId (the id of a relation from get_bug_report_relations).
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public BugReportLinksResponse unrelateBugReport(
            @McpToolParam(description = "Bug key (PROJ-BUG-12) or UUID") String idOrKey,
            @McpToolParam(description = "Test case key or UUID", required = false) String testCase,
            @McpToolParam(description = "Requirement external id or UUID", required = false) String requirement,
            @McpToolParam(description = "Relation UUID from get_bug_report_relations", required = false)
            UUID relationId) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        requireExactlyOne(testCase, requirement, relationId == null ? null : relationId.toString());
        return BugReportTools.enabled(() -> {
            if (isGiven(testCase)) {
                return relationService.unlinkTestCase(caller.projectId(), idOrKey, testCase, caller.userId());
            }
            if (isGiven(requirement)) {
                return relationService.unlinkRequirement(caller.projectId(), idOrKey, requirement, caller.userId());
            }
            return relationService.unrelate(caller.projectId(), idOrKey, relationId, caller.userId());
        });
    }

    private static boolean isGiven(String value) {
        return value != null && !value.isBlank();
    }

    private static void requireExactlyOne(String... targets) {
        if (Arrays.stream(targets).filter(BugReportMaintenanceTools::isGiven).count() != 1) {
            throw new McpToolException("Pass exactly one target: testCase, requirement, or the other bug.");
        }
    }


    @McpTool(
            name = "hand_over_bug_report",
            description = """
                    Hand a bug report over to the project's Azure DevOps: files a work item from its
                    title, texts and priority, or with existingWorkItemId links one a developer
                    already filed. From then on the bug's status follows the work item and cannot
                    be changed here; detach_bug_report_issue gives it back.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public IssueLinkResponse handOverBugReport(
            @McpToolParam(description = "Bug key (PROJ-BUG-12) or UUID") String idOrKey,
            @McpToolParam(description = "Id of an existing work item to link instead of filing one",
                    required = false) String existingWorkItemId) {
        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        return BugReportTools.enabled(() ->
                handoverService.handOver(caller.projectId(), idOrKey, existingWorkItemId, caller.userId()));
    }

    @McpTool(
            name = "detach_bug_report_issue",
            description = """
                    Undo hand_over_bug_report: the bug keeps its last status and can be changed here
                    again. The work item stays in Azure DevOps.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.BugDetail detachBugReportIssue(
            @McpToolParam(description = "Bug key (PROJ-BUG-12) or UUID") String idOrKey) {
        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        return BugReportTools.detail(attachmentService, BugReportTools.enabled(() -> {
            handoverService.detach(caller.projectId(), idOrKey, caller.userId());
            return bugReportService.findById(caller.projectId(), idOrKey);
        }));
    }

    /** Absent keeps the current plan, "" clears it, anything else must be a plan UUID. */
    private static UUID planId(String given, UUID current) {
        if (given == null) {
            return current;
        }
        if (given.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(given.strip());
        } catch (IllegalArgumentException notAUuid) {
            throw new McpToolException("'" + given + "' is not a test plan UUID. Use list_test_plans to find it.");
        }
    }
}
