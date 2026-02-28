package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.audit.AuditEntryResponse;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntry;
import com.deanmanagement.testmanagement.project.internal.repository.AuditEntryRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEntryRepository auditEntryRepository;
    private final UserService userService;

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
    }

    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> getActivity(UUID projectId, int page, int size) {
        Page<AuditEntry> entries = auditEntryRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(page, size));
        return entries.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> getEntityHistory(UUID projectId, UUID entityId, int page, int size) {
        Page<AuditEntry> entries = auditEntryRepository
                .findByProjectIdAndEntityIdOrderByCreatedAtDesc(projectId, entityId, PageRequest.of(page, size));
        return entries.map(this::toResponse);
    }

    private AuditEntryResponse toResponse(AuditEntry entry) {
        String displayName = null;
        if (entry.getUserId() != null) {
            displayName = userService.findEntityById(entry.getUserId())
                    .map(User::getDisplayName)
                    .orElse(null);
        }
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
