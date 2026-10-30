package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.issuetracker.IssueLinkResponse;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.BugResolution;
import com.deanmanagement.testmanagement.project.internal.entity.IssueLink;
import com.deanmanagement.testmanagement.project.internal.entity.IssueState;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.project.internal.issuetracker.Issue;
import com.deanmanagement.testmanagement.project.internal.issuetracker.IssueDraft;
import com.deanmanagement.testmanagement.project.internal.issuetracker.IssueNotFoundException;
import com.deanmanagement.testmanagement.project.internal.issuetracker.IssueTrackerProviderRegistry;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.IssueLinkRepository;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Hands a bug report over to the project's Azure DevOps (TM50, PRD-054 §2): files a work item from
 * it, or links one a developer already filed, and from then on the bug's status follows the work
 * item's state category. Texts stay editable here but are not pushed.
 */
@Service
@Transactional(readOnly = true)
public class BugTrackerHandoverService {

    private static final Logger log = LoggerFactory.getLogger(BugTrackerHandoverService.class);
    private static final int MAX_TITLE_LENGTH = 500;

    private final IssueLinkRepository issueLinkRepository;
    private final BugReportRepository bugReportRepository;
    private final BugReportService bugReportService;
    private final IssueTrackerConfigService configService;
    private final IssueTrackerProviderRegistry providerRegistry;
    private final AuditService auditService;
    private final String publicBaseUrl;

    public BugTrackerHandoverService(IssueLinkRepository issueLinkRepository, BugReportRepository bugReportRepository,
                                     BugReportService bugReportService, IssueTrackerConfigService configService,
                                     IssueTrackerProviderRegistry providerRegistry, AuditService auditService,
                                     @Value("${app.public-base-url:}") String publicBaseUrl) {
        this.issueLinkRepository = issueLinkRepository;
        this.bugReportRepository = bugReportRepository;
        this.bugReportService = bugReportService;
        this.configService = configService;
        this.providerRegistry = providerRegistry;
        this.auditService = auditService;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    public Optional<IssueLinkResponse> find(UUID projectId, String bugIdOrKey) {
        BugReport bug = bugReportService.require(projectId, bugIdOrKey);
        return issueLinkRepository.findByBugReportId(bug.getId()).map(IssueLinkService::toResponse);
    }

    /** Files a work item from the bug, or with {@code existingExternalId} links that one instead. */
    @Transactional
    public IssueLinkResponse handOver(UUID projectId, String bugIdOrKey, String existingExternalId, UUID userId) {
        bugReportService.requireBugReportsEnabled(projectId);
        BugReport bug = bugReportService.require(projectId, bugIdOrKey);
        IssueTrackerConfig config = requireAzureDevOps(projectId);
        issueLinkRepository.findByBugReportId(bug.getId()).ifPresent(existing -> {
            throw new ConflictException(bug.getKey() + " is already handed over to work item "
                    + existing.getExternalId() + ".");
        });

        boolean linkExisting = existingExternalId != null && !existingExternalId.isBlank();
        Issue issue = linkExisting
                ? configService.call(config, decrypted -> providerRegistry.require(config.getProvider())
                        .get(decrypted, existingExternalId.strip()))
                : configService.call(config, decrypted -> providerRegistry.require(config.getProvider())
                        .create(decrypted, draftOf(bug)));
        refuseSharedWorkItem(bug, config.getProvider(), issue.externalId());

        IssueLink link = new IssueLink();
        link.setBugReportId(bug.getId());
        link.setProvider(config.getProvider());
        link.setExternalId(issue.externalId());
        link.setUrl(issue.url());
        try {
            issueLinkRepository.saveAndFlush(link);
        } catch (DataIntegrityViolationException raced) {
            // A second handover won the unique index; a work item we just filed is now an orphan.
            log.warn("Bug {} was handed over concurrently; work item {} is not linked", bug.getKey(),
                    issue.externalId());
            throw new ConflictException(bug.getKey() + " was handed over at the same time by someone else.");
        }
        auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.BUG_REPORT, bug.getId(),
                BugReportService.label(bug), (linkExisting ? "Linked to " : "Handed over as ")
                        + "Azure DevOps work item " + issue.externalId());
        follow(link, issue);
        return IssueLinkService.toResponse(link);
    }

