package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.AuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {

    Page<AuditEntry> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    Page<AuditEntry> findByProjectIdAndEntityIdOrderByCreatedAtDesc(UUID projectId, UUID entityId, Pageable pageable);
}
