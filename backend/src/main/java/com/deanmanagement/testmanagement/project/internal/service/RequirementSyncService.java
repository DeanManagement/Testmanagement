package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.requirement.RequirementSyncConfigResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.RequirementSyncResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.RequirementSyncStatusResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.SaveRequirementSyncConfigRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Requirement;
import com.deanmanagement.testmanagement.project.internal.entity.RequirementStatus;
import com.deanmanagement.testmanagement.project.internal.entity.RequirementSyncConfig;
import com.deanmanagement.testmanagement.project.internal.issuetracker.AzureDevOpsIssueProvider;
import com.deanmanagement.testmanagement.project.internal.issuetracker.TrackerWorkItem;
import com.deanmanagement.testmanagement.project.internal.repository.RequirementRepository;
import com.deanmanagement.testmanagement.project.internal.repository.RequirementSyncConfigRepository;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One-way requirement sync from the project's issue tracker (TM19, PRD-053 §5 step 3). The tracker
 * owns a synced requirement's external id, title, description, status, priority and parent; custom
 * fields and test case links stay local. Azure DevOps only: the settings name a WIQL query.
 *
 * <p>The tracker is read outside any database transaction; the upsert then runs in one. A dry run
 * performs the very same upsert and rolls it back, so its counts cannot drift from a real run's.
 * A work item that leaves the query is marked OBSOLETE, never deleted: it may carry coverage history.
 */
@Service
public class RequirementSyncService {

    static final String DEFAULT_APPROVED_STATES = "Approved, Committed, Active, Resolved, Closed, Done";
    static final String DEFAULT_OBSOLETE_STATES = "Removed";
    private static final IssueTrackerProviderType SOURCE = IssueTrackerProviderType.AZURE_DEVOPS;
    private static final int MAX_ERROR_LENGTH = 500;
    private static final String WIQL_START = "select";

    private final RequirementSyncConfigRepository syncConfigRepository;
    private final RequirementRepository requirementRepository;
    private final IssueTrackerConfigService trackerConfigService;
    private final AzureDevOpsIssueProvider azureDevOps;
    private final AuditService auditService;
    private final TransactionTemplate transaction;