    /** Gives the bug its status back; the work item stays in the tracker as it is. */
    @Transactional
    public void detach(UUID projectId, String bugIdOrKey, UUID userId) {
        BugReport bug = bugReportService.require(projectId, bugIdOrKey);
        IssueLink link = issueLinkRepository.findByBugReportId(bug.getId())
                .orElseThrow(() -> new ConflictException(bug.getKey() + " is not handed over to a work item."));
        issueLinkRepository.delete(link);
        auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.BUG_REPORT, bug.getId(),
                BugReportService.label(bug), "Detached from work item " + link.getExternalId());
    }

    /** Reads the work item now rather than waiting for the poll. */
    @Transactional
    public IssueLinkResponse refresh(UUID projectId, String bugIdOrKey) {
        BugReport bug = bugReportService.require(projectId, bugIdOrKey);
        IssueLink link = issueLinkRepository.findByBugReportId(bug.getId())
                .orElseThrow(() -> new ConflictException(bug.getKey() + " is not handed over to a work item."));
        IssueTrackerConfig config = requireAzureDevOps(projectId);
        try {
            Issue issue = configService.call(config, decrypted -> providerRegistry.require(config.getProvider())
                    .get(decrypted, link.getExternalId()));
            follow(link, issue);
        } catch (IssueNotFoundException gone) {
            markGone(link);
        }
        return IssueLinkService.toResponse(link);
    }

    /**
     * Records what the tracker said about the work item and moves the bug to the status its state
     * category implies. Called on handover, on refresh and by the poller.
     */
    @Transactional
    public void follow(IssueLink link, Issue issue) {
        if (issue.externalId() != null) {
            link.setExternalId(issue.externalId());
        }
        if (issue.url() != null) {
            link.setUrl(issue.url());
        }
        if (issue.title() != null) {
            link.setTitle(issue.title().length() <= MAX_TITLE_LENGTH ? issue.title()
                    : issue.title().substring(0, MAX_TITLE_LENGTH));
        }
        link.setState(issue.state());
        link.setTrackerState(issue.trackerState());
        link.setStateCategory(issue.stateCategory());
        link.setStateCheckedAt(Instant.now());
        issueLinkRepository.save(link);

        bugReportRepository.findById(link.getBugReportId()).ifPresent(bug -> {
            Target target = targetFor(issue.stateCategory(), bug.getResolution());
            if (target != null) {
                bugReportService.followTracker(bug, target.status(), target.resolution(),
                        "Status changed by Azure DevOps (work item " + link.getExternalId() + ": "
                                + issue.trackerState() + ")");
            }
        });
    }

    /** The work item is gone: the bug keeps its last status and the link shows it as unknown. */
    @Transactional
    public void markGone(IssueLink link) {
        link.setState(IssueState.UNKNOWN);
        link.setTrackerState(null);
        link.setStateCategory(null);
        link.setStateCheckedAt(Instant.now());
        issueLinkRepository.save(link);
    }

    /** A bug status and resolution. */
    record Target(BugReportStatus status, BugResolution resolution) {
    }

    /**
     * PRD-054 §2.2. Null for a category this does not know, which leaves the bug alone. A closed
     * bug keeps a resolution it already had; "Removed" means the developers will not fix it.
     */
    static Target targetFor(String stateCategory, BugResolution current) {
        if (stateCategory == null) {
            return null;
        }
        return switch (stateCategory) {
            case "Proposed" -> new Target(BugReportStatus.OPEN, null);
            case "InProgress" -> new Target(BugReportStatus.IN_PROGRESS, null);
            case "Resolved" -> new Target(BugReportStatus.RESOLVED, current);
            case "Completed" -> new Target(BugReportStatus.CLOSED, current != null ? current : BugResolution.FIXED);
            case "Removed" -> new Target(BugReportStatus.CLOSED, BugResolution.WONT_FIX);
            default -> null;
        };
    }

    private IssueTrackerConfig requireAzureDevOps(UUID projectId) {
        IssueTrackerConfig config = configService.activeConfig(projectId)
                .orElseThrow(() -> new IllegalArgumentException("No issue tracker is configured for this project"));
        if (config.getProvider() != IssueTrackerProviderType.AZURE_DEVOPS) {
            throw new IllegalArgumentException("Handing bug reports over is supported for Azure DevOps only");
        }
        return config;
    }

    /** Two bugs following one work item would fight over its status. */
    private void refuseSharedWorkItem(BugReport bug, IssueTrackerProviderType provider, String externalId) {
        issueLinkRepository.findFirstByProviderAndExternalIdAndBugReportIdIsNotNull(provider, externalId)
                .flatMap(other -> bugReportRepository.findById(other.getBugReportId()))
                .ifPresent(other -> {
                    throw new ConflictException("Work item " + externalId + " already belongs to " + other.getKey()
                            + ". Close " + bug.getKey() + " as a duplicate of it instead.");
                });
    }

    /** Title, the four texts as Markdown sections, the environment and a way back to the bug. */
    private IssueDraft draftOf(BugReport bug) {
        StringBuilder body = new StringBuilder();
        section(body, "Description", bug.getDescription());
        section(body, "Steps to reproduce", bug.getStepsToReproduce());
        section(body, "Expected behaviour", bug.getExpectedBehavior());
        section(body, "Actual behaviour", bug.getActualBehavior());
        if (bug.getEnvironment() != null) {
            body.append("**Environment:** ").append(bug.getEnvironment()).append("\n");
        }
        if (bug.getTestResult() != null) {
            body.append("**Found in:** ").append(bug.getTestResult().getTestCase().getKey())
                    .append(" in run ").append(bug.getTestResult().getTestRun().getKey()).append("\n");
        }
        body.append("**Bug report:** ").append(bug.getKey());
        if (!publicBaseUrl.isEmpty()) {
            body.append(" — ").append(publicBaseUrl).append("/projects/").append(bug.getProject().getId())
                    .append("/bug-reports/").append(bug.getKey());
        }
        return new IssueDraft(bug.getTitle(), body.toString(), bug.getPriority());
    }

    private static void section(StringBuilder body, String heading, String text) {
        if (text != null && !text.isBlank()) {
            body.append("**").append(heading).append("**\n").append(text.strip()).append("\n\n");
        }
    }
}
