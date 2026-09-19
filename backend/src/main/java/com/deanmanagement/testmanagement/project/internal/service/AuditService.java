package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.audit.AuditEntryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.audit.AuditFilter;
import com.deanmanagement.testmanagement.project.internal.dto.audit.AuditLink;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntry;
import com.deanmanagement.testmanagement.project.internal.notification.NotificationDispatcher;
import com.deanmanagement.testmanagement.project.internal.repository.AuditEntryRepository;
import com.deanmanagement.testmanagement.project.internal.repository.spec.AuditEntrySpecifications;
import com.deanmanagement.testmanagement.user.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
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
    private final AuditLinks auditLinks;
    private final ObjectMapper objectMapper;

    @Transactional
    public void log(UUID projectId, UUID userId, AuditAction action,
                    AuditEntityType entityType, UUID entityId, String entityName, String details) {
        log(projectId, userId, action, entityType, entityId, entityName, details, FieldChanges.none(), null);
    }

    /** With the fields the update changed, old and new (PRD-046). */
    @Transactional
    public void log(UUID projectId, UUID userId, AuditAction action, AuditEntityType entityType, UUID entityId,
                    String entityName, String details, FieldChanges changes) {
        log(projectId, userId, action, entityType, entityId, entityName, details, changes, null);
    }

    /** {@code parent}: the object this one lives in, so the entry names and links to it. */
    @Transactional
    public void log(UUID projectId, UUID userId, AuditAction action, AuditEntityType entityType, UUID entityId,
                    String entityName, String details, FieldChanges changes, AuditParent parent) {
        AuditEntry entry = new AuditEntry();
        entry.setProjectId(projectId);
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setEntityName(entityName);
        entry.setDetails(details);
        entry.setChanges(changes.isEmpty() ? null : objectMapper.writeValueAsString(changes.list()));
        if (parent != null) {
            entry.setParentEntityType(parent.type());
            entry.setParentEntityId(parent.id());
        }
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

    /** The project's activity, or with {@code filter.entityId} one object's history (PRD-046). */
    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> find(UUID projectId, AuditFilter filter, Pageable pageable) {
        Page<AuditEntry> entries = auditEntryRepository.findAll(AuditEntrySpecifications.build(projectId, filter), pageable);
        List<AuditEntryResponse> responses = toResponses(entries.getContent());
        return new PageImpl<>(responses, pageable, entries.getTotalElements());
    }

    List<AuditEntryResponse> toResponses(List<AuditEntry> entries) {
        Map<UUID, String> displayNames = userService.findDisplayNamesByIds(entries.stream()
                .map(AuditEntry::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<UUID, AuditLink> links = auditLinks.resolve(entries);
        return entries.stream().map(entry -> toResponse(entry, displayNames, links.get(entry.getId()))).toList();
    }

    private AuditEntryResponse toResponse(AuditEntry entry, Map<UUID, String> displayNames, AuditLink link) {
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
                entry.getCreatedAt(),
                parseChanges(entry.getChanges()),
                entry.getParentEntityType(),
                entry.getParentEntityId(),
                link
        );
    }

    private List<FieldChanges.Change> parseChanges(String json) {
        if (json == null) {
            return List.of();
        }
        return List.of(objectMapper.readValue(json, FieldChanges.Change[].class));
    }
}
