package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.audit.AuditEntryResponse;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntry;
import com.deanmanagement.testmanagement.project.internal.notification.NotificationDispatcher;
import com.deanmanagement.testmanagement.project.internal.repository.AuditEntryRepository;
import com.deanmanagement.testmanagement.user.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEntryRepository auditEntryRepository;
    private final UserService userService;
    private final NotificationDispatcher notificationDispatcher;

    @Transactional
    public void log(UUID projectId, UUID userId, AuditAction action,
                    AuditEntityType entityType, UUID entityId, String entityName, String details) {
        AuditEntry entry = new AuditEntry();
        entry.setProjectId(projectId);
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setEntityName(entityName);
        entry.setDetails(details);
        entry.setCreatedAt(Instant.now());
        auditEntryRepository.save(entry);

        // Fan out to watchers. Never let a notification problem break the audited action.
        try {
            notificationDispatcher.dispatch(projectId, userId, action, entityType, entityId, entityName);
        } catch (Exception e) {
            log.warn("Notification dispatch failed for {} {} {}: {}",
                    action, entityType, entityId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> getActivity(UUID projectId, int page, int size) {
        Page<AuditEntry> entries = auditEntryRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(page, size));
        return toResponsePage(entries);
    }

    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> getEntityHistory(UUID projectId, UUID entityId, int page, int size) {
        Page<AuditEntry> entries = auditEntryRepository
                .findByProjectIdAndEntityIdOrderByCreatedAtDesc(projectId, entityId, PageRequest.of(page, size));
        return toResponsePage(entries);
    }

    private Page<AuditEntryResponse> toResponsePage(Page<AuditEntry> entries) {
        Map<UUID, String> displayNames = userService.findDisplayNamesByIds(entries.getContent().stream()
                .map(AuditEntry::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return entries.map(entry -> toResponse(entry, displayNames));
    }

    private AuditEntryResponse toResponse(AuditEntry entry, Map<UUID, String> displayNames) {
        String displayName = entry.getUserId() != null ? displayNames.get(entry.getUserId()) : null;
        return new AuditEntryResponse(
                entry.getId(),
                entry.getProjectId(),
                entry.getUserId(),
                displayName,
                entry.getAction(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getEntityName(),
                entry.getDetails(),
                entry.getCreatedAt()
        );
    }
}
