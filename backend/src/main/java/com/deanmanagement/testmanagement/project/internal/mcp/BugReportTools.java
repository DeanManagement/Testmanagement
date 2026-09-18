package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.ChangeBugStatusRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.CreateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.service.BugReportService;
import com.deanmanagement.testmanagement.shared.PageableUtils;
import com.deanmanagement.testmanagement.shared.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Bug report tools (PRD-027 §3.3) — so a failure an agent finds becomes a tracked defect rather
 * than a paragraph in a chat log.
 *
 * <p>These sit on the native {@code bug_reports} table rather than PRD-010's issue-tracker links.
 * The native entity works with no external dependency, which a self-hosted instance needs; filing
 * into GitLab or Forgejo requires an admin-configured tracker and is follow-on work. The two are
 * complementary — a bug report carries {@code testResultId}, which is what {@code IssueLinkService}
 * keys off.
 *
 * <p>There is deliberately no update-the-body tool. {@code UpdateBugReportRequest} is a full
 * replace by design (the SPA sends the whole object, and an omitted assignee is how a human
 * unassigns), so a partial-update tool over it would need the read-then-merge that PRD-025 §8
 * removed from {@code update_test_case} — making the agent the author of fields it never touched.
 */
@Service
@InToolGroup(McpToolGroup.EXECUTION)
@RequiredArgsConstructor
public class BugReportTools {

    private final McpCallerContext callerContext;
    private final BugReportService bugReportService;
    private final McpWriteThrottle writeThrottle;
    private final McpValidator validator;

    /** Open enough to be worth deduplicating against — see {@link #findDuplicates}. */
    private static final Set<BugReportStatus> LIVE =
            Set.of(BugReportStatus.OPEN, BugReportStatus.IN_PROGRESS);

