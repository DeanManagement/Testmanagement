package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.service.BugStatusService;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.dto.bugstatus.UpdateBugStatusRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugstatus.CreateBugStatusRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugstatus.BugStatusTransitionsRequest;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.ProjectWorkflowResponse;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.SetProjectWorkflowsRequest;
import com.deanmanagement.testmanagement.project.internal.dto.issuetracker.IssueTrackerConfigResponse;
import com.deanmanagement.testmanagement.project.internal.dto.issuetracker.SaveIssueTrackerConfigRequest;
import com.deanmanagement.testmanagement.project.internal.dto.webhook.CreateWebhookRequest;
import com.deanmanagement.testmanagement.project.internal.dto.webhook.UpdateWebhookRequest;
import com.deanmanagement.testmanagement.project.internal.dto.webhook.WebhookDeliveryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.webhook.WebhookResponse;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookFormat;
import com.deanmanagement.testmanagement.project.internal.service.BuildWorkflowService;
import com.deanmanagement.testmanagement.project.internal.service.IssueTrackerConfigService;
import com.deanmanagement.testmanagement.project.internal.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Webhooks, the issue-tracker connection and which pipeline workflows testers are offered (TM37).
 *
 * <p>Two gates, both required: an ADMIN key, as the settings pages need a project admin, and the
 * CONFIGURATION tool group, which an unrestricted key does not hold — it has to be named when the
 * key is created ({@link McpToolGroup#isImpliedByUnrestrictedKey()}).
 *
 * <p>Secrets go in and never come out: webhook signing secrets and tracker tokens are write-only,
 * and the responses carry {@code secretSet} / {@code tokenSet} instead. Outbound URLs are checked
 * by the services' SSRF validators, the same as the UI. Build servers and their workflows are
 * instance-admin settings and stay out of reach; a project admin only picks which of the workflows
 * open to the project testers may trigger, and so does this. The project's bug statuses and their
 * transitions (TM47) are here too.
 */
@Service
@InToolGroup(McpToolGroup.CONFIGURATION)
@RequiredArgsConstructor
public class ProjectConfigurationTools {

    private static final String DEFAULT_BUG_STATUS_COLOR = "#0ea5e9";

    private final McpCallerContext callerContext;
    private final WebhookService webhookService;
    private final IssueTrackerConfigService issueTrackerConfigService;
    private final BuildWorkflowService workflowService;
    private final McpWriteThrottle writeThrottle;
    private final McpValidator validator;
    private final BugStatusService bugStatusService;

    // --- webhooks --------------------------------------------------------------------------

    @McpTool(
            name = "list_webhooks",
            description = """
                    The project's outbound webhooks. Requires an ADMIN key. Signing secrets are never
                    returned, and Slack / Teams URLs come back masked because the URL is the
                    credential.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.WebhookList listWebhooks() {
        var caller = callerContext.requireAdmin();
        return new McpDtos.WebhookList(webhookService.findByProject(caller.projectId()).stream()
                .map(ProjectConfigurationTools::toDto).toList());
    }

    @McpTool(
            name = "create_webhook",
            description = """
                    Send project events to a URL. Requires an ADMIN key, and only do it because a
                    human asked: every event the project emits goes to that URL. format: GENERIC |
                    SLACK | TEAMS (default GENERIC). events: RUN_STARTED, RUN_COMPLETED, RUN_FAILED,
                    TEST_FAILED (not for SLACK / TEAMS), PLAN_COMPLETED, BUG_REPORT_CREATED.
                    secret signs every delivery; required for GENERIC, generated for chat formats.
                    It is not returned. Private and internal addresses are refused.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false,
                    openWorldHint = true))
    @Transactional
    public McpDtos.Webhook createWebhook(
            @McpToolParam(description = "Target URL, https") String url,
            @McpToolParam(description = "Events to send") Set<WebhookEventType> events,
            @McpToolParam(description = "GENERIC, SLACK or TEAMS; default GENERIC", required = false)
            WebhookFormat format,
            @McpToolParam(description = "Signing secret; required for GENERIC", required = false) String secret,
            @McpToolParam(description = "Whether it fires, default true", required = false) Boolean active) {
        var caller = callerContext.requireAdmin();
        writeThrottle.recordWrite(caller.apiKeyId());
        var request = new CreateWebhookRequest(url, secret, events, active, format);
        validator.validate(request);
        return toDto(webhookService.create(caller.projectId(), request));
    }

    @McpTool(
            name = "update_webhook",
            description = """
                    Change a webhook. Requires an ADMIN key. Only what you pass changes: omit url or
                    secret to keep the stored one (neither is ever shown); events replaces the whole
                    set. active false pauses it.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = true))
    @Transactional
    public McpDtos.Webhook updateWebhook(
            @McpToolParam(description = "Webhook UUID from list_webhooks") UUID id,
            @McpToolParam(description = "New target URL", required = false) String url,
            @McpToolParam(description = "Full new set of events", required = false) Set<WebhookEventType> events,
            @McpToolParam(description = "GENERIC, SLACK or TEAMS", required = false) WebhookFormat format,
            @McpToolParam(description = "New signing secret", required = false) String secret,
            @McpToolParam(description = "false pauses it, true resumes it", required = false) Boolean active) {
        var caller = callerContext.requireAdmin();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (url == null && events == null && format == null && secret == null && active == null) {
            throw new McpToolException("Nothing to update: pass url, events, format, secret or active.");
        }
        WebhookResponse current = webhook(caller, id);
        // url and secret null: the service keeps the stored values, which the response never holds.
        var request = new UpdateWebhookRequest(url, secret, events == null ? current.events() : events, active,
                format);
        validator.validate(request);
        return toDto(webhookService.update(caller.projectId(), id, request));
    }

    @McpTool(
            name = "delete_webhook",
            description = "Permanently delete a webhook and its delivery log. Requires an ADMIN key. Cannot be undone.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = false))
    @Transactional
    public McpDtos.Deleted deleteWebhook(@McpToolParam(description = "Webhook UUID from list_webhooks") UUID id) {
        var caller = callerContext.requireAdmin();
        writeThrottle.recordWrite(caller.apiKeyId());
        String url = redactUrl(webhook(caller, id).url());
        webhookService.delete(caller.projectId(), id);
        return new McpDtos.Deleted("WEBHOOK", id, url);
    }

    @McpTool(
            name = "test_webhook",
            description = """
                    Send a test event to a webhook now and report how its URL answered. Requires an
                    ADMIN key.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false,
                    openWorldHint = true))
    public McpDtos.WebhookDelivery testWebhook(
            @McpToolParam(description = "Webhook UUID from list_webhooks") UUID id) {
        var caller = callerContext.requireAdmin();
        writeThrottle.recordWrite(caller.apiKeyId());
        WebhookDeliveryResponse delivery = webhookService.sendTest(caller.projectId(), id);
        return new McpDtos.WebhookDelivery(delivery.id(), delivery.responseStatus(), delivery.success(),
                delivery.error());
    }

    // --- issue tracker ---------------------------------------------------------------------

    @McpTool(
            name = "get_issue_tracker_config",
            description = """
                    The project's external issue-tracker connection, without its API token
                    (tokenSet says whether one is stored). configured false means there is none.
                    Requires an ADMIN key.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.IssueTrackerConfig getIssueTrackerConfig() {
        var caller = callerContext.requireAdmin();
        return issueTrackerConfigService.find(caller.projectId())
                .map(ProjectConfigurationTools::toDto)
                .orElseGet(ProjectConfigurationTools::notConfigured);
    }

    @McpTool(
            name = "set_issue_tracker_config",
            description = """
                    Connect the project to an external issue tracker, or change the connection.
                    Requires an ADMIN key. Only what you pass changes; a first connection needs
                    provider, baseUrl, projectRef and apiToken. provider: GITLAB | FORGEJO | GITHUB
                    | JIRA | AZURE_DEVOPS. apiToken is stored encrypted and never returned; omit it
                    to keep the stored one. Jira Cloud also needs authUsername (the account email).
                    apiVersion and workItemType apply to AZURE_DEVOPS only. Private and internal
                    addresses are refused.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = true))
    @Transactional
    public McpDtos.IssueTrackerConfig setIssueTrackerConfig(
            @McpToolParam(description = "GITLAB, FORGEJO, GITHUB, JIRA or AZURE_DEVOPS", required = false)
            IssueTrackerProviderType provider,
            @McpToolParam(description = "Base URL of the tracker", required = false) String baseUrl,
            @McpToolParam(description = "Project in the tracker, e.g. group/repo or a Jira key", required = false)
            String projectRef,
            @McpToolParam(description = "API token; omit to keep the stored one", required = false) String apiToken,
            @McpToolParam(description = "false disables it without deleting it", required = false) Boolean active,
            @McpToolParam(description = "Jira Cloud account email", required = false) String authUsername,
            @McpToolParam(description = "Azure DevOps API version, e.g. 7.1", required = false) String apiVersion,
            @McpToolParam(description = "Azure DevOps work item type, default Bug", required = false)
            String workItemType) {
        var caller = callerContext.requireAdmin();
        writeThrottle.recordWrite(caller.apiKeyId());
        var current = issueTrackerConfigService.find(caller.projectId());
        var request = new SaveIssueTrackerConfigRequest(
                provider != null ? provider : current.map(IssueTrackerConfigResponse::provider).orElse(null),
                baseUrl != null ? baseUrl : current.map(IssueTrackerConfigResponse::baseUrl).orElse(null),
                projectRef != null ? projectRef : current.map(IssueTrackerConfigResponse::projectRef).orElse(null),
                apiToken,
                active != null ? active : current.map(IssueTrackerConfigResponse::active).orElse(null),
                authUsername != null ? authUsername : current.map(IssueTrackerConfigResponse::authUsername).orElse(null),
                apiVersion != null ? apiVersion : current.map(IssueTrackerConfigResponse::apiVersion).orElse(null),
                workItemType != null ? workItemType
                        : current.map(IssueTrackerConfigResponse::workItemType).orElse(null));
        validator.validate(request);
        return toDto(issueTrackerConfigService.save(caller.projectId(), request));
    }

    @McpTool(
            name = "test_issue_tracker_connection",
            description = """
                    Check the stored tracker credentials against the tracker now. Requires an ADMIN
                    key. Returns the config, whose lastError is cleared on success; a rejection is
                    reported as an error and recorded as lastError.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = true))
    @Transactional
    public McpDtos.IssueTrackerConfig testIssueTrackerConnection() {
        var caller = callerContext.requireAdmin();
        writeThrottle.recordWrite(caller.apiKeyId());
        issueTrackerConfigService.testConnection(caller.projectId());
        return getIssueTrackerConfig();
    }

    @McpTool(
            name = "delete_issue_tracker_config",
            description = """
                    Remove the project's issue-tracker connection, token included. Requires an ADMIN
                    key. Issues already linked keep their links. Cannot be undone.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = true))
    @Transactional
    public McpDtos.Deleted deleteIssueTrackerConfig() {
        var caller = callerContext.requireAdmin();
        writeThrottle.recordWrite(caller.apiKeyId());
        IssueTrackerConfigResponse current = issueTrackerConfigService.find(caller.projectId())
                .orElseThrow(() -> new McpToolException("This project has no issue tracker configured."));
        issueTrackerConfigService.delete(caller.projectId());
        return new McpDtos.Deleted("ISSUE_TRACKER_CONFIG", current.id(), current.provider().name());
    }

    // --- pipeline workflows ----------------------------------------------------------------

    @McpTool(
            name = "list_available_pipeline_workflows",
            description = """
                    Every active workflow on a build server open to this project — what
                    set_pipeline_workflows can choose from. Requires an ADMIN key.
                    list_pipeline_workflows shows which are offered to testers now.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.WorkflowList listAvailablePipelineWorkflows() {
        var caller = callerContext.requireAdmin();
        return workflows(workflowService.listAvailableForProject(caller.projectId()));
    }

    @McpTool(
            name = "set_pipeline_workflows",
            description = """
                    Choose exactly which workflows testers of this project may trigger, replacing
                    the current choice; an empty list offers none. Requires an ADMIN key. Ids come
                    from list_available_pipeline_workflows. Build servers and their workflows are
                    set up by an instance administrator, not here.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.WorkflowList setPipelineWorkflows(
            @McpToolParam(description = "Workflow UUIDs to offer") List<UUID> workflowIds) {
        var caller = callerContext.requireAdmin();
        writeThrottle.recordWrite(caller.apiKeyId());
        var request = new SetProjectWorkflowsRequest(workflowIds);
        validator.validate(request);
        workflowService.setProjectWorkflows(caller.projectId(), request.workflowIds());
        return workflows(workflowService.listForProject(caller.projectId()));
    }

    // --- helpers ---------------------------------------------------------------------------

    private WebhookResponse webhook(McpCallerContext.Caller caller, UUID id) {
        return webhookService.findByProject(caller.projectId()).stream()
                .filter(w -> w.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new McpToolException("No webhook " + id + " in this project. "
                        + "Call list_webhooks."));
    }

    /** WebhookService requires a secret on every webhook (generated for chat formats). */
    private static McpDtos.Webhook toDto(WebhookResponse w) {
        return new McpDtos.Webhook(w.id(), redactUrl(w.url()), w.format(), w.events(), w.active(), true);
    }

    /**
     * Drops credentials a URL may carry — user:password@ and the query string, where tokens tend to
     * go — which the settings page shows its admin but an agent's context should not hold.
     */
    static String redactUrl(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            if (uri.getRawUserInfo() == null && uri.getRawQuery() == null) {
                return url;
            }
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort())
                    + (uri.getRawPath() == null ? "" : uri.getRawPath())
                    + (uri.getRawQuery() == null ? "" : "?…");
        } catch (IllegalArgumentException unparseable) {
            return "(hidden: not a parseable URL)";
        }
    }

    private static McpDtos.IssueTrackerConfig toDto(IssueTrackerConfigResponse c) {
        return new McpDtos.IssueTrackerConfig(true, c.provider(), redactUrl(c.baseUrl()), c.projectRef(), c.active(),
                c.tokenSet(), c.authUsername(), c.apiVersion(), c.workItemType(), c.lastError(), c.lastErrorAt());
    }

    private static McpDtos.IssueTrackerConfig notConfigured() {
        return new McpDtos.IssueTrackerConfig(false, null, null, null, null, null, null, null, null, null, null);
    }

    private static McpDtos.WorkflowList workflows(List<ProjectWorkflowResponse> list) {
        List<McpDtos.Workflow> workflows = list.stream()
                .map(w -> new McpDtos.Workflow(w.id(), w.name(), w.serverName(),
                        w.provider() == null ? null : w.provider().name(), w.defaultRef(), w.defaultParameters()))
                .toList();
        return new McpDtos.WorkflowList(workflows, workflows.size());
    }

    // --- bug statuses (TM47) ----------------------------------------------------------------

    @McpTool(
            name = "create_bug_status",
            description = """
                    Add one of the project's own bug statuses. Requires an ADMIN key. category is the
                    fixed status it counts as in reports and the release gate: NEW | OPEN |
                    IN_PROGRESS | RESOLVED | CLOSED; it cannot be changed later. color: #rrggbb,
                    default #0ea5e9. Returns every status, as list_bug_statuses does, archived ones
                    included.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.BugStatusList createBugStatus(
            @McpToolParam(description = "Name, unique in the project, max 100 characters") String name,
            @McpToolParam(description = "NEW, OPEN, IN_PROGRESS, RESOLVED or CLOSED") BugReportStatus category,
            @McpToolParam(description = "#rrggbb, default #0ea5e9", required = false) String color) {
        var caller = callerContext.requireAdmin();
        writeThrottle.recordWrite(caller.apiKeyId());
        var request = new CreateBugStatusRequest(name, color == null ? DEFAULT_BUG_STATUS_COLOR : color, category);
        validator.validate(request);
        bugStatusService.create(caller.projectId(), request, caller.userId());
        return BugReportTools.bugStatusList(bugStatusService, caller.projectId(), true);
    }

    @McpTool(
            name = "update_bug_status",
            description = """
                    Rename, recolour, reorder, archive or restore one of the project's bug statuses,
                    named as it is now. Requires an ADMIN key. Only what you pass changes. An
                    archived status stays on the bugs that have it but is no longer offered; the
                    last active status of a category cannot be archived.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.BugStatusList updateBugStatus(
            @McpToolParam(description = "The status's current name") String status,
            @McpToolParam(description = "New name", required = false) String newName,
            @McpToolParam(description = "#rrggbb", required = false) String color,
            @McpToolParam(description = "Place in the order, 0 first", required = false) Integer position,
            @McpToolParam(description = "true archives it, false restores it", required = false) Boolean archived) {
        var caller = callerContext.requireAdmin();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (newName == null && color == null && position == null && archived == null) {
            throw new McpToolException("Nothing to update: pass newName, color, position or archived.");
        }
        UUID id = bugStatus(caller.projectId(), status);
        var request = new UpdateBugStatusRequest(newName, color, position, archived);
        validator.validate(request);
        bugStatusService.update(caller.projectId(), id, request, caller.userId());
        return BugReportTools.bugStatusList(bugStatusService, caller.projectId(), true);
    }

    @McpTool(
            name = "set_bug_status_transitions",
            description = """
                    Replace the project's allowed bug status moves. Requires an ADMIN key, and only
                    do it because a human asked: once any move is listed, only listed moves are
                    allowed, for everyone. Each transition names two statuses, from and to. An
                    empty list allows any move again. A bug may always stay in its status.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.BugStatusList setBugStatusTransitions(
            @McpToolParam(description = "Every allowed move, as from/to status names; [] allows any")
            List<McpDtos.BugTransition> transitions) {
        var caller = callerContext.requireAdmin();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (transitions == null) {
            throw new McpToolException("transitions is required; pass [] to allow any move.");
        }
        var request = new BugStatusTransitionsRequest(transitions.stream()
                .map(t -> new BugStatusTransitionsRequest.Transition(bugStatus(caller.projectId(), t.from()),
                        bugStatus(caller.projectId(), t.to())))
                .toList());
        validator.validate(request);
        bugStatusService.saveTransitions(caller.projectId(), request, caller.userId());
        return BugReportTools.bugStatusList(bugStatusService, caller.projectId(), true);
    }

    /** A status by its name, archived ones included; an unknown name lists the ones there are. */
    private UUID bugStatus(UUID projectId, String name) {
        if (name == null || name.isBlank()) {
            throw new McpToolException("A bug status name is required, e.g. from list_bug_statuses.");
        }
        return bugStatusService.findByName(projectId, name)
                .orElseThrow(() -> new McpToolException("No bug status named '" + name + "'. This project has: "
                        + bugStatusService.describeActive(projectId)))
                .getId();
    }
}