    public RequirementSyncService(RequirementSyncConfigRepository syncConfigRepository,
                                  RequirementRepository requirementRepository,
                                  IssueTrackerConfigService trackerConfigService,
                                  AzureDevOpsIssueProvider azureDevOps,
                                  AuditService auditService,
                                  PlatformTransactionManager transactionManager) {
        this.syncConfigRepository = syncConfigRepository;
        this.requirementRepository = requirementRepository;
        this.trackerConfigService = trackerConfigService;
        this.azureDevOps = azureDevOps;
        this.auditService = auditService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    // ---- settings ---------------------------------------------------------

    public Optional<RequirementSyncConfigResponse> find(UUID projectId) {
        return syncConfigRepository.findByProjectId(projectId).map(RequirementSyncService::toResponse);
    }

    public RequirementSyncStatusResponse status(UUID projectId) {
        return syncConfigRepository.findByProjectId(projectId)
                .map(c -> new RequirementSyncStatusResponse(c.isEnabled(), c.getLastSyncedAt(), c.getLastError()))
                .orElseGet(() -> new RequirementSyncStatusResponse(false, null, null));
    }

    public RequirementSyncConfigResponse save(UUID projectId, SaveRequirementSyncConfigRequest request) {
        requireAzureDevOpsTracker(projectId);
        String query = request.query().strip();
        if (!query.toLowerCase(Locale.ROOT).startsWith(WIQL_START)) {
            throw new IllegalArgumentException("The query must be a WIQL SELECT statement");
        }
        RequirementSyncConfig config = syncConfigRepository.findByProjectId(projectId).orElseGet(() -> {
            RequirementSyncConfig created = new RequirementSyncConfig();
            created.setProjectId(projectId);
            return created;
        });
        config.setEnabled(Boolean.TRUE.equals(request.enabled()));
        config.setScheduled(Boolean.TRUE.equals(request.scheduled()));
        config.setTrackerQuery(query);
        config.setExternalIdPrefix(blankToNull(request.externalIdPrefix()));
        config.setApprovedStates(orDefault(request.approvedStates(), DEFAULT_APPROVED_STATES));
        config.setObsoleteStates(orDefault(request.obsoleteStates(), DEFAULT_OBSOLETE_STATES));
        return toResponse(syncConfigRepository.save(config));
    }

    // ---- sync -------------------------------------------------------------

    /**
     * Pulls the query's work items and upserts them as requirements. A tracker failure is recorded
     * on the settings for the UI and rethrown, so REST answers 424 with the tracker's reason. A dry
     * run works before sync is enabled, so the settings can be previewed first.
     */
    public RequirementSyncResultResponse sync(UUID projectId, boolean dryRun, UUID actorId) {
        RequirementSyncConfig config = syncConfigRepository.findByProjectId(projectId)
                .filter(c -> dryRun || c.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Requirement sync is not enabled for this project"));
        IssueTrackerConfig tracker = requireAzureDevOpsTracker(projectId);
        List<TrackerWorkItem> items;
        try {
            items = trackerConfigService.call(tracker, decrypted -> azureDevOps.queryWorkItems(decrypted,
                    config.getTrackerQuery()));
        } catch (UpstreamServiceException e) {
            recordOutcome(config.getId(), e.getMessage());
            throw e;
        }
        RequirementSyncResultResponse result = transaction.execute(status -> {
            RequirementSyncResultResponse counts = upsert(projectId, config, items, actorId, dryRun);
            if (dryRun) {
                status.setRollbackOnly();
            }
            return counts;
        });
        if (!dryRun) {
            recordOutcome(config.getId(), null);
        }
        return result;
    }

    private RequirementSyncResultResponse upsert(UUID projectId, RequirementSyncConfig config,
                                                 List<TrackerWorkItem> items, UUID actorId, boolean dryRun) {
        Instant now = Instant.now();
        Map<String, Requirement> byExternalId = new HashMap<>();
        requirementRepository.findByProjectIdOrderByExternalIdAsc(projectId)
                .forEach(r -> byExternalId.put(r.getExternalId(), r));

        Map<String, Requirement> byWorkItemId = new LinkedHashMap<>();
        Set<String> created = new HashSet<>();
        Set<String> changed = new HashSet<>();
        for (TrackerWorkItem item : items) {
            String externalId = externalId(config, item.id());
            Requirement requirement = byExternalId.get(externalId);
            if (requirement == null) {
                requirement = new Requirement();
                requirement.setProjectId(projectId);
                requirement.setExternalId(externalId);
                created.add(item.id());
            }
            if (applyFields(requirement, item, config)) {
                changed.add(item.id());
            }
            requirement.setLastSyncedAt(now);
            byWorkItemId.put(item.id(), requirementRepository.save(requirement));
        }
        // Parents second: a parent may come later in the query, and only now does every one have an id.
        for (TrackerWorkItem item : items) {
            Requirement parent = item.parentId() == null ? null : byWorkItemId.get(item.parentId());
            UUID parentId = parent == null ? null : parent.getId();
            Requirement requirement = byWorkItemId.get(item.id());
            if (!Objects.equals(requirement.getParentId(), parentId)) {
                requirement.setParentId(parentId);
                changed.add(item.id());
            }
        }
        int obsoleted = obsoleteMissing(projectId, byExternalId.values(), byWorkItemId.values(), actorId);

        int updated = 0;
        for (Map.Entry<String, Requirement> entry : byWorkItemId.entrySet()) {
            Requirement requirement = entry.getValue();
            if (created.contains(entry.getKey())) {
                audit(projectId, actorId, AuditAction.CREATED, requirement, "Synced from Azure DevOps");
            } else if (changed.contains(entry.getKey())) {
                updated++;
                audit(projectId, actorId, AuditAction.UPDATED, requirement, "Synced from Azure DevOps");
            }
        }
        int unchanged = items.size() - created.size() - updated;
        return new RequirementSyncResultResponse(created.size(), updated, obsoleted, unchanged, dryRun);
    }

    /** Sets the tracker-owned fields; true when any of them changed. */
    private static boolean applyFields(Requirement requirement, TrackerWorkItem item, RequirementSyncConfig config) {
        RequirementStatus status = statusFor(item.state(), config);
        Priority priority = priorityFor(item.priority());
        boolean changed = !Objects.equals(requirement.getTitle(), item.title())
                || !Objects.equals(requirement.getDescription(), item.description())
                || requirement.getStatus() != status
                || requirement.getPriority() != priority
                || requirement.getSource() != SOURCE
                || !Objects.equals(requirement.getSourceUrl(), item.url());
        requirement.setTitle(item.title());
        requirement.setDescription(item.description());
        requirement.setStatus(status);
        requirement.setPriority(priority);
        requirement.setSource(SOURCE);
        requirement.setSourceUrl(item.url());
        return changed;
    }

    /** Synced requirements whose work item left the query; kept, as they may carry coverage history. */
    private int obsoleteMissing(UUID projectId, Iterable<Requirement> existing, Iterable<Requirement> synced,
                                UUID actorId) {
        Set<UUID> stillSynced = new HashSet<>();
        synced.forEach(r -> stillSynced.add(r.getId()));
        int obsoleted = 0;
        for (Requirement requirement : existing) {
            if (requirement.getSource() == SOURCE && !stillSynced.contains(requirement.getId())
                    && requirement.getStatus() != RequirementStatus.OBSOLETE) {
                requirement.setStatus(RequirementStatus.OBSOLETE);
                obsoleted++;
                audit(projectId, actorId, AuditAction.UPDATED, requirement,
                        "No longer returned by the Azure DevOps query; marked obsolete");
            }
        }
        return obsoleted;
    }

    static RequirementStatus statusFor(String state, RequirementSyncConfig config) {
        if (state == null) {
            return RequirementStatus.DRAFT;
        }
        if (listContains(config.getObsoleteStates(), state)) {
            return RequirementStatus.OBSOLETE;
        }
        return listContains(config.getApprovedStates(), state) ? RequirementStatus.APPROVED : RequirementStatus.DRAFT;
    }

    /** Azure DevOps priority 1 is the highest; unset or unknown is MEDIUM, as for a new requirement. */
    static Priority priorityFor(Integer azurePriority) {
        if (azurePriority == null) {
            return Priority.MEDIUM;
        }
        return switch (azurePriority) {
            case 1 -> Priority.CRITICAL;
            case 2 -> Priority.HIGH;
            case 4 -> Priority.LOW;
            default -> Priority.MEDIUM;
        };
    }

    // ---- helpers ----------------------------------------------------------

    private IssueTrackerConfig requireAzureDevOpsTracker(UUID projectId) {
        IssueTrackerConfig tracker = trackerConfigService.requireActiveConfig(projectId);
        if (tracker.getProvider() != SOURCE) {
            throw new IllegalArgumentException("Requirement sync needs the project's issue tracker to be Azure DevOps; "
                    + "it is " + tracker.getProvider());
        }
        return tracker;
    }

    /** In a transaction of its own, so a failed sync's rollback cannot take the recorded error with it. */
    private void recordOutcome(UUID configId, String error) {
        Instant now = Instant.now();
        transaction.executeWithoutResult(status -> syncConfigRepository.findById(configId).ifPresent(config -> {
            if (error == null) {
                config.setLastSyncedAt(now);
                config.setLastError(null);
                config.setLastErrorAt(null);
            } else {
                config.setLastError(error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH)));
                config.setLastErrorAt(now);
            }
        }));
    }

    private void audit(UUID projectId, UUID actorId, AuditAction action, Requirement requirement, String details) {
        auditService.log(projectId, actorId, action, AuditEntityType.REQUIREMENT, requirement.getId(),
                requirement.getExternalId(), details);
    }

    private static String externalId(RequirementSyncConfig config, String workItemId) {
        return config.getExternalIdPrefix() == null ? workItemId : config.getExternalIdPrefix() + workItemId;
    }

    private static boolean listContains(String commaSeparated, String value) {
        return Arrays.stream(commaSeparated.split(",")).map(String::strip).anyMatch(value.strip()::equalsIgnoreCase);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static RequirementSyncConfigResponse toResponse(RequirementSyncConfig config) {
        return new RequirementSyncConfigResponse(config.isEnabled(), config.isScheduled(), config.getTrackerQuery(),
                config.getExternalIdPrefix(), config.getApprovedStates(), config.getObsoleteStates(),
                config.getLastSyncedAt(), config.getLastError(), config.getLastErrorAt());
    }
}