    @McpTool(
            name = "create_bug_report",
            description = """
                    File a bug. Use this when a test fails for a real defect rather than a broken
                    test — the run records that it failed, this records why it matters.
                    priority: LOW | MEDIUM | HIGH | CRITICAL. Filed as OPEN.
                    Pass testResultId (from get_test_run) whenever the bug came out of a run, so
                    the bug is reachable from the failure that produced it. That links the run too
                    — do not also pass testRunId. testRunId is for a bug about a run as a whole,
                    with no single result to blame.
                    stepsToReproduce, expectedBehavior and actualBehavior are what make a report
                    actionable — fill them in rather than putting everything in description.
                    A title matching an already-open bug is refused with that bug's id, so check
                    before re-filing something known.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.BugDetail createBugReport(
            @McpToolParam(description = "Short summary, max 255 characters") String title,
            @McpToolParam(description = "Priority: LOW | MEDIUM | HIGH | CRITICAL") Priority priority,
            @McpToolParam(description = "Fuller explanation", required = false) String description,
            @McpToolParam(description = "Numbered steps that reproduce it", required = false)
            String stepsToReproduce,
            @McpToolParam(description = "What should have happened", required = false)
            String expectedBehavior,
            @McpToolParam(description = "What actually happened", required = false)
            String actualBehavior,
            @McpToolParam(description = "Where it was seen; prefer a name from list_environments", required = false)
            String environment,
            @McpToolParam(description = "Test result this bug came from", required = false)
            UUID testResultId,
            @McpToolParam(description = "Only for a bug about a whole run; taken from testResultId "
                    + "when that is given", required = false)
            UUID testRunId,
            @McpToolParam(description = "File it even though an open bug has the same title",
                    required = false) Boolean allowDuplicateTitle) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (title == null || title.isBlank()) {
            throw new McpToolException("title is required.");
        }
        if (priority == null) {
            throw new McpToolException("priority is required: LOW, MEDIUM, HIGH or CRITICAL.");
        }

        if (!Boolean.TRUE.equals(allowDuplicateTitle)) {
            List<McpDtos.DuplicateBug> duplicates = findDuplicates(caller.projectId(), title);
            if (!duplicates.isEmpty()) {
                throw new McpToolException("An open bug already has this title: "
                        + duplicates.stream()
                                .map(d -> d.id() + " (" + d.status() + ") " + d.title())
                                .collect(Collectors.joining("; "))
                        + ". Use change_bug_report_status on it, or pass allowDuplicateTitle=true "
                        + "if this really is a separate defect.");
            }
        }

        // No assigneeId: deciding who fixes a bug is a human's call (PRD-025 §3.4).
        var request = new CreateBugReportRequest(title, description, stepsToReproduce,
                expectedBehavior, actualBehavior, priority, environment, testResultId, testRunId,
                null, null);
        validator.validate(request);

        return detail(enabled(() ->
                bugReportService.create(caller.projectId(), request, caller.userId())));
    }

    @McpTool(
            name = "list_bug_reports",
            description = """
                    The project's bug reports, newest first, so you can check what is already known
                    before filing.
                    status: OPEN | IN_PROGRESS | RESOLVED | CLOSED | WONTFIX.
                    priority: LOW | MEDIUM | HIGH | CRITICAL.
                    Summaries only — call get_bug_report for reproduction steps.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.BugPage listBugReports(
            @McpToolParam(description = "Only bugs in these statuses", required = false)
            List<BugReportStatus> status,
            @McpToolParam(description = "Only bugs at these priorities", required = false)
            List<Priority> priority,
            @McpToolParam(description = "Zero-based page number, default 0", required = false)
            Integer page,
            @McpToolParam(description = "Page size, default 50, max 200", required = false)
            Integer size) {

        var caller = callerContext.require();

        List<BugReportResponse> matching = enabled(() ->
                bugReportService.findByProject(caller.projectId())).stream()
                .filter(b -> status == null || status.isEmpty() || status.contains(b.status()))
                .filter(b -> priority == null || priority.isEmpty() || priority.contains(b.priority()))
                .toList();

        // Filtered and sliced here rather than in a query: findByProject is unpaged, bug_reports is
        // a small table on a self-hosted instance, and a specification layer for one tool would be
        // the abstraction CLAUDE.md warns about. totalElements stays honest either way, and the fix
        // when a project outgrows this is a paged query behind an unchanged signature.
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? PageableUtils.DEFAULT_SIZE
                : Math.min(size, PageableUtils.MAX_SIZE);
        // long: page arrives from a model, and (int) pageNumber * pageSize overflows to a negative
        // offset for a large enough page number, which reaches subList and throws.
        int from = (int) Math.min((long) pageNumber * pageSize, matching.size());
        int to = (int) Math.min((long) from + pageSize, matching.size());

        List<McpDtos.BugSummary> slice = matching.subList(from, to).stream()
                .map(b -> new McpDtos.BugSummary(b.id(), b.title(), b.status(), b.priority(),
                        b.environment(), b.testResultId(), b.testCaseTitle(), b.testRunId(),
                        b.testRunName(), b.assigneeName()))
                .toList();

        return new McpDtos.BugPage(slice, pageNumber, pageSize, matching.size(),
                to < matching.size());
    }

    @McpTool(
            name = "get_bug_report",
            description = "One bug report in full, including reproduction steps and expected "
                    + "versus actual behaviour.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.BugDetail getBugReport(
            @McpToolParam(description = "Bug report UUID") UUID id) {
        var caller = callerContext.require();
        return detail(enabled(() -> bugReportService.findById(caller.projectId(), id)));
    }

    @McpTool(
            name = "change_bug_report_status",
            description = """
                    Move a bug report to a new status.
                    status: OPEN | IN_PROGRESS | RESOLVED | CLOSED | WONTFIX.
                    reason is required and is written to the project audit log as
                    "OLD -> NEW: reason" — say what you verified, not just that you changed it.
                    Use this rather than filing a second bug when something you already reported
                    turns out to be fixed or not a defect.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.BugDetail changeBugReportStatus(
            @McpToolParam(description = "Bug report UUID") UUID id,
            @McpToolParam(description = "New status") BugReportStatus status,
            @McpToolParam(description = "Why it is changing") String reason) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (status == null) {
            throw new McpToolException("status is required: OPEN, IN_PROGRESS, RESOLVED, CLOSED "
                    + "or WONTFIX.");
        }
        if (reason == null || reason.isBlank()) {
            throw new McpToolException("reason is required — it is what the audit log records "
                    + "about this change.");
        }
        var request = new ChangeBugStatusRequest(status, reason);
        validator.validate(request);

        return detail(enabled(() ->
                bugReportService.changeStatus(caller.projectId(), id, request, caller.userId())));
    }

    /**
     * Open bugs whose title matches after normalisation.
     *
     * <p>An agent running the same suite nightly will otherwise file the same bug every night, so
     * the guard matters more here than it does for test cases. It looks only at OPEN and
     * IN_PROGRESS on purpose: a regression of something closed last month is a genuinely new
     * report, and refusing it would suppress the most interesting signal the suite produces.
     *
     * <p>Exact-after-normalisation only. PRD-025's tier-2 trigram path is not extended to bugs —
     * bug titles are free prose where test case titles are formulaic, so fuzzy matching here would
     * mostly produce false positives, and a false refusal on a real new defect is expensive.
     */
    private List<McpDtos.DuplicateBug> findDuplicates(UUID projectId, String title) {
        String normalised = TestCaseDuplicateDetector.normalise(title);
        // A title of "???" is non-blank but normalises to empty, and would then match every other
        // punctuation-only bug in the project. TestCaseDuplicateDetector guards the same way.
        if (normalised.isEmpty()) {
            return List.of();
        }
        return enabled(() -> bugReportService.findByProject(projectId)).stream()
                .filter(b -> LIVE.contains(b.status()))
                .filter(b -> TestCaseDuplicateDetector.normalise(b.title()).equals(normalised))
                .map(b -> new McpDtos.DuplicateBug(b.id(), b.title(), b.status()))
                .toList();
    }

    /**
     * Turns "bug reports are off for this project" into something the agent can act on.
     *
     * <p>{@code Project.bugReportsEnabled} defaults to false and every {@code BugReportService}
     * method throws {@link ForbiddenException} until an admin turns it on. Left alone that reaches
     * the agent as an opaque refusal it will simply retry, so the message names both the setting
     * and the fact that the agent cannot change it: the toggle needs project ADMIN and API keys
     * top out at TESTER.
     */
    private <T> T enabled(Supplier<T> call) {
        try {
            return call.get();
        } catch (ForbiddenException e) {
            // Matched on the condition, not just the type. ForbiddenException is thrown from a
            // dozen places — "Not a member of this project", "Requires ADMIN role" — and none is
            // reachable through BugReportService today, but the moment one is, reporting it as a
            // settings problem would send the agent to fix the wrong thing. Anything else
            // propagates, and is audited as ERROR rather than REFUSED, which is the distinction
            // McpToolAuditor exists to keep.
            if (e.getMessage() == null || !e.getMessage().contains("not enabled")) {
                throw e;
            }
            throw new McpToolException("Bug reports are not enabled for this project. A project "
                    + "administrator has to turn them on in the project settings — an API key "
                    + "cannot, since that needs the ADMIN role. Report this rather than retrying.");
        }
    }

    private static McpDtos.BugDetail detail(BugReportResponse b) {
        return new McpDtos.BugDetail(b.id(), b.title(), b.description(), b.stepsToReproduce(),
                b.expectedBehavior(), b.actualBehavior(), b.status(), b.priority(), b.environment(),
                b.testResultId(), b.testCaseTitle(), b.testRunId(), b.testRunName(),
                b.assigneeName(), b.reporterName());
    }
}
